package io.github.yyan115.opentune.core

/** The lists of a listener's library that the service keeps. */
enum class LibraryList { Favorites, Albums, Artists, Playlists }

/** What a list looked like at one moment: how many items it holds and which item comes first. A change of either one means that the list changed. */
data class ListProbe(val total: Int, val first: String)

/**
 * Notices that the library changed somewhere else, such as on the TIDAL website or in another app, without loading the whole library.
 * A probe asks the service for one item of each list, which is a few hundred bytes. The whole library is loaded only when a probe differs from the one
 * that was taken when the library was loaded last.
 */
object LibraryFreshness {
    /** How often the app probes while it is on the screen. */
    const val PROBE_INTERVAL_MS = 60_000L

    /** The lists whose probe differs from [known]. Without a probe from an earlier load, every list counts as changed. */
    fun changed(known: Map<LibraryList, ListProbe>?, now: Map<LibraryList, ListProbe>): Set<LibraryList> =
        if (known == null) now.keys else now.filter { (list, probe) -> known[list] != probe }.keys
}
