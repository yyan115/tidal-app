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

/** The full player follows a finger that drags the small player up or itself down, and a swipe on the cover changes the track. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerGesturesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var tracks: List<Track>

    @Before fun playThreeTracks() {
        val file = File(app.filesDir, "gestures-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        tracks = listOf("First", "Second", "Third").mapIndexed { index, title ->
            Track("local:gesture-$index", "$title Song", "Gesture test", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
        compose.runOnIdle { model.play(tracks) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 3 && model.state.value.playback.playing }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        tracks.forEach { app.library.removeLocal(it.id) }
    }

    private fun playerIsOpen() = compose.onAllNodesWithContentDescription("Close player").fetchSemanticsNodes().isNotEmpty()

    /** A drag of the finger from the middle of the node of [tag] by [distance] pixels, which is negative upward. */
    private fun dragVertically(tag: String, distance: Float, millis: Long) {
        compose.onNodeWithTag(tag).performTouchInput { swipe(Offset(center.x, center.y), Offset(center.x, center.y + distance), millis) }
        compose.waitForIdle()
    }

    private fun screenHeight() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot.height

    @Test fun draggingTheSmallPlayerUpOpensTheFullPlayerAndDraggingItDownClosesIt() {
        dragVertically("mini-player", -screenHeight() * .6f, 250)
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithTag("player-title").assertIsDisplayed()
        dragVertically("player-cover", screenHeight() * .5f, 250)
        compose.waitUntil(5_000) { !playerIsOpen() }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
    }

    @Test fun aShortSlowDragLetsTheFullPlayerFallBackToWhereItWas() {
        dragVertically("mini-player", -screenHeight() * .15f, 900)
        compose.waitUntil(5_000) { !playerIsOpen() }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        dragVertically("player-cover", screenHeight() * .15f, 900)
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithTag("player-title").assertIsDisplayed()
    }

    @Test fun aFastFlickOpensThePlayerFromAShortDrag() {
        dragVertically("mini-player", -screenHeight() * .2f, 60)
        compose.waitUntil(5_000) { playerIsOpen() }
    }

    @Test fun aSwipeOnTheCoverChangesTheTrackInBothDirectionsAndResistsAtTheEnds() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithTag("player-cover").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.index == 1 }
        compose.onNode(hasTestTag("player-title") and hasText("Second Song")).assertIsDisplayed()
        compose.onNodeWithTag("player-cover").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.index == 0 }
        compose.onNodeWithTag("player-cover").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("There is no track before the first one", 0, model.state.value.playback.index)
        assertEquals(3, model.state.value.playback.queue.size)
        compose.onNode(hasTestTag("player-title") and hasText("First Song")).assertIsDisplayed()
    }

    @Test fun theMenuOfTheFullPlayerCanStopPlayback() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithContentDescription("Track options").performClick()
        compose.onNode(hasText("Stop playback") and hasClickAction()).performClick()
        // Stopping clears the queue, so the app asks first, as it does for Clear.
        compose.onNodeWithText("Stop playback?").assertIsDisplayed()
        compose.onNodeWithText("The queue will be cleared").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("Stop playback?").assertCountEquals(0)
        assertEquals("Cancel keeps the queue", 3, model.state.value.playback.queue.size)
        compose.onNodeWithContentDescription("Track options").performClick()
        compose.onNode(hasText("Stop playback") and hasClickAction()).performClick()
        compose.onNode(hasText("Stop") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { model.state.value.playback.queue.isEmpty() }
        compose.waitUntil(5_000) { !playerIsOpen() }
        compose.onAllNodesWithTag("mini-player").assertCountEquals(0)
    }

    @Test fun theMenuOfATrackInAListHasNoStopAction() {
        compose.waitUntil(10_000) { model.state.value.local.count { it.id.startsWith("local:gesture-") } == 3 }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Second Song"))
        compose.onAllNodesWithContentDescription("Options for Second Song").onFirst().performClick()
        compose.onNode(hasText("Add to queue") and hasClickAction()).assertIsDisplayed()
        compose.onAllNodesWithText("Stop playback").assertCountEquals(0)
    }

    @Test fun theSmallPlayerHasNoNextButtonBecauseASwipeChangesTheTrack() {
        compose.onAllNodesWithContentDescription("Next track").assertCountEquals(0)
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.index == 1 }
    }

    @Test fun aSwipeOnTheSmallPlayerBeforeTheFirstTrackAndAfterTheLastOneSaysSo() {
        compose.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Start of queue").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("The track stays", 0, model.state.value.playback.index)
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.index == 1 }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.index == 2 }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("End of queue").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("The track stays", 2, model.state.value.playback.index)
    }

    @Test fun aSwipeOnTheCoverOfTheFullPlayerSaysSoInsideTheFullPlayer() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithTag("player-cover").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Start of queue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start of queue").assertIsDisplayed()
        assertEquals(0, model.state.value.playback.index)
        compose.runOnIdle { model.play(tracks, 2) }
        compose.waitUntil(10_000) { model.state.value.playback.index == 2 }
        compose.onNodeWithTag("player-cover").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("End of queue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("End of queue").assertIsDisplayed()
    }

    @Test fun theStopActionIsTheLastRowOfTheMenuOfTheFullPlayer() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        compose.onNodeWithContentDescription("Track options").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Stop playback").fetchSemanticsNodes().isNotEmpty() }
        val text = androidx.compose.ui.semantics.SemanticsProperties.Text
        fun rows(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
            (if (node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) && node.config.contains(text)) listOf(node) else emptyList()) + node.children.flatMap(::rows)
        // The menu is a window of its own, over the full player.
        val menu = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first { root -> rows(root).any { it.config[text].joinToString { t -> t.text } == "Stop playback" } && rows(root).none { it.config[text].joinToString { t -> t.text } == "Lyrics" } }
        val rows = rows(menu)
        val lowest = rows.maxByOrNull { it.boundsInRoot.top }!!
        assertEquals("Stop playback", lowest.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text })
    }

    @Test fun theSmallPlayerIsNotShownOnTheSettingsPages() {
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("mini-player").assertCountEquals(0)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theQueueHasOneWayInAndOneIconInTheFullPlayer() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { playerIsOpen() }
        // The bottom button has the count and is the only way in. The card shows the next song and is only a preview.
        compose.onNode(hasText("Queue · 3") and hasClickAction()).assertExists()
        compose.onNodeWithText("NEXT IN QUEUE").assertExists()
        compose.onNode(hasText("NEXT IN QUEUE") and hasClickAction()).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Open queue").assertCountEquals(0)
    }
}
