package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.textRes
import io.github.yyan115.opentune.problemText
import io.github.yyan115.opentune.R
import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
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
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.data.DownloadWorker
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.playback.PlaybackCommands
import io.github.yyan115.opentune.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class PlaybackState(
    val queue: List<Track> = emptyList(), val index: Int = 0,
    val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0,
    val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val quality: String = "", val error: String? = null,
    val nextIndex: Int = -1, val source: String = "",
) { val track: Track? get() = queue.getOrNull(index) }

enum class LyricsStatus { Idle, Loading, Ready, None, Offline, Error }
data class CreditsUi(val title: String, val loading: Boolean = true, val credits: List<Credit> = emptyList(), val error: String? = null)

data class AppState(
    val signedIn: Boolean = false,
    val settings: Settings = Settings(),
    val local: List<Track> = emptyList(), val favorites: List<Track> = emptyList(), val recent: List<Track> = emptyList(),
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val albums: List<Release> = emptyList(), val playlists: List<Release> = emptyList(),
    val search: SearchResult = SearchResult(), val searchQuery: String = "", val searching: Boolean = false,
    val searchError: String? = null,
    val syncing: Boolean = false, val error: String? = null, val message: String? = null,
    val signingIn: Boolean = false,
    val pkceLogin: PkceLogin? = null,
    val detail: Release? = null, val detailTracks: List<Track> = emptyList(), val detailAlbums: List<Release> = emptyList(), val detailLoading: Boolean = false,
    val downloads: List<WorkInfo> = emptyList(), val playback: PlaybackState = PlaybackState(),
    val downloadTracks: Map<String, Track> = emptyMap(),
    val lyrics: Lyrics? = null, val lyricsStatus: LyricsStatus = LyricsStatus.Idle, val lyricsTrackId: String = "",
    val routes: List<PageRoute> = emptyList(), val artists: List<Release> = emptyList(),
    val credits: CreditsUi? = null, val recentSearches: List<String> = emptyList(), val video: Release? = null,
    val sleepEndsAt: Long = 0, val sleepAtTrackEnd: Boolean = false,
    /** Playlist folder listings by folder id. The top level is stored as "root". */
    val folders: Map<String, List<Release>> = emptyMap(),
    val hasMoreFavorites: Boolean = false,
    val exportProgress: Int? = null,
)

@OptIn(FlowPreview::class)
@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as OpenTuneApp
    private val ui = MutableStateFlow(AppState(signedIn = app.tidal.signedIn, settings = app.settings.state.value,
        pkceLogin = app.sessions.pendingPkce(),
        recentSearches = application.getSharedPreferences("search", Application.MODE_PRIVATE).getString("recent", null)
            ?.let { runCatching { org.json.JSONArray(it).let { array -> (0 until array.length()).map(array::getString) } }.getOrNull() }.orEmpty()))
    val state = ui.asStateFlow()
    private val query = MutableStateFlow("")
    private val searchRetry = MutableStateFlow(0L)
    private val preferences = application.getSharedPreferences("playback", Application.MODE_PRIVATE)
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) { updateSleepTimer(extras) }
        }).buildAsync()
    private var loginJob: Job? = null
    private var detailJob: Job? = null
    private var syncJob: Job? = null
    private var lyricsJob: Job? = null
    private val searchPreferences = application.getSharedPreferences("search", Application.MODE_PRIVATE)
    private val missingLyrics = mutableSetOf<String>()
    private var cloudFavorites = emptyList<Track>()
    private var favoritesOffset = 0
    private val favoriteSyncLock = Mutex()
    private var collectionReady = false
    private var wasKeepingOffline = app.settings.state.value.keepFavoritesOffline
    private var networkCallbackRegistered = false
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModelScope.launch { if (collectionReady) sync() } }
    }

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
                            is ServiceException, is ProtectedStreamException -> app.problemText(cause)
                            else -> app.getString(R.string.error_playback_stopped)
                        })) }
                    }
                })
                restoreQueue()
                updatePlayback()
                updateSleepTimer(player.sessionExtras)
            } catch (_: Exception) { notify(app.getString(R.string.notify_service_failed)) }
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
        viewModelScope.launch { app.settings.state.collect { settings ->
            val wasOffline = ui.value.settings.offline
            ui.update { it.copy(settings = settings) }
            if (!wasOffline && settings.offline) app.http.dispatcher.cancelAll()
            if (settings.keepFavoritesOffline && !wasKeepingOffline && collectionReady) autoDownload()
            wasKeepingOffline = settings.keepFavoritesOffline
            if (wasOffline && !settings.offline && collectionReady) sync()
        } }
        viewModelScope.launch {
            WorkManager.getInstance(application).getWorkInfosByTagFlow("audio-download").collect { jobs ->
                val tracks = withContext(Dispatchers.IO) { jobs.mapNotNull { work ->
                    work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")?.let { app.library.get(it) }
                }.associateBy(Track::id) }
                ui.update { it.copy(downloads = jobs, downloadTracks = tracks) }
            }
        }
        viewModelScope.launch {
            combine(query.debounce(350).distinctUntilChanged(), app.settings.state.map { it.offline }.distinctUntilChanged(),
                ui.map { it.signedIn }.distinctUntilChanged(), searchRetry) { text, offline, signedIn, _ -> Triple(text, offline, signedIn) }
                .collectLatest { (text, offline, signedIn) ->
                ui.update { it.copy(searchQuery = text, searchError = null) }
                if (text.isBlank() || !signedIn || offline) {
                    ui.update { it.copy(search = SearchResult(), searching = false) }
                } else {
                    ui.update { it.copy(searching = true) }
                    try { val result = app.tidal.search(text); ui.update { it.copy(search = result, searching = false) } }
                    catch (error: Exception) { if (error is CancellationException) throw error; ui.update { it.copy(searching = false, searchError = userMessage(error)) } }
                }
            }
        }
        try { connectivity.registerDefaultNetworkCallback(networkCallback); networkCallbackRegistered = true }
        catch (_: SecurityException) { /* Local playback also works with network access disabled. */ }
        viewModelScope.launch {
            hydrateCollection()
            collectionReady = true
            sync()
        }
    }

    private suspend fun hydrateCollection() {
        val account = app.tidal.accountId
        if (account.isBlank()) return
        val cached = withContext(Dispatchers.IO) {
            Triple(app.library.cache(account, "favorites"), app.library.cache(account, "albums"), app.library.cache(account, "playlists"))
        }
        val cachedRoot = withContext(Dispatchers.IO) { app.library.cache(account, "folder:${TidalClient.ROOT_FOLDER}") }
        cachedRoot?.let { value -> runCatching { JsonCodec.decodeReleases(value) }.getOrNull() }?.let { list -> ui.update { it.copy(folders = it.folders + (TidalClient.ROOT_FOLDER to list)) } }
        val cachedArtists = withContext(Dispatchers.IO) { app.library.cache(account, "artists") }
        cachedArtists?.let { value -> runCatching { JsonCodec.decodeReleases(value) }.getOrNull() }?.let { list -> ui.update { it.copy(artists = list) } }
        if (account != app.tidal.accountId) return
        cloudFavorites = cached.first?.let { runCatching { JsonCodec.decodeTracks(it) }.getOrNull() }.orEmpty()
        ui.update { it.copy(albums = cached.second?.let { value -> runCatching { JsonCodec.decodeReleases(value) }.getOrNull() }.orEmpty(),
            playlists = cached.third?.let { value -> runCatching { JsonCodec.decodeReleases(value) }.getOrNull() }.orEmpty()) }
        refreshLocal()
    }

    private suspend fun refreshLocal() {
        val local = withContext(Dispatchers.IO) { app.library.localTracks() }
        val favorites = withContext(Dispatchers.IO) { app.library.favorites() }
        val recent = withContext(Dispatchers.IO) { app.library.recent() }
        val playlists = withContext(Dispatchers.IO) { app.library.playlists() }
        val pending = withContext(Dispatchers.IO) { app.library.pendingFavorites(app.tidal.accountId) }
        val localById = local.associateBy(Track::id)
        ui.update { it.copy(local = local, favorites = (favorites + cloudFavorites).filter { track -> pending[track.id] != false }.distinctBy(Track::id)
            .map { track -> localById[track.id] ?: track }, recent = recent, localPlaylists = playlists) }
    }

    fun search(text: String) { query.value = text; ui.update { it.copy(searchQuery = text) } }
    fun retrySearch() { searchRetry.update { it + 1 } }
    fun messageShown() { ui.update { it.copy(message = null) } }
    fun notify(message: String) { ui.update { it.copy(message = message) } }

    fun beginBrowserLogin(): PkceLogin {
        loginJob?.cancel()
        val login = app.tidal.beginPkce()
        app.sessions.savePkce(login)
        ui.update { it.copy(pkceLogin = login, signingIn = false, error = null) }
        return login
    }

    fun completeBrowserLogin(callback: String) {
        if (loginJob?.isActive == true) return
        val pending = app.sessions.pendingPkce() ?: run {
            val message = app.getString(R.string.login_expired)
            ui.update { it.copy(error = message) }
            notify(message)
            return
        }
        loginJob = viewModelScope.launch {
            ui.update { it.copy(signingIn = true, error = null) }
            try {
                app.tidal.completePkce(pending, callback)
                app.sessions.clearPkce()
                ui.update { it.copy(pkceLogin = null, signedIn = true, signingIn = false, error = null) }
                notify(app.getString(R.string.settings_connected)); sync()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { it.copy(signingIn = false, error = userMessage(error)) }
                notify(userMessage(error))
            }
        }
    }

    fun cancelLogin() { loginJob?.cancel(); app.sessions.clearPkce(); ui.update { it.copy(signingIn = false, pkceLogin = null, error = null) } }
    fun signOut() {
        val account = app.tidal.accountId
        val saved = ui.value.local.filter { track -> cloudFavorites.any { it.id == track.id } }
        cancelLogin(); syncJob?.cancel(); detailJob?.cancel(); app.tidal.signOut(); cloudFavorites = emptyList()
        ui.update { it.copy(signedIn = false, syncing = false, hasMoreFavorites = false, albums = emptyList(), playlists = emptyList(), artists = emptyList(),
            search = SearchResult(), searchError = null, detail = null, routes = emptyList()) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { saved.forEach { app.library.setFavorite(it, true) }; app.library.clearCache(account) }
            refreshLocal()
        }
    }

    fun sync() {
        if (!app.tidal.signedIn || ui.value.settings.offline || ui.value.syncing) return
        syncJob = viewModelScope.launch {
            ui.update { it.copy(syncing = true, error = null) }
            try {
                val account = app.tidal.accountId
                favoriteSyncLock.withLock {
                    drainFavorites(account)
                    cloudFavorites = app.tidal.allFavorites(); favoritesOffset = cloudFavorites.size
                    withContext(Dispatchers.IO) { app.library.cache(account, "favorites", JsonCodec.encodeTracks(cloudFavorites)) }
                }
                ui.update { it.copy(hasMoreFavorites = false) }
                refreshLocal()
                val albums = app.tidal.allFavoriteAlbums()
                withContext(Dispatchers.IO) { app.library.cache(account, "albums", JsonCodec.encodeReleases(albums)) }
                ui.update { it.copy(albums = albums) }
                val playlists = app.tidal.allPlaylists()
                withContext(Dispatchers.IO) { app.library.cache(account, "playlists", JsonCodec.encodeReleases(playlists)) }
                ui.update { it.copy(playlists = playlists) }
                loadFolder().join()
                val artists = app.tidal.followedArtists()
                withContext(Dispatchers.IO) { app.library.cache(account, "artists", JsonCodec.encodeReleases(artists)) }
                ui.update { it.copy(artists = artists) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { it.copy(error = userMessage(error)) }
            } finally { ui.update { it.copy(syncing = false) }; autoDownload() }
        }
    }

    /** Queues the next saved tracks for download when the listener keeps saved tracks offline. */
    private fun autoDownload() = viewModelScope.launch {
        val current = ui.value
        if (!current.settings.keepFavoritesOffline || !current.signedIn || current.settings.offline) return@launch
        if (app.filesDir.usableSpace < MIN_FREE_BYTES_FOR_AUTO_DOWNLOAD) return@launch
        AutoDownload.pick(current.favorites, current.local.map(Track::id).toSet(), AUTO_DOWNLOAD_BATCH).forEach { download(it, quiet = true) }
    }

    fun moreFavorites() = viewModelScope.launch {
        try {
            val next = app.tidal.favorites(favoritesOffset)
            cloudFavorites = (cloudFavorites + next).distinctBy(Track::id); favoritesOffset += next.size
            ui.update { it.copy(hasMoreFavorites = next.size == 100) }; refreshLocal()
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }

    /** Opens an album, artist, mix or playlist, or starts a music video. */
    fun open(release: Release) {
        when (release.type) {
            ReleaseType.ARTIST -> pushRoute(PageRoute.artist(release.id, release.title))
            ReleaseType.MIX -> pushRoute(PageRoute.mix(release.id, release.title))
            ReleaseType.VIDEO -> ui.update { it.copy(video = release) }
            ReleaseType.ALBUM -> if (app.tidal.signedIn && !ui.value.settings.offline) pushRoute(PageRoute.album(release.id, release.title)) else openDetail(release)
            ReleaseType.PLAYLIST -> openDetail(release)
            ReleaseType.FOLDER -> Unit
        }
    }
    fun openPage(route: PageRoute) = pushRoute(route)
    fun closeVideo() { ui.update { it.copy(video = null) } }
    private fun pushRoute(route: PageRoute) = ui.update { state ->
        if (state.routes.lastOrNull()?.key == route.key) state else state.copy(routes = state.routes + route, error = null)
    }

    private fun openDetail(release: Release) {
        detailJob?.cancel()
        ui.update { it.copy(detail = release, detailLoading = true, detailTracks = emptyList(), detailAlbums = emptyList(), error = null) }
        detailJob = viewModelScope.launch {
            try {
                val account = app.tidal.accountId
                val tracks = when {
                    release.id.startsWith("local:") -> withContext(Dispatchers.IO) { app.library.playlistTracks(release.id) }
                    ui.value.settings.offline -> withContext(Dispatchers.IO) {
                        val cached = app.library.cache(account, "release:${release.type}:${release.id}")?.let { runCatching { JsonCodec.decodeTracks(it) }.getOrNull() }
                        val saved = app.library.localTracks()
                        val byId = saved.associateBy(Track::id)
                        cached?.mapNotNull { byId[it.id] } ?: saved.filter { release.type == ReleaseType.ALBUM && it.albumId == release.id }
                    }
                    else -> app.tidal.allTracks(release).also { tracks ->
                        withContext(Dispatchers.IO) { app.library.cache(account, "release:${release.type}:${release.id}", JsonCodec.encodeTracks(tracks)) }
                    }
                }
                ui.update { it.copy(detailTracks = tracks, detailLoading = false) }
                if (release.type == ReleaseType.ARTIST && !ui.value.settings.offline) {
                    val albums = app.tidal.artistAlbums(release.id); ui.update { it.copy(detailAlbums = albums) }
                }
            } catch (error: Exception) { if (error is CancellationException) throw error; ui.update { it.copy(detailLoading = false, error = userMessage(error)) } }
        }
    }
    fun closeDetail() {
        if (ui.value.routes.isNotEmpty()) { ui.update { it.copy(routes = it.routes.dropLast(1)) }; return }
        detailJob?.cancel(); ui.update { it.copy(detail = null, detailTracks = emptyList(), error = null) }
    }
    fun closeAll() { detailJob?.cancel(); ui.update { it.copy(routes = emptyList(), detail = null, detailTracks = emptyList(), error = null) } }

    fun play(tracks: List<Track>, index: Int = 0, source: String = "") {
        if (tracks.isEmpty()) return
        preferences.edit().putString("source", source).apply()
        ui.update { it.copy(playback = it.playback.copy(source = source)) }
        val player = controller ?: run { notify(app.getString(R.string.notify_service_starting)); return }
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
    private fun updateSleepTimer(extras: android.os.Bundle) {
        ui.update { it.copy(sleepEndsAt = extras.getLong(PlaybackCommands.EXTRA_ENDS_AT, 0), sleepAtTrackEnd = extras.getBoolean(PlaybackCommands.EXTRA_AT_TRACK_END, false)) }
    }

    /** Pauses playback after [minutes], or at the end of the current track. Zero minutes without the flag turns the timer off. */
    fun setSleepTimer(minutes: Int, endOfTrack: Boolean = false) {
        controller?.sendCustomCommand(PlaybackCommands.SleepTimer, android.os.Bundle().apply {
            putInt(PlaybackCommands.ARG_MINUTES, minutes); putBoolean(PlaybackCommands.ARG_END_OF_TRACK, endOfTrack)
        })
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
        notify(app.getString(if (next) R.string.notify_plays_next else R.string.notify_added_queue))
    }
    fun removeQueue(index: Int) { controller?.removeMediaItem(index) }
    fun moveQueue(from: Int, to: Int) { controller?.let { if (to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun jumpTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }
    fun clearQueue() { controller?.clearMediaItems() }

    fun favorite(track: Track) = viewModelScope.launch {
        val previous = ui.value.favorites.any { it.id == track.id }
        val account = app.tidal.accountId
        try { withContext(Dispatchers.IO) {
            app.library.setFavorite(track, !previous)
            if (!track.id.startsWith("local:")) app.library.queueFavorite(account, track.id, !previous)
        } } catch (error: Exception) {
            if (error is CancellationException) throw error
            notify(app.getString(R.string.notify_track_not_saved)); return@launch
        }
        cloudFavorites = if (previous) cloudFavorites.filterNot { it.id == track.id } else cloudFavorites
        refreshLocal()
        if (!track.id.startsWith("local:") && app.tidal.signedIn && !ui.value.settings.offline) {
            try { favoriteSyncLock.withLock { drainFavorites(account) } }
            catch (error: Exception) { if (error is CancellationException) throw error; notify(app.getString(R.string.notify_saved_local_failed, userMessage(error))) }
        }
    }

    private suspend fun drainFavorites(account: String) {
        while (account.isNotBlank() && account == app.tidal.accountId && !ui.value.settings.offline) {
            val changes = withContext(Dispatchers.IO) { app.library.pendingFavorites(account) }
            if (changes.isEmpty()) return
            for ((id, favorite) in changes) {
                if (account != app.tidal.accountId || ui.value.settings.offline) return
                app.tidal.setFavorite(id, favorite)
                val track = withContext(Dispatchers.IO) { app.library.favoriteSynced(account, id, favorite); app.library.get(id) }
                if (account != app.tidal.accountId) return
                cloudFavorites = if (favorite && track != null) (listOf(track) + cloudFavorites).distinctBy(Track::id) else cloudFavorites.filterNot { it.id == id }
            }
            withContext(Dispatchers.IO) { app.library.cache(account, "favorites", JsonCodec.encodeTracks(cloudFavorites)) }
            refreshLocal()
        }
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        var imported = 0
        for (uri in uris) {
            try {
                app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                app.importer.import(uri); imported++
            } catch (error: Exception) { if (error is CancellationException) throw error; notify(app.getString(R.string.notify_import_failed)) }
        }
        if (imported > 0) notify(app.resources.getQuantityString(R.plurals.notify_files_added, imported, imported))
    }

    fun download(track: Track, quiet: Boolean = false) = viewModelScope.launch {
        if (track.isLocal || withContext(Dispatchers.IO) { app.library.get(track.id)?.isLocal == true }) { if (!quiet) notify(app.getString(R.string.notify_already_local)); return@launch }
        if (!app.tidal.signedIn) { if (!quiet) notify(app.getString(R.string.notify_download_sign_in)); return@launch }
        if (ui.value.settings.offline) { if (!quiet) notify(app.getString(R.string.notify_download_offline)); return@launch }
        withContext(Dispatchers.IO) { app.library.save(track) }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("track" to JsonCodec.encodeTrack(track).toString()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (ui.value.settings.downloadOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag("audio-download").addTag("track:${track.id}").build()
        WorkManager.getInstance(app).enqueueUniqueWork("download:${track.id}", ExistingWorkPolicy.KEEP, request)
        if (!quiet) notify(app.getString(if (ui.value.settings.downloadOnWifi && !app.sources.wifi()) R.string.notify_download_queued_wifi else R.string.notify_download_queued))
    }
    fun cancelDownload(work: WorkInfo) { WorkManager.getInstance(app).cancelWorkById(work.id) }
    fun clearDownloadHistory() { WorkManager.getInstance(app).pruneWork() }

    fun export(track: Track, destination: Uri) = viewModelScope.launch {
        if (ui.value.exportProgress != null) { notify(app.getString(R.string.notify_export_busy)); return@launch }
        ui.update { it.copy(exportProgress = 0) }
        try {
            app.exporter.write(track, destination) { percent -> ui.update { it.copy(exportProgress = percent) } }
            notify(app.getString(R.string.notify_exported))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            notify(app.getString(R.string.notify_export_failed))
        } finally { ui.update { it.copy(exportProgress = null) } }
    }

    fun removeLocal(track: Track) = viewModelScope.launch {
        try {
            controller?.let { player ->
                if (track.id.startsWith("local:")) {
                    (player.mediaItemCount - 1 downTo 0).filter { player.getMediaItemAt(it).mediaId == track.id }.forEach(player::removeMediaItem)
                } else if (player.currentMediaItem?.mediaId == track.id) player.stop()
            }
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(app).cancelUniqueWork("download:${track.id}").result.get()
                if (track.localUri?.startsWith("opentune-offline:") == true) app.sources.removeOffline(track.id)
                if (track.localUri?.startsWith("file:") == true) {
                    val file = java.io.File(Uri.parse(track.localUri).path.orEmpty())
                    if (file.canonicalPath.startsWith(app.filesDir.canonicalPath + "/downloads/") && file.exists()) check(file.delete())
                }
                app.library.removeLocal(track.id)
                if (track.id.startsWith("local:") && track.localUri?.startsWith("content:") == true) runCatching {
                    app.contentResolver.releasePersistableUriPermission(Uri.parse(track.localUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            ui.value.detail?.takeIf { it.id.startsWith("local:") }?.let(::open)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            notify(app.getString(R.string.notify_remove_failed))
        }
    }

    fun createPlaylist(title: String, initialTrack: Track? = null) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (title.isNotBlank()) {
                val playlist = app.library.createPlaylist(title)
                initialTrack?.let { app.library.addToPlaylist(playlist.id, it) }
                notify(app.getString(R.string.notify_playlist_created))
            }
        } catch (_: Exception) { notify(app.getString(R.string.notify_playlist_not_saved)) }
    }
    fun addToPlaylist(playlist: LocalPlaylist, track: Track) = viewModelScope.launch(Dispatchers.IO) {
        app.library.addToPlaylist(playlist.id, track); notify(app.getString(R.string.notify_added_to, playlist.title))
    }
    fun deletePlaylist(id: String) = viewModelScope.launch(Dispatchers.IO) { app.library.deletePlaylist(id); closeDetail() }

    fun settings(transform: (Settings) -> Settings) { app.settings.update(transform) }

    /** Loads lyrics for the current track. Results, including "no lyrics", are cached on the device. */
    fun loadLyrics() {
        val track = ui.value.playback.track ?: return
        val current = ui.value
        if (current.lyricsTrackId == track.id && current.lyricsStatus in setOf(LyricsStatus.Ready, LyricsStatus.None, LyricsStatus.Loading)) return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            ui.update { it.copy(lyrics = null, lyricsStatus = LyricsStatus.Loading, lyricsTrackId = track.id) }
            if (track.id.startsWith("local:") || track.isLocal && !track.id.all(Char::isDigit)) {
                ui.update { it.copy(lyricsStatus = LyricsStatus.None) }; return@launch
            }
            val cached = withContext(Dispatchers.IO) { app.library.cache("shared", "lyrics:${track.id}") }
            if (cached != null && cached.isNotEmpty()) {
                runCatching { LyricsCodec.decode(cached) }.getOrNull()?.let { lyrics ->
                    ui.update { it.copy(lyrics = lyrics, lyricsStatus = LyricsStatus.Ready) }; return@launch
                }
            }
            val knownMissing = track.id in missingLyrics ||
                (cached == "" && withContext(Dispatchers.IO) { app.library.cacheAge("shared", "lyrics:${track.id}") } < MISSING_LYRICS_MS)
            if (knownMissing) { ui.update { it.copy(lyricsStatus = LyricsStatus.None) }; return@launch }
            if (ui.value.settings.offline || !app.tidal.signedIn) {
                ui.update { it.copy(lyricsStatus = LyricsStatus.Offline) }; return@launch
            }
            try {
                val lyrics = app.tidal.lyrics(track.id)
                withContext(Dispatchers.IO) { app.library.cache("shared", "lyrics:${track.id}", lyrics?.let(LyricsCodec::encode).orEmpty()); app.library.pruneCache("lyrics:", 500) }
                if (lyrics == null) missingLyrics += track.id
                ui.update { it.copy(lyrics = lyrics, lyricsStatus = if (lyrics == null) LyricsStatus.None else LyricsStatus.Ready) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { it.copy(lyricsStatus = LyricsStatus.Error) }
            }
        }
    }

    /** Plays the best matches for a spoken request such as "play Nujabes". */
    fun playFromSearch(query: String) = viewModelScope.launch {
        val text = query.trim()
        if (text.isEmpty()) return@launch
        val local = withContext(Dispatchers.IO) { (app.library.localTracks() + app.library.favorites() + app.library.recent()).filter {
            it.title.contains(text, true) || it.artist.contains(text, true) || it.album.contains(text, true)
        } }
        val remote = if (app.tidal.signedIn && !ui.value.settings.offline) runCatching { app.tidal.search(text).tracks }.getOrDefault(emptyList()) else emptyList()
        val tracks = (local + remote).distinctBy(Track::id).take(30)
        if (tracks.isEmpty()) notify(app.getString(R.string.notify_nothing_found, text)) else play(tracks, 0, app.getString(R.string.nav_search))
    }

    fun scrobbleToken(): String = app.sessions.scrobbleToken().orEmpty()

    fun saveScrobbling(enabled: Boolean, server: String, token: String) {
        try { app.sessions.setScrobbleToken(token) } catch (_: Exception) { notify(app.getString(R.string.notify_token_not_saved)); return }
        settings { it.copy(scrobble = enabled, scrobbleServer = server.trim().ifBlank { "https://api.listenbrainz.org" }) }
        if (enabled) viewModelScope.launch { runCatching { app.scrobbler.flush() } }
    }

    suspend fun checkScrobbling(server: String, token: String): String = try {
        app.scrobbler.check(server, token.trim())?.let { name -> if (name.isBlank()) app.getString(R.string.scrobble_token_valid) else app.getString(R.string.scrobble_token_ok, name) } ?: app.getString(R.string.error_scrobble_token_rejected)
    } catch (error: ServiceException) { app.problemText(error) }
    catch (_: java.io.IOException) { app.getString(R.string.scrobble_unreachable) }

    suspend fun listeningStats(days: Int): ListeningStats = withContext(Dispatchers.IO) {
        val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
        val (plays, listened) = app.library.listeningTotals(since)
        val tracks = app.library.topTracks(since, 500)
        val artists = tracks.groupBy { it.track.artist }.map { (name, entries) -> name to entries.sumOf { it.plays } }.sortedByDescending { it.second }
        ListeningStats(plays, listened / 3_600_000, listened / 60_000 % 60, tracks.take(25), artists)
    }

    fun clearListeningHistory() = viewModelScope.launch(Dispatchers.IO) { app.library.clearHistory(); notify(app.getString(R.string.notify_history_cleared)) }

    fun exportBackup(destination: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = io.github.yyan115.opentune.data.BackupManager(app).toJson().toString(2)
            app.contentResolver.openOutputStream(destination, "wt")!!.use { it.write(text.toByteArray()) }
            notify(app.getString(R.string.notify_backup_saved))
        } catch (error: Exception) { if (error is CancellationException) throw error; notify(app.getString(R.string.notify_backup_failed)) }
    }

    fun importBackup(source: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = app.contentResolver.openInputStream(source)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val summary = io.github.yyan115.opentune.data.BackupManager(app).restore(JSONObject(text))
            refreshLocal()
            notify(app.getString(R.string.notify_restored, app.resources.getQuantityString(R.plurals.playlist_count, summary.playlists, summary.playlists), app.resources.getQuantityString(R.plurals.stats_listens, summary.plays, summary.plays)))
        } catch (error: IllegalArgumentException) { notify(app.getString(((error as? io.github.yyan115.opentune.data.BackupException)?.problem ?: Problem.BACKUP_INVALID).textRes())) }
        catch (error: Exception) { if (error is CancellationException) throw error; notify(app.getString(R.string.notify_backup_unreadable)) }
    }

    fun retryLyrics() { ui.update { it.copy(lyricsStatus = LyricsStatus.Idle, lyricsTrackId = "") }; loadLyrics() }

    fun showCredits(kind: String, id: String, title: String) {
        ui.update { it.copy(credits = CreditsUi(title)) }
        viewModelScope.launch {
            try {
                val credits = app.tidal.credits(kind, id)
                ui.update { state -> state.credits?.let { state.copy(credits = it.copy(loading = false, credits = credits)) } ?: state }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.update { state -> state.credits?.let { state.copy(credits = it.copy(loading = false, error = userMessage(error))) } ?: state }
            }
        }
    }
    fun dismissCredits() { ui.update { it.copy(credits = null) } }

    fun playTrackRadio(track: Track) = accountAction {
        val radio = app.tidal.trackRadio(track.id).filterNot { it.id == track.id }
        if (radio.isEmpty()) notify(app.getString(R.string.notify_no_radio_track)) else play(listOf(track) + radio)
    }
    fun playArtistRadio(artistId: String) = accountAction {
        val radio = app.tidal.artistRadio(artistId)
        if (radio.isEmpty()) notify(app.getString(R.string.notify_no_radio_artist)) else play(radio)
    }
    fun playArtistTopTracks(artistId: String, shuffle: Boolean = false) = accountAction {
        val tracks = app.tidal.artistTopTracks(artistId)
        if (tracks.isEmpty()) notify(app.getString(R.string.notify_artist_empty)) else { play(tracks); if (shuffle && !ui.value.playback.shuffle) toggleShuffle() }
    }
    fun playMix(mixId: String, shuffle: Boolean = false) = accountAction {
        val tracks = app.tidal.mixTracks(mixId)
        if (tracks.isEmpty()) notify(app.getString(R.string.notify_mix_empty)) else { play(tracks); if (shuffle && !ui.value.playback.shuffle) toggleShuffle() }
    }
    fun playTracks(tracks: List<Track>, shuffle: Boolean, source: String = "") {
        if (tracks.isEmpty()) return
        play(tracks, if (shuffle) tracks.indices.random() else 0, source)
        controller?.let { if (it.shuffleModeEnabled != shuffle) it.shuffleModeEnabled = shuffle }
    }

    /** Loads the playlists and folders inside a folder. The last result stays on screen until a new one arrives. */
    fun loadFolder(id: String = TidalClient.ROOT_FOLDER) = viewModelScope.launch {
        val account = app.tidal.accountId
        if (account.isBlank()) return@launch
        if (ui.value.folders[id] == null) {
            withContext(Dispatchers.IO) { app.library.cache(account, "folder:$id") }?.let { runCatching { JsonCodec.decodeReleases(it) }.getOrNull() }
                ?.let { list -> ui.update { it.copy(folders = it.folders + (id to list)) } }
        }
        if (!ui.value.signedIn || ui.value.settings.offline) return@launch
        try {
            val list = app.tidal.playlistFolder(id)
            withContext(Dispatchers.IO) { app.library.cache(account, "folder:$id", JsonCodec.encodeReleases(list)) }
            ui.update { it.copy(folders = it.folders + (id to list)) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (ui.value.folders[id] == null) notify(userMessage(error))
        }
    }

    private suspend fun refreshFolders() { ui.value.folders.keys.plus(TidalClient.ROOT_FOLDER).forEach { loadFolder(it).join() } }

    fun createFolder(name: String) = accountAction {
        if (name.isBlank()) return@accountAction
        app.tidal.createFolder(name); refreshFolders(); notify(app.getString(R.string.notify_folder_created))
    }

    fun renameFolder(folder: Release, name: String) = accountAction {
        if (name.isBlank()) return@accountAction
        app.tidal.renameFolder(folder.id, name); refreshFolders()
    }

    /** Deletes a folder after moving its playlists to the top level, because TIDAL hides the playlists of a deleted folder. */
    fun deleteFolder(folder: Release) = accountAction {
        val inside = app.tidal.playlistFolder(folder.id)
        if (inside.any { it.type == ReleaseType.FOLDER }) { notify(app.getString(R.string.notify_folder_nested)); return@accountAction }
        inside.forEach { app.tidal.moveToFolder(it.id, TidalClient.ROOT_FOLDER) }
        app.tidal.removeFolder(folder.id)
        ui.update { it.copy(folders = it.folders - folder.id) }
        refreshFolders(); notify(app.getString(R.string.notify_folder_deleted))
    }

    fun moveToFolder(playlist: Release, folderId: String) = accountAction {
        app.tidal.moveToFolder(playlist.id, folderId); refreshFolders()
    }

    fun toggleFollow(artist: Release) = accountAction {
        val following = ui.value.artists.any { it.id == artist.id }
        app.tidal.setArtistFollowed(artist.id, !following)
        val next = if (following) ui.value.artists.filterNot { it.id == artist.id } else listOf(artist) + ui.value.artists
        ui.update { it.copy(artists = next) }
        withContext(Dispatchers.IO) { app.library.cache(app.tidal.accountId, "artists", JsonCodec.encodeReleases(next)) }
    }

    fun rememberSearch(text: String) {
        val value = text.trim()
        if (value.length < 2) return
        val next = (listOf(value) + ui.value.recentSearches.filterNot { it.equals(value, ignoreCase = true) }).take(10)
        ui.update { it.copy(recentSearches = next) }
        searchPreferences.edit().putString("recent", org.json.JSONArray(next).toString()).apply()
    }
    fun clearSearches() { ui.update { it.copy(recentSearches = emptyList()) }; searchPreferences.edit().remove("recent").apply() }

    fun saveQueueAsPlaylist(title: String) = viewModelScope.launch(Dispatchers.IO) {
        val queue = ui.value.playback.queue
        if (title.isBlank() || queue.isEmpty()) return@launch
        try {
            val playlist = app.library.createPlaylist(title)
            queue.forEach { app.library.addToPlaylist(playlist.id, it) }
            notify(app.resources.getQuantityString(R.plurals.notify_queue_saved, queue.size, queue.size, playlist.title))
        } catch (_: Exception) { notify(app.getString(R.string.notify_playlist_not_saved)) }
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
            shuffle = player.shuffleModeEnabled, repeat = player.repeatMode, quality = quality, nextIndex = player.nextMediaItemIndex,
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
            ui.update { state -> state.copy(playback = state.playback.copy(source = preferences.getString("source", "").orEmpty())) }
        }
    }

    fun canEdit(release: Release): Boolean = release.type == ReleaseType.PLAYLIST &&
        (release.id.startsWith("local:") || (app.tidal.signedIn && release.ownerId.isNotBlank() && release.ownerId == app.tidal.accountId))

    fun createCloudPlaylist(title: String, initialTrack: Track? = null) = accountAction {
        val playlist = app.tidal.createPlaylist(title.trim())
        ui.update { it.copy(playlists = it.playlists + playlist) }
        initialTrack?.let { app.tidal.addToPlaylist(playlist.id, it.id) }
        notify(app.getString(R.string.notify_tidal_playlist_created))
    }

    fun addToCloudPlaylist(playlist: Release, track: Track) = accountAction {
        app.tidal.addToPlaylist(playlist.id, track.id)
        notify(app.getString(R.string.notify_added_to, playlist.title))
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

    fun moveInPlaylist(release: Release, position: Int, delta: Int) = viewModelScope.launch {
        val tracks = ui.value.detailTracks
        val target = position + delta
        if (!canEdit(release) || position !in tracks.indices || target !in tracks.indices) return@launch
        try {
            if (release.id.startsWith("local:")) withContext(Dispatchers.IO) { app.library.moveInPlaylist(release.id, position, target) }
            else app.tidal.moveInPlaylist(release.id, position, target, tracks[position].id)
            openDetail(release)
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
        if (!app.tidal.signedIn || ui.value.settings.offline) { notify(app.getString(R.string.notify_account_change)); return@launch }
        try { action() }
        catch (error: Exception) { if (error is CancellationException) throw error; notify(userMessage(error)) }
    }
    private fun userMessage(error: Exception): String = app.problemText(error)
    private companion object {
        const val MISSING_LYRICS_MS = 7 * 24 * 3600_000L
        const val AUTO_DOWNLOAD_BATCH = 50
        const val MIN_FREE_BYTES_FOR_AUTO_DOWNLOAD = 2L * 1024 * 1024 * 1024
    }

    override fun onCleared() {
        if (networkCallbackRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        persistQueue(); MediaController.releaseFuture(controllerFuture); super.onCleared()
    }
}
