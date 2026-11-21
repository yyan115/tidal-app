package io.github.yyan115.opentune.data

import java.util.concurrent.atomic.AtomicLong

/**
 * The order in which queued downloads take their turns: one at a time, in the order in which they were queued.
 * A queued download carries its number as a tag, so that a download that waits for its turn can see who is ahead of it.
 */
object DownloadOrder {
    private const val PREFIX = "order:"
    private val last = AtomicLong(0)

    /** A number that is larger than every number given before it, and also larger than the numbers of an earlier run of the app. */
    fun next(): Long = last.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

    /** The tag of a download. The number has a fixed length, so that the tags sort like the numbers. */
    fun tag(order: Long): String = PREFIX + order.toString().padStart(19, '0')

    fun orderOf(tags: Set<String>): Long? = tags.firstOrNull { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()

    /** A queued download. [active] is true while it runs or waits, and false once it is finished. */
    data class Entry(val id: String, val order: Long, val active: Boolean)

    /** The downloads that [me] has to wait for: the ones that are queued before it and not finished. Equal numbers go by id. */
    fun waitsFor(me: Entry, all: List<Entry>): List<Entry> = all.filter { other ->
        other.active && other.id != me.id && (other.order < me.order || other.order == me.order && other.id < me.id)
    }.sortedWith(compareBy({ it.order }, { it.id }))
}
