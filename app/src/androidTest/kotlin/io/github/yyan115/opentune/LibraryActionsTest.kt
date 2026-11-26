package io.github.yyan115.opentune

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.ReleaseType
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.PlaylistHome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * What a listener with an account does with the library, against the fake TIDAL: radio, mixes and top tracks with and without the filter for AI-generated music,
 * the queue saved as a playlist on the account, and playlist folders. The view model ran none of this in the tests before the fake existed.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LibraryActionsTest {
    private val fake = FakeTidal()
    private val screen = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fake).around(screen)

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val model get() = ViewModelProvider(screen.activity)[MainViewModel::class.java]
    private val queue get() = model.state.value.playback.queue.map(Track::id)

    @Before fun prepare() {
        screen.runOnIdle { model.clearQueue(); model.settings { it.copy(offline = false, welcomeDone = true, downloadSaved = false, hideAiMusic = false, theme = "dark") } }
        // The view model loads the library by itself when the screen starts, and the tests start after that load.
        screen.waitUntil(30_000) { fake.log.any { it.contains("playlistsAndFavoritePlaylists") } && !model.state.value.syncing }
    }

    @After fun clean() {
        screen.runOnIdle { model.stopPlayback(); model.settings { it.copy(hideAiMusic = false) } }
        app.library.clearFavorites(app.library.favorites().map(Track::id))
        app.library.clearCache(fake.userId)
    }

    private val seed = Track("100", "Seed", "Artist", "Album", "5")

    private fun startRadio(name: String) = when (name) {
        "radio of a track" -> model.playTrackRadio(seed)
        "radio of an artist" -> model.playArtistRadio("1")
        "top tracks of an artist" -> model.playArtistTopTracks("1")
        else -> model.playMix("mix-1")
    }

    private val kinds = listOf("radio of a track", "radio of an artist", "top tracks of an artist", "mix")

    private fun expectedQueue(name: String, hide: Boolean): List<String> {
        val songs = fake.radio.filter { !(hide && it in fake.marked) }.map(Int::toString)
        return if (name == "radio of a track") listOf("100") + songs else songs
    }

    @Test fun radioMixesAndTopTracksPlayTheSongsThatTidalSends() {
        fake.radio.addAll(listOf(201, 202, 203, 204))
        for (name in kinds) {
            screen.runOnIdle { model.clearQueue() }
            screen.runOnIdle { startRadio(name) }
            screen.waitUntil(30_000) { queue == expectedQueue(name, hide = false) }
        }
    }

    @Test fun songsThatTidalMarksAsAiAreLeftOutWhenTheListenerChoseThat() {
        fake.radio.addAll(listOf(201, 202, 203, 204, 205, 206))
        fake.marked.addAll(listOf(203, 205))
        screen.runOnIdle { model.settings { it.copy(hideAiMusic = true) } }
        for (name in kinds) {
            screen.runOnIdle { model.clearQueue() }
            screen.runOnIdle { startRadio(name) }
            screen.waitUntil(30_000) { queue == expectedQueue(name, hide = true) }
            assertTrue("No song of $name is marked: $queue", queue.none { it == "203" || it == "205" })
        }
    }

    @Test fun anEmptyRadioTellsTheListenerAndPlaysNothing() {
        screen.runOnIdle { model.playTrackRadio(seed) }
        screen.waitUntil(30_000) { model.state.value.message != null }
        assertEquals(emptyList<String>(), queue)
    }

    @Test fun theQueueIsSavedOnTheAccountInPartsOfFiftyAndInOrder() {
        val songs = (301..420).map { Track("$it", "Song $it", "Artist", "Album", "5") }
        screen.runOnIdle { model.play(songs) }
        screen.waitUntil(30_000) { model.state.value.playback.queue.size == 120 }
        screen.runOnIdle { model.saveQueueAsPlaylist("Road trip", PlaylistHome.Account) }
        screen.waitUntil(30_000) { fake.playlistItems.values.sumOf { it.size } == 120 }
        val made = fake.playlistItems.entries.single()
        assertEquals("The songs were added in the order of the queue", (301..420).toList(), made.value.toList())
        val sent = fake.forms.filter { it.first.endsWith("/items") }.map { it.second.substringAfter("trackIds=").substringBefore("&").let { ids -> java.net.URLDecoder.decode(ids, "UTF-8").split(',').size } }
        assertEquals("Each request holds at most fifty songs", listOf(50, 50, 20), sent)
        val positions = fake.forms.filter { it.first.endsWith("/items") }.map { it.second.substringAfter("toIndex=").substringBefore("&").toInt() }
        assertEquals("Each part goes to the end of the playlist", listOf(0, 50, 100), positions)
        assertEquals("The new playlist is on the screen", true, model.state.value.playlists.any { it.title == "Road trip" })
    }

    @Test fun theQueueIsSavedOnTheDeviceWhenTheListenerChoosesTheDevice() {
        val songs = (501..503).map { Track("$it", "Device song $it", "Artist", "Album", "5") }
        screen.runOnIdle { model.play(songs) }
        screen.waitUntil(30_000) { model.state.value.playback.queue.size == 3 }
        val before = fake.log.size
        screen.runOnIdle { model.saveQueueAsPlaylist("On the phone", PlaylistHome.Device) }
        screen.waitUntil(30_000) { app.library.playlists().any { it.title == "On the phone" } }
        val saved = app.library.playlists().single { it.title == "On the phone" }
        assertEquals(listOf("501", "502", "503"), saved.trackIds)
        assertTrue("Nothing about playlists went to TIDAL", fake.since(before).none { it.contains("playlists") && !it.startsWith("GET") })
        app.library.deletePlaylist(saved.id)
    }

    @Test fun aFolderHoldsAPlaylistAndDeletingItBringsThePlaylistBackToTheTopLevel() {
        fake.playlists.add("a")
        screen.runOnIdle { model.sync() }
        screen.waitUntil(30_000) { model.state.value.playlists.size == 1 && !model.state.value.syncing }
        screen.runOnIdle { model.createFolder("Road") }
        screen.waitUntil(30_000) { fake.folders.values.contains("Road") }
        val folderId = fake.folders.entries.single().key
        screen.waitUntil(30_000) { model.state.value.folders["root"]?.any { it.type == ReleaseType.FOLDER && it.title == "Road" } == true }
        val playlist = model.state.value.playlists.single()
        screen.runOnIdle { model.moveToFolder(playlist, folderId) }
        screen.waitUntil(30_000) { fake.folderOf["a"] == folderId }
        screen.waitUntil(30_000) { model.state.value.folders["root"]?.none { it.type == ReleaseType.PLAYLIST } == true }
        val folder = model.state.value.folders["root"]!!.single { it.type == ReleaseType.FOLDER }
        screen.runOnIdle { model.deleteFolder(folder) }
        screen.waitUntil(30_000) { fake.folders.isEmpty() }
        assertEquals("The playlist went back to the top level before the folder went", "root", fake.folderOf["a"])
        screen.waitUntil(30_000) { model.state.value.folders["root"]?.any { it.type == ReleaseType.PLAYLIST } == true && model.state.value.folders["root"]?.none { it.type == ReleaseType.FOLDER } == true }
    }

    @Test fun aFolderCanBeRenamed() {
        screen.runOnIdle { model.createFolder("Road") }
        screen.waitUntil(30_000) { model.state.value.folders["root"]?.any { it.title == "Road" } == true }
        val folder = model.state.value.folders["root"]!!.single { it.title == "Road" }
        screen.runOnIdle { model.renameFolder(folder, "Trip") }
        screen.waitUntil(30_000) { model.state.value.folders["root"]?.any { it.title == "Trip" } == true }
        assertEquals(listOf("Trip"), fake.folders.values.toList())
    }
}
