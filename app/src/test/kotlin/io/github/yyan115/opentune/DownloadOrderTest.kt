package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.DownloadOrder
import io.github.yyan115.opentune.data.DownloadOrder.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOrderTest {
    private fun queue(vararg active: Boolean) = active.mapIndexed { index, on -> Entry("work-$index", 100L + index, on) }

    @Test fun theOrderNumbersOnlyGoUpEvenWhenTheClockStandsStill() {
        val numbers = (1..1000).map { DownloadOrder.next() }
        assertEquals(numbers.sorted(), numbers)
        assertEquals(numbers.size, numbers.toSet().size)
    }

    @Test fun theOrderSurvivesTheRoundTripThroughATag() {
        val order = DownloadOrder.next()
        assertEquals(order, DownloadOrder.orderOf(setOf("audio-download", "track:1", DownloadOrder.tag(order))))
        assertNull(DownloadOrder.orderOf(setOf("audio-download", "track:1")))
    }

    @Test fun tagsOfDifferentNumbersSortLikeTheNumbers() {
        val numbers = listOf(9L, 10L, 99L, 100L, 1_700_000_000_000L, 1_700_000_000_001L)
        assertEquals(numbers.map(DownloadOrder::tag), numbers.map(DownloadOrder::tag).sorted())
    }

    @Test fun theFirstDownloadWaitsForNobody() {
        val all = queue(true, true, true)
        assertTrue(DownloadOrder.waitsFor(all[0], all).isEmpty())
    }

    @Test fun eachLaterDownloadWaitsForEveryEarlierOne() {
        val all = queue(true, true, true)
        assertEquals(listOf("work-0"), DownloadOrder.waitsFor(all[1], all).map { it.id })
        assertEquals(listOf("work-0", "work-1"), DownloadOrder.waitsFor(all[2], all).map { it.id })
    }

    @Test fun aFinishedDownloadIsNotWaitedFor() {
        val all = queue(false, true, true)
        assertTrue(DownloadOrder.waitsFor(all[1], all).isEmpty())
        assertEquals(listOf("work-1"), DownloadOrder.waitsFor(all[2], all).map { it.id })
    }

    @Test fun aLaterDownloadIsNeverWaitedFor() {
        val all = queue(true, true, true)
        assertTrue(DownloadOrder.waitsFor(all[0], all).none { it.order > all[0].order })
    }

    @Test fun twoDownloadsWithTheSameNumberTakeTurnsByTheirId() {
        val first = Entry("a", 5L, true); val second = Entry("b", 5L, true)
        assertTrue(DownloadOrder.waitsFor(first, listOf(first, second)).isEmpty())
        assertEquals(listOf("a"), DownloadOrder.waitsFor(second, listOf(first, second)).map { it.id })
    }

    @Test fun theTurnsPassOneAtATimeInTheOrderOfQueuing() {
        // Whoever has nobody ahead goes next. When it finishes, the next one goes. This lists the turns of ten downloads queued in a shuffled order of arrival.
        var all = (0 until 10).map { Entry("work-$it", 100L + it, true) }.shuffled(java.util.Random(7))
        val turns = mutableListOf<String>()
        while (all.isNotEmpty()) {
            val ready = all.filter { DownloadOrder.waitsFor(it, all).isEmpty() }
            assertEquals("Exactly one download is ready", 1, ready.size)
            turns += ready.single().id
            all = all.filter { it.id != ready.single().id }
        }
        assertEquals((0 until 10).map { "work-$it" }, turns)
    }
}
