package com.vo2coach.gps

import android.os.SystemClock
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseUpdate
import com.vo2coach.Phase
import com.vo2coach.PhaseType
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

/** Distance/pace for one WORK interval. [workIndex] == IntervalStat.index (0-based among WORK phases). */
data class IntervalDistance(
    val workIndex: Int,
    val distanceM: Double,
    val durationSec: Int,
    val avgPaceSecPerKm: Int?,
)

/**
 * Live distance + pace for the running session, fed from Health Services [ExerciseUpdate]s.
 *
 * - Distance: DataType.DISTANCE_TOTAL (AggregateDataType -> CumulativeDataPoint<Double>.total, metres
 *   since exercise start). If the device never reports DISTANCE_TOTAL, falls back to summing
 *   DataType.DISTANCE deltas (DeltaDataType -> List<IntervalDataPoint<Double>>).
 * - Pace: 10 s rolling window over distance deltas ([PaceTracker]); DataType.PACE (ms/km,
 *   SampleDataPoint<Double>) is used only as an early fallback.
 * - App-level pause (WorkoutService only pauses its own timer, not the Health Services exercise):
 *   distance gained while [setPaused] (true) is excluded from [totalDistanceM] and intervals.
 * - Per-WORK-interval distance via [onPhase] (automatic) or [markIntervalStart]/[markIntervalEnd].
 *
 * Thread-safe (all state behind one lock): Health Services callbacks and the session loop may run
 * on different threads.
 */
class LiveRunMetrics(
    /** Epoch-ms clock; must match the Instant-based timestamps of the data points. */
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private class OpenInterval(
        val workIndex: Int,
        val startDistM: Double,
        val startMs: Long,
        val startPausedMs: Long,
    )

    private val lock = Any()
    private val pace = PaceTracker()

    private var rawTotalM: Double? = null
    private var deltaSumM = 0.0
    private var sawTotal = false
    private var pausedOffsetM = 0.0

    private var paused = false
    private var pausedSinceMs = 0L
    private var pausedTotalMs = 0L

    private var gpsEnabled = false
    private var status = GpsStatus.OFF

    private var phases: List<Phase> = emptyList()
    private var workOrdinal: Map<Int, Int> = emptyMap()
    private var lastPhaseIndex = -1
    private var open: OpenInterval? = null
    private val done = LinkedHashMap<Int, IntervalDistance>()

    // ------------------------------------------------------------------ lifecycle

    /** Call once at session start, before the first update. */
    fun reset(phases: List<Phase>, gpsEnabled: Boolean = false) {
        synchronized(lock) {
            pace.reset()
            rawTotalM = null
            deltaSumM = 0.0
            sawTotal = false
            pausedOffsetM = 0.0
            paused = false
            pausedSinceMs = 0L
            pausedTotalMs = 0L
            this.gpsEnabled = gpsEnabled
            status = if (gpsEnabled) GpsStatus.ACQUIRING else GpsStatus.OFF
            this.phases = phases
            var n = 0
            val m = HashMap<Int, Int>()
            phases.forEachIndexed { i, p -> if (p.type == PhaseType.WORK) m[i] = n++ }
            workOrdinal = m
            lastPhaseIndex = -1
            open = null
            done.clear()
        }
    }

    /** Call after the exercise started, with GpsExerciseConfig.Plan.gpsEnabled. Keeps distance data. */
    fun setGpsEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (enabled == gpsEnabled) return
            gpsEnabled = enabled
            status = if (enabled) GpsStatus.ACQUIRING else GpsStatus.OFF
        }
    }

    // ------------------------------------------------------------------ Health Services input

    /** Call from ExerciseUpdateCallback.onExerciseUpdateReceived. */
    fun onUpdate(update: ExerciseUpdate) {
        val metrics = update.latestMetrics
        // Boot instant converts the "duration from boot" timestamps of Sample/Interval points to wall clock.
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

        synchronized(lock) {
            val total = metrics.getData(DataType.DISTANCE_TOTAL) // CumulativeDataPoint<Double>?
            if (total != null) {
                sawTotal = true
                ingestRaw(total.total, total.end.toEpochMilli())
            } else if (!sawTotal) {
                for (d in metrics.getData(DataType.DISTANCE)) { // List<IntervalDataPoint<Double>>
                    deltaSumM += d.value
                    ingestRaw(deltaSumM, d.getEndInstant(bootInstant).toEpochMilli())
                }
            }
            metrics.getData(DataType.PACE).lastOrNull()?.let { p -> // SampleDataPoint<Double>, ms/km
                pace.addDevicePace(p.getTimeInstant(bootInstant).toEpochMilli(), p.value / 1000.0)
            }
            // A location fix implies GPS is working even if no availability callback arrived yet.
            if (gpsEnabled && status == GpsStatus.ACQUIRING && metrics.getData(DataType.LOCATION).isNotEmpty()) {
                status = GpsStatus.OK
            }
        }
    }

    /** Call from ExerciseUpdateCallback.onAvailabilityChanged. Non-location availabilities are ignored. */
    @Suppress("UNUSED_PARAMETER")
    fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
        val s = GpsStatus.from(availability) ?: return
        synchronized(lock) { if (gpsEnabled) status = s }
    }

    /** Pure entry point (also used by tests): cumulative device distance in metres at [tMs]. */
    fun ingestTotalDistance(tMs: Long, totalM: Double) {
        synchronized(lock) {
            sawTotal = true
            ingestRaw(totalM, tMs)
        }
    }

    private fun ingestRaw(raw: Double, tMs: Long) {
        if (raw.isNaN()) return
        val prev = rawTotalM
        if (prev != null) {
            if (raw < prev) return // never go backwards
            if (paused) pausedOffsetM += raw - prev
        }
        rawTotalM = raw
        pace.addDistance(tMs, raw - pausedOffsetM)
    }

    // ------------------------------------------------------------------ session-loop input

    /** Mirror of SessionState.paused. Call whenever it changes (idempotent; fine to call every tick). */
    fun setPaused(value: Boolean) {
        synchronized(lock) {
            if (value == paused) return
            val now = nowMs()
            if (value) {
                pausedSinceMs = now
            } else {
                pausedTotalMs += max(0L, now - pausedSinceMs)
                pace.reset() // don't blend pre-pause and post-pause samples
            }
            paused = value
        }
    }

    /**
     * Call every tick with the current phase index (idempotent). On change it closes the open WORK
     * interval (if any) and opens a new one when the new phase is WORK.
     */
    fun onPhase(phaseIndex: Int) {
        synchronized(lock) {
            if (phaseIndex == lastPhaseIndex) return
            lastPhaseIndex = phaseIndex
            endOpenLocked()
            val p = phases.getOrNull(phaseIndex) ?: return
            if (p.type == PhaseType.WORK) startLocked(workOrdinal[phaseIndex] ?: return)
        }
    }

    /** Manually open a WORK interval ([workIndex] = 0-based ordinal among WORK phases). Closes any open one. */
    fun markIntervalStart(workIndex: Int) {
        synchronized(lock) {
            endOpenLocked()
            startLocked(workIndex)
        }
    }

    /** Closes the open interval and returns its distance, or null if none was open. */
    fun markIntervalEnd(): IntervalDistance? = synchronized(lock) { endOpenLocked() }

    /** Call when the session ends (natural finish or quit) to close a still-open WORK interval. */
    fun finish() {
        synchronized(lock) {
            endOpenLocked()
            if (paused) {
                pausedTotalMs += max(0L, nowMs() - pausedSinceMs)
                paused = false
            }
        }
    }

    private fun startLocked(workIndex: Int) {
        open = OpenInterval(workIndex, activeDistanceLocked(), nowMs(), pausedMsNowLocked())
    }

    private fun endOpenLocked(): IntervalDistance? {
        val o = open ?: return null
        open = null
        val dist = max(0.0, activeDistanceLocked() - o.startDistM)
        val activeMs = (nowMs() - o.startMs) - (pausedMsNowLocked() - o.startPausedMs)
        val durSec = (activeMs / 1000.0).roundToInt().coerceAtLeast(0)
        val iv = IntervalDistance(o.workIndex, dist, durSec, PaceMath.avgSecPerKm(durSec, dist))
        done[o.workIndex] = iv
        return iv
    }

    private fun pausedMsNowLocked(): Long =
        pausedTotalMs + if (paused) max(0L, nowMs() - pausedSinceMs) else 0L

    private fun activeDistanceLocked(): Double = (rawTotalM ?: 0.0) - pausedOffsetM

    // ------------------------------------------------------------------ outputs

    /** Active distance in metres (0.0 until the first distance arrives). */
    val totalDistanceM: Double get() = synchronized(lock) { activeDistanceLocked() }

    /** Active distance, or null if the device never reported any distance (use this for SessionState/record). */
    val distanceMOrNull: Double? get() = synchronized(lock) { rawTotalM?.let { it - pausedOffsetM } }

    /** Smoothed current pace (s/km), null while paused, stopped or unknown. */
    val currentPaceSecPerKm: Int? get() = synchronized(lock) { if (paused) null else pace.currentSecPerKm(nowMs()) }

    val gpsStatus: GpsStatus get() = synchronized(lock) { status }

    /** Completed WORK intervals, ordered by workIndex. */
    fun intervals(): List<IntervalDistance> = synchronized(lock) { done.values.sortedBy { it.workIndex } }

    fun interval(workIndex: Int): IntervalDistance? = synchronized(lock) { done[workIndex] }
}
