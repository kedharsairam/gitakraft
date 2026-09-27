package com.gitakraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ThemeNames = listOf("System", "Light", "Dark")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val themeMode by vm.themeMode.collectAsState()
    val fontScale by vm.fontScale.collectAsState()
    val iastDefault by vm.iastDefault.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                // App header, Apple-style: mark + name + version.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Text(
                            text = "गी",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column {
                        Text(
                            text = "GitaKraft",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Version 0.1.0 · Content v1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        tint = Color(0xFF0A84FF),
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
                                .padding(top = 4.dp),
                        )
                    }
                    SettingSwitchRow(
                        icon = Icons.Filled.Translate,
                        tint = Color(0xFF30D158),
                        label = "Transliteration",
                        sublabel = "Show roman script under verses",
                        checked = iastDefault,
                        onChecked = { vm.setIastDefault(it) },
                    )
                }
            }
            item {
                SettingGroup(title = "Appearance") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconTile(icon = Icons.Filled.Palette, tint = Color(0xFFFF9F0A))
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ThemeNames.forEachIndexed { i, name ->
                            SegmentedButton(
                                selected = themeMode == i,
                                onClick = { vm.setThemeMode(i) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = i,
                                    count = ThemeNames.size,
                                ),
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
            item {
                SettingGroup(title = "About") {
                    SettingInfoRow(
                        icon = Icons.Filled.Book,
                        tint = Color(0xFFFF9F0A),
                        label = "Sources",
                        sublabel = "Telang (1882) and Arnold renderings, public domain. " +
                            "Numbering follows Gita Press.",
                    )
                    SettingInfoRow(
                        icon = Icons.Filled.Info,
                        tint = Color(0xFF0A84FF),
                        label = "Recension",
                        sublabel = "The Kashmir recension differs in places. " +
                            "This app follows the vulgate text.",
                    )
                    SettingInfoRow(
                        icon = Icons.Filled.Lock,
                        tint = Color(0xFF30D158),
                        label = "Privacy",
                        sublabel = "No account, no network, no tracking. Progress " +
                            "and saved verses never leave this phone.",
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingGroup(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    Column(modifier = Modifier.padding(top = 4.dp)) {
        content()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconTile(icon = icon, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingInfoRow(icon: ImageVector, tint: Color, label: String, sublabel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        IconTile(icon = icon, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
