package io.github.yyan115.opentune.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keep both neighboring covers mounted while dragging; change playback only after settling. */
@Composable
internal fun CoverCarousel(
    playback: PlaybackState,
    modifier: Modifier,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    content: @Composable (Track, Boolean) -> Unit,
) {
    val current = playback.track ?: return
    val next = playback.queue.getOrNull(playback.nextIndex)?.takeIf { onNext != null }
    val previous = playback.queue.getOrNull(playback.previousIndex)?.takeIf { onPrevious != null }
    val identities = remember { CoverIdentities() }
    val requested = listOfNotNull(
        CoverPage(playback.index, current, 0),
        previous?.let { CoverPage(playback.previousIndex, it, -1) },
        next?.let { CoverPage(playback.nextIndex, it, 1) },
    )
    val pages = identities.reconcile(requested)
    SideEffect { identities.pages = pages }
    // Reset motion separately from the image subtrees, which follow their queue occurrence.
    val gestureKey = requested.map { it.index to it.track.id }

    val scope = rememberCoroutineScope()
    val nextAction by rememberUpdatedState(onNext)
    val previousAction by rememberUpdatedState(onPrevious)
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    val flingSpeed = with(LocalDensity.current) { 700.dp.toPx() }
    var width by remember { mutableIntStateOf(1) }
    var offset by remember(gestureKey) { mutableFloatStateOf(0f) }
    var committing by remember(gestureKey) { mutableStateOf(false) }
    var animation by remember(gestureKey) { mutableStateOf<Job?>(null) }
    val velocity = remember { VelocityTracker() }
    val stride = width + gap
    DisposableEffect(gestureKey) {
        onDispose { animation?.cancel() }
    }

    fun settle(direction: Int, speed: Float = 0f) {
        animation?.cancel()
        committing = direction != 0
        identities.incoming = pages.firstOrNull { it.position == -direction }?.identity
        animation = scope.launch {
            animate(offset, direction * stride, initialVelocity = speed,
                animationSpec = spring(dampingRatio = 1f, stiffness = 450f)) { value, _ -> offset = value }
            if (direction < 0) nextAction?.invoke()
            if (direction > 0) previousAction?.invoke()
            // A one-item repeat queue does not change occurrence when it restarts.
            if (direction < 0 && playback.nextIndex == playback.index || direction > 0 && playback.previousIndex == playback.index) {
                offset = 0f
                committing = false
            }
        }
    }

    Box(modifier.testTag("player-artwork").clipToBounds().onSizeChanged { width = it.width.coerceAtLeast(1) }
        .pointerInput(width, committing, gestureKey) {
            if (committing) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { animation?.cancel(); velocity.resetTracking() },
                onDragCancel = { settle(0) },
                onDragEnd = {
                    val speed = velocity.calculateVelocity().x
                    val direction = when {
                        next != null && offset < 0f && speed < -flingSpeed -> -1
                        previous != null && offset > 0f && speed > flingSpeed -> 1
                        next != null && offset < -width * .25f && speed <= flingSpeed -> -1
                        previous != null && offset > width * .25f && speed >= -flingSpeed -> 1
                        else -> 0
                    }
                    settle(direction, speed)
                },
            ) { change, amount ->
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                offset = (offset + amount).coerceIn(if (next != null) -stride else 0f, if (previous != null) stride else 0f)
            }
        }) {
        for (page in pages) key(page.identity) {
            val tag = when (page.position) {
                -1 -> "cover-previous"
                1 -> "cover-next"
                else -> "cover-current"
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = offset + page.position * stride
            }.testTag(tag)) { content(page.track, page.position == 0) }
        }
    }
}

private data class CoverPage(val index: Int, val track: Track, val position: Int, val identity: Any = Any())

/** A repeated queue occurrence can be visible on both sides; preserve the side actually swiped in. */
private class CoverIdentities {
    var pages = emptyList<CoverPage>()
    var incoming: Any? = null

    fun reconcile(requested: List<CoverPage>): List<CoverPage> {
        val available = pages.toMutableList()
        val oldCurrent = pages.firstOrNull { it.position == 0 }
        return requested.map { page ->
            val matches = available.filter { it.index == page.index && it.track.id == page.track.id }
            val changedCurrent = page.position == 0 &&
                (oldCurrent?.index != page.index || oldCurrent.track.id != page.track.id)
            val existing = (if (changedCurrent) matches.firstOrNull { it.identity === incoming } else null)
                ?: matches.firstOrNull { it.position == page.position }
                ?: matches.firstOrNull()
            available.remove(existing)
            page.copy(identity = existing?.identity ?: page.identity)
        }
    }
}
