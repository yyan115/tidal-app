package io.github.yyan115.opentune.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** The flag belongs to an occurrence, not a track id: the same song may be queued twice. */
object ManualQueue {
    private const val KEY = "opentune.manual_queue"
    fun contains(item: MediaItem) = item.mediaMetadata.extras?.getBoolean(KEY) == true
    fun mark(item: MediaItem): MediaItem = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
        .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean(KEY, true) }).build()).build()

    fun upcomingIndices(player: Player): List<Int> =
        ((player.currentMediaItemIndex + 1).coerceAtLeast(0) until player.mediaItemCount).filter { contains(player.getMediaItemAt(it)) }

    fun insertionIndex(player: Player, next: Boolean): Int =
        if (next) (player.currentMediaItemIndex + 1).coerceAtLeast(0)
        else upcomingIndices(player).lastOrNull()?.plus(1) ?: (player.currentMediaItemIndex + 1).coerceAtLeast(0)
}
