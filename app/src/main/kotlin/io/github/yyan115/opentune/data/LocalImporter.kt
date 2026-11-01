package io.github.yyan115.opentune.data

import io.github.yyan115.opentune.R
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class LocalImporter(private val context: Context, private val library: LibraryStore) {
    suspend fun import(uri: Uri): Track = withContext(Dispatchers.IO) {
        val id = "local:" + MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: context.getString(R.string.import_default_title)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture?.let { bytes ->
                val file = File(context.filesDir, "artwork").apply { mkdirs() }.resolve(id.replace(':', '_') + ".jpg")
                file.outputStream().use { it.write(bytes) }; Uri.fromFile(file).toString()
            }
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            Track(id, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: filename.substringBeforeLast('.'),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: context.getString(R.string.import_unknown_artist),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(), artwork = art,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                localUri = uri.toString(), quality = if (mime.contains("flac")) "FLAC" else mime.substringAfterLast('/').uppercase())
                .also(library::save)
        } finally { retriever.release() }
    }
}
