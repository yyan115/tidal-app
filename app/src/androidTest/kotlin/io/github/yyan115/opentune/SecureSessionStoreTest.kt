package io.github.yyan115.opentune

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Session
import io.github.yyan115.opentune.data.SecureSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The saved session survives a keystore that fails for a moment, and a value that can never be read is removed. */
@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("private_session", Context.MODE_PRIVATE)
    private val session = Session("access", "refresh", 1_900_000_000_000, "42", "DE")
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var saved: String? = null

    // The store of the app keeps its session under the same name, so the test puts it back afterwards.
    @Before fun keepTheSessionOfTheApp() { saved = preferences.getString("session", null) }
    @After fun restoreTheSessionOfTheApp() {
        preferences.edit().apply { if (saved == null) remove("session") else putString("session", saved) }.commit()
    }

    @Test fun aSessionIsReadBackByANewStore() {
        SecureSessionStore(context) { key }.write(session)
        assertEquals(session, SecureSessionStore(context) { key }.read())
    }

    @Test fun aKeystoreThatFailsForAMomentKeepsTheSession() {
        SecureSessionStore(context) { key }.write(session)
        var failing = true
        val store = SecureSessionStore(context) { if (failing) throw ProviderException("Keystore operation failed") else key }
        assertNull("The session cannot be read while the keystore fails", store.read())
        assertNotNull("The stored session is kept", preferences.getString("session", null))
        failing = false
        assertEquals("The same store reads the session once the keystore works again", session, store.read())
    }

    @Test fun aSessionOfAKeyThatIsGoneIsRemoved() {
        SecureSessionStore(context) { key }.write(session)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(SecureSessionStore(context) { other }.read())
        assertNull("A session that can never be read is removed", preferences.getString("session", null))
    }

    @Test fun aDamagedValueIsRemoved() {
        preferences.edit().putString("session", "not base64 !!").commit()
        assertNull(SecureSessionStore(context) { key }.read())
        assertNull(preferences.getString("session", null))
    }
}
