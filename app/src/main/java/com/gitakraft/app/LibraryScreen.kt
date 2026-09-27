package com.gitakraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitakraft.app.data.ChapterProgress
import com.gitakraft.app.domain.Reading
import com.gitakraft.app.ui.LibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onChapter: (Int) -> Unit,
    onVerse: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val ready by vm.ready.collectAsState()
    val rows by vm.rows.collectAsState()
    val totalRead by vm.totalRead.collectAsState()
    val verseOfDay by vm.verseOfDay.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitaKraft") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search verses",
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!ready) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Preparing the library…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "$totalRead of 700 verses",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { Reading.fraction(totalRead, 700) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .semantics {
                                contentDescription =
                                    "$totalRead of 700 verses read"
                            },
                    )
                }
            }
            verseOfDay?.let { vod ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onVerse(vod.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "VERSE OF THE DAY · ${vod.id}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = vod.takeaway,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            items(rows, key = { it.chapter.n }) { row ->
                ChapterRow(
                    row = row,
                    onOpen = { onChapter(row.chapter.n) },
                    onLocked = {
                        scope.launch {
                            snackbar.showSnackbar(
                                "Finish Chapter ${row.chapter.n - 1} to unlock “${row.chapter.title}”.",
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    row: ChapterProgress,
    onOpen: () -> Unit,
    onLocked: () -> Unit,
) {
    val fraction = Reading.fraction(row.read, row.chapter.verseCount)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { if (row.unlocked) onOpen() else onLocked() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (row.unlocked) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription =
                                "${row.read} of ${row.chapter.verseCount} read"
                        },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chapter ${row.chapter.n}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.chapter.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${row.read} of ${row.chapter.verseCount} verses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
