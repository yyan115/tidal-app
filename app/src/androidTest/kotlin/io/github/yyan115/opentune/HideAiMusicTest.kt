package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.PageData
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.OpenTuneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A list of songs on a page leaves out the songs that TIDAL marks as AI-generated, when the listener chose that. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class HideAiMusicTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(page: PageData, hide: Boolean) {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.setContent { OpenTuneTheme("dark") { PageCase(page, model, Settings(hideAiMusic = hide, welcomeDone = true)) } }
    }

    // Every second song of the list is marked, so songs 2 and 4 are AI-generated and songs 1, 3 and 5 are not.
    private fun top() = PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(8, title = "Top tracks", machineEvery = 2, showMore = null))

    @Test fun markedSongsAreShownWhenTheListenerDidNotChooseToHideThem() {
        show(top(), hide = false)
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 4"))
        compose.onNodeWithText("Page song 2").assertExists()
        compose.onNodeWithText("Page song 4").assertExists()
    }

    @Test fun markedSongsAreLeftOutWhenTheListenerChoseToHideThem() {
        show(top(), hide = true)
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 5"))
        compose.onNodeWithText("Page song 1").assertExists()
        compose.onNodeWithText("Page song 3").assertExists()
        compose.onNodeWithText("Page song 5").assertExists()
        compose.onAllNodesWithText("Page song 2").assertCountEquals(0)
        compose.onAllNodesWithText("Page song 4").assertCountEquals(0)
    }

    @Test fun theSongsOfAnAlbumStayBecauseTheListenerOpenedThatAlbum() {
        show(PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(4, title = "Tracks", numbered = true, machineEvery = 2)), hide = true)
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 4"))
        compose.onNodeWithText("Page song 2").assertExists()
        compose.onNodeWithText("Page song 4").assertExists()
    }
}
