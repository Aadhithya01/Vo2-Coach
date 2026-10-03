package com.vo2coach.gps

import java.util.Locale

/**
 * Pure Kotlin (no Android) rolling pace calculator.
 *
 * Feed it cumulative *active* distance samples with their timestamps (epoch ms; [currentSecPerKm]'s
 * `nowMs` must use the same clock). Pace is computed over the last
 * [windowMs] from distance deltas: secPerKm = dtMs / dMeters (ms per metre == s per km).
 *
 * Falls back to the device PACE sample (if fresh) while the window is still too short.
 * Returns null when standing still / implausible so the UI shows "--".
 */
class PaceTracker(
    private val windowMs: Long = 10_000L,
    private val minWindowMs: Long = 4_000L,
    /** Below this many metres moved in the window we treat the runner as stopped. */
    private val minMoveM: Double = 2.0,
    /** Device PACE samples older than this are ignored. */
    private val paceSampleMaxAgeMs: Long = 6_000L,
    /** No distance sample for this long -> distance-based pace is considered stale. */
    private val staleMs: Long = 12_000L,
) {
    companion object {
        /** Faster than 1:40 /km is a GPS glitch for a runner. */
        const val MIN_SEC_PER_KM = 100

        /** Slower than 30:00 /km is effectively standing. */
        const val MAX_SEC_PER_KM = 1800
    }

    private data class Sample(val tMs: Long, val distM: Double)

    private val samples = ArrayDeque<Sample>()
    private var lastDevicePaceSecPerKm: Double? = null
    private var lastDevicePaceAtMs: Long = Long.MIN_VALUE

    fun reset() {
        samples.clear()
        lastDevicePaceSecPerKm = null
        lastDevicePaceAtMs = Long.MIN_VALUE
    }

    /** Cumulative active distance (metres) at time [tMs]. Out-of-order samples are ignored. */
    fun addDistance(tMs: Long, totalDistM: Double) {
        val last = samples.lastOrNull()
        if (last != null) {
            if (tMs < last.tMs) return
            if (tMs == last.tMs) {
                samples.removeLast()
            }
        }
        samples.addLast(Sample(tMs, totalDistM))
        prune(tMs)
    }

    /** Device-reported pace in seconds per km (Health Services PACE is ms/km -> divide by 1000). 0 = stopped. */
    fun addDevicePace(tMs: Long, secPerKm: Double) {
        lastDevicePaceSecPerKm = secPerKm
        lastDevicePaceAtMs = tMs
    }

    /**
     * Smoothed current pace in seconds per km, or null if unknown / stopped.
     *
     * The window is measured back from the *latest distance sample's* timestamp (not from [nowMs]),
     * so batched Health Services deliveries do not skew the result. [nowMs] is only used to
     * detect staleness: no distance sample for [staleMs] -> null.
     */
    fun currentSecPerKm(nowMs: Long): Int? {
        val anchor = samples.firstOrNull()
        val latest = samples.lastOrNull()
        if (anchor != null && latest != null && latest !== anchor && nowMs - latest.tMs <= staleMs) {
            val dt = latest.tMs - anchor.tMs
            if (dt >= minWindowMs) {
                val dd = latest.distM - anchor.distM
                if (dd < minMoveM * dt / windowMs.toDouble()) return null
                return plausible(dt / dd)
            }
        }
        // window too short (or distance stale) -> device pace if fresh
        val dp = lastDevicePaceSecPerKm ?: return null
        if (nowMs - lastDevicePaceAtMs > paceSampleMaxAgeMs) return null
        if (dp <= 0.0) return null
        return plausible(dp)
    }

    /** Keeps everything inside the window (relative to [latestMs]) plus one anchor at/older than its start. */
    private fun prune(latestMs: Long) {
        val start = latestMs - windowMs
        while (samples.size >= 2 && samples[1].tMs <= start) samples.removeFirst()
    }

    private fun plausible(secPerKm: Double): Int? {
        if (secPerKm.isNaN() || secPerKm.isInfinite()) return null
        val v = secPerKm.toInt()
        return if (v in MIN_SEC_PER_KM..MAX_SEC_PER_KM) v else null
    }
}

/** Formatting + math helpers shared by UI, service and record enrichment. Pure Kotlin. */
object PaceMath {
    /** Average pace (s/km) from a duration and distance; null below 50 m (too noisy). */
    fun avgSecPerKm(durationSec: Int, distanceM: Double?): Int? {
        if (distanceM == null || distanceM < 50.0 || durationSec <= 0) return null
        val v = (durationSec * 1000.0 / distanceM).toInt()
        return if (v in PaceTracker.MIN_SEC_PER_KM..3600) v else null
    }

    /** 312 -> "5'12\"", null -> "--'--\"" */
    fun formatPace(secPerKm: Int?): String =
        if (secPerKm == null || secPerKm <= 0) "--'--\""
        else String.format(Locale.US, "%d'%02d\"", secPerKm / 60, secPerKm % 60)

    /** < 1 km -> "850 M", else "2.34 KM". null -> "-- KM". */
    fun formatDistance(distanceM: Double?): String = when {
        distanceM == null -> "-- KM"
        distanceM < 1000.0 -> "${distanceM.toInt()} M"
        else -> String.format(Locale.US, "%.2f KM", distanceM / 1000.0)
    }
}
