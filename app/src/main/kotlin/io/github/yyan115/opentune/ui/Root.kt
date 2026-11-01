package io.github.yyan115.opentune.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.*

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun OpenTuneRoot(vm: MainViewModel, state: AppState, initialTab: Int = 0, initialPlayer: Boolean = false, initialSettings: Boolean = false) {
    val context = LocalContext.current
    val pagesVm: PagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var libraryFilter by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(initialSettings) }
    var login by rememberSaveable { mutableStateOf(false) }
    var nowPlaying by rememberSaveable { mutableStateOf(initialPlayer) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf<Track?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var newPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTitle by remember { mutableStateOf("") }
    var cloudPlaylist by remember { mutableStateOf(false) }
    var playlistPosition by remember { mutableStateOf<Int?>(null) }
    var editPlaylist by remember { mutableStateOf(false) }
    var deletePlaylist by remember { mutableStateOf(false) }
    var removeTrack by remember { mutableStateOf<Track?>(null) }
    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationMenu by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val largeText = LocalDensity.current.fontScale > 1.3f
    val rail = landscape && !largeText
    val navigationTitles = listOf("Listen", "Search", "Library", "Downloads")
    val navigationIcons = listOf(OpenTuneIcons.Headphones, OpenTuneIcons.Search, OpenTuneIcons.LibraryMusic, OpenTuneIcons.Download)
    val snackbar = remember { SnackbarHostState() }
    val inDetail = state.detail != null || state.routes.isNotEmpty()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.import(uris) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exporter = rememberLauncherForActivityResult(ExportContract()) { uri ->
        val encoded = exportTarget
        exportTarget = null
        if (uri != null && encoded != null) vm.export(JsonCodec.decodeTrack(org.json.JSONObject(encoded)), uri)
    }
    val importAudio = { importer.launch(arrayOf("audio/*")) }
    val openLogin = { login = true }
    val openTrackMenu: (Track) -> Unit = { trackMenu = it; playlistPosition = null }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(state.signedIn) { if (state.signedIn) login = false }
    BackHandler(enabled = state.detail != null || settings || state.routes.isNotEmpty()) { if (settings) settings = false else vm.closeDetail() }

    Row(Modifier.fillMaxSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier)) {
    if (rail) OpenTuneRail(navigationTitles, navigationIcons, tab) { index -> tab = index; settings = false; vm.closeAll() }
    Scaffold(modifier = Modifier.weight(1f),
        topBar = {
            val title = if (settings) "Settings" else state.detail?.let {
                when (it.type) { ReleaseType.ALBUM -> "Album"; ReleaseType.ARTIST -> "Artist"; ReleaseType.PLAYLIST -> "Playlist"; ReleaseType.MIX -> "Mix"; ReleaseType.VIDEO -> "Video" }
            } ?: navigationTitles[tab]
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = if (settings || inDetail || largeText) 8.dp else 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings || inDetail) IconButton(onClick = { if (settings) settings = false else vm.closeDetail() }) { Icon(OpenTuneIcons.ArrowBack, "Back") }
                    else if (largeText) IconButton(onClick = { navigationMenu = true }) { Icon(OpenTuneIcons.Menu, "Open navigation") }
                    if (!settings && !inDetail && !largeText) {
                        Icon(OpenTuneIcons.Brand, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text("OpenTune", Modifier.weight(1f), fontFamily = InterfaceFont, fontSize = 11.sp, lineHeight = 16.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    if (!settings) {
                        if (tab == 0 && !inDetail) IconButton(onClick = importAudio) { Icon(OpenTuneIcons.Add, "Import audio", Modifier.size(21.dp)) }
                        if (tab == 2 && !inDetail && state.signedIn && !state.settings.offline) IconButton(onClick = vm::sync, enabled = !state.syncing) { Icon(OpenTuneIcons.Refresh, "Refresh library") }
                        state.detail?.takeIf { state.routes.isEmpty() }?.let { release ->
                            if (vm.canEdit(release)) IconButton(onClick = { playlistTitle = release.title; editPlaylist = true }) { Icon(OpenTuneIcons.Edit, "Edit playlist") }
                            if (release.type == ReleaseType.ALBUM && state.signedIn) IconButton(onClick = { vm.favoriteAlbum(release) }) {
                                val saved = state.albums.any { it.id == release.id }
                                Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, if (saved) "Remove saved album" else "Save album")
                            }
                        }
                        if (state.settings.offline) Icon(OpenTuneIcons.CloudOff, "Offline mode", Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { settings = true }) { Icon(OpenTuneIcons.Settings, "Settings", Modifier.size(22.dp)) }
                    }
                }
                if (!inDetail || settings) Text(title,
                    Modifier.padding(start = 24.dp, end = 24.dp, top = if (largeText) 2.dp else 6.dp, bottom = 14.dp),
                    fontFamily = EditorialFont, fontSize = if (largeText) 30.sp else if (tab == 0 && !settings) 52.sp else 44.sp,
                    lineHeight = if (largeText) 38.sp else if (tab == 0 && !settings) 58.sp else 50.sp)
            }
        },
        bottomBar = {
            Column {
                if (state.playback.track != null) MiniPlayer(state.playback, onOpen = { nowPlaying = true }, onPlay = vm::togglePlay, onNext = vm::next)
                if (!settings && !landscape && !largeText) OpenTuneNavigation(navigationTitles, navigationIcons, tab) { index -> tab = index; settings = false; vm.closeAll() }
                else if (state.playback.track != null) Spacer(Modifier.navigationBarsPadding())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                settings -> SettingsScreen(state, vm, openLogin)
                state.routes.isNotEmpty() -> PageScreen(state.routes.last(), state, vm, pagesVm, openTrackMenu)
                state.detail != null -> DetailScreen(state, vm, { track, index -> trackMenu = track; playlistPosition = index })
                tab == 0 -> ListenScreen(state, vm, importAudio, openLogin, { filter -> libraryFilter = filter; tab = 2 }, openTrackMenu, { nowPlaying = true })
                tab == 1 -> SearchScreen(state, vm, openLogin, openTrackMenu)
                tab == 2 -> LibraryScreen(state, vm, importAudio, openLogin, { newPlaylistTrack = null; playlistTitle = ""; newPlaylist = true }, openTrackMenu,
                    libraryFilter, { libraryFilter = it })
                else -> DownloadsScreen(state, vm, openTrackMenu)
            }
            state.exportProgress?.let { percent -> Surface(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("Exporting audio · $percent%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            } }
        }
    }
    }

    if (navigationMenu) ModalBottomSheet(onDismissRequest = { navigationMenu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            navigationTitles.forEachIndexed { index, title -> NavigationDrawerItem(selected = tab == index, label = { Text(title) },
                icon = { Icon(navigationIcons[index], null) }, modifier = Modifier.padding(horizontal = 12.dp),
                onClick = { tab = index; settings = false; vm.closeAll(); navigationMenu = false }) }
        }
    }

    if (login) LoginSheet(state, vm, onClose = { login = false; vm.cancelLogin() })
    if (nowPlaying && state.playback.track != null) NowPlayingScreen(state, vm, onClose = { nowPlaying = false }, onQueue = { queue = true }, onMenu = openTrackMenu, onDownload = { track ->
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.download(track)
    })
    if (queue) QueueSheet(state, vm) { queue = false }
    trackMenu?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackMenu = null; playlistPosition = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Artwork(track.artwork, null, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    IconButton(onClick = { trackMenu = null }) { Icon(OpenTuneIcons.Close, "Close track options") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val saved = state.favorites.any { it.id == track.id }
                SettingRow(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, if (saved) "Remove from saved tracks" else "Save track", onClick = { vm.favorite(track); trackMenu = null })
                SettingRow(OpenTuneIcons.PlaylistPlay, "Play next", onClick = { vm.queue(track, true); trackMenu = null })
                SettingRow(OpenTuneIcons.QueueMusic, "Add to queue", onClick = { vm.queue(track); trackMenu = null })
                SettingRow(OpenTuneIcons.PlaylistAdd, "Add to playlist", onClick = { addToPlaylist = track; trackMenu = null })
                state.detail?.takeIf { vm.canEdit(it) }?.let { release ->
                    playlistPosition?.let { position ->
                        if (position > 0) SettingRow(OpenTuneIcons.ArrowUpward, "Move up", onClick = { vm.moveInPlaylist(release, position, -1); trackMenu = null; playlistPosition = null })
                        if (position < state.detailTracks.lastIndex) SettingRow(OpenTuneIcons.KeyboardArrowDown, "Move down", onClick = { vm.moveInPlaylist(release, position, 1); trackMenu = null; playlistPosition = null })
                        SettingRow(OpenTuneIcons.PlaylistRemove, "Remove from this playlist", onClick = {
                            vm.removeFromPlaylist(release, track, position); trackMenu = null; playlistPosition = null
                        })
                    }
                }
                if (!track.isLocal) SettingRow(OpenTuneIcons.Download, "Download", onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.download(track); trackMenu = null
                })
                if (track.albumId.isNotBlank()) SettingRow(OpenTuneIcons.Album, "Go to album", onClick = {
                    vm.open(Release(track.albumId, track.album, track.artist, track.artwork)); nowPlaying = false; trackMenu = null
                })
                if (track.artistId.isNotBlank() && !track.id.startsWith("local:")) SettingRow(OpenTuneIcons.Person, "Go to artist", onClick = {
                    vm.open(Release(track.artistId, track.artist.substringBefore(","), "", type = ReleaseType.ARTIST)); nowPlaying = false; trackMenu = null
                })
                if (!track.id.startsWith("local:") && state.signedIn && !state.settings.offline) {
                    SettingRow(OpenTuneIcons.Radio, "Start track radio", onClick = { vm.playTrackRadio(track); trackMenu = null })
                    SettingRow(OpenTuneIcons.Info, "Credits", onClick = { vm.showCredits("track", track.id, "${track.title} · ${track.artist}"); trackMenu = null })
                }
                if (!track.id.startsWith("local:")) SettingRow(OpenTuneIcons.Share, "Share TIDAL link", onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://tidal.com/browse/track/${track.id}"), "Share track")); trackMenu = null
                })
                if (track.isLocal) SettingRow(OpenTuneIcons.RemoveCircleOutline, "Remove from device library", onClick = { removeTrack = track; trackMenu = null })
                val localCopy = state.local.firstOrNull { it.id == track.id } ?: track.takeIf(Track::isLocal)
                if (localCopy != null) SettingRow(OpenTuneIcons.SaveAlt, "Export audio file", onClick = {
                    try {
                        exportTarget = JsonCodec.encodeTrack(localCopy).toString()
                        exporter.launch(vm.app.exporter.describe(localCopy)); trackMenu = null; nowPlaying = false
                    } catch (_: Exception) { vm.notify("This audio file is no longer readable") }
                })
            }
        }
    }
    state.video?.let { video -> VideoPlayerOverlay(video, state.settings.offline, vm::closeVideo) }
    state.credits?.let { credits -> CreditsSheet(credits, onClose = vm::dismissCredits, onPerson = { person ->
        vm.dismissCredits(); nowPlaying = false
        vm.open(Release(person.id, person.name, "", type = ReleaseType.ARTIST))
    }) }
    addToPlaylist?.let { track ->
        AlertDialog(onDismissRequest = { addToPlaylist = null }, title = { Text("Add to playlist") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.localPlaylists.isEmpty()) Text("Create a playlist in your library first.")
                state.localPlaylists.forEach { playlist -> TextButton(onClick = { vm.addToPlaylist(playlist, track); addToPlaylist = null }) { Text(playlist.title) } }
                if (!track.id.startsWith("local:")) state.playlists.filter(vm::canEdit).forEach { playlist ->
                    TextButton(onClick = { vm.addToCloudPlaylist(playlist, track); addToPlaylist = null }) { Text("${playlist.title} · TIDAL") }
                }
            }
        }, confirmButton = { TextButton(onClick = { newPlaylistTrack = track; playlistTitle = ""; addToPlaylist = null; newPlaylist = true }) { Text("New playlist") } }, dismissButton = { TextButton(onClick = { addToPlaylist = null }) { Text("Cancel") } })
    }
    if (newPlaylist) AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text("New playlist") }, text = {
        Column {
            OutlinedTextField(playlistTitle, { playlistTitle = it }, label = { Text("Name") }, singleLine = true)
            if (state.signedIn && !state.settings.offline && newPlaylistTrack?.id?.startsWith("local:") != true) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(cloudPlaylist, { cloudPlaylist = it }); Text("Create in TIDAL")
            }
        }
    }, confirmButton = { TextButton(enabled = playlistTitle.isNotBlank(), onClick = {
        if (cloudPlaylist && state.signedIn && newPlaylistTrack?.id?.startsWith("local:") != true) vm.createCloudPlaylist(playlistTitle, newPlaylistTrack) else vm.createPlaylist(playlistTitle, newPlaylistTrack)
        playlistTitle = ""; cloudPlaylist = false; newPlaylist = false; newPlaylistTrack = null
    }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text("Cancel") } })
    if (editPlaylist) state.detail?.let { release -> AlertDialog(onDismissRequest = { editPlaylist = false }, title = { Text("Edit playlist") }, text = {
        Column {
            OutlinedTextField(playlistTitle, { playlistTitle = it }, label = { Text("Name") }, singleLine = true)
            TextButton(onClick = { editPlaylist = false; deletePlaylist = true }) { Text("Delete playlist", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = playlistTitle.isNotBlank(), onClick = { vm.renamePlaylist(release, playlistTitle); editPlaylist = false; playlistTitle = "" }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editPlaylist = false }) { Text("Cancel") } }) }
    if (deletePlaylist) state.detail?.let { release -> AlertDialog(onDismissRequest = { deletePlaylist = false }, title = { Text("Delete ${release.title}?") },
        text = { Text("The playlist will be deleted. Its audio files stay in your library.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(release); deletePlaylist = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deletePlaylist = false }) { Text("Cancel") } }) }
    removeTrack?.let { track -> AlertDialog(onDismissRequest = { removeTrack = null }, title = { Text("Remove this track?") },
        text = { Text(if (track.id.startsWith("local:")) "The original audio file will stay where it is." else "The downloaded copy will be removed. You can download it again while it remains available.") },
        confirmButton = { TextButton(onClick = { vm.removeLocal(track); removeTrack = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removeTrack = null }) { Text("Cancel") } }) }
}
