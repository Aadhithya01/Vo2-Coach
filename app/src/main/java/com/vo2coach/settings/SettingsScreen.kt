package com.vo2coach.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LockOpen
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.WidgetCard
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.vo2coach.Neon
import com.vo2coach.gps.OutdoorToggle
import com.vo2coach.gps.RunSettings
import com.vo2coach.progression.ProgressionPrefs
import com.vo2coach.voice.VoicePrefs
import com.vo2coach.voice.VoiceSettingRow

/** Max HR bounds, shared with MainActivity's prefs handling. */
object MaxHrBounds {
    const val MIN = 140
    const val MAX = 220
    const val DEFAULT = 190
    fun clamp(v: Int): Int = v.coerceIn(MIN, MAX)
}

private val TnumSettings = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)

/**
 * One screen for every setting:
 *  - Max HR stepper (moved off the home list)
 *  - Outdoor GPS / treadmill toggle (feature #3, prefs "vo2"/"outdoorGps")
 *  - Voice coach mode (feature #7, prefs "vo2"/"voiceMode")
 *  - Force-unlock-all (feature #2, prefs "vo2"/"forceUnlockAll"; never bypasses the recovery guard)
 *
 * [maxHr] is hoisted because App() owns it (it is passed to the service on start).
 * [onForceUnlockChanged] lets App() rebuild its ProgressionEngine.
 */
@Composable
fun SettingsScreen(
    maxHr: Int,
    onMaxHr: (Int) -> Unit,
    onBack: () -> Unit,
    onForceUnlockChanged: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val run = remember { RunSettings(ctx) }
    val voicePrefs = remember { VoicePrefs(ctx) }
    var outdoor by remember { mutableStateOf(run.outdoorGps) }
    var voiceMode by remember { mutableStateOf(voicePrefs.mode) }
    var forceUnlock by remember { mutableStateOf(ProgressionPrefs.forceUnlockAll(ctx)) }

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        item { MaxHrStepper(maxHr, onMaxHr) }
        item {
            OutdoorToggle(outdoor) {
                outdoor = it
                run.outdoorGps = it
            }
        }
        item {
            VoiceSettingRow(
                mode = voiceMode,
                onChange = {
                    voiceMode = it
                    voicePrefs.mode = it
                },
            )
        }
        item {
            ToggleRow(
                label = "UNLOCK ALL",
                on = forceUnlock,
                color = Neon.Orange,
                hint = "",
            ) {
                forceUnlock = !forceUnlock
                ProgressionPrefs.setForceUnlockAll(ctx, forceUnlock)
                onForceUnlockChanged(forceUnlock)
            }
        }
        item {
            Text(
                "back", fontSize = 13.sp, color = Neon.Dim,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MaxHrStepper(maxHr: Int, onMaxHr: (Int) -> Unit) {
    WidgetCard(Neon.Hot) {
        CardHeader(Icons.Filled.Favorite, "Max heart rate", Neon.Hot)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundBtn("−", Color.White) { onMaxHr(MaxHrBounds.clamp(maxHr - 1)) }
            Text("$maxHr", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.White, style = TnumSettings)
            RoundBtn("+", Color.White) { onMaxHr(MaxHrBounds.clamp(maxHr + 1)) }
        }
    }
}

@Composable
private fun RoundBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** ON/OFF widget card. */
@Composable
private fun ToggleRow(label: String, on: Boolean, color: Color, hint: String, onToggle: () -> Unit) {
    val c = if (on) color else Neon.Dim
    WidgetCard(c, dim = !on, onClick = onToggle) {
        CardHeader(Icons.Filled.LockOpen, label.lowercase().replaceFirstChar { it.uppercase() }, c, badge = if (on) "On" else "Off")
    }
}
