package io.github.yyan115.opentune.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.Settings
import kotlinx.coroutines.launch

/** The preview is drawn at this size and scaled to the room that the sheet has, so that no theme needs scrolling. */
private val PreviewWidth = 300.dp
private val PreviewHeight = 500.dp

/**
 * Lets the listener browse the themes. Each page is a small copy of the real interface in that theme, drawn with the real components.
 * A theme applies to the whole app when the listener taps its button, and the sheet itself changes with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(settings: Settings, onChange: (Settings) -> Unit, onPreview: (String?) -> Unit = {}, onClose: () -> Unit) {
    val ids = listOf(SYSTEM_THEME) + appThemes.map { it.id }
    val systemDark = isSystemInDarkTheme()
    val pager = rememberPagerState(initialPage = ids.indexOf(settings.theme).coerceAtLeast(0)) { ids.size }
    val shown = ids[pager.currentPage]
    val inUse = settings.theme == shown
    // The whole app shows the theme that the listener swipes to. It stays only after Use, and closing the picker brings back the saved theme.
    LaunchedEffect(shown, settings.theme) { onPreview(shown.takeIf { it != settings.theme }) }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val theme = themeFor(shown, systemDark)
        val name = stringResource(if (shown == SYSTEM_THEME) R.string.theme_system else theme.nameRes)
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appearance_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val scale = ((maxHeight - 8.dp) / PreviewHeight).coerceIn(.3f, 1f)
                val width = PreviewWidth * scale
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("theme-pager"), contentPadding = PaddingValues(horizontal = ((maxWidth - width) / 2).coerceAtLeast(0.dp)),
                    pageSpacing = 12.dp, verticalAlignment = Alignment.CenterVertically) { page ->
                    Box(Modifier.size(width, PreviewHeight * scale), contentAlignment = Alignment.Center) {
                        Box(Modifier.requiredSize(PreviewWidth, PreviewHeight).scale(scale)) { ThemePreview(ids[page], systemDark, selected = settings.theme == ids[page]) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            TuneButton(onClick = { onChange(settings.copy(theme = shown)) }, enabled = !inUse,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp).heightIn(min = 48.dp).testTag("theme-apply")) {
                if (inUse) { Icon(OpenTuneIcons.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                Text(stringResource(if (inUse) R.string.appearance_in_use else R.string.appearance_use, name))
            }
        }
    }
}

private val SampleTracks = listOf(
    Track("preview:night-walk", "Night Walk", "Harbour Lights", "Late Hours", durationMs = 186_000),
    Track("preview:slow-tide", "Slow Tide", "Maren Vale", "Salt and Glass", durationMs = 175_000),
)

/** A picture of the interface in one theme. It uses the real components and does not react to taps. */
@Composable
private fun ThemePreview(id: String, systemDark: Boolean, selected: Boolean) {
    val theme = themeFor(id, systemDark)
    val name = stringResource(if (id == SYSTEM_THEME) R.string.theme_system else theme.nameRes)
    OpenTuneTheme(id) {
        val scheme = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = name }, color = scheme.background, contentColor = scheme.onBackground,
            shape = RoundedCornerShape(22.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)) {
            Box {
                Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
                    Text(stringResource(R.string.nav_home), Modifier.padding(horizontal = 20.dp), style = AppFonts.title(40, 46))
                    SectionTitle(stringResource(R.string.home_recently_played), stringResource(R.string.action_view_all))
                    CompositionLocalProvider(LocalQueueTrack provides null) {
                        TrackRow(SampleTracks[0], true, {}, {})
                        TrackRow(SampleTracks[1], false, {}, {})
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TuneButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_play)) }
                        TuneOutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_shuffle)) }
                    }
                    Spacer(Modifier.weight(1f))
                    MiniPlayer(PlaybackState(queue = SampleTracks, index = 0, playing = true, duration = 186_000, nextIndex = 1), remember { mutableStateOf(64_000L) }, {}, {}, {})
                    OpenTuneNavigation(listOf(Destination.Home, Destination.Search, Destination.Library, Destination.Device), Destination.Home) {}
                }
                // The preview shows the interface and does not use it, so it takes every tap.
                Box(Modifier.matchParentSize().clickable(indication = null, interactionSource = null) {})
            }
        }
    }
}
