package io.github.yyan115.opentune

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.SetupScreen
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The permissions page that follows the first sign-in. */
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule val compose = createComposeRule()
    private var continued = 0

    private fun show(folder: String = "", themeId: String = "dark", fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OpenTuneTheme(themeId) { SetupScreen(folder, onFolderPicked = { }, onContinue = { continued++ }) }
            }
        }
    }

    @Test fun thePageNamesWhatItAsksFor() {
        show()
        compose.onNodeWithText("Permissions").assertIsDisplayed()
        compose.onNodeWithText("OpenTune requires the following permissions.").assertIsDisplayed()
        compose.onNodeWithText("Required for downloads").assertIsDisplayed()
        compose.onNodeWithText("Download folder").assertIsDisplayed()
        compose.onNodeWithText("Saved in Music/OpenTune unless you choose another folder").assertIsDisplayed()
        compose.onNodeWithTag("setup-folder").assertIsDisplayed()
    }

    @Test fun notificationsAreOptionalAndAskedForFromAndroid13() {
        show()
        if (Build.VERSION.SDK_INT >= 33) {
            compose.onNodeWithText("Optional").assertExists()
            compose.onNodeWithText("Notifications").assertExists()
            compose.onNodeWithText("Download progress").assertExists()
        } else {
            compose.onAllNodesWithText("Optional").assertCountEquals(0)
            compose.onAllNodesWithText("Notifications").assertCountEquals(0)
        }
    }

    @Test fun aChosenFolderReplacesTheDefaultText() {
        show(folder = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FLossless")
        compose.onNodeWithText("Music/Lossless").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Saved in Music/OpenTune unless you choose another folder").assertCountEquals(0)
    }

    @Test fun continuingNeedsNoChoice() {
        show()
        compose.onNodeWithTag("setup-continue").performScrollTo().performClick()
        assertEquals(1, continued)
    }

    @Test fun thePageCanBeReadInEveryThemeAndAtTheLargestTextSize() {
        val theme = mutableStateOf(appThemes.first().id)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                OpenTuneTheme(theme.value) { SetupScreen("", onFolderPicked = { }, onContinue = { }) }
            }
        }
        for (item in appThemes) {
            theme.value = item.id; compose.waitForIdle()
            val problems = Readability.problems(compose)
            assertTrue("The permissions page is hard to read in ${item.id}: $problems", problems.isEmpty())
        }
    }
}
