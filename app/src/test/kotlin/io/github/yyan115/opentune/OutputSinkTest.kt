package io.github.yyan115.opentune

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import io.github.yyan115.opentune.playback.OutputPolicyState
import io.github.yyan115.opentune.playback.OutputSink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

/** The output of the player holds two outputs and picks one for each stream. Checked with outputs that only write down what they get. */
@UnstableApi
class OutputSinkTest {
    private class Recorder : InvocationHandler {
        val calls = mutableListOf<String>()
        val buffers = mutableListOf<ByteArray>()
        var takeBuffers = true
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            calls += method.name
            if (method.name == "handleBuffer") {
                val buffer = args!![0] as ByteBuffer
                // The real output refuses a buffer that is not little-endian, with an IllegalArgumentException.
                require(buffer.order() == java.nio.ByteOrder.LITTLE_ENDIAN) { "The buffer is ${buffer.order()}" }
                if (!takeBuffers) return false
                buffers += ByteArray(buffer.remaining()).also { buffer.get(it) }
                return true
            }
            return when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        }
    }

    private class Fixture(useInteger: Boolean = false, val policy: OutputPolicyState = OutputPolicyState()) {
        val floatRecorder = Recorder(); val integerRecorder = Recorder()
        val floatSink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java), floatRecorder) as AudioSink
        val integerSink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java), integerRecorder) as AudioSink
        val sink = OutputSink(floatSink, integerSink, { useInteger }, policy)
        val blocked = mutableListOf<String>()
        init { policy.onBlocked = { blocked += it } }
    }

    private val rate = 96_000
    private val frames = 4_096
    private val stepUs = frames * 1_000_000L / rate

    private fun format(encoding: Int, channels: Int = 2) = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(encoding).setChannelCount(channels).setSampleRate(rate).build()
    private fun configure(f: Fixture, encoding: Int, expectFloat: Boolean) {
        f.sink.expectFloat(expectFloat)
        f.sink.configure(AudioSink.AudioSinkConfig.Builder(format(encoding)).build())
    }
    /** Valid float samples between -0.5 and 0.5, so that a buffer is what a decoder of float samples returns. */
    private fun buffer(bytes: Int, seed: Int): ByteBuffer {
        val data = ByteBuffer.allocate(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bytes / 4) data.putFloat((((i * 31 + seed) % 200) - 100) / 200f)
        data.rewind()
        return ByteBuffer.wrap(data.array()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    }
    private fun pushed(f: Fixture, index: Int, bytes: Int): Boolean = f.sink.handleBuffer(buffer(bytes, index), index * stepUs, 1)

    @Test fun aSixteenBitStreamUsesTheIntegerOutputAndNothingIsHeld() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_16BIT, expectFloat = false)
        assertTrue(pushed(f, 0, frames * 4))
        assertEquals(1, f.integerRecorder.buffers.size)
        assertEquals(0, f.floatRecorder.buffers.size)
        assertFalse(f.floatRecorder.calls.contains("configure"))
        assertFalse(f.sink.hasPendingData())
    }

    @Test fun anHonestHighResolutionStreamIsReleasedInOrderAndUnchanged() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        val sent = (0 until 5).map { buffer(frames * 8, it).array().copyOf() }
        for (i in 0 until 5) assertTrue(f.sink.handleBuffer(ByteBuffer.wrap(sent[i].copyOf()).order(java.nio.ByteOrder.LITTLE_ENDIAN), i * stepUs, 1))
        assertEquals(5, f.floatRecorder.buffers.size)
        for (i in 0 until 5) assertArrayEquals("buffer $i", sent[i], f.floatRecorder.buffers[i])
        assertEquals(0, f.integerRecorder.buffers.size)
        assertTrue(f.blocked.isEmpty())
    }

    @Test fun nothingReachesTheOutputBeforeTheGuardIsSatisfied() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        pushed(f, 0, frames * 8); pushed(f, 1, frames * 8)
        assertEquals(0, f.floatRecorder.buffers.size)
        assertTrue(f.sink.hasPendingData())
        pushed(f, 2, frames * 8)
        assertEquals(3, f.floatRecorder.buffers.size)
        assertFalse(f.sink.hasPendingData())
    }

    @Test fun aLyingDecoderIsStoppedBeforeAnythingIsPlayed() {
        val f = Fixture()
        // The decoder returns float samples, 8 bytes a frame, and says that they have 16 bits, 4 bytes a frame.
        configure(f, C.ENCODING_PCM_16BIT, expectFloat = true)
        for (i in 0 until 3) assertTrue(pushed(f, i, frames * 8))
        assertEquals(0, f.floatRecorder.buffers.size)
        assertEquals(0, f.integerRecorder.buffers.size)
        assertEquals(1, f.blocked.size)
        assertFalse(f.policy.floatAllowed)
        // The player goes on feeding until it restarts, and what it feeds is dropped.
        assertTrue(pushed(f, 3, frames * 8))
        assertEquals(0, f.floatRecorder.buffers.size)
        assertEquals(0, f.integerRecorder.buffers.size)
    }

    @Test fun aDecoderThatReturnsHalfAsManyBytesAsItShouldIsStopped() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        for (i in 0 until 3) pushed(f, i, frames * 4)
        assertEquals(0, f.floatRecorder.buffers.size)
        assertFalse(f.policy.floatAllowed)
    }

    @Test fun invalidFloatSamplesAreStoppedAtOnce() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        val bytes = ByteBuffer.allocate(frames * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames * 2) bytes.putFloat(Float.NaN)
        bytes.flip()
        f.sink.handleBuffer(bytes, 0, 1)
        assertFalse(f.policy.floatAllowed)
        assertEquals(0, f.floatRecorder.buffers.size)
    }

    @Test fun aPhoneThatFailedTheCheckUsesTheIntegerOutputForEverything() {
        val policy = OutputPolicyState().also { it.block("size") }
        val f = Fixture(policy = policy)
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        assertTrue(pushed(f, 0, frames * 8))
        assertEquals(1, f.integerRecorder.buffers.size)
        assertEquals(0, f.floatRecorder.buffers.size)
    }

    @Test fun bitPerfectKeepsTheIntegerOutputAndTheGuardOut() {
        val f = Fixture(useInteger = true)
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        assertTrue(pushed(f, 0, frames * 8))
        assertEquals(1, f.integerRecorder.buffers.size)
        assertEquals(0, f.floatRecorder.buffers.size)
        assertFalse(f.sink.hasPendingData())
    }

    @Test fun aShortStreamThatEndsBeforeTheGuardDecidedIsPlayedInFull() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        pushed(f, 0, frames * 8); pushed(f, 1, frames * 8)
        f.sink.playToEndOfStream()
        assertEquals(2, f.floatRecorder.buffers.size)
        assertTrue(f.policy.floatAllowed)
    }

    @Test fun aSeekDropsWhatIsHeld() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        pushed(f, 0, frames * 8)
        f.sink.flush()
        assertFalse(f.sink.hasPendingData())
        assertEquals(0, f.floatRecorder.buffers.size)
    }

    @Test fun aBufferThatTheOutputDoesNotTakeIsOfferedAgainAndNothingIsLostOrRepeated() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        pushed(f, 0, frames * 8); pushed(f, 1, frames * 8)
        f.floatRecorder.takeBuffers = false
        pushed(f, 2, frames * 8)
        // The guard is satisfied, the output takes nothing yet, so the player must be told to wait.
        assertFalse(pushed(f, 3, frames * 8))
        assertTrue(f.sink.hasPendingData())
        f.floatRecorder.takeBuffers = true
        assertTrue(pushed(f, 3, frames * 8))
        assertEquals(4, f.floatRecorder.buffers.size)
        for (i in 0 until 4) assertArrayEquals("buffer $i", buffer(frames * 8, i).array(), f.floatRecorder.buffers[i])
    }

    @Test fun theStreamIsOkAfterAnotherConfigurationStartsAgain() {
        val f = Fixture()
        configure(f, C.ENCODING_PCM_FLOAT, expectFloat = true)
        for (i in 0 until 3) pushed(f, i, frames * 8)
        configure(f, C.ENCODING_PCM_16BIT, expectFloat = false)
        assertTrue(pushed(f, 0, frames * 4))
        assertEquals(1, f.integerRecorder.buffers.size)
    }
}
