package io.github.yyan115.opentune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Every text of the main screens can be read, in every theme and on a small and a large screen. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
abstract class ReadabilityTest(private val small: Boolean) {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(if (small) SmallScreen() else SmallScreen("1080x2400", "420")).around(compose)
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Before fun setUp() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(welcomeDone = true, offline = false) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun check(screen: String, problems: List<String>) = assertTrue("$screen is hard to read:\n" + problems.joinToString("\n"), problems.isEmpty())

    private fun openSettings() { compose.onNodeWithContentDescription("Settings").performClick(); compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() } }

    @Test fun theMainScreensCanBeReadInEveryTheme() {
        for (theme in appThemes) {
            compose.runOnIdle { model.settings { it.copy(theme = theme.id) } }
            compose.waitForIdle()
            check("On device in ${theme.id}", Readability.problems(compose))
            openSettings()
            compose.waitForIdle()
            check("Settings in ${theme.id}", Readability.problems(compose))
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun theSettingsPagesCanBeReadInTheLightAndTheDarkTheme() {
        for (theme in listOf("dark", "light")) {
            compose.runOnIdle { model.settings { it.copy(theme = theme) } }
            openSettings()
            for (row in listOf("Avoid repeats", "Listening statistics", "Licenses")) {
                compose.onNodeWithTag("settings-list").performScrollToNode(hasText(row))
                compose.onNode(hasText(row) and hasClickAction()).performClick()
                compose.waitForIdle()
                check("$row in $theme", Readability.problems(compose))
                compose.onNodeWithContentDescription("Back").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}

@UnstableApi class ReadabilityOnALargeScreenTest : ReadabilityTest(small = false)
@UnstableApi class ReadabilityOnASmallScreenTest : ReadabilityTest(small = true)
