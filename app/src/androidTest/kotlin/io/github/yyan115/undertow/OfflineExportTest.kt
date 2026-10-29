package io.github.yyan115.undertow

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@UnstableApi
@RunWith(AndroidJUnit4::class)
class OfflineExportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as UndertowApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Test fun localFileExportPreservesEveryByte(): Unit = runBlocking {
        val original = File(app.filesDir, "export-original.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> original.outputStream().use(input::copyTo) }
        val track = Track("local:export-original", "Night Walk", "Original recording", localUri = Uri.fromFile(original).toString())
        val exported = File(app.filesDir, "export-copy.flac")
        val spec = app.exporter.describe(track)
        assertEquals("audio/flac", spec.mimeType)
        assertTrue(spec.fileName.endsWith(".flac"))
        app.exporter.write(track, Uri.fromFile(exported))
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(original.readBytes()), MessageDigest.getInstance("SHA-256").digest(exported.readBytes()))
        original.delete(); exported.delete()
    }

    @Test fun downloadedFlacExportsAndPlaysWithoutNetworkOrAccountAccess() = runBlocking {
        assumeTrue("Requires an existing clear FLAC download", InstrumentationRegistry.getArguments().getString("existingDownload") == "true")
        val track = app.library.localTracks().first { !it.id.startsWith("local:") && it.quality.orEmpty().startsWith("FLAC") }
        val file = File(app.filesDir, "export-validation.flac")
        app.tidal.signOut()
        val progress = mutableListOf<Int>()
        app.exporter.write(track, Uri.fromFile(file), progress::add)
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("fLaC", file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }.toString(Charsets.US_ASCII))
        compose.runOnIdle {
            model.clearQueue()
            model.settings { it.copy(offline = true) }
            model.play(listOf(track.copy(id = "local:export-validation", localUri = Uri.fromFile(file).toString())))
        }
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
