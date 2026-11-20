package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** The full player grows out of the small player while a finger drags it up, and shrinks back into it while a finger drags it down. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerExpansionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var track: Track

    @Before fun playATrack() {
        val file = File(app.filesDir, "expansion-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        track = Track("local:expansion", "Expansion Song", "Expansion test", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        app.library.save(track)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(15_000) { model.state.value.playback.playing }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        app.library.removeLocal(track.id)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 5_000)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(app.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun screenHeight() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }

    /** The top and the bottom edge of the part of the full player that is drawn, as pixels of the window of the player. */
    private fun drawn(): Pair<Int, Int> {
        val bitmap = compose.onNodeWithTag("player-container").captureToImage().asAndroidBitmap()
        val x = bitmap.width / 2
        fun solid(y: Int) = (bitmap.getPixel(x, y) ushr 24) > 200
        val top = (0 until bitmap.height).firstOrNull { solid(it) } ?: bitmap.height
        val bottom = (bitmap.height - 1 downTo 0).firstOrNull { solid(it) } ?: -1
        return top to bottom
    }

    @Test fun halfWayThroughADragUpThePlayerCoversTheSpaceBetweenTheSmallPlayerAndTheScreen() {
        val height = screenHeight()
        val mini = compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mini-player").performTouchInput { down(center); moveBy(Offset(0f, -height * .25f)); moveBy(Offset(0f, -height * .25f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-container").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        screenshot("expand-half")
        val (top, bottom) = drawn()
        assertTrue("The top edge is between the small player (${mini.top}) and the top of the screen: $top", top > height * .05f && top < mini.top - height * .05f)
        // The small player may sit at the very bottom of the screen, as on a device without a navigation bar, so the edge may be at the bottom as well.
        assertTrue("The bottom edge is at or below the small player (${mini.bottom}) and not below the screen ($height): $bottom", bottom >= mini.bottom - height * .02f && bottom <= height)
        compose.onNodeWithTag("mini-player").performTouchInput { up() }
    }

    @Test fun halfWayThroughADragDownThePlayerShrinksTowardTheSmallPlayer() {
        val height = screenHeight()
        val mini = compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Close player").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("player-cover").performTouchInput { down(center); moveBy(Offset(0f, height * .25f)); moveBy(Offset(0f, height * .25f)) }
        compose.waitForIdle()
        screenshot("collapse-half")
        val (top, bottom) = drawn()
        assertTrue("The top edge has moved down from the top of the screen and not past the small player: $top", top > height * .05f && top < mini.top - height * .05f)
        assertTrue("The bottom edge is not above the small player (${mini.bottom}) and not below the screen ($height): $bottom", bottom >= mini.bottom - height * .02f && bottom <= height)
        compose.onNodeWithTag("player-cover").performTouchInput { up() }
    }

    @Test fun theFullPlayerFillsTheScreenOnceItIsOpenAndTheSmallPlayerIsBackWhenItIsClosed() {
        val height = screenHeight()
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Close player").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val (top, bottom) = drawn()
        assertTrue("Open: $top to $bottom of $height", top <= 1 && bottom >= height - 2)
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-container").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
    }
}
