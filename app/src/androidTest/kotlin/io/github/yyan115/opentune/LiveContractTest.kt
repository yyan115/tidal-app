package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Keeps the fake TIDAL honest. The tests of the library run against a fake service whose answers are copies of real answers (the fixtures of the core module).
 * They cannot tell when TIDAL changes its answers. This test asks the real service the same questions as the capture of those fixtures and compares the
 * structure of the answer with the fixture: the name and the kind of every field, and not its value. A field that the fixture has and the real answer lacks, or
 * a field that changed its kind, fails the test, and the message names it. A field that the real answer has and the fixture lacks is only listed.
 *
 * It needs an account, so it runs by hand before a release: `-e liveAccount true` with the debug app signed in, or the session pushed as for the other live tests.
 * `-e captureFixtures true` also saves the raw answers, and `scripts/refresh_fixtures.sh` turns them into new fixtures after a failure.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LiveContractTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    @get:Rule val account = LiveAccount.rule()

    private val http = OkHttpClient()
    private val capture get() = InstrumentationRegistry.getArguments().getString("captureFixtures") == "true"
    private val folder get() = File(app.getExternalFilesDir(null), "capture").apply { mkdirs() }

    private fun get(path: String): JSONObject {
        val session = requireNotNull(app.sessions.read())
        val url = "https://api.tidal.com/v1/$path" + (if (path.contains("?")) "&" else "?") + "countryCode=${session.country}"
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${session.accessToken}").build()).execute().use { response ->
            check(response.isSuccessful) { "TIDAL answered ${response.code} to $path" }
            return JSONObject(response.body.string())
        }
    }

    private fun fixture(name: String) = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("live/$name").bufferedReader().use { it.readText() })

    /** The differences that break the fake: what the fixture has and the live answer lacks, and a field that changed kind. */
    private fun differences(name: String, live: JSONObject): Pair<List<String>, List<String>> {
        if (capture) File(folder, name).writeText(live.toString(1))
        return ContractShapes.compare(name, fixture(name), live)
    }

    @Test fun theAnswersOfTidalHaveTheShapeOfTheFixtures() {
        val search = get("search?query=Norah%20Jones&types=TRACKS,ALBUMS,ARTISTS,PLAYLISTS&limit=4")
        val artistId = search.getJSONObject("artists").getJSONArray("items").getJSONObject(0).get("id").toString()
        val albumId = search.getJSONObject("albums").getJSONArray("items").getJSONObject(0).get("id").toString()
        val userId = requireNotNull(app.sessions.read()).userId
        val answers = linkedMapOf(
            "search.json" to search,
            "artist.json" to runBlocking { app.tidal.pageJson("pages/artist", mapOf("artistId" to artistId)) },
            "album.json" to runBlocking { app.tidal.pageJson("pages/album", mapOf("albumId" to albumId)) },
            "home.json" to runBlocking { app.tidal.pageJson("pages/home") },
            "favorites.json" to get("users/$userId/favorites/tracks?limit=5&offset=0&order=DATE&orderDirection=DESC"),
        )
        val broken = mutableListOf<String>()
        val added = mutableListOf<String>()
        for ((name, live) in answers) differences(name, live).let { broken += it.first; added += it.second }
        println("CONTRACT new fields (not a failure): ${added.take(40)}")
        assertTrue("TIDAL changed its answers. Refresh the fixtures with scripts/refresh_fixtures.sh and look at the fake:\n" + broken.joinToString("\n"), broken.isEmpty())
    }

    @Test fun theListsThatTheLookForChangesAsksForStillAnswerWithATotalAndTheItems() {
        val userId = requireNotNull(app.sessions.read()).userId
        for (path in listOf("favorites/tracks?limit=1&order=DATE&orderDirection=DESC", "favorites/albums?limit=1", "favorites/artists?limit=1")) {
            val answer = get("users/$userId/$path")
            assertTrue("$path has a total", answer.has("totalNumberOfItems") && answer.get("totalNumberOfItems") is Number)
            assertTrue("$path has a list of items", answer.has("items") && answer.get("items") is JSONArray)
        }
        val playlists = get("users/$userId/playlistsAndFavoritePlaylists?limit=1")
        assertTrue("The playlists have a total and items", playlists.get("totalNumberOfItems") is Number && playlists.get("items") is JSONArray)
        val first = playlists.getJSONArray("items").optJSONObject(0)
        if (first != null) assertTrue("A playlist names the time of its last change, which the look for changes reads", (first.optJSONObject("playlist") ?: first).has("lastUpdated"))
    }
}
