package io.github.yyan115.opentune.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track

@UnstableApi
@Composable
fun ListenScreen(state: AppState, vm: MainViewModel, onImport: () -> Unit, onLogin: () -> Unit,
    onLibrary: (Int) -> Unit, onMenu: (Track) -> Unit, onPlayer: () -> Unit) {
    fun available(track: Track) = track.isLocal || state.local.any { it.id == track.id }
    val recent = if (state.settings.offline) state.recent.filter(::available) else state.recent
    val current = state.playback.track?.takeIf { !state.settings.offline || available(it) }
    val hero = current ?: recent.firstOrNull()
    val heroTracks = recent.ifEmpty { listOfNotNull(hero) }
    val heroIndex = heroTracks.indexOfFirst { it.id == hero?.id }.coerceAtLeast(0)
    fun openHero() {
        if (hero != null && current?.id != hero.id) vm.play(heroTracks, heroIndex)
        onPlayer()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        if (hero != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    UpperLabel("Continue listening", Modifier.weight(1f))
                    if (available(hero)) Icon(OpenTuneIcons.DownloadDone, "Available offline", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val configuration = LocalConfiguration.current
                val wide = configuration.screenWidthDp > configuration.screenHeightDp
                val quality = state.playback.quality.takeIf { current?.id == hero.id }.orEmpty()
                val playing = current?.id == hero.id && state.playback.playing
                val buffering = current?.id == hero.id && state.playback.buffering
                val toggle = { if (current?.id == hero.id) vm.togglePlay() else vm.play(heroTracks, heroIndex) }
                if (wide) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ListeningArtwork(hero, quality, Modifier.width(240.dp), 1.5f, ::openHero)
                    ListeningTitle(hero, playing, buffering, Modifier.weight(1f), toggle)
                } else {
                    ListeningArtwork(hero, quality, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        if (configuration.screenHeightDp < 700) 1.9f else 1.6f, ::openHero)
                    ListeningTitle(hero, playing, buffering, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), toggle)
                }
            }
        } else {
            if (!state.signedIn && !state.settings.offline) item {
                ConnectCard(onLogin)
                TextButton(onClick = onImport, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(OpenTuneIcons.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Import audio files")
                }
            }
            else item { EmptyState(OpenTuneIcons.Album, "Nothing played yet", "Search your collection or import an audio file.", "Import audio", onImport) }
        }
        if (state.albums.isNotEmpty()) {
            item { SectionTitle("Saved albums", "View all", { onLibrary(1) }) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.albums.take(12), key = Release::id) { release -> ReleaseCard(release) { vm.open(release) } }
            } }
        }
        val others = recent.filterNot { it.id == hero?.id }.take(4)
        if (others.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            items(others, key = { "recent:${it.id}" }) { track -> TrackRow(track, false,
                { vm.play(recent, recent.indexOf(track)) }, { onMenu(track) }) }
        }
        val local = state.local.filterNot { it.id == hero?.id }.take(3)
        if (local.isNotEmpty()) {
            item { SectionTitle("On your device", "View all", { onLibrary(3) }) }
            items(local, key = { "device:${it.id}" }) { track -> TrackRow(track, false,
                { vm.play(state.local, state.local.indexOf(track)) }, { onMenu(track) }) }
        }
        if (hero != null && !state.signedIn && !state.settings.offline) item { Spacer(Modifier.height(16.dp)); ConnectCard(onLogin) }
    }
}


@Composable
private fun ListeningArtwork(track: Track, quality: String, modifier: Modifier, ratio: Float, onClick: () -> Unit) {
    Box(modifier.clickable(onClick = onClick)) {
        Artwork(track.artwork, "Artwork for ${track.album.ifBlank { track.title }}", Modifier.fillMaxWidth().aspectRatio(ratio))
        if (quality.isNotBlank()) Surface(Modifier.align(Alignment.TopStart).padding(12.dp),
            color = androidx.compose.ui.graphics.Color(0xE0121213), shape = RoundedCornerShape(3.dp)) {
            Text(quality.replace("-bit", " bit"), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                fontFamily = InterfaceFont, fontSize = 9.sp, lineHeight = 14.sp, color = androidx.compose.ui.graphics.Color(0xFFF1EBDD))
        }
    }
}

@Composable
private fun ListeningTitle(track: Track, playing: Boolean, buffering: Boolean, modifier: Modifier, onPlay: () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(track.title, fontFamily = EditorialFont, fontSize = 36.sp, lineHeight = 40.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayControl(playing, buffering, size = 54.dp, onClick = onPlay)
    }
}
