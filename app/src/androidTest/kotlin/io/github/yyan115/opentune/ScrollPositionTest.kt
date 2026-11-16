package io.github.yyan115.opentune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A list keeps its place when the listener opens an item and comes back. Without an account the listener sees one list, On device,
 * and the lists of Home, Search and Library are held in the same way.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ScrollPositionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val titles = (1..40).map { "Scroll %02d".format(it) }

    @Before fun makePlaylists() {
        titles.forEach { app.library.createPlaylist(it) }
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false) } }
        compose.waitUntil(15_000) { model.state.value.localPlaylists.count { it.title.startsWith("Scroll ") } == titles.size }
    }

    @After fun clean() {
        app.library.playlists().filter { it.title.startsWith("Scroll ") }.forEach { app.library.deletePlaylist(it.id) }
    }

    @Test fun theListOnDeviceKeepsItsPlaceAfterAPlaylistIsOpenedAndClosed() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Scroll 12"))
        compose.onNodeWithText("Scroll 12").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("detail-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Scroll 12").assertIsDisplayed()
    }
}
