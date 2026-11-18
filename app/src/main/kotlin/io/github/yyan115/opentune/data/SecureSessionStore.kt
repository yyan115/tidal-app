package io.github.yyan115.opentune.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.yyan115.opentune.core.ClientIdentity
import io.github.yyan115.opentune.core.Session
import io.github.yyan115.opentune.core.SessionStore
import io.github.yyan115.opentune.core.PkceLogin
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the account session and other secrets encrypted with a key of the Android Keystore.
 * [keySource] replaces the key of the keystore in a test.
 */
class SecureSessionStore(context: Context, private val keySource: (() -> SecretKey)? = null) : SessionStore {
    private val preferences = context.getSharedPreferences("private_session", Context.MODE_PRIVATE)
    private val alias = "opentune.session.v1"
    private var sessionLoaded = false
    private var cachedSession: Session? = null

    @Synchronized private fun key(): SecretKey {
        keySource?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    @Synchronized private fun writeEncrypted(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val result = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(name, Base64.encodeToString(result, Base64.NO_WRAP)).commit()) {
            "The account session could not be saved."
        }
    }

    /** A stored value: its text, nothing stored, or a value that cannot be read at the moment. */
    private sealed interface Stored {
        class Value(val text: String) : Stored
        data object Missing : Stored
        data object Unavailable : Stored
    }

    /**
     * Reads and decrypts a stored value. A value that is damaged, or that was encrypted with a key that no longer exists, can never be read,
     * so it is removed. Any other failure, such as a keystore that is busy, can pass. The value then stays, and a later read tries again.
     */
    @Synchronized private fun load(name: String): Stored {
        val stored = preferences.getString(name, null) ?: return Stored.Missing
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            Stored.Value(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
        } catch (error: Exception) {
            if (!unreadableForGood(error)) return Stored.Unavailable
            preferences.edit().remove(name).apply()
            Stored.Missing
        }
    }

    private fun readEncrypted(name: String): String? = (load(name) as? Stored.Value)?.text

    @Synchronized override fun read(): Session? {
        if (!sessionLoaded) {
            val stored = load("session")
            // After a failure that can pass, the session is not known yet, so the next call reads it again.
            if (stored == Stored.Unavailable) return null
            cachedSession = (stored as? Stored.Value)?.text?.let { text ->
                try {
                    val value = JSONObject(text)
                    Session(value.getString("accessToken"), value.getString("refreshToken"), value.getLong("expiresAt"),
                        value.optString("userId"), value.optString("country", "US"))
                } catch (_: Exception) { null }
            }
            sessionLoaded = true
        }
        return cachedSession
    }

    @Synchronized override fun write(session: Session) {
        writeEncrypted("session", JSONObject().apply {
            put("accessToken", session.accessToken); put("refreshToken", session.refreshToken)
            put("expiresAt", session.expiresAt); put("userId", session.userId); put("country", session.country)
        }.toString())
        cachedSession = session; sessionLoaded = true
    }

    @Synchronized override fun clear() { preferences.edit().remove("session").apply(); cachedSession = null; sessionLoaded = true }

    fun savePkce(login: PkceLogin) = writeEncrypted("pending_pkce", JSONObject().apply {
        put("url", login.authorizationUrl); put("verifier", login.verifier); put("state", login.state)
        put("unique", login.uniqueKey); put("expiresAt", login.expiresAt)
    }.toString())

    fun pendingPkce(): PkceLogin? = readEncrypted("pending_pkce")?.let { text -> runCatching {
        val json = JSONObject(text)
        PkceLogin(json.getString("url"), json.getString("verifier"), json.getString("state"), json.getString("unique"), json.getLong("expiresAt"))
            .takeIf { it.expiresAt > System.currentTimeMillis() }
    }.getOrNull() }

    fun clearPkce() { preferences.edit().remove("pending_pkce").apply() }

    /** The ListenBrainz user token. It is stored encrypted like the TIDAL session. */
    fun scrobbleToken(): String? = readEncrypted("scrobble_token")?.takeIf(String::isNotBlank)
    fun setScrobbleToken(token: String) {
        if (token.isBlank()) preferences.edit().remove("scrobble_token").apply() else writeEncrypted("scrobble_token", token.trim())
    }

    companion object {
        /** Whether a stored value can never be read: its text is damaged, or its key is gone or no longer valid. */
        internal fun unreadableForGood(error: Exception): Boolean =
            error is IllegalArgumentException || error is AEADBadTagException || error is KeyPermanentlyInvalidatedException
    }

    fun identity(): ClientIdentity {
        fun decode(value: String) = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        return ClientIdentity(
            decode(decode("TmtKRVUxSmtjRXM=") + decode("NWFIRkZRbFJuVlE9PQ==")),
            decode(decode("ZUdWMVVHMVpOMjVpY0ZvNVNVbGlURUZqVVQ=") + decode("a3pjMmhyWVRGV1RtaGxWVUZ4VGpaSlkzTjZhbFJIT0QwPQ==")),
        )
    }
}
