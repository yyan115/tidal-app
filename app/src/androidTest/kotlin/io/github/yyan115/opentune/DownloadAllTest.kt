package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.AppState
import io.github.yyan115.opentune.ui.DetailScreen
import io.github.yyan115.opentune.ui.DownloadAllButton
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.OpenTuneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Download button of a page is there from the first frame, and a download of more than ten songs asks first and says how many. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadAllTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val started = mutableListOf<String>()

    private fun songs(count: Int, onDevice: Int = 0) = (1..count).map { Track("tidal-$it", "Song $it", "Artist", "Album", localUri = if (it <= onDevice) "content://stored/$it" else null) }

    private fun show(tracks: List<Track>) {
        compose.setContent { OpenTuneTheme("dark") { DownloadAllButton(tracks = { tracks }, onDownload = { started += it.id }) } }
    }

    @Test fun tenSongsStartAtOnceWithoutAQuestion() {
        show(songs(10))
        compose.onNodeWithTag("download-all").performClick()
        compose.waitForIdle()
        assertEquals(10, started.size)
        compose.onAllNodes(hasText("Download", substring = true) and !hasClickAction()).assertCountEquals(0)
    }

    @Test fun fifteenSongsAskFirstAndNameTheNumber() {
        show(songs(15))
        compose.onNodeWithTag("download-all").performClick()
        compose.onNodeWithText("Download 15 songs?").assertIsDisplayed()
        compose.onNodeWithText("They download one at a time. You can stop them in the download queue.").assertIsDisplayed()
        assertEquals("Nothing starts before the answer", 0, started.size)
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, started.size)
        compose.onNodeWithTag("download-all").performClick()
        compose.onNode(hasText("Download") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(15, started.size)
    }

    @Test fun songsThatAreAlreadyOnTheDeviceAreNotCounted() {
        show(songs(14, onDevice = 4))
        compose.onNodeWithTag("download-all").performClick()
        compose.waitForIdle()
        assertEquals("Ten songs are left, so there is no question", 10, started.size)
        assertTrue(started.none { it in (1..4).map { n -> "tidal-$n" } })
    }

    @Test fun theButtonIsOnThePageFromTheFirstFrameAndDoesNotMoveWhenTheSongsArrive() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val release = Release("tidal-playlist", "Pop hits", "TIDAL", null, type = io.github.yyan115.opentune.core.ReleaseType.PLAYLIST)
        val state = mutableStateOf(AppState(detail = release, detailTracks = emptyList(), detailLoading = true))
        compose.setContent { OpenTuneTheme("dark") { DetailScreen(state.value, model, onMenu = { _, _ -> }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("download-all").assertExists().assertIsNotEnabled().assertIsDisplayed()
        val loading = compose.onNodeWithTag("download-all").fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = state.value.copy(detailTracks = songs(12), detailLoading = false) }
        compose.waitForIdle()
        compose.onNodeWithTag("download-all").assertIsEnabled()
        assertEquals("The Download button stays where it was", loading, compose.onNodeWithTag("download-all").fetchSemanticsNode().boundsInRoot)
        assertEquals("The Play button stays where it was", play, compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun theButtonStaysAndIsDisabledWhenEverySongIsOnTheDevice() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val state = mutableStateOf(AppState(detail = Release("tidal-playlist", "Pop hits", "TIDAL", null, type = io.github.yyan115.opentune.core.ReleaseType.PLAYLIST), detailTracks = songs(3, onDevice = 3)))
        compose.setContent { OpenTuneTheme("dark") { DetailScreen(state.value, model, onMenu = { _, _ -> }) } }
        compose.onNodeWithTag("download-all").assertExists().assertIsNotEnabled()
    }
}
