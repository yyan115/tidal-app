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

    /** How long the app waits before it looks again after a load of the library failed, so that a service that answers with an error is not asked every minute. */
    const val RETRY_AFTER_FAILURE_MS = 10 * 60_000L

    /** True when the app may look for changes. [failedAt] is the time of the last failed load of the library, or null when the last load worked. */
    fun mayLook(failedAt: Long?, now: Long): Boolean = failedAt == null || now - failedAt >= RETRY_AFTER_FAILURE_MS

    /**
     * The list that a request of the app changes, or null when the request changes no list that is probed. [path] is the path of the request without the
     * address of the service, and [userId] is the listener. A change that the app makes itself is not a change elsewhere, so the app takes a new probe of that list.
     */
    fun listChangedBy(path: String, userId: String): LibraryList? = when {
        path.startsWith("users/$userId/favorites/tracks") -> LibraryList.Favorites
        path.startsWith("users/$userId/favorites/albums") -> LibraryList.Albums
        path.startsWith("users/$userId/favorites/artists") -> LibraryList.Artists
        path.startsWith("users/$userId/favorites/playlists") || path.startsWith("users/$userId/playlists") || path.startsWith("playlists/") -> LibraryList.Playlists
        else -> null
    }

    /** The lists whose probe differs from [known]. Without a probe from an earlier load, every list counts as changed. */
    fun changed(known: Map<LibraryList, ListProbe>?, now: Map<LibraryList, ListProbe>): Set<LibraryList> =
        if (known == null) now.keys else now.filter { (list, probe) -> known[list] != probe }.keys
}
