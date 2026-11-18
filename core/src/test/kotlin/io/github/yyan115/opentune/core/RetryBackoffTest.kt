package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryBackoffTest {
    @Test fun eachTryWaitsAtLeastAsLongAsTheOneBeforeAndThenTheTriesEnd() {
        val backoff = RetryBackoff()
        val waits = generateSequence { backoff.next() }.toList()
        assertEquals(RetryBackoff.DEFAULT_WAITS, waits)
        assertEquals(waits.sorted(), waits)
        assertNull("No further try after the last wait", backoff.next())
        assertNull(backoff.next())
    }

    @Test fun aResetStartsAgainWithTheShortestWait() {
        val backoff = RetryBackoff(listOf(1L, 2L, 3L))
        assertEquals(1L, backoff.next()); assertEquals(2L, backoff.next())
        backoff.reset()
        assertEquals(1L, backoff.next())
    }

    @Test fun repeatedFailuresDoNotStartTheScheduleAgain() {
        // Each failed try asks for the next wait. Only a reset goes back to the start.
        val backoff = RetryBackoff(listOf(1L, 2L))
        assertEquals(listOf(1L, 2L, null, null), List(4) { backoff.next() })
    }
}
