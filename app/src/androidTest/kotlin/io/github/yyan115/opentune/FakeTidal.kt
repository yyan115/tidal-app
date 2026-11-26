package io.github.yyan115.opentune

import io.github.yyan115.opentune.core.ClientIdentity
import io.github.yyan115.opentune.core.Session
import io.github.yyan115.opentune.core.SessionStore
import io.github.yyan115.opentune.core.TidalClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake TIDAL on the device, with a library that the test can change. The app talks to it through the real client, so the view model of the app runs its
 * real code: the first load, the look for changes, the hearts and the way back from a failure. The tests of a library with a real account cannot run in the
 * CI, and this is the part of the app that they were the only ones to run.
 *
 * It keeps saved tracks, albums, followed artists and playlists as lists of numbers, newest first, and it answers the lists page by page as TIDAL does.
 */
class FakeTidal : TestRule {
    private val server = MockWebServer()
    val tracks = CopyOnWriteArrayList<Int>()
    val albums = CopyOnWriteArrayList<Int>()
    val artists = CopyOnWriteArrayList<Int>()
    val playlists = CopyOnWriteArrayList<String>()

    /** Every request that arrived, as `METHOD path?query`. */
    val log = CopyOnWriteArrayList<String>()

    /** A status for the next requests of a path, such as 503. A path that holds the text is refused. */
    @Volatile var refuse: Pair<String, Int>? = null

    val userId = "9"

    private fun track(id: Int) = JSONObject().put("id", id).put("title", "Song $id").put("duration", 100)
        .put("artists", JSONArray().put(JSONObject().put("id", 1).put("name", "Artist"))).put("album", JSONObject().put("id", 5).put("title", "Album"))
    private fun album(id: Int) = JSONObject().put("id", id).put("title", "Album $id").put("artists", JSONArray().put(JSONObject().put("id", 1).put("name", "Artist")))
    private fun artist(id: Int) = JSONObject().put("id", id).put("name", "Artist $id")
    private fun playlist(id: String) = JSONObject().put("type", "USER").put("playlist", JSONObject().put("uuid", id).put("title", "Playlist $id").put("lastUpdated", "2026-01-01T00:00:00"))

    private fun page(request: RecordedRequest, total: Int, item: (Int) -> JSONObject): MockResponse {
        val url = request.requestUrl!!
        val limit = url.queryParameter("limit")?.toInt() ?: 50
        val offset = url.queryParameter("offset")?.toInt() ?: 0
        val items = JSONArray()
        for (position in offset until minOf(total, offset + limit)) items.put(item(position))
        return MockResponse().setBody(JSONObject().put("items", items).put("totalNumberOfItems", total).toString())
    }

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl!!
            val path = url.encodedPath.removePrefix("/v1/").removePrefix("/v2/")
            log += "${request.method} $path" + (url.query?.let { "?$it" } ?: "")
            refuse?.let { (part, status) -> if (path.contains(part)) return MockResponse().setResponseCode(status) }
            val base = "users/$userId/"
            return when {
                request.method == "GET" && path == base + "favorites/tracks" -> page(request, tracks.size) { JSONObject().put("created", "2026-01-01T00:00:00").put("item", track(tracks[it])) }
                request.method == "POST" && path == base + "favorites/tracks" -> {
                    val id = request.body.readUtf8().substringAfter("trackIds=").substringBefore("&").toInt()
                    if (id !in tracks) tracks.add(0, id)
                    MockResponse().setBody("{}")
                }
                request.method == "DELETE" && path.startsWith(base + "favorites/tracks/") -> { tracks.remove(path.substringAfterLast('/').toInt()); MockResponse().setBody("{}") }
                request.method == "GET" && path == base + "favorites/albums" -> page(request, albums.size) { JSONObject().put("item", album(albums[it])) }
                request.method == "GET" && path == base + "favorites/artists" -> page(request, artists.size) { JSONObject().put("item", artist(artists[it])) }
                request.method == "GET" && path == base + "playlistsAndFavoritePlaylists" -> page(request, playlists.size) { playlist(playlists[it]) }
                request.method == "GET" && path == "my-collection/playlists/folders" -> MockResponse().setBody("""{"items":[]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    /** The requests that the library asked for since [mark] of the log, without the answers of one item that look for changes. */
    fun since(mark: Int) = log.drop(mark)

    private val probe = Regex("[?&]limit=1(&|$)")

    /** The requests among [requests] that load a list of the library page by page. A look for changes asks for one item and is not one of them. */
    fun fullLoads(requests: List<String>) = requests.filter { it.startsWith("GET users/$userId/") && !probe.containsMatchIn(it) && it.contains("/favorites/") }

    /** Puts the fake in place for the app while the test runs and puts the client of the app back after it. */
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
            server.dispatcher = dispatcher
            server.start()
            val store = object : SessionStore {
                @Volatile var session: Session? = Session("access", "refresh", System.currentTimeMillis() + 24 * 3_600_000, userId, "US")
                override fun read() = session
                override fun write(session: Session) { this.session = session }
                override fun clear() { session = null }
            }
            val client = TidalClient({ ClientIdentity("test-client", "test-secret") }, store, OkHttpClient(),
                apiBase = server.url("/v1/").toString(), authBase = server.url("/oauth/").toString(), apiBaseV2 = server.url("/v2/").toString())
            app.replaceTidal(client)
            try { base.evaluate() } finally { app.replaceTidal(null); server.shutdown() }
        }
    }
}
