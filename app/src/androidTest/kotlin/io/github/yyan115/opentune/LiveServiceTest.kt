package io.github.yyan115.opentune

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.github.yyan115.opentune.core.AudioQuality
import io.github.yyan115.opentune.core.Session
import io.github.yyan115.opentune.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.util.UUID

/** Optional, explicit live-account checks. Credentials are never part of a build or CI. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LiveServiceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    @get:Rule(order = 0) val account = LiveAccount.rule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Test fun temporaryPlaylistCanBeCreatedEditedAndDeleted() = runBlocking {
        val track = app.tidal.search("Nujabes Feather").tracks.first()
        val playlist = app.tidal.createPlaylist("OpenTune check ${UUID.randomUUID().toString().take(8)}")
        try {
            app.tidal.addToPlaylist(playlist.id, track.id)
            assertEquals(track.id, app.tidal.allTracks(playlist).single().id)
            app.tidal.renamePlaylist(playlist.id, "OpenTune renamed check")
            assertTrue(app.tidal.allPlaylists().any { it.id == playlist.id && it.title == "OpenTune renamed check" })
            app.tidal.removeFromPlaylist(playlist.id, 0, track.id)
            assertTrue(app.tidal.allTracks(playlist).isEmpty())
        } finally { app.tidal.deletePlaylist(playlist.id) }
        assertFalse(app.tidal.allPlaylists().any { it.id == playlist.id })
    }

    @Test fun favoriteChangesMadeOfflineAreUploadedWhenReconnected() = runBlocking {
        compose.runOnIdle { model.settings { it.copy(offline = false) } }
        val track = app.tidal.search("Nujabes Feather").tracks.first()
        val accountId = app.tidal.accountId
        val wasSaved = app.tidal.allFavorites().any { it.id == track.id }
        compose.waitUntil(30_000) { !model.state.value.syncing }
        try {
            compose.runOnIdle { model.settings { it.copy(offline = true) } }
            app.library.setFavorite(track, wasSaved)
            compose.waitUntil(5_000) { model.state.value.favorites.any { it.id == track.id } == wasSaved }
            compose.runOnIdle { model.favorite(track) }
            compose.waitUntil(5_000) { app.library.pendingFavorites(accountId)[track.id] == !wasSaved }
            compose.waitUntil(5_000) { model.state.value.favorites.any { it.id == track.id } == !wasSaved }
            compose.runOnIdle { model.settings { it.copy(offline = false) } }
            compose.waitUntil(30_000) { app.library.pendingFavorites(accountId).isEmpty() && !model.state.value.syncing }
            assertEquals(!wasSaved, app.tidal.allFavorites().any { it.id == track.id })
        } finally {
            compose.runOnIdle { model.settings { it.copy(offline = false) } }
            app.tidal.setFavorite(track.id, wasSaved)
            app.library.setFavorite(track, wasSaved)
            app.library.favoriteSynced(accountId, track.id, !wasSaved)
        }
    }

    @Test fun aFailedSearchCanBeRetriedAfterTheNetworkReturns() {
        assumeTrue("This check changes the dedicated device's network", InstrumentationRegistry.getArguments().getString("networkToggle") == "true")
        val manager = app.getSystemService(ConnectivityManager::class.java)
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        compose.runOnIdle { model.settings { it.copy(offline = false) }; model.search("") }
        try {
            shell("svc wifi disable"); shell("svc data disable")
            compose.waitUntil(15_000) { manager.activeNetwork == null }
            compose.runOnIdle { model.search("Nujabes Feather") }
            compose.waitUntil(30_000) { model.state.value.searchError != null }
            shell("svc wifi enable"); shell("svc data enable")
            compose.waitUntil(20_000) { manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true }
            // Reconnection also starts library sync. It must not hide the failed search or its retry action.
            compose.onNodeWithTag("nav-1").performClick()
            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(30_000) { model.state.value.search.tracks.isNotEmpty() }
            assertNull(model.state.value.searchError)
        } finally {
            shell("svc wifi enable"); shell("svc data enable")
            compose.runOnIdle { model.search("") }
        }
    }

    @Test fun losslessStreamDownloadsAndPlaysWithoutAnAccount() = runBlocking {
        val query = InstrumentationRegistry.getArguments().getString("liveQuery") ?: "Norah Jones Don't Know Why"
        val track = app.tidal.search(query).tracks.first()
        compose.runOnIdle {
            model.clearQueue()
            model.settings { it.copy(quality = AudioQuality.HI_RES_LOSSLESS, mobileQuality = AudioQuality.LOSSLESS, downloadOnWifi = false, offline = false) }
            model.play(listOf(track))
        }
        compose.waitUntil(30_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        compose.waitUntil(10_000) { model.state.value.playback.position > 500 && model.state.value.playback.quality.startsWith("FLAC") }
        assertTrue(model.state.value.playback.duration > 30_000)
        compose.runOnIdle { model.download(track) }
        compose.waitUntil(120_000) {
            app.library.get(track.id)?.isLocal == true || WorkManager.getInstance(app).getWorkInfosForUniqueWork("download:${track.id}").get().any { it.state == androidx.work.WorkInfo.State.FAILED }
        }
        assertTrue("The live audio download did not complete", app.library.get(track.id)?.isLocal == true)
        compose.runOnIdle { model.clearQueue(); model.signOut(); model.settings { it.copy(offline = true) } }
        compose.waitUntil(5_000) { model.state.value.playback.queue.isEmpty() }
        app.sources.clearStreamingCache()
        assertFalse(app.tidal.signedIn)
        // The queue metadata predates the download; resolution must still find the saved copy.
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(20_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        compose.waitUntil(5_000) { model.state.value.playback.position > 500 }
        val nearEnd = model.state.value.playback.duration - 20_000
        compose.runOnIdle { model.seek(nearEnd) }
        compose.waitUntil(10_000) { model.state.value.playback.position > nearEnd + 500 }
        assertTrue(model.state.value.playback.playing)
        compose.runOnIdle { model.togglePlay(); model.settings { it.copy(offline = false) } }
    }

}
