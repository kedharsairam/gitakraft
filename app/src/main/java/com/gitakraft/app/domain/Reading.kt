package com.gitakraft.app.domain

/** Pure reading rules — the only logic the unlock/progress system needs. */
object Reading {

    /**
     * Chapter [ch] (1-based) is unlocked iff it is the first chapter or the
     * previous chapter is fully read. Derived from read-verse ids only —
     * unlock state is never stored, so it cannot drift out of sync.
     */
    fun isUnlocked(ch: Int, readIds: Set<String>, counts: Map<Int, Int>): Boolean {
        if (ch <= 1) return true
        val prev = ch - 1
        val want = counts[prev] ?: return false
        return (1..want).all { "$prev:$it" in readIds }
    }

    /** Chapters fully read, in ascending order. */
    fun completedChapters(readIds: Set<String>, counts: Map<Int, Int>): List<Int> =
        counts.keys.sorted().filter { ch ->
            val want = counts[ch] ?: return@filter false
            (1..want).all { "$ch:$it" in readIds }
        }

    /** 0..1 fraction of [read] over [total]. Guards divide-by-zero. */
    fun fraction(read: Int, total: Int): Float =
        if (total <= 0) 0f else (read.coerceIn(0, total).toFloat() / total)

    /**
     * Deterministic verse-of-the-day: spreads consecutive days across
     * chapters round-robin so the pick varies in both chapter and verse.
     * [dayOfYear] is 1-based; returns a "ch:n" id.
     */
    fun verseOfDay(dayOfYear: Int, counts: Map<Int, Int>): String {
        val ordered = counts.keys.sorted()
        require(ordered.isNotEmpty()) { "no chapters" }
        val ch = ordered[(dayOfYear - 1).mod(ordered.size)]
        val total = counts.getValue(ch)
        val n = ((dayOfYear - 1) / ordered.size).mod(total) + 1
        return "$ch:$n"
    }

    /**
     * Sanitize free text for an FTS4 MATCH query. Strips every FTS
     * metacharacter, keeps per-token prefix matching (implicit AND).
     * Returns "" when nothing searchable remains — callers must skip
     * the MATCH query in that case (bare MATCH '' is a syntax error).
     */
    fun sanitizeFts(raw: String): String =
        raw.split(Regex("\\s+"))
            .map { tok -> tok.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
}
