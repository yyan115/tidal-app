package io.github.yyan115.opentune

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.AppDialog
import io.github.yyan115.opentune.ui.DialogConfirm
import io.github.yyan115.opentune.ui.DialogDismiss
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dialog of the app shows its title, its text and its actions in every theme, and the actions work. */
@RunWith(AndroidJUnit4::class)
class AppDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyThemeShowsTheDialogWithBothActionsAndTheyWork() {
        var themeId by mutableStateOf(appThemes.first().id)
        var deleting by mutableStateOf(false)
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            OpenTuneTheme(themeId) {
                AppDialog(onDismissRequest = { dismissed++ }, title = { Text("Delete playlist") }, text = { Text("The playlist is removed. The tracks stay in the library.") },
                    confirmButton = { DialogConfirm(onClick = { confirmed++ }, destructive = deleting) { Text("Delete") } },
                    dismissButton = { DialogDismiss(onClick = { dismissed++ }) { Text("Cancel") } })
            }
        }
        var rounds = 0
        for (theme in appThemes) for (destructive in listOf(false, true)) {
            themeId = theme.id; deleting = destructive; compose.waitForIdle()
            compose.onNodeWithTag("app-dialog").assertIsDisplayed()
            compose.onNodeWithText("Delete playlist").assertIsDisplayed()
            compose.onNodeWithText("The playlist is removed. The tracks stay in the library.").assertIsDisplayed()
            compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
            compose.onNode(hasText("Delete") and hasClickAction()).performClick()
            rounds++
            assertEquals("Cancel acted ${theme.id}", rounds, dismissed)
            assertEquals("Delete acted ${theme.id}", rounds, confirmed)
        }
    }

    @Test fun aLongTextScrollsAndTheActionsStayOnTheScreen() {
        compose.setContent {
            OpenTuneTheme("dark") {
                AppDialog(onDismissRequest = {}, title = { Text("Long text") },
                    text = { Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { repeat(120) { Text("Line $it of a text that is taller than the screen") } } },
                    confirmButton = { DialogConfirm(onClick = {}) { Text("Save") } }, dismissButton = { DialogDismiss(onClick = {}) { Text("Cancel") } })
            }
        }
        compose.onNode(hasText("Save") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun aDialogWithoutActionsShowsNoEmptySpaceForThem() {
        compose.setContent { OpenTuneTheme("dark") { AppDialog(onDismissRequest = {}, title = { Text("Audio quality") }, text = { Text("Pick one") }) } }
        compose.onNodeWithText("Pick one").assertIsDisplayed()
    }

    private fun styleOf(text: String) = run {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        results.first().layoutInput.style
    }

    @Test fun theTitleIsLargerAndBolderThanTheTextInEveryTheme() {
        var themeId by mutableStateOf(appThemes.first().id)
        compose.setContent {
            OpenTuneTheme(themeId) {
                AppDialog(onDismissRequest = {}, title = { Text("Download played tracks") }, text = { Text("Every track that plays is downloaded.") },
                    confirmButton = { DialogConfirm(onClick = {}) { Text("Enable") } }, dismissButton = { DialogDismiss(onClick = {}) { Text("Cancel") } })
            }
        }
        for (theme in appThemes) {
            themeId = theme.id; compose.waitForIdle()
            val title = styleOf("Download played tracks"); val body = styleOf("Every track that plays is downloaded.")
            assertEquals("The title of ${theme.id} has the size of a Material headline", 24.sp, title.fontSize)
            assertTrue("The title of ${theme.id} is at least 1.5 times the size of the text", title.fontSize.value >= body.fontSize.value * 1.5f)
            assertTrue("The title of ${theme.id} is not lighter than the text", (title.fontWeight ?: FontWeight.Normal).weight >= (body.fontWeight ?: FontWeight.Normal).weight)
        }
    }

    @Test fun theSecondActionHasAnOutlineOnlyNextToAMainAction() {
        var both by mutableStateOf(true)
        compose.setContent {
            OpenTuneTheme("dark") {
                AppDialog(onDismissRequest = {}, title = { Text("Title") }, text = { Text("Text") },
                    confirmButton = if (both) ({ DialogConfirm(onClick = {}) { Text("Enable") } }) else null, dismissButton = { DialogDismiss(onClick = {}) { Text("Cancel") } })
            }
        }
        compose.onNodeWithTag("dialog-outlined-action").assertIsDisplayed()
        both = false; compose.waitForIdle()
        compose.onNodeWithTag("dialog-outlined-action").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
