package io.github.yyan115.opentune.data

import io.github.yyan115.opentune.R
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import io.github.yyan115.opentune.core.AudioFormatInfo
import io.github.yyan115.opentune.core.AudioHeaders
import io.github.yyan115.opentune.core.DocumentKey
import io.github.yyan115.opentune.core.DEVICE_ID_PREFIX
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Reads audio files from the device into the library. A file is known by its document, so it is never stored twice. */
class LocalImporter(private val context: Context, private val library: LibraryStore) {
    /**
     * Adds a file, or reads it again when it changed. [modified] and [size] come from the document provider.
     * [cover] is a picture in the folder of the file. It stands in for artwork that the file does not carry.
     * A file that the listener [direct]ly added stays in the library when the folder that holds it is removed.
     */
    suspend fun import(uri: Uri, modified: Long = 0, size: Long = 0, cover: Uri? = null, direct: Boolean = false): Track = withContext(Dispatchers.IO) {
        val key = DocumentKey.of(uri.toString())
        // A file that a download became is a track already. Every route, a folder scan and a file that the listener adds by hand, returns that track
        // and adds nothing, so the same song is never listed twice.
        library.downloadTrackIdFor(key)?.let { id -> library.get(id)?.let { return@withContext it } }
        val known = library.sourceFor(key)
        val id = known?.trackId ?: idFor(key)
        val previous = library.get(id)
        val address = when {
            direct || previous?.localUri == null -> uri
            known?.direct == true -> Uri.parse(previous.localUri)
            else -> uri
        }
        val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: context.getString(R.string.import_default_title)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val format = readFormat(uri, retriever, size, duration)
            val art = retriever.embeddedPicture?.let(::storeEmbeddedCover) ?: cover?.let(::copyCover)
            val track = Track(id, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: filename.substringBeforeLast('.'),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf(String::isNotBlank)
                    ?: context.getString(R.string.import_unknown_artist),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(), artwork = art ?: previous?.artwork?.takeIf { File(Uri.parse(it).path.orEmpty()).exists() },
                durationMs = duration, localUri = address.toString(), quality = format.label.ifBlank { null },
                addedAt = previous?.addedAt ?: System.currentTimeMillis(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0)
            library.save(track)
            library.recordSource(LocalSource(key, id, modified, size, VERSION, direct || known?.direct == true))
            track
        } finally { retriever.release() }
    }

    /** The codec, the bit depth, the sample rate and the bit rate of a file. A value that the file does not tell stays empty. */
    private fun readFormat(uri: Uri, retriever: MediaMetadataRetriever, size: Long, durationMs: Long): AudioFormatInfo {
        val head = readHead(uri, 128)
        AudioHeaders.flac(head)?.let { return it.copy(bitrateKbps = averageKbps(size, durationMs)) }
        AudioHeaders.wav(head)?.let { return it.copy(bitrateKbps = averageKbps(size, durationMs)) }
        var mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
        var rate: Int? = null
        var bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            if (extractor.trackCount > 0) {
                val track = extractor.getTrackFormat(0)
                track.getString(MediaFormat.KEY_MIME)?.let { mime = it }
                if (track.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = track.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (bitrate == null && track.containsKey(MediaFormat.KEY_BIT_RATE)) bitrate = track.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
            }
        } catch (_: Exception) {
        } finally { extractor.release() }
        if (rate == null && Build.VERSION.SDK_INT >= 31) rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
        val codec = codecName(mime)
        val bits = if (codec in LOSSLESS && Build.VERSION.SDK_INT >= 31) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it > 0 } else null
        return AudioFormatInfo(codec, bits, rate, bitrate ?: averageKbps(size, durationMs))
    }

    private fun averageKbps(size: Long, durationMs: Long): Int? = if (size > 0 && durationMs > 0) (size * 8 / durationMs).toInt() else null

    private fun readHead(uri: Uri, count: Int): ByteArray = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(count)
            var read = 0
            while (read < count) { val n = input.read(buffer, read, count - read); if (n < 0) break; read += n }
            buffer.copyOf(read)
        }
    }.getOrNull() ?: ByteArray(0)

    /** Stores a picture that sits inside a file. The name comes from the picture, so the files of one album share one copy. */
    private fun storeEmbeddedCover(bytes: ByteArray): String {
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(24)
        val file = File(context.filesDir, "artwork").apply { mkdirs() }.resolve("embedded_$name.jpg")
        if (!file.exists()) file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    /** Copies a cover picture next to the artwork of other files. Files of one folder share one copy. */
    private fun copyCover(cover: Uri): String? = runCatching {
        val directory = File(context.filesDir, "artwork").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(DocumentKey.of(cover.toString()).toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        val file = directory.resolve("cover_$name.jpg")
        if (!file.exists()) context.contentResolver.openInputStream(cover)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size in 1..MAX_COVER_BYTES) file.writeBytes(bytes)
        }
        if (file.exists()) Uri.fromFile(file).toString() else null
    }.getOrNull()

    companion object {
        /** Raise this when the importer reads more from a file, so that the next scan reads every file again. */
        const val VERSION = 1
        private const val MAX_COVER_BYTES = 8 * 1024 * 1024
        private val LOSSLESS = setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "WavPack")

        /** The library id of a file. The same document always gives the same id. */
        fun idFor(key: String): String = DEVICE_ID_PREFIX + MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

        fun codecName(mime: String): String = when (mime.lowercase().substringBefore(';')) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3", "audio/mpeg3" -> "MP3"
            "audio/mp4a-latm", "audio/aac", "audio/aacp", "audio/mp4", "audio/x-m4a", "audio/m4a" -> "AAC"
            "audio/alac" -> "ALAC"
            "audio/vorbis", "audio/ogg", "audio/x-vorbis" -> "Vorbis"
            "audio/opus" -> "Opus"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "WAV"
            "audio/aiff", "audio/x-aiff" -> "AIFF"
            "audio/x-ms-wma", "audio/wma" -> "WMA"
            "audio/ape", "audio/x-ape" -> "APE"
            "audio/x-wavpack" -> "WavPack"
            "" -> ""
            else -> mime.substringAfter('/').substringAfter("x-").uppercase()
        }
    }
}
