package io.github.yyan115.opentune

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The settings of downloads name the folder and the notifications, so that both can be changed after the first sign-in. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsRowsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<*>? = null

    @After fun close() { scenario?.close() }

    private fun openSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch<android.app.Activity>(Intent().setClassName(context.packageName, "io.github.yyan115.opentune.preview.ThemePreviewActivity")
            .putExtra("page", "settings").putExtra("theme", "dark"))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theDownloadFolderIsAPlainRowThatAsksBeforeItChangesAnything() {
        openSettings()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Download folder"))
        compose.onNodeWithText("Music/OpenTune").assertIsDisplayed()
        compose.onNode(hasText("Download folder") and hasClickAction()).performClick()
        compose.onNodeWithText("Change folder?").assertIsDisplayed()
        // The title fits on one line, and a description says what changes and what stays.
        val layout = compose.onNodeWithText("Change folder?").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action
            ?.let { read -> val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); read(results); results.first() }
        org.junit.Assert.assertEquals("The title is on one line", 1, layout!!.lineCount)
        compose.onNodeWithText("New downloads are saved to the folder you pick. Existing downloads stay where they are.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNode(hasText("Change") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("Change folder?").assertCountEquals(0)
    }

    @Test fun thereIsNoRowToReturnToTheDefaultFolderAndNoNotificationsRow() {
        openSettings()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Download folder"))
        compose.onAllNodesWithText("Use default folder").assertCountEquals(0)
        compose.onAllNodesWithText("Notifications").assertCountEquals(0)
        compose.onAllNodesWithText("Download progress").assertCountEquals(0)
    }
}
