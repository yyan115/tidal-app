package io.github.yyan115.opentune.data

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.DocumentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** The folder that the listener picked for downloads cannot be written to. It may have been removed, or the access to it may have been withdrawn. */
class DownloadFolderUnavailableException : IOException("The download folder is not available.")

/**
 * Keeps a finished download as one audio file in the Music/OpenTune folder, where every file manager and music player sees it.
 * Android lets an app add its own audio files to the music folder without a permission.
 */
@UnstableApi
class DownloadFiles(private val app: OpenTuneApp) {
    /** A file that was written. [key] is the key of the same file as a folder scan would find it, so that a scan can leave it out. */
    data class Stored(val uri: Uri, val key: String)

    /** Creates the file, lets [write] fill it and returns where it lies. A failure removes the part that was written. */
    suspend fun create(name: String, mime: String, write: suspend (OutputStream) -> Unit): Stored = withContext(Dispatchers.IO) {
        val folder = app.settings.state.value.downloadFolder
        if (DownloadFolder.isDefault(folder)) createInMediaStore(name, mime, write) else createInPickedFolder(Uri.parse(folder), name, mime, write)
    }

    /** Whether files can be created in the folder that the listener picked. */
    fun usable(tree: Uri): Boolean = try {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        app.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            it.moveToFirst() && it.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        } ?: false
    } catch (_: Exception) { false }

    private suspend fun createInPickedFolder(tree: Uri, name: String, mime: String, write: suspend (OutputStream) -> Unit): Stored {
        val resolver = app.contentResolver
        val parent = try { DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) } catch (_: IllegalArgumentException) { throw DownloadFolderUnavailableException() }
        // The provider adds a number to a name that is taken, and its own key is read from the address that it returns.
        val uri = try { DocumentsContract.createDocument(resolver, parent, mime, name) }
        catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null } catch (_: java.io.FileNotFoundException) { null } catch (_: UnsupportedOperationException) { null }
        if (uri == null) throw DownloadFolderUnavailableException()
        try {
            (resolver.openOutputStream(uri, "w") ?: throw IOException("The audio file could not be opened.")).use { write(it) }
            return Stored(uri, DocumentKey.of(uri.toString()))
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
    }

    private suspend fun createInMediaStore(name: String, mime: String, write: suspend (OutputStream) -> Unit): Stored {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name); put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER); put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: throw IOException("The audio file could not be created.")
        try {
            (resolver.openOutputStream(uri, "w") ?: throw IOException("The audio file could not be opened.")).use { write(it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            // The system adds a number to a name that is taken, so the name that was kept is read back.
            val kept = resolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
            return Stored(uri, keyOf(kept))
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    /** Removes the file. Returns false when Android does not let the app remove it, as with a file of an earlier install. */
    fun delete(uri: Uri): Boolean = withDownloadFile(uri, missing = true) {
        if (uri.scheme == "file") File(uri.path.orEmpty()).let { !it.exists() || (ours(it) && it.delete()) }
        else if (DocumentsContract.isDocumentUri(app, uri)) DocumentsContract.deleteDocument(app.contentResolver, uri)
        else app.contentResolver.delete(uri, null, null) >= 0
    }

    /** Only the download folder and the private folder of earlier versions hold files that the app may remove. */
    private fun ours(file: File): Boolean {
        val path = file.canonicalPath
        return listOf(File(app.filesDir, "downloads"), File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "OpenTune")).any { path.startsWith(it.canonicalPath + "/") }
    }

    /** Whether the file can be read now. A file that the listener removed, or that a new install cannot open, cannot. */
    fun readable(uri: Uri): Boolean = withDownloadFile(uri, missing = false) {
        if (uri.scheme == "file") File(uri.path.orEmpty()).canRead()
        else app.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }

    private fun withDownloadFile(uri: Uri, missing: Boolean, action: () -> Boolean): Boolean = try { action() } catch (_: SecurityException) { false } catch (_: java.io.FileNotFoundException) { missing } catch (_: IllegalArgumentException) { missing }

    companion object {
        const val FOLDER = "Music/OpenTune"

        /** The key that a folder scan gives to a file of the download folder on the main storage. */
        fun keyOf(fileName: String) = "com.android.externalstorage.documents|primary:$FOLDER/$fileName"
    }
}
