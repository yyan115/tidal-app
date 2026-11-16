package io.github.yyan115.opentune

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dialog sits on a raised surface, and its main action is a filled button in the accent or, for a deletion, the error colour. */
class DialogColoursTest {
    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(a.luminance(), b.luminance()).toDouble(); val low = minOf(a.luminance(), b.luminance()).toDouble()
        return (high + .05) / (low + .05)
    }

    @Test fun everyThemeShowsReadableTextOnTheDialogAndItsButtons() {
        val failures = mutableListOf<String>()
        for (theme in appThemes) {
            val scheme = theme.scheme
            fun check(name: String, foreground: Color, background: Color, minimum: Double) {
                val ratio = contrast(foreground, background)
                if (ratio < minimum) failures += "${theme.id}: $name is %.2f, below %.1f".format(ratio, minimum)
            }
            check("the title on the dialog", scheme.onSurface, scheme.surfaceContainerHigh, 7.0)
            check("the text on the dialog", scheme.onSurfaceVariant, scheme.surfaceContainerHigh, 4.5)
            check("the label of the main button", scheme.onPrimary, scheme.primary, 4.5)
            check("the main button on the dialog", scheme.primary, scheme.surfaceContainerHigh, 3.0)
            check("the label of a deleting button", scheme.onError, scheme.error, 4.5)
            check("a deleting button on the dialog", scheme.error, scheme.surfaceContainerHigh, 3.0)
            check("the label of Cancel", scheme.primary, scheme.surfaceContainerHigh, 4.0)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
