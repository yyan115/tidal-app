package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.core.Track

/**
 * The saved record of [track], which knows whether the song is on the device. A song in a queue or in a list from TIDAL is a copy from the time it was
 * made, so it does not know about a download that finished after that. Every screen that shows or decides by the state of a song reads this record.
 */
fun AppState.current(track: Track): Track = local.firstOrNull { it.id == track.id } ?: track

/** The song that plays, as its saved record. */
val AppState.playingTrack: Track? get() = playback.track?.let { current(it) }
