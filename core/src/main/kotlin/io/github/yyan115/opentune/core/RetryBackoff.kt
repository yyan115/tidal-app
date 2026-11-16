package io.github.yyan115.opentune.core

/**
 * The waits between the tries to resume playback after a failure that can pass, such as a lost connection.
 * Each try waits longer than the one before, and after the last wait there is no further try.
 */
class RetryBackoff(private val waits: List<Long> = DEFAULT_WAITS) {
    private var attempt = 0

    /** The wait in milliseconds before the next try, or null when every try is used. */
    fun next(): Long? = waits.getOrNull(attempt)?.also { attempt++ }

    /** Playback resumed, the track changed or the connection returned, so the next failure starts with the shortest wait. */
    fun reset() { attempt = 0 }

    companion object {
        val DEFAULT_WAITS = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 30_000L, 30_000L, 30_000L)
    }
}
