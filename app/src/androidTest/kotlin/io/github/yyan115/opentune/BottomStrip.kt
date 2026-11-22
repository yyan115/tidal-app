package io.github.yyan115.opentune

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The strip of the screen behind the three buttons of the system. It has the colour of the screen above it and no band of another colour, whatever
 * the screen is. A band shows where the system draws a veil over the strip, or where a window does not reach the bottom edge.
 */
object BottomStrip {
    /** Switches the phone to the three-button navigation, which draws a bar of its own, and returns what was set before. */
    fun useThreeButtons(): String {
        val before = shell("cmd overlay list android").lines().firstOrNull { it.startsWith("[x]") && it.contains("navbar") }?.substringAfter("[x] ")?.trim().orEmpty()
        shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton")
        return before
    }

    fun restore(before: String) { if (before.isNotEmpty()) shell("cmd overlay enable-exclusive --category $before") }

    fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }

    /** The colour that most pixels of the rows from [from] to [to] have. The icons of the buttons are few, so they do not decide. */
    fun dominant(bitmap: Bitmap, from: Int, to: Int): Int {
        val counts = HashMap<Int, IntArray>()
        val width = bitmap.width
        val row = IntArray(width)
        for (y in from until to) {
            bitmap.getPixels(row, 0, width, 0, 0, width, 1)
            for (pixel in row) {
                val key = (Color.red(pixel) shr 2 shl 16) or (Color.green(pixel) shr 2 shl 8) or (Color.blue(pixel) shr 2)
                val entry = counts.getOrPut(key) { IntArray(4) }
                entry[0]++; entry[1] += Color.red(pixel); entry[2] += Color.green(pixel); entry[3] += Color.blue(pixel)
            }
        }
        val best = counts.values.maxByOrNull { it[0] } ?: return Color.TRANSPARENT
        return Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    }

    /** A description of the band, or null when the strip has the colour of the screen above it. */
    fun problem(bitmap: Bitmap, navigationHeight: Int, tolerance: Int = 10): String? {
        if (navigationHeight <= 0 || navigationHeight >= bitmap.height / 4) return null
        val top = bitmap.height - navigationHeight
        val strip = dominant(bitmap, top, bitmap.height)
        val above = dominant(bitmap, top - 24, top - 2)
        val gap = maxOf(Math.abs(Color.red(strip) - Color.red(above)), Math.abs(Color.green(strip) - Color.green(above)), Math.abs(Color.blue(strip) - Color.blue(above)))
        return if (gap > tolerance) "the strip behind the buttons is #%06X and the screen above it is #%06X".format(strip and 0xFFFFFF, above and 0xFFFFFF) else null
    }
}
