package io.github.yyan115.opentune

import io.github.yyan115.opentune.playback.PlaybackQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQualityTest {
    @Test fun theFormatOfTheStreamComesFirst() {
        assertEquals("FLAC · 24-bit · 96 kHz", PlaybackQuality.of("FLAC · 24-bit · 96 kHz", "AAC", "FLAC · 16-bit"))
    }

    @Test fun theFormatOfTheTrackComesNext() {
        assertEquals("AAC", PlaybackQuality.of(null, "AAC", "FLAC · 16-bit"))
        assertEquals("AAC", PlaybackQuality.of("", "AAC", "FLAC · 16-bit"))
    }

    @Test fun aDownloadThatFinishedWhileItPlaysKeepsItsFormatFromTheLibrary() {
        // The stream is forgotten when the download ends, and the queue still holds the track as it was before the download.
        assertEquals("FLAC · 16-bit · 44.1 kHz", PlaybackQuality.of(null, "", "FLAC · 16-bit · 44.1 kHz"))
    }

    @Test fun withoutAnyFormatTheResultIsEmpty() {
        assertEquals("", PlaybackQuality.of(null, "", null))
        assertEquals("", PlaybackQuality.of("", "", ""))
    }
}
