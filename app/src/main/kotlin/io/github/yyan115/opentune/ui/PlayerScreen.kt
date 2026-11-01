package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
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
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.core.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun QueueSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle(stringResource(R.string.queue_title), if (state.playback.queue.isNotEmpty()) stringResource(R.string.action_clear) else null, { confirmClear = true }) }
            if (state.playback.queue.isNotEmpty()) item(key = "save") {
                TextButton(onClick = { name = state.playback.source; save = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(OpenTuneIcons.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.queue_save))
                }
            }
            if (state.playback.queue.isEmpty()) item { EmptyState(OpenTuneIcons.PlaylistPlay, stringResource(R.string.queue_empty_title), stringResource(R.string.queue_empty_text)) }
            itemsIndexed(state.playback.queue, key = { index, track -> "$index:${track.id}" }) { index, track ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.jumpTo(index) }.padding(vertical = 12.dp)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                                color = if (index == state.playback.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.moveQueue(index, index - 1) }, enabled = index > 0) { Icon(OpenTuneIcons.ArrowUpward, stringResource(R.string.queue_move_up, track.title)) }
                        IconButton(onClick = { vm.removeQueue(index) }) { Icon(OpenTuneIcons.Close, stringResource(R.string.queue_remove, track.title)) }
                    }
                }
            }
        }
    }
    if (save) AlertDialog(onDismissRequest = { save = false }, title = { Text(stringResource(R.string.queue_save_title)) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveQueueAsPlaylist(name); save = false }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { save = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.queue_clear_title)) }, text = { Text(stringResource(R.string.queue_clear_text)) },
        confirmButton = { TextButton(onClick = { vm.clearQueue(); confirmClear = false }) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } })
}
