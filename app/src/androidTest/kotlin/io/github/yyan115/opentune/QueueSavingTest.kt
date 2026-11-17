package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.QueueOrder
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The playback service saves the queue and the place in it, so that they are kept when the app is swiped away. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class QueueSavingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val saved get() = app.getSharedPreferences("playback", android.content.Context.MODE_PRIVATE)

    private fun tracks(count: Int, uri: String) = (1..count).map { Track("local:save-$it", "Saved $it", "Save test", "Local audio checks", durationMs = 30_000, localUri = uri, quality = "FLAC") }

    private fun uri(): String {
        val file = File(app.filesDir, "save-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file).toString()
    }

    @After fun clean() { compose.runOnIdle { model.stopPlayback() } }

    private fun savedTitles() = JsonCodec.decodeTracks(saved.getString("queue", "[]").orEmpty()).map(Track::title)

    @Test fun theQueueTheIndexAndTheMarksAreSavedByTheService() {
        val list = tracks(4, uri())
        compose.runOnIdle { model.play(list) }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == 4 && model.state.value.playback.playing }
        compose.runOnIdle { model.queue(list[0].copy(id = "local:save-extra", title = "Saved Extra")) }
        compose.waitUntil(5_000) { model.state.value.playback.queue.size == 5 }
        compose.runOnIdle { model.next() }
        compose.waitUntil(15_000) { model.state.value.playback.index == 1 }
        // The service writes a moment after the last change.
        compose.waitUntil(10_000) { saved.getInt("index", -1) == 1 && savedTitles().size == 5 }
        assertEquals(listOf("Saved 1", "Saved Extra", "Saved 2", "Saved 3", "Saved 4"), savedTitles())
        assertEquals(listOf(false, true, false, false, false), QueueOrder.decode(saved.getString("manual", null), 5))
    }

    @Test fun aQueueOfThousandsOfTracksIsSavedWhole() {
        val list = tracks(3_000, uri())
        compose.runOnIdle { model.play(list) }
        compose.waitUntil(30_000) { model.state.value.playback.queue.size == 3_000 }
        compose.waitUntil(15_000) { savedTitles().size == 3_000 }
        assertEquals("Saved 3000", savedTitles().last())
    }

    @Test fun thePositionIsSavedWhilePlaying() {
        compose.runOnIdle { model.play(tracks(2, uri())) }
        compose.waitUntil(15_000) { model.state.value.playback.playing }
        compose.runOnIdle { model.seek(12_000) }
        compose.waitUntil(20_000) { saved.getLong("position", 0) >= 12_000 }
        assertTrue(saved.getLong("position", 0) >= 12_000)
    }
}
