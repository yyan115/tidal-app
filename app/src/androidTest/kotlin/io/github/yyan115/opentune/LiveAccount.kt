package io.github.yyan115.opentune

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Session
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream

/** Shared setup for the opt-in checks that use a real TIDAL account. Nothing here runs in CI. */
object LiveAccount {
    private var supplied: Session? = null
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp

    /** Signs the app in with the session pushed to `files/live-test-session.json`, and restores the previous state afterwards. */
    fun rule() = TestRule { base: Statement, _: Description -> object : Statement() {
        override fun evaluate() {
            assumeTrue("Live account tests require explicit opt-in", InstrumentationRegistry.getArguments().getString("liveAccount") == "true")
            if (supplied == null) {
                val file = File(app.filesDir, "live-test-session.json")
                if (file.isFile) try {
                    val json = JSONObject(file.readText())
                    supplied = Session(json.getString("access_token"), json.getString("refresh_token"),
                        (json.getDouble("expires_at") * 1000).toLong(), json.getString("userId"), json.getString("countryCode"))
                } finally { file.delete() }
                else supplied = app.sessions.read()
                assumeTrue("Sign in to the debug app before running live-account checks", supplied != null)
            }
            val previous = app.sessions.read()
            app.tidal.signOut()
            app.sessions.write(requireNotNull(supplied))
            // The account belongs to a person, so a test puts it back as it found it, also when it fails, and also after a crash, from the journal.
            val restore = AccountRestore(app)
            restore.recoverAfterACrash()
            restore.remember()
            try { base.evaluate() }
            finally {
                restore.restore()
                // `-e keepSession true` leaves the account signed in for manual checks on a development device.
                if (InstrumentationRegistry.getArguments().getString("keepSession") != "true") {
                    app.tidal.signOut()
                    previous?.let(app.sessions::write)
                }
            }
        }
    } }

    /** Saves the whole screen, including dialogs, under the app's external files folder. */
    fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = requireNotNull(instrumentation.targetContext.getExternalFilesDir("live-screens"))
        folder.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(folder, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** What a test of the live account may change, kept so that it can be undone. Only what the test added or removed is undone, and what was there before stays. */
class AccountRestore(private val app: OpenTuneApp) {
    private val journal = File(app.filesDir, "live-account-journal.json")

    /** The ids to remove and the ids to add so that [now] becomes [before]. */
    data class Changes(val remove: Set<String>, val add: Set<String>) {
        val isEmpty get() = remove.isEmpty() && add.isEmpty()
    }

    private fun load(): Map<String, Set<String>> = runBlocking {
        mapOf("tracks" to app.tidal.allFavorites().map { it.id }.toSet(), "albums" to app.tidal.allFavoriteAlbums().map { it.id }.toSet(),
            "artists" to app.tidal.followedArtists().map { it.id }.toSet(), "playlists" to app.tidal.allPlaylists().map { it.id }.toSet())
    }

    fun remember() {
        val state = load()
        journal.writeText(JSONObject().apply { state.forEach { (key, ids) -> put(key, org.json.JSONArray(ids.toList())) } }.toString())
    }

    private fun readJournal(): Map<String, Set<String>>? = if (!journal.isFile) null else runCatching {
        val json = JSONObject(journal.readText())
        json.keys().asSequence().associateWith { key -> json.getJSONArray(key).let { array -> (0 until array.length()).map(array::getString).toSet() } }
    }.getOrNull()

    /** A test that crashed left its journal behind, and the account is put back before the next test starts. */
    fun recoverAfterACrash() { if (journal.isFile) restore() }

    fun restore() {
        val before = readJournal() ?: return
        val now = load()
        val failures = mutableListOf<String>()
        fun changes(key: String) = changes(before.getValue(key), now.getValue(key))
        runBlocking {
            changes("tracks").let { c -> c.remove.forEach { id -> runCatching { app.tidal.setFavorite(id, false) }.onFailure { failures += "track $id" } }
                c.add.forEach { id -> runCatching { app.tidal.setFavorite(id, true) }.onFailure { failures += "track $id" } } }
            changes("albums").let { c -> c.remove.forEach { id -> runCatching { app.tidal.setAlbumFavorite(id, false) }.onFailure { failures += "album $id" } }
                c.add.forEach { id -> runCatching { app.tidal.setAlbumFavorite(id, true) }.onFailure { failures += "album $id" } } }
            changes("artists").let { c -> c.remove.forEach { id -> runCatching { app.tidal.setArtistFollowed(id, false) }.onFailure { failures += "artist $id" } }
                c.add.forEach { id -> runCatching { app.tidal.setArtistFollowed(id, true) }.onFailure { failures += "artist $id" } } }
            // A playlist that the test made is deleted. A playlist that was there stays, and one that a test deleted cannot come back.
            changes("playlists").let { c -> c.remove.forEach { id -> runCatching { app.tidal.deletePlaylist(id) }.onFailure { failures += "playlist $id" } }
                if (c.add.isNotEmpty()) failures += "playlists that were deleted and cannot come back: ${c.add}" }
        }
        if (failures.isEmpty()) journal.delete() else throw AssertionError("The account could not be put back as it was. Check by hand: $failures")
    }

    companion object {
        fun changes(before: Set<String>, now: Set<String>) = Changes(remove = now - before, add = before - now)
    }
}
