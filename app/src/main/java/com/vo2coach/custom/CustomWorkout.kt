package com.vo2coach.custom

import com.vo2coach.Phase
import com.vo2coach.PhaseType
import com.vo2coach.WorkoutMode
import java.util.UUID

/**
 * Feature #6 — user-built interval workouts. Pure Kotlin (no Android imports) so it can be unit-tested on the JVM.
 *
 * lowPct/highPct = target HR range as a fraction of max HR (same convention as [Phase]).
 */
enum class WorkIntensity(val label: String, val lowPct: Double, val highPct: Double) {
    MODERATE("MODERATE", 0.80, 0.90),
    HARD("HARD", 0.85, 0.92),
    VO2("VO2", 0.90, 0.95),
    MAX("MAX", 0.90, 1.00);

    /** VO2 / MAX are the "red line" intensities that get extra safety validation. */
    val isRedLine: Boolean get() = this == VO2 || this == MAX
}

enum class RecoveryType(val label: String, val lowPct: Double, val highPct: Double) {
    JOG("JOG", 0.60, 0.70),
    EASY("EASY", 0.65, 0.75),
    WALK("WALK", 0.50, 0.65),
}

/** Inclusive bounds + step sizes used by the builder steppers and by [CustomWorkout.clamped]. */
object CustomBounds {
    const val MAX_SAVED = 20

    const val REPS_MIN = 1
    const val REPS_MAX = 20
    const val REPS_STEP = 1

    const val WORK_MIN_SEC = 15
    const val WORK_MAX_SEC = 600
    const val WORK_STEP_SEC = 15

    const val REC_MIN_SEC = 15
    const val REC_MAX_SEC = 600
    const val REC_STEP_SEC = 15

    const val WARMUP_MIN = 0
    const val WARMUP_MAX = 20
    const val COOLDOWN_MIN = 0
    const val COOLDOWN_MAX = 20
    const val MIN_STEP = 1

    /** VO2/MAX reps longer than this are refused. */
    const val RED_LINE_MAX_WORK_SEC = 300

    /** Snap [v] to the nearest multiple of [step] (anchored at [min]) and clamp into [min]..[max]. */
    fun snap(v: Int, min: Int, max: Int, step: Int): Int {
        val c = v.coerceIn(min, max)
        val k = Math.round((c - min).toDouble() / step).toInt()
        return (min + k * step).coerceIn(min, max)
    }

    /** One stepper press: move by [dir] (-1 / +1) steps, clamped. */
    fun step(v: Int, dir: Int, min: Int, max: Int, step: Int): Int =
        snap(v + dir * step, min, max, step)
}

data class CustomWorkout(
    val id: String,
    val name: String,
    val warmupMin: Int = 10,
    val reps: Int = 4,
    val workSec: Int = 180,
    val workIntensity: WorkIntensity = WorkIntensity.HARD,
    val recoverySec: Int = 120,
    val recoveryType: RecoveryType = RecoveryType.JOG,
    val cooldownMin: Int = 5,
) {
    val modeId: String get() = MODE_PREFIX + id

    /** Phases built exactly like Modes.intervals: no recovery after the last rep; 0-min warm-up/cool-down omitted. */
    fun phases(): List<Phase> = buildList {
        if (warmupMin > 0) add(Phase("Warm-up", PhaseType.WARMUP, warmupMin * 60, 0.50, 0.65))
        for (i in 1..reps) {
            add(Phase("Work $i/$reps", PhaseType.WORK, workSec, workIntensity.lowPct, workIntensity.highPct))
            if (i < reps) {
                add(Phase("Recover", PhaseType.RECOVERY, recoverySec, recoveryType.lowPct, recoveryType.highPct))
            }
        }
        if (cooldownMin > 0) add(Phase("Cool-down", PhaseType.COOLDOWN, cooldownMin * 60, 0.50, 0.60))
    }

    val totalSeconds: Int
        get() = warmupMin * 60 + reps * workSec + (reps - 1).coerceAtLeast(0) * recoverySec + cooldownMin * 60

    /** e.g. "4 x 3:00 HARD, 2:00 JOG." — shown as the WorkoutMode summary. */
    fun summary(): String {
        val rec = if (reps > 1) ", ${fmtDuration(recoverySec)} ${recoveryType.label.lowercase()}" else ""
        return "$reps x ${fmtDuration(workSec)} ${workIntensity.label.lowercase()}$rec. Built by you."
    }

    fun toWorkoutMode(): WorkoutMode = WorkoutMode(
        id = modeId,
        title = name.ifBlank { autoName() },
        level = LEVEL,
        summary = summary(),
        phases = phases(),
    )

    /** Auto name, e.g. "4x3:00 HARD". Never contains ". " (the UI strips everything before ". " in titles). */
    fun autoName(): String = "${reps}x${fmtDuration(workSec)} ${workIntensity.label}"

    /** All fields forced into [CustomBounds]; blank name replaced by [autoName]. Used on load and before save. */
    fun clamped(): CustomWorkout {
        val c = copy(
            warmupMin = warmupMin.coerceIn(CustomBounds.WARMUP_MIN, CustomBounds.WARMUP_MAX),
            reps = reps.coerceIn(CustomBounds.REPS_MIN, CustomBounds.REPS_MAX),
            workSec = CustomBounds.snap(workSec, CustomBounds.WORK_MIN_SEC, CustomBounds.WORK_MAX_SEC, CustomBounds.WORK_STEP_SEC),
            recoverySec = CustomBounds.snap(recoverySec, CustomBounds.REC_MIN_SEC, CustomBounds.REC_MAX_SEC, CustomBounds.REC_STEP_SEC),
            cooldownMin = cooldownMin.coerceIn(CustomBounds.COOLDOWN_MIN, CustomBounds.COOLDOWN_MAX),
        )
        val n = c.name.trim().replace(". ", " ").take(MAX_NAME_LEN)
        return c.copy(name = n.ifBlank { c.autoName() })
    }

    companion object {
        const val MODE_PREFIX = "custom:"
        const val LEVEL = "Custom"
        const val MAX_NAME_LEN = 24

        fun isCustomModeId(modeId: String?): Boolean = modeId != null && modeId.startsWith(MODE_PREFIX)

        /** "custom:ab12cd34" -> "ab12cd34"; null for non-custom ids. */
        fun customIdOf(modeId: String?): String? =
            if (isCustomModeId(modeId)) modeId!!.removePrefix(MODE_PREFIX).ifBlank { null } else null

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

        /** Default template for "NEW": a Builder-like 4 x 3:00 hard / 2:00 jog. */
        fun template(id: String = newId()): CustomWorkout {
            val w = CustomWorkout(id = id, name = "")
            return w.copy(name = w.autoName())
        }

        fun fmtDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
    }
}

// ------------------------------------------------------------------ validation

enum class CustomIssue {
    /** VO2/MAX rep longer than [CustomBounds.RED_LINE_MAX_WORK_SEC]. */
    RED_LINE_WORK_TOO_LONG,
    /** VO2/MAX with recovery shorter than half the work time. */
    RED_LINE_RECOVERY_TOO_SHORT,
}

object CustomValidator {

    /** Empty list = OK to save/start. Any issue blocks saving. */
    fun validate(w: CustomWorkout): List<CustomIssue> = buildList {
        if (!w.workIntensity.isRedLine) return@buildList
        if (w.workSec > CustomBounds.RED_LINE_MAX_WORK_SEC) add(CustomIssue.RED_LINE_WORK_TOO_LONG)
        // reps == 1 has no recovery phase, so the ratio does not apply.
        if (w.reps > 1 && w.recoverySec * 2 < w.workSec) add(CustomIssue.RED_LINE_RECOVERY_TOO_SHORT)
    }

    fun isValid(w: CustomWorkout): Boolean = validate(w).isEmpty()

    /** Minimum recovery (multiple of 15 s) that satisfies the red-line rule for [workSec]. */
    fun minRecoveryFor(workSec: Int): Int {
        val half = (workSec + 1) / 2 // ceil(work / 2)
        val step = CustomBounds.REC_STEP_SEC
        return (((half + step - 1) / step) * step).coerceIn(CustomBounds.REC_MIN_SEC, CustomBounds.REC_MAX_SEC)
    }

    /** Firm-but-safe copy. Brutal about effort, never about safety. */
    fun message(issue: CustomIssue, w: CustomWorkout): String = when (issue) {
        CustomIssue.RED_LINE_WORK_TOO_LONG ->
            "${w.workIntensity.label} reps cap at 5:00. Past that it's not VO2 work, it's a blow-up. Cut the time or drop the intensity."
        CustomIssue.RED_LINE_RECOVERY_TOO_SHORT ->
            "Recovery too short. At ${w.workIntensity.label} you need at least ${CustomWorkout.fmtDuration(minRecoveryFor(w.workSec))} between reps. Rest is part of the work."
    }

    /** Whole-workout one-liner for the builder; null when valid. */
    fun firstMessage(w: CustomWorkout): String? = validate(w).firstOrNull()?.let { message(it, w) }

    /** Returns a copy that passes validation by the smallest change (used by the "FIX IT" button). */
    fun autoFix(w: CustomWorkout): CustomWorkout {
        var f = w
        if (f.workIntensity.isRedLine && f.workSec > CustomBounds.RED_LINE_MAX_WORK_SEC) {
            f = f.copy(workSec = CustomBounds.RED_LINE_MAX_WORK_SEC)
        }
        if (f.workIntensity.isRedLine && f.reps > 1 && f.recoverySec * 2 < f.workSec) {
            f = f.copy(recoverySec = minRecoveryFor(f.workSec))
        }
        return f
    }
}
