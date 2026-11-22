package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The full player slides over the screen as a whole. It follows a finger up and down, and a tap or the Back key moves it the same way. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerSlideTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var track: Track

    @Before fun playOneTrack() {
        val file = File(app.filesDir, "player-slide-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        track = Track("local:slide-test", "Slide Song", "Slide test", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        app.library.save(track)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) }; model.play(listOf(track)) }
        compose.waitUntil(15_000) { model.state.value.playback.track != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        app.library.removeLocal(track.id)
    }

    private fun screenHeight() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }
    /** Where the cover lies on the screen. The cover is inside the player, so it shows how far the player has slid. */
    private fun coverTop() = compose.onNodeWithTag("player-cover").fetchSemanticsNode().boundsInRoot.top
    private fun coverHeight() = compose.onNodeWithTag("player-cover").fetchSemanticsNode().boundsInRoot.height

    @Test fun theFullPlayerFollowsTheFingerAsAWholeAndOpensWhenTheFingerLetsGo() {
        val screen = screenHeight()
        // The finger goes up by a fifth of the screen twice. The player follows, and the cover keeps its size because the player is not stretched.
        compose.onNodeWithTag("mini-player").performTouchInput { down(center); moveBy(Offset(0f, -screen * .2f)); moveBy(Offset(0f, -screen * .2f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-cover").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val coverAtHalf = coverTop()
        val sizeAtHalf = coverHeight()
        compose.onNodeWithTag("mini-player").performTouchInput { moveBy(Offset(0f, -screen * .3f)); up() }
        compose.waitUntil(5_000) { coverTop() < coverAtHalf - screen * .3f }
        compose.waitForIdle()
        val settled = coverTop()
        assertEquals("The player slid up by 60% of the screen after the first 40%", screen * .6f, coverAtHalf - settled, screen * .08f)
        assertEquals("The cover keeps its size while the player slides", sizeAtHalf, coverHeight(), sizeAtHalf * .02f)
    }

    @Test fun theBackKeyAndTheCloseButtonSlideItAwayAgain() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-cover").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-container").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
    }
}
