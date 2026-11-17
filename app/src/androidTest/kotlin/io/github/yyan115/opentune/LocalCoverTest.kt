package io.github.yyan115.opentune

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A picture that sits inside several files is stored once, and it stays only as long as a track uses it. */
@RunWith(AndroidJUnit4::class)
class LocalCoverTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val directory get() = File(app.filesDir, "artwork")
    private val files = mutableListOf<File>()
    private val tracks = mutableListOf<Track>()

    private fun copy(asset: String): Uri {
        val file = File(app.filesDir, "cover-check-$asset.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("$asset.flac").use { input -> file.outputStream().use(input::copyTo) }
        files += file
        return Uri.fromFile(file)
    }

    private fun import(asset: String): Track = runBlocking { app.importer.import(copy(asset), direct = true) }.also { tracks += it }
    private fun coverFile(track: Track) = File(Uri.parse(track.artwork).path!!)

    @Before fun startClean() { directory.listFiles()?.filter { it.name.startsWith("embedded_") }?.forEach(File::delete) }

    @After fun clean() {
        tracks.forEach { app.library.removeLocal(it.id) }
        files.forEach(File::delete)
    }

    @Test fun tracksWithTheSamePictureShareOneFile() {
        val first = import("cover-a1"); val second = import("cover-a2"); val other = import("cover-b")
        assertNotNull(first.artwork)
        assertEquals("The same picture is one file", coverFile(first), coverFile(second))
        assertNotEquals("Another picture is another file", coverFile(first), coverFile(other))
        assertEquals(2, directory.listFiles()!!.count { it.name.startsWith("embedded_") })
    }

    @Test fun aPictureIsRemovedWhenTheLastTrackThatUsesItIsRemoved() {
        val first = import("cover-a1"); val second = import("cover-a2")
        val shared = coverFile(first)
        app.library.removeLocal(first.id)
        assertTrue("A track still uses the picture", shared.isFile)
        app.library.removeLocal(second.id)
        assertFalse("No track uses the picture", shared.exists())
    }

    @Test fun aPictureThatAnotherTrackUsesIsNotRemovedWithTheTrackOfAnotherPicture() {
        val first = import("cover-a1"); val other = import("cover-b")
        val kept = coverFile(first)
        app.library.removeLocal(other.id)
        assertTrue(kept.isFile)
        assertFalse(coverFile(other).exists())
    }
}
