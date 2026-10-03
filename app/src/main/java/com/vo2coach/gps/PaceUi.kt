package com.vo2coach.gps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GpsFixed
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.WidgetCard
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.vo2coach.Neon

private val Tnum = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)

/**
 * Compact pace + distance row for SessionScreen, e.g.  5'12" /KM · 2.34 KM
 * Width-bounded (~150 dp) so it fits inside the round ring.
 */
@Composable
fun PaceReadout(paceSecPerKm: Int?, distanceM: Double?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.widthIn(max = 150.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            PaceMath.formatPace(paceSecPerKm),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (paceSecPerKm != null) Neon.Cyan else Neon.Dim,
            style = Tnum,
            maxLines = 1,
        )
        Text(" /KM", fontSize = 11.sp, color = Neon.Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Text("  ·  ", fontSize = 11.sp, color = Neon.Dim, maxLines = 1)
        Text(
            PaceMath.formatDistance(distanceM),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = Tnum,
            maxLines = 1,
        )
    }
}

/** Small GPS indicator: dot + label. ACQUIRING blinks yellow, OK lime, LOST hot, OFF dim. */
@Composable
fun GpsStatusIndicator(status: GpsStatus, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        GpsStatus.OK -> Neon.Lime to "GPS"
        GpsStatus.ACQUIRING -> Neon.Yellow to "GPS…"
        GpsStatus.LOST -> Neon.Hot to "NO GPS"
        GpsStatus.OFF -> Neon.Dim to "INDOOR"
    }
    val blink = rememberInfiniteTransition(label = "gps")
    val a by blink.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "gpsA",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(if (status == GpsStatus.ACQUIRING) a else 1f)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
    }
}

/**
 * Outdoor/indoor toggle chip for the home list or DetailScreen.
 * Wire: `OutdoorToggle(outdoor) { outdoor = it; RunSettings(ctx).outdoorGps = it }`.
 */
@Composable
fun OutdoorToggle(outdoor: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = if (outdoor) Neon.Lime else Neon.Cyan
    WidgetCard(c, modifier = modifier, onClick = { onChange(!outdoor) }) {
        CardHeader(
            if (outdoor) Icons.Filled.GpsFixed else Icons.Filled.FitnessCenter,
            if (outdoor) "Outdoor" else "Treadmill",
            c,
            badge = if (outdoor) "GPS" else null,
        )
    }
}

/** Finish/detail screen block: distance + avg pace. Renders nothing when distance is null. */
@Composable
fun RunSummary(distanceM: Double?, avgPaceSecPerKm: Int?, modifier: Modifier = Modifier) {
    if (distanceM == null) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DISTANCE", fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp)
            Text(PaceMath.formatDistance(distanceM), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, style = Tnum)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AVG PACE", fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp)
            Text(PaceMath.formatPace(avgPaceSecPerKm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Neon.Cyan, style = Tnum)
        }
    }
}
