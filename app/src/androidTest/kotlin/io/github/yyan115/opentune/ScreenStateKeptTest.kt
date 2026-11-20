package io.github.yyan115.opentune

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A screen keeps everything it remembers, such as how far a list is scrolled, while it is on the stack of screens.
 * It keeps it when Settings, a page of Settings or another page opens over it, and it starts again from the top when it was closed.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ScreenStateKeptTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SmallScreen()).around(compose)
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private var playlistId: String? = null

    @Before fun setUp() {
        compose.runOnIdle { model.clearQueue(); model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, avoidRepeatHours = 0) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("device-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun clean() { playlistId?.let(app.library::deletePlaylist) }

    private fun scrollOf(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** Where a row is on the screen. The same place before and after means that the list is scrolled to the same point. */
    private fun topOf(text: String): Float = compose.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot.top

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun openSettings() { compose.onNodeWithContentDescription("Settings").performClick(); waitFor("settings-list") }

    private fun back() { compose.onNodeWithContentDescription("Back").performClick(); compose.waitForIdle() }

    @Test fun settingsKeepsItsPlaceWhileAPageOfSettingsIsOpen() {
        openSettings()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid repeats"))
        assertTrue("The list must be scrolled for this test", scrollOf("settings-list") > 0f)
        val before = topOf("Avoid repeats")
        compose.onNode(hasText("Avoid repeats") and hasClickAction()).performClick()
        waitFor("repeats-list")
        back(); waitFor("settings-list")
        assertEquals(before, topOf("Avoid repeats"), 1f)
    }

    @Test fun settingsStartsAtTheTopWhenItIsOpenedAgain() {
        openSettings()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid repeats"))
        assertTrue(scrollOf("settings-list") > 0f)
        back()
        waitFor("device-list")
        openSettings()
        assertEquals(0f, scrollOf("settings-list"), 0.01f)
    }

    @Test fun theLicenceListAndSettingsKeepTheirPlaceWhileADocumentIsOpen() {
        openSettings()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Licenses"))
        // The end of the list is the hardest place to keep, because a list that is shorter than the screen by a little puts itself back at the end.
        compose.onNodeWithTag("settings-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val settingsBefore = topOf("Licenses")
        compose.onNode(hasText("Licenses") and hasClickAction()).performClick()
        waitFor("licences-list")
        compose.onNodeWithTag("licences-list").performScrollToNode(hasText("Coil"))
        assertTrue("The licence list must be scrolled for this test", scrollOf("licences-list") > 0f)
        val licencesBefore = topOf("Coil")
        compose.onNode(hasText("Coil") and hasClickAction()).performClick()
        waitFor("licence-document")
        back(); waitFor("licences-list")
        assertEquals(licencesBefore, topOf("Coil"), 1f)
        back(); waitFor("settings-list")
        assertEquals(settingsBefore, topOf("Licenses"), 1f)
    }

    @Test fun aPlaylistAtItsEndKeepsItsPlaceWhileSettingsIsOpenOverIt() {
        val playlist = app.library.createPlaylist("Long list").also { playlistId = it.id }
        (1..60).forEach { number ->
            val track = Track("local:long-$number", "Long track %02d".format(number), "Artist", localUri = "file:///missing/long-$number.flac")
            app.library.save(track); app.library.addToPlaylist(playlist.id, track)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Long list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Long list").performClick()
        waitFor("detail-list")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Long track 60"))
        val before = topOf("Long track 60")
        openSettings()
        back()
        waitFor("detail-list")
        assertEquals(before, topOf("Long track 60"), 1f)
    }

    @Test fun aPlaylistKeepsItsPlaceWhileSettingsIsOpenOverIt() {
        val playlist = app.library.createPlaylist("Long list").also { playlistId = it.id }
        (1..60).forEach { number ->
            val track = Track("local:long-$number", "Long track %02d".format(number), "Artist", localUri = "file:///missing/long-$number.flac")
            app.library.save(track); app.library.addToPlaylist(playlist.id, track)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Long list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Long list").performClick()
        waitFor("detail-list")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Long track 40"))
        assertTrue("The playlist must be scrolled for this test", scrollOf("detail-list") > 0f)
        val before = topOf("Long track 40")
        openSettings()
        back()
        waitFor("detail-list")
        assertEquals(before, topOf("Long track 40"), 1f)
    }
}
