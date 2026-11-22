package io.github.yyan115.opentune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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

    @Test fun theHeaderStaysWhileTheCreditsScrollAndHasRoomBelowIt() {
        val credits = CreditsUi("Some album", loading = false, credits = (1..30).map { Credit("Role $it", listOf(Contributor("Person $it", "$it"))) })
        compose.setContent { OpenTuneTheme("dark") { CreditsSheet(credits, {}, {}) } }
        compose.waitForIdle()
        val albumBottom = compose.onNodeWithText("Some album").fetchSemanticsNode().boundsInRoot.bottom
        val firstRole = compose.onNodeWithText("Role 1", ignoreCase = true).fetchSemanticsNode().boundsInRoot.top
        val room = with(compose.density) { 26.dp.toPx() }
        assertTrue("The first credit starts ${firstRole - albumBottom} px below the album line, and it needs at least $room", firstRole - albumBottom >= room)
        val headerBefore = compose.onNodeWithText("Credits").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Role 30", ignoreCase = true))
        compose.waitForIdle()
        compose.onNodeWithText("Credits").assertIsDisplayed()
        assertEquals("The title does not move while the credits scroll", headerBefore, compose.onNodeWithText("Credits").fetchSemanticsNode().boundsInRoot)
        assertEquals("The album line does not move", albumBottom, compose.onNodeWithText("Some album").fetchSemanticsNode().boundsInRoot.bottom, 0.5f)
    }

    @Test fun theAlbumLineAndTheRolesAreNotSmallText() {
        val credits = io.github.yyan115.opentune.ui.CreditsUi("Night Walk · Harbour Lights", loading = false,
            credits = listOf(io.github.yyan115.opentune.core.Credit("Producer", listOf(io.github.yyan115.opentune.core.Contributor("Some Person", "1")))))
        compose.setContent { OpenTuneTheme("dark") { CreditsSheet(credits, {}, {}) } }
        compose.waitForIdle()
        fun sizeOf(text: String): Float {
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            return layouts.first().layoutInput.style.fontSize.value
        }
        assertTrue("The album line is at least 14 sp: ${sizeOf("Night Walk · Harbour Lights")}", sizeOf("Night Walk · Harbour Lights") >= 14f)
        assertTrue("The role is at least 12 sp: ${sizeOf("PRODUCER")}", sizeOf("PRODUCER") >= 12f)
    }
}
