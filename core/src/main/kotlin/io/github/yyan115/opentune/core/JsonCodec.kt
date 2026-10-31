package io.github.yyan115.opentune.core

import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
    fun track(value: JSONObject): Track {
        val album = value.optJSONObject("album")
        val artists = value.optJSONArray("artists")
        val artist = if (artists != null && artists.length() > 0) {
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
                .filter(String::isNotBlank).distinct().joinToString(", ")
        } else value.optJSONObject("artist")?.optString("name").orEmpty()
        return Track(
            id = value.get("id").toString(),
            title = value.optString("title", "Untitled"),
            artist = artist.ifBlank { value.optString("artistName", "Unknown artist") },
            album = album?.optString("title").orEmpty(),
            albumId = album?.opt("id")?.toString().orEmpty(),
            artwork = image(album?.optString("cover") ?: value.optString("cover")),
            durationMs = value.optLong("duration") * 1000,
            explicit = value.optBoolean("explicit"),
        )
    }

    fun release(value: JSONObject, type: ReleaseType): Release = Release(
        id = (value.opt("uuid") ?: value.opt("id") ?: "").toString(),
        title = value.optString("title").ifBlank { value.optString("name", "Untitled") },
        artist = value.optJSONObject("artist")?.optString("name")
            ?: value.optJSONObject("creator")?.optString("name").orEmpty(),
        artwork = image(value.optString("cover").ifBlank {
            value.optString("squareImage").ifBlank { value.optString("image").ifBlank { value.optString("picture") } }
        }),
        subtitle = value.optString("releaseDate").take(4),
        type = type,
        ownerId = value.optJSONObject("creator")?.let { (it.opt("id") ?: it.opt("userId") ?: "").toString() }.orEmpty(),
    )

    fun items(value: JSONObject): List<JSONObject> {
        val array = value.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { it.optJSONObject("item") ?: it }
        }
    }

    fun image(id: String?): String? = id?.takeIf { it.isNotBlank() && it != "null" }?.let {
        if (it.startsWith("https://")) it
        else "https://resources.tidal.com/images/${it.replace('-', '/')}/640x640.jpg"
    }

    fun encodeTrack(track: Track) = JSONObject().apply {
        put("id", track.id); put("title", track.title); put("artist", track.artist)
        put("album", track.album); put("albumId", track.albumId)
        put("artwork", track.artwork); put("durationMs", track.durationMs)
        put("explicit", track.explicit); put("localUri", track.localUri)
        put("quality", track.quality); put("addedAt", track.addedAt)
    }

    fun decodeTrack(value: JSONObject) = Track(
        id = value.getString("id"), title = value.getString("title"), artist = value.getString("artist"),
        album = value.optString("album"), albumId = value.optString("albumId"),
        artwork = value.optString("artwork").takeIf { it.isNotBlank() && it != "null" },
        durationMs = value.optLong("durationMs"), explicit = value.optBoolean("explicit"),
        localUri = value.optString("localUri").takeIf { it.isNotBlank() && it != "null" },
        quality = value.optString("quality").takeIf { it.isNotBlank() && it != "null" },
        addedAt = value.optLong("addedAt"),
    )

    fun encodeTracks(tracks: List<Track>) = JSONArray().apply { tracks.forEach { put(encodeTrack(it)) } }.toString()
    fun decodeTracks(value: String): List<Track> = JSONArray(value).let { array ->
        (0 until array.length()).map { decodeTrack(array.getJSONObject(it)) }
    }

    fun encodeReleases(releases: List<Release>) = JSONArray().apply { releases.forEach { release -> put(JSONObject().apply {
        put("id", release.id); put("title", release.title); put("artist", release.artist); put("artwork", release.artwork)
        put("subtitle", release.subtitle); put("type", release.type.name); put("ownerId", release.ownerId)
    }) } }.toString()

    fun decodeReleases(value: String): List<Release> = JSONArray(value).let { array ->
        (0 until array.length()).map { index -> array.getJSONObject(index).let { json ->
            Release(json.getString("id"), json.getString("title"), json.getString("artist"),
                json.optString("artwork").takeIf { it.isNotBlank() && it != "null" }, json.optString("subtitle"),
                ReleaseType.valueOf(json.getString("type")), json.optString("ownerId"))
        } }
    }
}
