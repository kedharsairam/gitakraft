package com.gitakraft.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitakraft.app.data.VerseRow

@Composable
fun VerseSearchRow(verse: VerseRow, onOpen: () -> Unit) {
    VerseListRow(verse = verse, read = false, onOpen = onOpen, numberLabel = verse.id)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedVerseCard(
    verse: VerseRow,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = if (selected) {
                        "Selected"
                    } else {
                        "Not selected"
                    },
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verse.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = verse.takeaway,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove verse ${verse.id} from saved",
                    )
                }
            }
        }
    }
}

private val SEARCH_SUGGESTIONS = listOf(
    "duty",
    "fear",
    "anger",
    "peace",
    "surrender",
    "soul",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onVerse: (String) -> Unit,
    onBack: () -> Unit,
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    // Autofocus on entry: the field takes focus and the keyboard opens.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                onSearch = { keyboard?.hide() },
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search 700 verses…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp)
                    .focusRequester(focusRequester),
            ) {}
            if (query.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Search meanings and takeaways.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = "Try a word below to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 24.dp)
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (s in SEARCH_SUGGESTIONS) {
                            FilterChip(
                                selected = false,
                                onClick = { vm.query.value = s },
                                label = { Text(s) },
                            )
                        }
                    }
                }
            } else if (results.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No matches for “$query”.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Try a single word, or browse by feeling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    text = "${results.size} result${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.id }) { v ->
                        VerseSearchRow(verse = v, onOpen = { onVerse(v.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(vm: BookmarksViewModel, onVerse: (String) -> Unit) {
    val marks by vm.bookmarks().collectAsState(initial = emptyList())
    val chapters by vm.chapters().collectAsState(initial = emptyList())
    val titleOf = chapters.associate { it.n to it.title }
    val groups = marks.groupBy { it.ch }.toSortedMap()
    var selection by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    val selecting = selection.isNotEmpty()

    // Every removal — single or batch — asks first. Nothing vanishes silently.
    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    if (ids.size == 1) {
                        "Remove this verse?"
                    } else {
                        "Remove ${ids.size} verses?"
                    },
                )
            },
            text = {
                Text(
                    if (ids.size == 1) {
                        "Verse ${ids.first()} will leave your library. " +
                            "You can save it again any time."
                    } else {
                        "These ${ids.size} verses will leave your library. " +
                            "You can save them again any time."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeMany(ids)
                        selection = emptySet()
                        pendingDelete = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selecting) "${selection.size} selected" else "Saved")
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear selection",
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { pendingDelete = selection }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete selected",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (marks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No saved verses yet.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Star any verse while reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = "${marks.size} saved verse${if (marks.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            groups.forEach { (ch, verses) ->
                stickyHeader {
                    Surface(shadowElevation = 2.dp) {
                        Text(
                            text = "Chapter $ch · ${titleOf[ch] ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(verses, key = { it.id }) { v ->
                    SavedVerseCard(
                        verse = v,
                        selected = v.id in selection,
                        selectionMode = selecting,
                        onOpen = { onVerse(v.id) },
                        onToggleSelect = {
                            selection = if (v.id in selection) {
                                selection - v.id
                            } else {
                                selection + v.id
                            }
                        },
                        onRemove = { pendingDelete = setOf(v.id) },
                    )
                }
            }
        }
    }
}
