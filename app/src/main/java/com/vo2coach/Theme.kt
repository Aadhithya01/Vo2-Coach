package com.vo2coach

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

/**
 * "Pro sport" palette. Object name kept as Neon so existing code keeps compiling;
 * values are now a restrained charcoal + single orange accent + standard HR-zone colours.
 */
object Neon {
    val Black = Color(0xFF0E0E12)   // background (charcoal)
    val Card = Color(0xFF1C1C22)    // surfaces
    val Dim = Color(0xFFA1A1AA)     // secondary text
    val Hot = Color(0xFFFF3B30)     // Z5 / danger / stop
    val Orange = Color(0xFFFF6A1A)  // brand accent
    val Yellow = Color(0xFFFF9F0A)  // Z4 / warm-up
    val Lime = Color(0xFF34C759)    // Z3 / in zone / success
    val Cyan = Color(0xFF3D9BFF)    // Z2 / recovery
    val Blue = Color(0xFF3D9BFF)
    val Violet = Color(0xFF8E8E93)  // neutral (cool-down, custom)
    val Magenta = Color(0xFFBF5AF2)
}

/** Brand accent alias for new code. */
val Accent: Color get() = Neon.Orange

fun phaseColors(t: PhaseType): Pair<Color, Color> = when (t) {
    PhaseType.WORK -> Neon.Orange to Neon.Orange
    PhaseType.RECOVERY -> Neon.Cyan to Neon.Cyan
    PhaseType.WARMUP -> Neon.Yellow to Neon.Yellow
    PhaseType.COOLDOWN -> Neon.Violet to Neon.Violet
}

fun phaseWord(t: PhaseType): String = when (t) {
    PhaseType.WORK -> "GO"
    PhaseType.RECOVERY -> "RECOVER"
    PhaseType.WARMUP -> "WARM UP"
    PhaseType.COOLDOWN -> "COOL DOWN"
}

/** Difficulty ladder follows the HR-zone colours: green → blue → amber → orange → red. */
fun levelColor(index: Int): Color = when (index) {
    0 -> Neon.Lime
    1 -> Neon.Cyan
    2 -> Neon.Yellow
    3 -> Neon.Orange
    else -> Neon.Hot
}

fun zoneColor(z: Zone): Color = when (z) {
    Zone.IN -> Neon.Lime
    Zone.LOW -> Neon.Cyan
    Zone.HIGH -> Neon.Hot
    Zone.UNKNOWN -> Neon.Dim
}

/** Default text is Medium weight so nothing renders thin. */
private val AppTypography = Typography(
    display1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp),
    title1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    title2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    title3 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    body1 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    body2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    button = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    caption1 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    caption2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    caption3 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = AppTypography, content = content)
}
