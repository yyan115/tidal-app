package io.github.yyan115.opentune

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every text of the screens that the preview shows with sample data can be read, in every theme. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PreviewReadabilityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun check(page: String, lyrics: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (theme in appThemes) {
            val scenario = ActivityScenario.launch<android.app.Activity>(Intent().setClassName(context.packageName, "io.github.yyan115.opentune.preview.ThemePreviewActivity")
                .putExtra("page", page).putExtra("theme", theme.id).putExtra("lyrics", lyrics))
            try {
                compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
                val problems = Readability.problems(compose)
                assertTrue("The $page page${if (lyrics) " with lyrics" else ""} is hard to read in ${theme.id}:\n" + problems.joinToString("\n"), problems.isEmpty())
            } finally { scenario.close() }
        }
    }

    @Test fun home() = check("home")
    @Test fun search() = check("search")
    @Test fun library() = check("library")
    @Test fun album() = check("album")
    @Test fun device() = check("device")
    @Test fun settings() = check("settings")
    @Test fun appearance() = check("appearance")
    @Test fun player() = check("player")
    @Test fun playerWithLyrics() = check("player", lyrics = true)
}
