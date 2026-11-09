package io.github.yyan115.opentune.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One continuous position for both player sizes, in the same window and pointer stream. */
@Stable
class PlayerExpansion(private val scope: CoroutineScope, initialExpanded: Boolean, private val saveTarget: (Boolean) -> Unit) {
    var fraction by mutableFloatStateOf(if (initialExpanded) 1f else 0f)
        private set
    var travel by mutableFloatStateOf(1f)
    var flingThreshold by mutableFloatStateOf(1000f)
    private var animation: Job? = null
    private var dragOrigin = fraction
    val visible get() = fraction > 0f
    val offset get() = travel * (1f - fraction)

    fun startDrag() { animation?.cancel(); dragOrigin = fraction }
    fun dragBy(pixels: Float) { fraction = (fraction - pixels / travel.coerceAtLeast(1f)).coerceIn(0f, 1f) }
    fun settle(velocity: Float) {
        val expanded = when {
            velocity < -flingThreshold -> true
            velocity > flingThreshold -> false
            fraction - dragOrigin > .12f -> true
            dragOrigin - fraction > .12f -> false
            else -> fraction >= .5f
        }
        moveTo(expanded, velocity)
    }
    fun open() = moveTo(true)
    fun close() = moveTo(false)
    private fun moveTo(expanded: Boolean, velocity: Float = 0f) {
        animation?.cancel()
        saveTarget(expanded)
        animation = scope.launch {
            animate(fraction, if (expanded) 1f else 0f, initialVelocity = -velocity / travel.coerceAtLeast(1f),
                animationSpec = spring(dampingRatio = 1f, stiffness = 500f)) { value, _ -> fraction = value.coerceIn(0f, 1f) }
        }
    }
}

@Composable
fun rememberPlayerExpansion(initialExpanded: Boolean): PlayerExpansion {
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerExpansion(scope, expanded) { expanded = it } }
}

@Composable
fun Modifier.playerDrag(expansion: PlayerExpansion): Modifier = draggable(
    state = rememberDraggableState(expansion::dragBy), orientation = Orientation.Vertical,
    onDragStarted = { expansion.startDrag() }, onDragStopped = { expansion.settle(it) },
)
