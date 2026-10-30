package io.github.yyan115.undertow.core

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl

object SignInCallback {
    const val NATIVE = "tidal://login/auth"
    const val LEGACY = "https://tidal.com/android/login/auth"

    fun matches(address: String, expected: String): Boolean {
        if (expected != NATIVE && expected != LEGACY) return false
        return try {
            val actual = URI(address.trim())
            val target = URI(expected)
            fun port(uri: URI) = if (uri.scheme == "https" && uri.port == -1) 443 else uri.port
            actual.scheme == target.scheme && actual.host == target.host && actual.rawPath == target.rawPath &&
                port(actual) == port(target) && actual.rawUserInfo == null && actual.rawFragment == null
        } catch (_: Exception) { false }
    }

    fun supported(address: String) = matches(address, NATIVE) || matches(address, LEGACY)

    fun parameters(address: String): Map<String, List<String>> {
        val query = URI(address.trim()).rawQuery.orEmpty()
        val parsed = ("https://callback.invalid/?$query").toHttpUrl()
        return parsed.queryParameterNames.associateWith { parsed.queryParameterValues(it).map { value -> value.orEmpty() } }
    }
}
