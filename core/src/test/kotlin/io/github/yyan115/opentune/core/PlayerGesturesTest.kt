package io.github.yyan115.opentune.core

import io.github.yyan115.opentune.core.PlayerGestures.Swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturesTest {
    @Test fun aSlowDragEndsOnTheNearerSideOfTheMiddle() {
        assertTrue(PlayerGestures.opens(0.6f, 0f))
        assertFalse(PlayerGestures.opens(0.4f, 0f))
        assertTrue("Exactly halfway counts as open", PlayerGestures.opens(0.5f, 0f))
    }

    @Test fun aFastFlingGoesTheWayOfTheFingerFromAnywhere() {
        assertTrue("A flick upward from a small drag opens the player", PlayerGestures.opens(0.1f, 0.8f))
        assertFalse("A flick downward from a long drag closes it", PlayerGestures.opens(0.9f, -0.8f))
    }

    @Test fun aSlowMovementBelowTheFlingSpeedDoesNotCountAsAFling() {
        assertFalse(PlayerGestures.opens(0.4f, PlayerGestures.SHEET_FLING - 0.01f))
        assertTrue(PlayerGestures.opens(0.6f, -(PlayerGestures.SHEET_FLING - 0.01f)))
    }

    @Test fun aLongSwipeToTheLeftPlaysTheNextTrackAndToTheRightThePreviousOne() {
        assertEquals(Swipe.Next, PlayerGestures.swipe(-120f, 0f, 360f, hasNext = true, hasPrevious = true))
        assertEquals(Swipe.Previous, PlayerGestures.swipe(120f, 0f, 360f, hasNext = true, hasPrevious = true))
    }

    @Test fun aShortSlowSwipeStays() {
        assertEquals(Swipe.Stay, PlayerGestures.swipe(-60f, 0.2f, 360f, hasNext = true, hasPrevious = true))
        assertEquals(Swipe.Stay, PlayerGestures.swipe(60f, -0.2f, 360f, hasNext = true, hasPrevious = true))
    }

    @Test fun aFastFlickChangesTheTrackAfterAShortMove() {
        assertEquals(Swipe.Next, PlayerGestures.swipe(-50f, -2.5f, 360f, hasNext = true, hasPrevious = true))
        assertEquals(Swipe.Previous, PlayerGestures.swipe(50f, 2.5f, 360f, hasNext = true, hasPrevious = true))
    }

    @Test fun aFlickThatBarelyMovedIsATapAndNotASwipe() {
        assertEquals(Swipe.Stay, PlayerGestures.swipe(-10f, -3f, 360f, hasNext = true, hasPrevious = true))
    }

    @Test fun aFlickBackAgainstTheMoveIsNotASwipe() {
        // The finger moved left and then flicked right, so the move to the left alone is too short to count.
        assertEquals(Swipe.Stay, PlayerGestures.swipe(-50f, 2.5f, 360f, hasNext = true, hasPrevious = true))
    }

    @Test fun aSwipeTowardATrackThatDoesNotExistStays() {
        assertEquals(Swipe.Stay, PlayerGestures.swipe(-200f, -3f, 360f, hasNext = false, hasPrevious = true))
        assertEquals(Swipe.Stay, PlayerGestures.swipe(200f, 3f, 360f, hasNext = true, hasPrevious = false))
    }

    @Test fun anUnknownWidthNeverChangesTheTrack() {
        assertEquals(Swipe.Stay, PlayerGestures.swipe(-200f, -3f, 0f, hasNext = true, hasPrevious = true))
    }
}
