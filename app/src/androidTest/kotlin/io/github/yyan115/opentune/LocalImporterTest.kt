package io.github.yyan115.opentune

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.DocumentKey
import io.github.yyan115.opentune.data.LocalImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reads a real FLAC file into the library and checks what the library knows about it afterwards. */
@RunWith(AndroidJUnit4::class)
class LocalImporterTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private lateinit var file: File
    private lateinit var uri: Uri

    @Before fun copyTheFile() {
        file = File(app.filesDir, "importer-check.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        uri = Uri.fromFile(file)
    }

    @After fun clean() {
        app.library.localTracks().filter { it.localUri == uri.toString() }.forEach { app.library.removeLocal(it.id) }
        file.delete()
    }

    @Test fun aFlacFileIsReadWithItsBitDepthAndSampleRate() = runBlocking {
        val track = app.importer.import(uri, modified = 5, size = file.length(), direct = true)
        assertTrue(track.isFile)
        assertTrue("The label starts with the codec, the bit depth and the sample rate: ${track.quality}", track.quality!!.startsWith("FLAC · 24-bit · 96.0 kHz"))
        assertTrue("The library tells where the file came from", app.library.isDirect(track.id))
        assertEquals(track.id, app.library.sourceFor(DocumentKey.of(uri.toString()))?.trackId)
    }

    @Test fun aFileThatADownloadBecameIsNotAddedAgain() = runBlocking {
        val key = DocumentKey.of(uri.toString())
        val downloaded = io.github.yyan115.opentune.core.Track("90210", "Night Walk", "Harbour Lights", "Late Hours", localUri = "opentune-download:90210")
        app.library.save(downloaded)
        app.library.recordDownloadFile(key, downloaded.id)
        try {
            val result = app.importer.import(uri, direct = true)
            assertEquals("The file gives back the downloaded track", downloaded.id, result.id)
            assertFalse("No second track of the file exists", app.library.localTracks().any { it.localUri == uri.toString() })
            assertNull("The file is not known as a file that was added", app.library.sourceFor(key))
        } finally {
            app.library.removeDownloadFile(downloaded.id)
            app.library.removeLocal(downloaded.id)
        }
    }

    @Test fun readingTheSameFileAgainKeepsOneTrackAndItsDate() = runBlocking {
        val first = app.importer.import(uri, direct = true)
        Thread.sleep(20)
        val second = app.importer.import(uri, modified = 9, size = 1, direct = true)
        assertEquals(first.id, second.id)
        assertEquals("The track keeps the time it was added", first.addedAt, second.addedAt)
        assertEquals(1, app.library.localTracks().count { it.id == first.id })
    }

    @Test fun aPictureFromTheFolderStandsInForMissingArtwork() = runBlocking {
        val picture = File(app.cacheDir, "cover.jpg")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.JPEG, 90, picture.outputStream())
        try {
            val track = app.importer.import(uri, cover = Uri.fromFile(picture), direct = true)
            val artwork = track.artwork
            assertNotNull("A file without a picture of its own takes the picture of its folder", artwork)
            assertTrue(File(Uri.parse(artwork).path!!).isFile)
        } finally { picture.delete() }
    }
}
