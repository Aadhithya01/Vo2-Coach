package com.vo2coach.tile

import com.vo2coach.data.SessionRecord
import com.vo2coach.history.StreakCalc
import java.time.ZoneId

/*
 * Feature #8: Tile + complication — pure model.
 *
 * No Android imports: everything here is JVM-testable. The tile layout and the complication
 * service only render what this file computes.
 */

/** Today's recommended session, as far as the tile cares. Built from ProgressionEngine.TodayPlan. */
data class TodayPick(
    val modeId: String,
    /** Short uppercase mode name, e.g. "NORWEGIAN 4X4". */
    val title: String,
    /** Index into Modes.all, used for levelColor. -1 if unknown. */
    val levelIndex: Int,
    /** True when the plan wanted a hard day but the safety guard forced Base. */
    val downgradedByGuard: Boolean = false,
    /** Plan's session target for this week, if the plan provides one. */
    val weeklyTarget: Int? = null,
)

/** Everything the tile needs to draw. */
data class TileSnapshot(
    val streakWeeks: Int,
    val sessionsThisWeek: Int,
    val weeklyTarget: Int,
    val atRisk: Boolean,
    val pick: TodayPick?,
    /** One short uppercase line under the START button (<= ~20 chars). */
    val headline: String,
) {
    /** 0f..1f progress of this week's sessions vs target. */
    val weekFraction: Float get() = TileModel.fraction(sessionsThisWeek, weeklyTarget)

    /** Mode id the START button launches, or null to just open the app. */
    val startModeId: String? get() = pick?.modeId
}

object TileModel {

    /** Fixed weekly target used by the complication and as the tile fallback. */
    const val DEFAULT_WEEKLY_TARGET = 3

    fun fraction(done: Int, target: Int): Float =
        if (target <= 0) 0f else (done.toFloat() / target.toFloat()).coerceIn(0f, 1f)

    /** "4. Norwegian 4x4" -> "NORWEGIAN 4X4". */
    fun shortTitle(modeTitle: String): String = modeTitle.substringAfter(". ").uppercase()

    /** Streak label: "0W", "3W". */
    fun streakLabel(weeks: Int): String = "${weeks.coerceAtLeast(0)}W"

    /** Weekly sessions label: "2/3". */
    fun weekLabel(done: Int, target: Int): String = "${done.coerceAtLeast(0)}/${target.coerceAtLeast(0)}"

    /**
     * Headline under the START button. Brutal but never abusive; a guard downgrade is stated
     * plainly and safely (easy day, no negotiation).
     */
    fun headline(streak: Int, done: Int, target: Int, atRisk: Boolean, pick: TodayPick?): String = when {
        pick?.downgradedByGuard == true -> "RECOVER. ZONE 2 ONLY."
        target > 0 && done >= target -> "WEEK DONE. RECOVER."
        atRisk -> "STREAK ON THE LINE."
        streak == 0 && done == 0 -> "DAY ONE. SHOW UP."
        target > done -> "${target - done} MORE. NO EXCUSES."
        else -> "SHOW UP."
    }

    /** Pure core: build from already-computed numbers. */
    fun build(
        streakWeeks: Int,
        sessionsThisWeek: Int,
        pick: TodayPick?,
        weeklyTargetOverride: Int? = null,
    ): TileSnapshot {
        val target = (weeklyTargetOverride ?: pick?.weeklyTarget ?: DEFAULT_WEEKLY_TARGET)
            .takeIf { it > 0 } ?: DEFAULT_WEEKLY_TARGET
        val atRisk = streakWeeks > 0 && sessionsThisWeek == 0
        return TileSnapshot(
            streakWeeks = streakWeeks,
            sessionsThisWeek = sessionsThisWeek,
            weeklyTarget = target,
            atRisk = atRisk,
            pick = pick,
            headline = headline(streakWeeks, sessionsThisWeek, target, atRisk, pick),
        )
    }

    /** Build from history (uses feature #1's pure StreakCalc). */
    fun fromRecords(
        records: List<SessionRecord>,
        nowMs: Long,
        pick: TodayPick?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TileSnapshot {
        val streak = StreakCalc.currentWeekStreak(records, nowMs, zone)
        val week = StreakCalc.sessionsThisWeek(records, nowMs, zone)
        return build(streak, week, pick)
    }

    // ------------------------------------------------------------ complication text

    /** SHORT_TEXT complication: text = streak, title = week progress vs fixed target 3. */
    data class ComplicationTexts(val text: String, val title: String, val description: String)

    fun complicationTexts(streakWeeks: Int, sessionsThisWeek: Int, target: Int = DEFAULT_WEEKLY_TARGET): ComplicationTexts =
        ComplicationTexts(
            text = streakLabel(streakWeeks),
            title = weekLabel(sessionsThisWeek, target),
            description = "VO2 streak $streakWeeks weeks, $sessionsThisWeek of $target sessions this week",
        )

    /** RANGED_VALUE: value clamped into [0, target] (the API requires min <= value <= max). */
    fun rangedValue(sessionsThisWeek: Int, target: Int = DEFAULT_WEEKLY_TARGET): Float =
        sessionsThisWeek.coerceIn(0, target.coerceAtLeast(1)).toFloat()
}
