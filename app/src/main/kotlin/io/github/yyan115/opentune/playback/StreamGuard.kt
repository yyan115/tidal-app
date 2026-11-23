package io.github.yyan115.opentune.playback

import kotlin.math.abs

/**
 * Checks that the buffers of a decoder hold as many bytes as the time that they cover. A decoder that was asked for samples of a larger size can
 * return them without saying so, and the player then reads each of them as two samples. The buffers hold twice as many bytes as the player believes,
 * and the sound comes out loud, distorted and fast. Nothing is played before the guard has seen enough, so the fault is never heard.
 *
 * [frameBytes] is the size of one frame as the player believes it to be, [sampleRate] the frames in a second.
 */
class StreamGuard(private val frameBytes: Int, private val sampleRate: Int, private val isFloat: Boolean) {
    sealed interface Verdict {
        object Pending : Verdict { override fun toString() = "Pending" }
        object Ok : Verdict { override fun toString() = "Ok" }
        data class Mismatch(val reason: String) : Verdict
    }

    private var lastBytes = -1
    private var lastUs = 0L
    private var good = 0
    private var bad = 0
    private var lastRatio = 1.0
    private var verdict: Verdict = Verdict.Pending

    /** A jump in time, such as after a seek, ends the comparison with the buffer before it. */
    fun discontinuity() { lastBytes = -1 }

    /** Adds a buffer of [bytes] that starts at [presentationUs]. [floatSamples] are the first samples of the buffer when it holds float samples. */
    fun add(bytes: Int, presentationUs: Long, floatSamples: FloatArray? = null): Verdict {
        if (verdict !is Verdict.Pending) return verdict
        if (isFloat && floatSamples != null && floatSamples.any { !it.isFinite() || abs(it) > MAX_SAMPLE }) {
            return Verdict.Mismatch("The decoder returned float samples that cannot be valid.").also { verdict = it }
        }
        if (lastBytes >= 0 && presentationUs > lastUs) {
            val coveredUs = presentationUs - lastUs
            if (coveredUs >= MIN_COVERED_US) {
                val expected = coveredUs * sampleRate.toDouble() * frameBytes / 1_000_000.0
                lastRatio = lastBytes / expected
                if (lastRatio in LOW..HIGH) good++ else bad++
            }
        }
        lastBytes = bytes; lastUs = presentationUs
        // Two steps that agree pass. Two that disagree, and more of them than agree, fail. One odd step, as after a seek, decides nothing.
        if (bad >= 2 && bad > good) verdict = Verdict.Mismatch("The buffers hold ${"%.1f".format(lastRatio)} times the bytes that the time covers.")
        else if (good >= 2 && good > bad) verdict = Verdict.Ok
        return verdict
    }

    /** The stream ends before the guard has decided. Only proof of a fault blocks a phone, a short stream without such proof passes. */
    fun end(): Verdict {
        if (verdict is Verdict.Pending) verdict = Verdict.Ok
        return verdict
    }

    companion object {
        private const val LOW = 0.88
        private const val HIGH = 1.12
        private const val MIN_COVERED_US = 2_000L
        /** Samples of music stay within -1 and 1, and a loud master may go a little over. */
        private const val MAX_SAMPLE = 64f
    }
}
