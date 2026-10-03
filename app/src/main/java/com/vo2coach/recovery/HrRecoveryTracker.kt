package com.vo2coach.recovery

import com.vo2coach.PhaseType
import kotlin.math.roundToInt

/**
 * Heart-rate recovery (HRR60) tracker. Pure Kotlin, no Android APIs, not thread-safe
 * (call from the WorkoutService session loop only).
 *
 * Definition used: for every WORK phase, `hrDrop60s = hrAtEnd - hrAt60s` where
 *  - `hrAtEnd` = HR on the last WORK tick if valid, else the peak valid HR of the last
 *    [END_WINDOW_SEC] WORK ticks (null if none -> interval result is null);
 *  - `hrAt60s` = HR sampled exactly [WINDOW_SEC] seconds after the interval ended. If the
 *    sample at exactly 60 s is null (sensor dropout), the first valid sample within the next
 *    [LATE_TOLERANCE_SEC] seconds is used; otherwise null. Nothing is ever extrapolated.
 *
 * If the next WORK phase begins before the 60 s window has elapsed (e.g. Max 30/15 with 15 s
 * rests, or WORK directly followed by WORK), that interval's result is null. The final WORK is
 * followed by the 5 min cool-down, so it is normally measured.
 *
 * Timing convention (matches WorkoutService): [onTick] is called once per counted second,
 * with the `hr` read at the top of that loop iteration, i.e. at the *start* of the second.
 * So the first non-WORK tick samples t = 0 s after interval end, and the 61st samples t = 60 s.
 *
 * Pauses: wall-clock time keeps running for the body, so call [onPausedTick] once per paused
 * loop iteration. It advances the 60 s recovery clock (and samples HR) without touching phase
 * tracking. If it is never called, pauses simply stretch the window (less accurate, still safe).
 */
class HrRecoveryTracker {

    companion object {
        const val WINDOW_SEC = 60
        const val END_WINDOW_SEC = 5
        const val LATE_TOLERANCE_SEC = 3
        /** After the 60 s sample is locked, keep the result visible in the live chip this long. */
        const val SHOW_LOCKED_SEC = 10
        /** Plausibility guard for raw sensor values. */
        private val VALID_HR = 30..250
    }

    /** Live chip data. secondsLeft == 0 means the 60 s value is locked in (drop is final). */
    data class Live(val drop: Int, val secondsLeft: Int, val locked: Boolean)

    private class Pending(val workIdx: Int, val hrAtEnd: Int) {
        /** Seconds since the interval ended for the *next* sample to be taken. */
        var t = 0
        var lastDrop: Int? = null
    }

    private val results = ArrayList<Int?>()
    private var lastPhaseIndex = -1
    private var lastType: PhaseType? = null
    private var workCount = 0

    // ring buffer of the last END_WINDOW_SEC WORK-tick HR values (nullable = no reading)
    private val endBuf = ArrayDeque<Int?>()

    private var pending: Pending? = null
    private var lockedDrop: Int? = null
    private var lockedShowLeft = 0

    /** hrDrop60s aligned to WORK index (0-based among WORK phases); null = not measured. */
    val perInterval: List<Int?> get() = results.toList()

    /** Mean of the non-null [perInterval] values, rounded; null if none. */
    val average: Int?
        get() {
            val v = results.filterNotNull()
            return if (v.isEmpty()) null else v.average().roundToInt()
        }

    /** Largest non-null drop; null if none. */
    val best: Int? get() = results.filterNotNull().maxOrNull()

    val averageRating: HrrRating? get() = average?.let { HrrRating.of(it) }

    /** Current live chip state, or null when nothing is being measured / shown. */
    var live: Live? = null
        private set

    /** Convenience for SessionState.liveHrDrop. */
    val liveHrDrop: Int? get() = live?.drop

    /** Convenience for SessionState.hrrSecondsLeft (0 = locked). */
    val hrrSecondsLeft: Int? get() = live?.secondsLeft

    /** Call once per counted (non-paused) second. */
    fun onTick(phaseIndex: Int, phaseType: PhaseType, hr: Int?) {
        val h = hr?.takeIf { it in VALID_HR }
        val phaseChanged = phaseIndex != lastPhaseIndex

        if (phaseChanged) {
            // Leaving a WORK phase: the interval just ended -> open a recovery window.
            if (lastType == PhaseType.WORK) closeWork()
            if (phaseType == PhaseType.WORK) {
                // A new WORK started: any open window is cut short -> null (no extrapolation).
                abandonPending()
                lockedDrop = null; lockedShowLeft = 0
                workCount++
                results.add(null)
                endBuf.clear()
            }
            lastPhaseIndex = phaseIndex
            lastType = phaseType
        }

        if (phaseType == PhaseType.WORK) {
            endBuf.addLast(h)
            while (endBuf.size > END_WINDOW_SEC) endBuf.removeFirst()
            live = null
            return
        }
        advanceRecovery(h)
    }

    /** Optional: call once per paused loop iteration (body keeps recovering in real time). */
    fun onPausedTick(hr: Int?) {
        if (lastType == PhaseType.WORK) return // paused mid-interval: interval hasn't ended
        advanceRecovery(hr?.takeIf { it in VALID_HR })
    }

    /** Session ended (finished or quit): any window still open is recorded as null. */
    fun finish() {
        if (lastType == PhaseType.WORK) closeWork() // quit mid-WORK: cannot measure
        abandonPending()
        live = null
    }

    // ---------------------------------------------------------------- internals

    private fun closeWork() {
        val idx = workCount - 1
        if (idx < 0) return
        val last = endBuf.lastOrNull()
        val hrAtEnd = last ?: endBuf.filterNotNull().maxOrNull()
        endBuf.clear()
        pending = hrAtEnd?.let { Pending(idx, it) }
    }

    private fun abandonPending() {
        pending = null // results[idx] already null
    }

    private fun advanceRecovery(h: Int?) {
        val p = pending
        if (p == null) {
            // Show the locked result briefly, then hide the chip.
            val d = lockedDrop
            if (d != null && lockedShowLeft > 0) {
                lockedShowLeft--
                live = Live(d, 0, locked = true)
            } else {
                lockedDrop = null
                live = null
            }
            return
        }
        val t = p.t
        if (h != null) p.lastDrop = p.hrAtEnd - h
        if (t >= WINDOW_SEC && h != null) {
            val drop = p.hrAtEnd - h
            results[p.workIdx] = drop
            pending = null
            lockedDrop = drop
            lockedShowLeft = SHOW_LOCKED_SEC - 1 // this tick counts as the first shown second
            live = Live(drop, 0, locked = true)
            return
        }
        if (t >= WINDOW_SEC + LATE_TOLERANCE_SEC) {
            // No valid reading around 60 s: give up, record null.
            pending = null
            live = null
            return
        }
        p.t = t + 1
        val shown = p.lastDrop
        live = if (shown != null) Live(shown, (WINDOW_SEC - t).coerceAtLeast(1), locked = false) else null
    }
}

/**
 * Interpretation of a 60 s heart-rate drop (bpm). Thresholds are approximate guidance for
 * training feedback only, not a medical assessment.
 */
enum class HrrRating(val label: String, val short: String, val detail: String) {
    POOR("Poor — check fatigue", "Poor", "Check fatigue. Sleep, fuel, easy day. Feel unwell? Stop."),
    AVERAGE("Average", "Average", "Engine's running. Build it."),
    GOOD("Good", "Good", "Fast reset. That's fitness."),
    ELITE("Elite engine", "Elite", "Drops like a stone. Earned.");

    companion object {
        fun of(dropBpm: Int): HrrRating = when {
            dropBpm < 12 -> POOR
            dropBpm < 20 -> AVERAGE
            dropBpm < 30 -> GOOD
            else -> ELITE
        }
    }
}
