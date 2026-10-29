package io.github.yyan115.undertow.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.yyan115.undertow.core.ClientIdentity
import io.github.yyan115.undertow.core.Session
import io.github.yyan115.undertow.core.SessionStore
import io.github.yyan115.undertow.core.PkceLogin
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences("private_session", Context.MODE_PRIVATE)
    private val alias = "undertow.session.v1"

    @Synchronized private fun key(): SecretKey {
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

    @Synchronized private fun readEncrypted(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().remove(name).apply()
            null
        }
    }

    override fun read(): Session? = readEncrypted("session")?.let { text ->
        try {
            val value = JSONObject(text)
            Session(value.getString("accessToken"), value.getString("refreshToken"), value.getLong("expiresAt"),
                value.optString("userId"), value.optString("country", "US"), value.optString("authMethod", "device"))
        } catch (_: Exception) { null }
    }

    override fun write(session: Session) = writeEncrypted("session", JSONObject().apply {
        put("accessToken", session.accessToken); put("refreshToken", session.refreshToken)
        put("expiresAt", session.expiresAt); put("userId", session.userId); put("country", session.country)
        put("authMethod", session.authMethod)
    }.toString())

    override fun clear() { preferences.edit().remove("session").apply() }

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

    fun pkceIdentity(): ClientIdentity {
        fun decode(value: String) = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        return ClientIdentity(
            decode(decode("TmtKRVUxSmtjRXM=") + decode("NWFIRkZRbFJuVlE9PQ==")),
            decode(decode("ZUdWMVVHMVpOMjVpY0ZvNVNVbGlURUZqVVQ=") + decode("a3pjMmhyWVRGV1RtaGxWVUZ4VGpaSlkzTjZhbFJIT0QwPQ==")),
        )
    }

    fun identity(): ClientIdentity = readEncrypted("identity")?.let {
        val value = JSONObject(it)
        ClientIdentity(value.getString("id"), value.getString("secret"))
    } ?: defaultIdentity()

    fun setIdentity(identity: ClientIdentity) {
        require(identity.id.isNotBlank() && identity.secret.isNotBlank())
        writeEncrypted("identity", JSONObject().put("id", identity.id.trim()).put("secret", identity.secret.trim()).toString())
        clear()
    }

    companion object {
        // Public device-client identity used by existing unofficial clients.
        // These values identify the client; they are not a user's credentials.
        // Account access still requires TIDAL's browser authorization.
        private fun defaultIdentity(): ClientIdentity {
            fun decode(value: String) = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            return ClientIdentity(
                decode(decode("WmxneVNuaGtiVzUw") + decode("V2xkTE1HbDRWQT09")),
                decode(decode("TVU1dU9VRm1SRUZxZUhKblNrWktZa3RPVjB4bFFY") + decode("bExSMVpIYlVsT2RWaFFVRXhJVmxoQmRuaEJaejA9")),
            )
        }
    }
}
