package com.gitakraft.app.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitakraft.app.ui.BookmarksScreen
import com.gitakraft.app.ui.BookmarksViewModel
import com.gitakraft.app.ui.ChapterScreen
import com.gitakraft.app.ui.ChaptersScreen
import com.gitakraft.app.ui.ChaptersViewModel
import com.gitakraft.app.ui.ChapterViewModel
import com.gitakraft.app.ui.FeelingDetailScreen
import com.gitakraft.app.ui.FeelingsViewModel
import com.gitakraft.app.ui.HomeScreen
import com.gitakraft.app.ui.HomeViewModel
import com.gitakraft.app.ui.ReaderScreen
import com.gitakraft.app.ui.ReaderViewModel
import com.gitakraft.app.ui.RepoFactory
import com.gitakraft.app.ui.SearchScreen
import com.gitakraft.app.ui.SearchViewModel
import com.gitakraft.app.ui.SettingsScreen
import com.gitakraft.app.ui.SettingsViewModel

@Composable
fun AppNav(factory: RepoFactory, iastDefault: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tabs = listOf(
        Triple(Routes.HOME, "Home", Icons.Filled.Home),
        Triple(Routes.CHAPTERS, "Chapters", Icons.Filled.Book),
        Triple(Routes.BOOKMARKS, "Saved", Icons.Filled.Bookmarks),
    )
    val showBar = entry?.destination?.route in tabs.map { it.first }
    Scaffold(
        // Screens own their top bars (each consumes the status-bar inset
        // exactly once). The shell must not add it again, or every screen
        // sits one status-bar too low. Bottom bar still offsets content.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    for ((route, label, icon) in tabs) {
                        NavigationBarItem(
                            selected = entry?.destination?.route == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(imageVector = icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(
                    vm = vm,
                    onVerse = { nav.navigate(Routes.reader(it)) },
                    onFeeling = { nav.navigate(Routes.feeling(it)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHAPTERS) {
                val vm: ChaptersViewModel = viewModel(factory = factory)
                ChaptersScreen(
                    vm = vm,
                    onChapter = { nav.navigate(Routes.chapter(it)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.BOOKMARKS) {
                val vm: BookmarksViewModel = viewModel(factory = factory)
                BookmarksScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onVerse = { nav.navigate(Routes.reader(it)) },
                )
            }
            composable(
                route = Routes.CHAPTER,
                arguments = listOf(navArgument("n") { type = NavType.IntType }),
            ) { e ->
                val n = e.arguments?.getInt("n") ?: 1
                val vm: ChapterViewModel = viewModel(factory = factory)
                ChapterScreen(
                    vm = vm,
                    n = n,
                    onBack = { nav.popBackStack() },
                    onVerse = { nav.navigate(Routes.reader(it)) },
                )
            }
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                val id = e.arguments?.getString("id") ?: "1:1"
                val vm: ReaderViewModel = viewModel(factory = factory)
                ReaderScreen(
                    vm = vm,
                    id = id,
                    iastDefault = iastDefault,
                    onBack = { nav.popBackStack() },
                    onNavigate = {
                        nav.navigate(Routes.reader(it)) {
                            popUpTo(Routes.reader(id)) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.FEELING,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { e ->
                val name = e.arguments?.getString("name") ?: ""
                val vm: FeelingsViewModel = viewModel(factory = factory)
                FeelingDetailScreen(
                    vm = vm,
                    name = name,
                    onBack = { nav.popBackStack() },
                    onVerse = { nav.navigate(Routes.reader(it)) },
                )
            }
            composable(Routes.SEARCH) {
                val vm: SearchViewModel = viewModel(factory = factory)
                SearchScreen(vm = vm, onVerse = { nav.navigate(Routes.reader(it)) })
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
