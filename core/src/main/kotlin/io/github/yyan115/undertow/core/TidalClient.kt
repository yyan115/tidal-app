package io.github.yyan115.undertow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

interface SessionStore {
    fun read(): Session?
    fun write(session: Session)
    fun clear()
}

data class ClientIdentity(val id: String, val secret: String) {
    override fun toString() = "ClientIdentity(configured=${id.isNotBlank()})"
}

class TidalClient(
    private val identity: () -> ClientIdentity,
    private val sessions: SessionStore,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(false).build(),
    private val apiBase: String = "https://api.tidal.com/v1/",
    private val authBase: String = "https://auth.tidal.com/v1/oauth2/",
) {
    private val refreshLock = Mutex()
    private val generation = AtomicLong()
    private val accountLock = Any()
    private val etags = ConcurrentHashMap<String, String>()
    val signedIn get() = sessions.read() != null
    val accountId get() = sessions.read()?.userId.orEmpty()

    suspend fun beginLogin(): DeviceLogin = withContext(Dispatchers.IO) {
        val json = form("device_authorization", mapOf("client_id" to identity().id, "scope" to "r_usr w_usr"))
        val rawUrl = json.getString("verificationUriComplete")
        val url = if (rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        val host = url.toHttpUrl().host
        if (host != "tidal.com" && !host.endsWith(".tidal.com")) throw ServiceException(502, "Unexpected sign-in address.")
        DeviceLogin(json.getString("deviceCode"), json.getString("userCode"), url,
            System.currentTimeMillis() + json.getLong("expiresIn") * 1000, json.optLong("interval", 5).coerceAtLeast(2))
    }

    suspend fun awaitLogin(login: DeviceLogin) {
        val loginGeneration = generation.get()
        var interval = login.intervalSeconds
        while (System.currentTimeMillis() < login.expiresAt) {
            delay(interval * 1000)
            val client = identity()
            try {
                val response = withContext(Dispatchers.IO) {
                    form("token", mapOf("client_id" to client.id, "client_secret" to client.secret,
                        "device_code" to login.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "scope" to "r_usr w_usr"))
                }
                saveSession(tokenSession(response), loginGeneration)
                val profile = request("sessions")
                val current = sessions.read() ?: throw ServiceException(401, "The session was not saved.")
                saveSession(current.copy(userId = profile.get("userId").toString(), country = profile.optString("countryCode", "US")), loginGeneration)
                return
            } catch (error: OAuthException) {
                when (error.code) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "expired_token", "access_denied" -> throw ServiceException(401, "Sign-in expired or was declined. Try again.")
                    else -> throw ServiceException(401, "TIDAL could not authorize this client.")
                }
            }
        }
        throw ServiceException(401, "The sign-in code expired. Start a new sign-in.")
    }

    suspend fun search(query: String): SearchResult {
        if (query.isBlank()) return SearchResult()
        val data = request("search", mapOf("query" to query, "types" to "TRACKS,ALBUMS,ARTISTS,PLAYLISTS", "limit" to "50"))
        fun collection(key: String) = data.optJSONObject(key)?.let(JsonCodec::items).orEmpty()
        return SearchResult(collection("tracks").map(JsonCodec::track),
            collection("albums").map { JsonCodec.release(it, ReleaseType.ALBUM) },
            collection("artists").map { JsonCodec.release(it, ReleaseType.ARTIST) },
            collection("playlists").map { JsonCodec.release(it, ReleaseType.PLAYLIST) })
    }

    suspend fun favorites(offset: Int = 0): List<Track> =
        JsonCodec.items(request("users/${userId()}/favorites/tracks", mapOf("limit" to "100", "offset" to "$offset", "order" to "DATE", "orderDirection" to "DESC"))).map(JsonCodec::track)

    suspend fun favoriteAlbums(offset: Int = 0): List<Release> =
        JsonCodec.items(request("users/${userId()}/favorites/albums", mapOf("limit" to "100", "offset" to "$offset"))).map { JsonCodec.release(it, ReleaseType.ALBUM) }

    suspend fun playlists(offset: Int = 0): List<Release> =
        JsonCodec.items(request("users/${userId()}/playlists", mapOf("limit" to "100", "offset" to "$offset"))).map { JsonCodec.release(it, ReleaseType.PLAYLIST) }

    suspend fun tracks(release: Release, offset: Int = 0): List<Track> {
        val path = when (release.type) {
            ReleaseType.ALBUM -> "albums/${release.id}/tracks"
            ReleaseType.PLAYLIST -> "playlists/${release.id}/items"
            ReleaseType.ARTIST -> "artists/${release.id}/toptracks"
        }
        return JsonCodec.items(request(path, mapOf("limit" to "100", "offset" to "$offset"))).map(JsonCodec::track)
    }

    suspend fun allTracks(release: Release): List<Track> = allPages { tracks(release, it) }
    suspend fun allFavorites(): List<Track> = allPages(::favorites)
    suspend fun allFavoriteAlbums(): List<Release> = allPages(::favoriteAlbums)
    suspend fun allPlaylists(): List<Release> = allPages(::playlists)

    private suspend fun <T> allPages(page: suspend (Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = page(offset)
            result.addAll(next)
            if (next.size < 100) return result
            offset += next.size
        }
    }

    suspend fun artistAlbums(id: String): List<Release> = allPages { offset ->
        JsonCodec.items(request("artists/$id/albums", mapOf("limit" to "100", "offset" to "$offset"))).map { JsonCodec.release(it, ReleaseType.ALBUM) }
    }

    suspend fun stream(id: String, quality: AudioQuality): StreamInfo = StreamManifest.parse(
        request("tracks/$id/playbackinfopostpaywall", mapOf("audioquality" to quality.name,
            "playbackmode" to "STREAM", "assetpresentation" to "FULL")))

    suspend fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) request("users/${userId()}/favorites/tracks", method = "POST", form = mapOf("trackIds" to id))
        else request("users/${userId()}/favorites/tracks/$id", method = "DELETE")
    }

    suspend fun createPlaylist(title: String): Release = JsonCodec.release(
        request("users/${userId()}/playlists", method = "POST", form = mapOf("title" to title, "description" to "")), ReleaseType.PLAYLIST).copy(ownerId = userId())

    suspend fun addToPlaylist(playlistId: String, trackId: String) {
        require(!trackId.startsWith("local:")) { "Local files cannot be uploaded to TIDAL playlists." }
        val metadata = request("playlists/$playlistId")
        val etag = etags["playlists/$playlistId"] ?: throw ServiceException(409, "Refresh this playlist before editing it.")
        request("playlists/$playlistId/items", method = "POST", form = mapOf("trackIds" to trackId,
            "toIndex" to metadata.optInt("numberOfTracks").toString(), "onDupes" to "SKIP", "onArtifactNotFound" to "FAIL"),
            match = etag)
    }

    suspend fun removeFromPlaylist(playlistId: String, index: Int, expectedTrackId: String) {
        request("playlists/$playlistId")
        val etag = etags["playlists/$playlistId"] ?: throw ServiceException(409, "Refresh this playlist before editing it.")
        val item = JsonCodec.items(request("playlists/$playlistId/items", mapOf("limit" to "1", "offset" to "$index"))).singleOrNull()
        if (item == null || JsonCodec.track(item).id != expectedTrackId) throw ServiceException(409, "This playlist changed elsewhere. Refresh it before editing again.")
        request("playlists/$playlistId/items/$index", method = "DELETE", match = etag)
    }

    suspend fun renamePlaylist(id: String, title: String) { request("playlists/$id", method = "POST", form = mapOf("title" to title)) }
    suspend fun deletePlaylist(id: String) { request("playlists/$id", method = "DELETE") }
    suspend fun setAlbumFavorite(id: String, favorite: Boolean) {
        if (favorite) request("users/${userId()}/favorites/albums", method = "POST", form = mapOf("albumIds" to id))
        else request("users/${userId()}/favorites/albums/$id", method = "DELETE")
    }

    suspend fun lyrics(id: String): String = request("tracks/$id/lyrics").let {
        it.optString("lyrics").ifBlank { it.optString("text") }
    }

    fun signOut() = synchronized(accountLock) { generation.incrementAndGet(); sessions.clear(); etags.clear() }

    private fun saveSession(session: Session, expectedGeneration: Long) = synchronized(accountLock) {
        if (generation.get() != expectedGeneration) throw kotlinx.coroutines.CancellationException("Account changed")
        sessions.write(session)
    }

    private fun userId() = sessions.read()?.userId?.takeIf(String::isNotBlank)
        ?: throw ServiceException(401, "Sign in to access your TIDAL collection.")

    private suspend fun accessToken(force: Boolean = false): String = refreshLock.withLock {
        val (requestGeneration, session) = synchronized(accountLock) {
            generation.get() to (sessions.read() ?: throw ServiceException(401, "Sign in to TIDAL to continue."))
        }
        if (!force && !session.needsRefresh()) return@withLock session.accessToken
        val client = identity()
        val response = try {
            withContext(Dispatchers.IO) { form("token", mapOf("client_id" to client.id, "client_secret" to client.secret,
                "grant_type" to "refresh_token", "refresh_token" to session.refreshToken)) }
        } catch (_: OAuthException) {
            throw ServiceException(401, "Your TIDAL session expired. Sign in again.")
        }
        tokenSession(response, session).also { saveSession(it, requestGeneration) }.accessToken
    }

    private suspend fun request(path: String, parameters: Map<String, String> = emptyMap(),
        method: String = "GET", form: Map<String, String>? = null, match: String? = null): JSONObject {
        val requestGeneration = generation.get()
        for (attempt in 0..1) {
            currentCoroutineContext().ensureActive()
            if (generation.get() != requestGeneration) throw kotlinx.coroutines.CancellationException("Account changed")
            val token = accessToken(force = attempt == 1)
            val country = sessions.read()?.country ?: "US"
            val result = withContext(Dispatchers.IO) {
                val url = (apiBase + path).toHttpUrl().newBuilder().addQueryParameter("countryCode", country).apply {
                    parameters.forEach { (key, value) -> addQueryParameter(key, value) }
                }.build()
                val body = form?.let { values -> FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build() }
                val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
                    .header("Accept", "application/json").apply { if (match != null) header("If-None-Match", match) }.method(method,
                        body ?: if (method in setOf("POST", "PUT")) ByteArray(0).toRequestBody(null) else null).build()
                http.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    Triple(response.code, text, response.header("ETag"))
                }
            }
            currentCoroutineContext().ensureActive()
            if (generation.get() != requestGeneration) throw kotlinx.coroutines.CancellationException("Account changed")
            if (result.first == 401 && attempt == 0) continue
            if (result.first !in 200..299) throw ServiceException(result.first, errorMessage(result.first))
            result.third?.let { etags[path] = it }
            return if (result.second.isBlank()) JSONObject() else try { JSONObject(result.second) }
                catch (_: Exception) { throw ServiceException(502, "TIDAL returned an unreadable response.") }
        }
        throw ServiceException(401, "Your session could not be renewed.")
    }

    private fun form(path: String, values: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build()
        return http.newCall(Request.Builder().url(authBase + path).post(body).build()).execute().use { response ->
            val json = try { JSONObject(response.body.string()) } catch (_: Exception) {
                throw ServiceException(response.code, "TIDAL rejected this sign-in request. Try opening the link in another browser.")
            }
            if (!response.isSuccessful) throw OAuthException(json.optString("error", "unknown"))
            json
        }
    }

    private fun tokenSession(response: JSONObject, previous: Session? = null) = Session(
        accessToken = response.getString("access_token"),
        refreshToken = response.optString("refresh_token").ifBlank { previous?.refreshToken.orEmpty() },
        expiresAt = System.currentTimeMillis() + response.optLong("expires_in", 3600) * 1000,
        userId = previous?.userId.orEmpty(), country = previous?.country ?: "US")

    private class OAuthException(val code: String) : Exception("OAuth authorization failed")
    private fun errorMessage(status: Int) = when (status) {
        401 -> "Your TIDAL session expired. Sign in again."
        403 -> "TIDAL did not grant access. Check your subscription, region, and client configuration."
        404 -> "This item is no longer available in your region."
        429 -> "TIDAL is limiting requests. Wait a moment and try again."
        409, 412 -> "This playlist changed elsewhere. Refresh it before editing again."
        in 500..599 -> "TIDAL is temporarily unavailable. Try again shortly."
        else -> "The request could not be completed (HTTP $status)."
    }
}
