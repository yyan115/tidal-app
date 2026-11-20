package io.github.yyan115.opentune

import android.content.ContentUris
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Problem
import io.github.yyan115.opentune.core.ServiceException
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** A finished download is one audio file in the Music/OpenTune folder. A file that is gone makes the track a stream again. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadFilesTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val audio get() = InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { it.readBytes() }

    @Test fun aFileIsWrittenToTheDownloadFolderReadBackAndRemoved() = runBlocking {
        val bytes = audio
        val stored = app.downloadFiles.create("OpenTune test - Night Walk.flac", "audio/flac") { it.write(bytes) }
        try {
            assertTrue(app.downloadFiles.readable(stored.uri))
            assertArrayEquals(bytes, app.contentResolver.openInputStream(stored.uri)!!.use { it.readBytes() })
            assertTrue("The key is the key that a scan of the music folder gives", stored.key.startsWith("com.android.externalstorage.documents|primary:Music/OpenTune/OpenTune test - Night Walk"))
            assertTrue(stored.key.endsWith(".flac"))
        } finally { assertTrue(app.downloadFiles.delete(stored.uri)) }
        assertFalse("The file is gone", app.downloadFiles.readable(stored.uri))
    }

    @Test fun aFailedWriteLeavesNoFile() = runBlocking {
        val name = "OpenTune test - Failed.flac"
        try { app.downloadFiles.create(name, "audio/flac") { it.write(1); throw IOException("The write failed") }; fail("The failure was swallowed") } catch (_: IOException) { }
        app.contentResolver.query(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.DISPLAY_NAME}=?", arrayOf(name), null)!!.use { assertEquals(0, it.count) }
    }

    @Test fun twoFilesWithTheSameNameBothStay() = runBlocking {
        val first = app.downloadFiles.create("OpenTune test - Twin.flac", "audio/flac") { it.write(1) }
        val second = app.downloadFiles.create("OpenTune test - Twin.flac", "audio/flac") { it.write(2) }
        try {
            assertNotEquals(first.uri, second.uri)
            assertNotEquals("Each file has its own key", first.key, second.key)
        } finally { app.downloadFiles.delete(first.uri); app.downloadFiles.delete(second.uri) }
    }

    @Test fun aDownloadWhoseFileIsGoneBecomesAStreamAgain() = runBlocking {
        val track = Track("2000001", "Gone", "Nobody", localUri = "file:///sdcard/Music/OpenTune/gone-for-good.flac", quality = "FLAC")
        app.library.save(track)
        app.library.recordDownloadFile("gone-key", track.id)
        app.settings.update { it.copy(offline = true) }
        try {
            try { app.sources.resolve(track); fail("The missing file was played") } catch (error: ServiceException) { assertEquals(Problem.NOT_AVAILABLE_OFFLINE, error.problem) }
        } finally { app.settings.update { it.copy(offline = false) } }
        assertNull("The track is not downloaded any more", app.library.get(track.id)?.localUri)
        assertFalse(app.library.isDownloadFile("gone-key"))
    }
}
