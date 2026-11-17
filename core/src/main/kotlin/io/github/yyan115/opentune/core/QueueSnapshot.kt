package io.github.yyan115.opentune.core

/** The queue as it is written to the device so that the next start continues where playback stopped. */
data class QueueSnapshot(
    /** The tracks as JSON texts, one for each item of the queue, in queue order. */
    val tracks: List<String>,
    /** For each item, whether the listener added it to the queue by hand. */
    val manual: List<Boolean>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: Int,
) {
    /** The queue as one JSON array. The texts of the tracks are used as they are, so that a long queue is not decoded and encoded again. */
    fun tracksJson(): String = tracks.joinToString(separator = ",", prefix = "[", postfix = "]")

    /** The marks of the queue as text, in the form that [QueueOrder.decode] reads. */
    fun manualMarks(): String = QueueOrder.encode(manual)
}
