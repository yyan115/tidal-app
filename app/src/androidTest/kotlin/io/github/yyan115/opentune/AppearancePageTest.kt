package io.github.yyan115.opentune

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The theme picker pages through every theme, shows each one as a preview of the real interface, and applies the chosen one to the app. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class AppearancePageTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 5_000)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(compose.activity.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun everyThemeCanBePreviewedAndApplied() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.runOnIdle { model.settings { it.copy(theme = appThemes.first().id, welcomeDone = true) } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(compose.activity.getString(R.string.settings_theme)))
        compose.onNodeWithText(compose.activity.getString(R.string.settings_theme)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("theme-pager").fetchSemanticsNodes().isNotEmpty() }
        screenshot("appearance-system")
        // The pager opens on the current theme, the first of the list, Light. The system choice comes before it and the rest follow the list.
        val names = appThemes.map { compose.activity.getString(it.nameRes) }
        compose.onAllNodesWithText(names[0]).onFirst().assertExists()
        for (index in 1..appThemes.lastIndex) {
            compose.onNodeWithTag("theme-pager").performTouchInput { swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY), 250) }
            compose.waitForIdle()
            compose.onAllNodesWithText(names[index]).onFirst().assertExists()
            if (appThemes[index].id in setOf("cyberpunk", "paper", "gold")) screenshot("appearance-${appThemes[index].id}")
        }
        val before = model.state.value.settings.theme
        compose.onNodeWithTag("theme-apply").performClick()
        compose.waitUntil(5_000) { model.state.value.settings.theme != before }
        assertEquals(appThemes.last().id, model.state.value.settings.theme)
        assertNotEquals(before, model.state.value.settings.theme)
        compose.onNodeWithTag("theme-apply").assertIsNotEnabled()
        screenshot("appearance-applied")
    }
}
