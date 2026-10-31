package io.github.yyan115.opentune.ui

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

data class SourceReadout(val codec: String, val bits: String?, val sampleRate: String?) {
    companion object {
        fun from(label: String): SourceReadout {
            val raw = label.substringBefore(" · ").trim()
            val codec = when { raw.startsWith("MP4A", true) -> "AAC"; raw.isBlank() -> "Not reported"; else -> raw.uppercase() }
            val bits = Regex("(\\d+)-bit").find(label)?.groupValues?.get(1)?.let { "$it bit" }
            val rate = Regex("([\\d.]+) kHz").find(label)?.groupValues?.get(1)?.let { "$it kHz" }
            return SourceReadout(codec, bits, rate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailsSheet(label: String, onClose: () -> Unit) {
    val source = SourceReadout.from(label)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Audio source", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onClose) { Text("Done") }
            }
            listOf("Codec" to source.codec, "Bit depth" to (source.bits ?: "Not reported"), "Sample rate" to (source.sampleRate ?: "Not reported")).forEachIndexed { index, (title, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f).padding(end = 12.dp)) { UpperLabel(title) }
                    Text(value, Modifier.testTag(listOf("source-codec", "source-bits", "source-rate")[index]), fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text("This is the source format. Android and connected audio devices may use a different output format.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
