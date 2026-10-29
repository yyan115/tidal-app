package io.github.yyan115.undertow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.core.formatDuration

@Composable
fun MiniPlayer(state: PlaybackState, onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    val track = state.track ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("mini-player")) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artwork, null, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(if (state.error != null) "Playback stopped" else track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.buffering) CircularProgressIndicator(Modifier.padding(14.dp).size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = onPlay) { Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (state.playing) "Pause" else "Play") }
                IconButton(onClick = onNext, enabled = state.queue.size > 1) { Icon(Icons.Outlined.SkipNext, "Next track") }
            }
            LinearProgressIndicator(progress = { if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@UnstableApi
@Composable
fun NowPlayingScreen(state: AppState, vm: MainViewModel, onClose: () -> Unit, onQueue: () -> Unit, onMenu: (Track) -> Unit) {
    val player = state.playback
    val track = player.track ?: return
    var lyrics by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val landscape = maxWidth > 600.dp && maxWidth > maxHeight
                val artSize = if (landscape) minOf(maxHeight - 96.dp, 320.dp) else minOf(maxWidth - 64.dp, maxHeight * .37f)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, "Close player") }
                        Text("Now playing", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { onMenu(track) }) { Icon(Icons.Outlined.MoreHoriz, "Track options") }
                    }
                    if (landscape) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Artwork(track.artwork, "Artwork for ${track.album.ifBlank { track.title }}", Modifier.size(artSize))
                            Column(Modifier.weight(1f)) { PlayerControls(state, vm, onQueue) { lyrics = true; vm.loadLyrics() } }
                        }
                    } else {
                        Artwork(track.artwork, "Artwork for ${track.album.ifBlank { track.title }}", Modifier.size(artSize).align(Alignment.CenterHorizontally).padding(4.dp))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp)) {
                            PlayerControls(state, vm, onQueue) { lyrics = true; vm.loadLyrics() }
                        }
                    }
                }
            }
        }
    }
    if (lyrics) AlertDialog(onDismissRequest = { lyrics = false }, title = { Text("Lyrics") }, text = {
        if (state.lyricsLoading) CircularProgressIndicator()
        else Text(state.lyrics.orEmpty(), Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge)
    }, confirmButton = { TextButton(onClick = { lyrics = false }) { Text("Close") } })
}

@UnstableApi
@Composable
private fun PlayerControls(state: AppState, vm: MainViewModel, onQueue: () -> Unit, onLyrics: () -> Unit) {
    val player = state.playback
    val track = player.track ?: return
    val saved = state.favorites.any { it.id == track.id }
    var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.favorite(track) }) { Icon(if (saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            if (saved) "Remove from saved tracks" else "Save track", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (player.error != null) {
        Text(player.error, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    } else if (player.quality.isNotBlank()) {
        Text(player.quality, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    } else Spacer(Modifier.height(14.dp))
    Slider(value = seeking ?: player.position.toFloat().coerceIn(0f, player.duration.coerceAtLeast(1).toFloat()),
        onValueChange = { seeking = it }, valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), enabled = player.duration > 0,
        onValueChangeFinished = { seeking?.let { vm.seek(it.toLong()) }; seeking = null }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(seeking?.toLong() ?: player.position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(player.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(player.shuffle, onCheckedChange = { vm.toggleShuffle() }) { Icon(Icons.Outlined.Shuffle, "Shuffle") }
        IconButton(onClick = vm::previous, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.SkipPrevious, "Previous track", Modifier.size(32.dp)) }
        FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(68.dp)) {
            if (player.buffering) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (player.playing) "Pause" else "Play", Modifier.size(36.dp))
        }
        IconButton(onClick = vm::next, enabled = player.index < player.queue.lastIndex || player.repeat != Player.REPEAT_MODE_OFF, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.SkipNext, "Next track", Modifier.size(32.dp))
        }
        IconToggleButton(player.repeat != Player.REPEAT_MODE_OFF, onCheckedChange = { vm.toggleRepeat() }) {
            Icon(if (player.repeat == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                when (player.repeat) { Player.REPEAT_MODE_ONE -> "Repeat one track"; Player.REPEAT_MODE_ALL -> "Repeat queue"; else -> "Repeat off" })
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onLyrics) { Icon(Icons.Outlined.Lyrics, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lyrics") }
        TextButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Queue · ${player.queue.size}") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun QueueSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("Queue", if (state.playback.queue.isNotEmpty()) "Clear" else null, { confirmClear = true }) }
            if (state.playback.queue.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.PlaylistPlay, "The queue is empty", "Play a track or add one from its menu.") }
            itemsIndexed(state.playback.queue, key = { index, track -> "$index:${track.id}" }) { index, track ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.jumpTo(index) }.padding(vertical = 12.dp)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                                color = if (index == state.playback.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.moveQueue(index, index - 1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Move ${track.title} up") }
                        IconButton(onClick = { vm.removeQueue(index) }) { Icon(Icons.Outlined.Close, "Remove ${track.title} from queue") }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear the queue?") }, text = { Text("Playback will stop.") },
        confirmButton = { TextButton(onClick = { vm.clearQueue(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
