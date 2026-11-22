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

/** An arrow after an action means that it opens a page. "View all" opens one, and "Clear", "Shuffle" and "New playlist" do not. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SectionActionsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<*>? = null

    @After fun close() { scenario?.close() }

    private fun open(page: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch<android.app.Activity>(Intent().setClassName(context.packageName, "io.github.yyan115.opentune.preview.ThemePreviewActivity")
            .putExtra("page", page).putExtra("theme", "dark"))
        compose.waitForIdle()
    }

    @Test fun viewAllKeepsItsArrow() {
        open("home")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("View all").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasTestTag("section-arrow"), useUnmergedTree = true).assertCountEquals(compose.onAllNodesWithText("View all").fetchSemanticsNodes().size)
    }

    @Test fun newPlaylistAndShuffleHaveNoArrow() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        val songs = (1..2).map { io.github.yyan115.opentune.core.Track("local:arrow-$it", "Arrow Song $it", "Arrow test", "Local", durationMs = 30_000, localUri = "content://arrow/$it") }
        songs.forEach(app.library::save)
        app.settings.update { it.copy(welcomeDone = true, offline = false) }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("New playlist").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Shuffle").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasTestTag("section-arrow"), useUnmergedTree = true).assertCountEquals(0)
        } finally { songs.forEach { app.library.removeLocal(it.id) } }
    }
}
