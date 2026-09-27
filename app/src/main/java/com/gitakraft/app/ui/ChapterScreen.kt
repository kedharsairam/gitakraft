package com.gitakraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitakraft.app.data.VerseRow
import com.gitakraft.app.domain.Reading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(
    vm: ChapterViewModel,
    n: Int,
    onBack: () -> Unit,
    onVerse: (String) -> Unit,
) {
    val chapters by vm.chapters().collectAsState(initial = emptyList())
    val verses by vm.verses(n).collectAsState(initial = emptyList())
    val readIds by vm.readIds().collectAsState(initial = emptyList())
    val meta = chapters.firstOrNull { it.n == n }
    val title = meta?.title ?: ""
    val verseCount = meta?.verseCount ?: 0
    val read = verses.count { it.id in readIds.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chapter $n · $title") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to library",
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                LinearProgressIndicator(
                    progress = { Reading.fraction(read, verseCount) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
            items(verses, key = { it.id }) { v ->
                VerseListRow(
                    verse = v,
                    read = v.id in readIds.toSet(),
                    onOpen = { onVerse(v.id) },
                )
            }
        }
    }
}

@Composable
fun VerseListRow(verse: VerseRow, read: Boolean, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${verse.n}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = verse.meaning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (read) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
