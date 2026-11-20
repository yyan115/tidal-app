package io.github.yyan115.opentune

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.pow

/**
 * Finds text that is hard to read, whatever the reason: a colour that does not follow the theme, a text outside of a surface, or a text that
 * is hidden behind something. It looks at the picture of the screen, not at the code, so it also finds what nobody thought of.
 * The colour of the background of a text is the colour that most of its pixels have, and the colour of the text is the pixel that differs most.
 */
object Readability {
    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double { val c = value / 255.0; return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel((color shr 16) and 0xFF) + 0.7152 * channel((color shr 8) and 0xFF) + 0.0722 * channel(color and 0xFF)
    }

    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun texts(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
        (if (node.config.contains(SemanticsProperties.Text)) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    /**
     * The texts on the screen whose colour has less contrast than [minimum] with the colour behind them. It looks at a picture of the whole screen,
     * so a dialog or a sheet, which is a window of its own, is looked at in the same way as the rest. A text is found by its place on the screen.
     */
    fun problems(compose: ComposeTestRule, minimum: Double = 2.5, keep: String? = null): List<String> {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (keep != null) {
            val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val directory = (output?.let { java.io.File(it) } ?: instrumentation.targetContext.getExternalFilesDir("readability")!!).apply { mkdirs() }
            java.io.File(directory, "$keep.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
        return (0 until roots.fetchSemanticsNodes().size).flatMap { index ->
            texts(roots[index].fetchSemanticsNode()).mapNotNull { node -> problemOf(node, bitmap, minimum) }
        }
    }

    private fun problemOf(node: androidx.compose.ui.semantics.SemanticsNode, bitmap: android.graphics.Bitmap, minimum: Double): String? {
        if (node.config.contains(SemanticsProperties.InvisibleToUser)) return null
        val (foreground, background) = coloursOf(node, bitmap) ?: return null
        val ratio = contrast(foreground, background)
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        return if (ratio < minimum && text.isNotBlank()) "\"${text.take(40)}\" has contrast %.1f (text #%06X on #%06X)".format(ratio, foreground and 0xFFFFFF, background and 0xFFFFFF) else null
    }

    /** The colour of the text of the node that [matcher] finds, as it is on the screen, and the colour behind it. */
    fun coloursOfText(compose: ComposeTestRule, matcher: androidx.compose.ui.test.SemanticsMatcher): Pair<Int, Int> {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        return coloursOf(compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode(), bitmap) ?: error("The text is not on the screen")
    }

    /** How far two colours are from each other, as the largest difference in one channel. */
    fun distance(first: Int, second: Int): Int = maxOf(kotlin.math.abs(((first shr 16) and 0xFF) - ((second shr 16) and 0xFF)),
        kotlin.math.abs(((first shr 8) and 0xFF) - ((second shr 8) and 0xFF)), kotlin.math.abs((first and 0xFF) - (second and 0xFF)))

    /** The colour of the text and the colour behind it, or null when too little of the text is on the screen to look at. */
    private fun coloursOf(node: androidx.compose.ui.semantics.SemanticsNode, bitmap: android.graphics.Bitmap): Pair<Int, Int>? {
        val origin = node.positionOnScreen
        val bounds = androidx.compose.ui.geometry.Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
        // A text that a list has composed ahead, outside the screen, or that the screen cuts off, has no picture to look at.
        val visibleWidth = minOf(bounds.right, bitmap.width.toFloat()) - maxOf(bounds.left, 0f)
        val visibleHeight = minOf(bounds.bottom, bitmap.height.toFloat()) - maxOf(bounds.top, 0f)
        if (visibleWidth < bounds.width * 0.9f || visibleHeight < bounds.height * 0.9f) return null
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1); val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width); val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 4 || bottom - top < 4) return null
        val width = right - left; val height = bottom - top
        val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, left, top, width, height) }
        val counts = HashMap<Int, Int>()
        pixels.forEach { counts.merge(it and 0xF0F0F0, 1, Int::plus) }
        val dominant = counts.maxByOrNull { entry -> entry.value }!!.key
        val background = pixels.first { (it and 0xF0F0F0) == dominant }
        val foreground = pixels.maxByOrNull { kotlin.math.abs(luminance(it) - luminance(background)) } ?: background
        return foreground to background
    }
}
