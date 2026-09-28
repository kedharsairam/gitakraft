package com.gitakraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

/** Shared pill: wrap-content, capped width, centered by the host.
 * M3 Snackbar always stretches full-bleed, so the island is a plain
 * Surface driven by the same [SnackbarData] (message + Undo action). */
@Composable
private fun NoticePill(
    message: String,
    icon: ImageVector?,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(max = 340.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
fun AppSnackbar(data: SnackbarData) {
    NoticePill(
        message = data.visuals.message,
        icon = noticeIcon(data.visuals.message),
        actionLabel = data.visuals.actionLabel,
        onAction = { data.performAction() },
    )
}

/** Rich variant: status icon, no buttons except the notice's own
 * action (Undo). Falls back to [AppSnackbar] when the message
 * carries no known key. Dismissal is timeout-only, by design. */
@Composable
fun AppSnackbarWithIcon(data: SnackbarData) {
    AppSnackbar(data)
}

/**
 * The house timeout: 5 seconds. Long enough to read a one-liner and
 * reach Undo; short enough to never feel stuck. Every notice in the
 * app goes through here — one duration, no exceptions.
 */
const val NOTICE_TIMEOUT_MS = 5_000L

suspend fun SnackbarHostState.showTimed(
    message: String,
    actionLabel: String? = null,
    millis: Long = NOTICE_TIMEOUT_MS,
): SnackbarResult {
    var result: SnackbarResult = SnackbarResult.Dismissed
    try {
        kotlinx.coroutines.withTimeout(millis) {
            result = showSnackbar(message, actionLabel)
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        currentSnackbarData?.dismiss()
    }
    return result
}

/** Lifted variant: a centered floating pill above the glass tab bar.
 * Wrap-content (never full-bleed); side margins only bite on very
 * long messages. Use in every screen with bottom navigation. */
@Composable
fun AppSnackbarLifted(data: SnackbarData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SnackbarLift.AboveTabBar),
        contentAlignment = Alignment.Center,
    ) {
        AppSnackbarWithIcon(data)
    }
}
