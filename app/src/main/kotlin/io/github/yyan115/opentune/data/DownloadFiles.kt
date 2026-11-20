package io.github.yyan115.opentune.data

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.OpenTuneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Keeps a finished download as one audio file in the Music/OpenTune folder, where every file manager and music player sees it.
 * Android lets an app add its own audio files to the music folder without a permission.
 */
@UnstableApi
class DownloadFiles(private val app: OpenTuneApp) {
    /** A file that was written. [key] is the key of the same file as a folder scan would find it, so that a scan can leave it out. */
    data class Stored(val uri: Uri, val key: String)

    /** Creates the file, lets [write] fill it and returns where it lies. A failure removes the part that was written. */
    suspend fun create(name: String, mime: String, write: suspend (OutputStream) -> Unit): Stored = withContext(Dispatchers.IO) { createInMediaStore(name, mime, write) }

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
