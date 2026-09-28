package com.gitakraft.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitakraft.app.ui.theme.VerseIast
import com.gitakraft.app.ui.theme.VerseSanskrit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DWELL_MS = 1500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    id: String,
    iastDefault: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val verse by vm.verse(id).collectAsState(initial = null)
    val bookmarked by vm.isBookmarked(id).collectAsState(initial = false)
    val context = LocalContext.current
    val fontScale = LocalFontScale.current
    var showIast by rememberSaveable(iastDefault) { mutableStateOf(iastDefault) }

    // Dwell-based read marking: cancelled automatically when [id] changes.
    LaunchedEffect(id) {
        delay(DWELL_MS)
        vm.markRead(id)
    }

    val v = verse
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Sibling navigation is hoisted: the pinned bottom bar needs it too.
    val (pch, _) = id.split(":").map { it.toInt() }
    val siblings by vm.versesInChapter(pch).collectAsState(initial = emptyList())
    val idx = if (v == null) -1 else siblings.indexOfFirst { it.id == v.id }
    val prevId = siblings.getOrNull(idx - 1)?.id
    val nextId = siblings.getOrNull(idx + 1)?.id
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(id) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (v != null) {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Bhagavad Gita ${v.id}\n\n${v.meaning}\n\n— via GitaKraft",
                                            )
                                        },
                                        null,
                                    ),
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share verse",
                            )
                        }
                        IconButton(
                            onClick = {
                                val was = bookmarked
                                vm.toggleBookmark(id, !was)
                                scope.launch {
                                    val r = snackbar.showTimed(
                                        if (!was) {
                                            "Verse $id saved"
                                        } else {
                                            "Verse $id removed from library"
                                        },
                                        actionLabel = "Undo",
                                    )
                                    if (r == SnackbarResult.ActionPerformed) {
                                        vm.toggleBookmark(id, was)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (bookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                contentDescription = if (bookmarked) {
                                    "Bookmarked"
                                } else {
                                    "Bookmark"
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Pinned transport: counter, hairline and prev/next never
            // scroll with the verse — they behave like a bottom bar.
            if (v != null && siblings.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    LinearProgressIndicator(
                        progress = { (idx + 1).toFloat() / siblings.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Uniform 120dp solid buttons: equal widths center the
                        // counter geometrically; matching solid fills center
                        // it optically (an outline against a fill reads
                        // off-center even when the math is exact).
                        FilledTonalButton(
                            onClick = { prevId?.let(onNavigate) },
                            enabled = prevId != null,
                            modifier = Modifier.width(120.dp),
                        ) {
                            Text("Previous")
                        }
                        Text(
                            text = "${idx + 1} of ${siblings.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                // Pressing Next means this verse is read:
                                // dwell alone misses fast tappers, which used
                                // to starve the chapter-complete sheet.
                                vm.markRead(id)
                                nextId?.let(onNavigate)
                            },
                            enabled = nextId != null,
                            modifier = Modifier.width(120.dp),
                        ) {
                            Text("Next")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { AppSnackbarLifted(it) } },
    ) { padding ->
        if (v == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val ch = pch

        // Chapter-complete sheet: last verse read, everything read.
        var celebrated by rememberSaveable(id) { mutableStateOf(false) }
        if (nextId == null && !celebrated && siblings.isNotEmpty()) {
            ChapterCompleteGate(
                vm = vm,
                ch = ch,
                onCelebrate = { celebrated = true },
                onNextChapter = { onNavigate("${ch + 1}:1") },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = v.devanagari,
                style = VerseSanskrit.copy(
                    fontSize = VerseSanskrit.fontSize * fontScale,
                    lineHeight = VerseSanskrit.lineHeight * fontScale,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showIast && v.iast.isNotBlank()) {
                Text(
                    text = v.iast,
                    style = VerseIast.copy(fontSize = VerseIast.fontSize * fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            TextButton(onClick = { showIast = !showIast }) {
                Text(if (showIast) "Hide transliteration" else "Show transliteration")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = v.meaning,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale,
                ),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TAKEAWAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = v.takeaway,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                        ),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Shows the completion dialog once the last verse's chapter is fully read. */
@Composable
private fun ChapterCompleteGate(
    vm: ReaderViewModel,
    ch: Int,
    onCelebrate: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val siblings by vm.versesInChapter(ch).collectAsState(initial = emptyList())
    val readIds by vm.readIds().collectAsState(initial = emptySet())
    val complete = siblings.isNotEmpty() &&
        siblings.all { it.id in readIds }
    if (complete) {
        AlertDialog(
            onDismissRequest = onCelebrate,
            title = { Text("Chapter $ch complete") },
            text = { Text("Well read. The journey continues.") },
            confirmButton = {
                if (ch < 18) {
                    Button(
                        onClick = {
                            onCelebrate()
                            onNextChapter()
                        },
                    ) {
                        Text("Read Chapter ${ch + 1}")
                    }
                } else {
                    Button(onClick = onCelebrate) {
                        Text("The Gita is complete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onCelebrate) {
                    Text("Stay")
                }
            },
        )
    }
}
