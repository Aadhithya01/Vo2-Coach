package com.vo2coach.data

/**
 * SHARED CONTRACT — every feature codes against these types. Do not rename fields.
 * Persisted as JSON by HistoryStore (feature 1).
 */
data class IntervalStat(
    val index: Int,                 // 0-based index among WORK phases
    val label: String,
    val durationSec: Int,
    val avgHr: Int?,
    val peakHr: Int?,
    val inZoneSec: Int,
    val distanceM: Double? = null,  // feature 3 (GPS)
    val avgPaceSecPerKm: Int? = null,
    val hrDrop60s: Int? = null,     // feature 4: peakHr at end of interval minus HR 60 s later
)

data class SessionRecord(
    val id: String,                 // UUID
    val startedAtMs: Long,
    val modeId: String,             // Modes.byId(...) id, or "custom:<id>" for feature 6
    val modeTitle: String,
    val completed: Boolean,         // false if quit early
    val elapsedSec: Int,
    val avgHr: Int?,
    val peakHr: Int?,
    val workSec: Int,
    val workInZoneSec: Int,
    val maxHrSetting: Int,
    val distanceM: Double? = null,
    val avgPaceSecPerKm: Int? = null,
    val avgHrDrop60s: Int? = null,
    val intervals: List<IntervalStat> = emptyList(),
) {
    val inZonePct: Int get() = if (workSec > 0) workInZoneSec * 100 / workSec else 0
}
