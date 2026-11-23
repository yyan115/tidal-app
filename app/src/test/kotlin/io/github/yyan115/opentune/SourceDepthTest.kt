package io.github.yyan115.opentune

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import io.github.yyan115.opentune.playback.SourceDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which songs hold more than 16 bits, read from what the player knows about a source before it decodes it. */
class SourceDepthTest {
    /** The STREAMINFO block of a FLAC file, 34 bytes, with the sample rate, the channels and the bits of a sample in the places that FLAC defines. */
    private fun streamInfo(bits: Int, rate: Int = 96_000, channels: Int = 2): ByteArray {
        val info = ByteArray(34)
        // 20 bits sample rate, 3 bits channels - 1, 5 bits bits per sample - 1, then 36 bits for the number of samples.
        val packed = (rate.toLong() shl 44) or ((channels - 1).toLong() shl 41) or ((bits - 1).toLong() shl 36) or 1_000_000L
        for (i in 0 until 8) info[10 + i] = ((packed shr (56 - 8 * i)) and 0xFF).toByte()
        return info
    }

    /** The marker, the header of the block and the block, as the FLAC reader and the MP4 reader of Media3 hand them over. */
    private fun withMarker(info: ByteArray) = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22) + info

    @Test fun aFlacFileOf24BitsIsHighResolution() {
        val data = listOf(withMarker(streamInfo(24)))
        assertEquals(24, SourceDepth.bits(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, data))
        assertTrue(SourceDepth.isHighResolution(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, data))
    }

    @Test fun aFlacFileOf16BitsIsNot() {
        val data = listOf(withMarker(streamInfo(16, rate = 44_100)))
        assertEquals(16, SourceDepth.bits(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, data))
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, data))
    }

    @Test fun theBlockWithoutTheMarkerIsReadToo() {
        assertEquals(24, SourceDepth.bits(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, listOf(streamInfo(24))))
    }

    @Test fun everyDepthThatFlacAllowsIsRead() {
        for (bits in listOf(8, 12, 16, 20, 24, 32)) assertEquals(bits, SourceDepth.bits(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, listOf(withMarker(streamInfo(bits)))))
    }

    @Test fun rawSamplesAreReadFromTheirEncoding() {
        assertEquals(16, SourceDepth.bits(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_16BIT, emptyList()))
        assertEquals(24, SourceDepth.bits(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_24BIT, emptyList()))
        assertEquals(32, SourceDepth.bits(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_32BIT, emptyList()))
        assertTrue(SourceDepth.isHighResolution(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_FLOAT, emptyList()))
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_16BIT, emptyList()))
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_8BIT, emptyList()))
    }

    @Test fun anAlacFileIsReadFromItsCookie() {
        val cookie = ByteArray(24); cookie[5] = 24
        assertEquals(24, SourceDepth.bits(MimeTypes.AUDIO_ALAC, C.ENCODING_INVALID, listOf(cookie)))
        cookie[5] = 16
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_ALAC, C.ENCODING_INVALID, listOf(cookie)))
    }

    @Test fun lossyAndUnknownFormatsAreNeverHighResolution() {
        for (mime in listOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS, MimeTypes.AUDIO_AC3, null)) {
            assertNull(mime, SourceDepth.bits(mime, C.ENCODING_INVALID, emptyList()))
            assertFalse(mime, SourceDepth.isHighResolution(mime, C.ENCODING_INVALID, emptyList()))
        }
    }

    @Test fun brokenStreamInformationIsNotHighResolution() {
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, emptyList()))
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_FLAC, C.ENCODING_INVALID, listOf(ByteArray(5))))
        assertFalse(SourceDepth.isHighResolution(MimeTypes.AUDIO_ALAC, C.ENCODING_INVALID, listOf(ByteArray(3))))
    }
}
