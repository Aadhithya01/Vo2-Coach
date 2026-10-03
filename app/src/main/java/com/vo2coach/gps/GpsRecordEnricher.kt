package com.vo2coach.gps

import com.vo2coach.data.SessionRecord

/**
 * Copies GPS distance/pace into the shared SessionRecord contract (never modifies the contract).
 * Pure Kotlin.
 *
 * - SessionRecord.distanceM       = active distance (null if the device reported none)
 * - SessionRecord.avgPaceSecPerKm = record.elapsedSec / km (null under 50 m)
 * - IntervalStat.distanceM / avgPaceSecPerKm matched by IntervalStat.index == IntervalDistance.workIndex;
 *   pace uses IntervalStat.durationSec (service-counted seconds) for consistency with HR stats.
 */
object GpsRecordEnricher {
    fun enrich(record: SessionRecord, distanceM: Double?, intervals: List<IntervalDistance>): SessionRecord {
        if (distanceM == null) return record
        val byIndex = intervals.associateBy { it.workIndex }
        return record.copy(
            distanceM = distanceM,
            avgPaceSecPerKm = PaceMath.avgSecPerKm(record.elapsedSec, distanceM),
            intervals = record.intervals.map { s ->
                val iv = byIndex[s.index] ?: return@map s
                s.copy(
                    distanceM = iv.distanceM,
                    avgPaceSecPerKm = PaceMath.avgSecPerKm(s.durationSec, iv.distanceM),
                )
            },
        )
    }

    fun enrich(record: SessionRecord, metrics: LiveRunMetrics): SessionRecord =
        enrich(record, metrics.distanceMOrNull, metrics.intervals())
}
