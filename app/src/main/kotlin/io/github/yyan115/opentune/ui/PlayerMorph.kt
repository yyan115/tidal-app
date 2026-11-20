package io.github.yyan115.opentune.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.lerp

/**
 * How the full player grows out of the small player and shrinks back into it. The numbers are the same for a drag of a finger and for an
 * animation, so the player follows a finger exactly and a button gives the same motion. [progress] is 0 for the small player and 1 for the full screen.
 */
object PlayerMorph {
    /** The surface of the player is solid from this progress on. Before it, the small player shows through. */
    private const val SURFACE_SOLID_AT = 0.25f

    /** The content of the full player starts to show at this progress. */
    private const val CONTENT_FROM = 0.35f

    /** The part of the screen that the player covers. When the small player is not known, the player grows from the bottom edge of the screen. */
    fun rect(progress: Float, mini: Rect, screen: Rect): Rect {
        val from = if (mini.isEmpty) Rect(screen.left, screen.bottom, screen.right, screen.bottom) else mini
        return lerp(from, screen, progress.coerceIn(0f, 1f))
    }

    /** The radius of the corners, from the radius of the small player to none at the full screen. */
    fun radius(progress: Float, miniRadius: Float): Float = miniRadius * (1f - progress.coerceIn(0f, 1f))

    /** The shape of the player: the rectangle [covered] by it, with corners of [radius] pixels. */
    fun shape(covered: Rect, radius: Float): Shape = Morphed(covered, radius)

    private class Morphed(private val rect: Rect, private val radius: Float) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
    }

    fun surfaceAlpha(progress: Float): Float = (progress / SURFACE_SOLID_AT).coerceIn(0f, 1f)

    fun contentAlpha(progress: Float): Float = ((progress - CONTENT_FROM) / (1f - CONTENT_FROM)).coerceIn(0f, 1f)
}
