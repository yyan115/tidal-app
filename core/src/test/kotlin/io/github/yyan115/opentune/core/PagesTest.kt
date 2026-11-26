package io.github.yyan115.opentune.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PagesTest {
    private fun fixture(name: String) = PageCodec.parse(JSONObject(javaClass.getResource("/fixtures/$name")!!.readText()))

    @Test fun homePageProducesCarouselsAndTrackLists() {
        val page = fixture("home.json")
        assertEquals(listOf("CarouselModule", "TrackListModule", "CarouselModule"), page.modules.map { it::class.simpleName })
        val playlists = page.modules[0] as CarouselModule
        assertEquals(CarouselLayout.PLAYLISTS, playlists.layout)
        assertTrue(playlists.items.all { it.type == ReleaseType.PLAYLIST })
        assertEquals("aaaa", playlists.items[0].id)
        assertEquals("pages/single-module-page/hits", playlists.showMore)
        val tracks = page.modules[1] as TrackListModule
        assertEquals(238, tracks.total)
        assertEquals(2, tracks.tracks.size)
        assertEquals("pages/data/example", tracks.dataPath)
        val albums = page.modules[2] as CarouselModule
        assertEquals("Example Artist", albums.items[0].artist)
        assertEquals("100", albums.items[0].artistId)
        assertEquals(202, albums.total)
    }

    @Test fun explorePageProducesLinksThatOpenOtherPages() {
        val links = fixture("explore.json").modules.filterIsInstance<LinksModule>()
        assertEquals(3, links.size)
        assertEquals(listOf(true, true, false), links.map { it.cloud })
        assertEquals(listOf(PageLink("Rock", "pages/genre_rock"), PageLink("Jazz", "pages/genre_jazz")), links[0].links)
    }

    @Test fun artistPageHasHeaderBioWithoutMarkupAndSections() {
        val page = fixture("artist.json")
        val header = page.modules.filterIsInstance<ArtistHeader>().single()
        assertEquals("Example Artist", header.artist.title)
        assertEquals("Example Artist is a singer.\n\nShe plays piano. Example Album came first.", header.bio)
        assertEquals("mix100", header.radioId)
        assertTrue(header.artist.artwork!!.endsWith("/750x750.jpg"))
        val carousels = page.modules.filterIsInstance<CarouselModule>()
        val mixed = carousels.single { it.layout == CarouselLayout.MIXED }
        assertEquals(listOf(ReleaseType.PLAYLIST, ReleaseType.ALBUM), mixed.items.map { it.type })
        val video = carousels.single { it.layout == CarouselLayout.VIDEOS }.items.single()
        assertEquals(ReleaseType.VIDEO, video.type)
        assertEquals("3:26", video.subtitle)
        assertTrue(video.artwork!!.endsWith("/640x360.jpg"))
        val related = carousels.single { it.layout == CarouselLayout.ARTISTS }.items.single()
        assertNotNull("The album cover stands in for a missing artist picture", related.artwork)
        assertEquals(134, page.modules.filterIsInstance<TrackListModule>().single().total)
    }

    @Test fun albumPageHasHeaderWithReviewAndNumberedTracks() {
        val page = fixture("album.json")
        val header = page.modules.filterIsInstance<AlbumHeader>().single()
        assertEquals("Example Album", header.album.title)
        assertEquals("A debut review.", header.review)
        assertEquals("2002-02-26", header.releaseDate)
        assertEquals(14, header.trackCount)
        assertEquals(2_715_000L, header.durationMs)
        assertEquals("100", header.artistId)
        assertTrue(header.tags.contains("HIRES_LOSSLESS"))
        val tracks = page.modules.filterIsInstance<TrackListModule>().single()
        assertTrue(tracks.numbered)
        assertEquals(listOf(1, 2), tracks.tracks.map { it.trackNumber })
        assertEquals(14, tracks.total)
    }

    // The shape of a credits module of an artist page as TIDAL answers it: a track with the roles that the artist had on it.
    private val creditsPage = """{"rows":[{"modules":[{"id":"c1","type":"ITEM_LIST_WITH_ROLES","title":"Credits","showMore":{"apiPath":"pages/contributor?artistId=10249"},
        "pagedList":{"dataApiPath":"pages/data/c1?artistId=10249","limit":4,"offset":0,"totalNumberOfItems":1049,"items":[
        {"type":"track","item":{"id":112706881,"title":"Come Away With Me","duration":198,"artists":[{"id":10249,"name":"Norah Jones","type":"MAIN"}],"album":{"id":1,"title":"Come Away With Me"}},
         "roles":[{"name":"Composer","category":"Songwriter","categoryId":2},{"name":"Lyricist","category":"Songwriter","categoryId":2},{"name":"Piano","category":"Performer","categoryId":11},{"name":"Piano","category":"Performer","categoryId":11}]},
        {"type":"track","item":{"id":112706882,"title":"Don't Know Why","duration":186,"artists":[{"id":10249,"name":"Norah Jones","type":"MAIN"}],"album":{"id":1,"title":"Come Away With Me"}},"roles":[]}]}}]}]}"""

    @Test fun aListOfCreditsKeepsTheRolesOfEachTrack() {
        val module = PageCodec.parse(JSONObject(creditsPage)).modules.filterIsInstance<TrackListModule>().single()
        assertEquals(1049, module.total)
        assertEquals("Come Away With Me", module.tracks.first().title)
        assertEquals("A role that an item repeats is kept once, in the order of TIDAL", listOf("Composer", "Lyricist", "Piano"), module.roles["112706881"])
        assertNull("A track without roles has no entry", module.roles["112706882"])
    }

    @Test fun otherListsOfTracksHaveNoRoles() {
        val module = fixture("home.json").modules.filterIsInstance<TrackListModule>().first()
        assertTrue(module.roles.isEmpty())
    }

    @Test fun theRolesOfALaterPageAreReadFromItsItems() {
        val page = JSONObject(creditsPage).getJSONArray("rows").getJSONObject(0).getJSONArray("modules").getJSONObject(0).getJSONObject("pagedList")
        assertEquals(mapOf("112706881" to listOf("Composer", "Lyricist", "Piano")), PageCodec.trackRoles(page))
        assertTrue(PageCodec.trackRoles(null).isEmpty())
    }

    @Test fun mixPageKeepsTrackDetailsNeededForPlayback() {
        val page = fixture("mix.json")
        val header = page.modules.filterIsInstance<MixHeader>().single()
        assertEquals("abc123", header.mix.id)
        assertEquals("https://images.tidal.com/example/640.jpg", header.mix.artwork)
        val track = page.modules.filterIsInstance<TrackListModule>().single().tracks.single()
        assertEquals("Track One (Live)", track.title)
        assertEquals("7", track.artistId)
        assertEquals(-7.5, track.replayGain!!, 0.0)
        assertEquals(listOf("LOSSLESS", "HIRES_LOSSLESS", "DOLBY_ATMOS"), track.tags)
        assertTrue(track.explicit)
    }

    @Test fun aMixPageThatShowsFiveOfFiftySongsSaysWhereTheRestIs() {
        val page = fixture("mix-long.json")
        val list = page.modules.filterIsInstance<TrackListModule>().single()
        assertEquals(5, list.tracks.size)
        assertEquals(50, list.total)
        assertEquals("pages/data/mix-songs", list.dataPath)
        assertEquals("", list.title)
        assertNull(list.showMore)
        assertEquals(1, page.modules.filterIsInstance<MixHeader>().size)
    }

    @Test fun trackSurvivesEncodingWithItsNewFields() {
        val original = Track("1", "A", "B", artistId = "7", trackNumber = 4, replayGain = -3.25, peak = 0.9, tags = listOf("LOSSLESS"))
        assertEquals(original.copy(addedAt = 0), JsonCodec.decodeTrack(JsonCodec.encodeTrack(original)).copy(addedAt = 0))
        assertNull(JsonCodec.decodeTrack(JSONObject("""{"id":"1","title":"A","artist":"B"}""")).replayGain)
    }

    @Test fun releaseSurvivesEncoding() {
        val original = Release("1", "T", "A", "https://x/y.jpg", "2002", ReleaseType.MIX, "9", listOf("LOSSLESS"), "d", "7")
        assertEquals(listOf(original), JsonCodec.decodeReleases(JsonCodec.encodeReleases(listOf(original))))
    }

    @Test fun nullFieldsDoNotBecomeTheWordNull() {
        val release = JsonCodec.release(JSONObject("""{"uuid":"p","title":"List","releaseDate":null,"creator":{"id":1,"name":null},"description":null,"shortSubtitle":null}"""), ReleaseType.PLAYLIST)
        assertEquals("", release.artist); assertEquals("", release.subtitle); assertEquals("", release.description)
        assertEquals("Untitled", JsonCodec.release(JSONObject("""{"id":3,"title":null,"name":null}"""), ReleaseType.ALBUM).title)
    }

    @Test fun missingTitlesUseTheTextThatTheAppSupplies() {
        val before = Placeholders.untitled
        try {
            Placeholders.untitled = "Sans titre"
            assertEquals("Sans titre", JsonCodec.release(JSONObject("""{"id":3,"title":null,"name":null}"""), ReleaseType.ALBUM).title)
        } finally { Placeholders.untitled = before }
    }

    @Test fun markupIsRemovedFromText() {
        assertEquals("One Two\n\nThree", PageCodec.plainText("""[wimpLink artistId="1"]One[/wimpLink] <b>Two</b><br>Three"""))
        assertEquals("A\n\nB", PageCodec.plainText("A<br/>B"))
        assertEquals("A\n\nB", PageCodec.plainText("A<br/><br/>B"))
        assertEquals("", PageCodec.plainText(null))
        assertEquals("", PageCodec.plainText("null"))
    }
}
