package com.gitakraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The app's one notice look, used by every host (reader, settings).
 *
 * Themed but unmistakable: [surfaceContainerHighest] sits a full tonal
 * step above cards and background, with a primary-orange status icon.
 * A floating island (24dp side margins), never full-bleed.
 * Dismissible three ways: X button, swipe away, or timeout.
 */
object SnackbarLift {
    val AboveTabBar = 150.dp
}

/** Message-keyed leading icon. Messages are fixed strings; the map
 * lives here (not scattered across call sites) so new notices pick
 * the house style by adding one line. */
private fun noticeIcon(message: String): ImageVector? = when {
    "saved" in message -> Icons.Filled.BookmarkAdd
    "removed from library" in message -> Icons.Filled.BookmarkRemove
    "up to date" in message -> Icons.Filled.Check
    "failed" in message -> Icons.Filled.Warning
    "No browser" in message -> Icons.Filled.Warning
    "deleted" in message -> Icons.Filled.Delete
    else -> null
}

@Composable
fun AppSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/** Rich variant: status icon + explicit X. Falls back to [AppSnackbar]
 * when the message carries no known key. */
@Composable
fun AppSnackbarWithIcon(data: SnackbarData) {
    val icon = noticeIcon(data.visuals.message)
    Snackbar(
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionContentColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(text = label)
                }
            }
        },
        dismissAction = {
            IconButton(onClick = { data.dismiss() }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Swipe-to-dismiss wrapper: fling the island away. The X covers
 * precise taps; the timeout covers inattention. */
@Composable
fun AppSnackbarDismissible(data: SnackbarData) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            data.dismiss()
            true
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        content = { AppSnackbarWithIcon(data) },
    )
}

/** Lifted variant: floats above the glass tab bar. Use in every
 * screen with bottom navigation (reader, settings). */
@Composable
fun AppSnackbarLifted(data: SnackbarData) {
    Box(
        modifier = Modifier.padding(bottom = SnackbarLift.AboveTabBar),
    ) {
        AppSnackbarDismissible(data)
    }
}
