package io.github.yyan115.opentune.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFilterTest {
    private val human = Track("1", "Human song", "Artist")
    private val machine = Track("2", "Machine song", "Artist", ai = true)
    private val album = Release("a1", "Human album", "Artist")
    private val machineAlbum = Release("a2", "Machine album", "Artist", ai = true)

    @Test fun nothingIsHiddenWhenTheListenerDidNotChooseIt() {
        assertEquals(listOf(human, machine), AiFilter.tracks(listOf(human, machine), hide = false))
        assertEquals(listOf(album, machineAlbum), AiFilter.releases(listOf(album, machineAlbum), hide = false))
    }

    @Test fun tracksAndAlbumsThatTidalMarksAreHiddenWhenTheListenerChoseIt() {
        assertEquals(listOf(human), AiFilter.tracks(listOf(human, machine), hide = true))
        assertEquals(listOf(album), AiFilter.releases(listOf(album, machineAlbum), hide = true))
    }

    @Test fun theOrderOfWhatStaysIsKept() {
        val list = listOf(machine, human, human.copy(id = "3"), machine.copy(id = "4"), human.copy(id = "5"))
        assertEquals(listOf("1", "3", "5"), AiFilter.tracks(list, true).map { it.id })
    }

    @Test fun aSearchResultLeavesOutMarkedTracksAlbumsAndTheTopHit() {
        val result = SearchResult(tracks = listOf(human, machine), albums = listOf(album, machineAlbum), artists = listOf(Release("r", "Artist", "", type = ReleaseType.ARTIST)),
            topTrack = machine, topRelease = machineAlbum)
        val filtered = AiFilter.search(result, hide = true)
        assertEquals(listOf(human), filtered.tracks)
        assertEquals(listOf(album), filtered.albums)
        assertEquals("Artists carry no mark and stay", result.artists, filtered.artists)
        assertNull(filtered.topTrack)
        assertNull(filtered.topRelease)
        assertEquals("A search result is kept as it is when nothing is hidden", result, AiFilter.search(result, hide = false))
    }

    @Test fun theMarkOfTidalIsReadFromTracksAndAlbums() {
        assertTrue(JsonCodec.track(JSONObject("""{"id":5,"title":"T","ai":true,"artists":[{"name":"A"}]}""")).ai)
        assertFalse(JsonCodec.track(JSONObject("""{"id":5,"title":"T","ai":false,"artists":[{"name":"A"}]}""")).ai)
        assertFalse("A track without the field is not marked", JsonCodec.track(JSONObject("""{"id":5,"title":"T","artists":[{"name":"A"}]}""")).ai)
        assertTrue(JsonCodec.release(JSONObject("""{"id":7,"title":"Album","ai":true}"""), ReleaseType.ALBUM).ai)
    }

    @Test fun onlyAlbumsCarryTheMarkAmongReleases() {
        assertFalse(JsonCodec.release(JSONObject("""{"uuid":"p","title":"Playlist","ai":true}"""), ReleaseType.PLAYLIST).ai)
        assertFalse(JsonCodec.release(JSONObject("""{"id":9,"name":"Artist","ai":true}"""), ReleaseType.ARTIST).ai)
    }

    @Test fun theMarkSurvivesTheCopyThatTheAppKeeps() {
        assertTrue(JsonCodec.decodeTrack(JsonCodec.encodeTrack(machine)).ai)
        assertFalse(JsonCodec.decodeTrack(JsonCodec.encodeTrack(human)).ai)
        assertTrue(JsonCodec.decodeRelease(JsonCodec.encodeRelease(machineAlbum)).ai)
        assertFalse("A copy that was saved before the mark existed reads as not marked", JsonCodec.decodeTrack(JSONObject(JsonCodec.encodeTrack(human).toString()).apply { remove("ai") }).ai)
    }
}
