package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.LocalQueueTrack
import io.github.yyan115.opentune.ui.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeQueueTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipingQueuesOnceWithoutPlayingAndTheRowCanBeUsedAgain() {
        var queued = 0
        var played = 0
        val track = Track("swipe-test", "Swipe Track", "Artist", "Album")
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalQueueTrack provides { queued++ }) {
                    TrackRow(track, onPlay = { played++ }, onMenu = {})
                }
            }
        }
        val row = compose.onNodeWithText("Swipe Track")
        row.performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, queued); assertEquals(0, played) }
        row.assertIsDisplayed()
        row.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, queued) }
        row.performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(2, queued) }
        row.performClick()
        compose.runOnIdle { assertEquals(1, played) }
    }

    @Test fun shortDragDoesNotQueueOrPlay() {
        var actions = 0
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                CompositionLocalProvider(LocalQueueTrack provides { actions++ }) {
                    TrackRow(Track("short", "Short Swipe", "Artist", "Album"), onPlay = { actions++ }, onMenu = {})
                }
            }
        }
        compose.onNodeWithText("Short Swipe").performTouchInput {
            swipe(center, center + Offset(48f * density, 0f))
        }
        compose.runOnIdle { assertEquals(0, actions) }
    }
}
