package io.github.yyan115.opentune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Contributor
import io.github.yyan115.opentune.core.Credit
import io.github.yyan115.opentune.ui.CreditsSheet
import io.github.yyan115.opentune.ui.CreditsUi
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The credits sheet has the same height while it loads, when it has nothing to show, when it fails and when it lists credits. */
@RunWith(AndroidJUnit4::class)
class CreditsSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun height() = compose.onNodeWithTag("credits-sheet").fetchSemanticsNode().boundsInRoot.height

    @Test fun theSheetKeepsItsHeightInEveryState() {
        var ui by mutableStateOf(CreditsUi("Some album", loading = true))
        compose.setContent { OpenTuneTheme("dark") { CreditsSheet(ui, {}, {}) } }
        compose.waitForIdle()
        val loading = height()
        ui = CreditsUi("Some album", loading = false); compose.waitForIdle()
        assertEquals("Without credits", loading, height(), 1f)
        compose.onNodeWithText("No credits are listed for this release").assertIsDisplayed()
        ui = CreditsUi("Some album", loading = false, error = "TIDAL could not be reached"); compose.waitForIdle()
        assertEquals("With an error", loading, height(), 1f)
        ui = CreditsUi("Some album", loading = false, credits = listOf(Credit("Producer", listOf(Contributor("Someone", "1"))))); compose.waitForIdle()
        assertEquals("With a credit", loading, height(), 1f)
        compose.onNodeWithText("Someone").assertIsDisplayed()
    }

    @Test fun theMessageIsInTheMiddleOfTheSpaceThatTheSheetKeeps() {
        compose.setContent { OpenTuneTheme("dark") { CreditsSheet(CreditsUi("Some album", loading = false), {}, {}) } }
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag("credits-sheet").fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithText("No credits are listed for this release").fetchSemanticsNode().boundsInRoot
        assertTrue("The message is below the title", message.top > sheet.top + sheet.height * 0.25f)
        assertTrue("The message is above the bottom", message.bottom < sheet.bottom)
    }

    @Test fun everyStateCanBeReadInEveryTheme() {
        var themeId by mutableStateOf(appThemes.first().id)
        var ui by mutableStateOf(CreditsUi("Some album", loading = false))
        compose.setContent { OpenTuneTheme(themeId) { CreditsSheet(ui, {}, {}) } }
        for (theme in appThemes) {
            themeId = theme.id; compose.waitForIdle()
            val problems = Readability.problems(compose, keep = "credits-${theme.id}")
            assertTrue("The credits sheet is hard to read in ${theme.id}: $problems", problems.isEmpty())
        }
    }
}
