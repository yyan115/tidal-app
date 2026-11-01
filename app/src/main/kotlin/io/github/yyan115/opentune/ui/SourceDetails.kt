package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The parts of a source format such as "FLAC · 24-bit · 96.0 kHz". Numbers have no units, so they can be formatted for any language. */
data class SourceReadout(val codec: String, val bits: String?, val sampleRate: String?) {
    companion object {
        fun from(label: String): SourceReadout {
            val raw = label.substringBefore(" · ").trim()
            val codec = when { raw.startsWith("MP4A", true) -> "AAC"; raw.isBlank() -> ""; else -> raw.uppercase() }
            val bits = Regex("(\\d+)-bit").find(label)?.groupValues?.get(1)
            val rate = Regex("([\\d.]+) kHz").find(label)?.groupValues?.get(1)
            return SourceReadout(codec, bits, rate)
        }
    }
}

@Composable fun SourceReadout.bitsText(): String? = bits?.let { stringResource(R.string.unit_bits, it) }
@Composable fun SourceReadout.rateText(): String? = sampleRate?.let { stringResource(R.string.unit_khz, it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailsSheet(label: String, onClose: () -> Unit) {
    val source = SourceReadout.from(label)
    val notReported = stringResource(R.string.source_not_reported)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.source_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
            }
            listOf(stringResource(R.string.source_codec) to source.codec.ifBlank { notReported }, stringResource(R.string.source_bit_depth) to (source.bitsText() ?: notReported),
                stringResource(R.string.source_sample_rate) to (source.rateText() ?: notReported)).forEachIndexed { index, (title, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f).padding(end = 12.dp)) { UpperLabel(title) }
                    Text(value, Modifier.testTag(listOf("source-codec", "source-bits", "source-rate")[index]), fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(stringResource(R.string.source_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
