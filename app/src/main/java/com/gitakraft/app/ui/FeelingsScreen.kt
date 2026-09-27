package com.gitakraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val FEELING_ICONS: Map<String, ImageVector> = mapOf(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeelingsScreen(vm: FeelingsViewModel, onFeeling: (String) -> Unit) {
    val feelings by vm.feelings().collectAsState(initial = emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text("How are you feeling?") }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Choose what weighs on you. Verses gathered for that state.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (f in feelings) {
                    FilterChip(
                        selected = false,
                        onClick = { onFeeling(f.name) },
                        label = { Text(f.name) },
                        leadingIcon = {
                            FEELING_ICONS[f.name]?.let {
                                Icon(imageVector = it, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeelingDetailScreen(
    vm: FeelingsViewModel,
    name: String,
    onBack: () -> Unit,
    onVerse: (String) -> Unit,
) {
    val feelings by vm.feelings().collectAsState(initial = emptyList())
    val verses by vm.versesForFeeling(name).collectAsState(initial = emptyList())
    val line = feelings.firstOrNull { it.name == name }?.line ?: ""
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to feelings",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (line.isNotBlank()) {
                item {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(verses, key = { it.id }) { v ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { onVerse(v.id) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = v.id,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = v.takeaway,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
