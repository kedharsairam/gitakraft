package com.gitakraft.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** Reading settings. Everything local, everything instant. */
class SettingsRepo(context: Context) {
    private val store = context.settingsStore

    /** Reading text scale, 0.85..1.3. */
    val fontScale: Flow<Float> = store.data.map { it[Keys.FONT] ?: 1f }

    /** IAST transliteration visible by default. */
    val iastDefault: Flow<Boolean> = store.data.map { it[Keys.IAST] ?: true }

    /** First-launch welcome shown. False on fresh install (show it once). */
    val welcomed: Flow<Boolean> = store.data.map { it[Keys.WELCOMED] ?: false }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[Keys.FONT] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setIastDefault(show: Boolean) {
        store.edit { it[Keys.IAST] = show }
    }

    suspend fun setWelcomed() {
        store.edit { it[Keys.WELCOMED] = true }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private object Keys {
        val FONT = floatPreferencesKey("font_scale")
        val IAST = booleanPreferencesKey("iast_default")
        val WELCOMED = booleanPreferencesKey("welcomed")
    }
}
