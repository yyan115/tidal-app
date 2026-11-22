package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A song that is in the queue when its download finishes. Every screen shows it as downloaded, from every way in, and the player plays the file.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadedSongTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val streamed = Track("2300040", "Finished Download", "Download Artist", "Download Album", albumId = "41", artistId = "42", durationMs = 30_000, quality = "FLAC · 16-bit · 44.1 kHz")
    private lateinit var file: File

    @Before fun queueTheSongBeforeItsDownloadFinishes() {
        file = File(app.filesDir, "downloaded-song-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
        // Nobody is signed in, so the stream cannot start, but the song stands in the queue as a copy without a file.
        compose.runOnIdle { model.play(listOf(streamed)) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 1 }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        app.library.removeLocal(streamed.id)
    }

    /** The Download row of the menu. The button of the player behind the menu has a description, and the row has none. */
    private val downloadRow = hasText("Download") and hasClickAction() and SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)

    private fun finishTheDownload() {
        app.library.save(streamed.copy(localUri = Uri.fromFile(file).toString()))
        compose.waitUntil(10_000) { model.state.value.local.any { it.id == streamed.id && it.onDevice } }
    }

    @Test fun theAudioDetailsOfThePlayerSayDownloadedOnceTheDownloadHasFinished() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Audio details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Audio details").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("source-origin", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("source-origin", useUnmergedTree = true).assertTextContains("TIDAL")
        compose.onNodeWithContentDescription("Close sheet").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * 0.08f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("source-origin", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        finishTheDownload()
        compose.onNodeWithContentDescription("Audio details").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("source-origin", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("source-origin", useUnmergedTree = true).assertTextContains("Downloaded")
    }

    @Test fun theMenuOfThePlayerOffersNoDownloadForASongThatIsOnTheDevice() {
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Track options").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Track options").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Play next").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(downloadRow).assertExists()
        compose.onNodeWithContentDescription("Close track options").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Play next").fetchSemanticsNodes().isEmpty() }
        finishTheDownload()
        compose.onNodeWithContentDescription("Track options").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Play next").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(downloadRow).assertCountEquals(0)
        compose.onNodeWithText("Remove download").assertExists()
    }

    @Test fun thePlayerPlaysTheFileOfADownloadWhenTheSongIsStartedAfterTheDownload() = runBlocking {
        finishTheDownload()
        val item = app.sources.resolve(streamed)
        assertEquals("The queued copy has no file, and the player still plays the saved one", Uri.fromFile(file), item.localConfiguration?.uri)
    }
}
