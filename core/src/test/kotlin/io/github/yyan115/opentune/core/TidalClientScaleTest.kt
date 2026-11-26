package io.github.yyan115.opentune.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Libraries are long. These tests load lists of many sizes through the real client from a fake service that answers page by page, as TIDAL does,
 * and check the count, the order, the number of requests and the offsets. Sizes around each multiple of the page size are where a loop that ends too
 * early or too late shows.
 */
class TidalClientScaleTest {
    private class Memory(var value: Session?) : SessionStore {
        override fun read() = value
        override fun write(session: Session) { value = session }
        override fun clear() { value = null }
    }

    private lateinit var server: MockWebServer
    private lateinit var client: TidalClient
    private val offsets = mutableListOf<Int>()

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        client = TidalClient({ ClientIdentity("test-client", "test-secret") }, Memory(Session("access", "refresh", System.currentTimeMillis() + 3_600_000, "123", "IN")),
            apiBase = server.url("/v1/").toString(), authBase = server.url("/oauth/").toString(), apiBaseV2 = server.url("/v2/").toString())
    }

    @After fun tearDown() { server.shutdown() }

    private fun trackJson(id: Int) = JSONObject().put("id", id).put("title", "Song $id").put("duration", 180).put("artists", JSONArray().put(JSONObject().put("name", "Artist")))
    private fun albumJson(id: Int) = JSONObject().put("id", id).put("title", "Album $id").put("artists", JSONArray().put(JSONObject().put("name", "Artist")))

    /**
     * Serves [ids] page by page. [skipPerPage] items of each page are left out, as TIDAL leaves out items that are not available, and [withTotal] tells
     * whether the answer names the size of the whole list.
     */
    private fun serve(path: String, ids: () -> List<Int>, wrap: (Int) -> JSONObject, skipPerPage: Int = 0, withTotal: Boolean = true) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (!url.encodedPath.endsWith(path)) return MockResponse().setResponseCode(404)
                val limit = url.queryParameter("limit")!!.toInt()
                val offset = url.queryParameter("offset")!!.toInt()
                offsets += offset
                val all = ids()
                val page = all.drop(offset).take(limit).drop(skipPerPage.coerceAtMost(limit))
                val body = JSONObject().put("items", JSONArray().apply { page.forEach { put(JSONObject().put("item", wrap(it))) } })
                if (withTotal) body.put("totalNumberOfItems", all.size)
                return MockResponse().setBody(body.toString())
            }
        }
    }

    private fun requests(size: Int, page: Int) = if (size == 0) 1 else (size + page - 1) / page

    @Test fun savedTracksOfEverySizeAroundAPageAreLoadedWholeInOrder() {
        for (size in listOf(0, 1, 99, 100, 101, 199, 200, 201, 599, 600, 601, 999, 1000, 1001, 2500)) {
            offsets.clear()
            serve("favorites/tracks", { (1..size).toList() }, ::trackJson)
            val loaded = runBlocking { client.allFavorites() }
            assertEquals("count for $size", size, loaded.size)
            assertEquals("order for $size", (1..size).map(Int::toString), loaded.map { it.id })
            assertEquals("requests for $size", requests(size, 100), offsets.size)
            assertEquals("offsets for $size", (0 until requests(size, 100)).map { it * 100 }, offsets)
        }
    }

    @Test fun savedAlbumsOfAThousandAreLoadedWhole() {
        serve("favorites/albums", { (1..1000).toList() }, ::albumJson)
        val loaded = runBlocking { client.allFavoriteAlbums() }
        assertEquals(1000, loaded.size)
        assertEquals(10, offsets.size)
        assertTrue(loaded.all { it.type == ReleaseType.ALBUM })
    }

    @Test fun playlistsAreLoadedFiftyAtATime() {
        serve("playlistsAndFavoritePlaylists", { (1..120).toList() }, { JSONObject().put("uuid", "p$it").put("title", "Playlist $it") })
        val loaded = runBlocking { client.allPlaylists() }
        assertEquals(120, loaded.size)
        assertEquals(listOf(0, 50, 100), offsets)
    }

    @Test fun aServiceThatLeavesOutUnavailableItemsStillGivesTheWholeListAndEnds() {
        // TIDAL answers with fewer items than the page size when it leaves some out. The size that it names counts all of them.
        serve("favorites/tracks", { (1..250).toList() }, ::trackJson, skipPerPage = 5)
        val loaded = runBlocking { client.allFavorites() }
        assertEquals("Each full page loses five, and the last page of fifty loses five too", 95 + 95 + 45, loaded.size)
        assertEquals(listOf(0, 100, 200), offsets)
    }

    @Test fun withoutASizeAShortPageEndsTheList() {
        serve("favorites/tracks", { (1..250).toList() }, ::trackJson, withTotal = false)
        assertEquals(250, runBlocking { client.allFavorites() }.size)
        assertEquals(listOf(0, 100, 200), offsets)
    }

    @Test fun withoutASizeAListOfExactPagesEndsOnTheEmptyPageAfterIt() {
        serve("favorites/tracks", { (1..200).toList() }, ::trackJson, withTotal = false)
        assertEquals(200, runBlocking { client.allFavorites() }.size)
        assertEquals("The empty page after the last full one tells the end", listOf(0, 100, 200), offsets)
    }

    @Test fun aTrackSavedElsewhereWhileTheListLoadsIsNotListedTwice() {
        // A new track goes to the top while the second page is asked, so the list shifts by one and the last track of the first page comes again.
        var calls = 0
        serve("favorites/tracks", { if (++calls >= 2) listOf(1000) + (1..250).toList() else (1..250).toList() }, ::trackJson)
        val loaded = runBlocking { client.allFavorites() }
        assertEquals("A list must never hold one track twice, because a list on the screen is keyed by the id", loaded.map { it.id }.distinct(), loaded.map { it.id })
    }
}
