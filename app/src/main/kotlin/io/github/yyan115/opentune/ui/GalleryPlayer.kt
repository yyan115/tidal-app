package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yyan115.opentune.playback.AudioSpectrum
import androidx.compose.ui.graphics.Brush
import io.github.yyan115.opentune.core.Spectrum
import io.github.yyan115.opentune.core.SpectrumAnalyzer
import io.github.yyan115.opentune.core.SpectrumSmoother
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val description = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(Modifier.size(size).clip(CircleShape).background(colors.onSurface).clickable(onClick = onClick)
        .semantics { contentDescription = description; role = Role.Button }, contentAlignment = Alignment.Center) {
        if (buffering) CircularProgressIndicator(Modifier.size(size * .35f), color = colors.background, strokeWidth = 1.6.dp)
        else Icon(if (playing) OpenTuneIcons.Pause else OpenTuneIcons.PlayArrow, null, Modifier.size(size * .42f), tint = colors.background)
    }
}

/** The playback position in milliseconds. The activity provides the position of the player and a preview provides a fixed one. */
val LocalPlaybackPosition = staticCompositionLocalOf<StateFlow<Long>> { MutableStateFlow(0L) }

/** The mini player. A swipe to either side that goes far enough stops playback and clears the queue. */
@Composable
fun MiniPlayer(state: PlaybackState, position: State<Long>, onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit, onDismiss: () -> Unit = {}) {
    val track = state.track ?: return
    var width by remember { mutableIntStateOf(1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by androidx.compose.animation.core.animateFloatAsState(if (dragging) offset else 0f, label = "mini-player-swipe")
    val stop = stringResource(R.string.player_stop)
    // Only a drag of the finger moves the bar, so that nothing but a deliberate swipe stops the music.
    Box(Modifier.testTag("mini-player").onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer { translationX = shown; alpha = 1f - .7f * (kotlin.math.abs(shown) / width).coerceIn(0f, 1f) }
        .semantics { customActions = listOf(CustomAccessibilityAction(stop) { onDismiss(); true }) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { dragging = true; offset = 0f },
                onDragEnd = { dragging = false; if (kotlin.math.abs(offset) > width * .35f) onDismiss() },
                onDragCancel = { dragging = false }) { change, amount -> change.consume(); offset += amount }
        }) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(track, null, Modifier.size(35.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                    Text(track.title, fontFamily = AppFonts.body, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.error != null) stringResource(R.string.player_stopped) else track.artist, Modifier.padding(top = 2.dp),
                        fontFamily = AppFonts.body, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onPlay) {
                    if (state.buffering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
                    else Icon(if (state.playing) OpenTuneIcons.Pause else OpenTuneIcons.PlayArrow, stringResource(if (state.playing) R.string.player_pause else R.string.player_play), Modifier.size(21.dp))
                }
                IconButton(onClick = onNext, enabled = state.nextIndex >= 0) { Icon(OpenTuneIcons.SkipNext, stringResource(R.string.player_next), Modifier.size(20.dp)) }
            }
            LinearProgressIndicator(progress = { if (state.duration > 0) (position.value.toFloat() / state.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(1.dp), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
    }
}

@UnstableApi
@Composable
fun NowPlayingScreen(state: AppState, vm: MainViewModel, onClose: () -> Unit, onQueue: () -> Unit, onMenu: (Track) -> Unit, onDownload: (Track) -> Unit,
    onAlbum: (Track) -> Unit = {}, onArtist: (Track) -> Unit = {}, initialLyrics: Boolean = false) {
    val track = state.playback.track ?: return
    var lyrics by rememberSaveable { mutableStateOf(initialLyrics) }
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
                // The controls stay at the bottom of the screen. Only a very short screen scrolls the whole page.
                val scrolling = maxHeight < 480.dp || (large && maxHeight < 700.dp)
                val dense = maxHeight < 700.dp || large
                val landscapeArtworkSize = minOf(maxHeight - 100.dp, maxWidth * .35f)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(OpenTuneIcons.KeyboardArrowDown, stringResource(R.string.player_close), Modifier.size(22.dp)) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            UpperLabel(if (state.playback.source.isNotBlank()) stringResource(R.string.player_playing_from) else stringResource(R.string.player_from_album))
                            Text(state.playback.source.ifBlank { track.album.ifBlank { stringResource(R.string.player_now_playing) } }, Modifier.padding(top = 3.dp), fontFamily = AppFonts.body,
                                fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onMenu(track) }) { Icon(OpenTuneIcons.MoreHoriz, stringResource(R.string.player_track_options), Modifier.size(22.dp)) }
                    }
                    if (landscape) {
                        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            if (lyrics) LyricsPanel(state, vm, Modifier.width(landscapeArtworkSize * 1.3f).height(landscapeArtworkSize))
                            else PlayerArtwork(track, stringResource(R.string.player_artwork, track.album), Modifier.size(landscapeArtworkSize), state.settings.visualizer && android.os.Build.VERSION.SDK_INT >= VISUALIZER_API, state.playback.playing)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                PlayerHeading(track, true, onAlbum, onArtist)
                                GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, true)
                            }
                        }
                    } else if (scrolling) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                            PlayerHeading(track, true, onAlbum, onArtist)
                            if (lyrics) LyricsPanel(state, vm, Modifier.fillMaxWidth().height(if (large) 220.dp else 260.dp))
                            else PlayerArtwork(track, stringResource(R.string.player_artwork, track.album), Modifier.size(if (large) 104.dp else 180.dp).align(Alignment.CenterHorizontally).padding(vertical = 8.dp), state.settings.visualizer && android.os.Build.VERSION.SDK_INT >= VISUALIZER_API, state.playback.playing)
                            GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, true)
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
                            PlayerHeading(track, dense, onAlbum, onArtist)
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (lyrics) LyricsPanel(state, vm, Modifier.fillMaxSize())
                                else PlayerArtwork(track, stringResource(R.string.player_artwork, track.album), Modifier.size(minOf(maxWidth - 20.dp, maxHeight - 8.dp).coerceAtLeast(0.dp)), state.settings.visualizer && android.os.Build.VERSION.SDK_INT >= VISUALIZER_API, state.playback.playing)
                            }
                            GalleryControls(state, vm, onQueue, onDownload, { source = true }, { lyrics = !lyrics }, lyrics, { timer = true }, dense)
                        }
                    }
                }
            }
        }
    }
    if (source) SourceDetailsSheet(state.playback.quality, state.playback.track, vm) { source = false }
    if (timer) SleepTimerSheet(state, vm) { timer = false }
}

@Composable
private fun PlayerHeading(track: Track, compact: Boolean, onAlbum: (Track) -> Unit, onArtist: (Track) -> Unit) {
    // A TIDAL track links to its album and its artist, as in TIDAL's own apps. A file on the device has neither page.
    val album = track.albumId.isNotBlank() && !track.isFile
    val artist = track.artistId.isNotBlank() && !track.isFile
    Text(track.title, Modifier.fillMaxWidth().testTag("player-title").padding(top = if (compact) 8.dp else 12.dp)
        .then(if (album) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.track_go_album)) { onAlbum(track) } else Modifier),
        style = if (compact) AppFonts.title(34, 40) else AppFonts.title(48, 54), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(track.artist, Modifier.fillMaxWidth().testTag("player-artist").padding(top = 6.dp, bottom = 18.dp).heightIn(min = 32.dp)
        .then(if (artist) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.track_go_artist)) { onArtist(track) } else Modifier),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun GalleryControls(state: AppState, vm: MainViewModel, onQueue: () -> Unit, onDownload: (Track) -> Unit, onSource: () -> Unit, onLyrics: () -> Unit, lyricsActive: Boolean, onTimer: () -> Unit, compact: Boolean) {
    val player = state.playback; val track = player.track ?: return
    val largeText = LocalDensity.current.fontScale > 1.3f
    val sourceDetails = stringResource(R.string.player_source_details)
    val lyricsDescription = stringResource(if (lyricsActive) R.string.player_hide_lyrics else R.string.player_show_lyrics)
    val timerOn = state.sleepEndsAt > 0 || state.sleepAtTrackEnd
    val timerDescription = stringResource(if (timerOn) R.string.player_timer_on else R.string.player_timer_set)
    val saved = state.favorites.any { it.id == track.id }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 44.dp).clickable(onClick = onSource).semantics { contentDescription = sourceDetails }, verticalAlignment = Alignment.CenterVertically) {
            val format = SourceReadout.from(player.quality)
            val resolution = listOfNotNull(format.bitsText(), format.rateText()).joinToString(" · ")
            if (largeText) Column(Modifier.weight(1f)) {
                Text(if (player.quality.isBlank()) stringResource(R.string.source_label) else format.codec.ifBlank { stringResource(R.string.source_not_reported) }, fontFamily = AppFonts.body, fontSize = 10.sp,
                    letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
                if (resolution.isNotBlank()) Text(resolution, Modifier.padding(top = 4.dp),
                    fontFamily = AppFonts.mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (player.quality.isBlank()) stringResource(R.string.source_label) else format.codec.ifBlank { stringResource(R.string.source_not_reported) }, fontFamily = AppFonts.body, fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
                Text(if (resolution.isEmpty()) "" else "  /  $resolution", fontFamily = AppFonts.mono, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(7.dp)); Icon(OpenTuneIcons.KeyboardArrowDown, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.favorite(track) }) { Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder,
            stringResource(if (saved) R.string.track_unsave else R.string.track_save), Modifier.size(22.dp), tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    player.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    PlaybackSlider(vm, track.id, player.duration)
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 12.dp else 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(player.shuffle, onCheckedChange = { vm.toggleShuffle() }) { Icon(OpenTuneIcons.Shuffle, stringResource(R.string.player_shuffle), Modifier.size(20.dp), tint = if (player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = vm::previous) { Icon(OpenTuneIcons.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(29.dp)) }
        PlayControl(player.playing, player.buffering, if (compact) 60.dp else 74.dp, vm::togglePlay)
        IconButton(onClick = vm::next, enabled = player.nextIndex >= 0) { Icon(OpenTuneIcons.SkipNext, stringResource(R.string.player_next), Modifier.size(29.dp)) }
        IconToggleButton(player.repeat != Player.REPEAT_MODE_OFF, onCheckedChange = { vm.toggleRepeat() }) {
            Icon(if (player.repeat == Player.REPEAT_MODE_ONE) OpenTuneIcons.RepeatOne else OpenTuneIcons.Repeat,
                stringResource(when (player.repeat) { Player.REPEAT_MODE_ONE -> R.string.player_repeat_one; Player.REPEAT_MODE_ALL -> R.string.player_repeat_all; else -> R.string.player_repeat_off }),
                Modifier.size(20.dp), tint = if (player.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // The next track sits above the actions, so that the actions stay the lowest element of the screen.
    if (!compact) player.queue.getOrNull(player.nextIndex)?.let { next ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick = onQueue).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArtwork(next, null, Modifier.size(34.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                UpperLabel(stringResource(R.string.player_next_in_queue))
                Text(next.title, fontFamily = AppFonts.body, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(OpenTuneIcons.PlaylistPlay, stringResource(R.string.player_open_queue), Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    val remaining by rememberSleepMinutes(state.sleepEndsAt)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    // Four equal columns with one-line captions, so a longer translation shortens a caption and never moves a button.
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
        PlayerAction(OpenTuneIcons.Lyrics, stringResource(R.string.player_lyrics), lyricsDescription, if (lyricsActive) accent else muted, Modifier.weight(1f), onClick = onLyrics)
        PlayerAction(OpenTuneIcons.Timer,
            if (!timerOn) stringResource(R.string.player_timer_label) else if (state.sleepAtTrackEnd) stringResource(R.string.player_timer_end) else stringResource(R.string.player_timer_minutes, remaining ?: 0),
            timerDescription, if (timerOn) accent else muted, Modifier.weight(1f), onClick = onTimer)
        DownloadAction(state, Modifier.weight(1f), onDownload)
        PlayerAction(OpenTuneIcons.PlaylistPlay, stringResource(R.string.player_queue_count, player.queue.size), null, muted, Modifier.weight(1f), onClick = onQueue)
    }
}

/** The cover of the playing track. With the visualizer on, the cover dims and the bars of the music move over it. */
@Composable
private fun PlayerArtwork(track: Track, description: String, modifier: Modifier, visualizer: Boolean, playing: Boolean) {
    Box(modifier) {
        TrackArtwork(track, description, Modifier.fillMaxSize().graphicsLayer { alpha = if (visualizer) VISUALIZER_COVER_ALPHA else 1f })
        if (visualizer) SpectrumBars(playing, Modifier.matchParentSize().padding(12.dp).testTag("spectrum"))
    }
}

/** The number of frequency bands. Each one is drawn twice, so that the bars mirror around the middle of the cover. */
private const val SPECTRUM_BANDS = 32

/** How much of the cover shows behind the bars. The rest is the background of the theme, so that the bars stay readable. */
const val VISUALIZER_COVER_ALPHA = .4f

/** How opaque the bars are at their tips. The centre line is fully opaque. */
const val VISUALIZER_TIP_ALPHA = .6f

/** How opaque the markers are. They use the text colour of the theme, which is dark on a light theme and light on a dark one. */
const val VISUALIZER_MARKER_ALPHA = .85f

/** The analysis runs at most this often, which is the rate of a 60 Hz screen. */
private const val SPECTRUM_FRAME_NANOS = 16_000_000L

/**
 * Bars that grow up and down from the middle line and follow the levels of the music, up to sixty times a second.
 * The lowest pitches are in the middle and the highest at both edges. A marker above each bar shows its recent peak.
 * The bars sink when the music pauses, and then nothing redraws until it plays again.
 */
@Composable
private fun SpectrumBars(playing: Boolean, modifier: Modifier) {
    val smoother = remember { SpectrumSmoother(SPECTRUM_BANDS) }
    val analyzer = remember { SpectrumAnalyzer() }
    var frame by remember { mutableIntStateOf(0) }
    val color = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = VISUALIZER_MARKER_ALPHA)
    LaunchedEffect(playing) {
        val samples = FloatArray(Spectrum.SIZE)
        val fresh = FloatArray(SPECTRUM_BANDS)
        var last = 0L
        var running = true
        while (running) {
            withFrameNanos { now ->
                if (now - last >= SPECTRUM_FRAME_NANOS) {
                    val seconds = if (last == 0L) 1f / 60f else (now - last) / 1_000_000_000f
                    last = now
                    val heard = playing && AudioSpectrum.latest(samples)
                    if (heard) analyzer.bands(samples, AudioSpectrum.sampleRate, SPECTRUM_BANDS, into = fresh)
                    smoother.update(if (heard) fresh else null, seconds)
                    frame++
                    if (!playing && smoother.idle) running = false
                }
            }
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        frame.let { }
        val middle = size.height / 2
        val slots = SPECTRUM_BANDS * 2
        val slot = size.width / slots
        val barWidth = slot * .62f
        val markerHeight = 2.dp.toPx()
        val markerGap = 3.dp.toPx()
        val reach = middle - markerGap - markerHeight
        val brush = Brush.verticalGradient(0f to color.copy(alpha = VISUALIZER_TIP_ALPHA), .5f to color, 1f to color.copy(alpha = VISUALIZER_TIP_ALPHA), startY = 0f, endY = size.height)
        for (slotIndex in 0 until slots) {
            val band = if (slotIndex < SPECTRUM_BANDS) SPECTRUM_BANDS - 1 - slotIndex else slotIndex - SPECTRUM_BANDS
            val x = slotIndex * slot + (slot - barWidth) / 2
            val height = (reach * smoother.levels[band].coerceIn(0f, 1f)).coerceAtLeast(1.5.dp.toPx())
            drawRoundRect(brush, androidx.compose.ui.geometry.Offset(x, middle - height), androidx.compose.ui.geometry.Size(barWidth, height * 2),
                androidx.compose.ui.geometry.CornerRadius(barWidth / 2))
            val peak = maxOf(height, reach * smoother.peaks[band].coerceIn(0f, 1f))
            if (smoother.peaks[band] > .02f) {
                val markerSize = androidx.compose.ui.geometry.Size(barWidth, markerHeight)
                val corner = androidx.compose.ui.geometry.CornerRadius(markerHeight / 2)
                drawRoundRect(markerColor, androidx.compose.ui.geometry.Offset(x, middle - peak - markerGap - markerHeight), markerSize, corner)
                drawRoundRect(markerColor, androidx.compose.ui.geometry.Offset(x, middle + peak + markerGap), markerSize, corner)
            }
        }
    }
}

/** The seek bar and the two times. It reads the position itself, so that only this part redraws while a track plays. */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun PlaybackSlider(vm: MainViewModel, trackId: String, duration: Long) {
    val position by LocalPlaybackPosition.current.collectAsStateWithLifecycle()
    var seeking by remember(trackId) { mutableStateOf<Float?>(null) }
    val sliderColors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant)
    Slider(value = seeking ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { seeking = it },
        valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0,
        onValueChangeFinished = { seeking?.let { vm.seek(it.toLong()) }; seeking = null }, modifier = Modifier.fillMaxWidth().height(38.dp),
        thumb = { Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
        track = { SliderDefaults.Track(it, Modifier.height(2.dp), colors = sliderColors, thumbTrackGapSize = 0.dp, drawStopIndicator = null) }, colors = sliderColors)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(seeking?.toLong() ?: position), fontFamily = AppFonts.mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(duration), fontFamily = AppFonts.mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An action below the transport controls: an icon with a one-line caption. */
@Composable
private fun PlayerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String?, tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val shown = if (enabled) tint else tint.copy(alpha = .45f)
    Column(modifier.heightIn(min = 52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 1.5.dp, color = shown) else Icon(icon, null, Modifier.size(22.dp), tint = shown)
        Text(label, Modifier.padding(top = 3.dp), fontFamily = AppFonts.body, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = shown)
    }
}

@Composable
private fun DownloadAction(state: AppState, modifier: Modifier, onDownload: (Track) -> Unit) {
    val track = state.playback.track ?: return
    val downloaded = track.onDevice || state.local.any { it.id == track.id }
    val pending = state.downloads.any { "track:${track.id}" in it.tags && !it.state.isFinished }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val file = track.isFile
    PlayerAction(if (file) OpenTuneIcons.Device else if (downloaded) OpenTuneIcons.DownloadDone else OpenTuneIcons.Download,
        stringResource(if (file) R.string.label_local else if (downloaded) R.string.player_downloaded else if (pending) R.string.player_downloading else R.string.player_label_download),
        stringResource(if (file) R.string.source_origin_file else if (downloaded) R.string.player_downloaded else R.string.player_download), muted, modifier, enabled = !downloaded && !pending, busy = pending) { onDownload(track) }
}
