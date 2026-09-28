package com.gitakraft.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.gitakraft.app.data.GitaRepository
import com.gitakraft.app.data.SettingsRepo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end journey on the real app (prepopulated DB, real DataStore).
 * State is cleared BEFORE each launch (the rule must not auto-launch),
 * so test order never matters.
 */
class FlowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun freshState() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            GitaRepository.get(ctx).clearUserData()
            SettingsRepo(ctx).clearAll()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @org.junit.After
    fun close() {
        scenario.close()
    }

    @Test
    fun welcomeShowsOnFreshStart() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Welcome to GitaKraft"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Welcome to GitaKraft").assertIsDisplayed()
    }

    @Test
    fun dialogBeginDismissesToHome() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Welcome to GitaKraft"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("welcome-begin").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Welcome to GitaKraft"))
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("BEGIN THE JOURNEY").assertIsDisplayed()
    }

    @Test
    fun beginTileOpensFirstVerse() {
        dismissWelcome()
        compose.onNodeWithText("BEGIN THE JOURNEY").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("1:1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1:1").assertIsDisplayed()
    }

    @Test
    fun bookmarkSaveAndUndo() {
        dismissWelcome()
        openVerse("1:1")
        compose.onNodeWithContentDescription("Bookmark").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Verse 1:1 saved", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Verse 1:1 saved", substring = true))
                .fetchSemanticsNodes().isEmpty()
        }
        // Undo restored the unbookmarked icon.
        compose.onNodeWithContentDescription("Bookmark").assertIsDisplayed()
    }

    @Test
    fun continueAdvancesAfterRead() {
        dismissWelcome()
        openVerse("1:1")
        // Dwell marking (~1.5s visible) flips the journey forward.
        Thread.sleep(3_000)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Chapter 1 · Verse 2"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }

    @Test
    fun chaptersListOpensChapter() {
        dismissWelcome()
        compose.onNode(hasText("Chapters") and hasClickAction())
            .performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Arjuna's Despair"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Wisdom of the Soul").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Chapter 2"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchFindsGrief() {
        dismissWelcome()
        compose.onNodeWithContentDescription("Search verses").performClick()
        compose.onNodeWithText("Search 700 verses…").performClick()
        compose.onNodeWithText("Search 700 verses…")
            .performTextInput("grief")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("results", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissWelcome() {
        // First launch seeds the 700-verse DB; wait for Home itself
        // (up to a minute cold) before expecting the dialog.
        compose.waitUntil(timeoutMillis = 60_000) {
            val begin = compose.onAllNodes(hasText("BEGIN THE JOURNEY"))
                .fetchSemanticsNodes()
            val cont = compose.onAllNodes(hasText("CONTINUE"))
                .fetchSemanticsNodes()
            begin.isNotEmpty() || cont.isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Welcome to GitaKraft"))
                .fetchSemanticsNodes().isEmpty()
        ) {
            return
        }
        compose.onNodeWithTag("welcome-begin").performClick()
    }

    private fun openVerse(id: String) {
        dismissWelcome()
        compose.onNodeWithText("BEGIN THE JOURNEY").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(id))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
