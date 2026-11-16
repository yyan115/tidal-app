package io.github.yyan115.opentune.core

import kotlin.math.abs

/** The rules of the swipes and drags of the player. They work on numbers, so that they can be tested without a screen. */
object PlayerGestures {
    /** A drag of the full player that is faster than this, in heights of the screen per second, goes the way of the finger. */
    const val SHEET_FLING = 0.35f

    /** A swipe on the cover or on the small player that goes farther than this share of its width changes the track. */
    const val SWIPE_DISTANCE = 0.30f

    /** A swipe that is faster than this, in widths per second, changes the track even when it is short. */
    const val SWIPE_FLING = 1.6f

    /** A swipe that does not reach this share of the width is never a change of track, however fast it is. */
    const val SWIPE_MINIMUM = 0.08f

    /**
     * Whether the full player ends up open when the finger lifts. [progress] is how far it is open, from 0 for closed to 1 for open.
     * [velocity] is in heights per second, and it is positive when the finger moves up, which is the way that opens the player.
     */
    fun opens(progress: Float, velocity: Float): Boolean = when {
        velocity >= SHEET_FLING -> true
        velocity <= -SHEET_FLING -> false
        else -> progress >= 0.5f
    }

    enum class Swipe { Next, Previous, Stay }

    /**
     * What a swipe on the cover or on the small player does when the finger lifts.
     * [offset] is how far the finger moved, negative to the left, in the same unit as [width]. [velocity] is in widths per second.
     * A swipe to the left plays the next track and a swipe to the right plays the previous one. A swipe toward a track that does not exist stays.
     */
    fun swipe(offset: Float, velocity: Float, width: Float, hasNext: Boolean, hasPrevious: Boolean): Swipe {
        if (width <= 0f) return Swipe.Stay
        val share = abs(offset) / width
        val toward = if (offset < 0f) Swipe.Next else Swipe.Previous
        val fast = abs(velocity) >= SWIPE_FLING && (velocity < 0f) == (offset < 0f)
        val far = share >= SWIPE_DISTANCE || (fast && share >= SWIPE_MINIMUM)
        return when {
            !far -> Swipe.Stay
            toward == Swipe.Next && hasNext -> Swipe.Next
            toward == Swipe.Previous && hasPrevious -> Swipe.Previous
            else -> Swipe.Stay
        }
    }

    /** How far a swipe toward a track that does not exist follows the finger, so that the bar resists. */
    const val RESISTANCE = 0.3f
}
