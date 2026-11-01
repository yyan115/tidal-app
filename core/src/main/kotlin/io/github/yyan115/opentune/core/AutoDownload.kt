package io.github.yyan115.opentune.core

/** Chooses which saved tracks to download next when the listener keeps saved tracks offline. */
object AutoDownload {
    /** Streamed tracks that are not on the device yet, newest first, at most [limit] of them. */
    fun pick(saved: List<Track>, onDevice: Set<String>, limit: Int): List<Track> =
        saved.filter { !it.isLocal && !it.id.startsWith("local:") && it.id !in onDevice }.distinctBy(Track::id).take(limit)
}
