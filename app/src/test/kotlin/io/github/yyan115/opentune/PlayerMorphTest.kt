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

    private val thumb = Rect(80f, 2120f, 200f, 2240f)
    private val cover = Rect(60f, 500f, 1020f, 1460f)

    @Test fun theCoverStartsOnTheSmallPictureAndEndsInItsOwnPlace() {
        val start = PlayerMorph.cover(0f, thumb, cover)
        assertEquals(thumb.width / cover.width, start.scale, 0.0001f)
        assertEquals(thumb.center.x, cover.center.x + start.dx, 0.01f)
        assertEquals(thumb.center.y, cover.center.y + start.dy, 0.01f)
        assertEquals(PlayerMorph.CoverPlace(1f, 0f, 0f), PlayerMorph.cover(1f, thumb, cover))
    }

    @Test fun theCoverGrowsAndMovesInOneDirectionOnly() {
        val places = steps.map { PlayerMorph.cover(it, thumb, cover) }
        places.zipWithNext { a, b ->
            assertTrue(b.scale >= a.scale)
            assertTrue(Math.abs(b.dx) <= Math.abs(a.dx) + 0.001f && Math.abs(b.dy) <= Math.abs(a.dy) + 0.001f)
        }
    }

    @Test fun withoutKnownPlacesTheCoverStaysWhereItIs() {
        assertEquals(PlayerMorph.CoverPlace(1f, 0f, 0f), PlayerMorph.cover(0f, Rect.Zero, cover))
        assertEquals(PlayerMorph.CoverPlace(1f, 0f, 0f), PlayerMorph.cover(0f, thumb, Rect.Zero))
    }
}
