package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.data.Settings
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
            Text(stringResource(R.string.sleep_title), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
            Text(when {
                state.sleepAtTrackEnd -> stringResource(R.string.sleep_status_track_end)
                state.sleepEndsAt > 0 -> stringResource(R.string.sleep_status_timed, remaining ?: 0)
                else -> stringResource(R.string.sleep_status_idle)
            }, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                SettingRow(OpenTuneIcons.Timer, pluralStringResource(R.plurals.sleep_minutes, minutes, minutes), onClick = { vm.setSleepTimer(minutes); onClose() })
            }
            SettingRow(OpenTuneIcons.SkipNext, stringResource(R.string.sleep_track_end), onClick = { vm.setSleepTimer(0, endOfTrack = true); onClose() })
            if (active) SettingRow(OpenTuneIcons.Close, stringResource(R.string.sleep_turn_off), onClick = { vm.setSleepTimer(0); onClose() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(settings: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    val info = remember { EqualizerInfo.read() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.headlineSmall)
            if (info == null) {
                Text(stringResource(R.string.eq_unavailable), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.eq_use), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(settings.equalizer, { onChange(settings.copy(equalizer = it)) })
            }
            val levels = List(info.bands) { settings.equalizerLevels.getOrNull(it) ?: 0 }
            val presets = listOf(stringResource(R.string.eq_flat) to List(info.bands) { 0 }) + info.presets.map { it.name to it.levels }
            val customName = stringResource(R.string.eq_custom)
            val chosen = if (settings.equalizerUsesCustom) customName else presets.firstOrNull { it.second == levels }?.first ?: customName
            var presetMenu by remember { mutableStateOf(false) }
            // The field is as inactive as the bands while the equalizer is off, and the list shows four and a half entries at a time, so that it opens below the field and the half entry shows that the list scrolls.
            ExposedDropdownMenuBox(presetMenu && settings.equalizer, { presetMenu = it && settings.equalizer }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedTextField(chosen, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), enabled = settings.equalizer,
                    readOnly = true, singleLine = true, label = { Text(stringResource(R.string.eq_preset)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetMenu && settings.equalizer) })
                ExposedDropdownMenu(presetMenu && settings.equalizer, { presetMenu = false }, Modifier.heightIn(max = 216.dp)) {
                    presets.forEach { (name, preset) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(settings.copy(equalizerLevels = preset, equalizerUsesCustom = false)); presetMenu = false }) }
                    DropdownMenuItem(text = { Text(customName) }, onClick = {
                        val custom = List(info.bands) { settings.equalizerCustom.getOrNull(it) ?: levels[it] }
                        onChange(settings.copy(equalizerLevels = custom, equalizerCustom = custom, equalizerUsesCustom = true)); presetMenu = false
                    })
                }
            }
            levels.forEachIndexed { band, level ->
                val hz = info.centerHz[band]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) stringResource(R.string.unit_khz, (hz / 1000).toString()) else stringResource(R.string.unit_hz, hz), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(level.toFloat(), { value -> val moved = levels.toMutableList().also { it[band] = value.toInt() }; onChange(settings.copy(equalizerLevels = moved, equalizerCustom = moved, equalizerUsesCustom = true)) },
                        Modifier.weight(1f), enabled = settings.equalizer, valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat())
                    Text(stringResource(R.string.unit_db, level / 100f), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun NormalizationDialog(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val options = listOf(
        Triple("off", stringResource(R.string.normalization_off), stringResource(R.string.normalization_off_hint)),
        Triple("track", stringResource(R.string.normalization_track), stringResource(R.string.normalization_track_hint)),
        Triple("album", stringResource(R.string.normalization_album), stringResource(R.string.normalization_album_hint)),
    )
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.normalization_title)) }, text = {
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
            Text(stringResource(R.string.normalization_note), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } })
}
