package com.gitakraft.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitakraft.app.data.ChapterProgress
import com.gitakraft.app.domain.Reading
import com.kraft.ui.tokens.KraftSpacing
import com.gitakraft.app.ui.theme.GitaMetrics

private val DEV_DIGITS = listOf("०", "१", "२", "३", "४", "५", "६", "७", "८", "९")

/** Chapter number in Devanagari digits: 1 -> १, 12 -> १२. */
fun devDigits(n: Int): String =
    n.toString().map { DEV_DIGITS[it.digitToInt()] }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    vm: ChaptersViewModel,
    onChapter: (Int) -> Unit,
    onSearch: () -> Unit,
) {
    val rows by vm.rows.collectAsState()
    val totalRead by vm.totalRead.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chapters") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search verses",
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
            contentPadding = PaddingValues(bottom = GitaMetrics.AboveTabBar),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            stickyHeader {
                Surface(shadowElevation = KraftSpacing.Spacing2) {
                    Column(modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing8)) {
                        Text(
                            text = "$totalRead of 700 verses",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LinearProgressIndicator(
                            progress = { Reading.fraction(totalRead, 700) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = KraftSpacing.Spacing8)
                                .clip(MaterialTheme.shapes.small)
                                .semantics {
                                    contentDescription =
                                        "$totalRead of 700 verses read"
                                },
                        )
                    }
                }
            }
            items(rows, key = { it.chapter.n }) { row ->
                ChapterRow(row = row, onOpen = { onChapter(row.chapter.n) })
            }
        }
    }
}

@Composable
private fun ChapterRow(row: ChapterProgress, onOpen: () -> Unit) {
    val fraction = Reading.fraction(row.read, row.chapter.verseCount)
    val complete = row.read >= row.chapter.verseCount
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing16)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KraftSpacing.Spacing16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(KraftSpacing.Spacing56),
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .size(KraftSpacing.Spacing56)
                        .semantics {
                            contentDescription =
                                "${row.read} of ${row.chapter.verseCount} read"
                        },
                )
                Text(
                    text = devDigits(row.chapter.n),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
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
                    text = if (complete) {
                        "${row.chapter.verseCount} verses · complete"
                    } else {
                        "${row.read} of ${row.chapter.verseCount} verses read"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = KraftSpacing.Spacing4),
                )
            }
            if (complete) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Chapter complete",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
