package com.vo2coach.progression

import com.vo2coach.Modes
import com.vo2coach.WorkoutMode
import com.vo2coach.data.SessionRecord
import java.time.Duration
import java.time.Instant

/*
 * Feature #2: 8-week auto-progression + hard-session guard.
 *
 * Pure Kotlin. No Android imports. Everything is derived from session history,
 * nothing is persisted here except the optional force-unlock flag (see ProgressionPrefs).
 */

/** Unlock state of one mode, derived from history. */
data class UnlockState(
    val modeId: String,
    val index: Int,
    val unlocked: Boolean,
    /** Mode that has to be earned first (null for Base). */
    val prerequisiteModeId: String?,
    val prerequisiteTitle: String?,
    /** Completed prerequisite sessions with inZonePct >= [ProgressionEngine.UNLOCK_MIN_IN_ZONE_PCT]. */
    val qualifying: Int,
    val required: Int = ProgressionEngine.UNLOCK_SESSIONS,
    /** True when unlocked only because of the force-unlock override. */
    val forced: Boolean = false,
) {
    /** Short uppercase condition line for a card / detail screen, e.g. "3x BASE ≥60% · 1/3". */
    val conditionLine: String
        get() = if (prerequisiteTitle == null) "" else
            "${required}x ${prerequisiteTitle.uppercase()} ≥${ProgressionEngine.UNLOCK_MIN_IN_ZONE_PCT}% · " +
                "${qualifying.coerceAtMost(required)}/$required"
}

enum class GuardRule { NONE, MIN_GAP_48H, MAX_2_PER_7D }

data class GuardResult(
    val allowed: Boolean,
    /** Human line, firm-but-safe. Empty when allowed. */
    val reason: String,
    /** When the next HARD session is allowed; null when allowed now. */
    val nextAllowedAt: Instant?,
    val rule: GuardRule = GuardRule.NONE,
) {
    companion object {
        val ALLOWED = GuardResult(true, "", null, GuardRule.NONE)
    }
}

data class TodayPlan(
    val recommendedModeId: String,
    val reasonLine: String,
    /** 1..8 (stays 8 after week 8). */
    val weekNumber: Int,
    val doneThisWeek: Int,
    val targetThisWeek: Int,
    /** True if the plan wanted a HARD session but the guard forced Base instead. */
    val downgradedByGuard: Boolean = false,
    /** Short label of this week's block, e.g. "BASE BLOCK", "BUILD BLOCK", "VO2 BLOCK". */
    val blockLabel: String = "",
)

/** Week target: [baseTarget] Base sessions + [qualityTarget] sessions of [qualityModeId]. */
data class WeekTarget(
    val baseTarget: Int,
    val qualityTarget: Int,
    val qualityModeId: String?,
) {
    val total: Int get() = baseTarget + qualityTarget
}

/** Pre-computed bundle for the UI: compute once per recomposition-relevant change. */
data class ProgressionSnapshot(
    val unlocks: List<UnlockState>,
    val hardGuard: GuardResult,
    val plan: TodayPlan,
) {
    fun unlockOf(modeId: String): UnlockState? = unlocks.firstOrNull { it.modeId == modeId }
}

/** Result of asking "can I press GO on this mode right now?". */
sealed class StartGate {
    object Allowed : StartGate()
    data class Locked(val unlock: UnlockState) : StartGate()
    data class Guarded(val guard: GuardResult) : StartGate()
}

class ProgressionEngine(
    private val modes: List<WorkoutMode> = Modes.all,
    private val forceUnlockAll: Boolean = false,
    /**
     * HARD classifier for ids that are NOT in [modes] (e.g. user-built "custom:<id>" workouts).
     * Used only by the safety guard; custom modes never take part in unlocks or the plan.
     */
    private val extraHard: (String) -> Boolean = { false },
) {
    companion object {
        const val UNLOCK_SESSIONS = 3
        const val UNLOCK_MIN_IN_ZONE_PCT = 60
        /** Mode index >= this is HARD (builder, norwegian, max). */
        const val HARD_FROM_INDEX = 2
        const val MAX_HARD_PER_7D = 2
        val MIN_HARD_GAP: Duration = Duration.ofHours(48)
        val WINDOW_7D: Duration = Duration.ofDays(7)
        val WEEK: Duration = Duration.ofDays(7)
        const val PLAN_WEEKS = 8

        /** End time of a session (start + elapsed). Used for all recency rules. */
        fun endMs(r: SessionRecord): Long = r.startedAtMs + r.elapsedSec.toLong() * 1000L

        /** "19 h", "2 d 4 h", "35 min". Always rounds up so we never promise too early. */
        fun formatWait(d: Duration): String {
            val totalMin = ((d.toMillis() + 59_999L) / 60_000L).coerceAtLeast(1L)
            if (totalMin < 60) return "$totalMin min"
            val totalH = (totalMin + 59) / 60
            if (totalH < 48) return "$totalH h"
            val days = totalH / 24
            val h = totalH % 24
            return if (h == 0L) "$days d" else "$days d $h h"
        }
    }

    private val baseId: String get() = modes.first().id

    fun indexOf(modeId: String): Int = modes.indexOfFirst { it.id == modeId }

    fun isHard(modeId: String): Boolean {
        val i = indexOf(modeId)
        return if (i >= 0) i >= HARD_FROM_INDEX else extraHard(modeId)
    }

    /** Only completed sessions of built-in modes count (custom "custom:<id>" sessions are ignored). */
    private fun completedKnown(history: List<SessionRecord>): List<SessionRecord> =
        history.filter { it.completed && indexOf(it.modeId) >= 0 }

    // ------------------------------------------------------------------ unlocks

    /**
     * Mode 0 always unlocked. Mode k is earned when mode k-1 has >= 3 completed sessions with
     * inZonePct >= 60. Unlocks are monotonic: if mode k is earned, every mode below k is unlocked
     * too (matters only after a force-unlock period was switched off again).
     */
    fun unlocks(history: List<SessionRecord>): List<UnlockState> {
        val done = completedKnown(history)
        val qualifying = IntArray(modes.size)
        done.forEach { r ->
            if (r.inZonePct >= UNLOCK_MIN_IN_ZONE_PCT) qualifying[indexOf(r.modeId)]++
        }
        var highestEarned = 0
        for (k in 1 until modes.size) {
            if (qualifying[k - 1] >= UNLOCK_SESSIONS) highestEarned = k
        }
        return modes.mapIndexed { k, m ->
            val earned = k <= highestEarned
            UnlockState(
                modeId = m.id,
                index = k,
                unlocked = earned || forceUnlockAll,
                prerequisiteModeId = if (k == 0) null else modes[k - 1].id,
                prerequisiteTitle = if (k == 0) null else modes[k - 1].title.substringAfter(". "),
                qualifying = if (k == 0) 0 else qualifying[k - 1],
                forced = !earned && forceUnlockAll,
            )
        }
    }

    fun isUnlocked(modeId: String, history: List<SessionRecord>): Boolean =
        unlocks(history).firstOrNull { it.modeId == modeId }?.unlocked ?: true // unknown/custom: never locked

    // ------------------------------------------------------------------ hard-session guard

    /**
     * Guard for HARD sessions (index >= 2, or [extraHard] custom ids). Base/Starter always allowed.
     * Force-unlock does NOT bypass the guard: it's a safety rule, not a progression rule.
     */
    fun guard(modeId: String, history: List<SessionRecord>, now: Instant): GuardResult =
        if (!isHard(modeId)) GuardResult.ALLOWED else hardGuard(history, now)

    /** Guard state that applies to every HARD mode. */
    fun hardGuard(history: List<SessionRecord>, now: Instant): GuardResult {
        val nowMs = now.toEpochMilli()
        val hard = history
            .filter { it.completed && isHard(it.modeId) }
            .map { endMs(it) }
            .filter { it <= nowMs }
            .sortedDescending()
        if (hard.isEmpty()) return GuardResult.ALLOWED

        // Rule 1: >= 48 h since the last HARD session.
        val gapUntil = hard.first() + MIN_HARD_GAP.toMillis()
        // Rule 2: < 2 HARD sessions in the trailing 7 days.
        val inWindow = hard.filter { it > nowMs - WINDOW_7D.toMillis() }
        // Allowed again once the window holds at most MAX-1 sessions, i.e. when the
        // (MAX_HARD_PER_7D)-th most recent one ages out.
        val windowUntil = if (inWindow.size >= MAX_HARD_PER_7D)
            inWindow[MAX_HARD_PER_7D - 1] + WINDOW_7D.toMillis() else Long.MIN_VALUE

        val gapBlocks = gapUntil > nowMs
        val windowBlocks = windowUntil > nowMs
        if (!gapBlocks && !windowBlocks) return GuardResult.ALLOWED

        val untilMs = maxOf(if (gapBlocks) gapUntil else Long.MIN_VALUE, if (windowBlocks) windowUntil else Long.MIN_VALUE)
        val until = Instant.ofEpochMilli(untilMs)
        val wait = formatWait(Duration.ofMillis(untilMs - nowMs))
        return if (windowBlocks && untilMs == windowUntil) {
            GuardResult(
                allowed = false,
                reason = "$MAX_HARD_PER_7D hard sessions in 7 days. That's the dose. " +
                    "Recovery is training. Come back in $wait.",
                nextAllowedAt = until,
                rule = GuardRule.MAX_2_PER_7D,
            )
        } else {
            val agoH = ((nowMs - hard.first()) / 3_600_000L).coerceAtLeast(0)
            GuardResult(
                allowed = false,
                reason = "Last hard session was $agoH h ago. Recovery is training. Come back in $wait.",
                nextAllowedAt = until,
                rule = GuardRule.MIN_GAP_48H,
            )
        }
    }

    /** One call for the GO button. Lock check first, then guard. */
    fun canStart(modeId: String, history: List<SessionRecord>, now: Instant): StartGate {
        val u = unlocks(history).firstOrNull { it.modeId == modeId }
        if (u != null && !u.unlocked) return StartGate.Locked(u)
        val g = guard(modeId, history, now)
        return if (g.allowed) StartGate.Allowed else StartGate.Guarded(g)
    }

    // ------------------------------------------------------------------ 8-week plan

    /** Raw weeks since first ever session, 1-based, unbounded. 1 when there is no history. */
    fun rawWeek(history: List<SessionRecord>, now: Instant): Int {
        val first = history.filter { indexOf(it.modeId) >= 0 }.minOfOrNull { it.startedAtMs } ?: return 1
        val elapsed = now.toEpochMilli() - first
        if (elapsed < 0) return 1
        return (elapsed / WEEK.toMillis()).toInt() + 1
    }

    /** Plan week 1..8; stays at 8 afterwards ("repeat at 8"). */
    fun planWeek(history: List<SessionRecord>, now: Instant): Int = rawWeek(history, now).coerceIn(1, PLAN_WEEKS)

    private fun highestUnlockedIn(unlocks: List<UnlockState>, range: IntRange): String? =
        unlocks.filter { it.index in range && it.unlocked }.maxByOrNull { it.index }?.modeId

    fun weekTarget(week: Int, unlocks: List<UnlockState>): WeekTarget = when (week) {
        1, 2 -> WeekTarget(baseTarget = 3, qualityTarget = 0, qualityModeId = null)
        3, 4 -> {
            val q = highestUnlockedIn(unlocks, 1..2)
            if (q == null) WeekTarget(3, 0, null) else WeekTarget(2, 1, q)
        }
        else -> {
            // 1-2x Base + 2x highest unlocked HARD. Target counts 1 Base; extra Base is a bonus.
            val q = highestUnlockedIn(unlocks, HARD_FROM_INDEX until modes.size)
                ?: highestUnlockedIn(unlocks, 1 until HARD_FROM_INDEX)
            if (q == null) WeekTarget(3, 0, null) else WeekTarget(1, 2, q)
        }
    }

    private fun blockLabel(week: Int) = when (week) {
        1, 2 -> "BASE BLOCK"
        3, 4 -> "BUILD BLOCK"
        else -> "VO2 BLOCK"
    }

    fun todayPlan(history: List<SessionRecord>, now: Instant): TodayPlan =
        todayPlan(history, now, unlocks(history), hardGuard(history, now))

    private fun todayPlan(
        history: List<SessionRecord>,
        now: Instant,
        unlocks: List<UnlockState>,
        hardGuard: GuardResult,
    ): TodayPlan {
        val raw = rawWeek(history, now)
        val week = raw.coerceIn(1, PLAN_WEEKS)
        val target = weekTarget(week, unlocks)

        val first = history.filter { indexOf(it.modeId) >= 0 }.minOfOrNull { it.startedAtMs }
        val weekStartMs = if (first == null) Long.MIN_VALUE else first + (raw - 1).toLong() * WEEK.toMillis()
        val thisWeek = completedKnown(history).filter { it.startedAtMs >= weekStartMs && it.startedAtMs <= now.toEpochMilli() }
        val baseDone = thisWeek.count { it.modeId == baseId }
        val qualityDone = thisWeek.count { it.modeId != baseId }
        val done = thisWeek.size
        val label = blockLabel(week)

        fun plan(modeId: String, reason: String, downgraded: Boolean = false) = TodayPlan(
            recommendedModeId = modeId,
            reasonLine = reason,
            weekNumber = week,
            doneThisWeek = done,
            targetThisWeek = target.total,
            downgradedByGuard = downgraded,
            blockLabel = label,
        )

        val q = target.qualityModeId
        val needQuality = q != null && qualityDone < target.qualityTarget
        val needBase = baseDone < target.baseTarget

        if (needQuality && q != null) {
            val qTitle = modes[indexOf(q)].title.substringAfter(". ")
            return if (isHard(q) && !hardGuard.allowed) {
                val wait = hardGuard.nextAllowedAt?.let { formatWait(Duration.between(now, it)) }
                plan(baseId, "Hard day blocked. Zone 2 today. ${qTitle} in ${wait ?: "a bit"}.", downgraded = true)
            } else {
                plan(q, "Week $week: ${qTitle} day. Earn it.")
            }
        }
        if (needBase) {
            val left = target.baseTarget - baseDone
            val line = when (week) {
                1, 2 -> "Week $week: build the engine. Easy pace, no ego. $left to go."
                else -> "Week $week: aerobic base. Slow is the work. $left to go."
            }
            return plan(baseId, line)
        }
        return plan(baseId, "Week $week done. Rest, or go easy. Nothing hard.")
    }

    /** Everything the home / detail screens need, computed once. */
    fun snapshot(history: List<SessionRecord>, now: Instant): ProgressionSnapshot {
        val u = unlocks(history)
        val g = hardGuard(history, now)
        return ProgressionSnapshot(u, g, todayPlan(history, now, u, g))
    }
}
