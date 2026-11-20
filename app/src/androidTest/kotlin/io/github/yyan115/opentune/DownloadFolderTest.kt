package io.github.yyan115.opentune

import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.DocumentKey
import io.github.yyan115.opentune.data.DownloadFolderUnavailableException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** New downloads go to the folder that the listener picked, and the downloads that exist stay where they are and keep working. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadFolderTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val audio get() = InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { it.readBytes() }
    private val authority get() = "${app.packageName}.testdocuments"
    private val directory get() = java.io.File(app.filesDir, "documents-test/download-test")
    private val tree: Uri get() = DocumentsContract.buildTreeDocumentUri(authority, "root/download-test")

    @Before fun useTheTestStorage() {
        directory.mkdirs()
        app.settings.update { it.copy(downloadFolder = tree.toString()) }
    }

    @After fun backToTheDefaultFolder() {
        app.settings.update { it.copy(downloadFolder = "") }
        directory.deleteRecursively()
    }

    @Test fun theTestStorageCanHoldFiles() {
        assertTrue(app.downloadFiles.usable(tree))
    }

    @Test fun aFileIsWrittenToThePickedFolderReadBackAndRemoved() = runBlocking {
        val bytes = audio
        val stored = app.downloadFiles.create("OpenTune folder test - Night Walk.flac", "audio/flac") { it.write(bytes) }
        try {
            assertEquals("The file is in the picked folder", authority, stored.uri.authority)
            assertTrue(app.downloadFiles.readable(stored.uri))
            assertArrayEquals(bytes, app.contentResolver.openInputStream(stored.uri)!!.use { it.readBytes() })
            assertEquals("The key is the key that a scan of the folder gives", DocumentKey.of(stored.uri.toString()), stored.key)
            assertTrue(stored.key.startsWith("${authority}|root/download-test/OpenTune folder test - Night Walk"))
        } finally { assertTrue(app.downloadFiles.delete(stored.uri)) }
        assertFalse("The file is gone", app.downloadFiles.readable(stored.uri))
    }

    @Test fun aFailedWriteLeavesNoFileInThePickedFolder() = runBlocking {
        val name = "OpenTune folder test - Failed.flac"
        try { app.downloadFiles.create(name, "audio/flac") { it.write(1); throw java.io.IOException("The write failed") }; fail("The failure was swallowed") } catch (_: java.io.IOException) { }
        val children = app.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root/download-test"), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
        }
        assertFalse("A file of the failed write is left: $children", children.any { it.startsWith("OpenTune folder test - Failed") })
    }

    @Test fun twoFilesWithTheSameNameBothStay() = runBlocking {
        val first = app.downloadFiles.create("OpenTune folder test - Twin.flac", "audio/flac") { it.write(1) }
        val second = app.downloadFiles.create("OpenTune folder test - Twin.flac", "audio/flac") { it.write(2) }
        try {
            assertTrue("The second file has its own key", first.key != second.key)
            assertTrue(app.downloadFiles.readable(first.uri) && app.downloadFiles.readable(second.uri))
        } finally { app.downloadFiles.delete(first.uri); app.downloadFiles.delete(second.uri) }
    }

    @Test fun downloadsThatExistKeepWorkingAfterTheFolderChanges() = runBlocking {
        app.settings.update { it.copy(downloadFolder = "") }
        val old = app.downloadFiles.create("OpenTune folder test - Old.flac", "audio/flac") { it.write(7) }
        app.settings.update { it.copy(downloadFolder = tree.toString()) }
        val new = app.downloadFiles.create("OpenTune folder test - New.flac", "audio/flac") { it.write(8) }
        try {
            assertTrue("The old download is still where it was", old.uri.authority == "media" || old.key.startsWith("com.android.externalstorage.documents|primary:Music/OpenTune/"))
            assertTrue("The old download can still be read", app.downloadFiles.readable(old.uri))
            assertTrue("The new download can be read", app.downloadFiles.readable(new.uri))
            assertEquals(authority, new.uri.authority)
        } finally { app.downloadFiles.delete(old.uri); app.downloadFiles.delete(new.uri) }
        assertFalse(app.downloadFiles.readable(old.uri)); assertFalse(app.downloadFiles.readable(new.uri))
    }

    @Test fun aFolderThatIsGoneStopsTheDownloadWithAClearReason() = runBlocking {
        app.settings.update { it.copy(downloadFolder = "content://${authority}/tree/missing") }
        try { app.downloadFiles.create("OpenTune folder test - Nowhere.flac", "audio/flac") { it.write(1) }; fail("A file was written to a folder that is gone") }
        catch (_: DownloadFolderUnavailableException) { }
        assertEquals("folder", io.github.yyan115.opentune.downloadErrorCode(DownloadFolderUnavailableException()))
        assertEquals("The download folder is not available. Choose another folder in Settings.", app.downloadErrorText("folder"))
        assertFalse(app.downloadFiles.usable(Uri.parse("content://${authority}/tree/missing")))
    }
}
