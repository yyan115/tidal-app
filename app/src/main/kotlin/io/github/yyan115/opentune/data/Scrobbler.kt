package io.github.yyan115.opentune.data

import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.BuildConfig
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.Listen
import io.github.yyan115.opentune.core.ListenBrainzClient
import io.github.yyan115.opentune.core.ServiceException
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Sends finished listens to ListenBrainz when the listener turned scrobbling on and added a token.
 * Listens wait in a local queue until the server accepts them, so a lost connection loses nothing.
 */
@UnstableApi
class Scrobbler(private val app: OpenTuneApp) {
    private val lock = Mutex()
    private val client by lazy { ListenBrainzClient(app.http, { app.settings.state.value.scrobbleServer }, { app.sessions.scrobbleToken() }, BuildConfig.VERSION_NAME) }
    val enabled: Boolean get() = app.settings.state.value.scrobble && !app.sessions.scrobbleToken().isNullOrBlank()

    suspend fun playingNow(track: Track) {
        if (!enabled) return
        withContext(Dispatchers.IO) { runCatching { client.playingNow(track.artist, track.title, track.album, track.durationMs) } }
    }

    suspend fun listened(track: Track, startedAtMs: Long) {
        if (!enabled) return
        withContext(Dispatchers.IO) {
            val listen = Listen(startedAtMs / 1000, track.artist, track.title, track.album, track.durationMs, track.trackNumber)
            app.library.queueScrobble(listen.listenedAt, client.listenJson(listen).toString())
        }
        flush()
    }

    /** Sends the queued listens. A rejected batch is dropped, and an unreachable server keeps the queue for later. */
    suspend fun flush() {
        if (!enabled) return
        lock.withLock {
            withContext(Dispatchers.IO) {
                while (true) {
                    val pending = app.library.pendingScrobbles(50)
                    if (pending.isEmpty()) return@withContext
                    try {
                        client.submit(pending.map { JSONObject(it.second) })
                        app.library.scrobblesSent(pending.map { it.first })
                    } catch (error: ServiceException) {
                        if (error.status == 401) return@withContext
                        app.library.scrobblesSent(pending.map { it.first })
                    } catch (_: IOException) { return@withContext }
                }
            }
        }
    }

    /** Checks a server address and token that have not been saved yet. Returns the user name, or null for a rejected token. */
    suspend fun check(server: String, token: String): String? = withContext(Dispatchers.IO) {
        ListenBrainzClient(app.http, { server }, { token }).validate()
    }
}
