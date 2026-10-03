package com.vo2coach.voice

import com.vo2coach.Hype
import com.vo2coach.Phase
import com.vo2coach.PhaseType
import com.vo2coach.SessionState
import com.vo2coach.Zone

enum class CueKind { PHASE, MILESTONE, ZONE, HYPE, DONE }

/**
 * One thing to say.
 * [flush] = interrupt whatever is being said (TextToSpeech.QUEUE_FLUSH); otherwise QUEUE_ADD.
 */
data class Cue(val text: String, val kind: CueKind, val flush: Boolean = false)

/**
 * Pure planner: (previous SessionState, current SessionState) -> cues to speak.
 * No Android dependencies. Holds only throttle bookkeeping; time comes from [clock] (ms).
 *
 * Rules:
 * - Phase changes / session start / done / pause / resume: always, QUEUE_FLUSH.
 * - "Halfway." (WORK phases >= 2 min) and "Ten seconds." (phases >= 45 s, not cool-down): always, QUEUE_ADD.
 * - Hype + zone alerts share one channel: at most one line per [hypeGapMs] (25 s),
 *   never within [quietAfterAnnounceMs] of an announcement, never in the last 14 s of a phase,
 *   never while paused, never the same line twice in a row.
 *   Priority: HR too high (safe copy) > too low in WORK > generic hype.
 */
class VoiceCuePlanner(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val hypeGapMs: Long = 25_000L,
    private val quietAfterAnnounceMs: Long = 5_000L,
) {
    private var lastHypeAtMs: Long = Long.MIN_VALUE / 2
    private var lastAnnounceAtMs: Long = Long.MIN_VALUE / 2
    private var lastHypeText: String? = null
    private var recoveryHypePhaseIndex: Int = -1

    fun reset() {
        lastHypeAtMs = Long.MIN_VALUE / 2
        lastAnnounceAtMs = Long.MIN_VALUE / 2
        lastHypeText = null
        recoveryHypePhaseIndex = -1
    }

    fun plan(prev: SessionState?, cur: SessionState): List<Cue> = plan(prev, cur, clock())

    fun plan(prev: SessionState?, cur: SessionState, nowMs: Long): List<Cue> {
        val p = prev ?: SessionState()

        // ---- session finished
        if (cur.finished && !p.finished) {
            lastAnnounceAtMs = nowMs
            return listOf(Cue(doneText(cur), CueKind.DONE, flush = true))
        }
        if (!cur.active) {
            // idle, or stopped by the user (prev.active && !cur.active): say nothing.
            return emptyList()
        }
        val phase = cur.phase ?: return emptyList()

        // ---- session start or phase change
        val started = !p.active
        if (started) reset()
        if (started || p.phaseIndex != cur.phaseIndex || p.phase == null) {
            lastAnnounceAtMs = nowMs
            return listOf(Cue(phaseText(phase), CueKind.PHASE, flush = true))
        }

        // ---- pause / resume
        if (cur.paused && !p.paused) {
            lastAnnounceAtMs = nowMs
            return listOf(Cue("Paused.", CueKind.PHASE, flush = true))
        }
        if (!cur.paused && p.paused) {
            lastAnnounceAtMs = nowMs
            return listOf(Cue("Back to it.", CueKind.PHASE, flush = true))
        }
        if (cur.paused) return emptyList()

        // ---- milestones within the same phase (crossing detection, robust to skipped ticks)
        val half = phase.seconds / 2
        if (phase.type == PhaseType.WORK && phase.seconds >= 120 &&
            p.remainingSec > half && cur.remainingSec <= half
        ) {
            lastAnnounceAtMs = nowMs
            return listOf(Cue("Halfway.", CueKind.MILESTONE))
        }
        if (phase.type != PhaseType.COOLDOWN && phase.seconds >= 45 &&
            p.remainingSec > 10 && cur.remainingSec in 1..10
        ) {
            lastAnnounceAtMs = nowMs
            val text = if (phase.type == PhaseType.WORK) "Ten seconds." else "Ten seconds. Get ready."
            return listOf(Cue(text, CueKind.MILESTONE))
        }

        // ---- hype / zone channel
        return listOfNotNull(hypeCue(cur, phase, nowMs))
    }

    private fun hypeCue(s: SessionState, phase: Phase, nowMs: Long): Cue? {
        if (nowMs - lastHypeAtMs < hypeGapMs) return null
        if (nowMs - lastAnnounceAtMs < quietAfterAnnounceMs) return null
        if (s.remainingSec <= 14) return null

        val elapsed = phase.seconds - s.remainingSec
        val seed = s.phaseIndex * 7 + elapsed / 15
        val alertable = phase.type == PhaseType.WORK || phase.type == PhaseType.RECOVERY
        val grace = if (phase.type == PhaseType.WORK) 30 else 45 // same as WorkoutService haptics

        val cue: Cue? = when {
            alertable && elapsed > grace && s.zone == Zone.HIGH ->
                Cue(Hype.pick(Hype.tooHigh, seed), CueKind.ZONE)

            phase.type == PhaseType.WORK && elapsed > grace && s.zone == Zone.LOW ->
                Cue(Hype.pick(Hype.slacking, seed), CueKind.ZONE)

            // Never push harder while HR is above target (even inside the grace period).
            s.zone == Zone.HIGH -> null

            phase.type == PhaseType.WORK && elapsed >= 12 -> {
                val line = Hype.forSession(s)
                if (line.isBlank()) null else Cue(line, CueKind.HYPE)
            }

            phase.type == PhaseType.RECOVERY && phase.seconds >= 60 && elapsed >= 8 &&
                recoveryHypePhaseIndex != s.phaseIndex -> {
                recoveryHypePhaseIndex = s.phaseIndex
                Cue(Hype.pick(Hype.recovery, seed), CueKind.HYPE)
            }

            else -> null // warm-up / cool-down stay quiet beyond the announcement
        }

        if (cue == null || cue.text == lastHypeText) return null
        lastHypeAtMs = nowMs
        lastHypeText = cue.text
        return cue
    }

    companion object {
        private val SLASH = Regex("""(\d+)\s*/\s*(\d+)""")
        private val SET_REP = Regex("""^Set\s+(\d+)\s+(\d+)\s*/\s*(\d+)""")

        /** Spoken announcement for the start of [p]. Pure; exposed for tests. */
        fun phaseText(p: Phase): String = when (p.type) {
            PhaseType.WARMUP -> "Warm up. ${duration(p.seconds)}."
            PhaseType.COOLDOWN -> "Cool down. ${duration(p.seconds)}. Good work."
            PhaseType.RECOVERY -> {
                val name = p.label.trim().ifBlank { "Recover" }
                if (p.seconds < 60) "$name." else "$name. ${duration(p.seconds)}."
            }
            PhaseType.WORK -> workText(p)
        }

        private fun workText(p: Phase): String {
            SET_REP.find(p.label)?.let { m ->
                val (set, rep, of) = m.destructured
                return when {
                    rep == "1" -> "Set $set. Go!"
                    rep == of -> "Last rep. Go!"
                    else -> "Rep $rep. Go!"
                }
            }
            SLASH.find(p.label)?.let { m ->
                val (k, n) = m.destructured
                return if (k == n) "Interval $k of $n. Last one. Go!" else "Interval $k of $n. Go!"
            }
            val name = p.label.trim().ifBlank { "Work" }
            return if (p.seconds < 60) "$name. Go!" else "$name. ${duration(p.seconds)}. Go!"
        }

        /** 15 -> "15 seconds", 60 -> "1 minute", 180 -> "3 minutes", 150 -> "2 minutes 30". */
        fun duration(sec: Int): String {
            val m = sec / 60
            val s = sec % 60
            return when {
                m == 0 -> "$s seconds"
                s == 0 -> if (m == 1) "1 minute" else "$m minutes"
                else -> "${if (m == 1) "1 minute" else "$m minutes"} $s"
            }
        }

        fun doneText(s: SessionState): String {
            if (s.workSec <= 0) return "Done."
            val pct = s.workInZoneSec * 100 / s.workSec
            return "Done. $pct percent in zone. ${Hype.verdict(pct)}"
        }
    }
}
