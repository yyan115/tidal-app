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
class FakeTidal(private val signedIn: Boolean = true) : TestRule {
    private val server = MockWebServer()
    val tracks = CopyOnWriteArrayList<Int>()
    val albums = CopyOnWriteArrayList<Int>()
    val artists = CopyOnWriteArrayList<Int>()
    val playlists = CopyOnWriteArrayList<String>()

    /** Every request that arrived, as `METHOD path?query`. */
    val log = CopyOnWriteArrayList<String>()

    /** The bodies of the requests that carry a form, in the order of arrival, as `path` to form text. */
    val forms = CopyOnWriteArrayList<Pair<String, String>>()

    /** The answers for radio, mixes and top tracks, and the tracks among them that TIDAL marks as AI-generated. */
    val radio = CopyOnWriteArrayList<Int>()
    val marked = CopyOnWriteArrayList<Int>()

    /** Playlist folders: the folder ids with their names, and the folder that holds each playlist. A playlist without an entry is at the top level. */
    val folders = java.util.concurrent.ConcurrentHashMap<String, String>()
    val folderOf = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** What the next token request answers: null for a good answer, or the OAuth error code such as invalid_grant. */
    @Volatile var tokenError: String? = null

    /** A status for the next requests of a path, such as 503. A path that holds the text is refused. */
    @Volatile var refuse: Pair<String, Int>? = null

    val userId = "9"

    // Every item is a copy of an item of a real answer of the service, with its own id and title. So the app reads the fields that TIDAL really sends,
    // and a field that the app needs but the fake lacks cannot hide. The real answers are in the fixtures of the core module, and LiveContractTest keeps them true.
    private object Real {
        private fun asset(name: String) = JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("live/$name").bufferedReader().use { it.readText() })
        val track: JSONObject by lazy { asset("favorites.json").getJSONArray("items").getJSONObject(0).getJSONObject("item") }
        val album: JSONObject by lazy { asset("search.json").getJSONObject("albums").getJSONArray("items").getJSONObject(0) }
        val artist: JSONObject by lazy { asset("search.json").getJSONObject("artists").getJSONArray("items").getJSONObject(0) }
        val playlist: JSONObject by lazy { asset("search.json").getJSONObject("playlists").getJSONArray("items").getJSONObject(0) }
        fun copy(value: JSONObject) = JSONObject(value.toString())
    }

    private fun track(id: Int) = Real.copy(Real.track).put("id", id).put("title", "Song $id").put("ai", id in marked)
    private fun album(id: Int) = Real.copy(Real.album).put("id", id).put("title", "Album $id")
    private fun artist(id: Int) = Real.copy(Real.artist).put("id", id).put("name", "Artist $id")
    private fun playlistBody(id: String) = Real.copy(Real.playlist).put("uuid", id).put("title", "Playlist $id").put("lastUpdated", "2026-01-01T00:00:00.000+0000")
    private fun playlist(id: String) = JSONObject().put("type", "USER").put("playlist", playlistBody(id))

    /** The track ids that a playlist holds, in the order that they were added. */
    val playlistItems = java.util.concurrent.ConcurrentHashMap<String, CopyOnWriteArrayList<Int>>()
    private fun itemsOf(id: String) = playlistItems.getOrPut(id) { CopyOnWriteArrayList() }

    private fun folderEntry(id: String) = JSONObject().put("itemType", "FOLDER").put("name", folders[id]).put("trn", "trn:folder:$id")
        .put("data", JSONObject().put("id", id).put("totalNumberOfItems", playlists.count { folderOf[it] == id }))

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
            if (request.method != "GET") forms += path to request.body.peek().readUtf8()
            return when {
                request.method == "POST" && url.encodedPath == "/oauth/token" -> tokenError?.let { code -> MockResponse().setResponseCode(400).setBody(JSONObject().put("error", code).toString()) }
                    ?: MockResponse().setBody(JSONObject().put("access_token", "fresh-access").put("refresh_token", "fresh-refresh").put("expires_in", 86_400).toString())
                request.method == "GET" && path == "sessions" -> MockResponse().setBody(JSONObject().put("userId", userId.toInt()).put("countryCode", "US").toString())
                request.method == "GET" && (path.matches(Regex("(tracks|artists)/[^/]+/radio")) || path.matches(Regex("mixes/[^/]+/items")) || path.matches(Regex("artists/[^/]+/toptracks"))) ->
                    page(request, radio.size) { JSONObject().put("item", track(radio[it])) }
                request.method == "GET" && path.matches(Regex("playlists/[^/]+")) ->
                    MockResponse().addHeader("ETag", "\"1\"").setBody(JSONObject().put("uuid", path.substringAfter('/')).put("numberOfTracks", itemsOf(path.substringAfter('/')).size).toString())
                request.method == "POST" && path.matches(Regex("playlists/[^/]+/items")) -> {
                    val body = request.body.peek().readUtf8()
                    val ids = body.substringAfter("trackIds=").substringBefore("&").let { java.net.URLDecoder.decode(it, "UTF-8") }.split(',').map(String::toInt)
                    itemsOf(path.substringAfter("playlists/").substringBefore('/')).addAll(ids)
                    MockResponse().addHeader("ETag", "\"2\"").setBody("{}")
                }
                request.method == "PUT" && path == "my-collection/playlists/folders/create-folder" -> {
                    val id = "f${folders.size + 1}"
                    folders[id] = url.queryParameter("name").orEmpty()
                    MockResponse().setBody(folderEntry(id).toString())
                }
                request.method == "PUT" && path == "my-collection/playlists/folders/rename" -> { folders[url.queryParameter("trn")!!.substringAfterLast(':')] = url.queryParameter("name").orEmpty(); MockResponse().setBody("{}") }
                request.method == "PUT" && path == "my-collection/playlists/folders/move" -> {
                    folderOf[url.queryParameter("trns")!!.substringAfterLast(':')] = url.queryParameter("folderId").orEmpty(); MockResponse().setBody("{}")
                }
                request.method == "PUT" && path == "my-collection/playlists/folders/remove" -> { folders.remove(url.queryParameter("trns")!!.substringAfterLast(':')); MockResponse().setBody("{}") }
                request.method == "GET" && path == base + "favorites/tracks" -> page(request, tracks.size) { JSONObject().put("created", "2026-01-01T00:00:00").put("item", track(tracks[it])) }
                request.method == "POST" && path == base + "favorites/tracks" -> {
                    val id = request.body.readUtf8().substringAfter("trackIds=").substringBefore("&").toInt()
                    if (id !in tracks) tracks.add(0, id)
                    MockResponse().setBody("{}")
                }
                request.method == "DELETE" && path.startsWith(base + "favorites/tracks/") -> { tracks.remove(path.substringAfterLast('/').toInt()); MockResponse().setBody("{}") }
                request.method == "POST" && path == base + "favorites/albums" -> { add(albums, request.body.readUtf8(), "albumIds"); MockResponse().setBody("{}") }
                request.method == "DELETE" && path.startsWith(base + "favorites/albums/") -> { albums.remove(path.substringAfterLast('/').toInt()); MockResponse().setBody("{}") }
                request.method == "POST" && path == base + "favorites/artists" -> { add(artists, request.body.readUtf8(), "artistIds"); MockResponse().setBody("{}") }
                request.method == "DELETE" && path.startsWith(base + "favorites/artists/") -> { artists.remove(path.substringAfterLast('/').toInt()); MockResponse().setBody("{}") }
                request.method == "POST" && path == base + "playlists" -> {
                    val id = "made-${playlists.size + 1}"
                    playlists.add(0, id)
                    MockResponse().setBody(JSONObject().put("uuid", id).put("title", java.net.URLDecoder.decode(request.body.readUtf8().substringAfter("title=").substringBefore("&"), "UTF-8")).toString())
                }
                request.method == "DELETE" && path.startsWith("playlists/") -> { playlists.remove(path.substringAfter("playlists/").substringBefore('/')); MockResponse().setBody("{}") }
                request.method == "GET" && path == base + "favorites/albums" -> page(request, albums.size) { JSONObject().put("item", album(albums[it])) }
                request.method == "GET" && path == base + "favorites/artists" -> page(request, artists.size) { JSONObject().put("item", artist(artists[it])) }
                request.method == "GET" && path == base + "playlistsAndFavoritePlaylists" -> page(request, playlists.size) { playlist(playlists[it]) }
                request.method == "GET" && path == "my-collection/playlists/folders" -> {
                    val parent = url.queryParameter("folderId") ?: "root"
                    val entries = JSONArray()
                    if (parent == "root") folders.keys.sorted().forEach { entries.put(folderEntry(it)) }
                    playlists.filter { (folderOf[it] ?: "root") == parent }.forEach { entries.put(JSONObject().put("itemType", "PLAYLIST").put("data", playlist(it).getJSONObject("playlist"))) }
                    MockResponse().setBody(JSONObject().put("items", entries).toString())
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun add(list: MutableList<Int>, form: String, field: String) {
        val id = form.substringAfter("$field=").substringBefore("&").toInt()
        if (id !in list) list.add(0, id)
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
                @Volatile var session: Session? = if (signedIn) Session("access", "refresh", System.currentTimeMillis() + 24 * 3_600_000, userId, "US") else null
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
