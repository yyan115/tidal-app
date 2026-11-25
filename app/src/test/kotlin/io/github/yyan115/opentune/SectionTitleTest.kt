package io.github.yyan115.opentune

import io.github.yyan115.opentune.ui.sectionTitleRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TIDAL names the sections of its pages. Two of its names are replaced, and every other name stays as TIDAL wrote it. */
class SectionTitleTest {
    @Test fun featuredAlbumsAreAlbums() {
        assertEquals(R.string.section_albums, sectionTitleRes("Featured Albums"))
        assertEquals(R.string.section_albums, sectionTitleRes("  featured albums "))
    }

    @Test fun relatedArtistsAreSimilarArtists() {
        assertEquals(R.string.section_similar_artists, sectionTitleRes("Related Artists"))
    }

    @Test fun otherNamesStayAsTidalWroteThem() {
        for (title in listOf("Top Tracks", "Albums", "EP & Singles", "Playlists", "Videos", "Credits", "")) assertNull(title, sectionTitleRes(title))
    }
}
