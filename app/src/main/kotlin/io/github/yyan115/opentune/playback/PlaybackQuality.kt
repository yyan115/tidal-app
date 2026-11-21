package io.github.yyan115.opentune.playback

/** The format of the audio that plays, as text such as FLAC · 16-bit · 44.1 kHz. */
object PlaybackQuality {
    /**
     * The format of the stream while it is known, then the format that the track itself carries, then the format that the library saved for the track.
     * The stream is forgotten when a download of the track ends, and the queue still holds the track as it was before.
     */
    fun of(stream: String?, track: String?, stored: String?): String = stream?.takeIf(String::isNotBlank) ?: track?.takeIf(String::isNotBlank) ?: stored.orEmpty()
}
