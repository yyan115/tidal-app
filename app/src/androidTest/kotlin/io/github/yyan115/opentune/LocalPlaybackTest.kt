package io.github.yyan115.opentune

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.ui.LocalPlaybackPosition
import io.github.yyan115.opentune.ui.OpenTuneRoot
import io.github.yyan115.opentune.ui.OpenTuneTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.QueueSwipeAction
import io.github.yyan115.opentune.data.AppSettings
import io.github.yyan115.opentune.ui.MainViewModel
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
    private lateinit var app: OpenTuneApp
    private lateinit var model: MainViewModel
    private lateinit var track: Track

    @Before fun seedOriginalTestAudio() {
        app = compose.activity.application as OpenTuneApp
        model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.runOnIdle {
            model.clearQueue()
            model.settings { it.copy(theme = "light", offline = false, welcomeDone = true, visualizer = false) }
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
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Night Walk"))
        compose.onNodeWithText("Night Walk").performClick()
        compose.waitUntil(25_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        assertTrue("FLAC did not begin playing", model.state.value.playback.playing)
        compose.waitUntil(10_000) { model.position.value > 500 }
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
        // A row leaves the queue with a swipe.
        compose.onNode(hasContentDescription("Reorder Second Track")).performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 2 }
        assertEquals(listOf(track.id, track.id), model.state.value.playback.queue.map(Track::id))
        screenshot("queue")
    }

    @Test fun localFlacRemainsPlayableInOfflineMode() {
        compose.runOnIdle { model.settings { it.copy(offline = true) }; model.play(listOf(track)) }
        compose.waitUntil(15_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        assertTrue(model.state.value.playback.playing)
        compose.waitUntil(5_000) { model.position.value > 500 }
        compose.runOnIdle { model.seek(15_000) }
        compose.waitUntil(5_000) { model.position.value in 15_000..25_000 }
        compose.runOnIdle { model.togglePlay() }
    }

    @Test fun sourceDetailsReportThePlayingFlacFormat() {
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(15_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        compose.waitUntil(5_000) { model.position.value > 500 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithContentDescription("Audio source details").performClick()
        compose.onNodeWithTag("source-codec").assertTextEquals("FLAC")
        compose.onNodeWithTag("source-bits").assertTextEquals("24 bit")
        compose.onNodeWithTag("source-rate").assertTextEquals("96 kHz")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.runOnIdle { if (model.state.value.playback.playing) model.togglePlay() }
    }

    @Test fun localPlaylistCreationAndSavingArePersistent() {
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("New playlist"))
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Name").performTextInput("Evening")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { app.library.playlists().any { it.title == "Evening" } }
        val playlist = app.library.playlists().first { it.title == "Evening" }
        app.library.addToPlaylist(playlist.id, track)
        compose.waitUntil(5_000) { model.state.value.localPlaylists.any { it.id == playlist.id && it.trackIds.size == 1 } }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Evening"))
        compose.onNodeWithText("Evening").performClick()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Night Walk"))
        compose.onNodeWithText("Night Walk").assertIsDisplayed()
        assertEquals(track.id, app.library.playlistTracks(playlist.id).single().id)
        screenshot("local-playlist")
    }

    @Test fun aNewPlaylistFromTrackOptionsContainsThatTrack() {
        compose.onNodeWithTag("device-list").performScrollToNode(hasContentDescription("Options for Night Walk"))
        compose.onNodeWithContentDescription("Options for Night Walk").performClick()
        compose.onNodeWithText("Add to playlist").performScrollTo().performClick()
        // The playlist section behind the dialog has the same action, and the dialog's own button comes last.
        compose.onAllNodesWithText("New playlist").onLast().performClick()
        compose.onNodeWithText("Name").performTextInput("Evening")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { app.library.playlists().any { it.title == "Evening" && it.trackIds == listOf(track.id) } }
        val playlist = app.library.playlists().single { it.title == "Evening" }
        assertEquals(track.id, app.library.playlistTracks(playlist.id).single().id)
    }

    @Test fun removingAnImportedTrackClearsItsQueueAndPlaylistButKeepsTheFile() {
        val playlist = app.library.createPlaylist("Removal check")
        try {
            app.library.addToPlaylist(playlist.id, track)
            compose.runOnIdle { model.play(listOf(track, track)) }
            compose.waitUntil(10_000) { model.state.value.playback.queue.size == 2 }
            compose.runOnIdle { model.removeLocal(track) }
            compose.waitUntil(5_000) { model.state.value.local.none { it.id == track.id } && model.state.value.playback.queue.isEmpty() }
            assertTrue(app.library.playlistTracks(playlist.id).isEmpty())
            assertTrue(File(Uri.parse(track.localUri).path!!).isFile)
        } finally { app.library.deletePlaylist(playlist.id) }
    }

    @Test fun mainScreensAndThemeCanBeOpened() {
        screenshot("on-device")
        // One place needs no navigation, and its filter does the work of a search.
        compose.onAllNodesWithTag("nav-device").assertCountEquals(0)
        compose.onAllNodesWithTag("nav-search").assertCountEquals(0)
        compose.onNodeWithText("Filter").performTextInput("Night")
        compose.onNodeWithText("Night Walk").assertIsDisplayed()
        screenshot("filter-local")
        compose.onNodeWithText("Add files").assertIsDisplayed()
        compose.onNodeWithText("Add folder").assertIsDisplayed()
        screenshot("downloads")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        // The sheet opens on the current theme. A swipe moves to the next one, and its button applies it to the whole app.
        compose.onNodeWithTag("theme-pager").performTouchInput { swipeLeft() }
        // The app shows the swiped theme at once, and the saved theme stays until Use.
        compose.waitUntil(5_000) { model.previewTheme.value != null }
        assertEquals("light", model.state.value.settings.theme)
        compose.onNodeWithTag("theme-apply").performClick()
        compose.waitUntil(5_000) { model.state.value.settings.theme != "light" }
        compose.waitUntil(5_000) { model.previewTheme.value == null }
        val chosen = model.state.value.settings.theme
        compose.onNodeWithText("Done").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { ViewModelProvider(compose.activity)[MainViewModel::class.java].state.value.settings.theme == chosen }
        screenshot("settings-dark")
    }

    @Test fun signedOutSettingsShowOnlyWhatWorksWithoutAnAccount() {
        compose.onNodeWithContentDescription("Settings").performClick()
        val list = compose.onNodeWithTag("settings-list")
        // Rows that change how TIDAL is streamed and downloaded need an account.
        for (hidden in listOf("Wi-Fi audio quality", "Mobile audio quality", "Autoplay", "Volume normalization", "Offline mode", "Download over mobile data", "Download saved tracks", "Download played tracks", "Audio output", "Keep saved tracks offline"))
            compose.onAllNodesWithText(hidden).assertCountEquals(0)
        // The update check can be turned off.
        list.performScrollToNode(hasText("Check for updates"))
        compose.onNode(hasText("Check for updates") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { !model.state.value.settings.checkUpdates }
        compose.onNode(hasText("Check for updates") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { model.state.value.settings.checkUpdates }
        // Experimental is a section of the list and not a page, so its rows show without a tap.
        list.performScrollToNode(hasText("Experimental"))
        list.performScrollToNode(hasText("Equalizer"))
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        list.performScrollToNode(hasText("ListenBrainz"))
        compose.onNodeWithText("ListenBrainz").assertIsDisplayed()
        list.performScrollToNode(hasText("Licenses"))
        compose.onNodeWithText("No ads, analytics or tracking. Streaming is provided by TIDAL.").assertIsDisplayed()
    }

    @Test fun theAvoidRepeatsPageListsTheExcludedTracksAndIncludesOneOnRequest() {
        app.library.clearHistory()
        app.library.recordPlay(track, 40_000)
        compose.runOnIdle { model.settings { it.copy(avoidRepeatHours = 24) } }
        try {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid repeats"))
            compose.onNode(hasText("Avoid repeats") and hasClickAction()).performClick()
            // Section labels are drawn in capitals.
            compose.onNodeWithText("AVOIDED TRACKS").assertIsDisplayed()
            compose.onNodeWithTag("repeats-list").performScrollToNode(hasText("Night Walk"))
            compose.onNodeWithText("Night Walk").assertIsDisplayed()
            compose.onNodeWithText("Allow").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("No avoided tracks").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.library.playedSince(0).isEmpty())
            // Every time window is one choice of several.
            compose.onNode(hasText("Off") and hasClickAction()).performClick()
            compose.waitUntil(5_000) { model.state.value.settings.avoidRepeatHours == 0 }
            compose.onAllNodesWithText("AVOIDED TRACKS").assertCountEquals(0)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("settings-list").assertIsDisplayed()
        } finally {
            compose.runOnIdle { model.settings { it.copy(avoidRepeatHours = 0) } }
            app.library.clearHistory()
        }
    }

    @Test fun theStatisticsPageSelectsOnePeriodAtATime() {
        app.library.clearHistory()
        app.library.recordPlay(track, 40_000)
        try {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Listening statistics"))
            compose.onNode(hasText("Listening statistics") and hasClickAction()).performClick()
            compose.onNodeWithTag("stats-range").assertIsDisplayed()
            compose.onNode(hasText("7 days") and hasClickAction()).assertIsNotSelected()
            compose.onNode(hasText("30 days") and hasClickAction()).assertIsSelected()
            compose.onNode(hasText("7 days") and hasClickAction()).performClick()
            compose.onNode(hasText("7 days") and hasClickAction()).assertIsSelected()
            compose.onNode(hasText("30 days") and hasClickAction()).assertIsNotSelected()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Night Walk").fetchSemanticsNodes().isNotEmpty() }
        } finally { app.library.clearHistory() }
    }

    @Test fun thePermissionsPageFollowsTheFirstScreenAndEndsWithContinue() {
        org.junit.Assume.assumeTrue("A permission of this version of Android is still missing", io.github.yyan115.opentune.ui.Permissions.missing(app).isNotEmpty())
        try {
            compose.runOnIdle { model.settings { it.copy(setupPending = true) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("setup").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Permissions").assertIsDisplayed()
            compose.onNodeWithTag("setup-continue").performClick()
            compose.waitUntil(5_000) { !model.state.value.settings.setupPending }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("setup").fetchSemanticsNodes().isEmpty() }
        } finally { compose.runOnIdle { model.settings { it.copy(setupPending = false) } } }
    }

    @Test fun theLicencesOpenAsAListAndEachDocumentOnItsOwnPage() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Licenses"))
        compose.onNode(hasText("Licenses") and hasClickAction()).performClick()
        // One row for OpenTune and for each component that it ships, each with its licence.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("licences-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("OpenTune") and hasText("Apache License 2.0")).assertIsDisplayed()
        compose.onNodeWithTag("licences-list").performScrollToNode(hasText("OkHttp and Okio"))
        compose.onNodeWithText("Square · Apache License 2.0").assertIsDisplayed()
        compose.onNodeWithTag("licences-list").performScrollToNode(hasText("JetBrains Mono"))
        compose.onNodeWithText("JetBrains · SIL Open Font License 1.1").assertIsDisplayed()
        compose.onNodeWithTag("licences-list").performScrollToNode(hasText("OpenTune"))
        compose.onNode(hasText("OpenTune") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Version 2.0, January 2004", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("licences-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("settings-list").assertIsDisplayed()
    }

    private fun screenshot(name: String, node: SemanticsNodeInteraction? = null) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 5_000)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(app.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
        val bitmap = node?.captureToImage()?.asAndroidBitmap() ?: InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun queueRowsCanBeDraggedToANewPosition() {
        val first = track.copy(id = "local:drag-1", title = "First Track"); val second = track.copy(id = "local:drag-2", title = "Second Track"); val third = track.copy(id = "local:drag-3", title = "Third Track")
        compose.runOnIdle { model.play(listOf(first, second, third)) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 3 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithText("Queue · 3").performClick()
        // A row is dragged by its handle past the row below it, then past the next one.
        fun dragFirstRowDown() = compose.onNodeWithContentDescription("Reorder First Track", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, 12f)); advanceEventTime(16) }
            up()
        }
        dragFirstRowDown()
        compose.waitUntil(5_000) { model.state.value.playback.queue.map(Track::title) == listOf("Second Track", "First Track", "Third Track") }
        compose.waitForIdle()
        dragFirstRowDown()
        compose.waitUntil(5_000) { model.state.value.playback.queue.map(Track::title) == listOf("Second Track", "Third Track", "First Track") }
    }

    @Test fun shuffleReordersTheQueueAndTurningItOffRestoresTheOrder() {
        val tracks = (1..12).map { track.copy(id = "local:shuffle-$it", title = "Track $it") }
        compose.runOnIdle { model.play(tracks) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 12 }
        compose.runOnIdle { model.toggleShuffle() }
        compose.waitUntil(5_000) { model.state.value.playback.shuffle && model.state.value.playback.queue.map(Track::id) != tracks.map(Track::id) }
        val shuffled = model.state.value.playback.queue
        assertEquals("The current track stays first", tracks.first().id, shuffled.first().id)
        assertEquals("No track is lost or doubled", tracks.map(Track::id).toSet(), shuffled.map(Track::id).toSet())
        assertEquals(12, shuffled.size)
        // A track queued to play next plays next, also while shuffle is on.
        compose.runOnIdle { model.queue(track.copy(id = "local:next-up", title = "Next Up"), next = true) }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 13 }
        assertEquals("Next Up", model.state.value.playback.queue[model.state.value.playback.index + 1].title)
        compose.runOnIdle { model.toggleShuffle() }
        compose.waitUntil(5_000) { !model.state.value.playback.shuffle && model.state.value.playback.queue.filterNot { it.id == "local:next-up" }.map(Track::id) == tracks.map(Track::id) }
        assertEquals("Next Up", model.state.value.playback.queue[1].title)
    }

    @Test fun swipingTheMiniPlayerNavigatesWithoutClearingTheQueue() {
        compose.runOnIdle { model.play(listOf(track, track.copy(id = "local:swipe-2", title = "Second Track"))) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 2 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithText("Queue · 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.onNodeWithTag("now-playing").assertDoesNotExist()
        assertEquals("A tap leaves the queue alone", 2, model.state.value.playback.queue.size)
        compose.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(track.id, model.state.value.playback.track?.id) }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == "local:swipe-2" }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == track.id }
        compose.runOnIdle { assertEquals(2, model.state.value.playback.queue.size) }
    }

    @Test fun seekThumbIsCenteredOnThePaintedTrack() {
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(10_000) { model.state.value.playback.duration > 0 }
        compose.runOnIdle { model.togglePlay(); model.seek(model.state.value.playback.duration / 2) }
        compose.waitUntil(5_000) { kotlin.math.abs(model.position.value - model.state.value.playback.duration / 2) < 1000 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top < 1f }
        val image = compose.onNodeWithTag("playback-slider").captureToImage()
        File(app.getExternalFilesDir(null), "seek-bar.png").outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = image.toPixelMap()
        val background = pixels[pixels.width / 2, 0]
        fun paintedRows(x: Int) = (0 until pixels.height).filter { y ->
            val c = pixels[x, y]
            kotlin.math.abs(c.red - background.red) + kotlin.math.abs(c.green - background.green) + kotlin.math.abs(c.blue - background.blue) > .15f
        }
        val columns = (0 until pixels.width).map(::paintedRows)
        val dot = columns.maxBy { it.size }
        val line = columns.filter { it.isNotEmpty() }.minBy { it.size }
        assertTrue("The capture must contain a dot, not only the line", dot.size > line.size * 2)
        assertEquals("The dot and line must share their vertical center", (line.first() + line.last()) / 2f, (dot.first() + dot.last()) / 2f, 1f)
    }

    @Test fun adjacentCoversFollowTheFingerAndShortDragsSnapBack() {
        val songs = (1..3).map { track.copy(id = "local:carousel-$it", title = "Carousel $it", album = "Cover $it") }
        compose.runOnIdle { model.play(songs, index = 1, shuffle = false) }
        compose.waitUntil(10_000) { model.state.value.playback.track?.id == songs[1].id }
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top < 1f }
        val cover = compose.onNodeWithTag("player-artwork")
        cover.performTouchInput { down(center); moveBy(Offset(-width * .45f, 0f), 400) }
        compose.onNodeWithTag("cover-current").assertIsDisplayed()
        compose.onNodeWithTag("cover-next").assertIsDisplayed()
        val nextLeft = compose.onNodeWithTag("cover-next").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { assertEquals(songs[1].id, model.state.value.playback.track?.id) }
        cover.performTouchInput { moveBy(Offset(-width * .1f, 0f), 150) }
        assertTrue(compose.onNodeWithTag("cover-next").fetchSemanticsNode().boundsInRoot.left < nextLeft - 10f)
        cover.performTouchInput { up() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == songs[2].id }
        cover.performTouchInput { down(center); moveBy(Offset(width * .4f, 0f), 400) }
        compose.onNodeWithTag("cover-previous").assertIsDisplayed()
        cover.performTouchInput { moveBy(Offset(-width * .3f, 0f), 500); advanceEventTime(200); up() }
        compose.onNodeWithTag("cover-previous").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(songs[2].id, model.state.value.playback.track?.id) }
        cover.performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == songs[1].id }
    }

    @Test fun swipingTheCoverChangesTracksButScrubbingDoesNot() {
        val songs = (1..3).map { track.copy(id = "local:cover-$it", title = "Cover $it") }
        compose.runOnIdle { model.play(songs, shuffle = false) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 3 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == songs[1].id }
        compose.runOnIdle { model.seek(10_000) }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == songs[0].id }
        compose.onNodeWithTag("playback-slider").performTouchInput {
            swipe(Offset(width * .2f, center.y), Offset(width * .6f, center.y))
        }
        compose.runOnIdle { assertEquals(songs[0].id, model.state.value.playback.track?.id) }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(songs[0].id, model.state.value.playback.track?.id) }
        compose.runOnIdle { model.jumpTo(2) }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == songs[2].id }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(songs[2].id, model.state.value.playback.track?.id) }
        compose.onNodeWithTag("now-playing").assertIsDisplayed()
    }

    @Test fun playerFollowsTheFingerBeforeItIsReleased() {
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(10_000) { model.state.value.playback.track != null }
        val mini = compose.onNodeWithTag("mini-player")
        mini.performTouchInput {
            down(center)
            moveBy(Offset(0f, -height * 4f), 300)
        }
        compose.onNodeWithTag("now-playing").assertIsDisplayed()
        val firstTop = compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top
        mini.performTouchInput { moveBy(Offset(0f, -height * 2f), 200) }
        val secondTop = compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The player must continue following the finger", secondTop < firstTop - 10f)
        mini.performTouchInput { up() }
    }

    @Test fun reversingThePlayerDragReturnsToTheMiniPlayer() {
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(10_000) { model.state.value.playback.track != null }
        val mini = compose.onNodeWithTag("mini-player")
        mini.performTouchInput { down(center); moveBy(Offset(0f, -height * 4f), 300) }
        compose.onNodeWithTag("now-playing").assertIsDisplayed()
        mini.performTouchInput { moveBy(Offset(0f, height * 3.8f), 600); up() }
        compose.onNodeWithTag("now-playing").assertDoesNotExist()
        mini.assertIsDisplayed()
    }

    /** Exercise a 360 x 640 dp viewport with large text without changing the device display. */
    @Test fun playerAndSwipeSettingsRemainUsableWithLargeText() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val state by model.state.collectAsState()
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalPlaybackPosition provides model.position) {
                    OpenTuneTheme("light") {
                        Box(Modifier.size(360.dp, 640.dp)) { OpenTuneRoot(model, state) }
                    }
                }
            }
        }
        compose.runOnIdle { model.play(listOf(track, track.copy(id = "local:layout-second", title = "Second Track"))) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 2 }
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top < 1f }
        screenshot("swipe-player-layout", compose.onNodeWithTag("now-playing"))
        val next = compose.onAllNodesWithContentDescription(app.getString(R.string.player_next))
            .filter(hasAnyAncestor(hasTestTag("now-playing")))[0]
        if (!next.isDisplayed()) next.performScrollTo()
        next.assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { model.state.value.playback.index == 1 }
        compose.onNodeWithContentDescription(app.getString(R.string.player_close)).performClick()
        compose.onNodeWithTag("now-playing").assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(app.getString(R.string.settings_swipe_action)))
        compose.onNodeWithText(app.getString(R.string.settings_swipe_action)).performClick()
        compose.onNodeWithText(app.getString(R.string.settings_swipe_ask)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.track_play_next)).assertIsDisplayed()
        compose.onNode(hasText(app.getString(R.string.track_add_queue)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        screenshot("swipe-action-settings", compose.onNode(isDialog()))
        compose.onNode(hasText(app.getString(R.string.track_add_queue)) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test fun askModeOffersAChoiceForEverySong() {
        val playlist = (1..100).map { track.copy(id = "local:choice-$it", title = "Source $it") }
        compose.runOnIdle { model.settings { it.copy(queueSwipeAction = QueueSwipeAction.Ask) }; model.play(playlist, shuffle = false) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 100 }
        if (model.state.value.signedIn) compose.onNodeWithTag("nav-device").performClick()
        compose.onNodeWithTag("device-list").performScrollToNode(hasText(track.title))
        compose.onNodeWithText(track.title).performTouchInput { swipeRight() }
        compose.onNodeWithTag("queue-choice").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.track_play_next)).performClick()
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 1 }
        compose.onNodeWithTag("queue-choice").assertDoesNotExist()
        assertEquals(track.id, model.state.value.playback.queue[1].id)
        compose.onNodeWithText(track.title).performTouchInput { swipeRight() }
        compose.onNodeWithTag("queue-choice").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.track_play_next)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.track_add_queue)).performClick()
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 2 }
        compose.onNodeWithText(track.title).performTouchInput { swipeRight() }
        compose.onNodeWithText(app.getString(R.string.track_play_next)).performClick()
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 3 }
        assertEquals(listOf(track.id, track.id, track.id, playlist[1].id), model.state.value.playback.queue.drop(1).take(4).map(Track::id))
        compose.runOnIdle { model.settings { it.copy(queueSwipeAction = QueueSwipeAction.AddToQueue) } }
    }

    @Test fun swipeSettingPersistsAndFixedModesDoNotAsk() {
        val first = track.copy(id = "local:setting-source", title = "Source")
        val pending = track.copy(id = "local:setting-pending", title = "Pending")
        compose.runOnIdle {
            model.settings { it.copy(queueSwipeAction = QueueSwipeAction.AddToQueue) }
            model.play(listOf(first, first.copy(id = "local:setting-source-2")), shuffle = false)
        }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 2 }
        compose.runOnIdle { model.queue(pending) }
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 1 }
        if (model.state.value.signedIn) compose.onNodeWithTag("nav-device").performClick()
        compose.onNodeWithTag("device-list").performScrollToNode(hasText(track.title))
        compose.onNodeWithText(track.title).performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 2 }
        compose.onNodeWithTag("queue-choice").assertDoesNotExist()
        assertEquals(listOf(first.id, pending.id, track.id), model.state.value.playback.queue.take(3).map(Track::id))
        compose.onNodeWithContentDescription(app.getString(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(app.getString(R.string.settings_swipe_action)))
        compose.onNodeWithText(app.getString(R.string.settings_swipe_action)).performClick()
        compose.onNodeWithText(app.getString(R.string.track_play_next)).performClick()
        compose.waitUntil(5_000) { model.state.value.settings.queueSwipeAction == QueueSwipeAction.PlayNext }
        assertEquals(QueueSwipeAction.PlayNext, AppSettings(app).state.value.queueSwipeAction)
        compose.onNodeWithContentDescription(app.getString(R.string.action_back)).performClick()
        compose.onNodeWithText(track.title).performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 3 }
        compose.onNodeWithTag("queue-choice").assertDoesNotExist()
        assertEquals(listOf(first.id, track.id, pending.id, track.id), model.state.value.playback.queue.take(4).map(Track::id))
        compose.runOnIdle { model.settings { it.copy(queueSwipeAction = QueueSwipeAction.AddToQueue) } }
    }

    @Test fun manualQueuePlaysBeforeTheRestOfALongPlaylist() {
        val playlist = (1..100).map { track.copy(id = "local:playlist-$it", title = "Playlist $it") }
        val a = track.copy(id = "local:manual-a", title = "Manual A")
        val b = track.copy(id = "local:manual-b", title = "Manual B")
        val c = track.copy(id = "local:manual-c", title = "Manual C")
        compose.runOnIdle { model.play(playlist, shuffle = false) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 100 }
        compose.runOnIdle { model.queue(a) }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 101 }
        assertEquals(a.id, model.state.value.playback.queue[1].id)
        compose.runOnIdle { model.queue(b); model.queue(c, next = true) }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 103 }
        assertEquals(listOf(playlist[0].id, c.id, a.id, b.id, playlist[1].id), model.state.value.playback.queue.take(5).map(Track::id))
        compose.runOnIdle { model.next() }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == c.id }
        compose.runOnIdle { model.queue(a) }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 104 }
        assertEquals(listOf(a.id, b.id, a.id, playlist[1].id), model.state.value.playback.queue.drop(model.state.value.playback.index + 1).take(4).map(Track::id))
        compose.runOnIdle { model.jumpTo(5) }
        compose.waitUntil(5_000) { model.state.value.playback.track?.id == playlist[1].id && model.state.value.playback.manualQueuedCount == 0 }
        compose.runOnIdle { model.queue(c) }
        compose.waitUntil(5_000) { model.state.value.playback.manualQueuedCount == 1 }
        assertEquals(c.id, model.state.value.playback.queue[6].id)
    }

    @Test fun verticalPlayerSwipesOpenAndCloseWithoutChangingPlayback() {
        compose.runOnIdle { model.play(listOf(track, track.copy(id = "local:vertical-2", title = "Second Track"))) }
        compose.waitUntil(10_000) { model.state.value.playback.queue.size == 2 }
        compose.onNodeWithTag("mini-player").performTouchInput {
            swipe(center, Offset(center.x, center.y - height * 3f))
        }
        compose.onNodeWithTag("now-playing").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onNodeWithTag("now-playing").fetchSemanticsNode().boundsInRoot.top < 1f }
        compose.onNodeWithTag("now-playing").performTouchInput {
            swipe(Offset(center.x, height * .12f), Offset(center.x, height * .75f))
        }
        compose.onNodeWithTag("now-playing").assertDoesNotExist()
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, model.state.value.playback.queue.size)
            assertEquals(track.id, model.state.value.playback.track?.id)
        }
    }

    @Test fun openingAnArtistFromThePlayerLeavesTheSettingsBehindIt() {
        // A downloaded track plays from the device and still belongs to TIDAL, so its artist has a page.
        val downloaded = track.copy(id = "4242", title = "Downloaded Track", artist = "Page Artist", artistId = "99", albumId = "98")
        app.library.save(downloaded)
        compose.runOnIdle { model.play(listOf(downloaded)) }
        compose.waitUntil(10_000) { model.state.value.playback.track?.id == downloaded.id }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").assertExists()
        compose.onNodeWithTag("mini-player").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-artist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player-artist").performClick()
        compose.waitUntil(5_000) { model.state.value.routes.isNotEmpty() }
        compose.onNodeWithTag("settings-list").assertDoesNotExist()
        compose.onNodeWithTag("player-artist").assertDoesNotExist()
    }

    @Test fun theVisualizerReadsTheSamplesThatTheOutputReceives() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= io.github.yyan115.opentune.ui.VISUALIZER_API)
        compose.runOnIdle { model.settings { it.copy(visualizer = true) } }
        try {
            io.github.yyan115.opentune.playback.AudioSpectrum.clear()
            compose.runOnIdle { model.play(listOf(track)) }
            compose.waitUntil(15_000) { model.state.value.playback.playing }
            val samples = FloatArray(io.github.yyan115.opentune.core.Spectrum.SIZE)
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end && !io.github.yyan115.opentune.playback.AudioSpectrum.latest(samples)) Thread.sleep(100)
            assertTrue("The tap received no samples: ${io.github.yyan115.opentune.playback.AudioSpectrum}", io.github.yyan115.opentune.playback.AudioSpectrum.latest(samples))
            assertTrue("The tap sees the music, not silence", samples.any { kotlin.math.abs(it) > 0.001f })
            val bands = io.github.yyan115.opentune.core.Spectrum.bands(samples, io.github.yyan115.opentune.playback.AudioSpectrum.sampleRate)
            assertTrue("Some band carries energy", bands.any { it > 0.05f })
        } finally {
            // The setting is kept on the device, so it must not stay on for the tests that follow.
            compose.runOnIdle { model.settings { it.copy(visualizer = false) }; model.stopPlayback() }
        }
    }
}
