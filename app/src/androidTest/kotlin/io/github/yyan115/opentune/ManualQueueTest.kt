package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Tracks that the listener adds by hand play first, in the order of adding, ahead of the rest of the album or playlist. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ManualQueueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var playlist: List<Track>
    private lateinit var x: Track
    private lateinit var y: Track
    private lateinit var w: Track
    private lateinit var z: Track

    private fun local(id: String, title: String, uri: String) = Track("local:queue-$id", title, "Queue test", "Local audio checks", durationMs = 30_000, localUri = uri, quality = "FLAC")

    @Before fun playAPlaylist() {
        val file = File(app.filesDir, "queue-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        val uri = Uri.fromFile(file).toString()
        playlist = ('A'..'H').map { local("list-$it", "Song $it", uri) }
        x = local("x", "Added X", uri); y = local("y", "Added Y", uri); w = local("w", "Play next W", uri); z = local("z", "Added Z", uri)
        (playlist + listOf(x, y, w, z)).forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, avoidRepeatMinutes = 0) } }
        compose.runOnIdle { model.play(playlist, 0, "Test list", shuffle = false) }
        compose.waitUntil(20_000) { model.state.value.playback.playing && model.state.value.playback.queue.size == playlist.size }
    }

    @After fun clean() {
        compose.runOnIdle { model.settings { it.copy(avoidRepeatMinutes = 0) }; model.stopPlayback() }
        (playlist + listOf(x, y, w, z)).forEach { app.library.removeLocal(it.id) }
    }

    private fun titles() = model.state.value.playback.queue.map { it.title.removePrefix("Song ") }
    private fun waitForSize(size: Int) = compose.waitUntil(10_000) { model.state.value.playback.queue.size == size }

    @Test fun addedTracksPlayBeforeTheRestOfThePlaylistAndPlayNextGoesAheadOfThem() {
        compose.runOnIdle { model.queue(x) }; waitForSize(9)
        compose.runOnIdle { model.queue(y) }; waitForSize(10)
        assertEquals(listOf("A", "Added X", "Added Y", "B", "C", "D", "E", "F", "G", "H"), titles())
        compose.runOnIdle { model.queue(w, next = true) }; waitForSize(11)
        assertEquals(listOf("A", "Play next W", "Added X", "Added Y", "B", "C"), titles().take(6))
    }

    @Test fun theAddedTracksPlayInOrderAndTheGroupIsCountedFromTheTrackThatPlaysNow() {
        compose.runOnIdle { model.queue(x) }; waitForSize(9)
        compose.runOnIdle { model.queue(y) }; waitForSize(10)
        compose.runOnIdle { model.next() }
        compose.waitUntil(10_000) { model.state.value.playback.index == 1 }
        // Added X plays now, and Added Y is still to come, so a new track goes after it.
        compose.runOnIdle { model.queue(z) }; waitForSize(11)
        assertEquals(listOf("A", "Added X", "Added Y", "Added Z", "B", "C"), titles().take(6))
    }

    @Test fun shuffleLeavesTheAddedTracksInPlaceAndShufflingOffKeepsThem() {
        compose.runOnIdle { model.queue(x) }; waitForSize(9)
        compose.runOnIdle { model.queue(y) }; waitForSize(10)
        compose.runOnIdle { model.toggleShuffle() }
        compose.waitUntil(10_000) { model.state.value.playback.shuffle }
        Thread.sleep(500)
        val shuffled = titles()
        assertEquals(listOf("A", "Added X", "Added Y"), shuffled.take(3))
        assertEquals("Every track still takes part", listOf("B", "C", "D", "E", "F", "G", "H"), shuffled.drop(3).sorted())
        // A track added while shuffle is on goes after the group and is not shuffled away.
        compose.runOnIdle { model.queue(z) }; waitForSize(11)
        assertEquals(listOf("A", "Added X", "Added Y", "Added Z"), titles().take(4))
        compose.runOnIdle { model.toggleShuffle() }
        compose.waitUntil(10_000) { !model.state.value.playback.shuffle }
        Thread.sleep(500)
        assertEquals(listOf("A", "Added X", "Added Y", "Added Z", "B", "C", "D", "E", "F", "G", "H"), titles())
    }

    @Test fun aNewListPlayedWhileShuffleIsOnIsShuffledAndKeepsTheChosenTrackFirst() {
        compose.runOnIdle { model.toggleShuffle() }
        compose.waitUntil(10_000) { model.state.value.playback.shuffle }
        val more = (1..12).map { playlist[0].copy(id = "local:fresh-$it", title = "Fresh $it") }
        more.forEach(app.library::save)
        try {
            compose.runOnIdle { model.play(more, 4, "Another list") }
            compose.waitUntil(15_000) { model.state.value.playback.queue.size == 12 && model.state.value.playback.queue.first().title == "Fresh 5" }
            Thread.sleep(1_500)
            val shown = model.state.value.playback.queue.map(Track::title)
            assertEquals("The chosen track plays first", "Fresh 5", shown.first())
            assertEquals("Every track of the list is in the queue", more.map(Track::title).sorted(), shown.sorted())
            assertTrue("The rest of the queue is shuffled: $shown", shown.drop(1) != more.map(Track::title).filterNot { it == "Fresh 5" })
            // Turning shuffle off puts the rest of the list back in its own order.
            compose.runOnIdle { model.toggleShuffle() }
            val inOrder = listOf("Fresh 5") + more.map(Track::title).filterNot { it == "Fresh 5" }
            compose.waitUntil(10_000) { !model.state.value.playback.shuffle && model.state.value.playback.queue.map(Track::title) == inOrder }
        } finally { more.forEach { app.library.removeLocal(it.id) } }
    }

    @Test fun anEmptyQueueStartsPlayingTheTrack() {
        compose.runOnIdle { model.stopPlayback() }
        compose.waitUntil(10_000) { model.state.value.playback.queue.isEmpty() }
        compose.runOnIdle { model.queue(x) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 1 }
        assertTrue(titles() == listOf("Added X"))
    }
}
