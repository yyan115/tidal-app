package io.github.yyan115.opentune.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
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

/**
 * The audio output of the player. It holds two outputs and uses one of them for each stream.
 *
 * [float] sends the samples as floating point numbers, which keep the full depth of 24-bit audio.
 * [integer] sends 16-bit samples. A bit-perfect connection needs samples in a format of the device, which floating point samples are not,
 * so [integer] is used while [useInteger] holds. The output is chosen when a stream is configured, so a change applies from the next stream.
 *
 * The output also lets the visualizer see the audio that arrives.
 */
@UnstableApi
class OutputSink(private val float: AudioSink, private val integer: AudioSink, private val useInteger: () -> Boolean) : AudioSink {
    private val tap = SpectrumTap()
    private var current = chosen()
    private var playing = false
    // A buffer that the output did not take in full arrives again, and the visualizer must see it once.
    private var bufferSeen = false

    private fun chosen() = if (useInteger()) integer else float
    private inline fun both(action: AudioSink.() -> Unit) { float.action(); integer.action() }

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val next = chosen()
        if (next !== current) {
            current.flush()
            current = next
            if (playing) current.play()
        }
        tap.configure(audioSinkConfig.format)
        bufferSeen = false
        current.configure(audioSinkConfig)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!bufferSeen) tap.accept(buffer)
        val handled = current.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        bufferSeen = !handled
        return handled
    }

    override fun play() { playing = true; current.play() }
    override fun pause() { playing = false; current.pause() }
    override fun flush() { bufferSeen = false; both { flush() } }
    override fun reset() { bufferSeen = false; both { reset() } }
    override fun release() { both { release() } }

    // What the renderer asks before it configures a stream is answered by the output that the stream will use.
    override fun supportsFormat(format: Format) = chosen().supportsFormat(format)
    override fun getFormatSupport(format: Format) = chosen().getFormatSupport(format)
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = chosen().getFormatOffloadSupport(format)

    override fun getCurrentPositionUs(sourceEnded: Boolean) = current.getCurrentPositionUs(sourceEnded)
    override fun handleDiscontinuity() = current.handleDiscontinuity()
    override fun playToEndOfStream() = current.playToEndOfStream()
    override fun isEnded() = current.isEnded
    override fun hasPendingData() = current.hasPendingData()
    override fun getPlaybackParameters(): PlaybackParameters = current.playbackParameters
    override fun getSkipSilenceEnabled() = current.skipSilenceEnabled
    override fun getAudioAttributes(): AudioAttributes? = current.audioAttributes
    override fun getAudioCapabilities(): AudioCapabilities? = current.audioCapabilities
    override fun getAudioTrackBufferSizeUs() = current.audioTrackBufferSizeUs

    // Every setting goes to both outputs, so that either one is ready when a stream uses it.
    override fun setListener(listener: AudioSink.Listener) = both { setListener(listener) }
    override fun setPlayerId(playerId: PlayerId?) = both { setPlayerId(playerId) }
    override fun setClock(clock: Clock) = both { setClock(clock) }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = both { setPlaybackParameters(playbackParameters) }
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = both { setSkipSilenceEnabled(skipSilenceEnabled) }
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = both { setAudioAttributes(audioAttributes) }
    override fun setAudioSessionId(audioSessionId: Int) = both { setAudioSessionId(audioSessionId) }
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = both { setAuxEffectInfo(auxEffectInfo) }
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = both { setPreferredDevice(audioDeviceInfo) }
    override fun setVirtualDeviceId(virtualDeviceId: Int) = both { setVirtualDeviceId(virtualDeviceId) }
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = both { setOutputStreamOffsetUs(outputStreamOffsetUs) }
    override fun enableTunnelingV21() = both { enableTunnelingV21() }
    override fun disableTunneling() = both { disableTunneling() }
    override fun setOffloadMode(offloadMode: Int) = both { setOffloadMode(offloadMode) }
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = both { setOffloadDelayPadding(delayInFrames, paddingInFrames) }
    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) = both { setAudioOutputProvider(audioOutputProvider) }
    override fun setVolume(volume: Float) = both { setVolume(volume) }
}
