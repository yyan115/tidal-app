package io.github.yyan115.opentune

import android.content.ComponentName
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.playback.AudioOutput
import io.github.yyan115.opentune.playback.EqualizerInfo
import io.github.yyan115.opentune.playback.PlaybackService
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Sleep timer, volume normalization, equalizer and output information, using an original test recording. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackFeaturesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: OpenTuneApp
    private lateinit var model: MainViewModel
    private lateinit var track: Track

    @Before fun seedAudio() {
        app = compose.activity.application as OpenTuneApp
        model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(offline = true, normalization = "off", equalizer = false) } }
        val file = File(app.filesDir, "features-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        track = Track("local:features-test", "Night Walk", "Playback test", "Local audio checks", durationMs = 30_000,
            localUri = Uri.fromFile(file).toString(), quality = "FLAC · 24-bit · 96 kHz", replayGain = -6.0)
        app.library.save(track)
        compose.waitUntil(10_000) { model.state.value.local.any { it.id == track.id } }
    }

    private fun startPlaying() {
        compose.runOnIdle { model.play(listOf(track)) }
        compose.waitUntil(25_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
    }

    private fun volume(player: MediaController): Float = compose.runOnUiThread { player.volume }

    private fun controller(): MediaController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        var result: MediaController? = null
        future.addListener({ result = future.get() }, ContextCompat.getMainExecutor(context))
        compose.waitUntil(10_000) { result != null }
        return requireNotNull(result)
    }

    @Test fun sleepTimerPausesAtTheEndOfTheTrackAndCanBeCancelled() {
        startPlaying()
        compose.runOnIdle { model.setSleepTimer(15) }
        compose.waitUntil(10_000) { model.state.value.sleepEndsAt > System.currentTimeMillis() + 14 * 60_000 }
        compose.runOnIdle { model.setSleepTimer(0) }
        compose.waitUntil(10_000) { model.state.value.sleepEndsAt == 0L && !model.state.value.sleepAtTrackEnd }
        compose.runOnIdle { model.setSleepTimer(0, endOfTrack = true) }
        compose.waitUntil(10_000) { model.state.value.sleepAtTrackEnd }
        compose.runOnIdle { model.seek(model.state.value.playback.duration - 1_500) }
        compose.waitUntil(20_000) { !model.state.value.playback.playing }
        compose.waitUntil(10_000) { !model.state.value.sleepAtTrackEnd }
        assertEquals(0L, model.state.value.sleepEndsAt)
    }

    @Test fun normalizationTurnsALoudTrackDownAndOffRestoresIt() {
        startPlaying()
        val player = controller()
        try {
            compose.waitUntil(10_000) { volume(player) == 1f }
            compose.runOnIdle { model.settings { it.copy(normalization = "track") } }
            compose.waitUntil(10_000) { kotlin.math.abs(volume(player) - 0.5012f) < 0.01f }
            compose.runOnIdle { model.settings { it.copy(normalization = "off") } }
            compose.waitUntil(10_000) { volume(player) == 1f }
        } finally { compose.runOnUiThread { player.release() } }
    }

    @Test fun equalizerCanBeSwitchedOnWhilePlaying() {
        val info = EqualizerInfo.read()
        assertNotNull("The emulator should provide the built-in equalizer", info)
        assertTrue(info!!.bands > 0 && info.maxLevel > info.minLevel)
        startPlaying()
        compose.runOnIdle { model.settings { it.copy(equalizer = true, equalizerLevels = List(info.bands) { if (it == 0) info.maxLevel else 0 }) } }
        compose.waitUntil(5_000) { model.state.value.settings.equalizer }
        compose.waitUntil(10_000) { model.state.value.playback.position > 500 }
        assertTrue(model.state.value.playback.playing)
        compose.runOnIdle { model.settings { it.copy(equalizer = false) } }
        compose.waitUntil(10_000) { model.state.value.playback.playing }
    }

    @Test fun audioOutputListsTheDevicesAndMarksTheOneInUse() {
        val devices = AudioOutput.devices(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("The emulator has at least a speaker", devices.isNotEmpty())
        assertTrue(devices.all { it.name.isNotBlank() })
    }
}
