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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A swipe to the right on a track shows two buttons, and a long swipe plays the track next. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SwipeToQueueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var playing: Track
    private lateinit var tracks: List<Track>

    private fun local(id: String, title: String, uri: String) = Track("local:swipe-$id", title, "Swipe test", "Local audio checks", durationMs = 30_000, localUri = uri, quality = "FLAC")

    @Before fun showTheDeviceList() {
        val file = File(app.filesDir, "swipe-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val uri = Uri.fromFile(file).toString()
        playing = local("now", "Swipe Playing", uri)
        tracks = listOf(local("a", "Swipe Alpha", uri), local("b", "Swipe Beta", uri), local("c", "Swipe Gamma", uri))
        (tracks + playing).forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false) } }
        compose.runOnIdle { model.play(listOf(playing)) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 1 }
        compose.waitUntil(10_000) { model.state.value.local.count { it.title.startsWith("Swipe ") } == 4 }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Swipe Alpha"))
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        (tracks + playing).forEach { app.library.removeLocal(it.id) }
    }

    /** Swipes the row of [title] from a share of the width of the screen to another one. */
    private fun swipeRow(title: String, from: Float, to: Float) {
        compose.onNodeWithTag("device-list").performScrollToNode(hasText(title))
        val y = compose.onNode(hasText(title)).fetchSemanticsNode().boundsInRoot.center.y
        compose.onRoot().performTouchInput { swipe(Offset(width * from, y), Offset(width * to, y), 500) }
        compose.waitForIdle()
    }

    private fun queue() = model.state.value.playback.queue.map(Track::title)

    @Test fun aShortSwipeShowsTheTwoButtonsAndAddToQueueAddsTheTrack() {
        swipeRow("Swipe Alpha", .05f, .40f)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Play next") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Add to queue") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { queue() == listOf("Swipe Playing", "Swipe Alpha") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun thePlayNextButtonGoesAheadOfTheTracksAddedBefore() {
        swipeRow("Swipe Alpha", .05f, .40f)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Add to queue") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { queue().size == 2 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isEmpty() }
        swipeRow("Swipe Beta", .05f, .40f)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Play next") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { queue() == listOf("Swipe Playing", "Swipe Beta", "Swipe Alpha") }
    }

    @Test fun aLongSwipePlaysTheTrackNextAndTheRowReturns() {
        swipeRow("Swipe Gamma", .03f, .97f)
        compose.waitUntil(5_000) { queue() == listOf("Swipe Playing", "Swipe Gamma") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Swipe Gamma").assertIsDisplayed()
    }

    @Test fun aTapOnAnOpenRowClosesItAndPlaysNothing() {
        swipeRow("Swipe Beta", .05f, .40f)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Swipe Beta").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isEmpty() }
        assertEquals("The tap did not start the track", "Swipe Playing", model.state.value.playback.track?.title)
        assertEquals(listOf("Swipe Playing"), queue())
    }

    @Test fun aSwipeThatStaysShortLeavesTheRowWhereItWas() {
        swipeRow("Swipe Alpha", .05f, .08f)
        assertEquals(0, compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().size)
        assertEquals(listOf("Swipe Playing"), queue())
    }
}
