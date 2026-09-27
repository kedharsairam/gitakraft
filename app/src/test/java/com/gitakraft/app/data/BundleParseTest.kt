package com.gitakraft.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleParseTest {
    private fun realBundle(): BundleFile {
        val f = File("src/main/assets/gita-bundle.json")
        assertTrue("bundle asset missing", f.exists())
        return Bundle.parse(f.readBytes())
    }

    @Test
    fun parses700Verses18Chapters12Feelings() {
        val b = realBundle()
        assertEquals(18, b.chapters.size)
        assertEquals(700, b.verses.size)
        assertEquals(12, b.feelings.size)
        assertEquals(700, b.verses.sumOf { 1 })
    }

    @Test
    fun verseIdsContiguous() {
        val b = realBundle()
        val counts = b.chapters.associate { it.n to it.verses }
        for ((ch, want) in counts) {
            val got = b.verses.filter { it.ch == ch }.map { it.n }.sorted()
            assertEquals((1..want).toList(), got)
        }
    }

    @Test
    fun spotCheckFamousVerses() {
        val b = realBundle()
        val byId = b.verses.associateBy { it.id }
        assertTrue(byId.getValue("2:47").meaning.isNotBlank())
        assertTrue(byId.getValue("11:32").meaning.isNotBlank())
        assertTrue(byId.getValue("18:66").takeaway.isNotBlank())
        assertTrue(byId.getValue("18:78").devanagari.isNotBlank())
    }

    @Test
    fun rejectsDanglingFeelingRef() {
        val b = realBundle()
        val broken = b.copy(
            feelings = listOf(BundleFeeling("X", "y", listOf("99:99"))),
        )
        // Re-encode through the parser path via a tampered copy.
        try {
            Bundle.parse(
                kotlinx.serialization.json.Json.encodeToString(
                    BundleFile.serializer(),
                    broken,
                ).toByteArray(),
            )
            throw AssertionError("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("99:99"))
        }
    }
}
