package io.github.yyan115.opentune

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.yyan115.opentune.ui.VISUALIZER_COVER_ALPHA
import io.github.yyan115.opentune.ui.VISUALIZER_MARKER_ALPHA
import io.github.yyan115.opentune.ui.VISUALIZER_TIP_ALPHA
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bars use the accent of the theme and the markers use its text colour, so that both can be seen in every theme. */
class VisualizerThemesTest {
    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(a.luminance(), b.luminance()).toDouble(); val low = minOf(a.luminance(), b.luminance()).toDouble()
        return (high + .05) / (low + .05)
    }

    @Test fun everyThemeShowsTheBarsTheirTipsAndTheMarkers() {
        val failures = mutableListOf<String>()
        for (theme in appThemes) {
            val scheme = theme.scheme
            fun check(name: String, foreground: Color, background: Color, minimum: Double) {
                val ratio = contrast(foreground, background)
                if (ratio < minimum) failures += "${theme.id}: $name is %.2f, below %.1f".format(ratio, minimum)
            }
            check("the bars on the background", scheme.primary, scheme.background, 4.5)
            check("the tips of the bars on the background", scheme.primary.copy(alpha = VISUALIZER_TIP_ALPHA).compositeOver(scheme.background), scheme.background, 2.4)
            // The cover shows behind the bars. A white cover and a black cover are the two extremes.
            for ((name, cover) in listOf("white" to Color.White, "black" to Color.Black)) {
                val behind = cover.copy(alpha = VISUALIZER_COVER_ALPHA).compositeOver(scheme.background)
                check("the markers over a $name cover", scheme.onSurface.copy(alpha = VISUALIZER_MARKER_ALPHA).compositeOver(behind), behind, 3.0)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
