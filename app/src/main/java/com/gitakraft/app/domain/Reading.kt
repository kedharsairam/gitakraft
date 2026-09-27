package com.gitakraft.app.domain

/** Pure reading rules — progress math and journey helpers. */
object Reading {

    /** Chapters fully read, in ascending order. */
    fun completedChapters(readIds: Set<String>, counts: Map<Int, Int>): List<Int> =
        counts.keys.sorted().filter { ch ->
            val want = counts[ch] ?: return@filter false
            (1..want).all { "$ch:$it" in readIds }
        }

    /** First unread verse id ("ch:n"), earliest chapter first; null when done. */
    fun continueFrom(readIds: Set<String>, counts: Map<Int, Int>): String? {
        for (ch in counts.keys.sorted()) {
            val want = counts[ch] ?: continue
            for (n in 1..want) {
                if ("$ch:$n" !in readIds) return "$ch:$n"
            }
        }
        return null
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
