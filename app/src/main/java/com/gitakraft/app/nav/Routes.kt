package com.gitakraft.app.nav

/** Three tabs (home · chapters · saved) + detail routes. */
object Routes {
    const val HOME = "home"
    const val CHAPTERS = "chapters"
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
