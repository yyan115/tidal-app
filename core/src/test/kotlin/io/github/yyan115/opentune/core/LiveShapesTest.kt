package io.github.yyan115.opentune.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser against answers of the real service, with the text made up and the shape kept (see scripts/sanitize_fixture.py). A fixture written by hand shows
 * what its author expected. These show what the service sent: the kinds of module, the fields of a track, the roles of a credit and the mark for AI-generated
 * music. The answers were taken on 2026-11-26.
 */
class LiveShapesTest {
    private fun raw(name: String) = JSONObject(javaClass.getResource("/fixtures/live/$name")!!.readText())
    private fun page(name: String) = PageCodec.parse(raw(name))

    @Test fun anArtistPageHasEverySectionInTheOrderOfTheService() {
        val modules = page("artist.json").modules
        assertEquals(
            listOf("ArtistHeader:", "TrackListModule:Top Tracks", "CarouselModule:Featured Albums", "CarouselModule:EP & Singles", "CarouselModule:Compilations",
                "CarouselModule:Live albums", "CarouselModule:Playlists", "CarouselModule:Videos", "CarouselModule:Appears On", "TrackListModule:Credits",
                "TextModule:Biography", "CarouselModule:Related Artists", "CarouselModule:Influencers"),
            modules.map { "${it::class.simpleName}:${it.title}" })
    }

    @Test fun theCreditsOfAnArtistPageCarryTheRolesOfTheArtist() {
        val credits = page("artist.json").modules.filterIsInstance<TrackListModule>().single { it.title == "Credits" }
        assertEquals("The service names how many credits exist, and the answer holds a few", 1048, credits.total)
        assertEquals(2, credits.tracks.size)
        assertEquals("Every credit has its roles", credits.tracks.map { it.id }.toSet(), credits.roles.keys)
        assertTrue(credits.roles.values.flatten().all { it.isNotBlank() })
        assertNotNull("The credits can be asked for in more pages", credits.dataPath)
    }

    @Test fun theTopTracksOfAnArtistAreCompleteTracks() {
        val top = page("artist.json").modules.filterIsInstance<TrackListModule>().single { it.title == "Top Tracks" }
        assertEquals(300, top.total)
        for (track in top.tracks) {
            assertTrue(track.id.isNotBlank() && track.title.isNotBlank() && track.artist.isNotBlank() && track.album.isNotBlank())
            assertTrue(track.artistId.isNotBlank() && track.albumId.isNotBlank() && track.durationMs > 0)
            assertFalse("A track of the test account is not AI-generated", track.ai)
        }
        assertTrue("The service sends the AI mark on every track of a list", raw("artist.json").toString().contains("\"ai\""))
    }

    @Test fun theAlbumListsOfAnArtistPageAreAlbumsWithTheirCovers() {
        val featured = page("artist.json").modules.filterIsInstance<CarouselModule>().single { it.title == "Featured Albums" }
        assertEquals(18, featured.total)
        assertTrue(featured.items.all { it.type == ReleaseType.ALBUM && it.id.isNotBlank() && it.artwork != null && !it.ai })
    }

    @Test fun anAlbumPageHasItsHeaderAndNumberedTracks() {
        val modules = page("album.json").modules
        assertTrue(modules.any { it is AlbumHeader })
        val tracks = modules.filterIsInstance<TrackListModule>().first()
        assertTrue(tracks.numbered)
        assertTrue(tracks.tracks.all { it.trackNumber > 0 })
    }

    @Test fun theHomePageHasSectionsThatTheAppCanShow() {
        val modules = page("home.json").modules
        assertTrue(modules.size >= 3)
        assertTrue("Every section has a label or is a header", modules.all { it.title.isNotBlank() || it is ArtistHeader || it is AlbumHeader || it is MixHeader || it is TrackListModule })
    }

    @Test fun aSearchAnswerHasTracksAlbumsAndArtists() {
        val search = raw("search.json")
        val tracks = JsonCodec.items(search.getJSONObject("tracks")).map(JsonCodec::track)
        val albums = JsonCodec.items(search.getJSONObject("albums")).map { JsonCodec.release(it, ReleaseType.ALBUM) }
        val artists = JsonCodec.items(search.getJSONObject("artists")).map { JsonCodec.release(it, ReleaseType.ARTIST) }
        assertTrue(tracks.isNotEmpty() && albums.isNotEmpty() && artists.isNotEmpty())
        assertTrue(tracks.all { it.id.isNotBlank() && it.artist.isNotBlank() && !it.ai })
        assertTrue(albums.all { it.id.isNotBlank() && !it.ai })
        assertTrue(artists.all { it.id.isNotBlank() && !it.ai })
    }

    @Test fun theSavedTracksAnswerNamesItsTotalAndItsItems() {
        val favorites = raw("favorites.json")
        assertTrue(favorites.getInt("totalNumberOfItems") >= 2)
        val tracks = JsonCodec.items(favorites).map(JsonCodec::track)
        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }
}
