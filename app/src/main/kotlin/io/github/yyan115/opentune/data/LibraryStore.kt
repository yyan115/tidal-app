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

class LibraryStore(context: Context, databaseName: String = "library.db") : SQLiteOpenHelper(context, databaseName, null, 3) {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, payload TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX recent_tracks ON tracks(played DESC)")
        createCollectionTables(db)
        createHistoryTables(db)
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createCollectionTables(db)
        if (oldVersion < 3) {
            // Version 1 databases just received the new cache table above, which already has the column.
            if (oldVersion == 2) db.execSQL("ALTER TABLE cloud_cache ADD COLUMN updated INTEGER NOT NULL DEFAULT 0")
            createHistoryTables(db)
        }
    }

    private fun createHistoryTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plays (id INTEGER PRIMARY KEY AUTOINCREMENT, track_id TEXT NOT NULL, played_at INTEGER NOT NULL, listened_ms INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX plays_time ON plays(played_at DESC)")
        db.execSQL("CREATE TABLE pending_scrobbles (id INTEGER PRIMARY KEY AUTOINCREMENT, listened_at INTEGER NOT NULL, payload TEXT NOT NULL)")
    }

    private fun createCollectionTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cloud_cache (account TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, updated INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,kind))")
        db.execSQL("CREATE TABLE pending_favorites (account TEXT NOT NULL, track_id TEXT NOT NULL, favorite INTEGER NOT NULL, PRIMARY KEY(account,track_id))")
    }

    @Synchronized fun cache(account: String, kind: String): String? = readableDatabase.rawQuery(
        "SELECT payload FROM cloud_cache WHERE account=? AND kind=?", arrayOf(account, kind)).use { if (it.moveToFirst()) it.getString(0) else null }

    @Synchronized fun cache(account: String, kind: String, payload: String) {
        if (account.isBlank()) return
        writableDatabase.insertWithOnConflict("cloud_cache", null, ContentValues().apply {
            put("account", account); put("kind", kind); put("payload", payload); put("updated", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Keeps only the [keep] most recently written cache entries whose kind starts with [prefix]. */
    @Synchronized fun pruneCache(prefix: String, keep: Int) {
        writableDatabase.execSQL("DELETE FROM cloud_cache WHERE kind LIKE ? AND rowid NOT IN (SELECT rowid FROM cloud_cache WHERE kind LIKE ? ORDER BY updated DESC LIMIT ?)",
            arrayOf<Any>("$prefix%", "$prefix%", keep))
    }

    @Synchronized fun cacheAge(account: String, kind: String): Long = readableDatabase.rawQuery(
        "SELECT updated FROM cloud_cache WHERE account=? AND kind=?", arrayOf(account, kind)).use { if (it.moveToFirst()) System.currentTimeMillis() - it.getLong(0) else Long.MAX_VALUE }

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

    /** Records that a track was listened to for [listenedMs]. These rows feed the local listening statistics. */
    @Synchronized fun recordPlay(track: Track, listenedMs: Long, at: Long = System.currentTimeMillis()) {
        save(track)
        writableDatabase.insert("plays", null, ContentValues().apply { put("track_id", track.id); put("played_at", at); put("listened_ms", listenedMs) })
    }

    data class PlayCount(val track: Track, val plays: Int, val listenedMs: Long)

    @Synchronized fun topTracks(since: Long, limit: Int = 25): List<PlayCount> = readableDatabase.rawQuery(
        "SELECT t.payload, COUNT(*) c, SUM(p.listened_ms) FROM plays p JOIN tracks t ON t.id=p.track_id WHERE p.played_at>=? GROUP BY p.track_id ORDER BY c DESC, SUM(p.listened_ms) DESC LIMIT ?",
        arrayOf(since.toString(), limit.toString())).use {
        buildList { while (it.moveToNext()) add(PlayCount(JsonCodec.decodeTrack(JSONObject(it.getString(0))), it.getInt(1), it.getLong(2))) }
    }

    @Synchronized fun listeningTotals(since: Long): Pair<Int, Long> = readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(listened_ms),0) FROM plays WHERE played_at>=?", arrayOf(since.toString())).use { it.moveToFirst(); it.getInt(0) to it.getLong(1) }

    data class PlayEntry(val track: Track, val playedAt: Long, val listenedMs: Long)

    @Synchronized fun exportPlays(limit: Int = 5000): List<PlayEntry> = readableDatabase.rawQuery(
        "SELECT t.payload, p.played_at, p.listened_ms FROM plays p JOIN tracks t ON t.id=p.track_id ORDER BY p.played_at DESC LIMIT ?", arrayOf(limit.toString())).use {
        buildList { while (it.moveToNext()) add(PlayEntry(JsonCodec.decodeTrack(JSONObject(it.getString(0))), it.getLong(1), it.getLong(2))) }
    }

    /** Adds history entries that are not stored yet and returns how many were new. */
    @Synchronized fun importPlays(entries: List<PlayEntry>): Int {
        var added = 0
        for (entry in entries) {
            val known = readableDatabase.rawQuery("SELECT 1 FROM plays WHERE track_id=? AND played_at=?", arrayOf(entry.track.id, entry.playedAt.toString())).use { it.moveToFirst() }
            if (!known) { recordPlay(entry.track, entry.listenedMs, entry.playedAt); added++ }
        }
        return added
    }

    @Synchronized fun moveInPlaylist(playlistId: String, from: Int, to: Int) {
        val ids = readableDatabase.rawQuery("SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position", arrayOf(playlistId)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return
        ids.add(to, ids.removeAt(from))
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEachIndexed { position, id -> db.update("playlist_tracks", ContentValues().apply { put("position", position) }, "playlist_id=? AND track_id=?", arrayOf(playlistId, id)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    }

    @Synchronized fun clearHistory() { writableDatabase.delete("plays", null, null); writableDatabase.update("tracks", ContentValues().apply { put("played", 0) }, null, null); revision.value++ }

    @Synchronized fun queueScrobble(listenedAt: Long, payload: String) {
        writableDatabase.insert("pending_scrobbles", null, ContentValues().apply { put("listened_at", listenedAt); put("payload", payload) })
    }
    @Synchronized fun pendingScrobbles(limit: Int = 50): List<Pair<Long, String>> = readableDatabase.rawQuery(
        "SELECT id,payload FROM pending_scrobbles ORDER BY id LIMIT ?", arrayOf(limit.toString())).use { buildList { while (it.moveToNext()) add(it.getLong(0) to it.getString(1)) } }
    @Synchronized fun scrobblesSent(ids: List<Long>) { ids.forEach { writableDatabase.delete("pending_scrobbles", "id=?", arrayOf(it.toString())) } }

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
