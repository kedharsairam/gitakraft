package com.gitakraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.data.SettingsRepo
import com.gitakraft.app.nav.AppNav
import com.gitakraft.app.ui.LocalFontScale
import com.gitakraft.app.ui.RepoFactory
import com.gitakraft.app.ui.theme.GitaKraftTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = GitaRepository.get(this)
        val settings = SettingsRepo(this)
        val factory = RepoFactory(repo, settings)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = 0)
            val fontScale by settings.fontScale.collectAsState(initial = 1f)
            val iastDefault by settings.iastDefault.collectAsState(initial = true)
            val systemDark = isSystemInDarkTheme()
            val dark = remember(themeMode, systemDark) {
                when (themeMode) {
                    1 -> false
                    2 -> true
                    else -> systemDark
                }
            }
            GitaKraftTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalFontScale provides fontScale) {
                    AppNav(factory = factory, iastDefault = iastDefault)
                }
            }
        }
    }
}
