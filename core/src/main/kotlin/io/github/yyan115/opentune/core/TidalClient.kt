package io.github.yyan115.opentune.core

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

    fun beginPkce(): PkceLogin {
        fun random(size: Int): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(size).also { java.security.SecureRandom().nextBytes(it) })
        val verifier = random(32)
        val state = random(24)
        val unique = random(16)
        val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = "https://login.tidal.com/authorize".toHttpUrl().newBuilder().apply {
            mapOf("response_type" to "code", "redirect_uri" to PKCE_REDIRECT, "client_id" to identity().id,
                "lang" to "EN", "appMode" to "android", "client_unique_key" to unique,
                "code_challenge" to challenge, "code_challenge_method" to "S256", "restrict_signup" to "true",
                "scope" to "r_usr w_usr", "state" to state).forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return PkceLogin(url.toString(), verifier, state, unique, System.currentTimeMillis() + 15 * 60_000)
    }

    suspend fun completePkce(login: PkceLogin, redirect: String) {
        if (System.currentTimeMillis() >= login.expiresAt) throw ServiceException(401, "This sign-in expired. Start again.")
        val expectedRedirect = login.authorizationUrl.toHttpUrl().queryParameter("redirect_uri").orEmpty()
        if (!SignInCallback.matches(redirect, expectedRedirect)) {
            throw ServiceException(400, "This is not a TIDAL sign-in callback.")
        }
        val parameters = SignInCallback.parameters(redirect)
        val returnedState = parameters["state"]?.singleOrNull().orEmpty()
        if (!java.security.MessageDigest.isEqual(login.state.toByteArray(), returnedState.toByteArray())) throw ServiceException(400, "This response belongs to a different sign-in. Start again.")
        if (parameters.containsKey("error")) throw ServiceException(401, "Sign-in was cancelled or declined on TIDAL. Start a new sign-in.")
        val code = parameters["code"]?.singleOrNull()?.takeIf(String::isNotBlank) ?: throw ServiceException(400, "TIDAL did not return an authorization code.")
        val expectedGeneration = generation.get()
        val response = try { withContext(Dispatchers.IO) {
            form("token", mapOf("code" to code, "client_id" to identity().id, "grant_type" to "authorization_code",
                "redirect_uri" to expectedRedirect, "scope" to "r_usr w_usr", "code_verifier" to login.verifier, "client_unique_key" to login.uniqueKey))
        } } catch (error: OAuthException) { throw ServiceException(401, when (error.code) {
            "invalid_grant", "expired_token" -> "This sign-in expired or was already used. Start a new sign-in."
            "invalid_client", "unauthorized_client" -> "TIDAL rejected this app's sign-in configuration. Update OpenTune and try again."
            else -> "TIDAL could not finish sign-in. Start a new sign-in and try again."
        }) }
        authenticate(response, expectedGeneration)
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
        JsonCodec.items(request("users/${userId()}/playlistsAndFavoritePlaylists", mapOf("limit" to "50", "offset" to "$offset")))
            .map { JsonCodec.release(it.optJSONObject("playlist") ?: it, ReleaseType.PLAYLIST) }

    suspend fun tracks(release: Release, offset: Int = 0): List<Track> {
        val path = when (release.type) {
            ReleaseType.ALBUM -> "albums/${release.id}/tracks"
            ReleaseType.PLAYLIST -> "playlists/${release.id}/items"
            ReleaseType.ARTIST -> "artists/${release.id}/toptracks"
        }
        return JsonCodec.items(request(path, mapOf("limit" to "100", "offset" to "$offset"))).map(JsonCodec::track)
    }

    suspend fun allTracks(release: Release): List<Track> = allPages { tracks(release, it) }
    suspend fun allFavorites(): List<Track> = allPages(page = ::favorites)
    suspend fun allFavoriteAlbums(): List<Release> = allPages(page = ::favoriteAlbums)
    suspend fun allPlaylists(): List<Release> = allPages(50, ::playlists)

    private suspend fun <T> allPages(pageSize: Int = 100, page: suspend (Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = page(offset)
            result.addAll(next)
            if (next.size < pageSize) return result
            offset += next.size
        }
    }

    suspend fun artistAlbums(id: String): List<Release> = allPages { offset ->
        JsonCodec.items(request("artists/$id/albums", mapOf("limit" to "100", "offset" to "$offset"))).map { JsonCodec.release(it, ReleaseType.ALBUM) }
    }

    suspend fun stream(id: String, quality: AudioQuality): StreamInfo {
        val stream = StreamManifest.parse(request("tracks/$id/playbackinfopostpaywall", mapOf("audioquality" to quality.name,
            "playbackmode" to "STREAM", "assetpresentation" to "FULL")))
        if (quality in setOf(AudioQuality.LOSSLESS, AudioQuality.HI_RES_LOSSLESS) &&
            !stream.codec.equals("flac", ignoreCase = true) && !stream.codec.equals("alac", ignoreCase = true)) {
            throw ServiceException(406, "TIDAL returned lossy audio instead of lossless. Sign in again using Sign in to TIDAL, or choose High quality to allow AAC.")
        }
        return stream
    }

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

    suspend fun lyrics(id: String): String = try {
        request("tracks/$id/lyrics").let { it.optString("lyrics").ifBlank { it.optString("text") } }
    } catch (error: ServiceException) { if (error.status == 404) "" else throw error }

    fun signOut() = synchronized(accountLock) { generation.incrementAndGet(); sessions.clear(); etags.clear() }

    private fun saveSession(session: Session, expectedGeneration: Long) = synchronized(accountLock) {
        if (generation.get() != expectedGeneration) throw kotlinx.coroutines.CancellationException("Account changed")
        sessions.write(session)
    }

    private suspend fun authenticate(response: JSONObject, expectedGeneration: Long) {
        val pending = tokenSession(response)
        val profile = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(apiBase + "sessions").header("Authorization", "Bearer ${pending.accessToken}").build()).execute().use { result ->
                if (!result.isSuccessful) throw ServiceException(result.code, errorMessage(result.code))
                try { JSONObject(result.body.string()) } catch (_: Exception) { throw ServiceException(502, "TIDAL returned an unreadable account response.") }
            }
        }
        currentCoroutineContext().ensureActive()
        saveSession(pending.copy(userId = profile.get("userId").toString(), country = profile.optString("countryCode", "US")), expectedGeneration)
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
                throw ServiceException(response.code, "TIDAL rejected this sign-in request. Start a new sign-in and try again.")
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

    companion object { const val PKCE_REDIRECT = SignInCallback.NATIVE }
}
