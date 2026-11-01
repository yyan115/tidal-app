package io.github.yyan115.opentune.ui

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
    AlertDialog(onDismissRequest = onClose, title = { Text("Scrobbling") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sends the artist, title and album of each track you finish to a ListenBrainz server. Nothing is sent while this is off.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Send listens", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(enabled, { enabled = it })
            }
            OutlinedTextField(server, { server = it; result = null }, Modifier.fillMaxWidth(), label = { Text("Server") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(token, { token = it; result = null }, Modifier.fillMaxWidth(), label = { Text("User token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), supportingText = { Text("Find it in your ListenBrainz settings.") })
            TextButton(onClick = { checking = true; scope.launch { result = vm.checkScrobbling(server, token); checking = false } }, enabled = !checking && token.isNotBlank()) {
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp) else Text("Check token")
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { vm.saveScrobbling(enabled && token.isNotBlank(), server, token); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun StatsSheet(vm: MainViewModel, onClose: () -> Unit) {
    var days by remember { mutableIntStateOf(30) }
    val stats by produceState<ListeningStats?>(null, days) { value = null; value = vm.listeningStats(days) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Listening statistics", style = MaterialTheme.typography.headlineSmall)
            Text("Kept on this device. OpenTune never uploads them.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 days", 30 to "30 days", 365 to "Year", 0 to "All").forEach { (value, label) ->
                    LinkPill(if (days == value) "$label ·" else label) { days = value }
                }
            }
            val current = stats
            if (current == null) CircularProgressIndicator(Modifier.padding(16.dp))
            else if (current.plays == 0) Text("No listens in this period yet. A listen counts after 30 seconds or half of the track.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("${current.plays} ${if (current.plays == 1) "listen" else "listens"} · ${current.hours} h ${current.minutes} min", style = MaterialTheme.typography.titleMedium)
                SectionHeading("Top tracks")
                current.tracks.take(10).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(entry.track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text("${entry.plays}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionHeading("Top artists")
                current.artists.take(10).forEach { (name, plays) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text("$plays×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) { UpperLabel(text, Modifier.padding(top = 18.dp, bottom = 4.dp)) }

data class ListeningStats(val plays: Int, val hours: Long, val minutes: Long, val tracks: List<io.github.yyan115.opentune.data.LibraryStore.PlayCount>, val artists: List<Pair<String, Int>>)
