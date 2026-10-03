package com.vo2coach.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.vo2coach.Modes
import com.vo2coach.Neon
import com.vo2coach.WorkoutMode
import com.vo2coach.levelColor

private val Tnum = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)

@Composable
private fun PillBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Neon.Black, letterSpacing = 1.sp)
    }
}

/** Week progress dots: filled = done, ring = to do. Capped at 6 dots. */
@Composable
private fun WeekDots(done: Int, target: Int, color: Color) {
    val n = maxOf(target, done).coerceIn(1, 6)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until n) {
            val filled = i < done
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (filled) color else Color.Transparent)
                    .border(2.dp, if (filled) color else Neon.Dim, CircleShape)
            )
        }
    }
}

/**
 * Top-of-home card. Tap anywhere (or START) -> onStart(); the integrator maps that to
 * `selected = Modes.byId(plan.recommendedModeId)`.
 */
@Composable
fun TodayPlanCard(plan: TodayPlan, onStart: () -> Unit) {
    val mode: WorkoutMode? = Modes.byId(plan.recommendedModeId)
    val idx = mode?.let { Modes.all.indexOf(it) } ?: 0
    val c = if (plan.downgradedByGuard) Neon.Cyan else levelColor(idx)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.28f), Neon.Card)))
            .border(2.dp, c, shape)
            .clickable(onClick = onStart)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "TODAY · WEEK ${plan.weekNumber}/${ProgressionEngine.PLAN_WEEKS}",
            fontSize = 11.sp, color = Neon.Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, style = Tnum,
        )
        Text(
            (mode?.title?.substringAfter(". ") ?: plan.recommendedModeId).uppercase(),
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (plan.blockLabel.isNotEmpty()) {
            Text(plan.blockLabel, fontSize = 11.sp, color = c, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(
            plan.reasonLine.uppercase(),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center, maxLines = 3,
            modifier = Modifier.padding(top = 3.dp, bottom = 5.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekDots(plan.doneThisWeek, plan.targetThisWeek, c)
            Text(
                "${plan.doneThisWeek}/${plan.targetThisWeek}",
                fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold, style = Tnum,
            )
        }
        Spacer(Modifier.height(6.dp))
        PillBtn("START", c, onStart)
    }
}

/** Small "LOCKED" chip (text only, no emoji/icon font dependency). */
@Composable
fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Neon.Black.copy(alpha = 0.8f))
            .border(2.dp, Neon.Dim, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("LOCKED", fontSize = 11.sp, color = Neon.Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Unlock condition line, e.g. "UNLOCK: 3x BASE ≥60% · 1/3". Empty for unlocked modes. */
@Composable
fun UnlockConditionText(unlock: UnlockState, modifier: Modifier = Modifier) {
    if (unlock.unlocked) return
    Text(
        "UNLOCK: ${unlock.conditionLine}",
        fontSize = 11.sp, color = Neon.Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        textAlign = TextAlign.Center, style = Tnum, modifier = modifier,
    )
}

/**
 * Drop-in replacement for ModeCard when the mode is locked: same shape/size as ModeCard,
 * greyed out, lock badge + unlock condition. Still clickable so the detail screen can explain.
 */
@Composable
fun LockedModeCard(mode: WorkoutMode, unlock: UnlockState, onClick: () -> Unit) {
    val c = levelColor(unlock.index)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(shape)
            .background(Neon.Card)
            .border(2.dp, c.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                mode.title.substringAfter(". "),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.45f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                unlock.conditionLine,
                fontSize = 11.sp, color = c.copy(alpha = 0.7f), fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, style = Tnum, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        LockBadge()
    }
}

/**
 * Generic overlay to put over any card inside a Box:
 * `Box { ModeCard(...); if (!u.unlocked) LockedOverlay(u, Modifier.matchParentSize()) }`.
 */
@Composable
fun LockedOverlay(unlock: UnlockState, modifier: Modifier = Modifier) {
    if (unlock.unlocked) return
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Neon.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LockBadge()
            UnlockConditionText(unlock, Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Full-screen blocking overlay (same pattern as the quit-confirm in SessionScreen).
 * Place it LAST inside the root Box so it draws on top.
 */
@Composable
fun GuardDialog(reason: String, onOk: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Neon.Black.copy(alpha = 0.94f)).clickable(onClick = onOk),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp)) {
            Text("RECOVERY DAY", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Neon.Cyan, letterSpacing = 1.sp)
            Text(
                reason.uppercase(),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "ADAPTATION HAPPENS AT REST.",
                fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            PillBtn("OK", Neon.Cyan, onOk)
        }
    }
}

/** Same overlay for a locked mode's GO. */
@Composable
fun LockedDialog(unlock: UnlockState, onOk: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Neon.Black.copy(alpha = 0.94f)).clickable(onClick = onOk),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp)) {
            Text("LOCKED.", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Hot)
            Text(
                "EARN IT FIRST.",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                unlock.conditionLine,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = levelColor((unlock.index - 1).coerceAtLeast(0)),
                textAlign = TextAlign.Center, style = Tnum, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            PillBtn("OK", Neon.Dim, onOk)
        }
    }
}

/** Convenience: render whichever dialog a non-Allowed StartGate needs. */
@Composable
fun StartGateDialog(gate: StartGate, onOk: () -> Unit) {
    when (gate) {
        is StartGate.Allowed -> Unit
        is StartGate.Locked -> LockedDialog(gate.unlock, onOk)
        is StartGate.Guarded -> GuardDialog(gate.guard.reason, onOk)
    }
}
