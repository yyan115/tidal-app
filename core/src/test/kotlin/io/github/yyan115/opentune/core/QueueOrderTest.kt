package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {
    /** A queue as letters, where a letter followed by a star was added by hand. */
    private fun queue(vararg items: String) = items.map { it.endsWith("*") }

    /** Adds [added] to the queue like the app does and returns the new order. */
    private fun add(items: List<String>, current: Int, added: String, playNext: Boolean): List<String> {
        val flags = items.map { it.endsWith("*") }
        return items.toMutableList().apply { add(QueueOrder.insertIndex(flags, current, playNext), "$added*") }
    }

    @Test fun addedTracksPlayBeforeTheRestOfALongPlaylistAndInTheOrderOfAdding() {
        var items = ('A'..'Z').map { it.toString() }
        items = add(items, 0, "X", playNext = false)
        items = add(items, 0, "Y", playNext = false)
        assertEquals(listOf("A", "X*", "Y*", "B", "C"), items.take(5))
        assertEquals("The rest of the playlist keeps its order", ('B'..'Z').map { it.toString() }, items.drop(3))
    }

    @Test fun playNextGoesAheadOfTheGroupAndEachPlayNextGoesAheadOfTheEarlierOne() {
        var items = listOf("A", "B", "C")
        items = add(items, 0, "X", playNext = false)
        items = add(items, 0, "Y", playNext = false)
        items = add(items, 0, "W", playNext = true)
        assertEquals(listOf("A", "W*", "X*", "Y*", "B", "C"), items)
        items = add(items, 0, "V", playNext = true)
        assertEquals(listOf("A", "V*", "W*", "X*", "Y*", "B", "C"), items)
    }

    @Test fun theGroupIsCountedFromTheTrackThatPlaysNow() {
        // A has played. B plays now, and X and Y are still to come.
        val items = listOf("A", "B", "X*", "Y*", "C", "D")
        assertEquals(2, QueueOrder.leadingManual(items.map { it.endsWith("*") }, current = 1))
        assertEquals(listOf("A", "B", "X*", "Y*", "Z*", "C", "D"), add(items, 1, "Z", playNext = false))
    }

    @Test fun aMarkedTrackAfterAnUnmarkedOneIsNotPartOfTheGroup() {
        val flags = queue("A", "B", "C", "X*", "D")
        assertEquals(0, QueueOrder.leadingManual(flags, 0))
        assertEquals("Add to queue goes right after the current track", 1, QueueOrder.insertIndex(flags, 0, playNext = false))
    }

    @Test fun theLastTrackAndAnEmptyQueueAreHandled() {
        assertEquals(1, QueueOrder.insertIndex(queue("A"), 0, playNext = false))
        assertEquals(1, QueueOrder.insertIndex(queue("A"), 0, playNext = true))
        assertEquals(0, QueueOrder.insertIndex(emptyList(), 0, playNext = false))
        assertEquals(3, QueueOrder.insertIndex(queue("A", "B", "X*"), 1, playNext = false))
        assertEquals(0, QueueOrder.leadingManual(queue("A", "B"), 1))
    }

    @Test fun theMarksOfASavedQueueComeBack() {
        val flags = queue("A", "X*", "Y*", "B", "Z*")
        assertEquals("1,2,4", QueueOrder.encode(flags))
        assertEquals(flags, QueueOrder.decode("1,2,4", 5))
        assertEquals(List(3) { false }, QueueOrder.decode(null, 3))
    }

    @Test fun textThatCannotBeReadGivesNoMarks() {
        assertEquals(List(3) { false }, QueueOrder.decode("", 3))
        assertEquals(List(3) { false }, QueueOrder.decode("a,,b", 3))
        assertEquals(listOf(false, true, false), QueueOrder.decode("1, 7, -2, x", 3))
    }
}
