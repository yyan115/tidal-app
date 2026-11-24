package io.github.yyan115.opentune

import io.github.yyan115.opentune.ui.SourceReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The audio details read the label of a stream into a codec, a bit depth, a rate and a bitrate. A song of lower quality shows its real format. */
class SourceReadoutTest {
    @Test fun aLosslessLabelIsReadInFull() {
        val readout = SourceReadout.from("FLAC · 24-bit · 96.0 kHz")
        assertEquals("FLAC", readout.codec); assertEquals("24", readout.bits); assertEquals("96.0", readout.sampleRate); assertNull(readout.bitrate)
    }

    @Test fun aLabelWithABitrateIsReadToo() {
        assertEquals("2304", SourceReadout.from("FLAC · 24-bit · 96.0 kHz · 2304 kbps").bitrate)
    }

    @Test fun anAacStreamOfALossyOnlySongShowsAac() {
        for (label in listOf("MP4A.40.2", "mp4a.40.5", "MP4A.40.2 · 44.1 kHz")) {
            val readout = SourceReadout.from(label)
            assertEquals(label, "AAC", readout.codec); assertNull(label, readout.bits)
        }
    }

    @Test fun otherCodecsAreShownInCapitals() {
        assertEquals("ALAC", SourceReadout.from("alac · 24-bit · 48.0 kHz").codec)
        assertEquals("MP3", SourceReadout.from("mp3").codec)
    }

    @Test fun anEmptyLabelReadsAsNothing() {
        val readout = SourceReadout.from("")
        assertEquals("", readout.codec); assertNull(readout.bits); assertNull(readout.sampleRate); assertNull(readout.bitrate)
    }
}
