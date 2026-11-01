package io.github.yyan115.opentune.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.playback.AudioOutput
import io.github.yyan115.opentune.playback.EqualizerInfo
import kotlinx.coroutines.delay

/** Minutes left on the sleep timer, refreshed every 20 seconds. Null when no timed sleep timer runs. */
@Composable
fun rememberSleepMinutes(endsAt: Long): State<Int?> = produceState<Int?>(null, endsAt) {
    while (endsAt > 0) {
        value = ((endsAt - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(0)
        delay(20_000)
    }
    value = null
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun SleepTimerSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    val remaining by rememberSleepMinutes(state.sleepEndsAt)
    val active = state.sleepEndsAt > 0 || state.sleepAtTrackEnd
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Sleep timer", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
            Text(when {
                state.sleepAtTrackEnd -> "Playback stops at the end of this track."
                state.sleepEndsAt > 0 -> "Playback stops in ${remaining ?: 0} min."
                else -> "Pause playback automatically."
            }, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                SettingRow(OpenTuneIcons.Timer, "$minutes minutes", onClick = { vm.setSleepTimer(minutes); onClose() })
            }
            SettingRow(OpenTuneIcons.SkipNext, "End of this track", onClick = { vm.setSleepTimer(0, endOfTrack = true); onClose() })
            if (active) SettingRow(OpenTuneIcons.Close, "Turn off", onClick = { vm.setSleepTimer(0); onClose() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(settings: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    val info = remember { EqualizerInfo.read() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Equalizer", style = MaterialTheme.typography.headlineSmall)
            if (info == null) {
                Text("This device has no equalizer that OpenTune can use.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Use equalizer", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(settings.equalizer, { onChange(settings.copy(equalizer = it)) })
            }
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkPill("Flat") { onChange(settings.copy(equalizerLevels = List(info.bands) { 0 })) }
                info.presets.forEach { preset -> LinkPill(preset.name) { onChange(settings.copy(equalizerLevels = preset.levels)) } }
            }
            val levels = List(info.bands) { settings.equalizerLevels.getOrNull(it) ?: 0 }
            levels.forEachIndexed { band, level ->
                val hz = info.centerHz[band]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(level.toFloat(), { value -> onChange(settings.copy(equalizerLevels = levels.toMutableList().also { it[band] = value.toInt() })) },
                        Modifier.weight(1f), enabled = settings.equalizer, valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat())
                    Text("%+.1f dB".format(level / 100f), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AudioOutputDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val devices = remember { AudioOutput.devices(context) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Audio output") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (devices.isEmpty()) Text("Android reports no audio outputs.")
            devices.forEach { device ->
                Column {
                    Text(device.name + if (device.active) " · in use" else "", style = MaterialTheme.typography.titleSmall)
                    Text(device.kind + if (device.sampleRates.isNotEmpty()) " · " + device.sampleRates.joinToString(", ") { "${it / 1000.0} kHz" } else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("The sample rates are what the output accepts. The format of the track is shown in the player.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
fun NormalizationDialog(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val options = listOf(
        Triple("off", "Off", "Play every track at its own level."),
        Triple("track", "Track", "Turn loud tracks down to even out the volume between tracks."),
        Triple("album", "Album", "Keep the level differences within an album."),
    )
    AlertDialog(onDismissRequest = onClose, title = { Text("Volume normalization") }, text = {
        Column {
            options.forEach { (value, label, description) ->
                Row(Modifier.fillMaxWidth().clickable { onPick(value); onClose() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(current == value, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("Uses the ReplayGain values from TIDAL. Quiet tracks are not amplified, so turn the volume up instead.", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
