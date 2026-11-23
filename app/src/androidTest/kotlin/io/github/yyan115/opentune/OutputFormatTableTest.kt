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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What reaches the audio output for each kind of file. A song of 16 bits or less, and every lossy song, must reach it as 16-bit samples, the way that
 * 0.7.0 sent them. Only a song of more than 16 bits may use float samples. A player that asked every decoder for float samples, as 0.8.0 did, fails the
 * rows of this table, and the loud and fast sound that some phones made of it can no longer reach a release unnoticed.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OutputFormatTableTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    /** A test recording, the decoder that it needs, the rate that the output must have and whether it holds more than 16 bits. */
    private class Row(val asset: String, val mime: String?, val rate: Int, val highResolution: Boolean)

    private val rows = listOf(
        Row("music-clip.flac", "audio/flac", 44_100, false),
        Row("table-wav16.wav", null, 44_100, false),
        Row("table-aac.m4a", "audio/mp4a-latm", 44_100, false),
        Row("table-mp3.mp3", "audio/mpeg", 44_100, false),
        Row("table-opus.ogg", "audio/opus", 48_000, false),
        Row("table-flac24.flac", "audio/flac", 48_000, true),
        Row("night-walk.flac", "audio/flac", 96_000, true),
        Row("table-wav24.wav", null, 48_000, true),
    )
    private val tracks = mutableMapOf<String, Track>()

    private fun canDecode(mime: String?) = mime == null || android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }

    @Before fun prepare() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", offline = false, welcomeDone = true, visualizer = false, bitPerfect = false, normalization = "off", equalizer = false) } }
        OutputPolicy.state.clear()
        rows.forEach { row ->
            val file = File(app.filesDir, "table-${row.asset}")
            InstrumentationRegistry.getInstrumentation().context.assets.open(row.asset).use { input -> file.outputStream().use(input::copyTo) }
            val track = Track("local:table-${row.asset}", row.asset, "Format table", "Local audio checks", durationMs = 1_500, localUri = Uri.fromFile(file).toString(), quality = "test")
            app.library.save(track); tracks[row.asset] = track
        }
        compose.waitUntil(10_000) { model.state.value.local.size >= rows.size }
    }

    @After fun clean() {
        compose.runOnIdle { model.stopPlayback() }
        OutputPolicy.state.clear()
        tracks.values.forEach { app.library.removeLocal(it.id) }
    }

    private fun play(track: Track) {
        val opened = OutputFormat.opened
        compose.runOnIdle { model.play(listOf(track), 0, "", shuffle = false) }
        // A song of more than 16 bits waits for the check of the guard first, so the song must be heard before the output is read.
        compose.waitUntil(20_000) { model.state.value.playback.playing && OutputFormat.opened > opened && model.position.value > 200 }
    }

    @Test fun everyKindOfFileReachesTheOutputInTheRightFormat() {
        val failures = mutableListOf<String>()
        for (row in rows) {
            if (!canDecode(row.mime)) continue
            play(tracks.getValue(row.asset))
            val encoding = OutputFormat.encoding
            val allowed = if (row.highResolution) setOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT) else setOf(C.ENCODING_PCM_16BIT)
            if (encoding !in allowed) failures += "${row.asset}: the output uses encoding $encoding, allowed are $allowed"
            if (OutputFormat.sampleRate != row.rate) failures += "${row.asset}: the output runs at ${OutputFormat.sampleRate} Hz and not at ${row.rate} Hz"
            if (!OutputPolicy.state.floatAllowed) failures += "${row.asset}: the check blocked the output of the phone: ${OutputPolicy.state.blockedReason}"
            compose.runOnIdle { model.stopPlayback() }
        }
        assertTrue("These files reached the output wrongly:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun aPhoneThatFailsTheCheckPlaysTheSongAt16BitsAndKeepsPlaying() {
        assumeTrue("This system has no FLAC decoder", canDecode("audio/flac"))
        val song = tracks.getValue("night-walk.flac")
        play(song)
        assertTrue("A song of 24 bits starts on a float output", OutputFormat.encoding in setOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT))
        val opened = OutputFormat.opened
        // This is what the guard does when the buffers of a decoder do not fit.
        OutputPolicy.state.block("The test says that the output failed.")
        compose.waitUntil(20_000) { OutputFormat.opened > opened && model.state.value.playback.playing && model.state.value.playback.track?.id == song.id }
        assertEquals("After the check failed the song plays at 16 bits", C.ENCODING_PCM_16BIT, OutputFormat.encoding)
        assertTrue(model.state.value.playback.error == null)
    }
}
