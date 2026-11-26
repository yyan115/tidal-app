package io.github.yyan115.opentune

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.WorkInfo
import io.github.yyan115.opentune.core.Contributor
import io.github.yyan115.opentune.core.Credit
import io.github.yyan115.opentune.core.LocalPlaylist
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.ReleaseType
import io.github.yyan115.opentune.core.SearchResult
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.playback.OutputPolicy
import io.github.yyan115.opentune.playback.OutputPolicyState
import io.github.yyan115.opentune.ui.*
import java.util.UUID

/** One screen in one state. [screen] is the name of the composable, and [state] says which state of it this is. */
class ScreenCase(val screen: String, val state: String, val content: @Composable (MainViewModel) -> Unit)

/** The songs and lists that the screens show. Names are long on purpose, so that a line that is too long for a small screen shows. */
object Fixtures {
    fun track(i: Int, onDevice: Boolean = false, file: Boolean = false) = Track(if (file) "local:fx-$i" else "fx$i", "Song number $i with a title that is long enough to wrap", "Artist number $i",
        "Album number $i", "album$i", null, 215_000, localUri = if (onDevice || file) "content://fixture/$i" else null, quality = "FLAC · 16-bit · 44.1 kHz", artistId = "artist$i")
    fun tracks(count: Int, onDevice: Int = 0, files: Boolean = false) = (1..count).map { track(it, onDevice = it <= onDevice, file = files) }
    val playlist = Release("fx-playlist", "A playlist with a name that is long enough to wrap", "TIDAL", null, type = ReleaseType.PLAYLIST)
    val albums = (1..6).map { Release("fx-album$it", "Album number $it", "Artist number $it", null) }
    val failure = "TIDAL could not be reached. Check the connection and try again."
    fun work(state: WorkInfo.State, track: Track) = WorkInfo(id = UUID.randomUUID(), state = state, tags = setOf("audio-download", "track:${track.id}"), outputData = Data.EMPTY)
}

/** Every screen of the app in every state that it can be in, built from the real composables. */
@UnstableApi
object ScreenCatalog {
    private fun state(vararg parts: Pair<String, AppState>, content: @Composable (MainViewModel, AppState) -> Unit, screen: String) =
        parts.map { (name, s) -> ScreenCase(screen, name) { vm -> content(vm, s) } }

    fun cases(): List<ScreenCase> = buildList {
        val songs = Fixtures.tracks(30)
        val signed = AppState(signedIn = true, settings = Settings(welcomeDone = true), favorites = songs, recent = songs.take(5), albums = Fixtures.albums, playlists = listOf(Fixtures.playlist))
        val queueOf = { n: Int, shuffle: Boolean -> AppState(settings = Settings(welcomeDone = true), playback = PlaybackState(queue = Fixtures.tracks(n), index = if (n > 3) 2 else 0, shuffle = shuffle, nextIndex = 1, playing = true, duration = 215_000, quality = "FLAC · 16-bit · 44.1 kHz")) }

        addAll(state("loading" to AppState(detail = Fixtures.playlist, detailLoading = true), "loaded" to AppState(detail = Fixtures.playlist, detailTracks = songs),
            "empty" to AppState(detail = Fixtures.playlist), "failed" to AppState(detail = Fixtures.playlist, error = Fixtures.failure),
            "everything on the device" to AppState(detail = Fixtures.playlist, detailTracks = Fixtures.tracks(5, onDevice = 5)), screen = "DetailScreen",
            content = { vm, s -> DetailScreen(s, vm, { _, _ -> }) }))
        addAll(state("nothing asked" to AppState(signedIn = true), "results" to AppState(signedIn = true, searchQuery = "number", search = SearchResult(tracks = songs.take(8), albums = Fixtures.albums, artists = Fixtures.albums)),
            "no result" to AppState(signedIn = true, searchQuery = "zzzz"), "searching" to AppState(signedIn = true, searchQuery = "number", searching = true),
            "failed" to AppState(signedIn = true, searchQuery = "number", searchError = Fixtures.failure), "signed out" to AppState(), screen = "SearchScreen",
            content = { vm, s -> SearchScreen(s, vm, {}, {}) }))
        addAll(state("signed out" to AppState(), "signed in" to signed, "signed in and empty" to AppState(signedIn = true), screen = "LibraryScreen",
            content = { vm, s -> LibraryScreen(s, vm, {}, {}, {}, 0, {}) }))
        addAll(state("empty" to AppState(), "files and playlists" to AppState(local = Fixtures.tracks(12, files = true), localPlaylists = listOf(LocalPlaylist("local:p1", "A playlist of files", Fixtures.tracks(3, files = true).map { it.id }))),
            "downloads" to AppState(local = Fixtures.tracks(8, onDevice = 8)), screen = "OnDeviceScreen",
            content = { vm, s -> OnDeviceScreen(s, vm, {}, {}, {}, {}, {}, {}) }))
        addAll(state("signed out and empty" to AppState(), "signed in" to signed, "offline" to signed.copy(settings = Settings(welcomeDone = true, offline = true)), screen = "ListenScreen",
            content = { vm, s -> ListenScreen(s, vm, {}, {}, {}, {}, {}, {}) }))
        addAll(state("signed out" to AppState(), "signed in" to signed, screen = "SettingsScreen", content = { vm, s -> SettingsScreen(s, vm, {}, {}) }))
        val waiting = Fixtures.track(1); val failed = Fixtures.track(2)
        addAll(state("empty" to AppState(), "waiting and failed" to AppState(downloads = listOf(Fixtures.work(WorkInfo.State.ENQUEUED, waiting), Fixtures.work(WorkInfo.State.FAILED, failed)),
            downloadTracks = mapOf(waiting.id to waiting, failed.id to failed)), screen = "DownloadQueuePage", content = { vm, s -> DownloadQueuePage(s, vm) }))
        addAll(state("empty" to queueOf(0, false), "one song" to queueOf(1, false), "many songs" to queueOf(40, false), "shuffled" to queueOf(12, true), screen = "QueueSheet",
            content = { vm, s -> QueueSheet(s, vm) {} }))
        for ((name, ui) in listOf("loading" to CreditsUi("An album", loading = true), "none" to CreditsUi("An album", loading = false), "failed" to CreditsUi("An album", loading = false, error = Fixtures.failure),
            "many credits" to CreditsUi("An album", loading = false, credits = (1..20).map { Credit("Role $it", listOf(Contributor("A person with a long name $it", "$it"), Contributor("Another", "x$it"))) })))
            add(ScreenCase("CreditsSheet", name) { CreditsSheet(ui, {}, {}) })
        for ((name, track) in listOf("streamed" to Fixtures.track(1), "downloaded" to Fixtures.track(2, onDevice = true), "file" to Fixtures.track(3, file = true)))
            add(ScreenCase("SourceDetailsSheet", name) { vm -> SourceDetailsSheet(track.quality.orEmpty(), track, vm, {}) })
        // A phone that failed the check of the 24-bit output says so in the sheet, and the line must fit on every screen.
        add(ScreenCase("SourceDetailsSheet", "24-bit not supported") { vm ->
            val before = androidx.compose.runtime.remember { OutputPolicy.state }
            androidx.compose.runtime.remember { OutputPolicyState().also { it.block("The check failed."); OutputPolicy.state = it } }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { OutputPolicy.state = before } }
            val track = Fixtures.track(5).copy(quality = "FLAC · 24-bit · 96 kHz")
            SourceDetailsSheet(track.quality.orEmpty(), track, vm, {})
        })
        add(ScreenCase("SleepTimerSheet", "idle") { vm -> SleepTimerSheet(AppState(), vm) {} })
        add(ScreenCase("EqualizerSheet", "default") { EqualizerSheet(Settings(welcomeDone = true), {}, {}) })
        add(ScreenCase("AppearancePage", "default") { AppearancePage(Settings(welcomeDone = true), {}, {}) })
        add(ScreenCase("AddToPlaylistSheet", "with playlists") { vm -> AddToPlaylistSheet(Fixtures.track(1), AppState(localPlaylists = listOf(LocalPlaylist("local:p1", "A playlist of files", emptyList()))), vm, {}, {}) })
        add(ScreenCase("AddSongsSheet", "with songs") { vm -> AddSongsSheet(Release("local:p1", "A playlist of files", "", null), AppState(local = Fixtures.tracks(6, files = true)), vm, {}) })
        add(ScreenCase("NormalizationDialog", "track") { NormalizationDialog("track", {}, {}) })
        add(ScreenCase("ScrobbleDialog", "default") { vm -> ScrobbleDialog(Settings(welcomeDone = true), vm, {}) })
        add(ScreenCase("NameDialog", "new") { NameDialog("New folder", "", "Create", {}, {}) })
        add(ScreenCase("NewPlaylistDialog", "signed out") { NewPlaylistDialog(accountAvailable = false, onCreate = { _, _ -> }, onClose = {}) })
        add(ScreenCase("NewPlaylistDialog", "signed in") { NewPlaylistDialog(accountAvailable = true, onCreate = { _, _ -> }, onClose = {}, initialName = "A name that is long enough to wrap onto a second line") })
        add(ScreenCase("MoveToFolderDialog", "folders") { MoveToFolderDialog(Fixtures.playlist, (1..4).map { Release("f$it", "Folder $it", "", null, type = ReleaseType.FOLDER) }, {}, {}) })
        add(ScreenCase("AppDialog", "title text and two actions") { AppDialog({}, title = { androidx.compose.material3.Text("A question that has a longer title than most") },
            text = { androidx.compose.material3.Text("A sentence that explains what happens when the listener agrees, long enough to wrap onto a few lines on a small screen.") },
            confirmButton = { DialogConfirm({}) { androidx.compose.material3.Text("Confirm") } }, dismissButton = { DialogDismiss({}) { androidx.compose.material3.Text("Cancel") } }) })
        // The rows of a page of TIDAL: a mix with every state of its list, and the preview of an artist.
        add(ScreenCase("PageScreen", "mix with more songs to load") { vm -> PageCase(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(20, total = 50, dataPath = "pages/data/mix")), vm) })
        add(ScreenCase("PageScreen", "mix with every song") { vm -> PageCase(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(20)), vm) })
        add(ScreenCase("PageScreen", "mix with one song") { vm -> PageCase(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(1)), vm) })
        add(ScreenCase("PageScreen", "mix without songs") { vm -> PageCase(PageFixtures.page(PageFixtures.mixHeader), vm) })
        add(ScreenCase("PageScreen", "artist with a preview of the top songs") { vm -> PageCase(PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(12, title = "Top tracks", showMore = "pages/data/top")), vm) })
        add(ScreenCase("SetupScreen", "default") { SetupScreen("", {}, {}) })
        add(ScreenCase("SetupScreen", "chosen folder") { SetupScreen("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FLossless", {}, {}) })
        add(ScreenCase("WelcomeScreen", "default") { WelcomeScreen({}, {}) })
        add(ScreenCase("AvoidRepeatsPage", "default") { vm -> AvoidRepeatsPage(AppState(settings = Settings(welcomeDone = true)), vm) })
        // The page sits under the top bar of the app. The case gives it the space of the status bar, so that the clock of the emulator does not draw over its first row.
        add(ScreenCase("StatisticsPage", "default") { vm -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.statusBarsPadding()) { StatisticsPage(vm) } })
        add(ScreenCase("LicencesPage", "default") { LicencesPage {} })
        add(ScreenCase("LicenceDocumentPage", "Apache") { LicenceDocumentPage(SettingsPage.Apache) })
        for ((name, s) in listOf("playing" to queueOf(5, false), "last song" to queueOf(1, false)))
            add(ScreenCase("NowPlayingScreen", name) { vm ->
                val scope = rememberCoroutineScope()
                NowPlayingScreen(s, vm, androidx.compose.runtime.remember { PlayerSheet(true, scope) }, {}, {}, {}, {})
            })
    }
}
