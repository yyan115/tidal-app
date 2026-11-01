package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoDownloadTest {
    private fun track(id: String, localUri: String? = null) = Track(id, "T$id", "A", localUri = localUri)

    @Test fun picksStreamedTracksThatAreNotOnTheDevice() {
        val saved = listOf(track("1"), track("2", localUri = "file:///x"), track("local:3"), track("4"), track("1"), track("5"))
        assertEquals(listOf("1", "5"), AutoDownload.pick(saved, onDevice = setOf("4"), limit = 10).map { it.id })
    }

    @Test fun respectsTheLimit() {
        assertEquals(listOf("1", "2"), AutoDownload.pick((1..9).map { track("$it") }, emptySet(), 2).map { it.id })
    }

    @Test fun nothingToDownloadGivesAnEmptyList() {
        assertEquals(emptyList<Track>(), AutoDownload.pick(emptyList(), emptySet(), 5))
    }
}
