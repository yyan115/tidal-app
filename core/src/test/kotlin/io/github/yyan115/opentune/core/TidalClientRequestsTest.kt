package io.github.yyan115.opentune.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * What the client sends to the service for each action of the listener: the method, the address, the form and the headers.
 * A request that is a little wrong, such as the name of a field or the order of a list, fails with an answer that the service refuses, and a test with a
 * made-up answer cannot see it. These tests read the request itself.
 */
class TidalClientRequestsTest {
    private class Memory(var value: Session?) : SessionStore {
        override fun read() = value
        override fun write(session: Session) { value = session }
        override fun clear() { value = null }
    }

    private lateinit var server: MockWebServer
    private lateinit var client: TidalClient

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        client = TidalClient({ ClientIdentity("test-client", "test-secret") }, Memory(Session("access", "refresh", System.currentTimeMillis() + 3_600_000, "123", "IN")),
            apiBase = server.url("/v1/").toString(), authBase = server.url("/oauth/").toString(), apiBaseV2 = server.url("/v2/").toString())
    }

    @After fun tearDown() { server.shutdown() }

    private fun answer(body: String = "{}", vararg headers: Pair<String, String>) {
        server.enqueue(MockResponse().setBody(body).apply { headers.forEach { (name, value) -> addHeader(name, value) } })
    }

    private fun taken(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    private fun RecordedRequest.where() = requestUrl!!.encodedPath.removePrefix("/v1/").removePrefix("/v2/")
    private fun RecordedRequest.query(name: String) = requestUrl!!.queryParameter(name)
    private fun RecordedRequest.form() = body.readUtf8()

    private val song = """{"id":42,"title":"Song","duration":182,"artists":[{"name":"First"}],"album":{"id":7,"title":"Album","cover":"a-b-c"}}"""
    private fun items(vararg tracks: String) = """{"items":[${tracks.joinToString(",") { """{"item":$it}""" }}]}"""

    // --- saving and following

    @Test fun savingATrackPostsItsIdAndUnsavingDeletesIt() = runBlocking {
        answer(); client.setFavorite("42", true)
        taken().let { assertEquals("POST", it.method); assertEquals("users/123/favorites/tracks", it.where()); assertEquals("trackIds=42", it.form()); assertEquals("IN", it.query("countryCode")) }
        answer(); client.setFavorite("42", false)
        taken().let { assertEquals("DELETE", it.method); assertEquals("users/123/favorites/tracks/42", it.where()) }
    }

    @Test fun savingAnAlbumPostsItsIdAndUnsavingDeletesIt() = runBlocking {
        answer(); client.setAlbumFavorite("7", true)
        taken().let { assertEquals("POST", it.method); assertEquals("users/123/favorites/albums", it.where()); assertEquals("albumIds=7", it.form()) }
        answer(); client.setAlbumFavorite("7", false)
        taken().let { assertEquals("DELETE", it.method); assertEquals("users/123/favorites/albums/7", it.where()) }
    }

    @Test fun savingAPlaylistOfSomeoneElsePostsItsUuid() = runBlocking {
        answer(); client.setPlaylistFavorite("abc-1", true)
        taken().let { assertEquals("POST", it.method); assertEquals("users/123/favorites/playlists", it.where()); assertEquals("uuids=abc-1", it.form()) }
        answer(); client.setPlaylistFavorite("abc-1", false)
        taken().let { assertEquals("DELETE", it.method); assertEquals("users/123/favorites/playlists/abc-1", it.where()) }
    }

    @Test fun followingAnArtistPostsItsIdAndUnfollowingDeletesIt() = runBlocking {
        answer(); client.setArtistFollowed("31", true)
        taken().let { assertEquals("POST", it.method); assertEquals("users/123/favorites/artists", it.where()); assertEquals("artistIds=31", it.form()) }
        answer(); client.setArtistFollowed("31", false)
        taken().let { assertEquals("DELETE", it.method); assertEquals("users/123/favorites/artists/31", it.where()) }
    }

    // --- lists

    @Test fun theNextPageOfTheSavedTracksIsAskedForByOffsetNewestFirst() = runBlocking {
        answer(items(song)); client.favorites(100)
        taken().let { assertEquals("users/123/favorites/tracks", it.where()); assertEquals("100", it.query("limit")); assertEquals("100", it.query("offset")); assertEquals("DATE", it.query("order")); assertEquals("DESC", it.query("orderDirection")) }
    }

    @Test fun theNextPagesOfAlbumsAndPlaylistsAreAskedForByOffset() = runBlocking {
        answer(items("""{"id":1,"title":"A"}""")); client.favoriteAlbums(100)
        taken().let { assertEquals("users/123/favorites/albums", it.where()); assertEquals("100", it.query("offset")); assertEquals("100", it.query("limit")) }
        answer(items("""{"uuid":"p","title":"P"}""")); client.playlists(50)
        taken().let { assertEquals("users/123/playlistsAndFavoritePlaylists", it.where()); assertEquals("50", it.query("offset")); assertEquals("50", it.query("limit")) }
    }

    @Test fun theTracksOfEachKindOfReleaseComeFromItsOwnAddress() = runBlocking {
        val cases = listOf(ReleaseType.ALBUM to "albums/9/tracks", ReleaseType.PLAYLIST to "playlists/9/items", ReleaseType.ARTIST to "artists/9/toptracks", ReleaseType.MIX to "mixes/9/items")
        for ((type, address) in cases) {
            answer(items(song)); client.tracks(Release("9", "Title", "", null, type = type), 200)
            taken().let { assertEquals(address, it.where()); assertEquals("200", it.query("offset")); assertEquals("100", it.query("limit")) }
        }
    }

    @Test fun aVideoOrAFolderHasNoListOfTracks() = runBlocking {
        for (type in listOf(ReleaseType.VIDEO, ReleaseType.FOLDER)) {
            val error = try { client.tracks(Release("9", "Title", "", null, type = type)); null } catch (error: IllegalArgumentException) { error }
            assertTrue("$type has no list of tracks", error != null)
        }
        assertEquals("Nothing was sent", 0, server.requestCount)
    }

    @Test fun radioAndMixAndArtistTracksAskForOneHundred() = runBlocking {
        answer(items(song)); client.trackRadio("42")
        taken().let { assertEquals("tracks/42/radio", it.where()); assertEquals("100", it.query("limit")) }
        answer(items(song)); client.mixTracks("mix1")
        taken().let { assertEquals("mixes/mix1/items", it.where()); assertEquals("100", it.query("limit")) }
        answer(items(song)); client.artistTopTracks("31")
        taken().let { assertEquals("artists/31/toptracks", it.where()); assertEquals("100", it.query("limit")) }
    }

    // --- playlists

    @Test fun aPlaylistIsCreatedWithItsTitleAndNoDescription() = runBlocking {
        answer("""{"uuid":"new-1","title":"Road trip"}""")
        val created = client.createPlaylist("Road trip")
        taken().let { assertEquals("POST", it.method); assertEquals("users/123/playlists", it.where()); assertEquals("title=Road+trip&description=", it.form()) }
        assertEquals("new-1", created.id); assertEquals("Road trip", created.title); assertEquals("123", created.ownerId)
    }

    @Test fun aPlaylistIsRenamedAndDeleted() = runBlocking {
        answer(); client.renamePlaylist("p1", "Evening")
        taken().let { assertEquals("POST", it.method); assertEquals("playlists/p1", it.where()); assertEquals("title=Evening", it.form()) }
        answer(); client.deletePlaylist("p1")
        taken().let { assertEquals("DELETE", it.method); assertEquals("playlists/p1", it.where()) }
    }

    @Test fun tracksAreAddedAtTheEndWithTheVersionOfThePlaylistAndDuplicatesSkipped() = runBlocking {
        answer("""{"numberOfTracks":7}""", "ETag" to "\"v7\"")
        answer()
        client.addToPlaylist("p1", listOf("1", "2"))
        assertEquals("playlists/p1", taken().where())
        taken().let {
            assertEquals("POST", it.method); assertEquals("playlists/p1/items", it.where()); assertEquals("\"v7\"", it.getHeader("If-None-Match"))
            val form = it.form(); assertTrue(form, "trackIds=1%2C2" in form && "toIndex=7" in form && "onDupes=SKIP" in form && "onArtifactNotFound=SKIP" in form)
        }
    }

    @Test fun oneTrackThatIsNotAvailableFailsAndSeveralAreSkipped() = runBlocking {
        answer("""{"numberOfTracks":0}""", "ETag" to "\"v0\""); answer()
        client.addToPlaylist("p1", "1")
        server.takeRequest()
        assertTrue(taken().form().contains("onArtifactNotFound=FAIL"))
    }

    @Test fun aFileOfTheDeviceCannotBeAddedToAPlaylistOfTidalAndNothingIsSent() = runBlocking {
        val error = try { client.addToPlaylist("p1", listOf("1", "local:abc")); null } catch (error: IllegalArgumentException) { error }
        assertTrue(error != null)
        client.addToPlaylist("p1", emptyList())
        assertEquals(0, server.requestCount)
    }

    @Test fun aTrackIsRemovedByItsPlaceAfterTheTrackThereWasChecked() = runBlocking {
        answer("{}", "ETag" to "\"v3\""); answer(items(song)); answer()
        client.removeFromPlaylist("p1", 4, "42")
        assertEquals("playlists/p1", taken().where())
        taken().let { assertEquals("playlists/p1/items", it.where()); assertEquals("1", it.query("limit")); assertEquals("4", it.query("offset")) }
        taken().let { assertEquals("DELETE", it.method); assertEquals("playlists/p1/items/4", it.where()); assertEquals("\"v3\"", it.getHeader("If-None-Match")) }
    }

    @Test fun aPlaylistThatChangedElsewhereIsNotEditedAndNothingIsDeleted() = runBlocking {
        answer("{}", "ETag" to "\"v3\""); answer(items(song))
        val error = try { client.removeFromPlaylist("p1", 4, "999"); null } catch (error: ServiceException) { error }
        assertEquals(Problem.PLAYLIST_CHANGED, error!!.problem)
        assertEquals("Only the playlist and the item were read", 2, server.requestCount)
    }

    @Test fun aPlaylistWithoutAVersionIsNotEdited() = runBlocking {
        answer("{}")
        val error = try { client.removeFromPlaylist("p1", 0, "42"); null } catch (error: ServiceException) { error }
        assertEquals(Problem.REFRESH_PLAYLIST, error!!.problem)
        assertEquals(1, server.requestCount)
    }

    @Test fun aTrackIsMovedFromItsPlaceToANewPlaceAfterTheTrackThereWasChecked() = runBlocking {
        answer("{}", "ETag" to "\"v5\""); answer(items(song)); answer()
        client.moveInPlaylist("p1", 2, 6, "42")
        server.takeRequest(); server.takeRequest()
        taken().let { assertEquals("POST", it.method); assertEquals("playlists/p1/items/2", it.where()); assertEquals("toIndex=6", it.form()); assertEquals("\"v5\"", it.getHeader("If-None-Match")) }
    }

    @Test fun aTrackThatIsNotWhereItWasExpectedIsNotMoved() = runBlocking {
        answer("{}", "ETag" to "\"v5\""); answer("""{"items":[]}""")
        val error = try { client.moveInPlaylist("p1", 2, 6, "42"); null } catch (error: ServiceException) { error }
        assertEquals(Problem.PLAYLIST_CHANGED, error!!.problem)
        assertEquals(2, server.requestCount)
    }

    // --- folders of playlists

    @Test fun foldersAreRenamedRemovedAndFilledThroughTheSecondInterface() = runBlocking {
        answer(); client.renameFolder("f1", "  Road trips ")
        taken().let { assertEquals("PUT", it.method); assertEquals("my-collection/playlists/folders/rename", it.where()); assertEquals("trn:folder:f1", it.query("trn")); assertEquals("Road trips", it.query("name")); assertTrue(it.path!!.startsWith("/v2/")) }
        answer(); client.removeFolder("f1")
        taken().let { assertEquals("PUT", it.method); assertEquals("my-collection/playlists/folders/remove", it.where()); assertEquals("trn:folder:f1", it.query("trns")) }
        answer(); client.moveToFolder("p1", "f2")
        taken().let { assertEquals("PUT", it.method); assertEquals("my-collection/playlists/folders/move", it.where()); assertEquals("f2", it.query("folderId")); assertEquals("trn:playlist:p1", it.query("trns")) }
    }

    // --- what the listener is told when a request fails

    private fun messageFor(status: Int): String? = runBlocking {
        server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
        try { client.deletePlaylist("p1"); null } catch (error: ServiceException) { error.message }
    }

    @Test fun everyKindOfFailureHasItsOwnPlainMessage() {
        assertEquals("TIDAL did not grant access. Check your subscription, region, and client configuration.", messageFor(403))
        assertEquals("This item is no longer available in your region.", messageFor(404))
        assertEquals("TIDAL is limiting requests. Wait a moment and try again.", messageFor(429))
        assertEquals("This playlist changed elsewhere. Refresh it before editing again.", messageFor(409))
        assertEquals("This playlist changed elsewhere. Refresh it before editing again.", messageFor(412))
        assertEquals("TIDAL is temporarily unavailable. Try again shortly.", messageFor(500))
        assertEquals("TIDAL is temporarily unavailable. Try again shortly.", messageFor(503))
        assertEquals("The request could not be completed (HTTP 418).", messageFor(418))
    }

    @Test fun aStatusBelowFiveHundredIsNotCalledUnavailable() {
        assertNull(messageFor(200))
        assertEquals("The request could not be completed (HTTP 499).", messageFor(499))
    }
}
