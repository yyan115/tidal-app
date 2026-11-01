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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun SearchScreen(state: AppState, vm: MainViewModel, onLogin: () -> Unit, onMenu: (Track) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = rememberLazyListState()
    LaunchedEffect(filter) { filters.animateScrollToItem(filter) }
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.searchQuery
    val pages: PagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val entries by pages.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = PageContext(state, vm, pages, scope, onMenu, "Search")
    val online = state.signedIn && !state.settings.offline
    LaunchedEffect(online) { if (online) pages.load(PageRoute.Explore) }
    val local = remember(state.local, query) { if (query.isBlank()) emptyList() else state.local.filter {
        it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
    } }
    val songs = (local + if (state.settings.offline) emptyList() else state.search.tracks).distinctBy(Track::id)
    val result = state.search
    val open: (Release) -> Unit = { release -> vm.rememberSearch(query); keyboard?.hide(); vm.open(release) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item { OutlinedTextField(query, vm::search, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            placeholder = { Text("Tracks, albums, artists") }, leadingIcon = { Icon(OpenTuneIcons.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { vm.search("") }) { Icon(OpenTuneIcons.Close, "Clear search") } },
            singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.rememberSearch(query) })) }
        if (query.isBlank()) {
            if (state.recentSearches.isNotEmpty()) {
                item { SectionTitle("Recent searches", "Clear", vm::clearSearches) }
                item { FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentSearches.forEach { LinkPill(it) { vm.search(it) } }
                } }
            }
            val explore = entries[PageRoute.Explore.key]?.page
            if (online && explore != null) pageContent(explore, entries.getValue(PageRoute.Explore.key), PageRoute.Explore.key, context, viewAll = false)
            else if (state.recentSearches.isEmpty()) item { EmptyState(OpenTuneIcons.Search, "Find something to play", "Search your audio files${if (state.signedIn && !state.settings.offline) " and TIDAL" else ""} by track, album, or artist.") }
            if (!state.signedIn && !state.settings.offline) item { ConnectCard(onLogin) }
        } else {
            item { LazyRow(state = filters, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(listOf("Top", "Tracks", "Albums", "Artists", "Playlists", "Videos")) { index, label -> FilterTab(filter == index, { filter = index }, label) }
            } }
            if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
            state.searchError?.let { error -> item { ErrorCard(error, vm::retrySearch) } }
            val play: (Track) -> Unit = { track -> keyboard?.hide(); vm.rememberSearch(query); vm.play(songs, songs.indexOf(track), "Search") }
            when (filter) {
                0 -> {
                    result.topRelease?.let { top -> item(key = "top-hit") {
                        UpperLabel("Top result", Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        ReleaseRow(top) { open(top) }
                    } }
                    result.topTrack?.let { top -> item(key = "top-track") {
                        UpperLabel("Top result", Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        TrackRow(top, state.playback.track?.id == top.id, onPlay = { play(top) }, onMenu = { onMenu(top) })
                    } }
                    if (songs.isNotEmpty()) {
                        item { SectionTitle("Tracks", if (songs.size > 5) "View all" else null) { filter = 1 } }
                        items(songs.take(5), key = { "top:${it.id}" }) { track -> TrackRow(track, state.playback.track?.id == track.id, onPlay = { play(track) }, onMenu = { onMenu(track) }) }
                    }
                    listOf("Albums" to result.albums, "Artists" to result.artists, "Playlists" to result.playlists, "Videos" to result.videos).forEachIndexed { position, (title, list) ->
                        if (list.isNotEmpty()) item(key = "top-$title") {
                            Column {
                                SectionTitle(title, "View all") { filter = position + 2 }
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(list.take(12), key = { index, release -> "${release.type}:${release.id}:$index" }) { _, release -> PageCard(release) { open(release) } }
                                }
                            }
                        }
                    }
                    if (songs.isEmpty() && result.albums.isEmpty() && result.artists.isEmpty() && result.playlists.isEmpty() && result.videos.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        "Nothing found", if (!state.signedIn) "Search is limited to files on your device until you connect TIDAL." else "Try another title or artist name.",
                        if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
                }
                1 -> {
                    items(songs, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, onPlay = { play(track) }, onMenu = { onMenu(track) }) }
                    if (songs.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        "No tracks found", if (!state.signedIn) "Search is limited to files on your device until you connect TIDAL." else "Try another title or artist name.",
                        if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
                }
                else -> {
                    val releases = if (state.settings.offline) emptyList() else when (filter) { 2 -> result.albums; 3 -> result.artists; 4 -> result.playlists; else -> result.videos }
                    items(releases, key = { "${it.type}:${it.id}" }) { release -> ReleaseRow(release) { open(release) } }
                    if (releases.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        "No results", if (!state.signedIn) "Connect TIDAL to search its catalogue." else "Try another search.", if (!state.signedIn) "Connect TIDAL" else null, onLogin) }
                }
            }
        }
    }
}

@Composable
fun ReleaseRow(release: Release, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(release.artwork, null, Modifier.size(60.dp).then(if (release.type == ReleaseType.ARTIST) Modifier.clip(androidx.compose.foundation.shape.CircleShape) else Modifier))
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

private enum class LibrarySort(val label: String) { Recent("Recently added"), Title("Title"), Artist("Artist") }

@UnstableApi
@Composable
fun LibraryScreen(state: AppState, vm: MainViewModel, onImport: () -> Unit, onLogin: () -> Unit, onNewPlaylist: () -> Unit,
    onMenu: (Track) -> Unit, filter: Int, onFilter: (Int) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    val filters = rememberLazyListState()
    LaunchedEffect(filter) { filters.animateScrollToItem(filter) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Recent) }
    var text by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val pages: PagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val entries by pages.state.collectAsStateWithLifecycle()
    val mixes = remember { PageRoute.path("pages/my_collection_my_mixes", "Mixes") }
    val scope = rememberCoroutineScope()
    val context = PageContext(state, vm, pages, scope, onMenu, "Mixes")
    LaunchedEffect(filter, state.signedIn, state.settings.offline) { if (filter == 5 && state.signedIn && !state.settings.offline) pages.load(mixes) }
    fun matches(vararg fields: String) = text.isBlank() || fields.any { it.contains(text.trim(), ignoreCase = true) }
    fun <T> List<T>.ordered(title: (T) -> String, artist: (T) -> String): List<T> = when (sort) {
        LibrarySort.Recent -> this
        LibrarySort.Title -> sortedBy { title(it).lowercase() }
        LibrarySort.Artist -> sortedWith(compareBy({ artist(it).lowercase() }, { title(it).lowercase() }))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { LazyRow(modifier = Modifier.testTag("library-filters"), state = filters, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("Saved tracks", "Albums", "Artists", "Playlists", "On device", "Mixes")) { index, label -> FilterTab(filter == index, { onFilter(index) }, label) }
        } }
        if (state.syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) }
        state.error?.let { item { ErrorCard(it, vm::sync) } }
        if (filter in 0..2) item(key = "tools") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Filter") },
                    leadingIcon = { Icon(OpenTuneIcons.Search, null, Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.large,
                    trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(OpenTuneIcons.Close, "Clear filter", Modifier.size(18.dp)) } })
                if (filter != 2) Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(OpenTuneIcons.Sort, "Sort by ${sort.label}") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        LibrarySort.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortMenu = false },
                            trailingIcon = { if (sort == option) Icon(OpenTuneIcons.Check, null, Modifier.size(18.dp)) }) }
                    }
                }
            }
        }
        when (filter) {
            0 -> {
                val all = if (state.settings.offline) state.favorites.filter(Track::isLocal) else state.favorites
                val tracks = all.filter { matches(it.title, it.artist, it.album) }.ordered({ it.title }, { it.artist })
                item { SectionTitle("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", if (tracks.size > 1) "Shuffle" else null) { vm.playTracks(tracks, shuffle = true) } }
                items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track), "Saved tracks") }, { onMenu(track) }) }
                if (tracks.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.FavoriteBorder, if (text.isBlank()) "No saved tracks" else "No matches",
                    if (text.isBlank()) "Save tracks from the player or connect your TIDAL collection." else "Try a different filter.", if (state.signedIn || text.isNotBlank()) null else "Connect TIDAL", onLogin) }
                if (state.hasMoreFavorites && text.isBlank()) item { TextButton(onClick = { vm.moreFavorites() }, modifier = Modifier.fillMaxWidth()) { Text("Load more tracks") } }
            }
            1 -> {
                val albums = state.albums.filter { matches(it.title, it.artist) }.ordered({ it.title }, { it.artist })
                releaseGrid(albums, columns, vm::open)
                if (albums.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.Album, if (text.isBlank()) "No saved albums" else "No matches",
                    if (text.isBlank()) "Albums saved in TIDAL will appear here." else "Try a different filter.", if (!state.signedIn && text.isBlank()) "Connect TIDAL" else null, onLogin) }
            }
            2 -> {
                val artists = state.artists.filter { matches(it.title) }.sortedBy { it.title.lowercase() }
                items(artists, key = { "artist:${it.id}" }) { ReleaseRow(it) { vm.open(it) } }
                if (artists.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.Person, if (text.isBlank()) "No followed artists" else "No matches",
                    if (text.isBlank()) "Artists you follow in TIDAL will appear here." else "Try a different filter.", if (!state.signedIn && text.isBlank()) "Connect TIDAL" else null, onLogin) }
            }
            3 -> {
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
            5 -> {
                val entry = entries[mixes.key]
                val page = entry?.page
                if (page != null) pageContent(page, entry, mixes.key, context, viewAll = true)
                else item(key = "mixes-state") {
                    when {
                        !state.signedIn -> EmptyState(OpenTuneIcons.Waves, "No mixes yet", "Connect TIDAL to see your mixes.", "Connect TIDAL", onLogin)
                        state.settings.offline -> EmptyState(OpenTuneIcons.CloudOff, "Mixes need a connection", "Turn off offline mode to see your mixes.")
                        entry?.error != null -> ErrorCard(entry.error) { pages.load(mixes, force = true) }
                        else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 1.5.dp) }
                    }
                }
            }
            else -> {
                item { SectionTitle("${state.local.size} ${if (state.local.size == 1) "track" else "tracks"}", "Import", onImport) }
                items(state.local, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(state.local, state.local.indexOf(track), "On device") }, { onMenu(track) }) }
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
            { vm.play(state.detailTracks, index, release.title) }, { onMenu(track, index) }, index) }
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
    var normalization by remember { mutableStateOf(false) }
    var equalizer by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf(false) }
    var scrobbling by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    val backup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val restore = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBackup) }
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
        item { SettingRow(OpenTuneIcons.GraphicEq, "Volume normalization", when (state.settings.normalization) { "track" -> "Track"; "album" -> "Album"; else -> "Off" }, { normalization = true }) }
        item { SettingRow(OpenTuneIcons.Equalizer, "Equalizer", if (state.settings.equalizer) "On" else "Off", { equalizer = true }) }
        item { SettingRow(OpenTuneIcons.Headphones, "Audio output", "See where the music plays", { output = true }) }
        if (android.os.Build.VERSION.SDK_INT >= 34) item { SettingRow(OpenTuneIcons.Waves, "Bit-perfect USB output", "Experimental. Sends audio to a USB DAC unchanged when the DAC supports it. Not tested with a DAC yet.", trailing = {
            Switch(state.settings.bitPerfect, { value -> vm.settings { it.copy(bitPerfect = value) } })
        }) }
        item { SectionTitle("Appearance") }
        item { SettingRow(OpenTuneIcons.Contrast, "Theme", "${colorTheme(state.settings.colorTheme).name} · ${state.settings.theme.replaceFirstChar(Char::uppercase)}", { theme = true }) }
        item { SectionTitle("Privacy and data") }
        item { SettingRow(OpenTuneIcons.Download, "Keep saved tracks offline", "Downloads your saved TIDAL tracks in the background, 50 at a time.", trailing = {
            Switch(state.settings.keepFavoritesOffline, { value -> vm.settings { it.copy(keepFavoritesOffline = value) } })
        }) }
        item { SettingRow(OpenTuneIcons.GraphicEq, "Scrobbling", if (state.settings.scrobble) "On · ${state.settings.scrobbleServer.removePrefix("https://")}" else "Off", { scrobbling = true }) }
        item { SettingRow(OpenTuneIcons.LibraryMusic, "Listening statistics", "Kept on this device", { stats = true }) }
        item { SettingRow(OpenTuneIcons.SaveAlt, "Back up data", "Settings, device playlists and listening history", { backup.launch("opentune-backup.json") }) }
        item { SettingRow(OpenTuneIcons.Refresh, "Restore data", "Merge a backup into this install", { restore.launch(arrayOf("application/json", "*/*")) }) }
        item { SettingRow(OpenTuneIcons.RemoveCircleOutline, "Clear listening history", "Removes statistics and recent plays", { clearHistory = true }) }
        item { SectionTitle("About") }
        item { SettingRow(OpenTuneIcons.Info, "OpenTune ${BuildConfig.VERSION_NAME}", "Unofficial open-source TIDAL client") }
        item { SettingRow(OpenTuneIcons.Code, "Source code", "github.com/yyan115/tidal-app", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yyan115/tidal-app"))) }
        }) }
        item { SettingRow(OpenTuneIcons.PrivacyTip, "Privacy", "No ads, analytics, or account proxy. Streaming connects directly to TIDAL.") }
        item { SettingRow(OpenTuneIcons.Description, "Open-source licenses", "GPL-3.0-or-later and dependency notices", { licenses = true }) }
    }
    if (licenses) LicensesDialog { licenses = false }
    if (normalization) NormalizationDialog(state.settings.normalization, { value -> vm.settings { it.copy(normalization = value) } }) { normalization = false }
    if (equalizer) EqualizerSheet(state.settings, { next -> vm.settings { next } }) { equalizer = false }
    if (output) AudioOutputDialog { output = false }
    if (scrobbling) ScrobbleDialog(state.settings, vm) { scrobbling = false }
    if (stats) StatsSheet(vm) { stats = false }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text("Clear listening history?") },
        text = { Text("Statistics and the recently played list are removed. Saved tracks and playlists stay.") },
        confirmButton = { TextButton(onClick = { vm.clearListeningHistory(); clearHistory = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
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
