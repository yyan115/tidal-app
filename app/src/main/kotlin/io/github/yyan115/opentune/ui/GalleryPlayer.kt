package io.github.yyan115.opentune.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.core.formatDuration

@Composable
fun PlayControl(playing: Boolean, buffering: Boolean = false, size: Dp = 72.dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape).background(colors.onSurface).clickable(onClick = onClick)
        .semantics { contentDescription = if (playing) "Pause" else "Play"; role = Role.Button }, contentAlignment = Alignment.Center) {
        if (buffering) CircularProgressIndicator(Modifier.size(size * .35f), color = colors.background, strokeWidth = 1.6.dp)
        else Icon(if (playing) OpenTuneIcons.Pause else OpenTuneIcons.PlayArrow, null, Modifier.size(size * .42f), tint = colors.background)
    }
}

@Composable
fun MiniPlayer(state: PlaybackState, onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    val track = state.track ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).testTag("mini-player"),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artwork, null, Modifier.size(35.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                    Text(track.title, fontFamily = InterfaceFont, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.error != null) "Playback stopped" else track.artist, Modifier.padding(top = 2.dp),
                        fontFamily = InterfaceFont, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onPlay) {
                    if (state.buffering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
                    else Icon(if (state.playing) OpenTuneIcons.Pause else OpenTuneIcons.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(21.dp))
                }
                IconButton(onClick = onNext, enabled = state.nextIndex >= 0) { Icon(OpenTuneIcons.SkipNext, "Next track", Modifier.size(20.dp)) }
            }
            LinearProgressIndicator(progress = { if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(1.dp), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@UnstableApi
@Composable
fun NowPlayingScreen(state: AppState, vm: MainViewModel, onClose: () -> Unit, onQueue: () -> Unit, onMenu: (Track) -> Unit, onDownload: (Track) -> Unit) {
    val track = state.playback.track ?: return
    var lyrics by rememberSaveable { mutableStateOf(false) }
    var timer by remember { mutableStateOf(false) }
    LaunchedEffect(track.id, lyrics) { if (lyrics) vm.loadLyrics() }
    var source by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val light = MaterialTheme.colorScheme.background.luminance() > .5f
        LaunchedEffect(light) {
            withFrameNanos { }
            WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val large = LocalDensity.current.fontScale > 1.3f
                val landscape = maxWidth > maxHeight && maxWidth >= 480.dp && !large
                val compact = maxHeight < 700.dp || large
                val landscapeArtworkSize = minOf(maxHeight - 100.dp, maxWidth * .35f)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(OpenTuneIcons.KeyboardArrowDown, "Close player", Modifier.size(22.dp)) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            UpperLabel("From the album")
                            Text(track.album.ifBlank { "Now playing" }, Modifier.padding(top = 3.dp), fontFamily = InterfaceFont,
                                fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onMenu(track) }) { Icon(OpenTuneIcons.MoreHoriz, "Track options", Modifier.size(22.dp)) }
                    }
                    if (landscape) {
                        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            if (lyrics) LyricsPanel(state, vm, Modifier.width(landscapeArtworkSize * 1.3f).height(landscapeArtworkSize))
                            else Artwork(track.artwork, "Artwork for ${track.album}", Modifier.size(landscapeArtworkSize))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                PlayerHeading(track, true)
                                GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, true)
                            }
                        }
                    } else if (compact) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                            PlayerHeading(track, true)
                            if (lyrics) LyricsPanel(state, vm, Modifier.fillMaxWidth().height(if (large) 220.dp else 260.dp))
                            else Artwork(track.artwork, "Artwork for ${track.album}", Modifier.size(if (large) 104.dp else 180.dp).align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
                            GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, true)
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
                            PlayerHeading(track, false)
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (lyrics) LyricsPanel(state, vm, Modifier.fillMaxSize())
                                else Artwork(track.artwork, "Artwork for ${track.album}", Modifier.size(minOf(maxWidth - 20.dp, maxHeight - 8.dp)))
                            }
                            GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, false)
                        }
                        state.playback.queue.getOrNull(state.playback.nextIndex)?.let { next ->
                            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().clickable(onClick = onQueue).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(next.artwork, null, Modifier.size(34.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    UpperLabel("Next in queue")
                                    Text(next.title, fontFamily = InterfaceFont, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(OpenTuneIcons.PlaylistPlay, "Open queue", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (source) SourceDetailsSheet(state.playback.quality) { source = false }
    if (timer) SleepTimerSheet(state, vm) { timer = false }
}

@Composable
private fun PlayerHeading(track: Track, compact: Boolean) {
    Text(track.title, Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 12.dp), fontFamily = EditorialFont,
        fontSize = if (compact) 34.sp else 48.sp, lineHeight = if (compact) 40.sp else 54.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(track.artist, Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun GalleryControls(state: AppState, vm: MainViewModel, onQueue: () -> Unit, onDownload: (Track) -> Unit, onSource: () -> Unit, onLyrics: () -> Unit, lyricsActive: Boolean, onTimer: () -> Unit, compact: Boolean) {
    val player = state.playback; val track = player.track ?: return
    val largeText = LocalDensity.current.fontScale > 1.3f
    val saved = state.favorites.any { it.id == track.id }
    var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 44.dp).clickable(onClick = onSource).semantics { contentDescription = "Audio source details" }, verticalAlignment = Alignment.CenterVertically) {
            val format = SourceReadout.from(player.quality)
            val resolution = listOfNotNull(format.bits, format.sampleRate).joinToString(" · ")
            if (largeText) Column(Modifier.weight(1f)) {
                Text(if (player.quality.isBlank()) "SOURCE" else format.codec, fontFamily = InterfaceFont, fontSize = 10.sp,
                    letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
                if (resolution.isNotBlank()) Text(resolution, Modifier.padding(top = 4.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (player.quality.isBlank()) "SOURCE" else format.codec, fontFamily = InterfaceFont, fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
                Text(if (resolution.isEmpty()) "" else "  /  $resolution", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(7.dp)); Icon(OpenTuneIcons.KeyboardArrowDown, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.favorite(track) }) { Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder,
            if (saved) "Remove from saved tracks" else "Save track", Modifier.size(22.dp), tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    player.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    val sliderColors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant)
    Slider(value = seeking ?: player.position.toFloat().coerceIn(0f, player.duration.coerceAtLeast(1).toFloat()), onValueChange = { seeking = it },
        valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), enabled = player.duration > 0,
        onValueChangeFinished = { seeking?.let { vm.seek(it.toLong()) }; seeking = null }, modifier = Modifier.fillMaxWidth().height(38.dp),
        thumb = { Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
        track = { SliderDefaults.Track(it, Modifier.height(2.dp), colors = sliderColors, thumbTrackGapSize = 0.dp, drawStopIndicator = null) }, colors = sliderColors)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(seeking?.toLong() ?: player.position), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(player.duration), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 12.dp else 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(player.shuffle, onCheckedChange = { vm.toggleShuffle() }) { Icon(OpenTuneIcons.Shuffle, "Shuffle", Modifier.size(20.dp), tint = if (player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = vm::previous) { Icon(OpenTuneIcons.SkipPrevious, "Previous track", Modifier.size(29.dp)) }
        PlayControl(player.playing, player.buffering, if (compact) 60.dp else 74.dp, vm::togglePlay)
        IconButton(onClick = vm::next, enabled = player.nextIndex >= 0) { Icon(OpenTuneIcons.SkipNext, "Next track", Modifier.size(29.dp)) }
        IconToggleButton(player.repeat != Player.REPEAT_MODE_OFF, onCheckedChange = { vm.toggleRepeat() }) {
            Icon(if (player.repeat == Player.REPEAT_MODE_ONE) OpenTuneIcons.RepeatOne else OpenTuneIcons.Repeat,
                when (player.repeat) { Player.REPEAT_MODE_ONE -> "Repeat one track"; Player.REPEAT_MODE_ALL -> "Repeat queue"; else -> "Repeat off" },
                Modifier.size(20.dp), tint = if (player.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onLyrics, colors = ButtonDefaults.textButtonColors(contentColor = if (lyricsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.semantics { contentDescription = if (lyricsActive) "Hide lyrics" else "Show lyrics" }) {
            Icon(OpenTuneIcons.Lyrics, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Lyrics", fontSize = 11.sp)
        }
        val remaining by rememberSleepMinutes(state.sleepEndsAt)
        val timerOn = state.sleepEndsAt > 0 || state.sleepAtTrackEnd
        IconButton(onClick = onTimer, modifier = Modifier.semantics { contentDescription = if (timerOn) "Sleep timer is on" else "Set sleep timer" }) {
            Icon(OpenTuneIcons.Timer, null, Modifier.size(20.dp), tint = if (timerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (timerOn) Text(if (state.sleepAtTrackEnd) "End" else "${remaining ?: 0}m", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        DownloadButton(state, onDownload)
        TextButton(onClick = onQueue) { Icon(OpenTuneIcons.PlaylistPlay, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Queue · ${player.queue.size}", fontSize = 11.sp) }
    }
}

@Composable
private fun DownloadButton(state: AppState, onDownload: (Track) -> Unit) {
    val track = state.playback.track ?: return
    val downloaded = track.isLocal || state.local.any { it.id == track.id }
    val pending = state.downloads.any { "track:${track.id}" in it.tags && !it.state.isFinished }
    IconButton(onClick = { onDownload(track) }, enabled = !downloaded && !pending) {
        if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
        else Icon(if (downloaded) OpenTuneIcons.DownloadDone else OpenTuneIcons.Download, if (downloaded) "Downloaded" else "Download track", Modifier.size(21.dp))
    }
}
