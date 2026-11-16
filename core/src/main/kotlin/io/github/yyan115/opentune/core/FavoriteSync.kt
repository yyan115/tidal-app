package io.github.yyan115.opentune.core

/** Sends the changes of saved tracks that wait for the service. */
object FavoriteSync {
    /**
     * Sends each of [changes], a track id with whether the track is saved, while [active] holds.
     * [sent] runs after the service accepted a change, and [rejected] after it refused one for good.
     * A refused change does not stop the changes after it. Any other failure, such as a lost connection,
     * ends the upload with that failure, and the changes that are left wait for the next upload.
     */
    suspend fun upload(
        changes: Map<String, Boolean>,
        active: () -> Boolean = { true },
        send: suspend (id: String, favorite: Boolean) -> Unit,
        sent: suspend (id: String, favorite: Boolean) -> Unit,
        rejected: suspend (id: String, favorite: Boolean) -> Unit,
    ) {
        for ((id, favorite) in changes) {
            if (!active()) return
            try { send(id, favorite) }
            catch (error: ServiceException) {
                if (!error.refused) throw error
                rejected(id, favorite)
                continue
            }
            sent(id, favorite)
        }
    }
}
