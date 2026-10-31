package io.github.yyan115.opentune

import android.app.Application
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
    val tidal by lazy { TidalClient(sessions::identity, sessions, http) }
    val importer by lazy { LocalImporter(this, library) }
    val exporter by lazy { OfflineExporter(this) }
    val sources by lazy { AudioSources(this) }
}
