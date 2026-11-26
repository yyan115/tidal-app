package io.github.yyan115.opentune

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The sign-in through the browser, from the address that the app opens to the library on the screen, against the fake TIDAL, which can also refuse. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class BrowserSignInTest {
    private val fake = FakeTidal(signedIn = false)
    private val screen = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fake).around(screen)

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val model get() = ViewModelProvider(screen.activity)[MainViewModel::class.java]

    @Before fun prepare() {
        screen.runOnIdle { model.settings { it.copy(offline = false, welcomeDone = true, theme = "dark") } }
        app.sessions.clearPkce()
    }

    @After fun clean() {
        app.sessions.clearPkce()
        app.library.clearFavorites(app.library.favorites().map(Track::id))
        app.library.clearCache(fake.userId)
        screen.runOnIdle { model.settings { it.copy(setupPending = false) } }
    }

    @Test fun theListenerIsSignedOutAtFirstAndTheLoginAddressAsksForTheRightThings() {
        assertFalse(model.state.value.signedIn)
        val login = screen.runOnIdle { model.beginBrowserLogin() }
        val url = login.authorizationUrl.toHttpUrl()
        assertEquals("login.tidal.com", url.host)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("tidal://login/auth", url.queryParameter("redirect_uri"))
        assertEquals(login.state, url.queryParameter("state"))
        assertNotNull("The app keeps the sign-in until the browser answers", app.sessions.pendingPkce())
    }

    @Test fun anAnswerOfTheBrowserSignsTheListenerInAndLoadsTheLibrary() {
        fake.tracks.addAll(listOf(11, 12, 13))
        val login = screen.runOnIdle { model.beginBrowserLogin() }
        screen.runOnIdle { model.completeBrowserLogin("tidal://login/auth?code=abc123&state=${login.state}") }
        screen.waitUntil(30_000) { model.state.value.signedIn && model.state.value.favorites.size == 3 }
        val exchange = fake.forms.single { it.first == "/oauth/token" }.second
        assertTrue("The code goes to TIDAL: $exchange", exchange.contains("code=abc123") && exchange.contains("grant_type=authorization_code"))
        assertTrue("The proof of the first step goes with it", exchange.contains("code_verifier=") && !exchange.contains("code_verifier=&"))
        assertNull("The sign-in is no longer pending", app.sessions.pendingPkce())
        assertTrue("The library of the account was asked for", fake.log.any { it.contains("users/${fake.userId}/favorites/tracks") })
        assertTrue("The permissions page follows the first sign-in", model.state.value.settings.setupPending || model.state.value.settings.setupShown)
    }

    @Test fun anAnswerOfAnotherSignInIsRefusedAndTheListenerStaysSignedOut() {
        screen.runOnIdle { model.beginBrowserLogin() }
        screen.runOnIdle { model.completeBrowserLogin("tidal://login/auth?code=abc123&state=somebody-elses") }
        screen.waitUntil(30_000) { model.state.value.error != null }
        assertFalse(model.state.value.signedIn)
        assertTrue("TIDAL was not asked for a token", fake.forms.none { it.first == "/oauth/token" })
    }

    @Test fun aListenerWhoDeclinedOnTheTidalPageStaysSignedOut() {
        val login = screen.runOnIdle { model.beginBrowserLogin() }
        screen.runOnIdle { model.completeBrowserLogin("tidal://login/auth?error=access_denied&state=${login.state}") }
        screen.waitUntil(30_000) { model.state.value.error != null }
        assertFalse(model.state.value.signedIn)
        assertTrue(fake.forms.none { it.first == "/oauth/token" })
    }

    @Test fun aCodeThatTidalRefusesLeavesTheListenerSignedOutWithAMessage() {
        fake.tokenError = "invalid_grant"
        val login = screen.runOnIdle { model.beginBrowserLogin() }
        screen.runOnIdle { model.completeBrowserLogin("tidal://login/auth?code=old&state=${login.state}") }
        screen.waitUntil(30_000) { model.state.value.error != null && !model.state.value.signingIn }
        assertFalse(model.state.value.signedIn)
        assertFalse(app.tidal.signedIn)
    }

    @Test fun anAnswerWithoutASignInThatWasStartedSaysThatTheLoginExpired() {
        screen.runOnIdle { model.completeBrowserLogin("tidal://login/auth?code=abc&state=x") }
        screen.waitUntil(30_000) { model.state.value.error != null }
        assertFalse(model.state.value.signedIn)
    }

    @Test fun cancellingTheSignInForgetsIt() {
        screen.runOnIdle { model.beginBrowserLogin() }
        screen.runOnIdle { model.cancelLogin() }
        assertNull(app.sessions.pendingPkce())
        assertNull(model.state.value.pkceLogin)
    }
}
