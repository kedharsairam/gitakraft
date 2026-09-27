package com.gitakraft.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** Reading + appearance settings. Everything local, everything instant. */
class SettingsRepo(context: Context) {
    private val store = context.settingsStore

    /** 0 = system, 1 = light, 2 = dark. */
    val themeMode: Flow<Int> = store.data.map { it[Keys.THEME] ?: 0 }

    /** Reading text scale, 0.85..1.3. */
    val fontScale: Flow<Float> = store.data.map { it[Keys.FONT] ?: 1f }

    /** IAST transliteration visible by default. */
    val iastDefault: Flow<Boolean> = store.data.map { it[Keys.IAST] ?: true }

    suspend fun setThemeMode(mode: Int) {
        store.edit { it[Keys.THEME] = mode.coerceIn(0, 2) }
    }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[Keys.FONT] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setIastDefault(show: Boolean) {
        store.edit { it[Keys.IAST] = show }
    }

    private object Keys {
        val THEME = intPreferencesKey("theme_mode")
        val FONT = floatPreferencesKey("font_scale")
        val IAST = booleanPreferencesKey("iast_default")
    }
}
