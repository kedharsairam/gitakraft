package com.gitakraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                // Chapter hero: giant numeral + title + woven-in progress.
                // Deliberately not the sticky strip from the library list.
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = devDigits(n),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Chapter $n",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "$read of $verseCount verses read",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LinearProgressIndicator(
                        progress = { Reading.fraction(read, verseCount) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
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
fun VerseListRow(
    verse: VerseRow,
    read: Boolean,
    onOpen: () -> Unit,
    numberLabel: String = "${verse.n}",
) {
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
            // Numeral badge: chapter rows carry rings + titles, verse rows
            // carry a tonal badge instead (Devanagari within a chapter,
            // full id like 2:47 across chapters in search/saved).
            val badge = if (numberLabel == "${verse.n}") {
                devDigits(verse.n)
            } else {
                numberLabel
            }
            // Full ids ("18:78") are wider than Devanagari numerals;
            // step down a size so long badges stay inside the circle.
            val badgeStyle = if (badge.length > 3) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(
                    text = badge,
                    style = badgeStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Text(
                text = verse.meaning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3,
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
