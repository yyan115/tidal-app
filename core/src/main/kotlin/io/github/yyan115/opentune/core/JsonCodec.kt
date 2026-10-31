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
        val primary = artists?.optJSONObject(0) ?: value.optJSONObject("artist")
        return Track(
            id = value.get("id").toString(),
            title = value.optString("title", "Untitled").let { title ->
                val version = value.optString("version").takeIf { it.isNotBlank() && it != "null" }
                if (version != null && !title.contains(version, ignoreCase = true)) "$title ($version)" else title
            },
            artist = artist.ifBlank { value.optString("artistName", "Unknown artist") },
            album = album?.optString("title").orEmpty(),
            albumId = album?.opt("id")?.toString().orEmpty(),
            artwork = image(album?.optString("cover") ?: value.optString("cover")),
            durationMs = value.optLong("duration") * 1000,
            explicit = value.optBoolean("explicit"),
            artistId = primary?.opt("id")?.toString()?.takeIf { it != "null" }.orEmpty(),
            trackNumber = value.optInt("trackNumber"),
            replayGain = number(value, "replayGain"),
            peak = number(value, "peak"),
            tags = tags(value),
        )
    }

    /** Quality and format badges such as HIRES_LOSSLESS or DOLBY_ATMOS. */
    fun tags(value: JSONObject): List<String> {
        val result = linkedSetOf<String>()
        val tagArray = value.optJSONObject("mediaMetadata")?.optJSONArray("tags")
        if (tagArray != null) for (index in 0 until tagArray.length()) tagArray.optString(index).takeIf(String::isNotBlank)?.let(result::add)
        val modes = value.optJSONArray("audioModes")
        if (modes != null) for (index in 0 until modes.length()) if (modes.optString(index) == "DOLBY_ATMOS") result.add("DOLBY_ATMOS")
        return result.toList()
    }

    private fun number(value: JSONObject, name: String): Double? =
        if (value.has(name) && !value.isNull(name)) value.optDouble(name, Double.NaN).takeIf { !it.isNaN() } else null

    fun release(value: JSONObject, type: ReleaseType): Release {
        val artists = value.optJSONArray("artists")
        val firstArtist = artists?.optJSONObject(0) ?: value.optJSONObject("artist")
        val artistName = value.optJSONObject("artist")?.optString("name")?.takeIf(String::isNotBlank)
            ?: artists?.let { list -> (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("name") }.filter(String::isNotBlank).distinct().joinToString(", ") }
            ?: value.optJSONObject("creator")?.optString("name").orEmpty()
        val mixImage = value.optJSONObject("images")?.let { images ->
            (images.optJSONObject("MEDIUM") ?: images.optJSONObject("LARGE") ?: images.optJSONObject("SMALL"))?.optString("url")
        }
        val artwork = when (type) {
            ReleaseType.VIDEO -> value.optString("imageId").takeIf { it.isNotBlank() && it != "null" }?.let { image(it, "640x360") }
            ReleaseType.MIX -> mixImage?.takeIf(String::isNotBlank)
            // Artist pictures exist at 750x750, not at the 640x640 used for album covers.
            ReleaseType.ARTIST -> image(value.optString("picture").takeIf { it != "null" }, "750x750")
                ?: image(value.optString("selectedAlbumCoverFallback").takeIf { it != "null" })
            else -> image(value.optString("cover").takeIf { it != "null" }.orEmpty().ifBlank {
                value.optString("squareImage").takeIf { it != "null" }.orEmpty().ifBlank {
                    value.optString("image").takeIf { it != "null" }.orEmpty().ifBlank {
                        value.optString("picture").takeIf { it != "null" }.orEmpty().ifBlank {
                            value.optString("selectedAlbumCoverFallback").takeIf { it != "null" }.orEmpty()
                        }
                    }
                }
            })
        }
        val subtitle = when (type) {
            ReleaseType.MIX -> value.optString("shortSubtitle").ifBlank { value.optString("subTitle") }
            ReleaseType.VIDEO -> formatDuration(value.optLong("duration") * 1000)
            else -> value.optString("releaseDate").take(4)
        }
        return Release(
            id = (value.opt("uuid") ?: value.opt("id") ?: "").toString(),
            title = value.optString("title").ifBlank { value.optString("name", "Untitled") },
            artist = artistName,
            artwork = artwork,
            subtitle = subtitle,
            type = type,
            ownerId = value.optJSONObject("creator")?.let { (it.opt("id") ?: it.opt("userId") ?: "").toString() }.orEmpty(),
            tags = tags(value),
            description = value.optString("description").takeIf { it != "null" }.orEmpty(),
            artistId = firstArtist?.opt("id")?.toString()?.takeIf { it != "null" }.orEmpty(),
        )
    }

    fun items(value: JSONObject): List<JSONObject> {
        val array = value.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { it.optJSONObject("item") ?: it }
        }
    }

    fun image(id: String?, size: String = "640x640"): String? = id?.takeIf { it.isNotBlank() && it != "null" }?.let {
        if (it.startsWith("https://")) it
        else "https://resources.tidal.com/images/${it.replace('-', '/')}/$size.jpg"
    }

    fun encodeTrack(track: Track) = JSONObject().apply {
        put("id", track.id); put("title", track.title); put("artist", track.artist)
        put("album", track.album); put("albumId", track.albumId)
        put("artwork", track.artwork); put("durationMs", track.durationMs)
        put("explicit", track.explicit); put("localUri", track.localUri)
        put("quality", track.quality); put("addedAt", track.addedAt)
        put("artistId", track.artistId); put("trackNumber", track.trackNumber)
        track.replayGain?.let { put("replayGain", it) }
        track.peak?.let { put("peak", it) }
        if (track.tags.isNotEmpty()) put("tags", JSONArray(track.tags))
    }

    fun decodeTrack(value: JSONObject) = Track(
        id = value.getString("id"), title = value.getString("title"), artist = value.getString("artist"),
        album = value.optString("album"), albumId = value.optString("albumId"),
        artwork = value.optString("artwork").takeIf { it.isNotBlank() && it != "null" },
        durationMs = value.optLong("durationMs"), explicit = value.optBoolean("explicit"),
        localUri = value.optString("localUri").takeIf { it.isNotBlank() && it != "null" },
        quality = value.optString("quality").takeIf { it.isNotBlank() && it != "null" },
        addedAt = value.optLong("addedAt"),
        artistId = value.optString("artistId").takeIf { it != "null" }.orEmpty(),
        trackNumber = value.optInt("trackNumber"),
        replayGain = number(value, "replayGain"), peak = number(value, "peak"),
        tags = value.optJSONArray("tags")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty(),
    )

    fun encodeTracks(tracks: List<Track>) = JSONArray().apply { tracks.forEach { put(encodeTrack(it)) } }.toString()
    fun decodeTracks(value: String): List<Track> = JSONArray(value).let { array ->
        (0 until array.length()).map { decodeTrack(array.getJSONObject(it)) }
    }

    fun encodeRelease(release: Release) = JSONObject().apply {
        put("id", release.id); put("title", release.title); put("artist", release.artist); put("artwork", release.artwork)
        put("subtitle", release.subtitle); put("type", release.type.name); put("ownerId", release.ownerId)
        put("artistId", release.artistId); put("description", release.description)
        if (release.tags.isNotEmpty()) put("tags", JSONArray(release.tags))
    }

    fun decodeRelease(json: JSONObject) = Release(json.getString("id"), json.getString("title"), json.getString("artist"),
        json.optString("artwork").takeIf { it.isNotBlank() && it != "null" }, json.optString("subtitle"),
        ReleaseType.valueOf(json.getString("type")), json.optString("ownerId"),
        json.optJSONArray("tags")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty(),
        json.optString("description"), json.optString("artistId"))

    fun encodeReleases(releases: List<Release>) = JSONArray().apply { releases.forEach { put(encodeRelease(it)) } }.toString()

    fun decodeReleases(value: String): List<Release> = JSONArray(value).let { array ->
        (0 until array.length()).map { decodeRelease(array.getJSONObject(it)) }
    }
}
