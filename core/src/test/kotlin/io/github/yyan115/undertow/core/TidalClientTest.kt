package io.github.yyan115.undertow.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl

class TidalClientTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    private lateinit var client: TidalClient
    @Before fun setUp() {
        server = MockWebServer(); server.start()
        store = MemoryStore(Session("access-before", "refresh-before", System.currentTimeMillis() + 3_600_000, "123", "IN"))
        client = TidalClient({ ClientIdentity("test-client", "test-secret") }, store,
            apiBase = server.url("/v1/").toString(), authBase = server.url("/oauth/").toString())
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun encodesSearchAndUsesAccountCountry() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tracks":{"items":[{"id":42,"title":"Song","duration":182,"artists":[{"name":"First"},{"name":"Second"}],"album":{"id":7,"title":"Album","cover":"a-b-c"}}]}}"""))
        val result = client.search("music & friends")
        assertEquals("First, Second", result.tracks.single().artist)
        assertEquals(182000L, result.tracks.single().durationMs)
        val request = server.takeRequest()
        assertEquals("music & friends", request.requestUrl!!.queryParameter("query"))
        assertEquals("IN", request.requestUrl!!.queryParameter("countryCode"))
        assertEquals("Bearer access-before", request.getHeader("Authorization"))
    }

    @Test fun renewsOnceAfter401AndKeepsRotatedRefreshToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"access_token":"access-after","refresh_token":"refresh-after","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"tracks":{"items":[]}}"""))
        client.search("song")
        server.takeRequest()
        val refresh = server.takeRequest()
        assertEquals("/oauth/token", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("refresh_token=refresh-before"))
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("Bearer access-after", server.takeRequest().getHeader("Authorization"))
        assertEquals("refresh-after", store.read()!!.refreshToken)
        assertEquals("123", store.read()!!.userId)
    }

    @Test fun preservesRefreshTokenWhenServerDoesNotRotateIt() = runBlocking {
        store.value = store.value!!.copy(expiresAt = 0)
        server.enqueue(MockResponse().setBody("""{"access_token":"new","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"tracks":{"items":[]}}"""))
        client.search("song")
        assertEquals("refresh-before", store.read()!!.refreshToken)
    }

    @Test fun doesNotExposeServerErrorBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private-token-from-server"))
        val error = try { client.search("song"); null } catch (error: ServiceException) { error }
        assertNotNull(error)
        assertFalse(error!!.message!!.contains("private-token"))
        assertEquals(403, error.status)
    }

    @Test fun deviceLoginRejectsOffsiteVerificationAddress() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"verificationUriComplete":"https://phishing.example/login","deviceCode":"private-device-code","userCode":"USER","expiresIn":300}"""))
        try { client.beginLogin(); fail("Offsite login address was accepted") } catch (error: ServiceException) { assertEquals(502, error.status) }
    }

    @Test fun sensitiveModelsDoNotLogTheirValues() {
        assertFalse(store.read().toString().contains("access-before"))
        assertFalse(store.read().toString().contains("refresh-before"))
        assertFalse(ClientIdentity("sensitive-client", "sensitive-secret").toString().contains("sensitive"))
        assertFalse(DeviceLogin("sensitive-code", "USER", "https://link.tidal.com/USER", 123, 5).toString().contains("sensitive"))
    }

    @Test fun losslessRequestRejectsAacInsteadOfSilentlyDowngrading() = runBlocking {
        server.enqueue(MockResponse().setBody(streamResponse("mp4a.40.2")))
        try { client.stream("42", AudioQuality.LOSSLESS); fail("Lossy audio was accepted for a lossless request") }
        catch (error: ServiceException) { assertEquals(406, error.status); assertTrue(error.message.orEmpty().contains("lossless")) }
    }

    @Test fun explicitAacQualityRemainsAvailable() = runBlocking {
        server.enqueue(MockResponse().setBody(streamResponse("mp4a.40.2")))
        assertEquals("mp4a.40.2", client.stream("42", AudioQuality.HIGH).codec)
    }

    @Test fun flacIsLosslessEvenWhenItsContainerIsMp4() = runBlocking {
        server.enqueue(MockResponse().setBody(streamResponse("flac")))
        val stream = client.stream("42", AudioQuality.HI_RES_LOSSLESS)
        assertEquals("audio/mp4", stream.mimeType)
        assertEquals("flac", stream.codec)
    }

    private fun streamResponse(codec: String): String {
        val manifest = java.util.Base64.getEncoder().encodeToString("""{"mimeType":"audio/mp4","codecs":"$codec","encryptionType":"NONE","urls":["https://audio.example/track.mp4"]}""".toByteArray())
        return """{"assetPresentation":"FULL","manifestMimeType":"application/vnd.tidal.bts","manifest":"$manifest","audioQuality":"LOSSLESS"}"""
    }

    @Test fun signingOutDuringRefreshCannotRestoreTheAccount() = runBlocking {
        store.value = store.value!!.copy(expiresAt = 0)
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                return MockResponse().setBody("""{"access_token":"late-access","refresh_token":"late-refresh","expires_in":3600}""")
            }
        }
        val pending = async(Dispatchers.IO) { runCatching { client.search("song") } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        client.signOut()
        finish.countDown()
        assertTrue(pending.await().isFailure)
        assertNull(store.read())
        assertFalse(client.signedIn)
    }

    @Test fun readsEveryPageOfALargePlaylistIncludingDuplicates() = runBlocking {
        val first = (1..100).joinToString(",") { """{"item":{"id":$it,"title":"Track $it"}}""" }
        server.enqueue(MockResponse().setBody("""{"items":[$first]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"item":{"id":1,"title":"Track 1"}}]}"""))
        val tracks = client.allTracks(Release("playlist", "List", "", type = ReleaseType.PLAYLIST))
        assertEquals(101, tracks.size)
        assertEquals(tracks.first().id, tracks.last().id)
        assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
        assertEquals("100", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }

    @Test fun playlistRemovalRefusesAStaleTrackPosition() = runBlocking {
        server.enqueue(MockResponse().setHeader("ETag", "fresh-version").setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"items":[{"item":{"id":999,"title":"Different track"}}]}"""))
        try { client.removeFromPlaylist("playlist", 3, "42"); fail("Stale position was deleted") }
        catch (error: ServiceException) { assertEquals(409, error.status) }
        assertEquals(2, server.requestCount)
    }

    @Test fun playlistRemovalUsesTheVersionItChecked() = runBlocking {
        server.enqueue(MockResponse().setHeader("ETag", "checked-version").setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"items":[{"item":{"id":42,"title":"Track"}}]}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        client.removeFromPlaylist("playlist", 3, "42")
        server.takeRequest(); server.takeRequest()
        val deletion = server.takeRequest()
        assertEquals("DELETE", deletion.method)
        assertEquals("checked-version", deletion.getHeader("If-None-Match"))
        assertEquals("/v1/playlists/playlist/items/3", deletion.requestUrl!!.encodedPath)
    }

    @Test fun pkceUsesASha256ChallengeAndMinimalScopes() {
        val login = client.beginPkce()
        val url = login.authorizationUrl.toHttpUrl()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(login.verifier.toByteArray())
        assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest), url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("r_usr w_usr", url.queryParameter("scope"))
        assertFalse(login.toString().contains(login.verifier))
        assertFalse(login.toString().contains(login.state))
        assertNotEquals(login.state, client.beginPkce().state)
    }

    @Test fun loadsOwnedAndFollowedPlaylistsAcrossFiftyItemPages() = runBlocking {
        val page = (1..50).joinToString(",") { """{"playlist":{"uuid":"list-$it","title":"List $it","creator":{"id":123}}}""" }
        server.enqueue(MockResponse().setBody("""{"items":[$page]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"playlist":{"uuid":"followed","title":"Followed","creator":{"id":456}}}]}"""))
        val lists = client.allPlaylists()
        assertEquals(51, lists.size)
        assertEquals("123", lists.first().ownerId)
        assertEquals("456", lists.last().ownerId)
        val first = server.takeRequest()
        assertEquals("/v1/users/123/playlistsAndFavoritePlaylists", first.requestUrl!!.encodedPath)
        assertEquals("50", first.requestUrl!!.queryParameter("limit"))
        assertEquals("50", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }

    @Test fun missingLyricsAreAnEmptyResultRatherThanAnUnavailableTrack() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals("", client.lyrics("42"))
    }

    @Test fun pkceRejectsWrongOriginStateAndExpiredRequestsWithoutSendingCodes() = runBlocking {
        val login = client.beginPkce()
        val callbacks = listOf(
            "http://tidal.com/android/login/auth?code=private-code&state=${login.state}",
            "https://tidal.com.attacker.example/android/login/auth?code=private-code&state=${login.state}",
            "tidal://login/auth?code=private-code&state=wrong-state",
            "tidal://login/auth?error=access_denied&state=${login.state}",
            "tidal://login/auth?code=private-code&state=${login.state}&state=duplicate",
            "tidal://login/auth?code=first&code=second&state=${login.state}")
        callbacks.forEach { callback ->
            try { client.completePkce(login, callback); fail("Untrusted callback accepted") }
            catch (error: ServiceException) { assertFalse(error.message.orEmpty().contains("private-code")) }
        }
        try { client.completePkce(login.copy(expiresAt = 0), "https://tidal.com/android/login/auth?code=private-code&state=${login.state}"); fail("Expired request accepted") }
        catch (_: ServiceException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun pkceExchangesVerifierAndKeepsItsAuthenticationMethodForRefresh() = runBlocking {
        val login = client.beginPkce()
        server.enqueue(MockResponse().setBody("""{"access_token":"browser-access","refresh_token":"browser-refresh","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"userId":456,"countryCode":"IN"}"""))
        client.completePkce(login, "tidal://login/auth?code=one-time-code&state=${login.state}")
        val exchange = server.takeRequest().body.readUtf8()
        assertTrue(exchange.contains("code_verifier=${login.verifier}"))
        assertTrue(exchange.contains("redirect_uri=tidal%3A%2F%2Flogin%2Fauth"))
        assertFalse(exchange.contains("w_sub"))
        assertEquals("pkce", store.read()!!.authMethod)
        assertEquals("456", store.read()!!.userId)
    }

    @Test fun completesAnOlderPendingSignInUsingItsOriginalRedirect() = runBlocking {
        val current = client.beginPkce()
        val legacyUrl = current.authorizationUrl.toHttpUrl().newBuilder().setQueryParameter("redirect_uri", SignInCallback.LEGACY).build().toString()
        val login = current.copy(authorizationUrl = legacyUrl)
        server.enqueue(MockResponse().setBody("""{"access_token":"legacy-access","refresh_token":"legacy-refresh","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"userId":456,"countryCode":"IN"}"""))
        client.completePkce(login, "https://tidal.com/android/login/auth?code=legacy-code&state=${login.state}")
        assertTrue(server.takeRequest().body.readUtf8().contains("redirect_uri=https%3A%2F%2Ftidal.com%2Fandroid%2Flogin%2Fauth"))
        assertEquals("pkce", store.read()!!.authMethod)
    }

    private class MemoryStore(var value: Session?) : SessionStore {
        override fun read() = value
        override fun write(session: Session) { value = session }
        override fun clear() { value = null }
    }
}
