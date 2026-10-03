package com.vo2coach.history

import com.vo2coach.data.SessionRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class StreakInfo(
    /** Consecutive ISO weeks (Mon-start) with >= 1 completed session, ending this week (or last week). */
    val currentWeekStreak: Int,
    /** Completed sessions in the current week. */
    val sessionsThisWeek: Int,
    /** All completed sessions in history. */
    val totalCompleted: Int,
) {
    /** True when the streak is alive only thanks to last week: one session this week keeps it going. */
    val atRisk: Boolean get() = currentWeekStreak > 0 && sessionsThisWeek == 0
}

/** Pure streak math. No Android dependencies, so it is unit-testable on the JVM. */
object StreakCalc {

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekStartOf(epochMs: Long, zone: ZoneId): LocalDate =
        weekStart(Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())

    fun compute(
        records: List<SessionRecord>,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): StreakInfo {
        val completed = records.filter { it.completed }
        val weeks: Set<LocalDate> = completed.mapTo(HashSet()) { weekStartOf(it.startedAtMs, zone) }
        val thisWeek = weekStartOf(nowMs, zone)

        val sessionsThisWeek = completed.count { weekStartOf(it.startedAtMs, zone) == thisWeek }

        var cursor = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        var streak = 0
        while (cursor in weeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }

        return StreakInfo(
            currentWeekStreak = streak,
            sessionsThisWeek = sessionsThisWeek,
            totalCompleted = completed.size,
        )
    }

    fun currentWeekStreak(records: List<SessionRecord>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int =
        compute(records, nowMs, zone).currentWeekStreak

    fun sessionsThisWeek(records: List<SessionRecord>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int =
        compute(records, nowMs, zone).sessionsThisWeek

    fun totalCompleted(records: List<SessionRecord>): Int = records.count { it.completed }
}
