package com.vo2coach.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.vo2coach.Neon

private val TnumHrr = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)

fun hrrColor(r: HrrRating): Color = when (r) {
    HrrRating.POOR -> Neon.Hot
    HrrRating.AVERAGE -> Neon.Yellow
    HrrRating.GOOD -> Neon.Lime
    HrrRating.ELITE -> Neon.Cyan
}

/** "-12" / "+3" (HR rose) style for a drop value. */
private fun dropText(drop: Int): String = if (drop >= 0) "-$drop" else "+${-drop}"

/**
 * Small live chip shown during RECOVERY / COOLDOWN while the 60 s HRR window runs.
 * Renders nothing when [liveHrDrop] is null.
 *
 * @param liveHrDrop    SessionState.liveHrDrop (hrAtEnd - HR now, bpm)
 * @param secondsLeft   SessionState.hrrSecondsLeft (60..1 while measuring, 0 = locked final value)
 */
@Composable
fun HrrChip(liveHrDrop: Int?, secondsLeft: Int?, modifier: Modifier = Modifier) {
    val drop = liveHrDrop ?: return
    val locked = secondsLeft == 0
    val c = if (locked) hrrColor(HrrRating.of(drop)) else Neon.Cyan
    Row(
        modifier = modifier
            .background(c.copy(alpha = 0.16f), RoundedCornerShape(50))
            .border(2.dp, c.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("HRR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c, letterSpacing = 1.sp)
        Spacer(Modifier.width(5.dp))
        Text(dropText(drop), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, style = TnumHrr)
        Spacer(Modifier.width(5.dp))
        Text(
            if (locked) "60S ✓" else "${secondsLeft ?: "--"}S",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neon.Dim, style = TnumHrr,
        )
    }
}

/**
 * Finish-screen row: average 60 s HR drop + rating. Shows a neutral "NO DATA" line when
 * nothing could be measured (e.g. no HR, or only short rests).
 *
 * @param avg     SessionState.avgHrDrop60s (or SessionRecord.avgHrDrop60s)
 * @param rating  HrrRating.of(avg), or null when avg is null
 */
@Composable
fun FinishHrrRow(avg: Int?, rating: HrrRating?, modifier: Modifier = Modifier) {
    if (avg == null || rating == null) {
        Text(
            "HRR  NO DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neon.Dim,
            letterSpacing = 1.sp, modifier = modifier,
        )
        return
    }
    val c = hrrColor(rating)
    Column(modifier = modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("HRR 60S", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neon.Dim, letterSpacing = 1.sp)
            Spacer(Modifier.width(6.dp))
            Text(dropText(avg), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c, style = TnumHrr)
            Spacer(Modifier.width(6.dp))
            Text(rating.short.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
        }
        if (rating == HrrRating.POOR || rating == HrrRating.ELITE) {
            Text(
                rating.detail.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier.width(150.dp),
            )
        }
    }
}
