package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedTracksTest {
    private fun track(id: String) = Track(id, "Song $id", "Artist")
    private fun ids(list: List<Track>) = list.map(Track::id)

    @Test fun theAccountOrderHoldsEvenForTracksSavedOnThisDevice() {
        val device = listOf(track("1"), track("2"), track("3"))
        val account = listOf(track("9"), track("3"), track("2"), track("1"))
        assertEquals(listOf("9", "3", "2", "1"), ids(SavedTracks.merge(device, account, emptyMap())))
    }

    @Test fun tracksTheAccountDoesNotListYetComeFirst() {
        val device = listOf(track("5"), track("local:1"), track("2"))
        val account = listOf(track("9"), track("2"))
        assertEquals(listOf("5", "local:1", "9", "2"), ids(SavedTracks.merge(device, account, mapOf("5" to true))))
    }

    @Test fun aWaitingRemovalHidesTheTrackEverywhere() {
        val device = listOf(track("1"))
        val account = listOf(track("2"), track("1"))
        assertEquals(listOf("2"), ids(SavedTracks.merge(device, account, mapOf("1" to false))))
    }

    @Test fun aTrackRemovedInAnotherAppIsFound() {
        val device = listOf(track("1"), track("2"), track("3"), track("4"), track("local:7"))
        val account = listOf(track("2"))
        assertEquals(listOf("1", "3"), ids(SavedTracks.removedElsewhere(device, account, mapOf("4" to true))))
    }
}
