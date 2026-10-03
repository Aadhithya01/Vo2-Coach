package com.vo2coach.voice

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeOff
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.WidgetCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.vo2coach.Neon

fun voiceModeColor(mode: VoiceMode): Color = when (mode) {
    VoiceMode.OFF -> Neon.Dim
    VoiceMode.HEADPHONES_ONLY -> Neon.Cyan
    VoiceMode.ALWAYS -> Neon.Lime
}

private fun voiceModeHint(mode: VoiceMode): String = when (mode) {
    VoiceMode.OFF -> "Silent. Haptics only."
    VoiceMode.HEADPHONES_ONLY -> "Talks when buds connected."
    VoiceMode.ALWAYS -> "Talks through the speaker too."
}

/**
 * Stateless row: tap cycles OFF -> HEADPHONES -> ALWAYS. Fits ~170 dp on a round screen.
 */
@Composable
fun VoiceSettingRow(mode: VoiceMode, onChange: (VoiceMode) -> Unit, modifier: Modifier = Modifier) {
    val c = voiceModeColor(mode)
    WidgetCard(c, modifier = modifier, dim = mode == VoiceMode.OFF, onClick = { onChange(mode.next()) }) {
        CardHeader(
            if (mode == VoiceMode.OFF) Icons.Filled.VolumeOff else Icons.Filled.RecordVoiceOver,
            "Voice", c, badge = mode.label.lowercase().replaceFirstChar { it.uppercase() },
        )
    }
}

/**
 * Stateful variant that reads/writes prefs "vo2"/"voiceMode" itself, so the integrator only
 * needs `item { VoiceSetting() }` inside ModeList's ScalingLazyColumn.
 */
@Composable
fun VoiceSetting(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { VoicePrefs(ctx) }
    var mode by remember { mutableStateOf(prefs.mode) }
    VoiceSettingRow(
        mode = mode,
        onChange = {
            mode = it
            prefs.mode = it
        },
        modifier = modifier,
    )
}
