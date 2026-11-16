package io.github.yyan115.opentune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a swipe on a track does. The first value is the track and the second is true for "Play next" and false for "Add to queue".
 * It is null where a swipe has no meaning, such as in the preview of a theme.
 */
val LocalQueueTrack = staticCompositionLocalOf<((Track, Boolean) -> Unit)?> { null }

private enum class SwipeValue { Closed, Open, Full }

private val ActionWidth = 88.dp

/**
 * A row that slides to the right when it is swiped. A short swipe leaves it open and shows two buttons behind it, "Play next" and "Add to queue".
 * A long swipe plays the track next at once. The row returns by itself after an action, and a tap on an open row closes it.
 * [content] receives a function that wraps the tap of the row, so that the tap closes an open row and does nothing else.
 */
@Composable
fun SwipeToQueueBox(onPlayNext: () -> Unit, onAddToQueue: () -> Unit, modifier: Modifier = Modifier, content: @Composable (guard: (() -> Unit) -> (() -> Unit)) -> Unit) {
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val actionWidth = with(density) { ActionWidth.toPx() }
    val state = remember { AnchoredDraggableState(SwipeValue.Closed) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(0) }
    val close: () -> Unit = { scope.launch { state.animateTo(SwipeValue.Closed) } }
    LaunchedEffect(width, actionWidth, direction) {
        if (width > 0) state.updateAnchors(DraggableAnchors {
            SwipeValue.Closed at 0f
            SwipeValue.Open at direction * actionWidth * 2
            SwipeValue.Full at direction * width
        })
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { value ->
            if (value == SwipeValue.Full) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayNext()
                state.animateTo(SwipeValue.Closed)
            }
        }
    }
    val playNext = stringResource(R.string.track_play_next)
    val addToQueue = stringResource(R.string.track_add_queue)
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width }.clipToBounds()
        .semantics { customActions = listOf(CustomAccessibilityAction(playNext) { onPlayNext(); true }, CustomAccessibilityAction(addToQueue) { onAddToQueue(); true }) }) {
        // The buttons exist only while the row has moved, so that they are not part of a list that stands still.
        val moved by remember { derivedStateOf { state.offset.let { !it.isNaN() && abs(it) > 1f } } }
        if (moved) Row(Modifier.matchParentSize().testTag("swipe-actions"), horizontalArrangement = Arrangement.Start) {
            SwipeAction(OpenTuneIcons.PlaylistPlay, playNext, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { close(); onPlayNext() }
            SwipeAction(OpenTuneIcons.QueueMusic, addToQueue, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) { close(); onAddToQueue() }
        }
        val guard: (() -> Unit) -> (() -> Unit) = { action -> { if (state.targetValue != SwipeValue.Closed) close() else action() } }
        Box(Modifier.offset { IntOffset(state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
            .anchoredDraggable(state, Orientation.Horizontal)
            .background(MaterialTheme.colorScheme.background)) { content(guard) }
    }
}

@Composable
private fun SwipeAction(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Column(Modifier.width(ActionWidth).fillMaxHeight().background(container).clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = content)
        Text(label, Modifier.padding(top = 4.dp), color = content, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
