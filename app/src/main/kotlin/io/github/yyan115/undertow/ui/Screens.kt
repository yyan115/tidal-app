package io.github.yyan115.undertow.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import io.github.yyan115.undertow.BuildConfig
import io.github.yyan115.undertow.core.*

@Composable
private fun ConnectCard(onLogin: () -> Unit) {
    OutlinedCard(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Waves, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Connect TIDAL", style = MaterialTheme.typography.titleSmall)
                Text("Stream and browse your collection", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@UnstableApi
@Composable
fun ListenScreen(state: AppState, vm: MainViewModel, onImport: () -> Unit, onLogin: () -> Unit, onLibrary: () -> Unit, onMenu: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        if (!state.signedIn) item { ConnectCard(onLogin) }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onImport) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import audio") }
                OutlinedButton(onClick = onLibrary) { Text("Your library") }
            }
        }
        if (state.recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            items(state.recent.take(8), key = { "recent:${it.id}" }) { track -> TrackRow(track, state.playback.track?.id == track.id,
                onPlay = { vm.play(state.recent, state.recent.indexOf(track)) }, onMenu = { onMenu(track) }) }
        } else item { EmptyState(Icons.Outlined.Headphones, "Nothing played yet", "Play a track from your library or search TIDAL.") }
        if (state.albums.isNotEmpty()) {
            item { SectionTitle("Saved albums", "View all", onLibrary) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.albums.take(12), key = Release::id) { ReleaseCard(it) { vm.open(it) } }
            } }
        }
        if (state.local.isNotEmpty()) {
            item { SectionTitle("On your device", "View all", onLibrary) }
            items(state.local.take(4), key = { "device:${it.id}" }) { track -> TrackRow(track, state.playback.track?.id == track.id,
                onPlay = { vm.play(state.local, state.local.indexOf(track)) }, onMenu = { onMenu(track) }) }
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
    val songs = (local + state.search.tracks).distinctBy(Track::id)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item { OutlinedTextField(query, vm::search, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            placeholder = { Text("Tracks, albums, artists") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { vm.search("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
            singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })) }
        if (query.isBlank()) {
            item { EmptyState(Icons.Outlined.Search, "Find something to play", "Search your audio files${if (state.signedIn) " and TIDAL" else ""} by track, album, or artist.") }
            if (!state.signedIn) item { ConnectCard(onLogin) }
        } else {
            item { LazyRow(state = filters, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(listOf("Tracks", "Albums", "Artists", "Playlists")) { index, label -> FilterChip(filter == index, { filter = index }, label = { Text(label) }) }
            } }
            if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
            state.error?.let { error -> item { ErrorCard(error) { vm.search(query + " "); vm.search(query) } } }
            if (filter == 0) {
                items(songs, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id,
                    onPlay = { keyboard?.hide(); vm.play(songs, songs.indexOf(track)) }, onMenu = { onMenu(track) }) }
                if (songs.isEmpty() && !state.searching && state.error == null) item { EmptyState(Icons.Outlined.SearchOff,
                    "No tracks found", if (!state.signedIn) "Search is limited to files on your device until you connect TIDAL." else "Try another title or artist name.",
                    if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
            } else {
                val releases = when (filter) { 1 -> state.search.albums; 2 -> state.search.artists; else -> state.search.playlists }
                items(releases, key = Release::id) { release -> ReleaseRow(release) { keyboard?.hide(); vm.open(release) } }
                if (releases.isEmpty() && !state.searching && state.error == null) item { EmptyState(Icons.Outlined.SearchOff,
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
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@UnstableApi
@Composable
fun LibraryScreen(state: AppState, vm: MainViewModel, onImport: () -> Unit, onLogin: () -> Unit, onNewPlaylist: () -> Unit, onMenu: (Track) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = rememberLazyListState()
    LaunchedEffect(filter) { filters.animateScrollToItem(filter) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { LazyRow(modifier = Modifier.testTag("library-filters"), state = filters, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("Saved tracks", "Albums", "Playlists", "On device")) { index, label -> FilterChip(filter == index, { filter = index }, label = { Text(label) }) }
        } }
        if (state.syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) }
        state.error?.let { item { ErrorCard(it, vm::sync) } }
        when (filter) {
            0 -> {
                val tracks = if (state.settings.offline) state.favorites.filter(Track::isLocal) else state.favorites
                item { SectionTitle("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}") }
                items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }) }
                if (tracks.isEmpty() && !state.syncing) item { EmptyState(Icons.Outlined.FavoriteBorder, "No saved tracks", "Save tracks from the player or connect your TIDAL collection.", if (state.signedIn) null else "Connect TIDAL", onLogin) }
                if (state.hasMoreFavorites) item { TextButton(onClick = { vm.moreFavorites() }, modifier = Modifier.fillMaxWidth()) { Text("Load more tracks") } }
            }
            1 -> {
                items(state.albums, key = Release::id) { ReleaseRow(it) { vm.open(it) } }
                if (state.albums.isEmpty() && !state.syncing) item { EmptyState(Icons.Outlined.Album, "No saved albums", "Albums saved in TIDAL will appear here.", if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
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
                if (state.local.isEmpty()) item { EmptyState(Icons.Outlined.AudioFile, "No audio files yet", "Import FLAC, MP3, AAC, or other supported audio files. Your original files stay where they are.", "Import audio", onImport) }
            }
        }
    }
}

@UnstableApi
@Composable
fun DetailScreen(state: AppState, vm: MainViewModel, onMenu: (Track, Int) -> Unit) {
    val release = state.detail ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(release.artwork, null, Modifier.size(192.dp))
            Text(release.title, Modifier.padding(top = 22.dp).fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(release.artist, Modifier.padding(top = 6.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.play(state.detailTracks) }, enabled = state.detailTracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                FilledTonalButton(onClick = { vm.play(state.detailTracks); if (!state.playback.shuffle) vm.toggleShuffle() }, enabled = state.detailTracks.isNotEmpty()) {
                    Icon(Icons.Outlined.Shuffle, "Shuffle play")
                }
                if (state.detailTracks.any { !it.isLocal }) OutlinedButton(onClick = { state.detailTracks.forEach { vm.download(it) } }) { Icon(Icons.Outlined.Download, "Download all tracks") }
            }
        } }
        if (state.detailLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
        state.error?.let { item { ErrorCard(it) { vm.open(release) } } }
        itemsIndexed(state.detailTracks, key = { index, track -> "$index:${track.id}" }) { index, track -> TrackRow(track, state.playback.track?.id == track.id,
            { vm.play(state.detailTracks, index) }, { onMenu(track, index) }, index) }
        if (!state.detailLoading && state.detailTracks.isEmpty() && state.error == null) item { EmptyState(Icons.AutoMirrored.Outlined.PlaylistPlay, "No tracks", "Tracks added to this playlist will appear here.") }
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
                    Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track?.title ?: "TIDAL download", style = MaterialTheme.typography.titleSmall)
                        Text(when (work.state) {
                            WorkInfo.State.RUNNING -> "${work.progress.getInt("progress", 0)}% downloaded"
                            WorkInfo.State.FAILED -> work.outputData.getString("error") ?: "Download failed"
                            WorkInfo.State.BLOCKED -> "Waiting"
                            else -> if (state.settings.downloadOnWifi) "Queued · Wi-Fi only" else "Queued"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!work.state.isFinished) IconButton(onClick = { vm.cancelDownload(work) }) { Icon(Icons.Outlined.Close, "Cancel download") }
                    else if (track != null) TextButton(onClick = { vm.download(track) }) { Text("Retry") }
                }
                if (work.state == WorkInfo.State.RUNNING) LinearProgressIndicator(progress = { work.progress.getInt("progress", 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        if (tracks.isNotEmpty()) item { SectionTitle("${tracks.size} saved ${if (tracks.size == 1) "track" else "tracks"}") }
        items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }) }
        if (tracks.isEmpty() && active.isEmpty()) item { EmptyState(Icons.Outlined.Download, "No downloads yet", "Use Download in a track's menu to save it here. By default, downloads wait for Wi-Fi.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun LoginSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var paste by remember { mutableStateOf(false) }
    var callback by remember { mutableStateOf("") }
    val openBrowser = {
        try {
            val pending = state.pkceLogin?.takeIf { it.expiresAt > System.currentTimeMillis() } ?: vm.beginBrowserLogin()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pending.authorizationUrl)))
        } catch (_: Exception) { vm.notify("A browser is needed to sign in. Install one and try again.") }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connect TIDAL", style = MaterialTheme.typography.headlineSmall)
            Text("Sign in on TIDAL's website. Your password stays with TIDAL.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.signingIn && state.login == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.login == null) {
                if (state.pkceLogin != null) {
                    Text("After signing in, share the final page from your browser to Undertow. You can also paste its address below.")
                    FilledTonalButton(onClick = { paste = true }, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) { Text("Paste final sign-in link") }
                }
                Button(onClick = openBrowser, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) { Text(if (state.pkceLogin == null) "Sign in in browser" else "Reopen browser") }
                TextButton(onClick = vm::beginLogin, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) { Text("Use a device code instead") }
                Text("Browser sign-in supports lossless streaming. Device-code access can be limited to AAC by TIDAL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.login?.let { link ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your sign-in code", style = MaterialTheme.typography.labelMedium)
                        Text(link.userCode, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.verificationUrl))) }
                    catch (_: Exception) { vm.notify("No browser is installed. Open link.tidal.com on another device and enter the code.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Open TIDAL sign-in") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Waiting for authorization", style = MaterialTheme.typography.bodySmall)
                }
                Text("You can also open link.tidal.com on another device and enter this code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openBrowser) { Text("Use browser sign-in instead") }
            }
        }
    }
    if (paste) AlertDialog(onDismissRequest = { paste = false; callback = "" }, title = { Text("Finish TIDAL sign-in") }, text = {
        OutlinedTextField(callback, { callback = it }, label = { Text("Final TIDAL page address") }, maxLines = 4,
            supportingText = { Text("The address starts with https://tidal.com/android/login/auth") })
    }, confirmButton = { TextButton(enabled = callback.isNotBlank(), onClick = { vm.completeBrowserLogin(callback); callback = ""; paste = false }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { callback = ""; paste = false }) { Text("Cancel") } })
}

@UnstableApi
@Composable
fun SettingsScreen(state: AppState, vm: MainViewModel, onLogin: () -> Unit) {
    val context = LocalContext.current
    var quality by remember { mutableStateOf(false) }
    var mobile by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionTitle("Account") }
        item { SettingRow(Icons.Outlined.AccountCircle, if (state.signedIn) "TIDAL connected" else "Connect TIDAL",
            if (state.signedIn) "Collection and streaming enabled" else "A subscription is required for full-track streaming",
            onClick = if (state.signedIn) ({ signOut = true }) else onLogin,
            trailing = { Icon(if (state.signedIn) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight, null) }) }
        item { SectionTitle("Playback") }
        item { SettingRow(Icons.Outlined.Wifi, "Wi-Fi audio quality", state.settings.quality.label, { quality = true }) }
        item { SettingRow(Icons.Outlined.SignalCellularAlt, "Mobile audio quality", state.settings.mobileQuality.label, { mobile = true }) }
        item { SettingRow(Icons.Outlined.CloudOff, "Offline mode", "Play only tracks saved on this device", trailing = {
            Switch(state.settings.offline, { value -> vm.settings { it.copy(offline = value) } })
        }) }
        item { SettingRow(Icons.Outlined.Download, "Download over Wi-Fi only", "Downloads stay queued on mobile data", trailing = {
            Switch(state.settings.downloadOnWifi, { value -> vm.settings { it.copy(downloadOnWifi = value) } })
        }) }
        item { SectionTitle("Appearance") }
        item { SettingRow(Icons.Outlined.Contrast, "Theme", state.settings.theme.replaceFirstChar(Char::uppercase), { theme = true }) }
        item { SectionTitle("About") }
        item { SettingRow(Icons.Outlined.Info, "Undertow ${BuildConfig.VERSION_NAME}", "Independent, open-source music player") }
        item { SettingRow(Icons.Outlined.Code, "Source code", "github.com/yyan115/tidal-app", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yyan115/tidal-app"))) }
        }) }
        item { SettingRow(Icons.Outlined.PrivacyTip, "Privacy", "No ads, analytics, or account proxy. Streaming connects directly to TIDAL.") }
        item { SettingRow(Icons.Outlined.Key, "Client configuration", "Use a different TIDAL OAuth client", { client = true }) }
    }
    if (quality || mobile) AlertDialog(onDismissRequest = { quality = false; mobile = false }, title = { Text(if (mobile) "Mobile audio quality" else "Wi-Fi audio quality") },
        text = { Column { AudioQuality.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable {
            if (mobile) vm.settings { it.copy(mobileQuality = value) } else vm.settings { it.copy(quality = value) }
            quality = false; mobile = false
        }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(if (mobile) state.settings.mobileQuality == value else state.settings.quality == value, onClick = null)
            Text(value.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        } } } }, confirmButton = {})
    if (theme) AlertDialog(onDismissRequest = { theme = false }, title = { Text("Theme") }, text = { Column {
        listOf("system", "light", "dark").forEach { value -> Row(Modifier.fillMaxWidth().clickable { vm.settings { it.copy(theme = value) }; theme = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(state.settings.theme == value, onClick = null); Text(value.replaceFirstChar(Char::uppercase), Modifier.padding(start = 12.dp))
        } }
    } }, confirmButton = {})
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text("Disconnect TIDAL?") }, text = { Text("Audio saved on this device stays in your library.") },
        confirmButton = { TextButton(onClick = { vm.signOut(); signOut = false }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { signOut = false }) { Text("Cancel") } })
    if (client) AlertDialog(onDismissRequest = { client = false }, title = { Text("Client configuration") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Changing the OAuth client disconnects the current TIDAL session.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") }, singleLine = true)
        OutlinedTextField(clientSecret, { clientSecret = it }, label = { Text("Client secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
    } }, confirmButton = { TextButton(enabled = clientId.isNotBlank() && clientSecret.isNotBlank(), onClick = { vm.configureClient(clientId, clientSecret); clientId = ""; clientSecret = ""; client = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { client = false; clientId = ""; clientSecret = "" }) { Text("Cancel") } })
}
