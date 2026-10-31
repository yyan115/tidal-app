package io.github.yyan115.opentune.core

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    val artwork: String? = null,
    val durationMs: Long = 0,
    val explicit: Boolean = false,
    val localUri: String? = null,
    val quality: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val isLocal get() = localUri != null
}

data class Release(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val subtitle: String = "",
    val type: ReleaseType = ReleaseType.ALBUM,
    val ownerId: String = "",
)

enum class ReleaseType { ALBUM, PLAYLIST, ARTIST }
data class SearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Release> = emptyList(),
    val artists: List<Release> = emptyList(),
    val playlists: List<Release> = emptyList(),
)

enum class AudioQuality(val label: String) {
    HI_RES_LOSSLESS("Max · up to 24-bit / 192 kHz"),
    LOSSLESS("Lossless · CD quality"),
    HIGH("High · AAC 320 kbps"),
    LOW("Low · AAC 96 kbps"),
}

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String = "",
    val country: String = "US",
) {
    fun needsRefresh(now: Long = System.currentTimeMillis()) = expiresAt <= now + 60_000
    override fun toString() = "Session(authenticated=${accessToken.isNotBlank()}, country=$country)"
}

data class PkceLogin(val authorizationUrl: String, val verifier: String, val state: String,
    val uniqueKey: String, val expiresAt: Long) {
    override fun toString() = "PkceLogin(expiresAt=$expiresAt)"
}

data class StreamInfo(
    val uri: String? = null,
    val dashManifest: String? = null,
    val mimeType: String,
    val codec: String,
    val quality: String,
    val bitDepth: Int = 0,
    val sampleRate: Int = 0,
) {
    val qualityLabel: String
        get() = buildString {
            append(if (codec.contains("flac", true)) "FLAC" else codec.uppercase())
            if (bitDepth > 0) append(" · $bitDepth-bit")
            if (sampleRate > 0) append(" · ${sampleRate / 1000.0} kHz")
        }
    override fun toString() = "StreamInfo(codec=$codec, quality=$quality, bitDepth=$bitDepth, sampleRate=$sampleRate)"
}

data class LocalPlaylist(val id: String, val title: String, val trackIds: List<String> = emptyList())

class ServiceException(val status: Int, message: String) : Exception(message)
class ProtectedStreamException : Exception("This stream requires content protection that this client does not support.")

fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
