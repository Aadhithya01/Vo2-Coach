package com.vo2coach

import kotlin.math.abs

/** Brutal (not abusive) coaching lines. "Too high" stays firm-but-safe on purpose. */
object Hype {
    val warmup = listOf(
        "Warm up. Then war.",
        "Loosen up. It gets ugly soon.",
        "Enjoy this. It's the easy part.",
    )
    val workStart = listOf(
        "GO. NOW.",
        "No excuses. Move.",
        "Pain is the price. Pay it.",
        "Hurt now or stay average.",
        "Your couch misses you. Too bad.",
    )
    val workMid = listOf(
        "Is that all you've got?",
        "Stop negotiating with yourself.",
        "Comfort is the enemy.",
        "Nobody's coming to save you.",
        "Average is a choice. Don't.",
        "Your legs are lying. Keep going.",
        "You said you wanted this.",
        "Soft gets nothing.",
    )
    val workEnd = listOf(
        "FINISH IT.",
        "Don't you dare slow down.",
        "Last seconds. Empty the tank.",
        "Quitters stop here. Not you.",
    )
    val slacking = listOf(
        "That's a jog. Pick it UP.",
        "Your heart says you're coasting.",
        "Too easy. Faster.",
        "You're cheating yourself. Push.",
        "Lazy. Go harder.",
    )
    val tooHigh = listOf(
        "Too hot. Ease off a little.",
        "Control it. Back off slightly.",
        "Above target. Settle the pace.",
    )
    val recovery = listOf(
        "Breathe. Next one's worse.",
        "Rest. Don't get comfortable.",
        "Recover. You're not done.",
        "Shake it out. Round's coming.",
    )
    val cooldown = listOf(
        "Walk it off. You earned this.",
        "Done suffering. For today.",
    )
    val paused = listOf(
        "The clock's waiting on your excuses.",
        "Paused. Your goals aren't.",
    )
    val quit = listOf(
        "Quitting already?",
        "Weak move. Sure?",
        "Future you is watching.",
    )
    val countdown = listOf(
        "No turning back.",
        "Lock in.",
        "Time to suffer.",
    )

    fun pick(list: List<String>, seed: Int): String = list[abs(seed) % list.size]

    fun forSession(s: SessionState): String {
        val p = s.phase ?: return ""
        val elapsed = p.seconds - s.remainingSec
        val bucket = elapsed / 15
        val seed = s.phaseIndex * 7 + bucket
        return when {
            s.paused -> pick(paused, s.phaseIndex)
            s.zone == Zone.HIGH && elapsed > 30 -> pick(tooHigh, seed)
            p.type == PhaseType.WORK && s.zone == Zone.LOW && elapsed > 30 -> pick(slacking, seed)
            p.type == PhaseType.WORK && s.remainingSec <= 15 -> pick(workEnd, s.phaseIndex)
            p.type == PhaseType.WORK && elapsed < 10 -> pick(workStart, s.phaseIndex)
            p.type == PhaseType.WORK -> pick(workMid, seed)
            p.type == PhaseType.RECOVERY -> pick(recovery, seed)
            p.type == PhaseType.WARMUP -> pick(warmup, bucket / 4)
            else -> pick(cooldown, bucket / 4)
        }
    }

    fun verdict(inZonePct: Int): String = when {
        inZonePct >= 70 -> "That's how engines get built."
        inZonePct >= 40 -> "Decent. Not great. Again in 3 days."
        else -> "You coasted. Be honest. Redo it."
    }
}
