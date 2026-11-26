package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.PageData
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.OpenTuneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A list of credits names the roles that the artist had on each song, and other lists of songs show no such line. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CreditsRolesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(page: PageData) {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.setContent { OpenTuneTheme("dark") { PageCase(page, model) } }
    }

    @Test fun everyCreditShowsItsRolesUnderTheArtist() {
        show(PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(12, title = "Credits", showMore = "pages/contributor", roles = true)))
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 1"))
        // The list draws the rows that fit on the screen. Every song that is drawn has its roles.
        val songs = compose.onAllNodesWithText("Page artist", useUnmergedTree = true).fetchSemanticsNodes().size
        val lines = compose.onAllNodesWithTag("track-detail", useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("At least three songs are drawn", songs >= 3)
        assertEquals("Each song that is drawn has a line of roles", songs, lines)
        compose.onAllNodesWithText("Producer, Composer, Lyricist, Piano, Vocalist, Mixing engineer, Mastering engineer", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test fun aListOfSongsWithoutRolesShowsNoRoleLine() {
        show(PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(12, title = "Top tracks", showMore = "pages/data/top")))
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 1"))
        compose.onAllNodesWithTag("track-detail", useUnmergedTree = true).assertCountEquals(0)
    }
}
