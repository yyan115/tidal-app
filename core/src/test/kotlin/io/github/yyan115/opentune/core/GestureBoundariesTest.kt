package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gestures at the exact values where they change their answer. A rule that is wrong by one step at its limit would still pass a test far from it. */
class GestureBoundariesTest {
    @Test fun theSheetOpensAtTheFlingSpeedAndAtHalfWay() {
        assertTrue(PlayerGestures.opens(0f, PlayerGestures.SHEET_FLING))
        assertFalse(PlayerGestures.opens(1f, -PlayerGestures.SHEET_FLING))
        assertTrue("Halfway and slow opens", PlayerGestures.opens(0.5f, 0f))
        assertFalse("A hair below halfway and slow closes", PlayerGestures.opens(0.4999f, 0f))
        assertTrue("A flick just below the fling speed is decided by the place", PlayerGestures.opens(0.6f, PlayerGestures.SHEET_FLING - 0.01f))
        assertFalse(PlayerGestures.opens(0.4f, -(PlayerGestures.SHEET_FLING - 0.01f)))
    }

    @Test fun aSwipeChangesTheTrackAtTheDistanceAndAtTheFlingSpeed() {
        val width = 1000f
        // Exactly the distance is far enough, and a hair less is not.
        assertEquals(PlayerGestures.Swipe.Next, PlayerGestures.swipe(-PlayerGestures.SWIPE_DISTANCE * width, 0f, width, true, true))
        assertEquals(PlayerGestures.Swipe.Stay, PlayerGestures.swipe(-(PlayerGestures.SWIPE_DISTANCE * width - 1f), 0f, width, true, true))
        assertEquals(PlayerGestures.Swipe.Previous, PlayerGestures.swipe(PlayerGestures.SWIPE_DISTANCE * width, 0f, width, true, true))
        // A fast swipe needs at least the minimum share, and exactly the fling speed is fast.
        assertEquals(PlayerGestures.Swipe.Next, PlayerGestures.swipe(-PlayerGestures.SWIPE_MINIMUM * width, -PlayerGestures.SWIPE_FLING, width, true, true))
        assertEquals(PlayerGestures.Swipe.Stay, PlayerGestures.swipe(-(PlayerGestures.SWIPE_MINIMUM * width - 1f), -PlayerGestures.SWIPE_FLING * 2, width, true, true))
        assertEquals(PlayerGestures.Swipe.Stay, PlayerGestures.swipe(-PlayerGestures.SWIPE_MINIMUM * width, -(PlayerGestures.SWIPE_FLING - 0.01f), width, true, true))
        // A fling the other way than the finger moved does not count as fast.
        assertEquals(PlayerGestures.Swipe.Stay, PlayerGestures.swipe(-PlayerGestures.SWIPE_MINIMUM * width, PlayerGestures.SWIPE_FLING * 2, width, true, true))
    }

    @Test fun aSwipeToATrackThatDoesNotExistEndsOrStartsTheQueue() {
        assertEquals(PlayerGestures.Swipe.End, PlayerGestures.swipe(-400f, 0f, 1000f, hasNext = false, hasPrevious = true))
        assertEquals(PlayerGestures.Swipe.Start, PlayerGestures.swipe(400f, 0f, 1000f, hasNext = true, hasPrevious = false))
        assertEquals(PlayerGestures.Swipe.Stay, PlayerGestures.swipe(-400f, 0f, 0f, true, true))
    }

    @Test fun theQualityLabelNamesTheDepthAndTheRateOnlyWhenTheyAreKnown() {
        fun info(bits: Int, rate: Int) = StreamInfo(mimeType = "audio/flac", codec = "flac", quality = "LOSSLESS", bitDepth = bits, sampleRate = rate)
        assertEquals("FLAC", info(0, 0).qualityLabel)
        assertEquals("FLAC · 1-bit", info(1, 0).qualityLabel)
        assertEquals("FLAC · 0.001 kHz", info(0, 1).qualityLabel)
        assertEquals("FLAC · 24-bit · 96.0 kHz", info(24, 96_000).qualityLabel)
        assertEquals("AAC", StreamInfo(mimeType = "audio/mp4", codec = "aac", quality = "HIGH").qualityLabel)
    }

    @Test fun aSnapshotOfTheQueueKeepsEveryFieldItWasMadeWith() {
        val tracks = listOf("{\"id\":\"1\"}", "{\"id\":\"2\"}")
        val snapshot = QueueSnapshot(tracks, listOf(false, true), 1, 12_345L, true, 2)
        assertEquals(tracks, snapshot.tracks); assertEquals(listOf(false, true), snapshot.manual); assertEquals(1, snapshot.index)
        assertEquals(12_345L, snapshot.positionMs); assertTrue(snapshot.shuffle); assertEquals(2, snapshot.repeat)
    }

    @Test fun theValuesOfAStreamAreKept() {
        val info = StreamInfo("https://stream", "manifest", "audio/flac", "flac", "HI_RES", 24, 96_000, -6.5, 0.9, -7.5, 0.8)
        assertEquals("https://stream", info.uri); assertEquals("HI_RES", info.quality); assertEquals(24, info.bitDepth)
        assertEquals(-6.5, info.trackGain!!, 0.0); assertEquals(0.9, info.trackPeak!!, 0.0); assertEquals(-7.5, info.albumGain!!, 0.0); assertEquals(0.8, info.albumPeak!!, 0.0)
    }
}
