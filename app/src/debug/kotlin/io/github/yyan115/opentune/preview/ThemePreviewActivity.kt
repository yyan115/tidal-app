package io.github.yyan115.opentune.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.core.SearchResult
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.ui.*
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
        val lateHours = cover("late-hours"); val saltAndGlass = cover("salt-and-glass")
        val tracks = listOf(
            Track("preview:night-walk", "Night Walk", "Harbour Lights", "Late Hours", "preview:late-hours", lateHours, 186674),
            Track("preview:glass-rooms", "Glass Rooms", "Harbour Lights", "Late Hours", "preview:late-hours", lateHours, 211000),
            Track("preview:slow-tide", "Slow Tide", "Maren Vale", "Salt and Glass", "preview:salt-and-glass", saltAndGlass, 175000),
            Track("preview:paper-sun", "Paper Sun", "Maren Vale, Oskar Lind", "Salt and Glass", "preview:salt-and-glass", saltAndGlass, 257000),
        )
        val albums = listOf(Release("preview:late-hours", "Late Hours", "Harbour Lights", lateHours),
            Release("preview:salt-and-glass", "Salt and Glass", "Maren Vale", saltAndGlass))
        val page = intent.getStringExtra("page")
        val state = AppState(signedIn = true, settings = Settings(theme = appearance, colorTheme = palette),
            recent = tracks.take(3), favorites = tracks, albums = albums,
            searchQuery = if (page == "search") "Harbour Lights" else "", search = SearchResult(tracks = listOf(tracks[0]), albums = listOf(albums[0])),
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
