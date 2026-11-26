package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings list of a listener with an account has a switch that hides AI-generated music, in the TIDAL section. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class HideAiMusicSettingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theSwitchSitsInTheTidalSectionAndChangesTheSetting() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.setContent { OpenTuneTheme("dark") {
            val state = AppState(signedIn = true, settings = io.github.yyan115.opentune.data.Settings(welcomeDone = true, hideAiMusic = false))
            SettingsScreen(state, model, {}, {})
        } }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Hide AI-generated music"))
        compose.onNodeWithText("In search, on pages and in radio").assertExists()
        compose.onNode(hasText("Hide AI-generated music") and hasClickAction()).assertIsDisplayed()
        // The switch is part of the TIDAL section, so it comes after the section title and before the Appearance section.
        val tidal = compose.onNodeWithText("TIDAL").fetchSemanticsNode().boundsInRoot.top
        val row = compose.onNodeWithText("Hide AI-generated music").fetchSemanticsNode().boundsInRoot.top
        val appearance = compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        assertTrue("The row is below the TIDAL title", row > tidal)
        if (appearance != null) assertTrue("The row is above Appearance", row < appearance)
    }

    @Test fun signedOutListenersDoNotSeeTheSwitch() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.setContent { OpenTuneTheme("dark") {
            SettingsScreen(AppState(signedIn = false, settings = io.github.yyan115.opentune.data.Settings(welcomeDone = true)), model, {}, {})
        } }
        compose.onAllNodesWithText("Hide AI-generated music").assertCountEquals(0)
    }
}
