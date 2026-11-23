package io.github.yyan115.opentune.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * How many bits a sample of a source holds, read from what the player knows before it decodes anything. Only a source of more than 16 bits needs
 * more than a 16-bit output, so only such a source may ask the decoder for samples of a larger size.
 */
@UnstableApi
object SourceDepth {
    /** The bits of a sample, or null when the format does not say. [initializationData] is what the reader of the file hands to the decoder. */
    fun bits(mimeType: String?, pcmEncoding: Int, initializationData: List<ByteArray>): Int? = when (mimeType) {
        MimeTypes.AUDIO_RAW -> rawBits(pcmEncoding)
        MimeTypes.AUDIO_FLAC -> initializationData.firstNotNullOfOrNull(::flacBits)
        MimeTypes.AUDIO_ALAC -> initializationData.firstNotNullOfOrNull(::alacBits)
        else -> null
    }

    fun isHighResolution(mimeType: String?, pcmEncoding: Int, initializationData: List<ByteArray>) = (bits(mimeType, pcmEncoding, initializationData) ?: 0) > 16

    private fun rawBits(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    /** The STREAMINFO block of FLAC: 20 bits sample rate, 3 bits channels, 5 bits bits per sample, each of them one less than its value. It may follow the marker "fLaC" and a header of 4 bytes. */
    private fun flacBits(data: ByteArray): Int? {
        val start = when {
            data.size >= 42 && data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() && data[2] == 0x61.toByte() && data[3] == 0x43.toByte() -> 8
            data.size == 34 -> 0
            else -> return null
        }
        val high = data[start + 12].toInt() and 0x01
        val low = (data[start + 13].toInt() and 0xF0) ushr 4
        return ((high shl 4) or low) + 1
    }

    /** The cookie of ALAC: 4 bytes frame length, 1 byte compatible version, then the bit depth. */
    private fun alacBits(data: ByteArray): Int? = if (data.size >= 24) data[5].toInt() and 0xFF else null
}
