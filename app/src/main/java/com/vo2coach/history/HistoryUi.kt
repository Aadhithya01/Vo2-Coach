package com.vo2coach.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import com.vo2coach.ui.CardBar
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.CardLabel
import com.vo2coach.ui.CardSegments
import com.vo2coach.ui.CardValue
import com.vo2coach.ui.WidgetCard
import com.vo2coach.ui.modeIcon
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Text
import com.vo2coach.Modes
import com.vo2coach.Neon
import com.vo2coach.data.IntervalStat
import com.vo2coach.data.SessionRecord
import com.vo2coach.levelColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- helpers

private val Tnum = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)
private val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun fmtDur(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    else "%d:%02d".format(sec / 60, sec % 60)

private fun fmtDay(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DayFmt).uppercase()

private fun fmtTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TimeFmt)

private fun fmtDist(m: Double): String =
    if (m >= 1000) "%.2f KM".format(m / 1000.0) else "%d M".format(m.toInt())

private fun fmtPace(secPerKm: Int): String = "%d:%02d /KM".format(secPerKm / 60, secPerKm % 60)

/** Colour of a mode via its position in Modes.all; custom/unknown modes get violet. */
fun modeColor(modeId: String): Color {
    val idx = Modes.all.indexOfFirst { it.id == modeId }
    return if (idx >= 0) levelColor(idx) else Neon.Violet
}

private fun shortTitle(r: SessionRecord): String = r.modeTitle.substringAfter(". ").ifBlank { r.modeId }

private fun pctColor(pct: Int): Color = when {
    pct >= 70 -> Neon.Lime
    pct >= 40 -> Neon.Yellow
    else -> Neon.Hot
}

private fun zonePct(inZone: Int, total: Int): Int = if (total > 0) inZone * 100 / total else 0

@Composable
private fun SmallLabel(text: String, color: Color = Neon.Dim, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = modifier,
    )
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, style = Tnum)
        Text(label, fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp)
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    Text(
        "back", fontSize = 13.sp, color = Neon.Dim,
        modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
    )
}

/** Thin horizontal in-zone bar. */
@Composable
private fun ZoneBar(pct: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

// ---------------------------------------------------------------- streak badge

@Composable
private fun Flame(lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 13.dp, height = 17.dp)) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * 0.52f, 0f)
            cubicTo(w * 0.95f, h * 0.30f, w * 1.00f, h * 0.62f, w * 0.88f, h * 0.80f)
            cubicTo(w * 0.76f, h * 0.97f, w * 0.62f, h, w * 0.50f, h)
            cubicTo(w * 0.36f, h, w * 0.20f, h * 0.97f, w * 0.10f, h * 0.80f)
            cubicTo(0f, h * 0.60f, w * 0.12f, h * 0.42f, w * 0.30f, h * 0.30f)
            cubicTo(w * 0.32f, h * 0.45f, w * 0.40f, h * 0.50f, w * 0.44f, h * 0.48f)
            cubicTo(w * 0.40f, h * 0.30f, w * 0.44f, h * 0.15f, w * 0.52f, 0f)
            close()
        }
        val inner = Path().apply {
            moveTo(w * 0.50f, h * 0.48f)
            cubicTo(w * 0.72f, h * 0.65f, w * 0.72f, h * 0.88f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.28f, h * 0.88f, w * 0.30f, h * 0.68f, w * 0.50f, h * 0.48f)
            close()
        }
        if (lit) {
            drawPath(outer, brush = Brush.verticalGradient(listOf(Neon.Yellow, Neon.Orange, Neon.Hot)))
            drawPath(inner, color = Neon.Yellow.copy(alpha = 0.9f))
        } else {
            drawPath(outer, color = Neon.Dim.copy(alpha = 0.5f))
        }
    }
}

/**
 * Small neon flame badge for the home screen: "🔥 3 WK · 2 THIS WK".
 * [streak] = consecutive weeks, [thisWeek] = completed sessions this week.
 */
@Composable
fun StreakBadge(
    streak: Int,
    thisWeek: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val lit = streak > 0
    val c = when {
        !lit -> Neon.Dim
        thisWeek == 0 -> Neon.Orange   // alive on last week's work only
        else -> Neon.Hot
    }
    val shape = RoundedCornerShape(50)
    val base = modifier
        .clip(shape)
        .background(Neon.Card)
        .border(2.dp, c.copy(alpha = 0.8f), shape)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Flame(lit)
        Column {
            Text(
                if (lit) "$streak WK STREAK" else "NO STREAK",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (lit) Color.White else Neon.Dim,
                letterSpacing = 1.sp, style = Tnum,
            )
            Text(
                when {
                    !lit -> "ONE RUN STARTS IT"
                    thisWeek == 0 -> "1 RUN KEEPS IT ALIVE"
                    else -> "$thisWeek THIS WEEK"
                },
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c, letterSpacing = 1.sp,
            )
        }
    }
}

/**
 * Self-loading home-screen entry: streak badge + "HISTORY" link. Refreshes when history changes.
 * Drop into ModeList's ScalingLazyColumn as one `item { HistoryHomeEntry { ... } }`.
 */
@Composable
fun HistoryHomeEntry(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val rev by HistoryStore.revision.collectAsState()
    val info by produceState(StreakInfo(0, 0, 0), rev) {
        value = StreakCalc.compute(store.allIo())
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StreakBadge(info.currentWeekStreak, info.sessionsThisWeek, onClick = onClick)
        Text(
            "HISTORY  ›", fontSize = 11.sp, color = Neon.Cyan, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- history list

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val rev by HistoryStore.revision.collectAsState()
    val records by produceState<List<SessionRecord>?>(null, rev) { value = store.allIo() }
    val info = remember(records) { records?.let { StreakCalc.compute(it) } }

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("History", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        if (info != null) item {
            WidgetCard(Neon.Orange) {
                CardHeader(Icons.Filled.LocalFireDepartment, "Streak", Neon.Orange)
                CardValue("${info.currentWeekStreak}", unit = "wk", metaIcon = Icons.Filled.CheckCircle, meta = "${info.totalCompleted}")
                CardSegments(info.sessionsThisWeek, 3, Neon.Orange)
            }
        }
        val list = records
        when {
            list == null -> item { SmallLabel("loading…") }
            list.isEmpty() -> item {
                Text(
                    "No runs yet.",
                    fontSize = 13.sp, color = Neon.Dim, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.width(150.dp).padding(vertical = 8.dp),
                )
            }
            else -> items(list, key = { it.id }) { r -> HistoryRow(r) { onOpen(r.id) } }
        }
        item { BackLink(onBack) }
    }
}

@Composable
fun HistoryRow(r: SessionRecord, onClick: () -> Unit) {
    val c = modeColor(r.modeId)
    val pct = r.inZonePct
    WidgetCard(c, dim = !r.completed, onClick = onClick) {
        CardHeader(modeIcon(r.modeId), shortTitle(r), c, badge = fmtDay(r.startedAtMs))
        CardLabel(if (r.completed) "In zone" else "Quit early")
        CardValue("$pct", unit = "%", metaIcon = Icons.Filled.Timer, meta = fmtDur(r.elapsedSec))
        CardBar(pct / 100f, c)
    }
}

// ---------------------------------------------------------------- session detail

/** Loads a record by id, then shows [SessionDetailScreen]. Handles delete. */
@Composable
fun SessionDetailRoute(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val scope = rememberCoroutineScope()
    val loaded by produceState<Pair<Boolean, SessionRecord?>>(false to null, id) {
        value = true to store.getIo(id)
    }
    val (done, record) = loaded
    when {
        !done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SmallLabel("loading…")
        }
        record == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallLabel("session not found")
                BackLink(onBack)
            }
        }
        else -> SessionDetailScreen(
            record = record,
            onBack = onBack,
            onDelete = {
                scope.launch {
                    store.deleteIo(record.id)
                    onBack()
                }
            },
        )
    }
}

@Composable
fun SessionDetailScreen(record: SessionRecord, onBack: () -> Unit, onDelete: (() -> Unit)? = null) {
    val c = modeColor(record.modeId)
    val pct = record.inZonePct
    val pc = pctColor(pct)
    var armed by remember(record.id) { mutableStateOf(false) }

    ScalingLazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(c.copy(alpha = 0.20f), Neon.Black), radius = 520f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                shortTitle(record).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier.width(160.dp),
            )
        }
        item {
            SmallLabel("${fmtDay(record.startedAtMs)} · ${fmtTime(record.startedAtMs)}", c)
        }
        item {
            SmallLabel(
                if (record.completed) "completed" else "quit early",
                if (record.completed) Neon.Lime else Neon.Hot,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("TIME", fmtDur(record.elapsedSec), Color.White)
                MiniStat("AVG", "${record.avgHr ?: "--"}", Neon.Cyan)
                MiniStat("PEAK", "${record.peakHr ?: "--"}", Neon.Hot)
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("IN ZONE  $pct%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pc, style = Tnum)
                ZoneBar(pct, pc, Modifier.width(120.dp).padding(top = 3.dp))
                SmallLabel(
                    "${fmtDur(record.workInZoneSec)} of ${fmtDur(record.workSec)} work · max ${record.maxHrSetting}",
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        val extras = buildList<Triple<String, String, Color>> {
            record.distanceM?.let { add(Triple("DIST", fmtDist(it), Neon.Cyan)) }
            record.avgPaceSecPerKm?.let { add(Triple("PACE", fmtPace(it), Neon.Yellow)) }
            record.avgHrDrop60s?.let { add(Triple("HR DROP", "-$it", Neon.Lime)) }
        }
        if (extras.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                extras.forEach { (l, v, col) -> MiniStat(l, v, col) }
            }
        }
        if (record.intervals.isNotEmpty()) {
            item { SmallLabel("intervals", Color.White, Modifier.padding(top = 4.dp)) }
            items(record.intervals) { iv -> IntervalCard(iv, c) }
        }
        if (onDelete != null) item {
            Text(
                if (armed) "TAP AGAIN TO DELETE" else "delete",
                fontSize = if (armed) 10.sp else 11.sp,
                fontWeight = if (armed) FontWeight.Bold else FontWeight.Medium,
                color = if (armed) Neon.Hot else Neon.Dim,
                letterSpacing = if (armed) 1.sp else 0.sp,
                modifier = Modifier
                    .clickable { if (armed) onDelete() else armed = true }
                    .padding(top = 6.dp, start = 8.dp, end = 8.dp),
            )
        }
        item { BackLink(onBack) }
    }
}

@Composable
private fun IntervalCard(iv: IntervalStat, c: Color) {
    val shape = RoundedCornerShape(16.dp)
    val pct = zonePct(iv.inZoneSec, iv.durationSec)
    val pc = pctColor(pct)
    Column(
        Modifier
            .fillMaxWidth(0.9f)
            .clip(shape)
            .background(Neon.Card)
            .border(2.dp, c.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                iv.label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(fmtDur(iv.durationSec), fontSize = 12.sp, color = Neon.Dim, style = Tnum)
        }
        Row(
            Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("AVG ${iv.avgHr ?: "--"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Neon.Cyan, style = Tnum)
            Text("PK ${iv.peakHr ?: "--"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Neon.Hot, style = Tnum)
            Spacer(Modifier.weight(1f))
            Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pc, style = Tnum)
        }
        ZoneBar(pct, pc, Modifier.fillMaxWidth().padding(top = 3.dp))
        val extra = buildList<String> {
            iv.distanceM?.let { add(fmtDist(it)) }
            iv.avgPaceSecPerKm?.let { add(fmtPace(it)) }
            iv.hrDrop60s?.let { add("DROP -$it") }
        }
        if (extra.isNotEmpty()) {
            Text(
                extra.joinToString(" · "), fontSize = 11.sp, color = Neon.Dim, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, style = Tnum, maxLines = 1, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
