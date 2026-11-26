package io.github.yyan115.opentune

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The library of a listener with an account, against a fake TIDAL that the test changes: the first load, the look for changes that were made elsewhere, the
 * hearts of the listener and the way back from a failure. The coverage report of the tests showed that the view model ran none of this without a real account.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LibrarySyncTest {
    private val fake = FakeTidal()
    private val screen = createAndroidComposeRule<MainActivity>()
    // The fake takes the place of the client first, because the view model reads the client when the screen starts.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fake).around(screen)

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val model get() = ViewModelProvider(screen.activity)[MainViewModel::class.java]
    private val saved get() = model.state.value.favorites

    @Before fun prepare() {
        screen.runOnIdle { model.settings { it.copy(offline = false, welcomeDone = true, downloadSaved = false, downloadPlayed = false, theme = "dark") } }
    }

    @After fun clean() {
        app.library.clearFavorites(app.library.favorites().map(Track::id))
        app.library.clearCache(fake.userId)
        app.library.pendingFavorites(fake.userId).keys.forEach { app.library.favoriteSynced(fake.userId, it, app.library.pendingFavorites(fake.userId)[it] == true) }
    }

    private fun waitForTheFirstLoad(expected: Int) {
        screen.waitUntil(30_000) { !model.state.value.syncing && saved.size == expected }
    }

    /**
     * The view model loads the library by itself when the screen starts, and it does so against whatever the fake holds at that time. The test waits for that
     * load to end, puts its library into the fake and loads again, so that the app has seen the library of the test when it takes its first look.
     */
    private fun startWith(vararg tracks: Int, albums: List<Int> = emptyList(), artists: List<Int> = emptyList(), playlists: List<String> = emptyList()) {
        screen.waitUntil(30_000) { fake.log.any { it.contains("playlistsAndFavoritePlaylists") && it.contains("limit=50") } && !model.state.value.syncing }
        fake.tracks.addAll(tracks.toList()); fake.albums.addAll(albums); fake.artists.addAll(artists); fake.playlists.addAll(playlists)
        screen.runOnIdle { model.sync() }
        waitForTheFirstLoad(tracks.size)
    }

    /** Waits until the fake has had no request for a while and the app does not load. A look asks first and loads after that, so the end of the look is quiet. */
    private fun quiet() {
        var seen = fake.log.size
        var since = System.currentTimeMillis()
        while (System.currentTimeMillis() - since < 700 || model.state.value.syncing) {
            Thread.sleep(50)
            if (fake.log.size != seen) { seen = fake.log.size; since = System.currentTimeMillis() }
            check(System.currentTimeMillis() - since < 30_000) { "The app did not become quiet" }
        }
    }

    private fun lookForChanges() {
        screen.runOnIdle { model.checkLibrary() }
        quiet()
    }

    @Test fun theWholeLibraryIsLoadedPageByPageAtTheStart() {
        startWith(*(1..250).toList().toIntArray(), albums = listOf(1, 2, 3), artists = listOf(7, 8), playlists = listOf("a", "b"))
        assertEquals("Saved tracks come newest first, as TIDAL sends them", (1..250).map(Int::toString), saved.map { it.id })
        assertEquals(3, model.state.value.albums.size)
        assertEquals(2, model.state.value.artists.size)
        assertEquals(2, model.state.value.playlists.size)
        val pages = fake.fullLoads(fake.log.toList()).filter { it.contains("/favorites/tracks") && it.contains("offset=") }
        assertTrue("The saved tracks came in pages of a hundred: ${pages.map { it.substringAfter("offset=").substringBefore("&") }.distinct()}", pages.any { it.contains("offset=200") })
        assertNotNull("A listener with an account is signed in", model.state.value.signedIn.takeIf { it })
    }

    @Test fun aLookForChangesWithoutChangesAsksForOneItemPerListAndLoadsNothing() {
        startWith(*(1..120).toList().toIntArray())
        val mark = fake.log.size
        repeat(3) { lookForChanges() }
        val requests = fake.since(mark)
        assertTrue("The look asked for something: $requests", requests.isNotEmpty())
        assertEquals("No list was loaded: $requests", emptyList<String>(), fake.fullLoads(requests))
    }

    @Test fun aTrackSavedElsewhereIsLoadedByTheNextLookAndOnlyThen() {
        startWith(*(1..120).toList().toIntArray())
        fake.tracks.add(0, 999)
        val mark = fake.log.size
        lookForChanges()
        screen.waitUntil(30_000) { saved.size == 121 }
        quiet()
        assertEquals("The new track is the first one", "999", saved.first().id)
        assertTrue("The change made the app load the list: ${fake.since(mark)}", fake.fullLoads(fake.since(mark)).isNotEmpty())
        val second = fake.log.size
        lookForChanges()
        assertEquals("The next look finds nothing new and loads nothing", emptyList<String>(), fake.fullLoads(fake.since(second)))
    }

    @Test fun aTrackRemovedElsewhereLeavesTheLibraryAfterTheNextLook() {
        startWith(*(1..30).toList().toIntArray())
        fake.tracks.remove(5)
        lookForChanges()
        screen.waitUntil(30_000) { saved.size == 29 }
        assertFalse(saved.any { it.id == "5" })
    }

    @Test fun aHeartOfTheListenerIsSentAndDoesNotMakeTheNextLookLoadTheLibraryAgain() {
        startWith(*(1..120).toList().toIntArray())
        val track = Track("555", "Song 555", "Artist", "Album", "5")
        screen.runOnIdle { model.favorite(track) }
        screen.waitUntil(30_000) { fake.tracks.contains(555) }
        screen.waitUntil(30_000) { saved.any { it.id == "555" } }
        // The app learns from the answer that its own change changed the list, so it does not take that for a change made elsewhere.
        Thread.sleep(800)
        val mark = fake.log.size
        lookForChanges()
        assertEquals("The look found only the change of the listener: ${fake.since(mark)}", emptyList<String>(), fake.fullLoads(fake.since(mark)))
    }

    @Test fun heartsMadeWithoutNetworkAreSentWhenTheAccountIsBack() {
        startWith(*(1..20).toList().toIntArray())
        screen.runOnIdle { model.settings { it.copy(offline = true) } }
        screen.runOnIdle { model.favorite(Track("777", "Song 777", "Artist", "Album", "5")) }
        screen.waitUntil(30_000) { saved.any { it.id == "777" } }
        assertFalse("Nothing was sent while the app is offline", fake.tracks.contains(777))
        assertEquals("The change waits", mapOf("777" to true), app.library.pendingFavorites(fake.userId))
        screen.runOnIdle { model.settings { it.copy(offline = false) } }
        screen.runOnIdle { model.sync() }
        screen.waitUntil(30_000) { fake.tracks.contains(777) }
        screen.waitUntil(30_000) { app.library.pendingFavorites(fake.userId).isEmpty() }
        assertTrue(saved.any { it.id == "777" })
    }

    @Test fun savingAnAlbumIsSentAndIsNotMistakenForAChangeElsewhere() {
        startWith(1, 2, 3, albums = listOf(1, 2, 3))
        screen.runOnIdle { model.favoriteAlbum(io.github.yyan115.opentune.core.Release("55", "Album 55", "Artist")) }
        screen.waitUntil(30_000) { fake.albums.contains(55) && model.state.value.albums.any { it.id == "55" } }
        assertEquals("The new album is the first one", "55", model.state.value.albums.first().id)
        quiet()
        val mark = fake.log.size
        lookForChanges()
        assertEquals("The look found only the change of the listener: ${fake.since(mark)}", emptyList<String>(), fake.fullLoads(fake.since(mark)))
        screen.runOnIdle { model.favoriteAlbum(io.github.yyan115.opentune.core.Release("55", "Album 55", "Artist")) }
        screen.waitUntil(30_000) { !fake.albums.contains(55) && model.state.value.albums.none { it.id == "55" } }
    }

    @Test fun followingAndUnfollowingAnArtistIsSentAndIsNotMistakenForAChangeElsewhere() {
        startWith(1, artists = listOf(7, 8))
        val artist = io.github.yyan115.opentune.core.Release("31", "Artist 31", "", type = io.github.yyan115.opentune.core.ReleaseType.ARTIST)
        screen.runOnIdle { model.toggleFollow(artist) }
        screen.waitUntil(30_000) { fake.artists.contains(31) && model.state.value.artists.any { it.id == "31" } }
        quiet()
        val mark = fake.log.size
        lookForChanges()
        assertEquals("The look found only the change of the listener: ${fake.since(mark)}", emptyList<String>(), fake.fullLoads(fake.since(mark)))
        screen.runOnIdle { model.toggleFollow(artist) }
        screen.waitUntil(30_000) { !fake.artists.contains(31) && model.state.value.artists.none { it.id == "31" } }
    }

    @Test fun aNewPlaylistAndItsDeletionAreSentAndAreNotMistakenForAChangeElsewhere() {
        startWith(1, playlists = listOf("a"))
        screen.runOnIdle { model.createCloudPlaylist("Road trip") }
        screen.waitUntil(30_000) { fake.playlists.size == 2 && model.state.value.playlists.size == 2 }
        quiet()
        val mark = fake.log.size
        lookForChanges()
        assertEquals("The look found only the change of the listener: ${fake.since(mark)}", emptyList<String>(), fake.fullLoads(fake.since(mark)))
        val made = model.state.value.playlists.first { it.id.startsWith("made-") }
        screen.runOnIdle { model.deletePlaylist(made) }
        screen.waitUntil(30_000) { fake.playlists == listOf("a") && model.state.value.playlists.size == 1 }
        quiet()
        val second = fake.log.size
        lookForChanges()
        assertEquals("The deletion is not a change elsewhere either: ${fake.since(second)}", emptyList<String>(), fake.fullLoads(fake.since(second)))
    }

    @Test fun signingOutLeavesNoAccountAndNoTracksOfItOnTheScreens() {
        startWith(*(1..12).toList().toIntArray(), albums = listOf(1), artists = listOf(7), playlists = listOf("a"))
        screen.runOnIdle { model.signOut() }
        screen.waitUntil(30_000) { !model.state.value.signedIn }
        screen.waitUntil(30_000) { saved.isEmpty() }
        assertTrue(model.state.value.albums.isEmpty() && model.state.value.artists.isEmpty() && model.state.value.playlists.isEmpty())
        assertFalse("The client has no account", app.tidal.signedIn)
        assertFalse("The offline switch belongs to the account", model.state.value.settings.offline)
        val mark = fake.log.size
        lookForChanges()
        assertEquals("A look without an account asks for nothing", emptyList<String>(), fake.since(mark))
    }

    @Test fun aFailedLoadIsNotAskedForAgainByTheLooksThatFollow() {
        startWith(*(1..50).toList().toIntArray())
        fake.tracks.add(0, 900)
        fake.refuse = "favorites/tracks" to 503
        screen.runOnIdle { model.sync() }
        screen.waitUntil(30_000) { model.state.value.error != null }
        val mark = fake.log.size
        repeat(3) { lookForChanges() }
        assertEquals("The app waits after a failure, so it asks for nothing: ${fake.since(mark)}", emptyList<String>(), fake.since(mark))
        assertEquals("What the app showed stays", 50, saved.size)
    }
}
