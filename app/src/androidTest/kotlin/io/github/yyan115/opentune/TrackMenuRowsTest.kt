package io.github.yyan115.opentune

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
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
 * The menu of a track has exactly the rows that suit the kind of track: a file on the device, a download, or a song of TIDAL.
 * A file has no page at TIDAL, a download is on the device already, and a song that is only at TIDAL can be downloaded.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TrackMenuRowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private fun text(id: Int) = compose.activity.getString(id)
    private val saved = mutableListOf<String>()
    private var downloadUri: Uri? = null

    @Before fun prepare() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() {
        saved.forEach(app.library::removeLocal)
        downloadUri?.let(app.downloadFiles::delete)
    }

    private fun openMenuOf(title: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText(title))
        compose.onAllNodesWithContentDescription(text(R.string.track_options).replace("%1\$s", title)).onFirst().performClick()
    }

    private fun rows(present: List<Int>, absent: List<Int>) {
        for (id in present) compose.onAllNodes(hasText(text(id)) and hasClickAction()).assertCountEquals(1)
        for (id in absent) compose.onAllNodesWithText(text(id)).assertCountEquals(0)
    }

    @Test fun aFileOnTheDeviceHasNoRowForTheServiceAndCanBeRemovedFromTheLibrary() {
        val file = File(app.filesDir, "menu-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val track = Track("local:menu-file", "Menu File", "Menu Artist", "Menu Album", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        app.library.save(track); saved += track.id
        openMenuOf("Menu File")
        rows(present = listOf(R.string.track_save, R.string.track_play_next, R.string.track_add_queue, R.string.playlist_add, R.string.track_details, R.string.track_remove_file),
            absent = listOf(R.string.action_download, R.string.track_go_album, R.string.track_go_artist, R.string.track_radio, R.string.album_credits, R.string.track_share, R.string.track_remove_local))
    }

    @Test fun aDownloadIsOnTheDeviceAndHasItsPagesAndCanBeRemoved() = runBlocking {
        app.settings.update { it.copy(downloadFolder = "") }
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { it.readBytes() }
        val stored = app.downloadFiles.create("Menu Download test.flac", "audio/flac") { it.write(bytes) }
        downloadUri = stored.uri
        val track = Track("2300020", "Menu Download", "Menu Artist", "Menu Album", albumId = "98", artistId = "99", durationMs = 30_000, localUri = stored.uri.toString(), quality = "FLAC")
        app.library.save(track); saved += track.id
        openMenuOf("Menu Download")
        // Nobody is signed in, so the rows that need an account are not there. A download keeps the pages of its album and its artist.
        rows(present = listOf(R.string.track_save, R.string.track_play_next, R.string.track_add_queue, R.string.playlist_add, R.string.track_go_album, R.string.track_go_artist,
            R.string.track_share, R.string.track_details, R.string.track_remove_local),
            absent = listOf(R.string.action_download, R.string.track_radio, R.string.album_credits, R.string.track_remove_file))
    }
}
