package io.github.yyan115.opentune

import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.hasMore
import io.github.yyan115.opentune.ui.showsEverySong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page of a mix is its list of songs, so every song shows and "Load more" appears while TIDAL has more. A preview of five songs belongs to a list
 * inside a page of many sections, such as the top songs of an artist, which has a title row with "View all".
 */
class PageRulesTest {
    private fun songs(count: Int) = (1..count).map { Track("t$it", "Song $it", "Artist") }
    private val release = Release("r", "A title", "TIDAL", null)
    private val mix = MixHeader("header", "", release.copy(type = ReleaseType.MIX), "Songs for you", "")
    private val artist = ArtistHeader("header", "", release.copy(type = ReleaseType.ARTIST), "", "", "")
    private val album = AlbumHeader("header", "", release.copy(type = ReleaseType.ALBUM), "Artist", "7", "", "", 10, 0, emptyList(), "", "", false)
    private fun list(count: Int, title: String = "", numbered: Boolean = false, total: Int = count, dataPath: String? = null, showMore: String? = null) =
        TrackListModule("list", title, songs(count), numbered, showMore, dataPath, total)
    private fun page(vararg modules: PageModule) = PageData("p", "Page", modules.toList())

    @Test fun aMixPageShowsEverySong() {
        for (count in listOf(0, 1, 5, 6, 50, 100)) assertTrue("$count songs", page(mix, list(count)).showsEverySong())
    }

    @Test fun aMixPageShowsEverySongWhateverItsListIsCalled() {
        assertTrue(page(mix, list(20, title = "Songs")).showsEverySong())
        assertTrue(page(mix, list(20, title = "")).showsEverySong())
    }

    @Test fun aMixPageWithMoreSectionsStillShowsEverySong() {
        assertTrue(page(mix, list(20), CarouselModule("more", "More mixes", CarouselLayout.MIXES, emptyList())).showsEverySong())
    }

    @Test fun anArtistPageKeepsThePreviewOfItsTopSongs() {
        assertFalse(page(artist, list(10, title = "Top tracks", showMore = "pages/data/top"), CarouselModule("a", "Albums", CarouselLayout.ALBUMS, emptyList())).showsEverySong())
    }

    @Test fun anAlbumPageKeepsItsNumberedList() {
        assertFalse(page(album, list(12, numbered = true)).showsEverySong())
    }

    @Test fun aPageWithoutAHeaderKeepsThePreviewOfItsLists() {
        assertFalse(page(list(12, title = "Recently played", showMore = "pages/data/recent")).showsEverySong())
        assertFalse(page(list(12, title = "A"), list(12, title = "B")).showsEverySong())
        assertFalse(page().showsEverySong())
    }

    @Test fun loadMoreShowsOnlyWhileTidalHasMoreSongs() {
        assertTrue(list(5, total = 50, dataPath = "pages/data/x").hasMore(shown = 5))
        assertTrue(list(5, total = 50, dataPath = "pages/data/x").hasMore(shown = 49))
        assertFalse(list(5, total = 50, dataPath = "pages/data/x").hasMore(shown = 50))
        assertFalse(list(5, total = 50, dataPath = "pages/data/x").hasMore(shown = 51))
        assertFalse(list(5, total = 50, dataPath = null).hasMore(shown = 5))
        assertFalse(list(5, total = 5, dataPath = "pages/data/x").hasMore(shown = 5))
        assertEquals(false, list(0, total = 0, dataPath = "pages/data/x").hasMore(shown = 0))
    }
}
