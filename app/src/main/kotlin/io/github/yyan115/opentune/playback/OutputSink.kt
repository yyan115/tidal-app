package io.github.yyan115.opentune.playback

import android.media.AudioDeviceInfo
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The audio output of the player. It holds two outputs and uses one of them for each stream.
 *
 * [integer] sends 16-bit samples, which every phone takes and which is all that a song of 16 bits or less needs. It is the output of 0.7.0.
 * [float] sends floating point samples, which keep the full depth of 24-bit audio. Only a song of more than 16 bits uses it, and only on a phone
 * that passed the check below. A bit-perfect connection needs samples in a format of the device, which floating point samples are not, so
 * [integer] is used while [useInteger] holds. The output is chosen when a stream is configured, so a change applies from the next stream.
 *
 * A decoder that is asked for float samples may return them without saying so, and the player then reads every sample as two. The first buffers
 * of such a stream are therefore held back and checked by a [StreamGuard] before anything reaches the speaker. When they do not fit, nothing is
 * played, the phone is marked in [policy], and the player starts the song again without asking for float samples.
 *
 * The output also lets the visualizer see the audio that arrives.
 */
@UnstableApi
class OutputSink(
    private val float: AudioSink,
    private val integer: AudioSink,
    private val useInteger: () -> Boolean,
    private val policy: OutputPolicyState = OutputPolicy.state,
) : AudioSink {
    private val tap = SpectrumTap()
    private var current: AudioSink = integer
    private var playing = false
    // A buffer that the output did not take in full arrives again, and the visualizer must see it once.
    private var bufferSeen = false
    // The renderer says before it configures a stream whether it asked the decoder for float samples.
    @Volatile private var floatExpected = false
    private var guard: StreamGuard? = null
    // Buffers that wait for the verdict of the guard, and then for the output to take them.
    private class Held(val data: ByteBuffer, val presentationUs: Long, val accessUnits: Int) { var seen = false }
    private val held = ArrayDeque<Held>()
    private var verdict: StreamGuard.Verdict = StreamGuard.Verdict.Ok
    private var dropping = false
    private val outputs = listOf(float, integer)

    fun expectFloat(value: Boolean) { floatExpected = value }

    private fun wantsFloat(format: Format?) = !useInteger() && policy.floatAllowed && (floatExpected || (format != null && highResolution(format.pcmEncoding)))

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        val next = if (wantsFloat(format)) float else integer
        if (next !== current) {
            current.flush()
            current = next
            if (playing) current.play()
        }
        held.clear(); dropping = false
        guard = if (floatExpected && current === float) StreamGuard(frameBytes(format), format.sampleRate, format.pcmEncoding == C.ENCODING_PCM_FLOAT) else null
        verdict = if (guard == null) StreamGuard.Verdict.Ok else StreamGuard.Verdict.Pending
        tap.configure(format)
        bufferSeen = false
        current.configure(audioSinkConfig)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (dropping) { buffer.position(buffer.limit()); return true }
        val checking = guard
        if (checking != null && verdict == StreamGuard.Verdict.Pending) {
            // The buffer waits here. It is a copy, because the decoder reuses its own.
            val copy = ByteBuffer.allocate(buffer.remaining()).also { it.put(buffer.duplicate()); it.flip() }
            buffer.position(buffer.limit())
            held += Held(copy, presentationTimeUs, encodedAccessUnitCount)
            decide(checking.add(copy.remaining(), presentationTimeUs, floatSamples(copy)))
            return true
        }
        if (!deliverHeld()) return false
        if (!bufferSeen) tap.accept(buffer)
        val handled = current.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        bufferSeen = !handled
        return handled
    }

    private fun decide(next: StreamGuard.Verdict) {
        verdict = next
        when (next) {
            is StreamGuard.Verdict.Mismatch -> { held.clear(); dropping = true; policy.block(next.reason) }
            StreamGuard.Verdict.Ok -> deliverHeld()
            StreamGuard.Verdict.Pending -> {}
        }
    }

    /** Gives the held buffers to the output in order. False when the output cannot take one of them yet. */
    private fun deliverHeld(): Boolean {
        while (held.isNotEmpty()) {
            val next = held.first()
            if (!next.seen) { tap.accept(next.data); next.seen = true }
            if (!current.handleBuffer(next.data, next.presentationUs, next.accessUnits)) return false
            held.removeFirst()
        }
        return true
    }

    private fun floatSamples(buffer: ByteBuffer): FloatArray? {
        if (!floatExpected) return null
        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(minOf(view.remaining(), SAMPLES_TO_CHECK)).also { view.get(it) }
    }

    override fun play() { playing = true; current.play() }
    override fun pause() { playing = false; current.pause() }
    override fun flush() {
        bufferSeen = false; held.clear(); dropping = false
        // A seek moves the time of the buffers. What the guard saw before it cannot be compared with what comes after.
        guard?.discontinuity()
        outputs.forEach { it.flush() }
    }
    override fun reset() { bufferSeen = false; held.clear(); dropping = false; guard = null; verdict = StreamGuard.Verdict.Ok; outputs.forEach { it.reset() } }
    override fun release() { outputs.forEach { it.release() } }

    // What the renderer asks before it configures a stream is answered by the output that the stream will use.
    // A phone that failed the check says that it takes no float samples, so that the decoder is not asked for them.
    override fun supportsFormat(format: Format) = answerFor(format).supportsFormat(format)
    override fun getFormatSupport(format: Format) = answerFor(format).getFormatSupport(format)
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = answerFor(format).getFormatOffloadSupport(format)
    private fun answerFor(format: Format) = if (!useInteger() && policy.floatAllowed) float else integer

    override fun getCurrentPositionUs(sourceEnded: Boolean) = current.getCurrentPositionUs(sourceEnded)
    override fun handleDiscontinuity() { guard?.discontinuity(); current.handleDiscontinuity() }
    override fun playToEndOfStream() {
        // A short stream ends before the guard decided. Without proof of a fault it plays.
        if (verdict == StreamGuard.Verdict.Pending) decide(guard?.end() ?: StreamGuard.Verdict.Ok)
        deliverHeld()
        if (held.isEmpty()) current.playToEndOfStream()
    }
    override fun isEnded() = held.isEmpty() && current.isEnded
    override fun hasPendingData() = held.isNotEmpty() || current.hasPendingData()
    override fun getPlaybackParameters(): PlaybackParameters = current.playbackParameters
    override fun getSkipSilenceEnabled() = current.skipSilenceEnabled
    override fun getAudioAttributes(): AudioAttributes? = current.audioAttributes
    override fun getAudioCapabilities(): AudioCapabilities? = current.audioCapabilities
    override fun getAudioTrackBufferSizeUs() = current.audioTrackBufferSizeUs

    // Every setting goes to both outputs, so that either one is ready when a stream uses it.
    override fun setListener(listener: AudioSink.Listener) { val reporting = OutputReport(listener); outputs.forEach { it.setListener(reporting) } }
    override fun setPlayerId(playerId: PlayerId?) = outputs.forEach { it.setPlayerId(playerId) }
    override fun setClock(clock: Clock) = outputs.forEach { it.setClock(clock) }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = outputs.forEach { it.setPlaybackParameters(playbackParameters) }
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = outputs.forEach { it.setSkipSilenceEnabled(skipSilenceEnabled) }
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = outputs.forEach { it.setAudioAttributes(audioAttributes) }
    override fun setAudioSessionId(audioSessionId: Int) = outputs.forEach { it.setAudioSessionId(audioSessionId) }
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = outputs.forEach { it.setAuxEffectInfo(auxEffectInfo) }
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = outputs.forEach { it.setPreferredDevice(audioDeviceInfo) }
    override fun setVirtualDeviceId(virtualDeviceId: Int) = outputs.forEach { it.setVirtualDeviceId(virtualDeviceId) }
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = outputs.forEach { it.setOutputStreamOffsetUs(outputStreamOffsetUs) }
    override fun enableTunnelingV21() = outputs.forEach { it.enableTunnelingV21() }
    override fun disableTunneling() = outputs.forEach { it.disableTunneling() }
    @RequiresApi(29)
    override fun setOffloadMode(offloadMode: Int) = outputs.forEach { it.setOffloadMode(offloadMode) }
    @RequiresApi(29)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = outputs.forEach { it.setOffloadDelayPadding(delayInFrames, paddingInFrames) }
    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) = outputs.forEach { it.setAudioOutputProvider(audioOutputProvider) }
    override fun setVolume(volume: Float) = outputs.forEach { it.setVolume(volume) }

    private companion object {
        const val SAMPLES_TO_CHECK = 512
        fun highResolution(encoding: Int) = encoding == C.ENCODING_PCM_24BIT || encoding == C.ENCODING_PCM_32BIT || encoding == C.ENCODING_PCM_FLOAT ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN || encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        fun bytesPerSample(encoding: Int) = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        fun frameBytes(format: Format) = format.channelCount.coerceAtLeast(1) * bytesPerSample(format.pcmEncoding)
    }
}

/** Passes every event of the output on to [next] and records the format of each output that opens. */
@UnstableApi
private class OutputReport(private val next: AudioSink.Listener) : AudioSink.Listener {
    override fun onPositionDiscontinuity() = next.onPositionDiscontinuity()
    override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) = next.onPositionAdvancing(playoutStartSystemTimeMs)
    override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = next.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = next.onSkipSilenceEnabledChanged(skipSilenceEnabled)
    override fun onOffloadBufferEmptying() = next.onOffloadBufferEmptying()
    override fun onOffloadBufferFull() = next.onOffloadBufferFull()
    override fun onAudioSinkError(audioSinkError: Exception) = next.onAudioSinkError(audioSinkError)
    override fun onAudioCapabilitiesChanged() = next.onAudioCapabilitiesChanged()
    override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
        OutputFormat.record(audioTrackConfig.encoding, audioTrackConfig.sampleRate)
        next.onAudioTrackInitialized(audioTrackConfig)
    }
    override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) = next.onAudioTrackReleased(audioTrackConfig)
    override fun onSilenceSkipped() = next.onSilenceSkipped()
    override fun onAudioSessionIdChanged(audioSessionId: Int) = next.onAudioSessionIdChanged(audioSessionId)
}
