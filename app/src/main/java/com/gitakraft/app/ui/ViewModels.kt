package com.gitakraft.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.gitakraft.app.data.ChapterProgress
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.data.SettingsRepo
import com.gitakraft.app.data.VerseRow
import com.gitakraft.app.data.libraryProgress
import com.gitakraft.app.domain.Reading
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Shared factory: ViewModels take the repository as a plain parameter. */
class RepoFactory(
    private val repo: GitaRepository,
    private val settings: SettingsRepo,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repo) as T
            modelClass.isAssignableFrom(ChaptersViewModel::class.java) ->
                ChaptersViewModel(repo) as T
            modelClass.isAssignableFrom(ChapterViewModel::class.java) ->
                ChapterViewModel(repo) as T
            modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                ReaderViewModel(repo) as T
            modelClass.isAssignableFrom(FeelingsViewModel::class.java) ->
                FeelingsViewModel(repo) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(repo) as T
            modelClass.isAssignableFrom(BookmarksViewModel::class.java) ->
                BookmarksViewModel(repo) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(repo, settings) as T
            else -> throw IllegalArgumentException(modelClass.name)
        }
}

class HomeViewModel(private val repo: GitaRepository) : ViewModel() {
    val ready: StateFlow<Boolean> = repo.ready

    private val chapters = repo.chapters()

    /** First unread verse id ("ch:n"), null when the whole Gita is read. */
    val continueTo: StateFlow<String?> =
        combine(chapters, repo.readIds()) { chs, ids ->
            Reading.continueFrom(
                ids.toSet(),
                chs.associate { it.n to it.verseCount },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Deterministic verse-of-the-day with its loaded row. */
    val verseOfDay: StateFlow<VerseRow?> =
        MutableStateFlow<VerseRow?>(null).also { out ->
            viewModelScope.launch {
                repo.ready.first { it }
                val counts = repo.chapters().first().associate { it.n to it.verseCount }
                val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                repo.verse(Reading.verseOfDay(day, counts)).collect { out.value = it }
            }
        }

    fun feelings() = repo.feelings()
}

class ChaptersViewModel(private val repo: GitaRepository) : ViewModel() {
    private val chapters = repo.chapters()
    private val reads = repo.readPerChapter()

    val rows: StateFlow<List<ChapterProgress>> =
        combine(chapters, reads) { chs, rds ->
            libraryProgress(chs, rds)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRead: StateFlow<Int> =
        reads.map { list -> list.sumOf { it.read } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

class ChapterViewModel(private val repo: GitaRepository) : ViewModel() {
    fun chapters() = repo.chapters()
    fun verses(ch: Int) = repo.versesInChapter(ch)
    fun readIds(): Flow<List<String>> = repo.readIds()
}

class ReaderViewModel(private val repo: GitaRepository) : ViewModel() {
    fun verse(id: String) = repo.verse(id)
    fun isBookmarked(id: String) = repo.isBookmarked(id)
    fun readIds(): Flow<Set<String>> = repo.readIds().map { it.toSet() }

    /** Dwell-based read marking: the screen calls this after ~1.5s visible. */
    fun markRead(id: String) {
        viewModelScope.launch { repo.markRead(id) }
    }

    fun toggleBookmark(id: String, marked: Boolean) {
        viewModelScope.launch { repo.toggleBookmark(id, marked) }
    }

    fun chapterCount(ch: Int): Flow<Int> =
        repo.versesInChapter(ch).map { it.size }

    fun versesInChapter(ch: Int) = repo.versesInChapter(ch)
}

class FeelingsViewModel(private val repo: GitaRepository) : ViewModel() {
    fun feelings() = repo.feelings()
    fun versesForFeeling(name: String) = repo.versesForFeeling(name)
}

class BookmarksViewModel(private val repo: GitaRepository) : ViewModel() {
    fun bookmarks() = repo.bookmarks()

    fun remove(id: String) {
        viewModelScope.launch { repo.toggleBookmark(id, false) }
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repo: GitaRepository) : ViewModel() {
    val query = MutableStateFlow("")

    val results: StateFlow<List<VerseRow>> =
        query.debounce(300).flatMapLatest { q ->
            flow { emit(repo.search(q)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class SettingsViewModel(
    private val repo: GitaRepository,
    private val settings: SettingsRepo,
) : ViewModel() {
    val fontScale = settings.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    val iastDefault = settings.iastDefault
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Journey stats moved off home: total progress, chapters, saved. */
    private val reads = repo.readPerChapter()

    val totalRead: StateFlow<Int> =
        reads.map { list -> list.sumOf { it.read } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> =
        combine(repo.chapters(), repo.readIds()) { chs, ids ->
            Reading.completedChapters(
                ids.toSet(),
                chs.associate { it.n to it.verseCount },
            ).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedCount: StateFlow<Int> =
        repo.bookmarks().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settings.setFontScale(scale) }
    }

    fun setIastDefault(show: Boolean) {
        viewModelScope.launch { settings.setIastDefault(show) }
    }
}
