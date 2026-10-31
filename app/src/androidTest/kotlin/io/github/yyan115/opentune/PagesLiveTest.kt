package io.github.yyan115.opentune

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in checks of the TIDAL pages, lyrics and credits against a real account. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PagesLiveTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    @get:Rule(order = 0) val account = LiveAccount.rule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val pages get() = ViewModelProvider(compose.activity)[PagesViewModel::class.java]

    private fun settle(milliseconds: Long = 1500) { compose.waitForIdle(); Thread.sleep(milliseconds); compose.waitForIdle() }

    @Test fun listenTabShowsPersonalisedAndEditorialSections() {
        compose.runOnIdle { model.settings { it.copy(offline = false, theme = "dark") } }
        compose.waitUntil(45_000) { pages.state.value[PageRoute.ForYou.key]?.page != null && pages.state.value[PageRoute.Home.key]?.page != null }
        val forYou = pages.state.value.getValue(PageRoute.ForYou.key).page!!
        assertTrue("For you has mixes", forYou.modules.any { it is CarouselModule && it.layout == CarouselLayout.MIXES })
        assertTrue("Home has tracks or albums", pages.state.value.getValue(PageRoute.Home.key).page!!.modules.any { it is TrackListModule || it is CarouselModule })
        settle(); LiveAccount.screenshot("listen-top")
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(30_000) { pages.state.value[PageRoute.Explore.key]?.page != null }
        assertTrue(pages.state.value.getValue(PageRoute.Explore.key).page!!.modules.any { it is LinksModule && it.cloud })
        settle(); LiveAccount.screenshot("search-explore")
    }

    @Test fun albumArtistCreditsAndLyricsWork() {
        compose.runOnIdle { model.settings { it.copy(offline = false, theme = "dark") } }
        compose.runOnIdle { model.search("Norah Jones Don't Know Why") }
        compose.waitUntil(30_000) { model.state.value.search.tracks.isNotEmpty() }
        val track = model.state.value.search.tracks.first { it.title.startsWith("Don't Know Why") }
        compose.onNodeWithTag("nav-1").performClick()
        settle(); LiveAccount.screenshot("search-results")

        compose.runOnIdle { model.open(Release(track.albumId, track.album, track.artist, type = ReleaseType.ALBUM)) }
        val albumKey = PageRoute.album(track.albumId).key
        compose.waitUntil(30_000) { pages.state.value[albumKey]?.page != null }
        val album = pages.state.value.getValue(albumKey).page!!
        assertTrue(album.modules.any { it is AlbumHeader && it.review.isNotBlank() })
        assertTrue(album.modules.filterIsInstance<TrackListModule>().first().numbered)
        settle(); LiveAccount.screenshot("album-page")

        compose.runOnIdle { model.showCredits("album", track.albumId, track.album) }
        compose.waitUntil(30_000) { model.state.value.credits?.loading == false }
        assertTrue(model.state.value.credits!!.credits.isNotEmpty())
        settle(); LiveAccount.screenshot("album-credits")
        compose.runOnIdle { model.dismissCredits() }

        compose.runOnIdle { model.closeAll(); model.open(Release(track.artistId, "Norah Jones", "", type = ReleaseType.ARTIST)) }
        val artistKey = PageRoute.artist(track.artistId).key
        compose.waitUntil(30_000) { pages.state.value[artistKey]?.page != null }
        val artist = pages.state.value.getValue(artistKey).page!!
        assertTrue(artist.modules.any { it is ArtistHeader && it.bio.isNotBlank() && !it.bio.contains("wimpLink") })
        settle(); LiveAccount.screenshot("artist-page")

        // This track has timed lyrics. Norah Jones' track above has none, which is covered by the unit tests.
        val lyricTrack = Track("163793678", "Feather", "Nujabes")
        compose.runOnIdle { model.closeAll(); model.play(listOf(lyricTrack)) }
        compose.waitUntil(30_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        assertNull(model.state.value.playback.error)
        compose.onNodeWithTag("mini-player").performClick()
        settle()
        compose.onNodeWithText("Lyrics").performClick()
        compose.waitUntil(30_000) { model.state.value.lyricsStatus == LyricsStatus.Ready }
        val lyrics = model.state.value.lyrics!!
        assertTrue("Lyrics have timing", lyrics.synced)
        val index = lyrics.lines.size / 2
        compose.runOnIdle { model.seek(lyrics.lines[index].timeMs + 400) }
        compose.waitUntil(15_000) { model.state.value.playback.position >= lyrics.lines[index].timeMs }
        settle(2500); LiveAccount.screenshot("player-lyrics")
        compose.runOnIdle { model.togglePlay() }
    }

    @Test fun mixPageAndTrackRadioPlay() {
        compose.runOnIdle { model.settings { it.copy(offline = false) } }
        compose.waitUntil(45_000) { pages.state.value[PageRoute.ForYou.key]?.page != null || run { pages.load(PageRoute.ForYou); false } }
        val mix = pages.state.value.getValue(PageRoute.ForYou.key).page!!.modules.filterIsInstance<CarouselModule>().first { it.layout == CarouselLayout.MIXES }.items.first()
        compose.runOnIdle { model.open(mix) }
        val key = PageRoute.mix(mix.id).key
        compose.waitUntil(30_000) { pages.state.value[key]?.page != null }
        val tracks = pages.state.value.getValue(key).page!!.modules.filterIsInstance<TrackListModule>().first().tracks
        assertTrue(tracks.isNotEmpty())
        settle(); LiveAccount.screenshot("mix-page")
        compose.runOnIdle { model.playTrackRadio(tracks.first()) }
        compose.waitUntil(30_000) { model.state.value.playback.queue.size > 10 }
        compose.runOnIdle { model.togglePlay() }
    }
}
