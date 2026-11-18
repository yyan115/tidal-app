package io.github.yyan115.opentune.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.Spectrum
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The last samples that the player sent to the audio output, as one channel, for the visualizer.
 * The player writes them, and the screen reads them about thirty times a second.
 */
object AudioSpectrum {
    private val ring = FloatArray(Spectrum.SIZE * 4)
    @Volatile private var written = 0L
    /** The rate of the samples in the ring. */
    @Volatile var sampleRate = 44_100
        private set
    /** The tap does nothing while this is off, so that the visualizer costs nothing when nobody looks at it. */
    @Volatile var enabled = false

    internal fun write(sample: Float, rate: Int) {
        sampleRate = rate
        val index = written
        ring[(index % ring.size).toInt()] = sample
        written = index + 1
    }

    /** Copies the latest [Spectrum.SIZE] samples into [into]. Returns false when fewer samples have arrived. */
    fun latest(into: FloatArray): Boolean {
        val end = written
        if (end < Spectrum.SIZE) return false
        val start = end - Spectrum.SIZE
        for (i in 0 until Spectrum.SIZE) into[i] = ring[((start + i) % ring.size).toInt()]
        return true
    }

    fun clear() { written = 0 }

    /** The format that the tap was last configured for, for reports of a visualizer that stays empty. */
    @Volatile var configuration = "never configured"
        internal set

    /** How many audio sinks the player built with the tap, for the same reports. */
    @Volatile var sinks = 0
        internal set

    override fun toString() = "enabled=$enabled written=$written sinks=$sinks configuration=$configuration"
}

/**
 * Lets [AudioSpectrum] see the audio on its way to the output. The samples of all channels are averaged,
 * and a rate above 48 kHz is brought down by whole steps, so that the analysis covers the same span of time at every rate.
 */
@UnstableApi
class SpectrumTap {
    private var encoding = C.ENCODING_INVALID
    private var channels = 2
    private var step = 1
    private var rate = 44_100
    private var sum = 0f
    private var count = 0

    /** Sets the format of the audio that follows. A format that the tap cannot read, such as compressed audio sent on to a receiver, turns it off. */
    fun configure(format: Format) {
        val readable = format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding in SUPPORTED && format.sampleRate > 0
        encoding = if (readable) format.pcmEncoding else C.ENCODING_INVALID
        if (!readable) return
        AudioSpectrum.configuration = "encoding ${format.pcmEncoding}, ${format.channelCount} channels, ${format.sampleRate} Hz"
        channels = format.channelCount.coerceAtLeast(1)
        step = (format.sampleRate / 48_000).coerceAtLeast(1); rate = format.sampleRate / step
        sum = 0f; count = 0
    }

    /** Reads the samples of [buffer] without changing it. */
    fun accept(buffer: ByteBuffer) {
        if (encoding == C.ENCODING_INVALID || !AudioSpectrum.enabled || !buffer.hasRemaining()) return
        tap(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
    }

    private fun tap(buffer: ByteBuffer) {
        val bytes = when (encoding) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; else -> 4 }
        val frame = bytes * channels
        var frames = buffer.remaining() / frame
        var position = buffer.position()
        while (frames-- > 0) {
            var mixed = 0f
            for (channel in 0 until channels) {
                mixed += when (encoding) {
                    C.ENCODING_PCM_16BIT -> buffer.getShort(position).toFloat() / 32768f
                    C.ENCODING_PCM_24BIT -> (((buffer.get(position + 2).toInt() shl 16) or ((buffer.get(position + 1).toInt() and 0xFF) shl 8) or (buffer.get(position).toInt() and 0xFF)).toFloat()) / 8388608f
                    C.ENCODING_PCM_32BIT -> buffer.getInt(position).toFloat() / 2147483648f
                    else -> buffer.getFloat(position)
                }
                position += bytes
            }
            sum += mixed / channels
            if (++count == step) { AudioSpectrum.write(sum / step, rate); sum = 0f; count = 0 }
        }
    }

    private companion object {
        val SUPPORTED = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
    }
}
