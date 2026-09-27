package com.gitakraft.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.gitakraft.app.data.ChapterProgress
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.data.VerseRow
import com.gitakraft.app.data.libraryProgress
import com.gitakraft.app.domain.Reading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Shared factory: ViewModels take the repository as a plain parameter. */
class RepoFactory(private val repo: GitaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(repo) as T
            modelClass.isAssignableFrom(ChapterViewModel::class.java) ->
                ChapterViewModel(repo) as T
            modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                ReaderViewModel(repo) as T
            else -> throw IllegalArgumentException(modelClass.name)
        }
}

class LibraryViewModel(private val repo: GitaRepository) : ViewModel() {
    val ready: StateFlow<Boolean> = repo.ready

    private val chapters = repo.chapters()
    private val reads = repo.readPerChapter()

    val rows: StateFlow<List<ChapterProgress>> =
        combine(chapters, reads, repo.readIds()) { chs, rds, ids ->
            libraryProgress(chs, rds, ids.toSet())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRead: StateFlow<Int> =
        reads.map { list -> list.sumOf { it.read } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
}

class ChapterViewModel(private val repo: GitaRepository) : ViewModel() {
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
