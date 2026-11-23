package io.github.yyan115.opentune

import io.github.yyan115.opentune.playback.StreamGuard
import io.github.yyan115.opentune.playback.StreamGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard checks that the buffers of a decoder hold as many bytes as the time that they cover. A decoder that was asked for float samples and
 * returns them without saying so makes the player read every sample as two, and the buffers hold twice as many bytes as the player believes.
 */
class StreamGuardTest {
    private val rate = 96_000
    private val frameBytes = 8 // two channels of 4 bytes
    private val frames = 4_096
    private val bufferUs = frames * 1_000_000L / rate
    private fun honest(guard: StreamGuard, count: Int, from: Int = 0, bytes: Int = frames * frameBytes): Verdict {
        var verdict: Verdict = Verdict.Pending
        for (i in from until from + count) verdict = guard.add(bytes, i * bufferUs)
        return verdict
    }

    @Test fun honestBuffersPassAfterThree() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertEquals(Verdict.Pending, guard.add(frames * frameBytes, 0))
        assertEquals(Verdict.Pending, guard.add(frames * frameBytes, bufferUs))
        assertEquals(Verdict.Ok, guard.add(frames * frameBytes, 2 * bufferUs))
    }

    @Test fun floatSamplesThatThePlayerBelievesToBe16BitAreCaught() {
        // The buffers hold 4-byte samples, the player counts 2-byte samples.
        val guard = StreamGuard(frameBytes / 2, rate, isFloat = false)
        guard.add(frames * frameBytes, 0)
        guard.add(frames * frameBytes, bufferUs)
        val verdict = guard.add(frames * frameBytes, 2 * bufferUs)
        assertTrue("$verdict", verdict is Verdict.Mismatch)
    }

    @Test fun halfAsManyBytesAsTheTimeCoversIsCaught() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        val verdict = honest(guard, 3, bytes = frames * frameBytes / 2)
        assertTrue("$verdict", verdict is Verdict.Mismatch)
    }

    @Test fun oneAndAHalfTimesAsManyBytesIsCaught() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertTrue(honest(guard, 3, bytes = frames * frameBytes * 3 / 2) is Verdict.Mismatch)
    }

    @Test fun aSmallDifferenceInSizeIsNotAnAlarm() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertEquals(Verdict.Ok, honest(guard, 3, bytes = (frames * frameBytes * 1.05).toInt()))
    }

    @Test fun oneOddBufferAtTheStartDoesNotRaiseTheAlarm() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        // The first buffer is short, as after a seek, and the next ones are right.
        guard.add(frames * frameBytes / 3, 0)
        guard.add(frames * frameBytes, bufferUs)
        guard.add(frames * frameBytes, 2 * bufferUs)
        assertEquals(Verdict.Ok, guard.add(frames * frameBytes, 3 * bufferUs))
    }

    @Test fun invalidFloatSamplesAreCaughtAtOnce() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertTrue(guard.add(frames * frameBytes, 0, floatArrayOf(0.1f, Float.NaN)) is Verdict.Mismatch)
        assertTrue(StreamGuard(frameBytes, rate, isFloat = true).add(frames * frameBytes, 0, floatArrayOf(0.1f, 3.0e9f)) is Verdict.Mismatch)
        assertTrue(StreamGuard(frameBytes, rate, isFloat = true).add(frames * frameBytes, 0, floatArrayOf(Float.POSITIVE_INFINITY)) is Verdict.Mismatch)
    }

    @Test fun loudButValidFloatSamplesAreFine() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertEquals(Verdict.Pending, guard.add(frames * frameBytes, 0, floatArrayOf(1.0f, -1.0f, 1.5f, 0f)))
    }

    @Test fun aStreamThatEndsEarlyWithoutProofOfAFaultIsAccepted() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        guard.add(frames * frameBytes, 0)
        guard.add(frames * frameBytes, bufferUs)
        assertEquals(Verdict.Ok, guard.end())
        assertEquals(Verdict.Ok, StreamGuard(frameBytes, rate, isFloat = true).end())
    }

    @Test fun aSingleBadStepAtTheEndIsNotEnoughToBlockAPhone() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        guard.add(frames * frameBytes * 2, 0)
        guard.add(frames * frameBytes, bufferUs)
        assertEquals(Verdict.Ok, guard.end())
    }

    @Test fun aJumpInTimeIsNotTreatedAsAFault() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        guard.add(frames * frameBytes, 0)
        guard.discontinuity()
        guard.add(frames * frameBytes, 5_000_000)
        guard.add(frames * frameBytes, 5_000_000 + bufferUs)
        assertEquals(Verdict.Ok, guard.add(frames * frameBytes, 5_000_000 + 2 * bufferUs))
    }

    @Test fun theVerdictStaysOnceItIsMade() {
        val guard = StreamGuard(frameBytes, rate, isFloat = true)
        assertTrue(honest(guard, 3, bytes = frames * frameBytes / 2) is Verdict.Mismatch)
        assertTrue(honest(guard, 3, from = 3) is Verdict.Mismatch)
        val good = StreamGuard(frameBytes, rate, isFloat = true)
        assertEquals(Verdict.Ok, honest(good, 3))
        assertEquals(Verdict.Ok, honest(good, 3, from = 3, bytes = 12))
    }

    @Test fun everyRateAndChannelCountOfRealFilesPasses() {
        for (rate in listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000)) for (channels in listOf(1, 2, 6)) for (bytes in listOf(2, 3, 4)) {
            val frame = channels * bytes
            val block = 4_096
            val step = block * 1_000_000L / rate
            val guard = StreamGuard(frame, rate, isFloat = bytes == 4)
            var verdict: Verdict = Verdict.Pending
            for (i in 0 until 3) verdict = guard.add(block * frame, i * step)
            assertEquals("$rate Hz, $channels channels, $bytes bytes", Verdict.Ok, verdict)
        }
    }
}
