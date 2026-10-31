package io.github.yyan115.undertow.preview

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.core.formatDuration
import io.github.yyan115.undertow.data.Settings
import io.github.yyan115.undertow.ui.*

private data class SourceReadout(val codec: String, val bits: String, val rate: String)
private fun sourceReadout(state: AppState): SourceReadout {
    val label = state.playback.quality
    return SourceReadout(label.substringBefore(" · ").ifBlank { "—" },
        Regex("(\\d+)-bit").find(label)?.groupValues?.get(1)?.let { "$it bit" } ?: "—",
        Regex("([\\d.]+) kHz").find(label)?.groupValues?.get(1)?.let { "$it kHz" } ?: "—")
}

/** Interactive layout studies using the app's real artwork, fonts and theme picker. */
@Composable
fun PremiumConcepts(initial: AppState, concept: String, page: String?, onSettings: (Settings) -> Unit) {
    var settings by remember { mutableStateOf(initial.settings.copy(theme = "dark", colorTheme = concept)) }
    var player by rememberSaveable { mutableStateOf(page == "player") }
    var appearance by rememberSaveable { mutableStateOf(page == "appearance") }
    val state = initial.copy(settings = settings)
    UndertowTheme(settings.theme, settings.colorTheme) {
        val view = LocalView.current
        val activity = LocalActivity.current
        val light = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            activity?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
            } }
        }
        ProvideTextStyle(TextStyle(fontFamily = InterfaceFont, fontSize = 12.sp, lineHeight = 18.sp)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (player) ConceptPlayer(state, concept, { player = false }, { appearance = true })
            else Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (concept) {
                        "gallery" -> GalleryHome(state, { player = true }, { appearance = true })
                        "archive" -> ArchiveHome(state, { player = true }, { appearance = true })
                        else -> ReferenceHome(state, { player = true }, { appearance = true })
                    }
                }
                ConceptDock(state, concept, { player = true })
            }
        }
        if (appearance) AppearanceSheet(settings, { settings = it; onSettings(it) }) { appearance = false }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, bright: Boolean = false) {
    Text(text.uppercase(), modifier, fontFamily = InterfaceFont, fontSize = 9.sp, lineHeight = 14.sp,
        letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium,
        color = if (bright) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Masthead(onSettings: () -> Unit, technical: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (technical) Icon(Icons.Outlined.GraphicEq, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text("UNDERTOW", fontFamily = InterfaceFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, "Appearance", Modifier.size(22.dp)) }
    }
}

@Composable
private fun Heading(title: String, serif: Boolean = false, modifier: Modifier = Modifier, size: Int = 52) {
    Text(title, modifier, fontFamily = if (serif) EditorialFont else InterfaceFont,
        fontSize = size.sp, lineHeight = (size + 3).sp, fontWeight = FontWeight.Normal,
        letterSpacing = if (serif) (-.3).sp else (-2).sp)
}

@Composable
private fun AlbumStrip(state: AppState, serif: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Saved albums", Modifier.weight(1f), fontFamily = if (serif) EditorialFont else InterfaceFont,
            fontSize = if (serif) 27.sp else 18.sp, fontWeight = FontWeight.Normal)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "View albums", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        state.albums.take(2).forEach { album ->
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Artwork(album.artwork, album.title, Modifier.fillMaxWidth().aspectRatio(1.12f))
                Text(album.title, Modifier.padding(top = 9.dp), fontFamily = InterfaceFont, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!serif) Text(album.artist, Modifier.padding(top = 3.dp), fontFamily = InterfaceFont, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GalleryHome(state: AppState, onOpen: () -> Unit, onSettings: () -> Unit) {
    val track = requireNotNull(state.playback.track)
    val source = sourceReadout(state)
    Masthead(onSettings)
    Heading("Listen", serif = true, Modifier.padding(start = 24.dp, top = 5.dp), 52)
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Label("Continue listening", Modifier.weight(1f))
        Text("01 / 03", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable(onClick = onOpen)) {
        Artwork(track.artwork, "Featured album: ${track.album}", Modifier.fillMaxWidth().aspectRatio(1.6f))
        Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color(0xDA141414), shape = RoundedCornerShape(3.dp)) {
            Text("${source.codec}  /  ${source.bits.uppercase()} · ${source.rate}", Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                fontFamily = InterfaceFont, fontSize = 8.sp, letterSpacing = .5.sp, color = Color(0xFFF5EEE0))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Heading(track.title, serif = true, size = 38)
            Text(track.artist, Modifier.padding(top = 3.dp), fontFamily = InterfaceFont, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        PlayFace(onOpen, size = 54, ivory = true)
    }
    AlbumStrip(state, true, onOpen)
}

@Composable
private fun ArchiveHome(state: AppState, onOpen: () -> Unit, onSettings: () -> Unit) {
    val track = requireNotNull(state.playback.track)
    Masthead(onSettings)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        Heading("Collection", size = 46)
        Text("${state.albums.size} albums  /  ${state.favorites.size} saved tracks", Modifier.padding(top = 9.dp),
            fontFamily = InterfaceFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, track.album, Modifier.weight(1.1f).aspectRatio(1f))
            Column(Modifier.weight(1f)) {
                Label("Recently played", bright = true)
                Text(track.album, Modifier.padding(top = 12.dp), fontFamily = EditorialFont, fontSize = 35.sp, lineHeight = 34.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(track.artist, Modifier.padding(top = 8.dp), fontFamily = InterfaceFont, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Continue", fontFamily = InterfaceFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Saved tracks", Modifier.weight(1f), fontFamily = InterfaceFont, fontSize = 18.sp)
        Label("View all")
    }
    state.favorites.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(index + 1), Modifier.width(33.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(item.title, fontFamily = InterfaceFont, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(item.artist, Modifier.padding(top = 5.dp), fontFamily = InterfaceFont, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(item.durationMs), Modifier.padding(start = 10.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ReferenceHome(state: AppState, onOpen: () -> Unit, onSettings: () -> Unit) {
    val track = requireNotNull(state.playback.track)
    val source = sourceReadout(state)
    Masthead(onSettings, true)
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
        Heading("Listen", modifier = Modifier.weight(1f), size = 46)
        Label("Your library", Modifier.padding(bottom = 5.dp))
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(24.dp).clickable(onClick = onOpen), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Artwork(track.artwork, track.album, Modifier.weight(1.35f).aspectRatio(1f))
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Label("Continue", bright = true)
            Text(track.album, Modifier.padding(top = 12.dp), fontFamily = InterfaceFont, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-1).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(track.artist, Modifier.padding(top = 8.dp), fontFamily = InterfaceFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Label("Source")
            Text(source.codec, Modifier.padding(top = 6.dp), fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text("${source.bits} / ${source.rate}", Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(4.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, fontFamily = InterfaceFont, fontSize = 16.sp)
                Text("Resume at 1:04", Modifier.padding(top = 4.dp), fontFamily = InterfaceFont, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.PlayArrow, "Open player", tint = MaterialTheme.colorScheme.primary) }
        }
    }
    AlbumStrip(state, false, onOpen)
}

@Composable
private fun ConceptDock(state: AppState, concept: String, onOpen: () -> Unit) {
    val track = requireNotNull(state.playback.track)
    if (concept != "reference") Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(if (concept == "gallery") 6.dp else 10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, null, Modifier.size(34.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(track.title, fontFamily = InterfaceFont, fontSize = 11.sp)
                Text("${track.artist.substringBefore(',')} · ${track.album}", Modifier.padding(top = 3.dp), fontFamily = InterfaceFont, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.PlayArrow, "Open player", Modifier.size(23.dp)) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Icons.Outlined.Headphones to "Listen", Icons.Outlined.Search to "Search", Icons.Outlined.LibraryMusic to "Library", Icons.Outlined.Download to "Downloads").forEachIndexed { index, (icon, title) ->
            Column(Modifier.widthIn(min = 64.dp).heightIn(min = 52.dp).clickable {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, title, Modifier.size(21.dp), tint = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (index == 0 || concept == "reference") Text(title, Modifier.padding(top = 5.dp), fontFamily = InterfaceFont, fontSize = 8.sp,
                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlayFace(onClick: () -> Unit, size: Int = 72, ivory: Boolean = false, square: Boolean = false) {
    var playing by remember { mutableStateOf(false) }
    val fill = if (ivory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val ink = if (ivory) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onPrimary
    Box(Modifier.size(size.dp).clip(if (square) RoundedCornerShape(12.dp) else CircleShape).background(fill)
        .clickable { playing = !playing; onClick() }, contentAlignment = Alignment.Center) {
        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", Modifier.size((size * .45f).dp), tint = ink)
    }
}

@Composable
private fun FineSeek(position: Long, duration: Long) {
    var value by remember { mutableFloatStateOf(position.toFloat() / duration.coerceAtLeast(1)) }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(38.dp)
        .pointerInput(Unit) { detectTapGestures { value = (it.x / size.width).coerceIn(0f, 1f) } }
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f); setProgress { value = it.coerceIn(0f, 1f); true } }) {
        val y = size.height / 2
        drawLine(inactive, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(active, Offset(0f, y), Offset(size.width * value, y), 1.5.dp.toPx(), StrokeCap.Round)
        drawCircle(active, 3.dp.toPx(), Offset(size.width * value, y))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration((duration * value).toLong()), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(duration), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourcePanel(state: AppState) {
    val border = MaterialTheme.colorScheme.outlineVariant
    val source = sourceReadout(state)
    Column(Modifier.fillMaxWidth().border(1.dp, border, RoundedCornerShape(4.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Codec" to source.codec, "Bit depth" to source.bits, "Sample rate" to source.rate).forEach { (label, value) ->
                Column {
                    Label(label)
                    Text(value, Modifier.padding(top = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ConceptPlayer(state: AppState, concept: String, onClose: () -> Unit, onSettings: () -> Unit) {
    val track = requireNotNull(state.playback.track)
    val gallery = concept == "gallery"
    val archive = concept == "archive"
    val colors = MaterialTheme.colorScheme
    val source = sourceReadout(state)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.surfaceContainerLow, colors.background))).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, "Close player") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(if (archive) "Now playing" else "From the album")
                if (!archive) Text(track.album, Modifier.padding(top = 3.dp), fontFamily = InterfaceFont, fontSize = 11.sp)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.MoreHoriz, "Appearance") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val art = minOf(maxWidth - (if (archive) 64.dp else 48.dp), maxHeight * (if (archive) .40f else if (gallery) .43f else .46f))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (gallery) {
                    Heading(track.title, true, Modifier.padding(top = 12.dp), 54)
                    Text(track.artist, Modifier.padding(top = 5.dp, bottom = 22.dp), fontFamily = InterfaceFont, fontSize = 12.sp, color = colors.onSurfaceVariant)
                } else Spacer(Modifier.height(16.dp))
                Box(Modifier.align(Alignment.CenterHorizontally).then(if (archive) Modifier.background(Color(0xFFE5D8C9)).padding(10.dp) else Modifier)) {
                    Artwork(track.artwork, "Artwork for ${track.album}", Modifier.size(art))
                }
                if (!gallery) {
                    Label(track.album, Modifier.padding(top = 22.dp), bright = archive)
                    Heading(track.title, false, Modifier.padding(top = 7.dp), if (archive) 39 else 36)
                    Text(track.artist, Modifier.padding(top = 7.dp), fontFamily = InterfaceFont, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                if (concept == "reference") {
                    Spacer(Modifier.height(20.dp)); SourcePanel(state)
                } else Row(Modifier.fillMaxWidth().padding(top = if (gallery) 22.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(source.codec, bright = true)
                    Text("  /  ${source.bits} · ${source.rate}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.FavoriteBorder, "Save track", Modifier.size(21.dp), tint = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                FineSeek(state.playback.position, state.playback.duration)
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Shuffle, "Shuffle", Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.SkipPrevious, "Previous track", Modifier.size(30.dp)) }
                    PlayFace({}, size = if (gallery) 76 else 68, ivory = gallery, square = concept == "reference")
                    IconButton(onClick = {}) { Icon(Icons.Outlined.SkipNext, "Next track", Modifier.size(30.dp)) }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Repeat, "Repeat", Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {}) { Icon(Icons.Outlined.Lyrics, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Lyrics", fontFamily = InterfaceFont, fontSize = 11.sp) }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Download, "Download", Modifier.size(20.dp)) }
                    TextButton(onClick = {}) { Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Queue", fontFamily = InterfaceFont, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (concept != "reference") {
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.playback.queue[1].artwork, null, Modifier.size(34.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Label("Next in queue")
                    Text(state.playback.queue[1].title, Modifier.padding(top = 4.dp), fontFamily = InterfaceFont, fontSize = 11.sp)
                }
                Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, "Open queue", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}
