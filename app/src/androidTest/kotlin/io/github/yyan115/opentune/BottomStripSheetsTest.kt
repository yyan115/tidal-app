package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** With the three-button navigation, the strip behind the buttons has no band of another colour behind a sheet or a pop-up either. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class BottomStripSheetsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private var before = ""
    private val failures = mutableListOf<String>()
    private lateinit var tracks: List<Track>

    @Before fun prepare() {
        before = BottomStrip.useThreeButtons(); Thread.sleep(1500)
        val file = File(app.filesDir, "strip-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("cover-a1.flac").use { input -> file.outputStream().use(input::copyTo) }
        tracks = (1..4).map { Track("local:strip-$it", "Strip Song $it", "Strip test", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC") }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        tracks.forEach { app.library.removeLocal(it.id) }
        BottomStrip.restore(before)
    }

    private fun check(name: String) {
        compose.waitForIdle(); Thread.sleep(700); compose.waitForIdle()
        var height = 0
        compose.runOnIdle { height = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0 }
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("The three-button navigation is not on: the inset is ${height / density} dp", height >= 40 * density)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { BottomStrip.problem(bitmap, height)?.let { failures += "$name: $it" } } finally { bitmap.recycle() }
    }

    private fun clickText(text: String) { compose.waitUntil(8_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick() }

    @Test fun noSheetOrPopUpHasABandBehindTheButtons() {
        compose.runOnIdle { model.play(tracks, 0, "Strip list") }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 4 }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
        check("small player on the page")
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-cover").fetchSemanticsNodes().isNotEmpty() }
        check("full player")
        // The queue sheet.
        clickText("Queue · 4"); compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-list").fetchSemanticsNodes().isNotEmpty() }
        check("queue sheet")
        compose.onNode(hasText("Clear") and hasClickAction()).performClick(); compose.waitUntil(5_000) { compose.onAllNodesWithText("Clear the queue?").fetchSemanticsNodes().isNotEmpty() }
        check("clear the queue pop-up")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Close sheet").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * 0.08f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("queue-list").fetchSemanticsNodes().isEmpty() }
        // The menu of the track.
        compose.onNodeWithContentDescription("Track options").performClick(); compose.waitUntil(5_000) { compose.onAllNodesWithText("Play next").fetchSemanticsNodes().isNotEmpty() }
        check("track menu")
        clickText("Stop playback"); compose.waitUntil(5_000) { compose.onAllNodesWithText("Stop playback?").fetchSemanticsNodes().isNotEmpty() }
        check("stop playback pop-up")
        compose.onNodeWithText("Cancel").performClick()
        // The audio details and the timer.
        compose.onNodeWithContentDescription("Audio details").performClick(); compose.waitUntil(5_000) { compose.onAllNodesWithText("Audio details").fetchSemanticsNodes().size > 1 || compose.onAllNodesWithTag("source-origin", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        check("audio details sheet")
        compose.onNodeWithContentDescription("Close sheet").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * 0.08f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("source-origin", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertTrue("A band shows behind the three buttons:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
