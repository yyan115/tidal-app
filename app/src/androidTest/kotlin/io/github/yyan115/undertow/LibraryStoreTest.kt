package io.github.yyan115.undertow

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.undertow.core.JsonCodec
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.data.LibraryStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun upgradesExistingLibraryWithoutLosingTracksOrPlaylists() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val track = Track("local:migration", "Original track", "Original artist", localUri = "content://original/audio/1")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, payload TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
            old.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
            old.execSQL("INSERT INTO tracks VALUES (?,?,1,123)", arrayOf(track.id, JsonCodec.encodeTrack(track).toString()))
            old.execSQL("INSERT INTO playlists VALUES ('local:existing','Existing playlist')")
            old.execSQL("INSERT INTO playlist_tracks VALUES ('local:existing',?,0)", arrayOf(track.id))
            old.version = 1
        }
        try { LibraryStore(context, name).use { store ->
            assertEquals(track, store.get(track.id))
            assertEquals(track.id, store.favorites().single().id)
            assertEquals(track.id, store.recent().single().id)
            assertEquals(track.id, store.playlistTracks("local:existing").single().id)
            store.cache("account-a", "favorites", JsonCodec.encodeTracks(listOf(track)))
            assertNull(store.cache("account-b", "favorites"))
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun pendingFavoritesKeepTheLatestChoiceAcrossRestarts() {
        val name = "pending-favorites-test.db"
        context.deleteDatabase(name)
        try {
            LibraryStore(context, name).use { store ->
                store.queueFavorite("account-a", "42", true)
                store.queueFavorite("account-a", "42", false)
                store.favoriteSynced("account-a", "42", true)
                assertEquals(mapOf("42" to false), store.pendingFavorites("account-a"))
                assertTrue(store.pendingFavorites("account-b").isEmpty())
                store.cache("account-a", "favorites", "[]")
            }
            LibraryStore(context, name).use { store ->
                assertEquals(mapOf("42" to false), store.pendingFavorites("account-a"))
                assertEquals("[]", store.cache("account-a", "favorites"))
                store.favoriteSynced("account-a", "42", false)
                assertTrue(store.pendingFavorites("account-a").isEmpty())
                store.clearCache("account-a")
                assertNull(store.cache("account-a", "favorites"))
            }
        } finally { context.deleteDatabase(name) }
    }
}
