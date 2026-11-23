package io.github.yyan115.opentune.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * The audio renderer of the player. Media3 asks the decoder of the phone for float samples whenever the output takes them, for every song, so a
 * 16-bit song and a lossy song are decoded to float as well. Only a song of more than 16 bits has any use for float samples, and a decoder of
 * some phones returns them without saying so. This renderer asks for float samples only for such a song, and only on a phone that has not failed
 * the check of the output. Every other song is decoded the way that 0.7.0 decoded it.
 */
@UnstableApi
class OpenTuneAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    private val output: OutputSink,
    private val policy: () -> OutputPolicyState = { OutputPolicy.state },
) : MediaCodecAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, output) {
    private var requestedFloat = false

    override fun getMediaFormat(format: Format, codecMimeType: String, codecMaxInputSize: Int, codecOperatingRate: Float): MediaFormat {
        val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        val needed = SourceDepth.isHighResolution(format.sampleMimeType, format.pcmEncoding, format.initializationData)
        if (!(needed && policy().floatAllowed)) mediaFormat.removeKey(MediaFormat.KEY_PCM_ENCODING)
        requestedFloat = mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) && mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
        return mediaFormat
    }

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        output.expectFloat(requestedFloat)
        super.onOutputFormatChanged(format, mediaFormat)
    }
}
