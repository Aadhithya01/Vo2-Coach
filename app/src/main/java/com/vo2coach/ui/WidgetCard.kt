package com.vo2coach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

/*
 * Widget-style card system with motion:
 *  - cards fade/slide in with a stagger (index), spring-scale when pressed
 *  - bars and segments fill from zero, numbers count up
 *  - LiveBackground: a slowly drifting colour glow behind every screen
 */

private val CardBase = Color(0xFF131317)
private val CardShape = RoundedCornerShape(26.dp)
private val Muted = Color.White.copy(alpha = 0.55f)
private val NumStyle = TextStyle(fontFeatureSettings = "tnum")
private val GlassBorder = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)))

private fun tintBrush(color: Color, k: Float = 1f) = Brush.linearGradient(
    listOf(color.copy(alpha = 0.06f * k), color.copy(alpha = 0.40f * k)),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/** Entrance (fade + rise, staggered by [index]) and press (spring scale) in one graphicsLayer. */
@Composable
private fun rememberMotion(index: Int, onClick: (() -> Unit)?): Pair<Modifier, Modifier> {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 8) * 70L)
        appear.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val layer = Modifier.graphicsLayer {
        val a = appear.value
        alpha = a
        translationY = (1f - a) * 28.dp.toPx()
        val sc = press * (0.94f + 0.06f * a)
        scaleX = sc; scaleY = sc
    }
    val click = if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier
    return layer to click
}

/** Tinted glass card. */
@Composable
fun WidgetCard(
    color: Color,
    modifier: Modifier = Modifier,
    index: Int = 0,
    widthFraction: Float = 0.94f,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (layer, click) = rememberMotion(index, onClick)
    val c by animateColorAsState(color, tween(500), label = "card")
    Column(
        modifier
            .then(layer)
            .fillMaxWidth(widthFraction)
            .clip(CardShape)
            .background(CardBase)
            .background(tintBrush(c, if (dim) 0.45f else 1f))
            .border(1.5.dp, GlassBorder, CardShape)
            .then(click)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .graphicsLayer { alpha = if (dim) 0.7f else 1f },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** Icon + title on the left, optional small badge on the right. */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    color: Color,
    badge: String? = null,
    badgeIcon: ImageVector? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            if (badgeIcon != null) {
                Icon(badgeIcon, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(badge, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted, style = NumStyle, maxLines = 1, softWrap = false)
        }
    }
}

/** Muted metric label. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted, maxLines = 1, modifier = modifier.padding(top = 6.dp))
}

/** Count-up integer text (animates from 0 on first show, then between values). */
@Composable
fun CountUpText(value: Int, fontSize: TextUnit, color: Color = Color.White, weight: FontWeight = FontWeight.Medium) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val v by animateIntAsState(if (started) value else 0, tween(900, easing = FastOutSlowInEasing), label = "count")
    Text("$v", fontSize = fontSize, fontWeight = weight, color = color, style = NumStyle, maxLines = 1, softWrap = false)
}

/** Big number (counts up when numeric) + unit, with an optional right-aligned meta. Never wraps. */
@Composable
fun CardValue(
    value: String,
    unit: String? = null,
    metaIcon: ImageVector? = null,
    meta: String? = null,
    size: TextUnit = 30.sp,
    color: Color = Color.White,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        val n = value.toIntOrNull()
        if (n != null) CountUpText(n, size, color)
        else Text(value, fontSize = size, fontWeight = FontWeight.Medium, color = color, style = NumStyle, maxLines = 1, softWrap = false)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.weight(1f))
        if (meta != null) {
            if (metaIcon != null) {
                Icon(metaIcon, contentDescription = null, tint = Muted, modifier = Modifier.padding(bottom = 6.dp).size(13.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(meta, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted, style = NumStyle, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

enum class BarStyle { TRACK, TICKS }

/** Rounded progress bar that fills from zero; unfilled part is fine ticks (or a dim track). */
@Composable
fun CardBar(progress: Float, color: Color, style: BarStyle = BarStyle.TICKS, height: Dp = 8.dp) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); started = true }
    val p by animateFloatAsState(
        if (started) progress.coerceIn(0f, 1f) else 0f,
        tween(1000, easing = FastOutSlowInEasing), label = "bar",
    )
    Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(height)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        val filled = size.width * p
        if (style == BarStyle.TRACK) {
            drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r)
        } else {
            val step = 3.5.dp.toPx()
            val tickW = 1.2.dp.toPx()
            var x = filled + step
            while (x < size.width) {
                drawRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(x, h * 0.15f), size = Size(tickW, h * 0.7f))
                x += step
            }
        }
        if (filled > 0.5f) {
            drawRoundRect(color, size = Size(filled.coerceAtLeast(h), h), cornerRadius = r)
            // soft highlight on the leading edge
            drawCircle(Color.White.copy(alpha = 0.35f), radius = h * 0.32f, center = Offset(filled.coerceAtLeast(h) - h / 2, h / 2))
        }
    }
}

/** Discrete segments that light up one after another. */
@Composable
fun CardSegments(done: Int, total: Int, color: Color, height: Dp = 8.dp) {
    val n = total.coerceIn(1, 24)
    var lit by remember { mutableStateOf(0) }
    LaunchedEffect(done) {
        if (lit > done) lit = done
        while (lit < done.coerceAtMost(n)) { delay(110); lit++ }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (i in 0 until n) {
            val on = i < lit
            val segColor by animateColorAsState(if (on) color else Color.White.copy(alpha = 0.14f), tween(250), label = "seg")
            Box(Modifier.weight(1f).height(height).clip(RoundedCornerShape(2.dp)).background(segColor))
        }
    }
}

/** Small square tile for secondary entries. */
@Composable
fun SquareCard(
    icon: ImageVector,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    index: Int = 0,
    onClick: () -> Unit,
) {
    val (layer, click) = rememberMotion(index, onClick)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .then(layer)
            .size(82.dp)
            .clip(shape)
            .background(CardBase)
            .background(tintBrush(color))
            .border(1.5.dp, GlassBorder, shape)
            .then(click)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

/** Big round primary action with expanding pulse rings and spring press. */
@Composable
fun PrimaryButton(label: String, color: Color, pulse: Boolean = true, onClick: () -> Unit) {
    val (layer, click) = rememberMotion(0, onClick)
    val t = rememberInfiniteTransition(label = "ring")
    val r by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "r")
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        if (pulse) Canvas(Modifier.fillMaxSize()) {
            val base = 30.dp.toPx()
            val span = size.minDimension / 2 - base
            for (k in 0..1) {
                val f = (r + k * 0.5f) % 1f
                drawCircle(
                    color.copy(alpha = 0.45f * (1f - f)),
                    radius = base + span * f,
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        Box(
            Modifier.then(layer).size(60.dp).clip(RoundedCornerShape(50)).background(color).then(click),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E0E12))
        }
    }
}

/** Slowly drifting two-blob colour glow. Put it behind a screen; [color] cross-fades on change. */
@Composable
fun LiveBackground(color: Color, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val c by animateColorAsState(color, tween(900), label = "bg")
    val t = rememberInfiniteTransition(label = "drift")
    val d by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d")
    val breathe by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color(0xFF0B0B0E))
        val w = size.width; val h = size.height
        val c1 = Offset(w * (0.25f + 0.5f * d), h * (0.15f + 0.25f * d))
        val c2 = Offset(w * (0.75f - 0.5f * d), h * (0.85f - 0.2f * d))
        val r1 = w * 0.75f * breathe
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.30f * intensity), Color.Transparent), c1, r1), r1, c1)
        val r2 = w * 0.6f
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.16f * intensity), Color.Transparent), c2, r2), r2, c2)
    }
}

/** Rounded pill button: filled (primary) or outlined (secondary), with spring press. */
@Composable
fun PillButton(
    label: String,
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val (layer, click) = rememberMotion(0, onClick)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .then(layer)
            .height(height)
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.10f))
            .border(2.dp, if (filled) color else color.copy(alpha = 0.8f), shape)
            .then(click)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (filled) Color(0xFF0E0E12) else color
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Round icon button with spring press. */
@Composable
fun IconCircleButton(icon: ImageVector, color: Color, size: Dp = 40.dp, filled: Boolean = false, onClick: () -> Unit) {
    val (layer, click) = rememberMotion(0, onClick)
    Box(
        Modifier
            .then(layer)
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else Color.White.copy(alpha = 0.08f))
            .border(2.dp, if (filled) color else color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .then(click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color(0xFF0E0E12) else color, modifier = Modifier.size(size * 0.48f))
    }
}
