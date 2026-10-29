package io.github.yyan115.undertow.ui

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.core.*

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun UndertowRoot(vm: MainViewModel, state: AppState) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var login by rememberSaveable { mutableStateOf(false) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf<Track?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf("") }
    var cloudPlaylist by remember { mutableStateOf(false) }
    var playlistPosition by remember { mutableStateOf<Int?>(null) }
    var editPlaylist by remember { mutableStateOf(false) }
    var deletePlaylist by remember { mutableStateOf(false) }
    var removeTrack by remember { mutableStateOf<Track?>(null) }
    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.import(uris) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exporter = rememberLauncherForActivityResult(ExportContract()) { uri ->
        val encoded = exportTarget
        exportTarget = null
        if (uri != null && encoded != null) vm.export(JsonCodec.decodeTrack(org.json.JSONObject(encoded)), uri)
    }
    val importAudio = { importer.launch(arrayOf("audio/*")) }
    val openLogin = { login = true }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(state.signedIn) { if (state.signedIn) login = false }
    BackHandler(enabled = state.detail != null || settings) { if (settings) settings = false else vm.closeDetail() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (settings) "Settings" else state.detail?.let {
                when (it.type) { ReleaseType.ALBUM -> "Album"; ReleaseType.ARTIST -> "Artist"; ReleaseType.PLAYLIST -> "Playlist" }
            } ?: listOf("Listen", "Search", "Library", "Downloads")[tab], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (settings || state.detail != null) IconButton(onClick = { if (settings) settings = false else vm.closeDetail() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (!settings) {
                        if (tab == 2 && state.detail == null && state.signedIn && !state.settings.offline) IconButton(onClick = vm::sync, enabled = !state.syncing) { Icon(Icons.Outlined.Refresh, "Refresh library") }
                        state.detail?.let { release ->
                            if (vm.canEdit(release)) IconButton(onClick = { playlistTitle = release.title; editPlaylist = true }) { Icon(Icons.Outlined.Edit, "Edit playlist") }
                            if (release.type == ReleaseType.ALBUM && state.signedIn) IconButton(onClick = { vm.favoriteAlbum(release) }) {
                                val saved = state.albums.any { it.id == release.id }
                                Icon(if (saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (saved) "Remove saved album" else "Save album")
                            }
                        }
                        if (state.settings.offline) Icon(Icons.Outlined.CloudOff, "Offline mode", Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                })
        },
        bottomBar = {
            Column {
                if (state.playback.track != null) MiniPlayer(state.playback, onOpen = { nowPlaying = true }, onPlay = vm::togglePlay, onNext = vm::next)
                if (!settings) NavigationBar {
                    val titles = listOf("Listen", "Search", "Library", "Downloads")
                    val icons = listOf(Icons.Outlined.Headphones, Icons.Outlined.Search, Icons.Outlined.LibraryMusic, Icons.Outlined.Download)
                    titles.forEachIndexed { index, title -> NavigationBarItem(selected = tab == index, modifier = Modifier.testTag("nav-$index"),
                        onClick = { tab = index; settings = false; vm.closeDetail() }, icon = { Icon(icons[index], null) }, label = { Text(title) }) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                settings -> SettingsScreen(state, vm, openLogin)
                state.detail != null -> DetailScreen(state, vm, { track, index -> trackMenu = track; playlistPosition = index })
                tab == 0 -> ListenScreen(state, vm, importAudio, openLogin, { tab = 2 }, { trackMenu = it })
                tab == 1 -> SearchScreen(state, vm, openLogin, { trackMenu = it })
                tab == 2 -> LibraryScreen(state, vm, importAudio, openLogin, { newPlaylist = true }, { trackMenu = it })
                else -> DownloadsScreen(state, vm, { trackMenu = it })
            }
            state.exportProgress?.let { percent -> Surface(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("Exporting audio · $percent%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            } }
        }
    }

    if (login) LoginSheet(state, vm, onClose = { login = false; vm.cancelLogin() })
    if (nowPlaying && state.playback.track != null) NowPlayingScreen(state, vm, onClose = { nowPlaying = false }, onQueue = { queue = true }, onMenu = { trackMenu = it }, onDownload = { track ->
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.download(track)
    })
    if (queue) QueueSheet(state, vm) { queue = false }
    trackMenu?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackMenu = null }) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                TrackRow(track, onPlay = { vm.play(listOf(track)); trackMenu = null }, onMenu = { trackMenu = null })
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val saved = state.favorites.any { it.id == track.id }
                SettingRow(if (saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (saved) "Remove from saved tracks" else "Save track", onClick = { vm.favorite(track); trackMenu = null })
                SettingRow(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next", onClick = { vm.queue(track, true); trackMenu = null })
                SettingRow(Icons.AutoMirrored.Outlined.QueueMusic, "Add to queue", onClick = { vm.queue(track); trackMenu = null })
                SettingRow(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", onClick = { addToPlaylist = track; trackMenu = null })
                state.detail?.takeIf { vm.canEdit(it) }?.let { release ->
                    playlistPosition?.let { position -> SettingRow(Icons.Outlined.PlaylistRemove, "Remove from this playlist", onClick = {
                        vm.removeFromPlaylist(release, track, position); trackMenu = null; playlistPosition = null
                    }) }
                }
                if (!track.isLocal) SettingRow(Icons.Outlined.Download, "Download", onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.download(track); trackMenu = null
                })
                if (track.albumId.isNotBlank()) SettingRow(Icons.Outlined.Album, "Go to album", onClick = {
                    vm.open(Release(track.albumId, track.album, track.artist, track.artwork)); nowPlaying = false; trackMenu = null
                })
                if (!track.id.startsWith("local:")) SettingRow(Icons.Outlined.Share, "Share TIDAL link", onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://tidal.com/browse/track/${track.id}"), "Share track")); trackMenu = null
                })
                if (track.isLocal) SettingRow(Icons.Outlined.RemoveCircleOutline, "Remove from device library", onClick = { removeTrack = track; trackMenu = null })
                val localCopy = state.local.firstOrNull { it.id == track.id } ?: track.takeIf(Track::isLocal)
                if (localCopy != null) SettingRow(Icons.Outlined.SaveAlt, "Export audio file", onClick = {
                    try {
                        exportTarget = JsonCodec.encodeTrack(localCopy).toString()
                        exporter.launch(vm.app.exporter.describe(localCopy)); trackMenu = null
                    } catch (_: Exception) { vm.notify("This audio file is no longer readable") }
                })
            }
        }
    }
    addToPlaylist?.let { track ->
        AlertDialog(onDismissRequest = { addToPlaylist = null }, title = { Text("Add to playlist") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.localPlaylists.isEmpty()) Text("Create a playlist in your library first.")
                state.localPlaylists.forEach { playlist -> TextButton(onClick = { vm.addToPlaylist(playlist, track); addToPlaylist = null }) { Text(playlist.title) } }
                if (!track.id.startsWith("local:")) state.playlists.filter(vm::canEdit).forEach { playlist ->
                    TextButton(onClick = { vm.addToCloudPlaylist(playlist, track); addToPlaylist = null }) { Text("${playlist.title} · TIDAL") }
                }
            }
        }, confirmButton = { TextButton(onClick = { addToPlaylist = null; newPlaylist = true }) { Text("New playlist") } }, dismissButton = { TextButton(onClick = { addToPlaylist = null }) { Text("Cancel") } })
    }
    if (newPlaylist) AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text("New playlist") }, text = {
        Column {
            OutlinedTextField(playlistTitle, { playlistTitle = it }, label = { Text("Name") }, singleLine = true)
            if (state.signedIn && !state.settings.offline) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(cloudPlaylist, { cloudPlaylist = it }); Text("Create in TIDAL")
            }
        }
    }, confirmButton = { TextButton(enabled = playlistTitle.isNotBlank(), onClick = {
        if (cloudPlaylist && state.signedIn) vm.createCloudPlaylist(playlistTitle) else vm.createPlaylist(playlistTitle)
        playlistTitle = ""; cloudPlaylist = false; newPlaylist = false
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
