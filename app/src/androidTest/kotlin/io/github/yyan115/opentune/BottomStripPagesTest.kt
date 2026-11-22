package io.github.yyan115.opentune

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** With the three-button navigation, the strip behind the buttons has the colour of the page above it, on every page and in every theme. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class BottomStripPagesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var before = ""

    @Before fun threeButtons() { before = BottomStrip.useThreeButtons(); Thread.sleep(1500) }
    @After fun restore() { BottomStrip.restore(before) }

    private fun problemOf(page: String, theme: String): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var height = 0
        ActivityScenario.launch<android.app.Activity>(Intent().setClassName(context.packageName, "io.github.yyan115.opentune.preview.ThemePreviewActivity")
            .putExtra("page", page).putExtra("theme", theme)).use { scenario ->
            compose.waitForIdle(); Thread.sleep(1200); compose.waitForIdle()
            scenario.onActivity { activity ->
                height = ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val density = context.resources.displayMetrics.density
            // The three buttons need a bar of 48 dp. A smaller inset means that the phone is not in the three-button mode and the test would prove nothing.
            assertTrue("The three-button navigation is not on: the inset is ${height / density} dp", height >= 40 * density)
            return try { BottomStrip.problem(bitmap, height) } finally { bitmap.recycle() }
        }
    }

    @Test fun everyPageHasNoBandBehindTheButtons() {
        val failures = mutableListOf<String>()
        for (night in listOf("yes", "no")) {
            BottomStrip.shell("cmd uimode night $night"); Thread.sleep(1500)
            for (theme in io.github.yyan115.opentune.ui.appThemes.map { it.id }) for (page in listOf("home", "player", "settings")) {
                problemOf(page, theme)?.let { failures += "$page in $theme with the phone at night=$night: $it" }
            }
        }
        BottomStrip.shell("cmd uimode night auto")
        assertTrue("A band shows behind the buttons:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
