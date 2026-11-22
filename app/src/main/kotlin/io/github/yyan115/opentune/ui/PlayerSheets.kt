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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
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
    AppSheet(onDismissRequest = onClose) {
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

/** The number of entries that the list of presets shows at a time. The last one shows in half, which tells that the list scrolls. */
private const val VISIBLE_ENTRIES = 4.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(settings: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    val info = remember { EqualizerInfo.read() }
    AppSheet(onDismissRequest = onClose) {
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
            // The height of an entry follows the text size. A hidden entry that takes no room in the sheet is measured before the menu opens,
            // so that the menu has its height from its first frame and does not open above the field and then jump below it.
            var entryHeight by remember { mutableIntStateOf(0) }
            Box(Modifier.clearAndSetSemantics { }.layout { measurable, constraints ->
                entryHeight = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)).height
                layout(0, 0) { }
            }) { DropdownMenuItem(text = { Text(" ") }, onClick = { }) }
            val density = LocalDensity.current
            // The field is as inactive as the bands while the equalizer is off, and the list shows four and a half entries at a time, so that it opens below the field and the half entry shows that the list scrolls.
            ExposedDropdownMenuBox(presetMenu && settings.equalizer, { presetMenu = it && settings.equalizer }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedTextField(chosen, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), enabled = settings.equalizer,
                    readOnly = true, singleLine = true, label = { Text(stringResource(R.string.eq_preset)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetMenu && settings.equalizer) })
                ExposedDropdownMenu(presetMenu && settings.equalizer, { presetMenu = false }) {
                    val scroll = rememberScrollState()
                    // The menu scrolls inside its own height, so the list gets a height of its own only when the height of an entry is known.
                    val limit = if (entryHeight == 0) Modifier else Modifier.heightIn(max = with(density) { (entryHeight * VISIBLE_ENTRIES).toDp() }).verticalScroll(scroll)
                    Column(limit) {
                        presets.forEachIndexed { index, (name, preset) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { onChange(settings.copy(equalizerLevels = preset, equalizerUsesCustom = false)); presetMenu = false })
                        }
                        DropdownMenuItem(text = { Text(customName) }, onClick = {
                            val custom = List(info.bands) { settings.equalizerCustom.getOrNull(it) ?: levels[it] }
                            onChange(settings.copy(equalizerLevels = custom, equalizerCustom = custom, equalizerUsesCustom = true)); presetMenu = false
                        })
                    }
                }
            }
            val label = MaterialTheme.typography.labelMedium
            val frequencies = info.centerHz.map { hz -> if (hz >= 1000) stringResource(R.string.unit_khz, (hz / 1000).toString()) else stringResource(R.string.unit_hz, hz) }
            val gains = listOf(info.minLevel, info.maxLevel, 0).map { stringResource(R.string.unit_db, it / 100f) }
            // The labels have the width of the widest one, whatever the size of the text, so that the sliders line up.
            val measurer = rememberTextMeasurer()
            val frequencyWidth = with(density) { frequencies.maxOf { measurer.measure(it, label).size.width }.toDp() }
            val gainWidth = with(density) { gains.maxOf { measurer.measure(it, label).size.width }.toDp() }
            levels.forEachIndexed { band, level ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(frequencies[band], Modifier.width(frequencyWidth), style = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(level.toFloat(), { value -> val moved = levels.toMutableList().also { it[band] = value.toInt() }; onChange(settings.copy(equalizerLevels = moved, equalizerCustom = moved, equalizerUsesCustom = true)) },
                        Modifier.weight(1f), enabled = settings.equalizer, valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat())
                    Text(stringResource(R.string.unit_db, level / 100f), Modifier.width(gainWidth), style = label, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
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
    AppDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.normalization_title)) }, text = {
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
    }, confirmButton = { DialogDismiss(onClick = onClose) { Text(stringResource(R.string.action_close)) } })
}
