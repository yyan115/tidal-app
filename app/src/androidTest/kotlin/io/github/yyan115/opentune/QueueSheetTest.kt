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

    private fun sheetHeight() = compose.onNodeWithTag("queue-list").fetchSemanticsNode().boundsInRoot.height
    private fun screenHeight() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }

    @Test fun theSheetHasTheSameHeightForAShortAndALongQueue() {
        play(2); openQueue(2)
        val short = sheetHeight()
        closeQueue()
        compose.onNodeWithContentDescription("Close player").performClick()
        play(30); openQueue(30)
        val long = sheetHeight()
        assertEquals("The queue sheet with 2 tracks and with 30 tracks", short, long, 1f)
        assertTrue("The sheet is tall: $short of ${screenHeight()}", short >= screenHeight() * 0.7f)
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
}
