package com.gitakraft.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitakraft.app.BuildConfig
import com.gitakraft.app.R
import com.gitakraft.app.domain.Updates
import kotlinx.coroutines.launch
import com.kraft.ui.tokens.KraftSpacing
import com.gitakraft.app.ui.theme.GitaMetrics
import com.gitakraft.app.ui.theme.GitaTints

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val fontScale by vm.fontScale.collectAsState()
    val iastDefault by vm.iastDefault.collectAsState()
    val totalRead by vm.totalRead.collectAsState()
    val completedCount by vm.completedCount.collectAsState()
    val savedCount by vm.savedCount.collectAsState()
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    val updateState by vm.updateState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // External links need no permission: the system browser opens them.
    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            scope.launch { snackbar.showTimed("No browser found") }
        }
    }
    // One-shot notices: up-to-date / failed report once, then reset.
    LaunchedEffect(updateState) {
        when (updateState) {
            is SettingsViewModel.UpdateUiState.UpToDate -> {
                snackbar.showTimed("You're up to date")
                vm.consumeUpdateNotice()
            }
            is SettingsViewModel.UpdateUiState.Failed -> {
                snackbar.showTimed("Check failed. Try again.")
                vm.consumeUpdateNotice()
            }
            else -> {}
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        snackbarHost = {
            // House style (high-contrast pill), lifted above the
            // floating tab bar which would otherwise swallow it.
            SnackbarHost(snackbar) { data ->
                AppSnackbarLifted(data)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = KraftSpacing.Spacing12, bottom = GitaMetrics.AboveTabBar),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing20),
        ) {
            // No app header: WallKraft settings open straight into groups.
            item {
                SettingGroup(
                    title = "Journey",
                    footer = "Your reading lives on this phone.",
                ) {
                    Text(
                        text = "$totalRead of 700 verses",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = {
                            com.gitakraft.app.domain.Reading.fraction(
                                totalRead,
                                700,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = KraftSpacing.Spacing8)
                            .clip(MaterialTheme.shapes.small),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = KraftSpacing.Spacing12),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        JourneyStat(value = "$totalRead", label = "verses")
                        JourneyStat(
                            value = "$completedCount/18",
                            label = "chapters",
                        )
                        JourneyStat(value = "$savedCount", label = "saved")
                    }
                }
            }
            item {
                SettingGroup(
                    title = "Reading",
                    footer = "Applies to verse text throughout the app.",
                ) {
                    SettingSliderRow(
                        icon = Icons.Filled.FormatSize,
                        tint = GitaTints.Info,
                        label = "Text size",
                        value = "${(fontScale * 100).toInt()}%",
                    ) {
                        Slider(
                            value = fontScale,
                            onValueChange = { vm.setFontScale(it) },
                            valueRange = 0.85f..1.3f,
                            steps = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Reading text size"
                                },
                        )
                        Text(
                            text = "धर्मक्षेत्रे कुरुक्षेत्रे",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale,
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = KraftSpacing.Spacing4),
                        )
                    }
                    SettingSwitchRow(
                        icon = Icons.Filled.Translate,
                        tint = GitaTints.Active,
                        label = "Transliteration",
                        sublabel = "Show roman script under verses",
                        checked = iastDefault,
                        onChecked = { vm.setIastDefault(it) },
                    )
                }
            }
            item {
                // Support mirrors WallKraft exactly: description plus the
                // official brand button (182dp, press spring, haptic).
                SettingGroup(title = "Support") {
                    Text(
                        text = "If you enjoy GitaKraft, buy me a coffee:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = KraftSpacing.Spacing12),
                    )
                    BuyMeACoffeeButton(
                        onClick = { openLink("https://buymeacoffee.com/kedhartech") },
                    )
                }
            }
            item {
                // About: developer, version and reference rows together —
                // one group, WallKraft pattern. Nothing scattered.
                SettingGroup(title = "About") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = KraftSpacing.Spacing6),
                    ) {
                        // Your GitHub avatar, bundled as an asset: the app is
                        // offline, so the photo ships inside the APK instead
                        // of loading over the network like WallKraft's.
                        Image(
                            painter = painterResource(R.drawable.avatar),
                            contentDescription = "Kedhar",
                            modifier = Modifier
                                .size(KraftSpacing.Spacing40)
                                .clip(RoundedCornerShape(KraftSpacing.Spacing12)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kedhar",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Developer & Designer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = KraftSpacing.Spacing4),
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = KraftSpacing.Spacing6),
                    ) {
                        Text(
                            text = "GitaKraft version",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${BuildConfig.VERSION_NAME} · Content v1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = null,
                        tint = Color.Transparent,
                        label = "Check for updates",
                        sublabel = "Compares against the latest GitHub release.",
                        onClick = {
                            val s = updateState
                            if (s is SettingsViewModel.UpdateUiState.Available) {
                                showUpdate = true
                            } else {
                                vm.checkUpdates()
                            }
                        },
                        // Fixed 104dp slot, end-aligned: the row geometry
                        // never moves however the state changes — button,
                        // spinner and dot all live in the same footprint.
                        trailing = {
                            Box(
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier.width(GitaMetrics.UpdateSlotWidth),
                            ) {
                                when (updateState) {
                                    is SettingsViewModel.UpdateUiState.Checking ->
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(KraftSpacing.Spacing20),
                                            strokeWidth = KraftSpacing.Spacing2,
                                        )
                                    is SettingsViewModel.UpdateUiState.Available ->
                                        Box(
                                            modifier = Modifier
                                                .size(KraftSpacing.Spacing8)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                ),
                                        )
                                    // A real button: rows that act must look
                                    // the part, not hide behind a tap target.
                                    else -> Button(
                                        onClick = { vm.checkUpdates() },
                                        contentPadding = PaddingValues(
                                            horizontal = KraftSpacing.Spacing16,
                                            vertical = KraftSpacing.Spacing8,
                                        ),
                                    ) {
                                        Text("Check")
                                    }
                                }
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = null,
                        tint = Color.Transparent,
                        label = "GitHub",
                        sublabel = "github.com/kedharsairam",
                        onClick = { openLink("https://github.com/kedharsairam") },
                        markRes = R.drawable.github_mark,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = Icons.Filled.Book,
                        tint = GitaTints.Reference,
                        label = "Sources",
                        sublabel = "Telang (1882) and Arnold renderings, public domain. " +
                            "Numbering follows Gita Press.",
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = Icons.Filled.Info,
                        tint = GitaTints.Info,
                        label = "Recension",
                        sublabel = "The Kashmir recension differs in places. " +
                            "This app follows the vulgate text.",
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = Icons.Filled.Lock,
                        tint = GitaTints.Active,
                        label = "Privacy",
                        sublabel = "No account, no tracking. GitHub is contacted " +
                            "only when you check for updates.",
                        onClick = { showPrivacy = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing2))
                    SettingInfoRow(
                        icon = Icons.Filled.LibraryBooks,
                        tint = GitaTints.Reference,
                        label = "Open-source licenses",
                        sublabel = "Every library in this app, and its license.",
                        onClick = { showLicenses = true },
                    )
                }
            }
            item {
                SettingGroup(title = "Danger zone") {
                    SettingInfoRow(
                        icon = Icons.Filled.DeleteForever,
                        tint = GitaTints.Destructive,
                        label = "Delete all data",
                        sublabel = "Progress, saved verses and settings, gone.",
                    )
                    Button(
                        onClick = { confirmWipe = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = KraftSpacing.Spacing12),
                    ) {
                        Text("Delete all data")
                    }
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy policy") },
            text = {
                Text(
                    "GitaKraft works fully offline. It creates no account, " +
                        "sends no analytics and shows no ads. Your reading " +
                        "progress, saved verses and settings live only in " +
                        "this phone's private storage, and disappear " +
                        "completely if you uninstall the app. The single " +
                        "exception: tapping “Check for updates” contacts " +
                        "GitHub once to compare release versions — nothing " +
                        "else ever leaves this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) {
                    Text("Close")
                }
            },
        )
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
                    LicenseRow("Jetpack Compose + Material 3", "Apache 2.0")
                    LicenseRow("Room", "Apache 2.0")
                    LicenseRow("DataStore", "Apache 2.0")
                    LicenseRow("Navigation Compose", "Apache 2.0")
                    LicenseRow("Lifecycle", "Apache 2.0")
                    LicenseRow("kotlinx.serialization", "Apache 2.0")
                    LicenseRow(
                        "Liquid-glass engine (Mortd3kay, via WallKraft)",
                        "Apache 2.0",
                    )
                    LicenseRow(
                        "Gita text: Telang (1882), Arnold — public domain",
                        "Public domain",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text("Close")
                }
            },
        )
    }
    val updateInfo = (updateState as? SettingsViewModel.UpdateUiState.Available)?.info
    if (showUpdate && updateInfo != null) {
        AlertDialog(
            onDismissRequest = {
                showUpdate = false
                vm.dismissUpdate()
            },
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
                    Text(
                        text = "Version ${updateInfo.version} " +
                            "(${Updates.formatBytes(updateInfo.sizeBytes)})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (updateInfo.notes.isNotBlank()) {
                        Text(
                            text = updateInfo.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openLink(updateInfo.apkUrl)
                        showUpdate = false
                        vm.dismissUpdate()
                    },
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUpdate = false
                        vm.dismissUpdate()
                    },
                ) {
                    Text("Later")
                }
            },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "Reading progress, saved verses and settings return to " +
                        "fresh-install state. The 700 verses themselves stay.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAllData()
                        confirmWipe = false
                        scope.launch {
                            snackbar.showTimed("All data deleted")
                        }
                    },
                ) {
                    Text("Delete everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingGroup(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = KraftSpacing.Spacing16, bottom = KraftSpacing.Spacing8),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(KraftSpacing.Spacing16)) {
                content()
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = KraftSpacing.Spacing16, end = KraftSpacing.Spacing16, top = KraftSpacing.Spacing8),
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KraftSpacing.Spacing32)
            .clip(RoundedCornerShape(KraftSpacing.Spacing8))
            .background(tint),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(KraftSpacing.Spacing20),
        )
    }
}

@Composable
private fun SettingSliderRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconTile(icon = icon, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(modifier = Modifier.padding(top = KraftSpacing.Spacing4)) {
        content()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing8))
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    sublabel: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconTile(icon = icon, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = KraftSpacing.Spacing4),
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/**
 * Official Buy-Me-a-Coffee brand button (WallKraft port): fixed 182dp
 * brand width, press spring + haptic, motion-aware.
 */
@Composable
private fun BuyMeACoffeeButton(onClick: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.7f, stiffness = 400f)
        },
        label = "bmcScale",
    )
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.bmc_button),
            contentDescription = "Buy me a coffee",
            modifier = Modifier
                .width(GitaMetrics.SponsorButtonWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(KraftSpacing.Spacing12))
                .clickable {
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
        )
    }
}

@Composable
private fun LicenseRow(name: String, license: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = license,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JourneyStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingInfoRow(
    icon: ImageVector?,
    tint: Color,
    label: String,
    sublabel: String,
    onClick: (() -> Unit)? = null,
    markRes: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KraftSpacing.Spacing6)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // Brand marks (GitHub) use the standard rounded-square tile
        // like every other row — shape uniformity is the design.
        if (markRes != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(KraftSpacing.Spacing32)
                    .clip(RoundedCornerShape(KraftSpacing.Spacing8))
                    .background(Color.Black),
            ) {
                Image(
                    painter = painterResource(markRes),
                    contentDescription = null,
                    modifier = Modifier.size(KraftSpacing.Spacing20),
                )
            }
        } else if (icon != null) {
            IconTile(icon = icon, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = KraftSpacing.Spacing4),
            )
        }
        trailing?.invoke()
    }
}
