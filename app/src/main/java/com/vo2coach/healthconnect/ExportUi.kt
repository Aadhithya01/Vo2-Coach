package com.vo2coach.healthconnect

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import com.vo2coach.ui.CardBar
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.CardValue
import com.vo2coach.ui.WidgetCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.vo2coach.Neon
import com.vo2coach.data.SessionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- pure status model

/** What the status line under the buttons says. Pure, so it is unit-testable. */
data class ExportStatus(val text: String, val tone: Tone) {
    enum class Tone { IDLE, BUSY, OK, WARN, ERROR }

    companion object {
        val Idle = ExportStatus("", Tone.IDLE)
        fun busy(what: String) = ExportStatus(what, Tone.BUSY)
        val Empty = ExportStatus("No runs yet", Tone.WARN)

        fun forFiles(r: SessionExporter.ExportResult): ExportStatus = ExportStatus(
            "Saved ${r.sessionCount}",
            Tone.OK,
        )

        fun forSend(r: DataLayerSender.SendResult): ExportStatus = when (r) {
            is DataLayerSender.SendResult.Sent ->
                ExportStatus("Sent ${r.sessionCount}", Tone.OK)
            DataLayerSender.SendResult.NothingToSend -> Empty
            DataLayerSender.SendResult.NoPhone ->
                ExportStatus("No phone connected", Tone.WARN)
            DataLayerSender.SendResult.NoCompanionApp ->
                ExportStatus("Phone app not installed", Tone.WARN)
            DataLayerSender.SendResult.TooLarge ->
                ExportStatus("Too large to send", Tone.ERROR)
            is DataLayerSender.SendResult.Failed ->
                ExportStatus("Send failed", Tone.ERROR)
        }

        fun forError(e: Throwable) =
            ExportStatus("Export failed", Tone.ERROR)
    }
}

private fun toneColor(t: ExportStatus.Tone): Color = when (t) {
    ExportStatus.Tone.IDLE -> Neon.Dim
    ExportStatus.Tone.BUSY -> Neon.Cyan
    ExportStatus.Tone.OK -> Neon.Lime
    ExportStatus.Tone.WARN -> Neon.Yellow
    ExportStatus.Tone.ERROR -> Neon.Hot
}

// ---------------------------------------------------------------- screen

/**
 * Export / send-to-phone screen.
 *
 * @param loadSessions supplies saved sessions (e.g. `{ HistoryStore(ctx).allIo() }`).
 * @param onBack navigates back.
 */
@Composable
fun ExportScreen(
    loadSessions: suspend () -> List<SessionRecord>,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val exporter = remember { SessionExporter(ctx) }
    val sender = remember { DataLayerSender(ctx) }
    val scope = rememberCoroutineScope()

    var count by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ExportStatus.Idle) }

    LaunchedEffect(Unit) {
        count = runCatching { loadSessions().size }.getOrNull()
    }

    fun run(label: String, block: suspend (List<SessionRecord>) -> ExportStatus) {
        if (busy) return
        busy = true
        status = ExportStatus.busy(label)
        scope.launch {
            status = try {
                val records = loadSessions()
                count = records.size
                if (records.isEmpty()) ExportStatus.Empty else block(records)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportStatus.forError(e)
            } finally {
                busy = false
            }
        }
    }

    var active by remember { mutableStateOf<String?>(null) }
    fun act(key: String, label: String, block: suspend (List<SessionRecord>) -> ExportStatus) {
        active = key
        run(label, block)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Sync", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        item {
            WidgetCard(Neon.Violet, index = 0) {
                CardHeader(Icons.Filled.History, "Sessions", Neon.Violet, badge = if (busy) "…" else null)
                CardValue(count?.toString() ?: "–", unit = "saved")
                if (status.text.isNotBlank()) {
                    Text(status.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = toneColor(status.tone), maxLines = 1)
                }
            }
        }
        item {
            ActionCard(Icons.Filled.Description, "Export CSV", Neon.Lime, 1, busy, active == "csv") {
                act("csv", "Writing CSV") { ExportStatus.forFiles(exporter.exportCsvIo(it)) }
            }
        }
        item {
            ActionCard(Icons.Filled.Code, "Export JSON", Neon.Yellow, 2, busy, active == "json") {
                act("json", "Writing JSON") { ExportStatus.forFiles(exporter.exportJsonIo(it)) }
            }
        }
        item {
            ActionCard(Icons.Filled.PhoneAndroid, "Send to phone", Neon.Cyan, 3, busy, active == "phone") {
                act("phone", "Sending") { ExportStatus.forSend(sender.sendAll(it)) }
            }
        }
        item {
            Text("Back", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Neon.Dim,
                modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector, title: String, color: Color, index: Int,
    busy: Boolean, isActive: Boolean, onClick: () -> Unit,
) {
    WidgetCard(color, index = index, dim = busy && !isActive, onClick = if (busy) null else onClick) {
        CardHeader(icon, title, color, badge = if (busy && isActive) "…" else null, badgeIcon = null)
        if (busy && isActive) CardBar(1f, color)
    }
}

@Composable
private fun NeonButton(
    label: String,
    c1: Color,
    c2: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        Modifier
            .width(150.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(c1.copy(alpha = alpha), c2.copy(alpha = alpha))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Neon.Black,
            letterSpacing = 1.sp,
        )
    }
}

/** Small home-list entry ("EXPORT / SYNC") to drop into ModeList's ScalingLazyColumn as an `item { }`. */
@Composable
fun ExportEntry(onClick: () -> Unit) {
    Box(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Neon.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "EXPORT / SYNC", fontSize = 12.sp, color = Neon.Cyan, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
