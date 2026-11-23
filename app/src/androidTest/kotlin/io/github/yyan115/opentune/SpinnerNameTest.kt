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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The button of the small player keeps the name of its action while the player buffers and the button shows a spinner, and the spinner has a name of its own. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SpinnerNameTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("tidal:1", "Song", "Artist", "Album", durationMs = 200_000)

    private fun show(state: PlaybackState) {
        val position = mutableStateOf(0L)
        compose.setContent { OpenTuneTheme("dark") { MiniPlayer(state, position, onOpen = {}, onPlay = {}, onNext = {}) } }
    }

    @Test fun theButtonOfABufferingSmallPlayerHasAName() {
        show(PlaybackState(queue = listOf(track), buffering = true))
        compose.onNode(hasClickAction() and hasContentDescription("Play")).assertIsDisplayed()
        compose.onNode(hasContentDescription("Loading")).assertIsDisplayed()
        val problems = LayoutRules.problems(compose)
        assertTrue("The small player has faults while it buffers: $problems", problems.isEmpty())
    }

    @Test fun theButtonOfAPlayingSmallPlayerStillSaysPause() {
        show(PlaybackState(queue = listOf(track), playing = true))
        compose.onNode(hasClickAction() and hasContentDescription("Pause")).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Loading").assertCountEquals(0)
    }
}
