package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.appThemes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The queue sheet has the same height for a short queue, a long queue and an empty one, and its status text is not drawn like an action. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class QueueSheetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var tracks: List<Track>

    @Before fun prepare() {
        val file = File(app.filesDir, "queue-sheet-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        tracks = (1..30).map { Track("local:sheet-$it", "Sheet Song $it", "Queue sheet test", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC") }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        tracks.forEach { app.library.removeLocal(it.id) }
    }

    private fun play(count: Int, shuffle: Boolean = false) {
        compose.runOnIdle { model.play(tracks.take(count), 0, "Test list", shuffle = shuffle) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == count && model.state.value.playback.playing }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openQueue(count: Int) {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Queue · $count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Queue · $count").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun closeQueue() {
        // The scrim covers the screen behind the sheet, and the sheet covers most of it, so the tap goes to the strip above the sheet.
        compose.onNodeWithContentDescription("Close sheet").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * 0.08f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-list").fetchSemanticsNodes().isEmpty() }
    }

    private fun sheetHeight() = compose.onNodeWithTag("queue-sheet").fetchSemanticsNode().boundsInRoot.height
    private fun screenHeight() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }

    @Test fun theSheetHasTheSameHeightForAShortAndALongQueue() {
        play(2); openQueue(2)
        val short = sheetHeight()
        closeQueue()
        compose.onNodeWithContentDescription("Close player").performClick()
        play(30); openQueue(30)
        val long = sheetHeight()
        assertEquals("The queue sheet with 2 tracks and with 30 tracks", short, long, 1f)
        assertTrue("The sheet is full height: $short of ${screenHeight()}", short >= screenHeight() * 0.85f)
    }

    @Test fun theSheetKeepsItsHeightWhenTheQueueIsCleared() {
        play(3); openQueue(3)
        val before = sheetHeight()
        compose.onNodeWithText("Clear").performClick()
        compose.onAllNodes(hasText("Clear") and hasClickAction()).onLast().performClick()
        compose.waitUntil(5_000) { model.state.value.playback.queue.isEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Queue is empty", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("The queue sheet with 3 tracks and with none", before, sheetHeight(), 1f)
    }

    @Test fun theShuffledLabelIsDrawnInTheColourOfSecondaryTextInEveryTheme() {
        play(5, shuffle = true)
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Queue · 5").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Queue · 5").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-shuffled").fetchSemanticsNodes().isNotEmpty() }
        for (theme in appThemes) {
            compose.runOnIdle { model.settings { it.copy(theme = theme.id) } }
            compose.waitForIdle()
            val (text, _) = Readability.coloursOfText(compose, hasTestTag("queue-shuffled"))
            val secondary = theme.scheme.onSurfaceVariant.toArgb()
            val action = theme.scheme.primary.toArgb()
            assertTrue("Shuffled in ${theme.id} is #%06X, secondary text is #%06X and an action is #%06X".format(text and 0xFFFFFF, secondary and 0xFFFFFF, action and 0xFFFFFF),
                Readability.distance(text, secondary) < Readability.distance(text, action))
        }
    }

    @Test fun theSongThatPlaysStaysAtTheTopAndFollowsAChangeOfSong() {
        play(3); openQueue(3)
        compose.onNodeWithTag("queue-now-playing-title").assertTextEquals("Sheet Song 1")
        val top = compose.onNodeWithTag("queue-now-playing").fetchSemanticsNode().positionOnScreen.y
        compose.runOnIdle { model.next() }
        compose.waitUntil(10_000) { model.state.value.playback.track?.id == tracks[1].id }
        compose.onNodeWithTag("queue-now-playing-title").assertTextEquals("Sheet Song 2")
        assertEquals("The bar does not move when the song changes", top, compose.onNodeWithTag("queue-now-playing").fetchSemanticsNode().positionOnScreen.y, 1f)
        compose.onNodeWithTag("queue-now-playing-toggle").performClick()
        compose.waitUntil(10_000) { !model.state.value.playback.playing }
        compose.onNodeWithTag("queue-now-playing-toggle").performClick()
        compose.waitUntil(10_000) { model.state.value.playback.playing }
    }

    private fun sheetTop() = compose.onNodeWithTag("queue-sheet").fetchSemanticsNode().positionOnScreen.y

    @Test fun theTitleAndTheToolsStayAtTheTopWhileTheSongsScroll() {
        compose.runOnIdle { model.play(tracks, 20, "Test list", shuffle = false) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 30 && model.state.value.playback.playing }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
        openQueue(30)
        // The list starts at the song that plays, far below the first one, and the buttons are still there.
        compose.onNodeWithText("Clear").assertIsDisplayed()
        compose.onNodeWithText("Save as playlist").assertIsDisplayed()
        compose.onNodeWithTag("queue-list").performScrollToNode(hasText("Sheet Song 30"))
        compose.onNodeWithText("Clear").assertIsDisplayed()
        compose.onNodeWithTag("queue-list").performScrollToNode(hasText("Sheet Song 1"))
        compose.onNodeWithText("Save as playlist").assertIsDisplayed()
    }

    @Test fun theSheetOpensFullAndClosesWhenItIsDraggedDown() {
        play(30); openQueue(30)
        val screen = screenHeight()
        val top = sheetTop()
        assertTrue("The sheet opens full at once: top at $top of $screen", top < screen * 0.25f)
        compose.onNodeWithContentDescription("Drag handle").performTouchInput { swipe(center, center.copy(y = center.y + screen * 0.5f), 400) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-sheet").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theQueueIsOnePlainListWithNoNamesForItsParts() {
        compose.runOnIdle { model.play(tracks.take(6), 0, "Test list", shuffle = false) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 6 && model.state.value.playback.playing }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { model.queue(tracks[10]); model.queue(tracks[11]) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 8 }
        openQueue(8)
        compose.onAllNodesWithText("Your queue", ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Next from", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Up next", ignoreCase = true).assertCountEquals(0)
        // What the listener added still plays before the rest of the list, so it comes first in the list.
        val order = model.state.value.playback.queue.map { it.title }
        assertEquals(listOf("Sheet Song 1", "Sheet Song 11", "Sheet Song 12", "Sheet Song 2"), order.take(4))
        fun top(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().map { it.positionOnScreen.y }.filter { it > sheetTop() }.min()
        assertTrue("The added songs are listed before the rest", top("Sheet Song 11") < top("Sheet Song 12") && top("Sheet Song 12") < top("Sheet Song 2"))
    }

    @Test fun theClearActionHasNoArrowBecauseItOpensNothing() {
        play(3); openQueue(3)
        compose.onNodeWithText("Clear").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("section-arrow") and hasAnyAncestor(hasTestTag("queue-sheet")), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aSwipedSongShowsTheCrossOnTheSideThatItUncovers() {
        play(5); openQueue(5)
        val row = compose.onNode(hasText("Sheet Song 3"))
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        row.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(width * 0.25f, 0f)) }
        compose.waitForIdle()
        // Every row has a cross behind it, so the one that belongs to this row is the one at the height of the row.
        val toTheRight = compose.onAllNodesWithTag("queue-remove-icon", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.center.y in rowBounds.top..rowBounds.bottom }
        assertTrue("A swipe to the right uncovers the left side: cross at ${toTheRight.center.x} in a row that starts at ${rowBounds.left}", toTheRight.center.x < rowBounds.left + rowBounds.width * 0.3f)
        row.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-width * 0.5f, 0f)); up() }
        compose.waitForIdle()
    }
}
