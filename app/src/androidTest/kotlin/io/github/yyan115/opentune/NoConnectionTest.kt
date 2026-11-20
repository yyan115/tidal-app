package io.github.yyan115.opentune

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MiniPlayer
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.PlaybackState
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The small player says No connection when the connection is the reason that playback stopped, and Playback stopped for any other reason. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class NoConnectionTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("tidal:1", "Song", "Artist", "Album", durationMs = 200_000)

    private fun show(state: PlaybackState, themeId: String = "dark") {
        compose.setContent { OpenTuneTheme(themeId) { MiniPlayer(state, mutableStateOf(0L), onOpen = {}, onPlay = {}, onNext = {}) } }
    }

    @Test fun aLostConnectionIsNamedInTheSmallPlayer() {
        show(PlaybackState(queue = listOf(track), error = "No connection", noConnection = true))
        compose.onNodeWithText("No connection").assertIsDisplayed()
        compose.onAllNodesWithText("Playback stopped").assertCountEquals(0)
        compose.onAllNodesWithText("Artist").assertCountEquals(0)
    }

    @Test fun anotherFailureIsStillPlaybackStopped() {
        show(PlaybackState(queue = listOf(track), error = "Something else", noConnection = false))
        compose.onNodeWithText("Playback stopped").assertIsDisplayed()
        compose.onAllNodesWithText("No connection").assertCountEquals(0)
    }

    @Test fun withoutAFailureTheSmallPlayerShowsTheArtist() {
        show(PlaybackState(queue = listOf(track)))
        compose.onNodeWithText("Artist").assertIsDisplayed()
        compose.onAllNodesWithText("No connection").assertCountEquals(0)
    }

    @Test fun theMessageCanBeReadInEveryTheme() {
        val theme = mutableStateOf(appThemes.first().id)
        compose.setContent { OpenTuneTheme(theme.value) { MiniPlayer(PlaybackState(queue = listOf(track), error = "No connection", noConnection = true), mutableStateOf(0L), onOpen = {}, onPlay = {}, onNext = {}) } }
        for (item in appThemes) {
            theme.value = item.id; compose.waitForIdle()
            val problems = Readability.problems(compose)
            assertTrue("The small player is hard to read in ${item.id}: $problems", problems.isEmpty())
        }
    }
}
