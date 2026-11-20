package io.github.yyan115.opentune

import androidx.compose.ui.geometry.Rect
import io.github.yyan115.opentune.ui.PlayerMorph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMorphTest {
    private val screen = Rect(0f, 0f, 1080f, 2400f)
    private val mini = Rect(48f, 2100f, 1032f, 2250f)
    private val steps = (0..20).map { it / 20f }

    @Test fun theFullPlayerStartsAsTheSmallPlayerAndEndsAsTheScreen() {
        assertEquals(mini, PlayerMorph.rect(0f, mini, screen))
        assertEquals(screen, PlayerMorph.rect(1f, mini, screen))
        assertEquals(0f, PlayerMorph.radius(1f, 20f), 0f)
        assertEquals(20f, PlayerMorph.radius(0f, 20f), 0f)
    }

    @Test fun everyEdgeMovesOnlyOutwardWhileThePlayerOpens() {
        val rects = steps.map { PlayerMorph.rect(it, mini, screen) }
        rects.zipWithNext { a, b ->
            assertTrue(b.top <= a.top && b.left <= a.left && b.right >= a.right && b.bottom >= a.bottom)
        }
        assertTrue(rects.all { it.top >= screen.top && it.bottom <= screen.bottom && it.left >= screen.left && it.right <= screen.right })
    }

    @Test fun theRectIsBetweenTheSmallPlayerAndTheScreenAtHalfWay() {
        val half = PlayerMorph.rect(.5f, mini, screen)
        assertEquals((mini.top + screen.top) / 2f, half.top, 0.001f)
        assertEquals((mini.bottom + screen.bottom) / 2f, half.bottom, 0.001f)
    }

    @Test fun theSurfaceAppearsFirstAndTheContentFollows() {
        assertEquals(0f, PlayerMorph.surfaceAlpha(0f), 0f)
        assertEquals(1f, PlayerMorph.surfaceAlpha(1f), 0f)
        assertEquals(0f, PlayerMorph.contentAlpha(0f), 0f)
        assertEquals(1f, PlayerMorph.contentAlpha(1f), 0f)
        assertTrue(steps.all { PlayerMorph.surfaceAlpha(it) >= PlayerMorph.contentAlpha(it) })
        steps.zipWithNext { a, b ->
            assertTrue(PlayerMorph.surfaceAlpha(b) >= PlayerMorph.surfaceAlpha(a))
            assertTrue(PlayerMorph.contentAlpha(b) >= PlayerMorph.contentAlpha(a))
        }
    }

    @Test fun withoutAKnownSmallPlayerTheScreenGrowsFromItsBottomEdge() {
        val start = PlayerMorph.rect(0f, Rect.Zero, screen)
        assertEquals(screen.bottom, start.top, 0f)
        assertEquals(screen.bottom, start.bottom, 0f)
        assertEquals(screen, PlayerMorph.rect(1f, Rect.Zero, screen))
    }

    @Test fun anOverdrawnProgressStaysInsideTheScreen() {
        assertEquals(screen, PlayerMorph.rect(1.4f, mini, screen))
        assertEquals(mini, PlayerMorph.rect(-.2f, mini, screen))
    }
}
