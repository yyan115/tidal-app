package io.github.yyan115.opentune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.AppVersion
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The order of the Settings list, the custom time of Avoid repeats and the version line. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsPagesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val hours get() = model.state.value.settings.avoidRepeatHours

    @Before fun openSettings() {
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, avoidRepeatHours = 0) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun reset() { compose.runOnIdle { model.settings { it.copy(avoidRepeatHours = 0) } } }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
    private fun scrollTo(text: String) = compose.onNodeWithTag("settings-list").performScrollToNode(hasText(text))

    @Test fun avoidRepeatsIsAnExperimentalFeatureAndNoSectionHoldsOnlyOneRow() {
        compose.onAllNodesWithText("Playback").assertCountEquals(0)
        scrollTo("Experimental")
        compose.onNodeWithText("Features that are still in development and may change or not work correctly").assertIsDisplayed()
        assertTrue(top("Experimental") < top("Avoid repeats"))
        assertTrue(top("Avoid repeats") < top("Equalizer"))
        scrollTo("Data")
        compose.onAllNodesWithText("Listening").assertCountEquals(0)
        assertTrue("Listening statistics sits in Data", top("Data") < top("Listening statistics"))
        assertTrue(top("Listening statistics") < top("Back up data"))
    }

    @Test fun theVersionLineSaysPrereleaseUntilTheFirstFullRelease() {
        scrollTo("Version")
        val shown = if (AppVersion.isPrerelease(BuildConfig.VERSION_NAME)) "${BuildConfig.VERSION_NAME} (prerelease)" else BuildConfig.VERSION_NAME
        compose.onNodeWithText(shown).assertIsDisplayed()
    }

    private fun openAvoidRepeats() {
        scrollTo("Avoid repeats")
        compose.onNode(hasText("Avoid repeats") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("repeats-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aCustomTimeIsEnteredInHoursOrDaysAndOnlyAValidNumberApplies() {
        openAvoidRepeats()
        compose.onNodeWithText("Custom").performClick()
        compose.waitUntil(5_000) { hours == 48 }
        compose.onNodeWithTag("repeats-custom").assertTextContains("2")
        compose.onNodeWithTag("repeats-custom").performTextReplacement("3")
        compose.waitUntil(5_000) { hours == 72 }
        compose.onNodeWithText("Hours").performClick()
        compose.onNodeWithTag("repeats-custom").assertTextContains("72")
        assertEquals("Changing the unit keeps the span", 72, hours)
        compose.onNodeWithTag("repeats-custom").performTextReplacement("9999")
        compose.waitForIdle()
        assertEquals("A number above 365 days leaves the window as it was", 72, hours)
        compose.onNodeWithTag("repeats-custom").performTextReplacement("0")
        compose.waitForIdle()
        assertEquals("Zero leaves the window as it was", 72, hours)
        compose.onNodeWithTag("repeats-custom").performTextReplacement("36")
        compose.waitUntil(5_000) { hours == 36 }
        compose.onNodeWithText("Days").performClick()
        compose.waitUntil(5_000) { hours == 48 }
        compose.onNodeWithTag("repeats-custom").assertTextContains("2")
    }

    @Test fun aCustomTimeOpensAsCustomAndAPresetOpensAsItself() {
        compose.runOnIdle { model.settings { it.copy(avoidRepeatHours = 36) } }
        openAvoidRepeats()
        compose.onNodeWithTag("repeats-custom").assertTextContains("36")
        compose.onNodeWithText("1 day").performClick()
        compose.waitUntil(5_000) { hours == 24 }
        compose.onAllNodesWithTag("repeats-custom").assertCountEquals(0)
    }
}
