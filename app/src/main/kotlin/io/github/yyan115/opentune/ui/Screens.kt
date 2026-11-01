package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.downloadErrorText
import androidx.compose.ui.res.pluralStringResource
import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalResources
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
                Text(stringResource(R.string.action_connect_tidal), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.connect_card_text), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
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
    val searchLabel = stringResource(R.string.nav_search)
    val tabTitles = listOf(R.string.search_tab_top, R.string.label_tracks, R.string.label_albums, R.string.label_artists, R.string.label_playlists, R.string.label_videos).map { stringResource(it) }
    val context = PageContext(state, vm, pages, scope, onMenu, searchLabel)
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
            placeholder = { Text(stringResource(R.string.search_hint)) }, leadingIcon = { Icon(OpenTuneIcons.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { vm.search("") }) { Icon(OpenTuneIcons.Close, stringResource(R.string.search_clear)) } },
            singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.rememberSearch(query) })) }
        if (query.isBlank()) {
            if (state.recentSearches.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.search_recent), stringResource(R.string.action_clear), vm::clearSearches) }
                item { FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentSearches.forEach { LinkPill(it) { vm.search(it) } }
                } }
            }
            val explore = entries[PageRoute.Explore.key]?.page
            if (online && explore != null) pageContent(explore, entries.getValue(PageRoute.Explore.key), PageRoute.Explore.key, context, viewAll = false)
            else if (state.recentSearches.isEmpty()) item { EmptyState(OpenTuneIcons.Search, stringResource(R.string.search_empty_title), stringResource(if (state.signedIn && !state.settings.offline) R.string.search_prompt_tidal else R.string.search_prompt_local)) }
            if (!state.signedIn && !state.settings.offline) item { ConnectCard(onLogin) }
        } else {
            item { LazyRow(state = filters, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tabTitles) { index, label -> FilterTab(filter == index, { filter = index }, label) }
            } }
            if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
            state.searchError?.let { error -> item { ErrorCard(error, vm::retrySearch) } }
            val play: (Track) -> Unit = { track -> keyboard?.hide(); vm.rememberSearch(query); vm.play(songs, songs.indexOf(track), searchLabel) }
            when (filter) {
                0 -> {
                    result.topRelease?.let { top -> item(key = "top-hit") {
                        UpperLabel(stringResource(R.string.search_top_result), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        ReleaseRow(top) { open(top) }
                    } }
                    result.topTrack?.let { top -> item(key = "top-track") {
                        UpperLabel(stringResource(R.string.search_top_result), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        TrackRow(top, state.playback.track?.id == top.id, onPlay = { play(top) }, onMenu = { onMenu(top) })
                    } }
                    if (songs.isNotEmpty()) {
                        item { SectionTitle(tabTitles[1], if (songs.size > 5) stringResource(R.string.action_view_all) else null) { filter = 1 } }
                        items(songs.take(5), key = { "top:${it.id}" }) { track -> TrackRow(track, state.playback.track?.id == track.id, onPlay = { play(track) }, onMenu = { onMenu(track) }) }
                    }
                    listOf(tabTitles[2] to result.albums, tabTitles[3] to result.artists, tabTitles[4] to result.playlists, tabTitles[5] to result.videos).forEachIndexed { position, (title, list) ->
                        if (list.isNotEmpty()) item(key = "top-$position") {
                            Column {
                                SectionTitle(title, stringResource(R.string.action_view_all)) { filter = position + 2 }
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(list.take(12), key = { index, release -> "${release.type}:${release.id}:$index" }) { _, release -> PageCard(release) { open(release) } }
                                }
                            }
                        }
                    }
                    if (songs.isEmpty() && result.albums.isEmpty() && result.artists.isEmpty() && result.playlists.isEmpty() && result.videos.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        stringResource(R.string.search_none), if (!state.signedIn) stringResource(R.string.search_limited) else stringResource(R.string.search_try_title),
                        if (!state.signedIn) stringResource(R.string.action_connect_tidal) else null, onLogin) }
                }
                1 -> {
                    items(songs, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, onPlay = { play(track) }, onMenu = { onMenu(track) }) }
                    if (songs.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        stringResource(R.string.search_no_tracks), if (!state.signedIn) stringResource(R.string.search_limited) else stringResource(R.string.search_try_title),
                        if (!state.signedIn) stringResource(R.string.action_connect_tidal) else null, onLogin) }
                }
                else -> {
                    val releases = if (state.settings.offline) emptyList() else when (filter) { 2 -> result.albums; 3 -> result.artists; 4 -> result.playlists; else -> result.videos }
                    items(releases, key = { "${it.type}:${it.id}" }) { release -> ReleaseRow(release) { open(release) } }
                    if (releases.isEmpty() && !state.searching && state.searchError == null) item { EmptyState(OpenTuneIcons.SearchOff,
                        stringResource(R.string.search_no_results), if (!state.signedIn) stringResource(R.string.search_connect) else stringResource(R.string.search_try_other), if (!state.signedIn) stringResource(R.string.action_connect_tidal) else null, onLogin) }
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

private val FolderStackSaver = androidx.compose.runtime.saveable.Saver<List<Pair<String, String>>, List<String>>(
    save = { stack -> stack.flatMap { listOf(it.first, it.second) } }, restore = { saved -> saved.chunked(2).map { it[0] to it[1] } })

private enum class LibrarySort(@androidx.annotation.StringRes val label: Int) { Recent(R.string.sort_recent), Title(R.string.sort_title), Artist(R.string.sort_artist) }

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
    val libraryTabs = listOf(R.string.library_saved_tracks, R.string.label_albums, R.string.label_artists, R.string.label_playlists, R.string.library_on_device, R.string.library_mixes).map { stringResource(it) }
    val onThisDevice = stringResource(R.string.library_on_this_device)
    val mixes = remember { PageRoute.path("pages/my_collection_my_mixes") }
    val scope = rememberCoroutineScope()
    val context = PageContext(state, vm, pages, scope, onMenu, libraryTabs[5])
    LaunchedEffect(filter, state.signedIn, state.settings.offline) { if (filter == 5 && state.signedIn && !state.settings.offline) pages.load(mixes) }
    val online = state.signedIn && !state.settings.offline
    var folderStack by rememberSaveable(stateSaver = FolderStackSaver) { mutableStateOf(emptyList<Pair<String, String>>()) }
    val openFolder = folderStack.lastOrNull()
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Release?>(null) }
    var deleting by remember { mutableStateOf<Release?>(null) }
    var moving by remember { mutableStateOf<Release?>(null) }
    LaunchedEffect(filter, openFolder?.first, state.signedIn) { if (filter == 3 && state.signedIn) vm.loadFolder(openFolder?.first ?: TidalClient.ROOT_FOLDER) }
    androidx.activity.compose.BackHandler(enabled = filter == 3 && openFolder != null) { folderStack = folderStack.dropLast(1) }
    if (newFolder) NameDialog(stringResource(R.string.folder_new), "", stringResource(R.string.action_create), vm::createFolder) { newFolder = false }
    renaming?.let { folder -> NameDialog(stringResource(R.string.folder_rename), folder.title, stringResource(R.string.action_save), { vm.renameFolder(folder, it) }) { renaming = null } }
    moving?.let { playlist -> MoveToFolderDialog(playlist, state.folders[TidalClient.ROOT_FOLDER].orEmpty(), { vm.moveToFolder(playlist, it) }) { moving = null } }
    deleting?.let { folder -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.folder_delete_title, folder.title)) },
        text = { Text(stringResource(R.string.folder_delete_text)) },
        confirmButton = { TextButton(onClick = { vm.deleteFolder(folder); deleting = null; if (openFolder?.first == folder.id) folderStack = emptyList() }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }) }
    fun matches(vararg fields: String) = text.isBlank() || fields.any { it.contains(text.trim(), ignoreCase = true) }
    fun <T> List<T>.ordered(title: (T) -> String, artist: (T) -> String): List<T> = when (sort) {
        LibrarySort.Recent -> this
        LibrarySort.Title -> sortedBy { title(it).lowercase() }
        LibrarySort.Artist -> sortedWith(compareBy({ artist(it).lowercase() }, { title(it).lowercase() }))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { LazyRow(modifier = Modifier.testTag("library-filters"), state = filters, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(libraryTabs) { index, label -> FilterTab(filter == index, { onFilter(index) }, label) }
        } }
        if (state.syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) }
        state.error?.let { item { ErrorCard(it, vm::sync) } }
        if (filter in 0..2) item(key = "tools") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.library_filter)) },
                    leadingIcon = { Icon(OpenTuneIcons.Search, null, Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.large,
                    trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(OpenTuneIcons.Close, stringResource(R.string.library_filter_clear), Modifier.size(18.dp)) } })
                if (filter != 2) Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(OpenTuneIcons.Sort, stringResource(R.string.library_sort_by, stringResource(sort.label))) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        LibrarySort.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = { sort = option; sortMenu = false },
                            trailingIcon = { if (sort == option) Icon(OpenTuneIcons.Check, null, Modifier.size(18.dp)) }) }
                    }
                }
            }
        }
        when (filter) {
            0 -> {
                val all = if (state.settings.offline) state.favorites.filter(Track::isLocal) else state.favorites
                val tracks = all.filter { matches(it.title, it.artist, it.album) }.ordered({ it.title }, { it.artist })
                item { SectionTitle(pluralStringResource(R.plurals.track_count, tracks.size, tracks.size), if (tracks.size > 1) stringResource(R.string.action_shuffle) else null) { vm.playTracks(tracks, shuffle = true) } }
                items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track), libraryTabs[0]) }, { onMenu(track) }) }
                if (tracks.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.FavoriteBorder, if (text.isBlank()) stringResource(R.string.library_no_saved) else stringResource(R.string.library_no_matches),
                    if (text.isBlank()) stringResource(R.string.library_no_saved_text) else stringResource(R.string.library_try_filter), if (state.signedIn || text.isNotBlank()) null else stringResource(R.string.action_connect_tidal), onLogin) }
                if (state.hasMoreFavorites && text.isBlank()) item { TextButton(onClick = { vm.moreFavorites() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_load_more_tracks)) } }
            }
            1 -> {
                val albums = state.albums.filter { matches(it.title, it.artist) }.ordered({ it.title }, { it.artist })
                releaseGrid(albums, columns, vm::open)
                if (albums.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.Album, if (text.isBlank()) stringResource(R.string.library_no_albums) else stringResource(R.string.library_no_matches),
                    if (text.isBlank()) stringResource(R.string.library_no_albums_text) else stringResource(R.string.library_try_filter), if (!state.signedIn && text.isBlank()) stringResource(R.string.action_connect_tidal) else null, onLogin) }
            }
            2 -> {
                val artists = state.artists.filter { matches(it.title) }.sortedBy { it.title.lowercase() }
                items(artists, key = { "artist:${it.id}" }) { ReleaseRow(it) { vm.open(it) } }
                if (artists.isEmpty() && !state.syncing) item { EmptyState(OpenTuneIcons.Person, if (text.isBlank()) stringResource(R.string.library_no_artists) else stringResource(R.string.library_no_matches),
                    if (text.isBlank()) stringResource(R.string.library_no_artists_text) else stringResource(R.string.library_try_filter), if (!state.signedIn && text.isBlank()) stringResource(R.string.action_connect_tidal) else null, onLogin) }
            }
            3 -> {
                item { SectionTitle(onThisDevice, stringResource(R.string.playlist_new), onNewPlaylist) }
                items(state.localPlaylists, key = LocalPlaylist::id) { playlist ->
                    ReleaseRow(Release(playlist.id, playlist.title, pluralStringResource(R.plurals.track_count, playlist.trackIds.size, playlist.trackIds.size), type = ReleaseType.PLAYLIST)) {
                        vm.open(Release(playlist.id, playlist.title, onThisDevice, type = ReleaseType.PLAYLIST))
                    }
                }
                if (state.localPlaylists.isEmpty()) item { Text(stringResource(R.string.library_playlists_hint), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val folderId = openFolder?.first ?: TidalClient.ROOT_FOLDER
                val entries = state.folders[folderId] ?: if (openFolder == null) state.playlists else emptyList()
                if (state.playlists.isNotEmpty() || state.folders.isNotEmpty()) {
                    item(key = "tidal-title") {
                        SectionTitle(openFolder?.second ?: stringResource(R.string.library_tidal_playlists), if (openFolder == null && online) stringResource(R.string.folder_new) else null) { newFolder = true }
                    }
                    if (openFolder != null) item(key = "folder-back") {
                        TextButton(onClick = { folderStack = folderStack.dropLast(1) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Icon(OpenTuneIcons.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.library_all_playlists))
                        }
                    }
                    items(entries, key = { "${it.type}:${it.id}" }) { entry ->
                        if (entry.type == ReleaseType.FOLDER) FolderRow(entry, onOpen = { folderStack = folderStack + (entry.id to entry.title) },
                            onRename = { renaming = entry }, onDelete = { deleting = entry })
                        else PlaylistRow(entry, canMove = online, onMove = { moving = entry }) { vm.open(entry) }
                    }
                    if (entries.isEmpty() && openFolder != null) item { Text(stringResource(R.string.folder_empty), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            5 -> {
                val entry = entries[mixes.key]
                val page = entry?.page
                if (page != null) pageContent(page, entry, mixes.key, context, viewAll = true)
                else item(key = "mixes-state") {
                    when {
                        !state.signedIn -> EmptyState(OpenTuneIcons.Waves, stringResource(R.string.mixes_none), stringResource(R.string.mixes_connect), stringResource(R.string.action_connect_tidal), onLogin)
                        state.settings.offline -> EmptyState(OpenTuneIcons.CloudOff, stringResource(R.string.mixes_offline), stringResource(R.string.mixes_offline_text))
                        entry?.error != null -> ErrorCard(entry.error) { pages.load(mixes, force = true) }
                        else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 1.5.dp) }
                    }
                }
            }
            else -> {
                item { SectionTitle(pluralStringResource(R.plurals.track_count, state.local.size, state.local.size), stringResource(R.string.action_import), onImport) }
                items(state.local, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(state.local, state.local.indexOf(track), libraryTabs[4]) }, { onMenu(track) }) }
                if (state.local.isEmpty()) item { EmptyState(OpenTuneIcons.AudioFile, stringResource(R.string.device_none), stringResource(R.string.device_none_text), stringResource(R.string.action_import_audio), onImport) }
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
                    Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_play))
                }
                TuneTonalButton(onClick = { vm.play(state.detailTracks); if (!state.playback.shuffle) vm.toggleShuffle() }, enabled = state.detailTracks.isNotEmpty()) {
                    Icon(OpenTuneIcons.Shuffle, stringResource(R.string.album_shuffle))
                }
                if (state.detailTracks.any { !it.isLocal }) TuneOutlinedButton(onClick = { state.detailTracks.forEach { vm.download(it) } }) { Icon(OpenTuneIcons.Download, stringResource(R.string.album_download_all)) }
            }
        } }
        if (state.detailLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
        state.error?.let { item { ErrorCard(it) { vm.open(release) } } }
        itemsIndexed(state.detailTracks, key = { index, track -> "$index:${track.id}" }) { index, track -> TrackRow(track, state.playback.track?.id == track.id,
            { vm.play(state.detailTracks, index, release.title) }, { onMenu(track, index) }, index) }
        if (!state.detailLoading && state.detailTracks.isEmpty() && state.error == null) item { EmptyState(OpenTuneIcons.PlaylistPlay, stringResource(R.string.detail_no_tracks), stringResource(R.string.detail_no_tracks_text)) }
        if (state.detailAlbums.isNotEmpty()) item { SectionTitle(stringResource(R.string.label_albums)) }
        items(state.detailAlbums, key = Release::id) { ReleaseRow(it) { vm.open(it) } }
    }
}

@UnstableApi
@Composable
fun DownloadsScreen(state: AppState, vm: MainViewModel, onMenu: (Track) -> Unit) {
    val active = state.downloads.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
    val tracks = state.local.filterNot { it.id.startsWith("local:") }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(stringResource(R.string.downloads_intro), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.downloads.any { it.state.isFinished }) item { TextButton(onClick = vm::clearDownloadHistory, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.downloads_clear)) } }
        items(active, key = { it.id }) { work ->
            val trackId = work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")
            val track = state.downloadTracks[trackId]
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track?.title ?: stringResource(R.string.downloads_unnamed), style = MaterialTheme.typography.titleSmall)
                        Text(when (work.state) {
                            WorkInfo.State.RUNNING -> stringResource(R.string.download_progress, work.progress.getInt("progress", 0))
                            WorkInfo.State.FAILED -> context.downloadErrorText(work.outputData.getString("error"))
                            WorkInfo.State.BLOCKED -> stringResource(R.string.download_waiting)
                            else -> if (state.settings.downloadOnWifi) stringResource(R.string.download_queued_wifi) else stringResource(R.string.download_queued)
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!work.state.isFinished) IconButton(onClick = { vm.cancelDownload(work) }) { Icon(OpenTuneIcons.Close, stringResource(R.string.download_cancel)) }
                    else if (track != null) TextButton(onClick = { vm.download(track) }) { Text(stringResource(R.string.action_retry)) }
                }
                if (work.state == WorkInfo.State.RUNNING) LinearProgressIndicator(progress = { work.progress.getInt("progress", 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        if (tracks.isNotEmpty()) item { SectionTitle(pluralStringResource(R.plurals.saved_track_count, tracks.size, tracks.size)) }
        items(tracks, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }) }
        if (tracks.isEmpty() && active.isEmpty()) item { EmptyState(OpenTuneIcons.Download, stringResource(R.string.downloads_none), stringResource(R.string.downloads_none_text)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun LoginSheet(state: AppState, vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val openBrowser = {
        try {
            val pending = vm.beginBrowserLogin()
            CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
                .launchUrl(context, Uri.parse(pending.authorizationUrl))
        } catch (_: Exception) { vm.notify(resources.getString(R.string.login_no_browser)) }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.action_connect_tidal), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.login_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.signingIn) LinearProgressIndicator(Modifier.fillMaxWidth())
            TuneButton(onClick = openBrowser, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_button)) }
            Text(stringResource(R.string.login_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { SectionTitle(stringResource(R.string.settings_account)) }
        item { SettingRow(OpenTuneIcons.AccountCircle, if (state.signedIn) stringResource(R.string.settings_connected) else stringResource(R.string.action_connect_tidal),
            if (state.signedIn) stringResource(R.string.settings_connected_text) else stringResource(R.string.settings_connect_text),
            onClick = if (state.signedIn) ({ signOut = true }) else onLogin,
            trailing = { Icon(if (state.signedIn) OpenTuneIcons.CheckCircle else OpenTuneIcons.ChevronRight, null) }) }
        item { SectionTitle(stringResource(R.string.settings_playback)) }
        item { SettingRow(OpenTuneIcons.Wifi, stringResource(R.string.settings_quality_wifi), stringResource(state.settings.quality.labelRes()), { quality = true }) }
        item { SettingRow(OpenTuneIcons.SignalCellularAlt, stringResource(R.string.settings_quality_mobile), stringResource(state.settings.mobileQuality.labelRes()), { mobile = true }) }
        item { SettingRow(OpenTuneIcons.CloudOff, stringResource(R.string.status_offline), stringResource(R.string.settings_offline_text), trailing = {
            Switch(state.settings.offline, { value -> vm.settings { it.copy(offline = value) } })
        }) }
        item { SettingRow(OpenTuneIcons.Download, stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_text), trailing = {
            Switch(state.settings.downloadOnWifi, { value -> vm.settings { it.copy(downloadOnWifi = value) } })
        }) }
        item { SettingRow(OpenTuneIcons.GraphicEq, stringResource(R.string.normalization_title), stringResource(when (state.settings.normalization) { "track" -> R.string.normalization_track; "album" -> R.string.normalization_album; else -> R.string.settings_off }), { normalization = true }) }
        item { SettingRow(OpenTuneIcons.Equalizer, stringResource(R.string.eq_title), stringResource(if (state.settings.equalizer) R.string.settings_on else R.string.settings_off), { equalizer = true }) }
        item { SettingRow(OpenTuneIcons.Headphones, stringResource(R.string.output_title), stringResource(R.string.settings_output_text), { output = true }) }
        if (android.os.Build.VERSION.SDK_INT >= 34) item { SettingRow(OpenTuneIcons.Waves, stringResource(R.string.settings_bitperfect), stringResource(R.string.settings_bitperfect_text), trailing = {
            Switch(state.settings.bitPerfect, { value -> vm.settings { it.copy(bitPerfect = value) } })
        }) }
        item { SectionTitle(stringResource(R.string.settings_appearance)) }
        item { SettingRow(OpenTuneIcons.Contrast, stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_value, stringResource(colorTheme(state.settings.colorTheme).nameRes), stringResource(when (state.settings.theme) { "light" -> R.string.appearance_light; "system" -> R.string.appearance_system; else -> R.string.appearance_dark })), { theme = true }) }
        item { SectionTitle(stringResource(R.string.settings_privacy_data)) }
        item { SettingRow(OpenTuneIcons.Download, stringResource(R.string.settings_keep_offline), stringResource(R.string.settings_keep_offline_text), trailing = {
            Switch(state.settings.keepFavoritesOffline, { value -> vm.settings { it.copy(keepFavoritesOffline = value) } })
        }) }
        item { SettingRow(OpenTuneIcons.GraphicEq, stringResource(R.string.scrobble_title), if (state.settings.scrobble) stringResource(R.string.settings_scrobble_on, state.settings.scrobbleServer.removePrefix("https://")) else stringResource(R.string.settings_off), { scrobbling = true }) }
        item { SettingRow(OpenTuneIcons.LibraryMusic, stringResource(R.string.stats_title), stringResource(R.string.settings_stats_text), { stats = true }) }
        item { SettingRow(OpenTuneIcons.SaveAlt, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_text), { backup.launch("opentune-backup.json") }) }
        item { SettingRow(OpenTuneIcons.Refresh, stringResource(R.string.settings_restore), stringResource(R.string.settings_restore_text), { restore.launch(arrayOf("application/json", "*/*")) }) }
        item { SettingRow(OpenTuneIcons.RemoveCircleOutline, stringResource(R.string.settings_clear_history), stringResource(R.string.settings_clear_history_text), { clearHistory = true }) }
        item { SectionTitle(stringResource(R.string.settings_about)) }
        item { SettingRow(OpenTuneIcons.Info, stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), stringResource(R.string.settings_tagline)) }
        item { SettingRow(OpenTuneIcons.Code, stringResource(R.string.settings_source), "github.com/yyan115/tidal-app", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yyan115/tidal-app"))) }
        }) }
        item { SettingRow(OpenTuneIcons.PrivacyTip, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_text)) }
        item { SettingRow(OpenTuneIcons.Description, stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_text), { licenses = true }) }
    }
    if (licenses) LicensesDialog { licenses = false }
    if (normalization) NormalizationDialog(state.settings.normalization, { value -> vm.settings { it.copy(normalization = value) } }) { normalization = false }
    if (equalizer) EqualizerSheet(state.settings, { next -> vm.settings { next } }) { equalizer = false }
    if (output) AudioOutputDialog { output = false }
    if (scrobbling) ScrobbleDialog(state.settings, vm) { scrobbling = false }
    if (stats) StatsSheet(vm) { stats = false }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(stringResource(R.string.history_clear_text)) },
        confirmButton = { TextButton(onClick = { vm.clearListeningHistory(); clearHistory = false }) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (quality || mobile) AlertDialog(onDismissRequest = { quality = false; mobile = false }, title = { Text(if (mobile) stringResource(R.string.settings_quality_mobile) else stringResource(R.string.settings_quality_wifi)) },
        text = { Column { AudioQuality.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable {
            if (mobile) vm.settings { it.copy(mobileQuality = value) } else vm.settings { it.copy(quality = value) }
            quality = false; mobile = false
        }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(if (mobile) state.settings.mobileQuality == value else state.settings.quality == value, onClick = null)
            Text(stringResource(value.labelRes()), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        } } } }, confirmButton = {})
    if (theme) AppearanceSheet(state.settings, { next -> vm.settings { next } }) { theme = false }
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text(stringResource(R.string.signout_title)) }, text = { Text(stringResource(R.string.signout_text)) },
        confirmButton = { TextButton(onClick = { vm.signOut(); signOut = false }) { Text(stringResource(R.string.signout_confirm)) } }, dismissButton = { TextButton(onClick = { signOut = false }) { Text(stringResource(R.string.action_cancel)) } })
}
