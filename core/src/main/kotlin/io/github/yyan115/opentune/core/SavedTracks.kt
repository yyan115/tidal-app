package io.github.yyan115.opentune.core

/** Combines the saved tracks that the device holds with the list of the account. */
object SavedTracks {
    /**
     * Tracks that the account does not list come first, because they were saved on this device and are not in the list yet.
     * The tracks of the account follow in the order that the service gives, which is the order they were saved in.
     * A track with a waiting removal is left out.
     */
    fun merge(device: List<Track>, account: List<Track>, pending: Map<String, Boolean>): List<Track> {
        val listed = account.mapTo(HashSet(), Track::id)
        return (device.filterNot { it.id in listed } + account).filter { pending[it.id] != false }.distinctBy(Track::id)
    }

    /**
     * Tracks that the device marks as saved while the complete list of the account does not hold them, which means they were removed from another app.
     * Files of the device have no copy in the account and tracks with a waiting change are not judged by the list.
     */
    fun removedElsewhere(device: List<Track>, account: List<Track>, pending: Map<String, Boolean>): List<Track> {
        val listed = account.mapTo(HashSet(), Track::id)
        return device.filter { !it.isFile && it.id !in listed && it.id !in pending }
    }
}
