package io.github.yyan115.opentune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.SourceDetailsSheet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The details of a downloaded track tell where its file is. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val audio get() = InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { it.readBytes() }

    private fun textOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    @Test fun aDownloadShowsItsFileAndItsPlace() {
        val bytes = audio
        app.settings.update { it.copy(downloadFolder = "") }
        val stored = runBlocking { app.downloadFiles.create("OpenTune details test - Night Walk.flac", "audio/flac") { it.write(bytes) } }
        try {
            val track = Track("2300001", "Night Walk", "Artist", "Album", durationMs = 30_000, localUri = stored.uri.toString(), quality = "FLAC")
            compose.setContent { OpenTuneTheme("dark") { SourceDetailsSheet("FLAC · 16-bit · 44.1 kHz", track, null) { } } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("source-location").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("Internal storage › Music › OpenTune", textOf("source-location"))
            assertTrue(textOf("source-file").startsWith("OpenTune details test - Night Walk"))
            compose.onAllNodesWithTag("source-folder").assertCountEquals(0)
            compose.onNodeWithTag("source-size").assertExists()
        } finally { app.downloadFiles.delete(stored.uri) }
    }

    @Test fun aDownloadOfAnEarlierVersionHasNoPlaceToShow() {
        val track = Track("2300002", "Old", "Artist", "Album", durationMs = 30_000, localUri = "opentune-offline://2300002", quality = "FLAC")
        compose.setContent { OpenTuneTheme("dark") { SourceDetailsSheet("FLAC", track, null) { } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("source-location").assertCountEquals(0)
        compose.onAllNodesWithTag("source-file").assertCountEquals(0)
    }

    @Test fun aTrackThatIsNotOnTheDeviceHasNoFileRows() {
        val track = Track("2300003", "Stream", "Artist", "Album", durationMs = 30_000)
        compose.setContent { OpenTuneTheme("dark") { SourceDetailsSheet("FLAC", track, null) { } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("source-location").assertCountEquals(0)
    }
}
