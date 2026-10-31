package io.github.yyan115.opentune.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import io.github.yyan115.opentune.BuildConfig
import androidx.browser.customtabs.CustomTabsIntent
import io.github.yyan115.opentune.core.*

@Composable
fun ConnectCard(onLogin: () -> Unit) {
    OutlinedCard(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OpenTuneIcons.Waves, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Connect TIDAL", style = MaterialTheme.typography.titleSmall)
                Text("Stream and browse your collection", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(OpenTuneIcons.ChevronRight, null)
        }
    }
}

@UnstableApi
@Composable
fun SearchScreen(state: AppState, vm: MainViewModel, onLogin: () -> Unit, onMenu: (Track) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = rememberLazyListState()
    LaunchedEffect(filter) { filters.animateScrollToItem(filter) }
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.searchQuery
    val local = remember(state.local, query) { if (query.isBlank()) emptyList() else state.local.filter {
        it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
    } }
    val songs = (local + if (state.settings.offline) emptyList() else state.search.tracks).distinctBy(Track::id)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item { OutlinedTextField(query, vm::search, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            placeholder = { Text("Tracks, albums, artists") }, leadingIcon = { Icon(OpenTuneIcons.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { vm.search("") }) { Icon(OpenTuneIcons.Close, "Clear search") } },
            singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })) }
        if (query.isBlank()) {
            item { EmptyState(OpenTuneIcons.Search, "Find something to play", "Search your audio files${if (state.signedIn && !state.settings.offline) " and TIDAL" else ""} by track, album, or artist.") }
            if (!state.signedIn && !state.settings.offline) item { ConnectCard(onLogin) }
        } else {
            item { LazyRow(state = filters, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(listOf("Tracks", "Albums", "Artists", "Playlists")) { index, label -> FilterTab(filter == index, { filter = index }, label) }
            } }
            if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
            state.searchError?.let { error -> item { ErrorCard(error, vm::retrySearch) } }
            if (filter == 0) {
                items(songs, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id,
                    onPlay = { keyboard?.hide(); vm.play(songs, songs.indexOf(track)) }, onMenu = { onMenu(track) }) }
                if (songs.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                    "No tracks found", if (!state.signedIn) "Search is limited to files on your device until you connect TIDAL." else "Try another title or artist name.",
                    if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
            } else {
                val releases = if (state.settings.offline) emptyList() else when (filter) { 1 -> state.search.albums; 2 -> state.search.artists; else -> state.search.playlists }
                items(releases, key = Release::id) { release -> ReleaseRow(release) { keyboard?.hide(); vm.open(release) } }
                if (releases.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                    "No results", if (!state.signedIn) "Connect TIDAL to search its catalogue." else "Try another search.", if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
            }
        }
    }
}

@Composable
fun ReleaseRow(release: Release, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(release.artwork, null, Modifier.size(60.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(release.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(release.artist.ifBlank { release.subtitle }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(OpenTuneIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.releaseGrid(releases: List<Release>, columns: Int, onOpen: (Release) -> Unit) {
    items(releases.chunked(columns), key = { row -> row.joinToString(":") { it.id } }) { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            row.forEach { release ->
                Column(Modifier.weight(1f).clickable { onOpen(release) }.padding(bottom = 6.dp)) {
                    Artwork(release.artwork, null, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(release.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(release.artist.ifBlank { release.subtitle }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@UnstableApi
@Composable
fun LibraryScreen(state: AppState, vm: MainViewModel, onImport: () -> Unit, onLogin: () -> Unit, onNewPlaylist: () -> Unit,
    onMenu: (Track) -> Unit, filter: Int, onFilter: (Int) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    val filters = rememberLazyListState()
    LaunchedEffect(filter) { filters.animateScrollToItem(filter) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { LazyRow(modifier = Modifier.testTag("library-filters"), state = filters, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("Saved tracks", "Albums", "Playlists", "On device")) { index, label -> FilterTab(filter == index, { onFilter(index) }, label) }
        } }
        if (state.syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) }
        state.error?.let { item { ErrorCard(it, vm::sync) } }
        when (filter) {
            0 -> {
                val tracks = if (state.settings.offline) state.favorites.filter(Track::isLocal) else state.favorites
                item { SectionTitle("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}") }
                items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }) }
                if (tracks.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.FavoriteBorder, "No saved tracks", "Save tracks from the player or connect your TIDAL collection.", if (state.signedIn) null else "Connect TIDAL", onLogin) }
                if (state.hasMoreFavorites) item { TextButton(onClick = { vm.moreFavorites() }, modifier = Modifier.fillMaxWidth()) { Text("Load more tracks") } }
            }
            1 -> {
                releaseGrid(state.albums, columns, vm::open)
                if (state.albums.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.Album, "No saved albums", "Albums saved in TIDAL will appear here.", if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
            }
            2 -> {
                item { SectionTitle("On this device", "New playlist", onNewPlaylist) }
                items(state.localPlaylists, key = LocalPlaylist::id) { playlist ->
                    ReleaseRow(Release(playlist.id, playlist.title, "${playlist.trackIds.size} tracks", type = ReleaseType.PLAYLIST)) {
                        vm.open(Release(playlist.id, playlist.title, "On this device", type = ReleaseType.PLAYLIST))
                    }
                }
                if (state.localPlaylists.isEmpty()) item { Text("Create playlists for your local and TIDAL tracks.", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.playlists.isNotEmpty()) item { SectionTitle("TIDAL playlists") }
                items(state.playlists, key = Release::id) { ReleaseRow(it) { vm.open(it) } }
            }
            3 -> {
                item { SectionTitle("${state.local.size} ${if (state.local.size == 1) "track" else "tracks"}", "Import", onImport) }
                items(state.local, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(state.local, state.local.indexOf(track)) }, { onMenu(track) }) }
                if (state.local.isEmpty()) item { EmptyState(OpenTuneIcons.AudioFile, "No audio files yet", "Import FLAC, MP3, AAC, or other supported audio files. Your original files stay where they are.", "Import audio", onImport) }
            }
        }
    }
}

@UnstableApi
@Composable
fun DetailScreen(state: AppState, vm: MainViewModel, onMenu: (Track, Int) -> Unit) {
    val release = state.detail ?: return
    val largeText = LocalDensity.current.fontScale > 1.3f
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(release.artwork, null, Modifier.size(if (largeText) 120.dp else 248.dp))
            Text(release.title, Modifier.padding(top = 22.dp).fillMaxWidth(), style = MaterialTheme.typography.headlineLarge)
            Text(release.artist, Modifier.padding(top = 6.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuneButton(onClick = { vm.play(state.detailTracks) }, enabled = state.detailTracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                TuneTonalButton(onClick = { vm.play(state.detailTracks); if (!state.playback.shuffle) vm.toggleShuffle() }, enabled = state.detailTracks.isNotEmpty()) {
                    Icon(OpenTuneIcons.Shuffle, "Shuffle play")
                }
                if (state.detailTracks.any { !it.isLocal }) TuneOutlinedButton(onClick = { state.detailTracks.forEach { vm.download(it) } }) { Icon(OpenTuneIcons.Download, "Download all tracks") }
            }
        } }
        if (state.detailLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
        state.error?.let { item { ErrorCard(it) { vm.open(release) } } }
        itemsIndexed(state.detailTracks, key = { index, track -> "$index:${track.id}" }) { index, track -> TrackRow(track, state.playback.track?.id == track.id,
            { vm.play(state.detailTracks, index) }, { onMenu(track, index) }, index) }
        if (!state.detailLoading && state.detailTracks.isEmpty() && state.error == null) item { EmptyState(OpenTuneIcons.PlaylistPlay, "No tracks", "Tracks added to this playlist will appear here.") }
        if (state.detailAlbums.isNotEmpty()) item { SectionTitle("Albums") }
        items(state.detailAlbums, key = Release::id) { ReleaseRow(it) { vm.open(it) } }
    }
}

@UnstableApi
@Composable
fun DownloadsScreen(state: AppState, vm: MainViewModel, onMenu: (Track) -> Unit) {
    val active = state.downloads.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
    val tracks = state.local.filterNot { it.id.startsWith("local:") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Available without a connection", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.downloads.any { it.state.isFinished }) item { TextButton(onClick = vm::clearDownloadHistory, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear download history") } }
        items(active, key = { it.id }) { work ->
            val trackId = work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")
            val track = state.downloadTracks[trackId]
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track?.title ?: "TIDAL download", style = MaterialTheme.typography.titleSmall)
                        Text(when (work.state) {
                            WorkInfo.State.RUNNING -> "${work.progress.getInt("progress", 0)}% downloaded"
                            WorkInfo.State.FAILED -> work.outputData.getString("error") ?: "Download failed"
                            WorkInfo.State.BLOCKED -> "Waiting"
                            else -> if (state.settings.downloadOnWifi) "Queued · Wi-Fi only" else "Queued"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!work.state.isFinished) IconButton(onClick = { vm.cancelDownload(work) }) { Icon(OpenTuneIcons.Close, "Cancel download") }
                    else if (track != null) TextButton(onClick = { vm.download(track) }) { Text("Retry") }
                }
                if (work.state == WorkInfo.State.RUNNING) LinearProgressIndicator(progress = { work.progress.getInt("progress", 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        if (tracks.isNotEmpty()) item { SectionTitle("${tracks.size} saved ${if (tracks.size == 1) "track" else "tracks"}") }
        items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }) }
        if (tracks.isEmpty() && active.isEmpty()) item { EmptyState(OpenTuneIcons.Download, "No downloads yet", "Use Download in a track's menu to save it here. By default, downloads wait for Wi-Fi.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun LoginSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val openBrowser = {
        try {
            val pending = vm.beginBrowserLogin()
            CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
                .launchUrl(context, Uri.parse(pending.authorizationUrl))
        } catch (_: Exception) { vm.notify("A browser is needed to sign in. Install one and try again.") }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connect TIDAL", style = MaterialTheme.typography.headlineSmall)
            Text("Your browser handles sign-in and returns to OpenTune automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.signingIn) LinearProgressIndicator(Modifier.fillMaxWidth())
            TuneButton(onClick = openBrowser, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in to TIDAL") }
            Text("Your password stays in your browser. OpenTune does not receive it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@UnstableApi
@Composable
fun SettingsScreen(state: AppState, vm: MainViewModel, onLogin: () -> Unit) {
    val context = LocalContext.current
    var quality by remember { mutableStateOf(false) }
    var mobile by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionTitle("Account") }
        item { SettingRow(OpenTuneIcons.AccountCircle, if (state.signedIn) "TIDAL connected" else "Connect TIDAL",
            if (state.signedIn) "Collection and streaming enabled" else "A subscription is required for full-track streaming",
            onClick = if (state.signedIn) ({ signOut = true }) else onLogin,
            trailing = { Icon(if (state.signedIn) OpenTuneIcons.CheckCircle else OpenTuneIcons.ChevronRight, null) }) }
        item { SectionTitle("Playback") }
        item { SettingRow(OpenTuneIcons.Wifi, "Wi-Fi audio quality", state.settings.quality.label, { quality = true }) }
        item { SettingRow(OpenTuneIcons.SignalCellularAlt, "Mobile audio quality", state.settings.mobileQuality.label, { mobile = true }) }
        item { SettingRow(OpenTuneIcons.CloudOff, "Offline mode", "Play only tracks saved on this device", trailing = {
            Switch(state.settings.offline, { value -> vm.settings { it.copy(offline = value) } })
        }) }
        item { SettingRow(OpenTuneIcons.Download, "Download over Wi-Fi only", "Downloads stay queued on mobile data", trailing = {
            Switch(state.settings.downloadOnWifi, { value -> vm.settings { it.copy(downloadOnWifi = value) } })
        }) }
        item { SectionTitle("Appearance") }
        item { SettingRow(OpenTuneIcons.Contrast, "Theme", "${colorTheme(state.settings.colorTheme).name} · ${state.settings.theme.replaceFirstChar(Char::uppercase)}", { theme = true }) }
        item { SectionTitle("About") }
        item { SettingRow(OpenTuneIcons.Info, "OpenTune ${BuildConfig.VERSION_NAME}", "Unofficial open-source TIDAL client") }
        item { SettingRow(OpenTuneIcons.Code, "Source code", "github.com/yyan115/tidal-app", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yyan115/tidal-app"))) }
        }) }
        item { SettingRow(OpenTuneIcons.PrivacyTip, "Privacy", "No ads, analytics, or account proxy. Streaming connects directly to TIDAL.") }
        item { SettingRow(OpenTuneIcons.Description, "Open-source licenses", "GPL-3.0-or-later and dependency notices", { licenses = true }) }
    }
    if (licenses) LicensesDialog { licenses = false }
    if (quality || mobile) AlertDialog(onDismissRequest = { quality = false; mobile = false }, title = { Text(if (mobile) "Mobile audio quality" else "Wi-Fi audio quality") },
        text = { Column { AudioQuality.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable {
            if (mobile) vm.settings { it.copy(mobileQuality = value) } else vm.settings { it.copy(quality = value) }
            quality = false; mobile = false
        }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(if (mobile) state.settings.mobileQuality == value else state.settings.quality == value, onClick = null)
            Text(value.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        } } } }, confirmButton = {})
    if (theme) AppearanceSheet(state.settings, { next -> vm.settings { next } }) { theme = false }
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text("Disconnect TIDAL?") }, text = { Text("Audio saved on this device stays in your library.") },
        confirmButton = { TextButton(onClick = { vm.signOut(); signOut = false }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { signOut = false }) { Text("Cancel") } })
}
