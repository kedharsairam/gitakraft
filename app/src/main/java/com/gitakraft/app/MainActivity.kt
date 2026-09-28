package com.gitakraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.data.SettingsRepo
import com.gitakraft.app.nav.AppNav
import com.gitakraft.app.ui.LocalFontScale
import com.gitakraft.app.ui.RepoFactory
import com.gitakraft.app.ui.theme.GitaKraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        val repo = GitaRepository.get(this)
        val settings = SettingsRepo(this)
        val factory = RepoFactory(repo, settings)
        setContent {
            val fontScale by settings.fontScale.collectAsState(initial = 1f)
            val iastDefault by settings.iastDefault.collectAsState(initial = true)
            // Dark-only: no theme switch, no system check.
            GitaKraftTheme {
                CompositionLocalProvider(LocalFontScale provides fontScale) {
                    AppNav(factory = factory, iastDefault = iastDefault)
                }
            }
        }
    }
}
