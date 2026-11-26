package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.core.DocumentKey
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.core.formatDuration
import io.github.yyan115.opentune.playback.OutputFormat
import io.github.yyan115.opentune.playback.OutputPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** The parts of a source format such as "FLAC · 24-bit · 96.0 kHz · 2304 kbps". Numbers have no units, so they can be formatted for any language. */
data class SourceReadout(val codec: String, val bits: String?, val sampleRate: String?, val bitrate: String? = null) {
    companion object {
        fun from(label: String): SourceReadout {
            val raw = label.substringBefore(" · ").trim()
            val codec = when { raw.startsWith("MP4A", true) -> "AAC"; raw.isBlank() -> ""; else -> raw.uppercase() }
            val bits = Regex("(\\d+)-bit").find(label)?.groupValues?.get(1)
            val rate = Regex("([\\d.]+) kHz").find(label)?.groupValues?.get(1)
            val bitrate = Regex("(\\d+) kbps").find(label)?.groupValues?.get(1)
            return SourceReadout(codec, bits, rate, bitrate)
        }
    }
}

@Composable fun SourceReadout.bitsText(): String? = bits?.let { stringResource(R.string.unit_bits, it) }
@Composable fun SourceReadout.rateText(): String? = sampleRate?.let { stringResource(R.string.unit_khz, it) }
@Composable fun SourceReadout.bitrateText(): String? = bitrate?.let { stringResource(R.string.unit_kbps, it) }

/** The place of a file that the media store keeps, such as a download in the default folder. Only the main storage holds those. */
private fun mediaStoreLocation(context: android.content.Context, uri: Uri): io.github.yyan115.opentune.core.DocumentLocation? = runCatching {
    if (uri.authority != android.provider.MediaStore.AUTHORITY) return@runCatching null
    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) DocumentKey.fromPath("primary", it.getString(0).orEmpty(), it.getString(1).orEmpty()) else null
    }
}.getOrNull()

/** What can be told about the file of a track: where it lies, how large it is and which folder holds it. */
private class FileFacts(val location: String?, val fileName: String?, val folder: String?, val size: Long?)

@Composable
private fun volumeName(volume: String): String = if (volume == "primary") stringResource(R.string.volume_internal) else stringResource(R.string.volume_card)

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun SourceDetailsSheet(label: String, track: Track?, vm: MainViewModel?, onClose: () -> Unit) {
    val source = SourceReadout.from(label)
    val appState = vm?.state?.collectAsStateWithLifecycle()?.value
    // The setting applies from the next stream. The song that plays now says 16-bit only when its output is open at 16 bits.
    val playingNow = track != null && appState?.playingTrack?.id == track.id
    val limited = appState?.settings?.limitTo16Bit == true && (!playingNow || OutputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT)
    val notReported = stringResource(R.string.source_not_reported)
    val context = LocalContext.current
    // A file that was added and a download both lie in a folder of the device. A download of an earlier version is kept in the private storage of the app and has no place to show.
    val stored = track?.takeIf { it.onDevice && it.localUri?.startsWith("opentune-offline:") != true }
    val facts = produceState<FileFacts?>(null, track?.id) {
        val uri = stored?.localUri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val parsed = DocumentKey.location(uri) ?: mediaStoreLocation(context, Uri.parse(uri))
            val size = runCatching { context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } }.getOrNull()
            FileFacts(parsed?.let { it.folders.joinToString(" › ") }, parsed?.fileName, if (track.isFile) vm?.app?.library?.folderOf(track.id)?.name else null, size)
        }
    }.value
    val volume = stored?.localUri?.let { DocumentKey.location(it)?.volume ?: if (facts != null) "primary" else null }
    AppSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            // The sheet closes by a drag down, by Back and by a tap outside, as the credits do, so it has no button for it.
            Text(stringResource(R.string.source_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium)
            val rows = buildList {
                if (track != null) add(Triple("source-origin", stringResource(R.string.source_origin), stringResource(when { track.isFile -> R.string.source_origin_file; track.onDevice -> R.string.source_origin_download; else -> R.string.source_origin_stream })))
                add(Triple("source-codec", stringResource(R.string.source_codec), source.codec.ifBlank { notReported }))
                if (source.bits != null || source.bitrate == null || track?.isFile != true) add(Triple("source-bits", stringResource(R.string.source_bit_depth), source.bitsText() ?: notReported))
                add(Triple("source-rate", stringResource(R.string.source_sample_rate), source.rateText() ?: notReported))
                // A phone that failed the check of the 24-bit output plays such a song at 16-bit, and the sheet says why.
                source.bits?.toIntOrNull()?.takeIf { it > 16 && !OutputPolicy.state.floatAllowed }?.let { add(Triple("source-output", stringResource(R.string.source_output), stringResource(R.string.source_output_blocked, it))) }
                // A listener who chose 16-bit output hears a song of more than 16 bits at 16 bits, and the sheet says so.
                source.bits?.toIntOrNull()?.takeIf { it > 16 && limited && OutputPolicy.state.floatAllowed }?.let { add(Triple("source-output", stringResource(R.string.source_output), stringResource(R.string.source_output_limited))) }
                source.bitrateText()?.let { add(Triple("source-bitrate", stringResource(R.string.source_bitrate), it)) }
                if (stored != null) {
                    facts?.fileName?.let { add(Triple("source-file", stringResource(R.string.source_file), it)) }
                    val where = listOfNotNull(volume?.let { volumeName(it) }, facts?.location?.takeIf(String::isNotBlank)).joinToString(" › ")
                    if (where.isNotBlank()) add(Triple("source-location", stringResource(R.string.source_location), where))
                    if (stored.isFile) add(Triple("source-folder", stringResource(R.string.source_folder), facts?.folder ?: stringResource(R.string.source_added_by_hand)))
                    facts?.size?.let { add(Triple("source-size", stringResource(R.string.source_size), android.text.format.Formatter.formatFileSize(context, it))) }
                }
                if (track != null && track.onDevice && track.durationMs > 0) add(Triple("source-duration", stringResource(R.string.source_duration), formatDuration(track.durationMs)))
                if (track != null && track.onDevice) add(Triple("source-added", stringResource(R.string.source_added), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(track.addedAt))))
            }
            rows.forEach { (tag, title, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    UpperLabel(title, Modifier.padding(end = 16.dp))
                    Text(value, Modifier.weight(1f).testTag(tag), fontFamily = AppFonts.mono, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
