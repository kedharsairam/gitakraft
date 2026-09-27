package com.gitakraft.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    private val counts = mapOf(1 to 47, 2 to 72, 3 to 43)

    @Test
    fun chapterOneAlwaysUnlocked() {
        assertTrue(Reading.isUnlocked(1, emptySet(), counts))
    }

    @Test
    fun chapterTwoLockedUntilOneComplete() {
        assertFalse(Reading.isUnlocked(2, emptySet(), counts))
        val partial = (1..46).map { "1:$it" }.toSet()
        assertFalse(Reading.isUnlocked(2, partial, counts))
        val full = (1..47).map { "1:$it" }.toSet()
        assertTrue(Reading.isUnlocked(2, full, counts))
    }

    @Test
    fun unlockChainNeedsImmediatePredecessor() {
        // Ch1+Ch2 done unlocks 3; Ch1 alone does not unlock 3.
        val one = (1..47).map { "1:$it" }.toSet()
        assertFalse(Reading.isUnlocked(3, one, counts))
        val two = one + (1..72).map { "2:$it" }.toSet()
        assertTrue(Reading.isUnlocked(3, two, counts))
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
