package io.github.yyan115.opentune.core

/**
 * Hides what TIDAL marks as AI-generated, when the listener chose to. TIDAL marks tracks and albums. Artists, playlists, mixes and videos carry no mark,
 * so they always stay. The filter is for what the app offers, such as search results, pages and radio. It is not for what the listener saved or downloaded.
 */
object AiFilter {
    fun tracks(list: List<Track>, hide: Boolean): List<Track> = if (hide) list.filterNot(Track::ai) else list
    fun releases(list: List<Release>, hide: Boolean): List<Release> = if (hide) list.filterNot(Release::ai) else list
    fun search(result: SearchResult, hide: Boolean): SearchResult = if (!hide) result else result.copy(
        tracks = tracks(result.tracks, true), albums = releases(result.albums, true),
        topTrack = result.topTrack?.takeUnless(Track::ai), topRelease = result.topRelease?.takeUnless(Release::ai),
    )
}
