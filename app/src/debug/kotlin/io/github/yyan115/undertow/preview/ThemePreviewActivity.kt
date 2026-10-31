package io.github.yyan115.undertow.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.core.Release
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.core.SearchResult
import io.github.yyan115.undertow.data.Settings
import io.github.yyan115.undertow.ui.*
import java.io.File

/** Reproducible visual checks of the production components; never included in a release APK. */
@UnstableApi
class ThemePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val palette = intent.getStringExtra("direction") ?: "gallery"
        val appearance = intent.getStringExtra("mode") ?: "dark"
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = appearance == "light"
            isAppearanceLightNavigationBars = appearance == "light"
        }
        val model = ViewModelProvider(this)[MainViewModel::class.java]
        val covers = File(filesDir, "theme-preview")
        fun cover(name: String) = covers.resolve("$name.jpg").takeIf(File::isFile)?.toURI()?.toString()
        val modal = cover("modal-soul"); val norah = cover("come-away")
        val tracks = listOf(
            Track("112706877", "Don't Know Why", "Norah Jones", "Come Away with Me", "112706871", norah, 186674),
            Track("163793678", "Feather", "Nujabes, Cise Starr, Akin", "Modal Soul", "163793677", modal, 175000),
            Track("preview:reflection", "reflection eternal", "Nujabes", "Modal Soul", artwork = modal, durationMs = 257000),
            Track("preview:ordinary", "Ordinary Joe", "Nujabes, Terry Callier", "Modal Soul", artwork = modal, durationMs = 309000),
        )
        val albums = listOf(Release("112706871", "Come Away with Me", "Norah Jones", norah), Release("163793677", "Modal Soul", "Nujabes", modal))
        val page = intent.getStringExtra("page")
        val state = AppState(signedIn = true, settings = Settings(theme = appearance, colorTheme = palette),
            recent = tracks.take(3), favorites = tracks, albums = albums,
            searchQuery = if (page == "search") "Norah Jones" else "", search = SearchResult(tracks = listOf(tracks[0]), albums = listOf(albums[0])),
            playback = PlaybackState(queue = tracks, index = 0, nextIndex = 1, position = 64000, duration = tracks[0].durationMs, quality = "FLAC · 24-bit · 192 kHz"),
            detail = if (page == "album") albums[0] else null, detailTracks = if (page == "album") tracks.take(1) else emptyList())
        setContent { OpenTuneTheme(appearance, palette) {
            if (page == "icons") IconSheet() else CompositionLocalProvider(LocalOfflineMode provides true) {
                OpenTuneRoot(model, state, initialTab = when (page) { "search" -> 1; "library" -> 2; "downloads" -> 3; else -> 0 },
                    initialPlayer = page == "player", initialSettings = page == "settings")
            }
        } }
    }
}
