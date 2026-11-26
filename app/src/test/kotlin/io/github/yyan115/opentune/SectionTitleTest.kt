package io.github.yyan115.opentune

import io.github.yyan115.opentune.ui.sectionTitleRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TIDAL names the sections of its pages. Two of its names on an artist page are replaced, and every other name stays as TIDAL wrote it. */
class SectionTitleTest {
    private val artist = "pages/artist?artistId=3"

    @Test fun featuredAlbumsOfAnArtistAreAlbums() {
        assertEquals(R.string.section_albums, sectionTitleRes("Featured Albums", artist, listOf("Top Tracks", "Featured Albums")))
        assertEquals(R.string.section_albums, sectionTitleRes("  featured albums ", artist, emptyList()))
    }

    @Test fun relatedArtistsAreSimilarArtists() {
        assertEquals(R.string.section_similar_artists, sectionTitleRes("Related Artists", artist, emptyList()))
    }

    @Test fun aPageThatHasASectionNamedAlbumsKeepsTheNameOfTheOther() {
        assertNull(sectionTitleRes("Featured Albums", artist, listOf("Albums", "Featured Albums")))
    }

    @Test fun otherPagesKeepTheNamesOfTidal() {
        for (route in listOf("pages/home", "pages/explore", "pages/album?albumId=1", "pages/my_collection_my_mixes")) {
            assertNull(route, sectionTitleRes("Featured Albums", route, emptyList()))
            assertNull(route, sectionTitleRes("Related Artists", route, emptyList()))
        }
    }

    @Test fun otherNamesStayAsTidalWroteThem() {
        for (title in listOf("Top Tracks", "Albums", "EP & Singles", "Playlists", "Videos", "Credits", "")) assertNull(title, sectionTitleRes(title, artist, emptyList()))
    }
}
