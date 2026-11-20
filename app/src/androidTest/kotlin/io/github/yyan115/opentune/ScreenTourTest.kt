package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.data.DownloadWorker
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks the screens that a listener without an account sees and saves a picture of each one, in the folder of the test output.
 * It is a tool for reviewing the interface and runs only with the instrumentation argument `screenTour` set to `true`.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ScreenTourTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Before fun onlyWhenAsked() { assumeTrue(InstrumentationRegistry.getArguments().getString("screenTour") == "true") }

    private fun picture(name: String) {
        compose.waitForIdle(); Thread.sleep(600); compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 5_000)
        save(name)
    }

    /** A picture of a screen that never rests, such as the visualizer, which cannot wait for the screen to be idle. */
    private fun movingPicture(name: String) { Thread.sleep(80); save(name) }

    private fun stepFrame() { compose.mainClock.advanceTimeBy(16); Thread.sleep(16) }

    private fun save(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(compose.activity.getExternalFilesDir(null), "tour")).apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun tour() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", offline = false, welcomeDone = true) } }
        val file = File(app.filesDir, "tour.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val tracks = listOf("Night Walk" to "Local audio checks", "Glass Rooms" to "Local audio checks", "Slow Tide" to "Salt and Glass").mapIndexed { index, (title, album) ->
            Track("local:tour-$index", title, if (index < 2) "Harbour Lights" else "Maren Vale", album, durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC · 24-bit · 96.0 kHz · 2304 kbps")
        }
        tracks.forEach(app.library::save)
        app.library.playlists().filter { it.title == "Evening" }.forEach { app.library.deletePlaylist(it.id) }
        val playlist = app.library.createPlaylist("Evening"); app.library.addToPlaylist(playlist.id, tracks[0])
        compose.waitUntil(10_000) { model.state.value.local.size >= 3 && model.state.value.localPlaylists.any { it.title == "Evening" } }
        picture("1-on-device")
        compose.onNodeWithTag("device-list").performScrollToNode(hasContentDescription("Options for Night Walk"))
        compose.onAllNodesWithContentDescription("Options for Night Walk").onFirst().performClick()
        picture("2-track-menu")
        compose.onNode(hasText("Details") and hasClickAction()).performClick()
        picture("3-details")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Evening"))
        compose.onNodeWithText("Evening").performClick()
        picture("4-playlist")
        compose.onNodeWithTag("add-songs").performClick()
        picture("5-add-songs")
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model.play(tracks) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 3 }
        compose.onNodeWithTag("mini-player").performClick()
        picture("6-player")
        compose.onNodeWithText("Queue · 3").performClick()
        picture("7-queue")
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model.stopPlayback() }
        compose.onNodeWithContentDescription("Settings").performClick()
        picture("8-settings")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Experimental"))
        picture("9-experimental")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        picture("10-appearance")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Licenses"))
        compose.onNode(hasText("Licenses") and hasClickAction()).performClick()
        picture("11-licences")
    }

    @Test fun settingsAndFirstScreen() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", offline = false, welcomeDone = false, avoidRepeatMinutes = 0) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("welcome").fetchSemanticsNodes().isNotEmpty() }
        picture("20-first-screen")
        compose.runOnIdle { model.settings { it.copy(welcomeDone = true) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        picture("21-on-device")
        compose.onNodeWithContentDescription("Settings").performClick()
        picture("22-settings-top")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Licenses"))
        picture("23-settings-about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Listening statistics"))
        compose.onNode(hasText("Listening statistics") and hasClickAction()).performClick()
        app.library.clearHistory()
        picture("24-statistics-empty")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid repeats"))
        compose.runOnIdle { model.settings { it.copy(avoidRepeatMinutes = 1440) } }
        compose.onNode(hasText("Avoid repeats") and hasClickAction()).performClick()
        picture("25-avoid-repeats")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { model.settings { it.copy(avoidRepeatMinutes = 0) } }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Licenses"))
        compose.onNode(hasText("Licenses") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("licences-list").fetchSemanticsNodes().isNotEmpty() }
        picture("26-licences")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithTag("theme-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        picture("27-theme-preview")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { model.settings { it.copy(equalizer = true) } }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Equalizer"))
        compose.onNode(hasText("Equalizer") and hasClickAction()).performClick()
        if (compose.onAllNodesWithText("Preset").fetchSemanticsNodes().isNotEmpty()) {
            picture("28-equalizer")
            compose.onNodeWithText("Preset").performClick()
            picture("29-equalizer-presets")
        }
    }

    @Test fun downloadQueueAndCustomRepeatTime() {
        val track = Track("2200002", "Queued Song", "Queue Artist")
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, downloadOnWifi = true, avoidRepeatMinutes = 0) } }
        app.library.save(track)
        val manager = WorkManager.getInstance(app)
        // The download waits for a device that is idle, which a device in a test is not, so that the queue has an entry to show.
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("track" to JsonCodec.encodeTrack(track).toString()))
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build()).addTag("audio-download").addTag("track:${track.id}").build()
        manager.enqueueUniqueWork("download:${track.id}", ExistingWorkPolicy.REPLACE, request).result.get()
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Download queue").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("device-list").performScrollToNode(hasText("Download queue"))
            picture("40-on-device-queue-row")
            compose.onNode(hasText("Download queue") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Queued Song").fetchSemanticsNodes().isNotEmpty() }
            picture("41-download-queue")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.runOnIdle { model.settings { it.copy(avoidRepeatMinutes = 2160) } }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid repeats"))
            compose.onNode(hasText("Avoid repeats") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("repeats-days").fetchSemanticsNodes().isNotEmpty() }
            picture("42-avoid-repeats-custom-time")
        } finally {
            manager.cancelAllWorkByTag("audio-download").result.get()
            manager.pruneWork().result.get()
            app.library.removeLocal(track.id)
            compose.runOnIdle { model.settings { it.copy(avoidRepeatMinutes = 0) } }
        }
    }

    @Test fun visualizerOverMusic() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= io.github.yyan115.opentune.ui.VISUALIZER_API)
        val file = File(app.filesDir, "music-clip.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("music-clip.flac").use { input -> file.outputStream().use(input::copyTo) }
        val track = Track("local:music-clip", "Harbour Lights", "Maren Vale", "Salt and Glass", durationMs = 16_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC · 16-bit · 44.1 kHz")
        app.library.save(track)
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
        try {
            compose.runOnIdle { model.play(listOf(track)) }
            compose.waitUntil(15_000) { model.state.value.playback.playing }
            compose.onNodeWithTag("mini-player").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("player-artist").fetchSemanticsNodes().isNotEmpty() }
            // The visualizer redraws at every frame, so the screen is never idle after this. It is turned on last,
            // and the frames are stepped by hand, because a test only draws a frame when its clock moves.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.settings { it.copy(visualizer = true) } }
            compose.mainClock.autoAdvance = false
            repeat(60) { stepFrame() }
            for (index in 1..4) {
                repeat(22) { stepFrame() }
                movingPicture("5$index-visualizer")
            }
        } finally {
            compose.mainClock.autoAdvance = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.settings { it.copy(visualizer = false) }; model.stopPlayback() }
            app.library.removeLocal(track.id)
        }
    }

    @Test fun swipeToQueue() {
        val file = File(app.filesDir, "tour.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val tracks = listOf("Night Walk", "Glass Rooms", "Slow Tide").mapIndexed { index, title ->
            Track("local:tour-swipe-$index", title, "Harbour Lights", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false) } }
        try {
            compose.waitUntil(10_000) { model.state.value.local.count { it.id.startsWith("local:tour-swipe") } == 3 }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("device-list").performScrollToNode(hasText("Glass Rooms"))
            picture("70-track-rows")
            val y = compose.onNode(hasText("Glass Rooms")).fetchSemanticsNode().boundsInRoot.center.y
            compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * .05f, y), androidx.compose.ui.geometry.Offset(width * .40f, y), 500) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("swipe-actions").fetchSemanticsNodes().isNotEmpty() }
            picture("71-track-row-swiped")
        } finally {
            tracks.forEach { app.library.removeLocal(it.id) }
        }
    }

    @Test fun playerFollowsTheFinger() {
        val file = File(app.filesDir, "tour.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val tracks = listOf("Night Walk", "Glass Rooms", "Slow Tide").mapIndexed { index, title ->
            Track("local:tour-drag-$index", title, "Harbour Lights", "Local audio checks", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC")
        }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
        try {
            compose.runOnIdle { model.play(tracks) }
            compose.waitUntil(15_000) { model.state.value.playback.queue.size == 3 }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
            val height = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot.height
            // The finger stays down on the small player after the first move, so that the picture shows the player half way up.
            compose.onNodeWithTag("mini-player").performTouchInput { down(center); moveTo(androidx.compose.ui.geometry.Offset(center.x, center.y - height * .55f)) }
            compose.waitForIdle()
            save("80-player-dragged-up")
            compose.onNodeWithTag("mini-player").performTouchInput { up() }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Close player").fetchSemanticsNodes().isNotEmpty() }
            picture("81-player-open")
            compose.onNodeWithTag("player-cover").performTouchInput { down(center); moveTo(androidx.compose.ui.geometry.Offset(center.x - width * .45f, center.y)) }
            compose.waitForIdle()
            save("82-cover-dragged-left")
            compose.onNodeWithTag("player-cover").performTouchInput { up() }
            compose.waitUntil(5_000) { model.state.value.playback.index == 1 }
            picture("83-next-track")
        } finally {
            compose.runOnIdle { model.stopPlayback() }
            tracks.forEach { app.library.removeLocal(it.id) }
        }
    }

    @Test fun dialogsInEveryTheme() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false) } }
        for (theme in io.github.yyan115.opentune.ui.appThemes) {
            compose.runOnIdle { model.settings { it.copy(theme = theme.id) } }
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isEmpty()) compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Clear listening history"))
            compose.onNode(hasText("Clear listening history") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("app-dialog").fetchSemanticsNodes().isNotEmpty() }
            picture("60-dialog-${theme.id}")
            compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("app-dialog").fetchSemanticsNodes().isEmpty() }
        }
        compose.runOnIdle { model.settings { it.copy(theme = "dark") } }
    }

    @Test fun emptyDeviceList() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true) } }
        app.library.localTracks().forEach { app.library.removeLocal(it.id) }
        app.library.playlists().forEach { app.library.deletePlaylist(it.id) }
        compose.waitUntil(10_000) { model.state.value.local.isEmpty() && model.state.value.localPlaylists.isEmpty() }
        picture("30-empty-device")
    }

    @Test fun permissionsPage() {
        assumeTrue("A permission of this version of Android is still missing", io.github.yyan115.opentune.ui.Permissions.missing(app).isNotEmpty())
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, setupPending = true) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("setup").fetchSemanticsNodes().isNotEmpty() }
        picture("31-permissions")
        compose.runOnIdle { model.settings { it.copy(setupPending = false) } }
    }
}
