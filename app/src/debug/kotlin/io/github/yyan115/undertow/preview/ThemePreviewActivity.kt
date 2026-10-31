package io.github.yyan115.undertow.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.core.Release
import io.github.yyan115.undertow.core.Track
import io.github.yyan115.undertow.data.Settings
import io.github.yyan115.undertow.ui.AppState
import io.github.yyan115.undertow.ui.LocalOfflineMode
import io.github.yyan115.undertow.ui.MainViewModel
import io.github.yyan115.undertow.ui.PlaybackState
import io.github.yyan115.undertow.ui.UndertowRoot
import java.io.File

/** Renders the actual app components with fixed sample content for visual comparison. */
@UnstableApi
class ThemePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val direction = themeDirections.firstOrNull { it.id == intent.getStringExtra("direction") } ?: themeDirections.first()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = direction.light
            isAppearanceLightNavigationBars = direction.light
        }
        val model = ViewModelProvider(this)[MainViewModel::class.java]
        val covers = File(filesDir, "theme-preview").apply { mkdirs() }
        fun cover(name: String): String? = covers.resolve("$name.jpg").takeIf(File::isFile)?.toURI()?.toString()
        val modal = cover("modal-soul")
        val norah = cover("come-away")
        val tracks = listOf(
            Track("preview:feather", "Feather", "Nujabes, Cise Starr, Akin", "Modal Soul", artwork = modal, durationMs = 175000),
            Track("preview:dont-know-why", "Don't Know Why", "Norah Jones", "Come Away with Me", artwork = norah, durationMs = 186674),
            Track("preview:reflection", "reflection eternal", "Nujabes", "Modal Soul", artwork = modal, durationMs = 257000),
            Track("preview:ordinary-joe", "Ordinary Joe", "Nujabes, Terry Callier", "Modal Soul", artwork = modal, durationMs = 309000),
        )
        val premium = intent.getStringExtra("direction") in listOf("gallery", "archive", "reference")
        val queue = if (premium) listOf(tracks[1], tracks[0]) + tracks.drop(2) else tracks
        val state = AppState(signedIn = true,
            settings = Settings(theme = if (direction.light) "light" else "dark"),
            recent = tracks.take(3), favorites = tracks,
            albums = listOf(Release("preview:modal", "Modal Soul", "Nujabes", modal), Release("preview:come-away", "Come Away with Me", "Norah Jones", norah)),
            playback = PlaybackState(queue = queue, index = 0, position = 64000, duration = queue.first().durationMs,
                quality = if (premium) "FLAC · 24-bit · 192 kHz" else "FLAC · 16-bit · 44.1 kHz"))
        setContent {
            val concept = intent.getStringExtra("direction").orEmpty()
            if (concept in listOf("gallery", "archive", "reference")) {
                CompositionLocalProvider(LocalOfflineMode provides true) {
                    PremiumConcepts(state, concept, intent.getStringExtra("page")) { next -> model.settings { next } }
                }
            } else MaterialTheme(colorScheme = direction.colors, typography = direction.typography, shapes = direction.shapes) {
                CompositionLocalProvider(LocalOfflineMode provides true) { UndertowRoot(model, state) }
            }
        }
    }
}
