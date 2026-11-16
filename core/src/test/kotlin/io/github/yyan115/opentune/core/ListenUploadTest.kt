package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ListenUploadTest {
    /** A queue of listens that hands out batches of two, like the store of the app. */
    private class Queue(count: Int) {
        val waiting = (1..count).map { it.toLong() to """{"n":$it}""" }.toMutableList()
        var submitted = 0
        fun next() = waiting.take(2)
        fun sent(ids: List<Long>) { waiting.removeAll { it.first in ids } }
    }

    private fun flush(queue: Queue, answer: (Int) -> Exception?) = ListenUpload.flush(queue::next, { queue.submitted++; answer(queue.submitted)?.let { throw it } }, queue::sent)

    @Test fun everyBatchIsSentUntilNothingWaits() {
        val queue = Queue(5)
        flush(queue) { null }
        assertEquals(3, queue.submitted)
        assertEquals(0, queue.waiting.size)
    }

    @Test fun aRateLimitKeepsEveryListenForLater() {
        val queue = Queue(4)
        flush(queue) { ServiceException(429, "Too many requests.") }
        assertEquals("The upload stops at the rate limit", 1, queue.submitted)
        assertEquals(4, queue.waiting.size)
    }

    @Test fun aTokenThatIsNotAcceptedKeepsEveryListenForLater() {
        val queue = Queue(4)
        flush(queue) { ServiceException(401, "The server rejected the token.") }
        assertEquals(4, queue.waiting.size)
    }

    @Test fun aServerThatCannotBeReachedKeepsEveryListenForLater() {
        val queue = Queue(4)
        flush(queue) { IOException("The server answered with HTTP 503.") }
        assertEquals(4, queue.waiting.size)
    }

    @Test fun aBatchThatIsRefusedForGoodIsRemovedAndTheNextBatchIsSent() {
        val queue = Queue(4)
        flush(queue) { attempt -> if (attempt == 1) ServiceException(400, "The server rejected the listen.") else null }
        assertEquals(2, queue.submitted)
        assertEquals(0, queue.waiting.size)
    }
}
