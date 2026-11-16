package io.github.yyan115.opentune.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.yyan115.opentune.core.QueueOrder

/** The key in the metadata of a queue item that marks a track the listener added by hand. */
const val MANUAL_EXTRA = "manual"

/** Whether the listener added this item to the queue by hand. */
fun MediaItem.isManual(): Boolean = mediaMetadata.extras?.getBoolean(MANUAL_EXTRA) == true

/** For each item of the queue, whether the listener added it by hand. */
fun Player.manualMarks(): List<Boolean> = List(mediaItemCount) { getMediaItemAt(it).isManual() }

/** The number of items right after the current one that the listener added by hand. */
fun Player.leadingManual(): Int = QueueOrder.leadingManual(manualMarks(), currentMediaItemIndex)
