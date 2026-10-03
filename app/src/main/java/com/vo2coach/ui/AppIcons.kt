package com.vo2coach.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/** One icon per training mode, used in card headers. */
fun modeIcon(modeId: String): ImageVector = when (modeId) {
    "base" -> Icons.Filled.DirectionsRun
    "starter" -> Icons.Filled.Speed
    "builder" -> Icons.Filled.TrendingUp
    "norwegian" -> Icons.Filled.Bolt
    "max" -> Icons.Filled.Whatshot
    else -> Icons.Filled.Tune // custom
}
