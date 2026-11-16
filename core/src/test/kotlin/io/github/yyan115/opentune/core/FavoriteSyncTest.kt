package io.github.yyan115.opentune.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FavoriteSyncTest {
    private val changes = linkedMapOf("a" to true, "b" to true, "c" to false)

    private class Outcome { val sent = mutableListOf<String>(); val rejected = mutableListOf<String>() }

    private suspend fun upload(outcome: Outcome, active: () -> Boolean = { true }, send: suspend (String, Boolean) -> Unit) =
        FavoriteSync.upload(changes, active, send, { id, _ -> outcome.sent += id }, { id, _ -> outcome.rejected += id })

    @Test fun everyChangeIsSentInOrder() = runBlocking {
        val outcome = Outcome()
        upload(outcome) { _, _ -> }
        assertEquals(listOf("a", "b", "c"), outcome.sent)
        assertTrue(outcome.rejected.isEmpty())
    }

    @Test fun aChangeThatTheServiceRefusesDoesNotStopTheChangesAfterIt() = runBlocking {
        val outcome = Outcome()
        upload(outcome) { id, _ -> if (id == "b") throw ServiceException(404, "The track does not exist.") }
        assertEquals(listOf("a", "c"), outcome.sent)
        assertEquals(listOf("b"), outcome.rejected)
    }

    @Test fun aServerFaultEndsTheUploadAndKeepsTheRestWaiting() = runBlocking {
        val outcome = Outcome()
        val failure = runCatching { upload(outcome) { id, _ -> if (id == "b") throw ServiceException(503, "The service is busy.") } }.exceptionOrNull()
        assertTrue(failure is ServiceException)
        assertEquals(listOf("a"), outcome.sent)
        assertTrue("Nothing is dropped after a fault that passes by", outcome.rejected.isEmpty())
    }

    @Test fun aLostConnectionEndsTheUploadAndKeepsTheRestWaiting() = runBlocking {
        val outcome = Outcome()
        val failure = runCatching { upload(outcome) { id, _ -> if (id == "a") throw IOException("offline") } }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(outcome.sent.isEmpty() && outcome.rejected.isEmpty())
    }

    @Test fun theUploadStopsWhenItIsNoLongerActive() = runBlocking {
        val outcome = Outcome()
        upload(outcome, active = { outcome.sent.size < 1 }) { _, _ -> }
        assertEquals(listOf("a"), outcome.sent)
    }

    @Test fun onlyAClientErrorThatCannotPassByCountsAsRefused() {
        for (status in listOf(400, 403, 404, 409, 422)) assertTrue("$status", ServiceException(status, "").refused)
        for (status in listOf(401, 408, 429, 500, 502, 503, 0)) assertFalse("$status", ServiceException(status, "").refused)
    }
}
