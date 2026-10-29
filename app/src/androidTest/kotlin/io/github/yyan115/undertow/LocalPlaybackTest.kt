package io.github.yyan115.undertow

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: UndertowApp
    private lateinit var model: MainViewModel
    private lateinit var track: Track

    @Before fun seedOriginalTestAudio() {
        app = compose.activity.application as UndertowApp
        model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.runOnIdle {
            model.clearQueue()
            model.settings { it.copy(theme = "light", offline = false) }
        }
        app.library.playlists().filter { it.title == "Evening" }.forEach { app.library.deletePlaylist(it.id) }
        val file = File(app.filesDir, "playback-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        track = Track("local:playback-test", "Night Walk", "Playback test", "Local audio checks", durationMs = 30_000,
            localUri = Uri.fromFile(file).toString(), quality = "FLAC · 24-bit · 96 kHz")
        app.library.save(track)
        compose.waitUntil(10_000) { model.state.value.local.any { it.id == track.id } }
    }

    @Test fun localFlacPlaysThroughTheInterfaceAndPauses() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithTag("library-filters").performScrollToNode(hasText("On device"))
        compose.onNodeWithText("On device").performClick()
        compose.onNodeWithText("Night Walk").performClick()
        compose.waitUntil(25_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        assertTrue("FLAC did not begin playing", model.state.value.playback.playing)
        compose.waitUntil(10_000) { model.state.value.playback.position > 500 }
        screenshot("library-playing")
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.waitUntil(5_000) { !model.state.value.playback.playing }
        assertTrue(model.state.value.playback.duration > 29_000)
    }

    @Test fun queueCanBeEditedWithoutLosingDuplicateTracks() {
        compose.runOnIdle { model.play(listOf(track, track.copy(id = "local:playback-test-2", title = "Second Track"), track)) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 3 }
        compose.onNodeWithTag("mini-player").performClick()
        screenshot("now-playing")
        compose.onNodeWithText("Queue · 3").performClick()
        compose.onNodeWithContentDescription("Remove Second Track from queue").performClick()
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 2 }
        assertEquals(listOf(track.id, track.id), model.state.value.playback.queue.map(Track::id))
        screenshot("queue")
    }

    @Test fun localPlaylistCreationAndSavingArePersistent() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Name").performTextInput("Evening")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { app.library.playlists().any { it.title == "Evening" } }
        val playlist = app.library.playlists().first { it.title == "Evening" }
        app.library.addToPlaylist(playlist.id, track)
        compose.waitUntil(5_000) { model.state.value.localPlaylists.any { it.id == playlist.id && it.trackIds.size == 1 } }
        compose.onNodeWithText("Evening").performClick()
        compose.onNodeWithText("Night Walk").assertIsDisplayed()
        assertEquals(track.id, app.library.playlistTracks(playlist.id).single().id)
        screenshot("local-playlist")
    }

    @Test fun mainScreensAndThemeCanBeOpened() {
        screenshot("listen")
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Tracks, albums, artists").performTextInput("Night")
        compose.waitUntil(5_000) { model.state.value.searchQuery == "Night" }
        compose.onNodeWithText("Night Walk").assertIsDisplayed()
        screenshot("search-local")
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("No downloads yet").assertIsDisplayed()
        screenshot("downloads-empty")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { model.state.value.settings.theme == "dark" }
        screenshot("settings-dark")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(app.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
