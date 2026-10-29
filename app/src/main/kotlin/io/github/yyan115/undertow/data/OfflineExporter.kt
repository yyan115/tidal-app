package io.github.yyan115.undertow.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import io.github.yyan115.undertow.UndertowApp
import io.github.yyan115.undertow.core.ProtectedStreamException
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.core.FlacMp4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream

data class ExportSpec(val fileName: String, val mimeType: String)

/** Copies clear audio without transcoding, unpacking FLAC samples into a native FLAC file. */
@UnstableApi
class OfflineExporter(private val app: UndertowApp) {
    fun describe(track: Track): ExportSpec {
        val local = track.localUri ?: throw IOException("Download this track before exporting it.")
        val mime = if (local.startsWith("undertow-offline:")) {
            if (app.sources.readOffline(track.id).codec.contains("flac", true)) "audio/flac" else "audio/mp4"
        } else
            app.contentResolver.getType(Uri.parse(local)) ?: when {
                local.endsWith(".flac", true) -> "audio/flac"
                local.endsWith(".mp3", true) -> "audio/mpeg"
                local.endsWith(".m4a", true) -> "audio/mp4"
                local.endsWith(".ogg", true) -> "audio/ogg"
                local.endsWith(".wav", true) -> "audio/wav"
                else -> "application/octet-stream"
            }
        val extension = when (mime) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/mpeg" -> "mp3"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "audio"
        }
        val name = "${track.artist} - ${track.title}".replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim().take(160)
        return ExportSpec("${name.ifBlank { "Audio" }}.$extension", mime)
    }

    suspend fun write(track: Track, destination: Uri, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val saved = app.library.get(track.id)?.takeIf(Track::isLocal) ?: track
        val local = saved.localUri ?: throw IOException("Download this track before exporting it.")
        if (Uri.parse(local) == destination) throw IOException("Choose a different destination from the original file.")
        val output = app.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("The export destination could not be opened.")
        try {
            output.use { stream ->
                if (local.startsWith("undertow-offline:")) writeDash(saved, stream, onProgress)
                else app.contentResolver.openInputStream(Uri.parse(local))?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                    }
                } ?: throw IOException("The original audio file is no longer readable.")
                stream.flush()
            }
            onProgress(100)
        } catch (error: Exception) {
            runCatching {
                if (android.provider.DocumentsContract.isDocumentUri(app, destination)) android.provider.DocumentsContract.deleteDocument(app.contentResolver, destination)
                else app.contentResolver.delete(destination, null, null)
            }
            throw error
        }
    }

    private suspend fun writeDash(track: Track, output: OutputStream, onProgress: (Int) -> Unit) {
        val stream = app.sources.readOffline(track.id)
        val xml = stream.dashManifest ?: throw IOException("This download has no DASH manifest.")
        val manifest = DashManifestParser().parse(Uri.parse(app.sources.manifestUri(stream)), ByteArrayInputStream(xml.toByteArray()))
        if (manifest.dynamic || manifest.periodCount != 1) throw IOException("This download cannot be exported as one audio file.")
        val representation = manifest.getPeriod(0).adaptationSets.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { it.representations }.singleOrNull() ?: throw IOException("This download contains multiple audio variants.")
        if (representation.format.drmInitData != null) throw ProtectedStreamException()
        val index = representation.index ?: throw IOException("The download has no segment index.")
        val count = index.getSegmentCount(manifest.getPeriodDurationUs(0))
        if (count !in 1..100_000) throw IOException("The download has an unsupported segment index.")
        val base = representation.baseUrls.first().url
        val initialization = representation.initializationUri ?: throw IOException("The download has no audio initialization segment.")
        val ranges = sequence {
            yield(initialization)
            for (number in index.firstSegmentNum until index.firstSegmentNum + count) yield(index.getSegmentUrl(number))
        }
        val flac = stream.codec.contains("flac", true)
        ranges.forEachIndexed { position, range ->
            currentCoroutineContext().ensureActive()
            val spec = DashUtil.buildDataSpec(representation, base, range, 0, emptyMap())
            DataSourceInputStream(app.sources.dataSource(offlineOnly = true).createDataSource(), spec).use { input ->
                if (flac && position == 0) {
                    val initialization = initializationBytes(input)
                    output.write(FlacMp4.flacHeader(initialization, linkedMapOf("TITLE" to track.title, "ARTIST" to track.artist, "ALBUM" to track.album)))
                } else if (flac) FlacMp4.copyFrames(input, output)
                else input.copyTo(output, 64 * 1024)
            }
            onProgress(((position + 1) * 100L / (count + 1)).toInt())
        }
    }

    private fun initializationBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > 2 * 1024 * 1024) throw IOException("The audio initialization is too large.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
