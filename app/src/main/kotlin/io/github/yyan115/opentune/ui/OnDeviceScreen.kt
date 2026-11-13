package io.github.yyan115.opentune.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.ReleaseType
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.LocalFolder

private enum class DeviceSort(@androidx.annotation.StringRes val label: Int) {
    Recent(R.string.sort_recent), Title(R.string.sort_title), Artist(R.string.sort_artist), Album(R.string.sort_album)
}

/**
 * Everything that is stored on the device: downloaded tracks, audio files that the listener added,
 * and the folders that OpenTune keeps scanning. Without an account the playlists made on the device are listed here too.
 */
@UnstableApi
@Composable
fun OnDeviceScreen(state: AppState, vm: MainViewModel, onAddFiles: () -> Unit, onAddFolder: () -> Unit, onLogin: () -> Unit, onNewPlaylist: () -> Unit, onOpenQueue: () -> Unit, onMenu: (Track) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(DeviceSort.Recent) }
    var text by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LocalFolder?>(null) }
    val active = queuedDownloads(state)
    val shown = remember(state.local, filter, sort, text) {
        val source = when (filter) { 1 -> state.local.filter { it.isDownload }; 2 -> state.local.filter { it.isFile }; else -> state.local }
        val matching = source.filter { text.isBlank() || it.title.contains(text.trim(), true) || it.artist.contains(text.trim(), true) || it.album.contains(text.trim(), true) }
        when (sort) {
            DeviceSort.Recent -> matching
            DeviceSort.Title -> matching.sortedBy { it.title.lowercase() }
            DeviceSort.Artist -> matching.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
            DeviceSort.Album -> matching.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
        }
    }
    val sourceLabel = stringResource(R.string.nav_device)
    val filters = listOf(R.string.device_filter_all, R.string.device_filter_downloads, R.string.device_filter_files).map { stringResource(it) }
    LazyColumn(Modifier.fillMaxSize().testTag("device-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuneOutlinedButton(onClick = onAddFiles, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp)) { Icon(OpenTuneIcons.AudioFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.device_add_files), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TuneOutlinedButton(onClick = onAddFolder, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp)) { Icon(OpenTuneIcons.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.device_add_folder), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (!state.signedIn) item { ConnectCard(onLogin) }
        // The downloads in the queue have their own page. A row opens it and shows how many downloads run, wait or failed.
        if (active.isNotEmpty()) item {
            val running = active.count { !it.state.isFinished }
            val failed = active.size - running
            val summary = listOfNotNull(
                running.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_in_progress, it, it) },
                failed.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_failed, it, it) },
            ).joinToString(" · ")
            SettingRow(OpenTuneIcons.Download, stringResource(R.string.downloads_queue_title), summary, onOpenQueue, trailing = { Icon(OpenTuneIcons.ChevronRight, null) })
        }

        if (state.deviceFolders.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.device_folders)) }
            items(state.deviceFolders, key = { "folder:${it.id}" }) { folder ->
                val scan = state.scans[folder.id]
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Folder, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            scan?.failed == true -> stringResource(R.string.device_folder_unavailable)
                            scan?.running == true -> stringResource(R.string.device_folder_scanning, scan.found)
                            else -> pluralStringResource(R.plurals.track_count, folder.trackCount, folder.trackCount)
                        }, style = MaterialTheme.typography.bodySmall, color = if (scan?.failed == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.rescanDeviceFolder(folder.id) }, enabled = scan?.running != true) { Icon(OpenTuneIcons.Refresh, stringResource(R.string.device_folder_rescan, folder.name), Modifier.size(20.dp)) }
                    IconButton(onClick = { removing = folder }) { Icon(OpenTuneIcons.RemoveCircleOutline, stringResource(R.string.device_folder_remove, folder.name), Modifier.size(20.dp)) }
                }
            }
        }

        if (!state.signedIn && (state.localPlaylists.isNotEmpty() || state.local.isNotEmpty())) {
            item { SectionTitle(stringResource(R.string.label_playlists), stringResource(R.string.playlist_new), onNewPlaylist) }
            items(state.localPlaylists, key = { "playlist:${it.id}" }) { playlist -> PlaylistListRow(deviceRelease(playlist)) { vm.open(deviceRelease(playlist)) } }
        }

        if (state.local.isEmpty()) {
            // The icon, the title and the text sit in the middle of the free space.
            if (active.isEmpty()) item { Box(Modifier.fillParentMaxHeight(.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(OpenTuneIcons.AudioFile, stringResource(R.string.device_empty_title), stringResource(if (state.signedIn) R.string.device_empty_text else R.string.device_empty_text_local))
            } }
        } else {
            item { LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters.size) { index -> FilterTab(filter == index, { filter = index }, filters[index]) }
            } }
            item(key = "tools") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.library_filter)) },
                        leadingIcon = { Icon(OpenTuneIcons.Search, null, Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.large,
                        trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(OpenTuneIcons.Close, stringResource(R.string.library_filter_clear), Modifier.size(18.dp)) } })
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(OpenTuneIcons.Sort, stringResource(R.string.library_sort_by, stringResource(sort.label))) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            DeviceSort.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = { sort = option; sortMenu = false },
                                trailingIcon = { if (sort == option) Icon(OpenTuneIcons.Check, null, Modifier.size(18.dp)) }) }
                        }
                    }
                }
            }
            item { SectionTitle(pluralStringResource(R.plurals.track_count, shown.size, shown.size), if (shown.size > 1) stringResource(R.string.action_shuffle) else null) { vm.playTracks(shown, shuffle = true, source = sourceLabel) } }
            items(shown, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(shown, shown.indexOf(track), sourceLabel) }, { onMenu(track) }) }
            if (shown.isEmpty()) item { Text(stringResource(R.string.device_filter_empty), Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    removing?.let { folder ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.device_folder_remove_title, folder.name)) },
            text = { Text(stringResource(R.string.device_folder_remove_text)) },
            confirmButton = { TextButton(onClick = { vm.removeDeviceFolder(folder.id); removing = null }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
