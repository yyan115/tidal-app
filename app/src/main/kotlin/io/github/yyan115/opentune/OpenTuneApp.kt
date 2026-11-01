package io.github.yyan115.opentune

import android.app.Application
import io.github.yyan115.opentune.core.Placeholders
import io.github.yyan115.opentune.core.TidalClient
import io.github.yyan115.opentune.data.AppSettings
import io.github.yyan115.opentune.data.LibraryStore
import io.github.yyan115.opentune.data.LocalImporter
import io.github.yyan115.opentune.data.OfflineExporter
import io.github.yyan115.opentune.data.SecureSessionStore
import okhttp3.OkHttpClient
import io.github.yyan115.opentune.playback.AudioSources
import java.util.concurrent.TimeUnit

@androidx.media3.common.util.UnstableApi
class OpenTuneApp : Application() {
    val sessions by lazy { SecureSessionStore(this) }
    val settings by lazy { AppSettings(this) }
    val library by lazy { LibraryStore(this) }
    val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (settings.state.value.offline) throw java.io.IOException("Offline mode is enabled")
            chain.proceed(chain.request())
        }.build() }
    val tidal by lazy { TidalClient(sessions::identity, sessions, http, locale = ::tidalLocale) }
    val importer by lazy { LocalImporter(this, library) }
    val scrobbler by lazy { io.github.yyan115.opentune.data.Scrobbler(this) }
    val exporter by lazy { OfflineExporter(this) }
    val sources by lazy { AudioSources(this) }

    override fun onCreate() {
        super.onCreate()
        applyPlaceholders()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlaceholders()
    }

    /** The core module cannot read resources, so the app hands it the translated stand-in texts. */
    private fun applyPlaceholders() {
        Placeholders.untitled = getString(R.string.placeholder_untitled)
        Placeholders.unknownArtist = getString(R.string.placeholder_unknown_artist)
    }
}

/** The language and region for TIDAL page text, in the form that TIDAL expects. */
internal fun tidalLocale(locale: java.util.Locale = java.util.Locale.getDefault()): String =
    locale.language.ifBlank { "en" } + "_" + locale.country.ifBlank { "US" }
