package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.playback.OutputFormat
import io.github.yyan115.opentune.playback.OutputPolicy
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The output after songs of different kinds follow each other. A listener goes forward, goes back and jumps around in one queue, and the output of every
 * song must be right, whatever played before it. A reporter of loud and fast sound found that a song played fine the first time and was garbled after other songs
 * had played, so a test that plays each kind of file once, on a fresh player, cannot show that kind of fault.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OutputSequenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    private class Kind(val asset: String, val mime: String?, val highResolution: Boolean)

    private val kinds = listOf(
        Kind("music-clip.flac", "audio/flac", false),
        Kind("table-flac24.flac", "audio/flac", true),
        Kind("table-aac.m4a", "audio/mp4a-latm", false),
        Kind("table-wav24.wav", null, true),
        Kind("table-mp3.mp3", "audio/mpeg", false),
        Kind("night-walk.flac", "audio/flac", true),
        Kind("table-wav16.wav", null, false),
    )
    private lateinit var queue: List<Pair<Kind, Track>>

    private fun canDecode(mime: String?) = mime == null || android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }

    @Before fun prepare() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", offline = false, welcomeDone = true, visualizer = false, bitPerfect = false, normalization = "off", equalizer = false, limitTo16Bit = false) } }
        OutputPolicy.state.clear()
        queue = kinds.filter { canDecode(it.mime) }.map { kind ->
            val file = File(app.filesDir, "sequence-${kind.asset}")
            InstrumentationRegistry.getInstrumentation().context.assets.open(kind.asset).use { input -> file.outputStream().use(input::copyTo) }
            val track = Track("local:sequence-${kind.asset}", kind.asset, "Sequence", "Local audio checks", durationMs = 12_000, localUri = Uri.fromFile(file).toString(), quality = "test")
            app.library.save(track)
            kind to track
        }
        assumeTrue("The system decodes too few kinds of file for a sequence", queue.size >= 4)
        compose.waitUntil(10_000) { model.state.value.local.size >= queue.size }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback(); model.settings { it.copy(limitTo16Bit = false) } }
        OutputPolicy.state.clear()
        if (::queue.isInitialized) queue.forEach { app.library.removeLocal(it.second.id) }
    }

    private val failures = mutableListOf<String>()

    private var lastIndex = -1

    /**
     * Gives the command of a step and waits until the song of [index] plays and its output is open, and checks the output against the kind of file.
     * The position that the screen shows lags behind a change of song, so right after the change it can still tell that the old song plays. The output that
     * is read then is the output of the old song. The player opens an output when it starts a song, so a change to another song waits for a new output.
     * A song that starts again, such as a jump to the song that plays, opens none.
     */
    private fun heard(index: Int, step: String, command: () -> Unit) {
        val opened = OutputFormat.opened
        compose.runOnIdle(command)
        val (kind, track) = queue[index]
        compose.waitUntil(25_000) { model.state.value.playback.track?.id == track.id && model.state.value.playback.playing && model.position.value > 300 }
        if (index != lastIndex) {
            try { compose.waitUntil(10_000) { OutputFormat.opened > opened } }
            catch (_: androidx.compose.ui.test.ComposeTimeoutException) { failures += "$step, ${kind.asset}: the player opened no output for the song" }
        }
        lastIndex = index
        val allowed = if (kind.highResolution) setOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT) else setOf(C.ENCODING_PCM_16BIT)
        if (OutputFormat.encoding !in allowed) failures += "$step, ${kind.asset}: the output uses encoding ${OutputFormat.encoding}, allowed are $allowed"
        if (!OutputPolicy.state.floatAllowed) failures += "$step, ${kind.asset}: the check blocked the output: ${OutputPolicy.state.blockedReason}"
        if (model.state.value.playback.error != null) failures += "$step, ${kind.asset}: the player reports ${model.state.value.playback.error}"
    }

    @Test fun everySongIsRightAfterEveryOtherKindOfSong() {
        heard(0, "start") { model.play(queue.map { it.second }, 0, "", shuffle = false) }
        for (index in 1..queue.lastIndex) heard(index, "forward to $index") { model.next() }
        // Back through the whole queue. A press of Previous within three seconds goes to the song before.
        for (index in queue.lastIndex - 1 downTo 0) heard(index, "back to $index") { model.previous() }
        // Jumps between kinds, the same song twice in a row and songs that came before.
        val jumps = listOf(2, 0, 3, 1, 3, 5.coerceAtMost(queue.lastIndex), 2, 2, 0)
        for (jump in jumps) heard(jump, "jump to $jump") { model.jumpTo(jump) }
        assertTrue("These songs reached the output wrongly after other songs had played:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun aListenerWhoLimitsTheOutputTo16BitHearsEverySongAt16Bit() {
        compose.runOnIdle { model.settings { it.copy(limitTo16Bit = true) } }
        compose.runOnIdle { model.play(queue.map { it.second }, 0, "", shuffle = false) }
        for (index in queue.indices) {
            if (index > 0) compose.runOnIdle { model.next() }
            val (kind, track) = queue[index]
            compose.waitUntil(25_000) { model.state.value.playback.track?.id == track.id && model.state.value.playback.playing && model.position.value > 300 }
            if (OutputFormat.encoding != C.ENCODING_PCM_16BIT) failures += "${kind.asset}: the output uses encoding ${OutputFormat.encoding} and not 16-bit"
        }
        // When the check of the phone blocked the float output, 16 bits is the check's choice and not the setting's.
        if (!OutputPolicy.state.floatAllowed) failures += "the check blocked the float output, so the setting was not what chose 16 bits: ${OutputPolicy.state.blockedReason}"
        assertTrue("These songs did not reach the output at 16 bits:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
