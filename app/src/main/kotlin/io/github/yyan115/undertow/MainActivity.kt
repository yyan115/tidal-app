package io.github.yyan115.undertow

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.ui.MainViewModel
import io.github.yyan115.undertow.ui.UndertowRoot
import io.github.yyan115.undertow.ui.UndertowTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private var incomingCallback by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptIntent(intent)
        enableEdgeToEdge()
        setContent {
            val model: MainViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(incomingCallback) {
                incomingCallback?.let { model.completeBrowserLogin(it); incomingCallback = null }
            }
            val dark = state.settings.theme == "dark" || (state.settings.theme == "system" && isSystemInDarkTheme())
            LaunchedEffect(dark) {
                withFrameNanos { }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            UndertowTheme(state.settings.theme) { UndertowRoot(model, state) }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptIntent(intent) }

    private fun acceptIntent(incoming: Intent) {
        val value = when (incoming.action) {
            Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> incoming.dataString
            else -> null
        }
        value?.let { text ->
            Regex("https://tidal\\.com/android/login/auth\\?[^\\s]+") .find(text)?.value?.let { incomingCallback = it }
        }
        // Authorization codes should not remain in the activity's launch intent.
        setIntent(Intent(this, MainActivity::class.java))
    }
}
