package io.github.yyan115.opentune.playback

import androidx.media3.common.C

/** The format of the audio output that the player opened last, for tests and reports. */
object OutputFormat {
    /** The encoding of the samples, one of the `C.ENCODING_PCM` values, or [C.ENCODING_INVALID] when no output was opened yet. */
    @Volatile var encoding = C.ENCODING_INVALID
        internal set
    @Volatile var sampleRate = 0
        internal set
    /** How many outputs the player opened so far. */
    @Volatile var opened = 0
        internal set

    internal fun record(encoding: Int, sampleRate: Int) {
        this.encoding = encoding; this.sampleRate = sampleRate; opened++
    }
}
