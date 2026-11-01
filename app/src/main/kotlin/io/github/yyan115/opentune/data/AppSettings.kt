package io.github.yyan115.opentune.data

import android.content.Context
import io.github.yyan115.opentune.core.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val quality: AudioQuality = AudioQuality.LOSSLESS,
    val mobileQuality: AudioQuality = AudioQuality.LOSSLESS,
    val downloadOnWifi: Boolean = true,
    val offline: Boolean = false,
    val theme: String = "dark",
    val colorTheme: String = "gallery",
    /** Volume normalization from ReplayGain data: "off", "track" or "album". */
    val normalization: String = "off",
    val equalizer: Boolean = false,
    /** Band levels in millibels, in band order. Empty until the listener changes a band. */
    val equalizerLevels: List<Int> = emptyList(),
    val bitPerfect: Boolean = false,
    val keepFavoritesOffline: Boolean = false,
    val scrobble: Boolean = false,
    val scrobbleServer: String = "https://api.listenbrainz.org",
)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun quality(key: String, fallback: AudioQuality) = runCatching {
        AudioQuality.valueOf(preferences.getString(key, fallback.name)!!)
    }.getOrDefault(fallback)
    private val mutable = MutableStateFlow(Settings(
        quality("quality", AudioQuality.LOSSLESS), quality("mobileQuality", AudioQuality.LOSSLESS),
        preferences.getBoolean("downloadOnWifi", true), preferences.getBoolean("offline", false),
        preferences.getString("theme", "dark") ?: "dark",
        preferences.getString("colorTheme", "gallery") ?: "gallery",
        preferences.getString("normalization", "off") ?: "off", preferences.getBoolean("equalizer", false),
        preferences.getString("equalizerLevels", "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
        preferences.getBoolean("bitPerfect", false), preferences.getBoolean("keepFavoritesOffline", false),
        preferences.getBoolean("scrobble", false), preferences.getString("scrobbleServer", "https://api.listenbrainz.org") ?: "https://api.listenbrainz.org"))
    val state = mutable.asStateFlow()
    fun update(transform: (Settings) -> Settings) {
        val next = transform(mutable.value)
        preferences.edit().putString("quality", next.quality.name).putString("mobileQuality", next.mobileQuality.name)
            .putBoolean("downloadOnWifi", next.downloadOnWifi).putBoolean("offline", next.offline)
            .putString("theme", next.theme).putString("colorTheme", next.colorTheme)
            .putString("normalization", next.normalization).putBoolean("equalizer", next.equalizer)
            .putString("equalizerLevels", next.equalizerLevels.joinToString(",")).putBoolean("bitPerfect", next.bitPerfect)
            .putBoolean("keepFavoritesOffline", next.keepFavoritesOffline).putBoolean("scrobble", next.scrobble)
            .putString("scrobbleServer", next.scrobbleServer).apply()
        mutable.value = next
    }
}
