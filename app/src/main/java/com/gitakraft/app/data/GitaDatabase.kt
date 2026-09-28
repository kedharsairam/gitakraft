package com.gitakraft.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chapters")
data class ChapterRow(
    @PrimaryKey val n: Int,
    val name: String,
    val title: String,
    val verseCount: Int,
)

@Entity(tableName = "verses")
data class VerseRow(
    @PrimaryKey val id: String, // "ch:n"
    val ch: Int,
    val n: Int,
    val speaker: String?,
    val devanagari: String,
    val iast: String,
    val meaning: String,
    val takeaway: String,
)

/** External-content FTS kept in sync by Room-generated triggers. */
@Fts4(contentEntity = VerseRow::class)
@Entity(tableName = "verses_fts")
data class VerseFts(
    val id: String,
    val meaning: String,
    val takeaway: String,
    val iast: String,
)

@Entity(tableName = "progress", primaryKeys = ["verseId"])
data class ProgressRow(val verseId: String, val readAt: Long)

@Entity(tableName = "bookmarks", primaryKeys = ["verseId"])
data class BookmarkRow(val verseId: String, val createdAt: Long)

@Entity(tableName = "feelings")
data class FeelingRow(@PrimaryKey val name: String, val line: String)

@Entity(tableName = "feeling_verses", primaryKeys = ["feeling", "position"])
data class FeelingVerseRow(val feeling: String, val verseId: String, val position: Int)

@Entity(tableName = "meta", primaryKeys = ["key"])
data class MetaRow(val key: String, val value: String)

data class ChapterRead(val ch: Int, val read: Int)

@Dao
interface GitaDao {
    // -- content (written only by the seeder) --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(rows: List<ChapterRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(rows: List<VerseRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeelings(rows: List<FeelingRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeelingVerses(rows: List<FeelingVerseRow>)

    @Query("DELETE FROM verses")
    suspend fun clearVerses()

    @Query("DELETE FROM chapters")
    suspend fun clearChapters()

    @Query("DELETE FROM feelings")
    suspend fun clearFeelings()

    @Query("DELETE FROM feeling_verses")
    suspend fun clearFeelingVerses()

    @Query("SELECT * FROM chapters ORDER BY n")
    fun chapters(): Flow<List<ChapterRow>>

    @Query("SELECT * FROM verses WHERE ch = :ch ORDER BY n")
    fun versesInChapter(ch: Int): Flow<List<VerseRow>>

    @Query("SELECT * FROM verses WHERE id = :id")
    fun verse(id: String): Flow<VerseRow?>

    @Query("SELECT COUNT(*) FROM verses")
    suspend fun verseCount(): Int

    @Query(
        "SELECT v.* FROM verses v JOIN verses_fts f ON v.rowid = f.rowid " +
            "WHERE verses_fts MATCH :q ORDER BY v.ch, v.n LIMIT 50",
    )
    suspend fun search(q: String): List<VerseRow>

    // -- progress --
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRead(row: ProgressRow)

    @Query(
        "SELECT v.ch AS ch, COUNT(p.verseId) AS read FROM verses v " +
            "LEFT JOIN progress p ON p.verseId = v.id GROUP BY v.ch",
    )
    fun readPerChapter(): Flow<List<ChapterRead>>

    @Query("SELECT verseId FROM progress")
    fun readIds(): Flow<List<String>>

    // -- bookmarks --
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookmark(row: BookmarkRow)

    @Query("DELETE FROM bookmarks WHERE verseId = :id")
    suspend fun removeBookmark(id: String)

    @Query("DELETE FROM bookmarks WHERE verseId IN (:ids)")
    suspend fun removeBookmarks(ids: List<String>)

    @Query(
        "SELECT v.* FROM verses v JOIN bookmarks b ON b.verseId = v.id " +
            "ORDER BY b.createdAt DESC",
    )
    fun bookmarks(): Flow<List<VerseRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE verseId = :id)")
    fun isBookmarked(id: String): Flow<Boolean>

    // -- feelings --
    @Query("SELECT * FROM feelings ORDER BY name")
    fun feelings(): Flow<List<FeelingRow>>

    @Query(
        "SELECT v.* FROM verses v JOIN feeling_verses f ON f.verseId = v.id " +
            "WHERE f.feeling = :name ORDER BY f.position",
    )
    fun versesForFeeling(name: String): Flow<List<VerseRow>>

    // -- meta --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(row: MetaRow)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?
}

@Database(
    entities = [
        ChapterRow::class, VerseRow::class, VerseFts::class,
        ProgressRow::class, BookmarkRow::class,
        FeelingRow::class, FeelingVerseRow::class, MetaRow::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GitaDatabase : RoomDatabase() {
    abstract fun dao(): GitaDao
}
