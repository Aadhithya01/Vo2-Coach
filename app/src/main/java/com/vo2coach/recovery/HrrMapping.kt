package com.vo2coach.recovery

import com.vo2coach.data.SessionRecord

/**
 * Copies HRR results into the shared [SessionRecord] contract (pure, no Android).
 *  - SessionRecord.avgHrDrop60s  <- tracker.average
 *  - IntervalStat.hrDrop60s      <- tracker.perInterval[IntervalStat.index]
 * IntervalStat.index is the 0-based index among WORK phases, which is exactly how
 * [HrRecoveryTracker.perInterval] is aligned.
 */
fun SessionRecord.withHrRecovery(tracker: HrRecoveryTracker): SessionRecord {
    val per = tracker.perInterval
    return copy(
        avgHrDrop60s = tracker.average,
        intervals = intervals.map { it.copy(hrDrop60s = per.getOrNull(it.index)) },
    )
}
