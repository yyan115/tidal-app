package io.github.yyan115.opentune

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the screen does not cover has the colour of the theme in every theme, and the system draws no veil of its own behind the three buttons. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class WindowBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Test fun theWindowBehindTheAppFollowsTheTheme() {
        compose.runOnIdle { model.settings { it.copy(welcomeDone = true) } }
        for (theme in appThemes) {
            compose.runOnIdle { model.settings { it.copy(theme = theme.id) } }
            compose.waitUntil(5_000) { (compose.activity.window.decorView.background as? ColorDrawable)?.color == theme.scheme.background.toArgb() }
            assertEquals("The window in ${theme.id}", theme.scheme.background.toArgb(), (compose.activity.window.decorView.background as ColorDrawable).color)
        }
    }

    @Test fun theSystemDrawsNoVeilBehindTheButtons() {
        compose.runOnIdle {
            if (android.os.Build.VERSION.SDK_INT >= 29) assertEquals(false, compose.activity.window.isNavigationBarContrastEnforced)
            @Suppress("DEPRECATION") assertEquals(android.graphics.Color.TRANSPARENT, compose.activity.window.navigationBarColor)
        }
    }
}
