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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.undertow.ui.MainViewModel
import io.github.yyan115.undertow.ui.UndertowRoot
import io.github.yyan115.undertow.ui.UndertowTheme
import io.github.yyan115.undertow.ui.LocalOfflineMode
import io.github.yyan115.undertow.core.SignInCallback

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
            UndertowTheme(state.settings.theme) { CompositionLocalProvider(LocalOfflineMode provides state.settings.offline) { UndertowRoot(model, state) } }
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
            val callback = if (SignInCallback.supported(text)) text else
                Regex("(?:https://tidal\\.com/android/login/auth|tidal://login/auth)\\?[^\\s]+") .find(text)?.value
            callback?.takeIf(SignInCallback::supported)?.let { incomingCallback = it }
        }
        // Keep the launch action intact; lifecycle observers use it to identify this activity.
        // Remove only callback data so one-time codes do not remain in its intent.
        if (incoming.action == Intent.ACTION_SEND) incoming.removeExtra(Intent.EXTRA_TEXT)
        if (incoming.action == Intent.ACTION_VIEW) incoming.data = null
        setIntent(incoming)
    }
}
