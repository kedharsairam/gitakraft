package com.gitakraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.nav.Routes
import com.gitakraft.app.ui.ChapterScreen
import com.gitakraft.app.ui.ChapterViewModel
import com.gitakraft.app.ui.LibraryScreen
import com.gitakraft.app.ui.LibraryViewModel
import com.gitakraft.app.ui.ReaderScreen
import com.gitakraft.app.ui.ReaderViewModel
import com.gitakraft.app.ui.RepoFactory
import com.gitakraft.app.ui.theme.GitaKraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = GitaRepository.get(this)
        val factory = RepoFactory(repo)
        setContent {
            GitaKraftTheme {
                val nav = rememberNavController()
                // Chapter titles for headers; falls back to the number alone.
                val chapters by repo.chapters().collectAsState(initial = emptyList())
                val titleOf = remember(chapters) {
                    chapters.associate { it.n to it.title }
                }
                val countOf = remember(chapters) {
                    chapters.associate { it.n to it.verseCount }
                }
                NavHost(navController = nav, startDestination = Routes.LIBRARY) {
                    composable(Routes.LIBRARY) {
                        val vm: LibraryViewModel = viewModel(factory = factory)
                        LibraryScreen(
                            vm = vm,
                            onChapter = { nav.navigate(Routes.chapter(it)) },
                            onVerse = { nav.navigate(Routes.reader(it)) },
                        )
                    }
                    composable(
                        route = Routes.CHAPTER,
                        arguments = listOf(navArgument("n") { type = NavType.IntType }),
                    ) { entry ->
                        val n = entry.arguments?.getInt("n") ?: 1
                        val vm: ChapterViewModel = viewModel(factory = factory)
                        ChapterScreen(
                            vm = vm,
                            n = n,
                            title = titleOf[n] ?: "",
                            verseCount = countOf[n] ?: 0,
                            onBack = { nav.popBackStack() },
                            onVerse = { nav.navigate(Routes.reader(it)) },
                        )
                    }
                    composable(
                        route = Routes.READER,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id") ?: "1:1"
                        val vm: ReaderViewModel = viewModel(factory = factory)
                        ReaderScreen(
                            vm = vm,
                            id = id,
                            onBack = { nav.popBackStack() },
                            onNavigate = {
                                nav.navigate(Routes.reader(it)) {
                                    popUpTo(Routes.reader(id)) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
