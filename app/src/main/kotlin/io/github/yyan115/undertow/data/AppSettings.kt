package io.github.yyan115.undertow.data

import android.content.Context
import io.github.yyan115.undertow.core.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val quality: AudioQuality = AudioQuality.LOSSLESS,
    val mobileQuality: AudioQuality = AudioQuality.LOSSLESS,
    val downloadOnWifi: Boolean = true,
    val offline: Boolean = false,
    val theme: String = "system",
    val colorTheme: String = "studio",
)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun quality(key: String, fallback: AudioQuality) = runCatching {
        AudioQuality.valueOf(preferences.getString(key, fallback.name)!!)
    }.getOrDefault(fallback)
    private val mutable = MutableStateFlow(Settings(
        quality("quality", AudioQuality.LOSSLESS), quality("mobileQuality", AudioQuality.LOSSLESS),
        preferences.getBoolean("downloadOnWifi", true), preferences.getBoolean("offline", false),
        preferences.getString("theme", "system") ?: "system",
        preferences.getString("colorTheme", "studio") ?: "studio"))
    val state = mutable.asStateFlow()
    fun update(transform: (Settings) -> Settings) {
        val next = transform(mutable.value)
        preferences.edit().putString("quality", next.quality.name).putString("mobileQuality", next.mobileQuality.name)
            .putBoolean("downloadOnWifi", next.downloadOnWifi).putBoolean("offline", next.offline)
            .putString("theme", next.theme).putString("colorTheme", next.colorTheme).apply()
        mutable.value = next
    }
}
