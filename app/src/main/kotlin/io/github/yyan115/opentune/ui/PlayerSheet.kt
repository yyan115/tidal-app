package io.github.yyan115.opentune.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.yyan115.opentune.core.PlayerGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far the full player is open, from 0 when it is closed to 1 when it fills the screen.
 * A finger moves it, either by dragging the small player up or by dragging the full player down. It settles when the finger lifts.
 */
@Stable
class PlayerSheet(open: Boolean, private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(if (open) 1f else 0f)
        private set

    /** The height of the screen in pixels. A drag of this length opens or closes the player. */
    var height by mutableFloatStateOf(0f)

    /** The value that the player moves toward or rests at: 0 or 1. */
    var target = progress
        private set
    private var animation: Job? = null

    /** A finger touched the player, so a movement in progress stops. */
    fun dragStarted() { animation?.cancel() }

    /** The finger moved by [delta] pixels, which is positive downward. */
    fun dragBy(delta: Float) { if (height > 0f) progress = (progress - delta / height).coerceIn(0f, 1f) }

    /** The finger lifted at [velocity] pixels per second, positive downward. Returns whether the player ends up open. */
    fun released(velocity: Float): Boolean {
        val perHeight = if (height > 0f) -velocity / height else 0f
        val open = PlayerGestures.opens(progress, perHeight)
        moveTo(if (open) 1f else 0f, perHeight)
        return open
    }

    /** Moves the player to open or closed, so that a button or the Back key opens and closes it with the same motion. */
    fun moveTo(open: Boolean) = moveTo(if (open) 1f else 0f, 0f)

    private fun moveTo(to: Float, velocity: Float) {
        target = to
        animation?.cancel()
        animation = scope.launch { animate(progress, to, velocity, spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.002f)) { value, _ -> progress = value } }
    }

    /** Follows the open state of the app. Nothing moves when the player already goes there. */
    fun follow(open: Boolean) { if (target != (if (open) 1f else 0f)) moveTo(open) }
}
