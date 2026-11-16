package io.github.yyan115.opentune.core

/**
 * The order of the queue when the listener adds a track by hand.
 * Tracks that the listener added form their own group right after the track that plays. It plays first, in the order of adding,
 * ahead of the rest of the album or playlist. "Play next" goes ahead of that group and "Add to queue" goes at its end.
 * Every function takes [manual], which tells for each item of the queue whether the listener added it by hand.
 */
object QueueOrder {
    /** The number of items in a row after the item at [current] that the listener added by hand. */
    fun leadingManual(manual: List<Boolean>, current: Int): Int {
        var count = 0
        while (current + 1 + count < manual.size && manual[current + 1 + count]) count++
        return count
    }

    /** The position for a new track: right after the current one for "Play next", or after the group for "Add to queue". */
    fun insertIndex(manual: List<Boolean>, current: Int, playNext: Boolean): Int {
        val first = (current + 1).coerceIn(0, manual.size)
        return if (playNext) first else first + leadingManual(manual, current)
    }

    /** The marks of a queue as text, for a queue that is saved. The positions of the marked items, separated by commas. */
    fun encode(manual: List<Boolean>): String = manual.indices.filter { manual[it] }.joinToString(",")

    /** The marks of a saved queue of [size] items. Text that cannot be read, or a position outside the queue, gives no mark. */
    fun decode(text: String?, size: Int): List<Boolean> {
        val marked = text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until size }.toSet()
        return List(size) { it in marked }
    }
}
