package com.gitakraft.app.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Update-check domain: pure version compare + release parsing. Network in data. */
object Updates {
    const val OWNER = "kedharsairam"
    const val REPO = "gitakraft"
    const val LATEST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    @Serializable
    data class ReleaseAsset(
        @SerialName("name") val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        @SerialName("size") val size: Long = 0L,
    )

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("body") val notes: String = "",
        @SerialName("assets") val assets: List<ReleaseAsset> = emptyList(),
    )

    data class Available(
        val tag: String,
        val version: String,
        val notes: String,
        val sizeBytes: Long,
        val apkUrl: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parseRelease(body: String): Release = json.decodeFromString(body)

    /**
     * Numeric dotted compare ("v1.2.10" > "v1.2.9"); leading v/V ignored.
     * Positive = [tag] newer than [current].
     */
    fun compareVersions(tag: String, current: String): Int {
        val t = tag.trim().removePrefix("v").removePrefix("V").split(".")
        val c = current.trim().removePrefix("v").removePrefix("V").split(".")
        for (i in 0 until maxOf(t.size, c.size)) {
            val a = t.getOrNull(i)?.toIntOrNull() ?: 0
            val b = c.getOrNull(i)?.toIntOrNull() ?: 0
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /** Newer release with a downloadable APK asset, or null. */
    fun toAvailable(release: Release, currentVersion: String): Available? {
        val tag = release.tag.trim()
        if (tag.isEmpty() || compareVersions(tag, currentVersion) <= 0) return null
        val apk = release.assets.firstOrNull {
            it.name.lowercase().endsWith(".apk") &&
                it.url.startsWith("https://github.com/")
        } ?: return null
        return Available(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            notes = release.notes,
            sizeBytes = apk.size,
            apkUrl = apk.url,
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${(kb * 10).roundToInt() / 10.0} KB"
        return "${(kb / 1024 * 10).roundToInt() / 10.0} MB"
    }
}
