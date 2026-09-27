package com.gitakraft.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.gitakraft.app.ui.GlassTab
import com.gitakraft.app.ui.GlassTabBar
import com.gitakraft.app.ui.HomeScreen
import com.gitakraft.app.ui.HomeViewModel
import com.gitakraft.app.ui.ReaderScreen
import com.gitakraft.app.ui.ReaderViewModel
import com.gitakraft.app.ui.RepoFactory
import com.gitakraft.app.ui.SearchScreen
import com.gitakraft.app.ui.SearchViewModel
import com.gitakraft.app.ui.SettingsScreen
import com.gitakraft.app.ui.SettingsViewModel
import com.gitakraft.app.ui.glass.GlassBox
import com.gitakraft.app.ui.glass.GlassContainerWithHidden

@Composable
fun AppNav(factory: RepoFactory, iastDefault: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tabs = listOf(
        GlassTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        GlassTab(Routes.CHAPTERS, "Chapters", Icons.Filled.Book, Icons.Outlined.MenuBook),
        GlassTab(Routes.BOOKMARKS, "Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
        GlassTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    val showBar = entry?.destination?.route in tabs.map { it.route }
    Scaffold(
        // Invisible shell: screens own their top bars (status inset once).
        // The tab pill floats OVER content (WallKraft pattern) via the
        // liquid-glass container below — never a layout slot.
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // OEM gesture navs sometimes report a 0 inset; 8dp keeps the pill
        // above any gesture hint (WallKraft floor).
        val density = LocalDensity.current
        val navBarBottom = maxOf(
            WindowInsets.navigationBars.asPaddingValues(density).calculateBottomPadding(),
            8.dp,
        )
        GlassContainerWithHidden(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            hidden = !showBar,
            content = {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                ) {
                    composable(Routes.HOME) {
                        val vm: HomeViewModel = viewModel(factory = factory)
                        HomeScreen(
                            vm = vm,
                            onVerse = { nav.navigate(Routes.reader(it)) },
                            onFeeling = { nav.navigate(Routes.feeling(it)) },
                            onSearch = { nav.navigate(Routes.SEARCH) },
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
                        SettingsScreen(vm = vm)
                    }
                }
            },
            glassContent = {
                GlassBox(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBarBottom)
                        .padding(vertical = 2.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    blur = 0.95f,
                    scale = 0.12f,
                    centerDistortion = 0f,
                    shape = RoundedCornerShape(percent = 50),
                    elevation = 8.dp,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    darkness = 0.10f,
                    warpEdges = 0.22f,
                ) {
                    GlassTabBar(
                        tabs = tabs,
                        selectedRoute = entry?.destination?.route,
                        onTabClick = { tab ->
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        )
    }
}
