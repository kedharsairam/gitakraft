package com.gitakraft.app.data

import com.gitakraft.app.BuildConfig
import com.gitakraft.app.domain.Updates
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val info: Updates.Available) : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * Manual update check against GitHub Releases. Called only from the
 * settings row — never automatically, never in the background.
 * HttpURLConnection (no new dependencies); 10s timeouts; every
 * failure collapses to [UpdateCheck.Failed], including 404 (no repo
 * or no releases published yet).
 */
object UpdateChecker {
    suspend fun checkLatest(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(Updates.LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GitaKraft")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext UpdateCheck.Failed
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (body.isBlank()) return@withContext UpdateCheck.Failed
            val release = try {
                Updates.parseRelease(body)
            } catch (_: Exception) {
                return@withContext UpdateCheck.Failed
            }
            val available = Updates.toAvailable(release, BuildConfig.VERSION_NAME)
                ?: return@withContext UpdateCheck.UpToDate
            UpdateCheck.Available(available)
        } catch (_: IOException) {
            UpdateCheck.Failed
        } catch (_: Exception) {
            UpdateCheck.Failed
        }
    }
}
