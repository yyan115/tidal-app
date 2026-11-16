package io.github.yyan115.opentune.core

import org.json.JSONObject
import java.io.IOException

/** Sends the listens that wait for the scrobbling server, a batch at a time. */
object ListenUpload {
    /**
     * [next] gives the next waiting listens as pairs of an id and a payload, [submit] sends them and [sent] removes them from the queue.
     * A batch that the server refuses for good is removed, so that it cannot block the listens after it.
     * The upload ends when nothing waits, or when the server cannot take listens at the moment:
     * it cannot be reached, it does not accept the token, or it limits the rate. The listens then wait for the next upload.
     */
    fun flush(next: () -> List<Pair<Long, String>>, submit: (List<JSONObject>) -> Unit, sent: (List<Long>) -> Unit) {
        while (true) {
            val pending = next()
            if (pending.isEmpty()) return
            try { submit(pending.map { JSONObject(it.second) }) }
            catch (error: ServiceException) { if (!error.refused) return }
            catch (_: IOException) { return }
            sent(pending.map { it.first })
        }
    }
}
