package com.gitakraft.app.nav

/** Minimum viable reading path: library -> chapter -> reader. */
object Routes {
    const val LIBRARY = "library"
    const val CHAPTER = "chapter/{n}"
    const val READER = "reader/{id}"

    fun chapter(n: Int) = "chapter/$n"
    fun reader(id: String) = "reader/$id"
}
