package com.gitakraft.app.nav

/** Full app map: three tabs (read · feelings · saved) + detail routes. */
object Routes {
    const val LIBRARY = "library"
    const val FEELINGS = "feelings"
    const val BOOKMARKS = "bookmarks"
    const val CHAPTER = "chapter/{n}"
    const val READER = "reader/{id}"
    const val FEELING = "feeling/{name}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun chapter(n: Int) = "chapter/$n"
    fun reader(id: String) = "reader/$id"
    fun feeling(name: String) = "feeling/$name"
}
