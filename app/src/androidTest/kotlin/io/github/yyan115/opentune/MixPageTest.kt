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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A page of a mix lists every song, and a preview of five stays where it belongs. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class MixPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(page: PageData) {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.setContent { OpenTuneTheme("dark") { PageCase(page, model) } }
    }

    private fun scrollTo(song: Int) = compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song $song"))

    @Test fun aMixOfTwentySongsShowsAllTwentyAndLoadMoreWhileTidalHasMore() {
        show(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(20, total = 50, dataPath = "pages/data/mix")))
        scrollTo(6); compose.onNodeWithText("Page song 6").assertExists()
        scrollTo(20); compose.onNodeWithText("Page song 20").assertExists()
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").assertExists()
    }

    @Test fun aMixWhoseSongsAreAllShownHasNoLoadMore() {
        show(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(20, total = 20, dataPath = "pages/data/mix")))
        scrollTo(20)
        compose.onAllNodesWithText("Load more").assertCountEquals(0)
    }

    @Test fun aMixWithoutAPlaceToAskForMoreHasNoLoadMore() {
        show(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(20, total = 50, dataPath = null)))
        scrollTo(20)
        compose.onAllNodesWithText("Load more").assertCountEquals(0)
    }

    @Test fun aMixOfOneSongShowsIt() {
        show(PageFixtures.page(PageFixtures.mixHeader, PageFixtures.list(1)))
        compose.onNodeWithText("Page song 1").assertExists()
        compose.onAllNodesWithText("Load more").assertCountEquals(0)
    }

    @Test fun aMixHeaderWithoutAListShowsNoSong() {
        show(PageFixtures.page(PageFixtures.mixHeader))
        compose.onAllNodesWithText("Page song 1").assertCountEquals(0)
    }

    @Test fun theTopSongsOfAnArtistStayAPreviewOfFiveWithViewAll() {
        show(PageFixtures.page(PageFixtures.artistHeader, PageFixtures.list(12, title = "Top tracks", showMore = "pages/data/top")))
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Page song 5"))
        compose.onNodeWithText("Page song 5").assertExists()
        compose.onAllNodesWithText("Page song 6").assertCountEquals(0)
        compose.onNodeWithText("View all").assertExists()
    }

    @Test fun theNumberedListOfAnAlbumShowsEverySong() {
        show(PageFixtures.page(PageFixtures.list(12, numbered = true)))
        scrollTo(12)
        compose.onNodeWithText("Page song 12").assertExists()
    }
}
