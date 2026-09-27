package com.gitakraft.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    private val counts = mapOf(1 to 47, 2 to 72, 3 to 43)

    @Test
    fun journeyHelpers() {
        val one = (1..47).map { "1:$it" }.toSet()
        assertEquals(listOf(1), Reading.completedChapters(one, counts))
        assertEquals("2:1", Reading.continueFrom(one, counts))
        assertEquals(null, Reading.continueFrom(
            one + (1..72).map { "2:$it" } + (1..43).map { "3:$it" }, counts))
        assertEquals("1:1", Reading.continueFrom(emptySet(), counts))
    }

    @Test
    fun fractions() {
        assertEquals(0f, Reading.fraction(0, 47))
        assertEquals(1f, Reading.fraction(47, 47))
        assertEquals(0.5f, Reading.fraction(10, 20))
        assertEquals(0f, Reading.fraction(5, 0))
        assertEquals(1f, Reading.fraction(99, 47))
    }

    @Test
    fun verseOfDayStaysInRange() {
        val ids = (1..800).map { Reading.verseOfDay(it, counts) }.toSet()
        assertTrue(ids.size > 18) // spreads across chapters AND verses
        for (id in ids) {
            val (ch, n) = id.split(":").map { it.toInt() }
            assertTrue(n in 1..counts.getValue(ch))
        }
    }

    @Test
    fun verseOfDayDeterministic() {
        assertEquals(
            Reading.verseOfDay(100, counts),
            Reading.verseOfDay(100, counts),
        )
    }

    @Test
    fun sanitizeStripsFtsMetachars() {
        assertEquals("karma*", Reading.sanitizeFts("karma"))
        assertEquals("karma* yoga*", Reading.sanitizeFts("  karma   yoga "))
        assertEquals("1132*", Reading.sanitizeFts("\"11:32\""))
        assertEquals("", Reading.sanitizeFts("*** ::: \"\""))
        assertEquals("", Reading.sanitizeFts("   "))
    }
}
