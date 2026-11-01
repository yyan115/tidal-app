package io.github.yyan115.opentune.data

import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.AudioQuality
import io.github.yyan115.opentune.core.JsonCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves and restores what lives only on the device: settings, device playlists and listening history.
 * TIDAL data stays in the account, and account tokens, downloads and imported files are never included.
 */
@UnstableApi
class BackupManager(private val app: OpenTuneApp) {
    data class Summary(val playlists: Int, val plays: Int)

    fun toJson(): JSONObject {
        val settings = app.settings.state.value
        return JSONObject().apply {
            put("format", "opentune-backup"); put("version", 1); put("createdAt", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("quality", settings.quality.name); put("mobileQuality", settings.mobileQuality.name); put("downloadOnWifi", settings.downloadOnWifi)
                put("theme", settings.theme); put("colorTheme", settings.colorTheme); put("normalization", settings.normalization)
                put("equalizer", settings.equalizer); put("equalizerLevels", JSONArray(settings.equalizerLevels)); put("bitPerfect", settings.bitPerfect)
                put("keepFavoritesOffline", settings.keepFavoritesOffline); put("scrobble", settings.scrobble); put("scrobbleServer", settings.scrobbleServer)
            })
            put("playlists", JSONArray().apply {
                app.library.playlists().forEach { playlist ->
                    // Imported files cannot be restored on another install, so only TIDAL tracks are kept.
                    val tracks = app.library.playlistTracks(playlist.id).filterNot { it.id.startsWith("local:") }
                    put(JSONObject().put("title", playlist.title).put("tracks", JSONArray(tracks.map(JsonCodec::encodeTrack))))
                }
            })
            put("plays", JSONArray().apply {
                app.library.exportPlays().filterNot { it.track.id.startsWith("local:") }.forEach {
                    put(JSONObject().put("track", JsonCodec.encodeTrack(it.track)).put("playedAt", it.playedAt).put("listenedMs", it.listenedMs))
                }
            })
        }
    }

    /** Merges a backup into this install. Existing playlists with the same name receive only the tracks they lack. */
    fun restore(json: JSONObject): Summary {
        require(json.optString("format") == "opentune-backup") { "This file is not an OpenTune backup." }
        require(json.optInt("version") in 1..1) { "This backup comes from a newer version of OpenTune." }
        json.optJSONObject("settings")?.let { values ->
            app.settings.update { current ->
                fun quality(name: String, fallback: AudioQuality) = runCatching { AudioQuality.valueOf(values.getString(name)) }.getOrDefault(fallback)
                current.copy(
                    quality = quality("quality", current.quality), mobileQuality = quality("mobileQuality", current.mobileQuality),
                    downloadOnWifi = values.optBoolean("downloadOnWifi", current.downloadOnWifi),
                    theme = values.optString("theme", current.theme), colorTheme = values.optString("colorTheme", current.colorTheme),
                    normalization = values.optString("normalization", current.normalization), equalizer = values.optBoolean("equalizer", current.equalizer),
                    equalizerLevels = values.optJSONArray("equalizerLevels")?.let { array -> (0 until array.length()).map(array::getInt) } ?: current.equalizerLevels,
                    bitPerfect = values.optBoolean("bitPerfect", current.bitPerfect), keepFavoritesOffline = values.optBoolean("keepFavoritesOffline", current.keepFavoritesOffline),
                    scrobble = values.optBoolean("scrobble", current.scrobble), scrobbleServer = values.optString("scrobbleServer", current.scrobbleServer),
                )
            }
        }
        var playlists = 0
        json.optJSONArray("playlists")?.let { array ->
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                val title = entry.getString("title")
                val existing = app.library.playlists().firstOrNull { it.title == title }
                val playlist = existing ?: app.library.createPlaylist(title)
                val present = existing?.trackIds.orEmpty().toSet()
                val tracks = entry.optJSONArray("tracks")
                if (tracks != null) for (position in 0 until tracks.length()) {
                    val track = JsonCodec.decodeTrack(tracks.getJSONObject(position))
                    if (track.id !in present) app.library.addToPlaylist(playlist.id, track)
                }
                playlists++
            }
        }
        val plays = json.optJSONArray("plays")?.let { array ->
            app.library.importPlays((0 until array.length()).map { position ->
                array.getJSONObject(position).let { LibraryStore.PlayEntry(JsonCodec.decodeTrack(it.getJSONObject("track")), it.getLong("playedAt"), it.getLong("listenedMs")) }
            })
        } ?: 0
        return Summary(playlists, plays)
    }
}
