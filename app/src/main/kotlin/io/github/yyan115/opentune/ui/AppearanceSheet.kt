package io.github.yyan115.opentune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.data.Settings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppearanceSheet(settings: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    val dark = settings.theme == "dark" || (settings.theme == "system" && isSystemInDarkTheme())
    val columns = if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) 1 else 2
    val rows = appColorThemes.chunked(columns)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            stickyHeader {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow), verticalAlignment = Alignment.CenterVertically) {
                    Text("Appearance", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text("Done") }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, title) ->
                        FilterChip(settings.theme == value, { onChange(settings.copy(theme = value)) }, label = { Text(title) })
                    }
                }
            }
            item { Text("Colour", style = MaterialTheme.typography.titleSmall) }
            items(rows.size) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows[row].forEach { palette ->
                        ColorThemeCard(palette, dark, settings.colorTheme == palette.id,
                            Modifier.weight(1f)) { onChange(settings.copy(colorTheme = palette.id)) }
                    }
                    if (rows[row].size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorThemeCard(palette: AppColorTheme, dark: Boolean, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val scheme = if (dark) palette.dark else palette.light
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
        .clip(shape).background(scheme.background).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Listen", Modifier.weight(1f), color = scheme.onSurface, fontFamily = EditorialFont,
                fontSize = 22.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(18.dp).background(scheme.primary, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(34.dp).background(scheme.primaryContainer, RoundedCornerShape(palette.radius.dp)))
            Column(Modifier.weight(1f).padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(.85f).height(3.dp).background(scheme.onSurface.copy(alpha = .7f)))
                Box(Modifier.fillMaxWidth(.6f).height(3.dp).background(scheme.onSurfaceVariant.copy(alpha = .5f)))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(palette.name, Modifier.weight(1f), color = scheme.onSurface, fontFamily = InterfaceFont, fontSize = 12.sp)
            if (selected) Icon(OpenTuneIcons.Check, "Selected", Modifier.size(16.dp), tint = scheme.primary)
        }
    }
}
