package io.github.yyan115.undertow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.core.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun QueueSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("Queue", if (state.playback.queue.isNotEmpty()) "Clear" else null, { confirmClear = true }) }
            if (state.playback.queue.isEmpty()) item { EmptyState(OpenTuneIcons.PlaylistPlay, "The queue is empty", "Play a track or add one from its menu.") }
            itemsIndexed(state.playback.queue, key = { index, track -> "$index:${track.id}" }) { index, track ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.jumpTo(index) }.padding(vertical = 12.dp)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                                color = if (index == state.playback.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.moveQueue(index, index - 1) }, enabled = index > 0) { Icon(OpenTuneIcons.ArrowUpward, "Move ${track.title} up") }
                        IconButton(onClick = { vm.removeQueue(index) }) { Icon(OpenTuneIcons.Close, "Remove ${track.title} from queue") }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear the queue?") }, text = { Text("Playback will stop.") },
        confirmButton = { TextButton(onClick = { vm.clearQueue(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
