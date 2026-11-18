package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSnapshotTest {
    private val tracks = listOf(Track("1", "First", "Artist", "Album", "9"), Track("2", "Second", "Artist"), Track("3", "Third", "Artist", durationMs = 1_000))

    private fun snapshot(list: List<Track>) = QueueSnapshot(list.map { JsonCodec.encodeTrack(it).toString() }, List(list.size) { it == 1 }, 1, 12_000, true, 2)

    @Test fun theJsonOfTheQueueReadsBackAsTheSameTracks() {
        assertEquals(tracks, JsonCodec.decodeTracks(snapshot(tracks).tracksJson()))
    }

    @Test fun theJsonMatchesWhatTheEncoderOfTheTracksWrites() {
        assertEquals(JsonCodec.decodeTracks(JsonCodec.encodeTracks(tracks)), JsonCodec.decodeTracks(snapshot(tracks).tracksJson()))
    }

    @Test fun anEmptyQueueIsAnEmptyArray() {
        assertEquals("[]", snapshot(emptyList()).tracksJson())
        assertEquals(emptyList<Track>(), JsonCodec.decodeTracks(snapshot(emptyList()).tracksJson()))
    }

    @Test fun aLongQueueKeepsEveryTrack() {
        val many = (1..5_000).map { Track("$it", "Song $it", "Artist") }
        assertEquals(many, JsonCodec.decodeTracks(snapshot(many).tracksJson()))
    }

    @Test fun theMarksKeepTheTracksThatWereAddedByHand() {
        assertEquals(listOf(false, true, false), QueueOrder.decode(snapshot(tracks).manualMarks(), 3))
    }
}
