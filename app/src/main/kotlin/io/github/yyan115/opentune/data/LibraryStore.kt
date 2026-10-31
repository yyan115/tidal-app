package io.github.yyan115.opentune.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.LocalPlaylist
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LibraryStore(context: Context, databaseName: String = "library.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, payload TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX recent_tracks ON tracks(played DESC)")
        createCollectionTables(db)
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createCollectionTables(db)
    }

    private fun createCollectionTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cloud_cache (account TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(account,kind))")
        db.execSQL("CREATE TABLE pending_favorites (account TEXT NOT NULL, track_id TEXT NOT NULL, favorite INTEGER NOT NULL, PRIMARY KEY(account,track_id))")
    }

    @Synchronized fun cache(account: String, kind: String): String? = readableDatabase.rawQuery(
        "SELECT payload FROM cloud_cache WHERE account=? AND kind=?", arrayOf(account, kind)).use { if (it.moveToFirst()) it.getString(0) else null }

    @Synchronized fun cache(account: String, kind: String, payload: String) {
        if (account.isBlank()) return
        writableDatabase.insertWithOnConflict("cloud_cache", null, ContentValues().apply {
            put("account", account); put("kind", kind); put("payload", payload)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun clearCache(account: String) { writableDatabase.delete("cloud_cache", "account=?", arrayOf(account)) }

    @Synchronized fun queueFavorite(account: String, trackId: String, favorite: Boolean) {
        if (account.isBlank()) return
        writableDatabase.insertWithOnConflict("pending_favorites", null, ContentValues().apply {
            put("account", account); put("track_id", trackId); put("favorite", if (favorite) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun pendingFavorites(account: String): Map<String, Boolean> = readableDatabase.rawQuery(
        "SELECT track_id,favorite FROM pending_favorites WHERE account=?", arrayOf(account)).use { cursor ->
        buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1) == 1) }
    }

    @Synchronized fun favoriteSynced(account: String, trackId: String, favorite: Boolean) {
        writableDatabase.delete("pending_favorites", "account=? AND track_id=? AND favorite=?", arrayOf(account, trackId, if (favorite) "1" else "0"))
    }

    @Synchronized fun save(track: Track) {
        val previous = get(track.id)
        val merged = if (previous?.localUri != null && track.localUri == null) track.copy(localUri = previous.localUri, quality = previous.quality) else track
        val values = ContentValues().apply { put("id", track.id); put("payload", JsonCodec.encodeTrack(merged).toString()) }
        writableDatabase.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        writableDatabase.update("tracks", values, "id=?", arrayOf(track.id))
        revision.value++
    }

    @Synchronized fun get(id: String): Track? = readableDatabase.rawQuery("SELECT payload FROM tracks WHERE id=?", arrayOf(id)).use {
        if (it.moveToFirst()) JsonCodec.decodeTrack(JSONObject(it.getString(0))) else null
    }

    @Synchronized fun localTracks(): List<Track> = queryTracks("SELECT payload FROM tracks ORDER BY rowid DESC").filter(Track::isLocal).sortedByDescending(Track::addedAt)
    @Synchronized fun favorites(): List<Track> = queryTracks("SELECT payload FROM tracks WHERE favorite=1 ORDER BY rowid DESC")
    @Synchronized fun recent(): List<Track> = queryTracks("SELECT payload FROM tracks WHERE played>0 ORDER BY played DESC LIMIT 30")
    @Synchronized fun isFavorite(id: String): Boolean = readableDatabase.rawQuery("SELECT favorite FROM tracks WHERE id=?", arrayOf(id)).use { it.moveToFirst() && it.getInt(0) == 1 }

    @Synchronized fun setFavorite(track: Track, favorite: Boolean) {
        save(track)
        writableDatabase.update("tracks", ContentValues().apply { put("favorite", if (favorite) 1 else 0) }, "id=?", arrayOf(track.id))
        revision.value++
    }

    @Synchronized fun played(track: Track) {
        save(track)
        writableDatabase.update("tracks", ContentValues().apply { put("played", System.currentTimeMillis()) }, "id=?", arrayOf(track.id))
        revision.value++
    }

    @Synchronized fun removeLocal(id: String) {
        if (id.startsWith("local:")) {
            writableDatabase.beginTransaction()
            try {
                writableDatabase.delete("playlist_tracks", "track_id=?", arrayOf(id))
                writableDatabase.delete("tracks", "id=?", arrayOf(id))
                writableDatabase.setTransactionSuccessful()
            } finally { writableDatabase.endTransaction() }
        } else get(id)?.let { track -> writableDatabase.update("tracks", ContentValues().apply {
            put("payload", JsonCodec.encodeTrack(track.copy(localUri = null, quality = null)).toString())
        }, "id=?", arrayOf(id)) }
        revision.value++
    }

    @Synchronized fun createPlaylist(title: String): LocalPlaylist {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty())
        val id = "local:${UUID.randomUUID()}"
        writableDatabase.insertOrThrow("playlists", null, ContentValues().apply { put("id", id); put("title", trimmed) })
        revision.value++
        return LocalPlaylist(id, trimmed)
    }

    @Synchronized fun playlists(): List<LocalPlaylist> = readableDatabase.rawQuery("SELECT id,title FROM playlists ORDER BY title COLLATE NOCASE", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val ids = readableDatabase.rawQuery("SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position", arrayOf(id)).use {
                    buildList { while (it.moveToNext()) add(it.getString(0)) }
                }
                add(LocalPlaylist(id, cursor.getString(1), ids))
            }
        }
    }

    @Synchronized fun addToPlaylist(playlistId: String, track: Track) {
        save(track)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?", arrayOf(playlistId)).use { it.moveToFirst(); it.getInt(0) }
            db.insertWithOnConflict("playlist_tracks", null, ContentValues().apply {
                put("playlist_id", playlistId); put("track_id", track.id); put("position", position)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    }

    @Synchronized fun playlistTracks(id: String): List<Track> = readableDatabase.rawQuery(
        "SELECT t.payload FROM tracks t JOIN playlist_tracks p ON t.id=p.track_id WHERE p.playlist_id=? ORDER BY p.position", arrayOf(id)).use {
        buildList { while (it.moveToNext()) add(JsonCodec.decodeTrack(JSONObject(it.getString(0)))) }
    }

    @Synchronized fun deletePlaylist(id: String) {
        writableDatabase.delete("playlists", "id=?", arrayOf(id)); revision.value++
    }

    @Synchronized fun renamePlaylist(id: String, title: String) {
        require(title.isNotBlank())
        writableDatabase.update("playlists", ContentValues().apply { put("title", title.trim()) }, "id=?", arrayOf(id))
        revision.value++
    }

    @Synchronized fun removeFromPlaylist(id: String, trackId: String) {
        writableDatabase.delete("playlist_tracks", "playlist_id=? AND track_id=?", arrayOf(id, trackId))
        revision.value++
    }

    @Synchronized private fun queryTracks(sql: String): List<Track> = readableDatabase.rawQuery(sql, null).use {
        buildList { while (it.moveToNext()) add(JsonCodec.decodeTrack(JSONObject(it.getString(0)))) }
    }
}
