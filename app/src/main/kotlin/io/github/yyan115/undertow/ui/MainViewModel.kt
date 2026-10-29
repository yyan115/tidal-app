package io.github.yyan115.undertow.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.yyan115.undertow.UndertowApp
import io.github.yyan115.undertow.core.*
import io.github.yyan115.undertow.data.DownloadWorker
import io.github.yyan115.undertow.data.Settings
import io.github.yyan115.undertow.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PlaybackState(
    val queue: List<Track> = emptyList(), val index: Int = 0,
    val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0,
    val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val quality: String = "", val error: String? = null,
) { val track: Track? get() = queue.getOrNull(index) }

data class AppState(
    val signedIn: Boolean = false,
    val settings: Settings = Settings(),
    val local: List<Track> = emptyList(), val favorites: List<Track> = emptyList(), val recent: List<Track> = emptyList(),
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val albums: List<Release> = emptyList(), val playlists: List<Release> = emptyList(),
    val search: SearchResult = SearchResult(), val searchQuery: String = "", val searching: Boolean = false,
    val syncing: Boolean = false, val error: String? = null, val message: String? = null,
    val login: DeviceLogin? = null, val signingIn: Boolean = false,
    val detail: Release? = null, val detailTracks: List<Track> = emptyList(), val detailAlbums: List<Release> = emptyList(), val detailLoading: Boolean = false,
    val downloads: List<WorkInfo> = emptyList(), val playback: PlaybackState = PlaybackState(),
    val downloadTracks: Map<String, Track> = emptyMap(),
    val lyrics: String? = null, val lyricsLoading: Boolean = false,
    val hasMoreFavorites: Boolean = false,
)

@OptIn(FlowPreview::class)
@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as UndertowApp
    private val ui = MutableStateFlow(AppState(signedIn = app.tidal.signedIn, settings = app.settings.state.value))
    val state = ui.asStateFlow()
    private val query = MutableStateFlow("")
    private val preferences = application.getSharedPreferences("playback", Application.MODE_PRIVATE)
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    private var loginJob: Job? = null
    private var detailJob: Job? = null
    private var syncJob: Job? = null
    private var lyricsJob: Job? = null
    private var cloudFavorites = emptyList<Track>()
    private var favoritesOffset = 0

    init {
        controllerFuture.addListener({
            try {
                val player = controllerFuture.get()
                controller = player
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { updatePlayback(); persistQueue() }
                    override fun onPlayerError(error: PlaybackException) {
                        val cause = generateSequence<Throwable>(error) { it.cause }.last()
                        ui.update { it.copy(playback = it.playback.copy(error = when (cause) {
                            is ServiceException, is ProtectedStreamException, is IllegalStateException -> cause.message
                            else -> "Playback stopped. Check the connection or choose another track."
                        })) }
                    }
                })
                restoreQueue()
                updatePlayback()
            } catch (_: Exception) { notify("The audio service could not start. Reopen the app to retry.") }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let { player ->
                    ui.update { it.copy(playback = it.playback.copy(position = player.currentPosition.coerceAtLeast(0),
                        quality = it.playback.track?.let { track -> app.sources.quality(track.id) ?: track.quality }.orEmpty(),
                        duration = player.duration.takeIf { d -> d > 0 } ?: it.playback.track?.durationMs ?: 0)) }
                }
            }
        }
        viewModelScope.launch { while (true) { delay(10_000); persistQueue() } }
        viewModelScope.launch { app.library.changes.collect { refreshLocal() } }
        viewModelScope.launch { app.settings.state.collect { settings -> ui.update { it.copy(settings = settings) } } }
        viewModelScope.launch {
            WorkManager.getInstance(application).getWorkInfosByTagFlow("audio-download").collect { jobs ->
                val tracks = withContext(Dispatchers.IO) { jobs.mapNotNull { work ->
                    work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")?.let { app.library.get(it) }
                }.associateBy(Track::id) }
                ui.update { it.copy(downloads = jobs, downloadTracks = tracks) }
            }
        }
        viewModelScope.launch {
            query.debounce(350).distinctUntilChanged().collectLatest { text ->
                ui.update { it.copy(searchQuery = text, error = null) }
                if (text.isBlank() || !app.tidal.signedIn || ui.value.settings.offline) {
                    ui.update { it.copy(search = SearchResult(), searching = false) }
                } else {
                    ui.update { it.copy(searching = true) }
                    try { val result = app.tidal.search(text); ui.update { it.copy(search = result, searching = false) } }
                    catch (error: Exception) { if (error is CancellationException) throw error; ui.update { it.copy(searching = false, error = userMessage(error)) } }
                }
            }
        }
        if (app.tidal.signedIn && !ui.value.settings.offline) sync()
    }

    private suspend fun refreshLocal() {
        val local = withContext(Dispatchers.IO) { app.library.localTracks() }
        val favorites = withContext(Dispatchers.IO) { app.library.favorites() }
        val recent = withContext(Dispatchers.IO) { app.library.recent() }
        val playlists = withContext(Dispatchers.IO) { app.library.playlists() }
        val localById = local.associateBy(Track::id)
        ui.update { it.copy(local = local, favorites = (favorites + cloudFavorites).distinctBy(Track::id)
            .map { track -> localById[track.id] ?: track }, recent = recent, localPlaylists = playlists) }
    }

    fun search(text: String) { query.value = text; ui.update { it.copy(searchQuery = text) } }
    fun messageShown() { ui.update { it.copy(message = null) } }
    fun notify(message: String) { ui.update { it.copy(message = message) } }

    fun beginLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            ui.update { it.copy(signingIn = true, login = null, error = null) }
            try {
                val login = app.tidal.beginLogin()
                ui.update { it.copy(login = login) }
                app.tidal.awaitLogin(login)
                ui.update { it.copy(signedIn = true, login = null, signingIn = false) }
                sync()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { it.copy(signingIn = false, login = null, error = userMessage(error)) }
            }
        }
    }

    fun cancelLogin() { loginJob?.cancel(); ui.update { it.copy(signingIn = false, login = null, error = null) } }
    fun signOut() {
        cancelLogin(); syncJob?.cancel(); detailJob?.cancel(); app.tidal.signOut(); cloudFavorites = emptyList()
        ui.update { it.copy(signedIn = false, syncing = false, hasMoreFavorites = false, albums = emptyList(), playlists = emptyList(), search = SearchResult(), detail = null) }
        viewModelScope.launch { refreshLocal() }
    }

    fun sync() {
        if (!app.tidal.signedIn || ui.value.settings.offline || ui.value.syncing) return
        syncJob = viewModelScope.launch {
            ui.update { it.copy(syncing = true, error = null) }
            try {
                cloudFavorites = app.tidal.allFavorites(); favoritesOffset = cloudFavorites.size
                ui.update { it.copy(hasMoreFavorites = false) }
                refreshLocal()
                val albums = app.tidal.allFavoriteAlbums()
                ui.update { it.copy(albums = albums) }
                val playlists = app.tidal.allPlaylists()
                ui.update { it.copy(playlists = playlists) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { it.copy(error = userMessage(error)) }
            } finally { ui.update { it.copy(syncing = false) } }
        }
    }

    fun moreFavorites() = viewModelScope.launch {
        try {
            val next = app.tidal.favorites(favoritesOffset)
            cloudFavorites = (cloudFavorites + next).distinctBy(Track::id); favoritesOffset += next.size
            ui.update { it.copy(hasMoreFavorites = next.size == 100) }; refreshLocal()
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }

    fun open(release: Release) {
        detailJob?.cancel()
        ui.update { it.copy(detail = release, detailLoading = true, detailTracks = emptyList(), detailAlbums = emptyList(), error = null) }
        detailJob = viewModelScope.launch {
            try {
                val tracks = if (release.id.startsWith("local:")) withContext(Dispatchers.IO) { app.library.playlistTracks(release.id) }
                    else app.tidal.allTracks(release)
                ui.update { it.copy(detailTracks = tracks, detailLoading = false) }
                if (release.type == ReleaseType.ARTIST) {
                    val albums = app.tidal.artistAlbums(release.id); ui.update { it.copy(detailAlbums = albums) }
                }
            } catch (error: Exception) { if (error is CancellationException) throw error; ui.update { it.copy(detailLoading = false, error = userMessage(error)) } }
        }
    }
    fun closeDetail() { detailJob?.cancel(); ui.update { it.copy(detail = null, detailTracks = emptyList(), error = null) } }

    fun play(tracks: List<Track>, index: Int = 0) {
        if (tracks.isEmpty()) return
        val player = controller ?: run { notify("The audio service is starting. Try again in a moment."); return }
        ui.update { it.copy(playback = it.playback.copy(error = null)) }
        player.setMediaItems(tracks.map(app.sources::unresolved), index.coerceIn(tracks.indices), 0)
        player.prepare(); player.play()
    }
    fun togglePlay() {
        controller?.let { player ->
            if (player.isPlaying) player.pause()
            else {
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                if (player.playerError != null || player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)) }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun queue(track: Track, next: Boolean = false) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) { play(listOf(track)); return }
        player.addMediaItem(if (next) player.currentMediaItemIndex + 1 else player.mediaItemCount, app.sources.unresolved(track))
        notify(if (next) "Plays next" else "Added to queue")
    }
    fun removeQueue(index: Int) { controller?.removeMediaItem(index) }
    fun moveQueue(from: Int, to: Int) { controller?.let { if (to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun jumpTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }
    fun clearQueue() { controller?.clearMediaItems() }

    fun favorite(track: Track) = viewModelScope.launch {
        val previous = ui.value.favorites.any { it.id == track.id }
        withContext(Dispatchers.IO) { app.library.setFavorite(track, !previous) }
        cloudFavorites = if (previous) cloudFavorites.filterNot { it.id == track.id } else cloudFavorites
        refreshLocal()
        if (!track.id.startsWith("local:") && app.tidal.signedIn && !ui.value.settings.offline) {
            try { app.tidal.setFavorite(track.id, !previous) }
            catch (error: Exception) { if (error is CancellationException) throw error; notify("Saved on this device. TIDAL could not be updated: ${userMessage(error)}") }
        }
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        var imported = 0
        for (uri in uris) {
            try {
                app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                app.importer.import(uri); imported++
            } catch (error: Exception) { if (error is CancellationException) throw error; notify("An audio file could not be imported. Check that it is readable.") }
        }
        if (imported > 0) notify(if (imported == 1) "Audio file added" else "$imported audio files added")
    }

    fun download(track: Track) = viewModelScope.launch {
        if (track.isLocal) { notify("This track is already on your device"); return@launch }
        if (!app.tidal.signedIn) { notify("Sign in to TIDAL before downloading"); return@launch }
        if (ui.value.settings.offline) { notify("Turn off offline mode before downloading"); return@launch }
        withContext(Dispatchers.IO) { app.library.save(track) }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("track" to JsonCodec.encodeTrack(track).toString()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (ui.value.settings.downloadOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag("audio-download").addTag("track:${track.id}").build()
        WorkManager.getInstance(app).enqueueUniqueWork("download:${track.id}", ExistingWorkPolicy.KEEP, request)
        notify(if (ui.value.settings.downloadOnWifi && !app.sources.wifi()) "Download queued for Wi-Fi" else "Download queued")
    }
    fun cancelDownload(work: WorkInfo) { WorkManager.getInstance(app).cancelWorkById(work.id) }
    fun clearDownloadHistory() { WorkManager.getInstance(app).pruneWork() }

    fun removeLocal(track: Track) = viewModelScope.launch(Dispatchers.IO) {
        WorkManager.getInstance(app).cancelUniqueWork("download:${track.id}")
        if (track.localUri?.startsWith("undertow-offline:") == true) {
            try { app.sources.removeOffline(track.id) }
            catch (_: Exception) { notify("The downloaded audio could not be removed. Try again."); return@launch }
        }
        if (track.localUri?.startsWith("file:") == true) {
            val file = java.io.File(Uri.parse(track.localUri).path.orEmpty())
            if (file.canonicalPath.startsWith(app.filesDir.canonicalPath + "/downloads/")) file.delete()
        }
        app.library.removeLocal(track.id)
    }

    fun createPlaylist(title: String) = viewModelScope.launch(Dispatchers.IO) {
        if (title.isNotBlank()) { app.library.createPlaylist(title); notify("Playlist created") }
    }
    fun addToPlaylist(playlist: LocalPlaylist, track: Track) = viewModelScope.launch(Dispatchers.IO) {
        app.library.addToPlaylist(playlist.id, track); notify("Added to ${playlist.title}")
    }
    fun deletePlaylist(id: String) = viewModelScope.launch(Dispatchers.IO) { app.library.deletePlaylist(id); closeDetail() }

    fun settings(transform: (Settings) -> Settings) { app.settings.update(transform) }
    fun configureClient(id: String, secret: String) {
        try { app.sessions.setIdentity(ClientIdentity(id, secret)); signOut(); notify("Client settings saved") }
        catch (_: Exception) { notify("The client settings could not be saved") }
    }

    fun loadLyrics() {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
        val track = ui.value.playback.track ?: return@launch
        ui.update { it.copy(lyrics = null, lyricsLoading = true) }
        try {
            val lyrics = if (track.id.startsWith("local:")) "Lyrics are not available for this local file." else app.tidal.lyrics(track.id).ifBlank { "No lyrics are available for this track." }
            ui.update { it.copy(lyrics = lyrics, lyricsLoading = false) }
        } catch (error: Exception) { if (error is CancellationException) throw error; ui.update { it.copy(lyrics = "Lyrics could not be loaded. ${userMessage(error)}", lyricsLoading = false) } }
        }
    }

    private fun updatePlayback() {
        val player = controller ?: return
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaMetadata.extras?.getString("track")?.let {
                runCatching { JsonCodec.decodeTrack(JSONObject(it)) }.getOrNull()
            }
        }
        val quality = player.currentMediaItem?.localConfiguration?.tag as? String ?: queue.getOrNull(player.currentMediaItemIndex)?.quality.orEmpty()
        ui.update { it.copy(playback = it.playback.copy(queue = queue, index = player.currentMediaItemIndex.coerceAtLeast(0),
            playing = player.isPlaying, buffering = player.playbackState == Player.STATE_BUFFERING,
            shuffle = player.shuffleModeEnabled, repeat = player.repeatMode, quality = quality,
            error = if (player.playerError == null) null else it.playback.error)) }
    }

    private fun persistQueue() {
        val player = controller ?: return
        preferences.edit().putString("queue", JsonCodec.encodeTracks(ui.value.playback.queue))
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0)).putLong("position", player.currentPosition.coerceAtLeast(0))
            .putBoolean("shuffle", player.shuffleModeEnabled).putInt("repeat", player.repeatMode).apply()
    }
    private fun restoreQueue() {
        if ((controller?.mediaItemCount ?: 0) > 0) return
        val saved = preferences.getString("queue", null) ?: return
        val tracks = runCatching { JsonCodec.decodeTracks(saved) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return
        val index = preferences.getInt("index", 0).coerceIn(tracks.indices)
        val position = preferences.getLong("position", 0)
        val shuffle = preferences.getBoolean("shuffle", false)
        val repeat = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
        controller?.let {
            it.setMediaItems(tracks.map(app.sources::unresolved), index, position)
            it.shuffleModeEnabled = shuffle
            it.repeatMode = repeat
        }
    }

    fun canEdit(release: Release): Boolean = release.type == ReleaseType.PLAYLIST &&
        (release.id.startsWith("local:") || (app.tidal.signedIn && release.ownerId.isNotBlank() && release.ownerId == app.tidal.accountId))

    fun createCloudPlaylist(title: String) = accountAction {
        val playlist = app.tidal.createPlaylist(title.trim())
        ui.update { it.copy(playlists = it.playlists + playlist) }
        notify("Playlist created in TIDAL")
    }

    fun addToCloudPlaylist(playlist: Release, track: Track) = accountAction {
        app.tidal.addToPlaylist(playlist.id, track.id)
        notify("Added to ${playlist.title}")
    }

    fun renamePlaylist(release: Release, title: String) = viewModelScope.launch {
        if (!canEdit(release) || title.isBlank()) return@launch
        try {
            if (release.id.startsWith("local:")) withContext(Dispatchers.IO) { app.library.renamePlaylist(release.id, title) }
            else app.tidal.renamePlaylist(release.id, title.trim())
            val renamed = release.copy(title = title.trim())
            ui.update { it.copy(detail = renamed, playlists = it.playlists.map { playlist -> if (playlist.id == release.id) renamed else playlist }) }
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }

    fun removeFromPlaylist(release: Release, track: Track, index: Int) = viewModelScope.launch {
        if (!canEdit(release)) return@launch
        try {
            if (release.id.startsWith("local:")) withContext(Dispatchers.IO) { app.library.removeFromPlaylist(release.id, track.id) }
            else app.tidal.removeFromPlaylist(release.id, index, track.id)
            open(release)
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }

    fun deletePlaylist(release: Release) = viewModelScope.launch {
        if (!canEdit(release)) return@launch
        try {
            if (release.id.startsWith("local:")) withContext(Dispatchers.IO) { app.library.deletePlaylist(release.id) }
            else app.tidal.deletePlaylist(release.id)
            ui.update { it.copy(playlists = it.playlists.filterNot { playlist -> playlist.id == release.id }) }
            closeDetail()
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }

    fun favoriteAlbum(release: Release) = accountAction {
        val saved = ui.value.albums.any { it.id == release.id }
        app.tidal.setAlbumFavorite(release.id, !saved)
        ui.update { it.copy(albums = if (saved) it.albums.filterNot { album -> album.id == release.id } else listOf(release) + it.albums) }
    }

    private fun accountAction(action: suspend () -> Unit) = viewModelScope.launch {
        if (!app.tidal.signedIn || ui.value.settings.offline) { notify("Connect TIDAL and turn off offline mode to make this change"); return@launch }
        try { action() }
        catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }
    private fun userMessage(error: Exception): String = when (error) {
        is ServiceException, is ProtectedStreamException -> error.message ?: "The request failed."
        is java.io.IOException -> "The connection failed. Check your network and try again."
        else -> "The request could not be completed. Please retry."
    }
    override fun onCleared() { persistQueue(); MediaController.releaseFuture(controllerFuture); super.onCleared() }
}
