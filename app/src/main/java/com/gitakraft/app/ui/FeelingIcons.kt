package com.gitakraft.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LiveHelp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/** One icon per feeling — shared by Home grid and Feelings tab (if any). */
val FEELING_ICONS: Map<String, ImageVector> = mapOf(
    "Grieving" to Icons.Filled.HeartBroken,
    "Afraid" to Icons.Filled.Warning,
    "Angry" to Icons.Filled.Whatshot,
    "Confused" to Icons.Filled.Help,
    "Doubtful" to Icons.Filled.LiveHelp,
    "Restless" to Icons.Filled.Waves,
    "Failing" to Icons.Filled.TrendingDown,
    "Guilty" to Icons.Filled.Gavel,
    "Envious" to Icons.Filled.Visibility,
    "Indecisive" to Icons.Filled.CallSplit,
    "Weary" to Icons.Filled.Bedtime,
    "Seeking Purpose" to Icons.Filled.Explore,
)
