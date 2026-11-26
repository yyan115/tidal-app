package io.github.yyan115.opentune.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/** The requests of the functions that the other request tests do not reach: pages, more items of a module, radio, followed artists, artist albums and video. */
class TidalClientMoreRequestsTest {
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
            apiBase = server.url("/v1/").toString(), authBase = server.url("/oauth/").toString(), apiBaseV2 = server.url("/v2/").toString(), locale = { "en_GB" })
    }

    @After fun tearDown() { server.shutdown() }

    private fun items(vararg objects: JSONObject) = JSONObject().put("items", JSONArray(objects.toList())).toString()
    private fun track(id: Int) = JSONObject().put("id", id).put("title", "Song $id").put("duration", 120).put("artists", JSONArray().put(JSONObject().put("name", "A")))
    private fun artist(id: Int) = JSONObject().put("id", id).put("name", "Artist $id")

    @Test fun aPageIsAskedForWithTheLanguageOfTheListenerAndThePhoneLayout() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"p","title":"Page","rows":[]}"""))
        val page = client.pageJson("pages/artist", mapOf("artistId" to "77"))
        assertEquals("Page", page.getString("title"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/pages/artist", request.requestUrl!!.encodedPath)
        assertEquals("77", request.requestUrl!!.queryParameter("artistId"))
        assertEquals("en_GB", request.requestUrl!!.queryParameter("locale"))
        assertEquals("PHONE", request.requestUrl!!.queryParameter("deviceType"))
        assertEquals("IN", request.requestUrl!!.queryParameter("countryCode"))
    }

    @Test fun morePageItemsAreAskedForByOffsetAndLimitThroughThePathOfTheModule() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        client.moduleItems("pages/data/abc?artistId=77", offset = 50, limit = 25)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/pages/data/abc", url.encodedPath)
        assertEquals("The path of the module keeps its own query", "77", url.queryParameter("artistId"))
        assertEquals("50", url.queryParameter("offset"))
        assertEquals("25", url.queryParameter("limit"))
        assertEquals("en_GB", url.queryParameter("locale"))
    }

    @Test fun artistRadioAsksForAHundredTracksOfTheArtist() = runBlocking {
        server.enqueue(MockResponse().setBody(items(track(1), track(2))))
        val tracks = client.artistRadio("77")
        assertEquals(listOf("1", "2"), tracks.map { it.id })
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/artists/77/radio", url.encodedPath)
        assertEquals("100", url.queryParameter("limit"))
    }

    @Test fun followedArtistsAreLoadedByPageAndNeverListedTwice() = runBlocking {
        val first = items(*(1..100).map(::artist).toTypedArray())
        // The second page repeats the artist that moved down when another artist was followed in the meantime.
        server.enqueue(MockResponse().setBody(JSONObject(first).put("totalNumberOfItems", 102).toString()))
        server.enqueue(MockResponse().setBody(JSONObject(items(artist(100), artist(101), artist(102))).put("totalNumberOfItems", 102).toString()))
        val followed = client.followedArtists()
        assertEquals((1..102).map(Int::toString), followed.map { it.id })
        assertTrue(followed.all { it.type == ReleaseType.ARTIST })
        assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
        val second = server.takeRequest().requestUrl!!
        assertEquals("/v1/users/123/favorites/artists", second.encodedPath)
        assertEquals("100", second.queryParameter("offset"))
    }

    @Test fun anArtistsAlbumsAreLoadedHundredAtATime() = runBlocking {
        val album = { id: Int -> JSONObject().put("id", id).put("title", "Album $id").put("artists", JSONArray().put(artist(77))) }
        server.enqueue(MockResponse().setBody(JSONObject(items(album(1), album(2))).put("totalNumberOfItems", 2).toString()))
        val albums = client.artistAlbums("77")
        assertEquals(2, albums.size)
        assertTrue(albums.all { it.type == ReleaseType.ALBUM })
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/artists/77/albums", url.encodedPath)
        assertEquals("100", url.queryParameter("limit"))
    }

    @Test fun aVideoIsAskedForInHighQualityAsAStreamAndGivesItsAddress() = runBlocking {
        val manifest = Base64.getEncoder().encodeToString("""{"encryptionType":"NONE","urls":["https://video.example/clip.m3u8"]}""".toByteArray())
        server.enqueue(MockResponse().setBody("""{"assetPresentation":"FULL","manifest":"$manifest"}"""))
        assertEquals("https://video.example/clip.m3u8", client.videoUrl("555"))
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/videos/555/playbackinfopostpaywall", url.encodedPath)
        assertEquals("HIGH", url.queryParameter("videoquality"))
        assertEquals("STREAM", url.queryParameter("playbackmode"))
        assertEquals("FULL", url.queryParameter("assetpresentation"))
    }

    @Test fun aVideoThatTidalOffersOnlyAsAPreviewIsRefused() {
        val manifest = Base64.getEncoder().encodeToString("""{"encryptionType":"NONE","urls":["https://video.example/clip.m3u8"]}""".toByteArray())
        server.enqueue(MockResponse().setBody("""{"assetPresentation":"PREVIEW","manifest":"$manifest"}"""))
        val error = try { runBlocking { client.videoUrl("555") }; null } catch (error: ServiceException) { error }
        assertEquals(403, error?.status)
    }
}
