package com.gitakraft.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors tools/content export_app output (gita-bundle.json). */
@Serializable
data class BundleFile(
    val bundle: Int,
    val generated: String,
    val chapters: List<BundleChapter>,
    val verses: List<BundleVerse>,
    val feelings: List<BundleFeeling>,
)

@Serializable
data class BundleChapter(
    val n: Int,
    val name: String,
    val title: String,
    val verses: Int,
)

@Serializable
data class BundleVerse(
    val id: String,
    val ch: Int,
    val n: Int,
    val speaker: String? = null,
    val devanagari: String,
    val iast: String = "",
    val meaning: String,
    val takeaway: String,
)

@Serializable
data class BundleFeeling(
    val name: String,
    val line: String,
    val verses: List<String>,
)

object Bundle {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parse + consistency-check. Throws IllegalArgumentException naming the defect. */
    fun parse(bytes: ByteArray): BundleFile {
        val file = json.decodeFromString<BundleFile>(bytes.decodeToString())
        require(file.bundle == 1) { "unsupported bundle version ${file.bundle}" }
        require(file.chapters.size == 18) { "want 18 chapters, got ${file.chapters.size}" }
        val byChapter = file.verses.groupBy { it.ch }
        for (ch in file.chapters) {
            val vs = (byChapter[ch.n] ?: emptyList()).sortedBy { it.n }
            require(vs.size == ch.verses) {
                "ch${ch.n}: want ${ch.verses} verses, got ${vs.size}"
            }
            require(vs.map { it.n } == (1..ch.verses).toList()) {
                "ch${ch.n}: verse numbers not contiguous 1..${ch.verses}"
            }
            for (v in vs) {
                require(v.id == "${ch.n}:${v.n}") { "bad id ${v.id}" }
                require(v.devanagari.isNotBlank()) { "${v.id}: blank devanagari" }
                require(v.meaning.isNotBlank()) { "${v.id}: blank meaning" }
                require(v.takeaway.isNotBlank()) { "${v.id}: blank takeaway" }
            }
        }
        val ids = file.verses.map { it.id }.toSet()
        for (f in file.feelings) {
            require(f.verses.isNotEmpty()) { "feeling ${f.name}: empty" }
            val bad = f.verses.filter { it !in ids }
            require(bad.isEmpty()) { "feeling ${f.name}: dangling refs $bad" }
        }
        return file
    }
}
