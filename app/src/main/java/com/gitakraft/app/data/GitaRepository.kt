package com.gitakraft.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.gitakraft.app.domain.Reading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val META_BUNDLE = "bundle_generated"

/** Owns the database, one-shot seeding, and all reads/writes. */
class GitaRepository private constructor(private val db: GitaDatabase) {
    private val dao = db.dao()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Parse asset + seed inside one transaction; skips when already seeded. */
    suspend fun ensureSeeded(asset: ByteArray) {
        if (_ready.value) return
        val file = Bundle.parse(asset)
        val stamped = dao.meta(META_BUNDLE)
        if (stamped == file.generated && dao.verseCount() == file.verses.size) {
            _ready.value = true
            return
        }
        db.withTransaction {
            dao.clearFeelingVerses()
            dao.clearFeelings()
            dao.clearVerses()
            dao.clearChapters()
            dao.insertChapters(
                file.chapters.map { ChapterRow(it.n, it.name, it.title, it.verses) },
            )
            dao.insertVerses(
                file.verses.map {
                    VerseRow(it.id, it.ch, it.n, it.speaker, it.devanagari,
                        it.iast, it.meaning, it.takeaway)
                },
            )
            dao.insertFeelings(file.feelings.map { FeelingRow(it.name, it.line) })
            dao.insertFeelingVerses(
                file.feelings.flatMap { f ->
                    f.verses.mapIndexed { i, vid ->
                        FeelingVerseRow(f.name, vid, i)
                    }
                },
            )
            dao.putMeta(MetaRow(META_BUNDLE, file.generated))
        }
        _ready.value = true
    }

    fun chapters(): Flow<List<ChapterRow>> = dao.chapters()
    fun versesInChapter(ch: Int): Flow<List<VerseRow>> = dao.versesInChapter(ch)
    fun verse(id: String): Flow<VerseRow?> = dao.verse(id)
    fun feelings(): Flow<List<FeelingRow>> = dao.feelings()
    fun versesForFeeling(name: String): Flow<List<VerseRow>> = dao.versesForFeeling(name)
    fun bookmarks(): Flow<List<VerseRow>> = dao.bookmarks()
    fun isBookmarked(id: String): Flow<Boolean> = dao.isBookmarked(id)

    /** FTS over meaning + takeaway + IAST. Blank input short-circuits. */
    suspend fun search(raw: String): List<VerseRow> {
        val q = Reading.sanitizeFts(raw)
        if (q.isBlank()) return emptyList()
        return dao.search(q)
    }

    suspend fun markRead(id: String) {
        dao.markRead(ProgressRow(id, System.currentTimeMillis()))
    }

    suspend fun toggleBookmark(id: String, marked: Boolean) {
        if (marked) dao.addBookmark(BookmarkRow(id, System.currentTimeMillis()))
        else dao.removeBookmark(id)
    }

    /** Chapter unlock map, derived from read ids — never stored. */
    fun unlocks(counts: Map<Int, Int>): Flow<Map<Int, Boolean>> =
        dao.readIds().map { ids ->
            val set = ids.toSet()
            counts.keys.associateWith { Reading.isUnlocked(it, set, counts) }
        }

    fun readPerChapter(): Flow<List<ChapterRead>> = dao.readPerChapter()

    companion object {
        @Volatile
        private var instance: GitaRepository? = null

        fun get(context: Context): GitaRepository =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): GitaRepository {
            lateinit var repo: GitaRepository
            val db = Room.databaseBuilder(context, GitaDatabase::class.java, "gita.db")
                .build()
            repo = GitaRepository(db)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val bytes = context.assets.open("gita-bundle.json").use { it.readBytes() }
                repo.ensureSeeded(bytes)
            }
            return repo
        }
    }
}

/** Per-chapter progress with unlock flags for the library screen. */
data class ChapterProgress(
    val chapter: ChapterRow,
    val read: Int,
    val unlocked: Boolean,
)

fun libraryProgress(
    chapters: List<ChapterRow>,
    reads: List<ChapterRead>,
    readIds: Set<String>,
): List<ChapterProgress> {
    val counts = chapters.associate { it.n to it.verseCount }
    val byCh = reads.associate { it.ch to it.read }
    return chapters.map {
        ChapterProgress(it, byCh[it.n] ?: 0, Reading.isUnlocked(it.n, readIds, counts))
    }
}
