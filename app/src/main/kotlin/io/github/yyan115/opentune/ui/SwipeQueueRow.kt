package io.github.yyan115.opentune.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.data.QueueSwipeAction
import io.github.yyan115.opentune.core.Track

fun QueueSwipeAction.labelRes(): Int = when (this) {
    QueueSwipeAction.Ask -> R.string.settings_swipe_ask
    QueueSwipeAction.PlayNext -> R.string.track_play_next
    QueueSwipeAction.AddToQueue -> R.string.track_add_queue
}

/** Shared by browsing screens; previews without a playback owner stay inert. */
val LocalQueueActionLabel = staticCompositionLocalOf { R.string.track_add_queue }
val LocalQueueTrack = staticCompositionLocalOf<((Track) -> Unit)?> { null }

@Composable
internal fun SwipeQueueRow(id: String, onQueue: (() -> Unit)?, content: @Composable () -> Unit) {
    if (onQueue == null) { content(); return }
    val queue by rememberUpdatedState(onQueue)
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var offset by remember(id) { mutableFloatStateOf(0f) }
    var dragging by remember(id) { mutableStateOf(false) }
    val resting by animateFloatAsState(if (dragging) offset else 0f, label = "queue-return")
    val shown = if (dragging) offset else resting
    val label = stringResource(LocalQueueActionLabel.current)
    Box(Modifier.fillMaxWidth().clipToBounds()
        .semantics { customActions = listOf(CustomAccessibilityAction(label) { queue(); true }) }
        .pointerInput(id, threshold) {
            detectHorizontalDragGestures(
                onDragStart = { offset = 0f; dragging = true },
                onDragCancel = { dragging = false },
                onDragEnd = { if (offset >= threshold) queue(); dragging = false },
            ) { change, amount ->
                change.consume()
                val previous = offset
                offset = (offset + amount).coerceIn(0f, threshold * 1.5f)
                if (previous < threshold && offset >= threshold) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }) {
        if (shown > 0f) Box(Modifier.matchParentSize().background(
            if (offset >= threshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.CenterStart) {
            val tint = if (offset >= threshold) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            Column(Modifier.width(120.dp).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (LocalQueueActionLabel.current == R.string.track_play_next) OpenTuneIcons.PlaylistPlay else OpenTuneIcons.QueueMusic, null, tint = tint)
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Box(Modifier.graphicsLayer { translationX = shown }.background(MaterialTheme.colorScheme.background)) { content() }
    }
}
