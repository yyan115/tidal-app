package io.github.yyan115.opentune.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import io.github.yyan115.opentune.core.PlayerGestures
import io.github.yyan115.opentune.core.PlayerGestures.Swipe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What a swipe on the cover or on the small player can do: play the next or the previous track, when there is one. */
@Immutable
data class ChangeTrack(val hasNext: Boolean, val hasPrevious: Boolean, val onNext: () -> Unit, val onPrevious: () -> Unit)

/**
 * Lets a swipe to the left play the next track and a swipe to the right play the previous one. The content follows the finger.
 * After a change the content of the new track slides in from the side that the swipe came from, once [trackKey] shows the new track.
 * A swipe toward a track that does not exist resists and returns.
 */
@Composable
fun SwipeToChangeTrack(trackKey: Any?, change: ChangeTrack, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    // The side that the new track comes from, as -1 or 1. It is 0 while no change is waiting for the new track.
    var entering by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(trackKey) {
        if (entering != 0f) {
            val side = entering
            entering = 0f
            offset.snapTo(side * width)
            offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
        }
    }
    Box(modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer { translationX = offset.value; alpha = 1f - .6f * (abs(offset.value) / width).coerceIn(0f, 1f) }
        .draggable(rememberDraggableState { delta ->
            val next = offset.value + delta
            val missing = (next < 0f && !change.hasNext) || (next > 0f && !change.hasPrevious)
            scope.launch { offset.snapTo(offset.value + if (missing) delta * PlayerGestures.RESISTANCE else delta) }
        }, Orientation.Horizontal, onDragStarted = { offset.stop() }, onDragStopped = { velocity ->
            scope.launch {
                when (PlayerGestures.swipe(offset.value, velocity / width, width.toFloat(), change.hasNext, change.hasPrevious)) {
                    Swipe.Next -> { entering = 1f; offset.animateTo(-width.toFloat(), tween(110)); change.onNext() }
                    Swipe.Previous -> { entering = -1f; offset.animateTo(width.toFloat(), tween(110)); change.onPrevious() }
                    Swipe.Stay -> offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
                }
                // The track normally changes within a moment. If it does not, the content comes back.
                var waited = 0
                while (entering != 0f && waited < 800) { delay(16); waited += 16 }
                if (entering != 0f) { entering = 0f; offset.animateTo(0f) }
            }
        })) { content() }
}
