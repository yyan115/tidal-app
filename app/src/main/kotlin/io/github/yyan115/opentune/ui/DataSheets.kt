package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.data.Settings
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun ScrobbleDialog(settings: Settings, vm: MainViewModel, onClose: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.scrobble) }
    var server by remember { mutableStateOf(settings.scrobbleServer) }
    var token by remember { mutableStateOf(vm.scrobbleToken()) }
    var result by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.scrobble_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.scrobble_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scrobble_send), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(enabled, { enabled = it })
            }
            OutlinedTextField(server, { server = it; result = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.scrobble_server)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(token, { token = it; result = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.scrobble_token)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), supportingText = { Text(stringResource(R.string.scrobble_token_hint)) })
            TextButton(onClick = { checking = true; scope.launch { result = vm.checkScrobbling(server, token); checking = false } }, enabled = !checking && token.isNotBlank()) {
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp) else Text(stringResource(R.string.scrobble_check))
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { vm.saveScrobbling(enabled && token.isNotBlank(), server, token); onClose() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun StatsSheet(vm: MainViewModel, onClose: () -> Unit) {
    var days by remember { mutableIntStateOf(30) }
    val stats by produceState<ListeningStats?>(null, days) { value = null; value = vm.listeningStats(days) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.stats_note), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to pluralStringResource(R.plurals.stats_days, 7, 7), 30 to pluralStringResource(R.plurals.stats_days, 30, 30), 365 to stringResource(R.string.stats_year), 0 to stringResource(R.string.stats_all)).forEach { (value, label) ->
                    LinkPill(if (days == value) stringResource(R.string.stats_selected, label) else label) { days = value }
                }
            }
            val current = stats
            if (current == null) CircularProgressIndicator(Modifier.padding(16.dp))
            else if (current.plays == 0) Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyMedium)
            else {
                Text(stringResource(R.string.stats_summary, pluralStringResource(R.plurals.stats_listens, current.plays, current.plays), stringResource(R.string.stats_duration, current.hours.toInt(), current.minutes.toInt())), style = MaterialTheme.typography.titleMedium)
                SectionHeading(stringResource(R.string.stats_top_tracks))
                current.tracks.take(10).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(entry.track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(stringResource(R.string.stats_times, entry.plays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionHeading(stringResource(R.string.stats_top_artists))
                current.artists.take(10).forEach { (name, plays) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(stringResource(R.string.stats_times, plays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) { UpperLabel(text, Modifier.padding(top = 18.dp, bottom = 4.dp)) }

data class ListeningStats(val plays: Int, val hours: Long, val minutes: Long, val tracks: List<io.github.yyan115.opentune.data.LibraryStore.PlayCount>, val artists: List<Pair<String, Int>>)
