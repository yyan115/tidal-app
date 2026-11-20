package io.github.yyan115.opentune

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.data.Settings
import io.github.yyan115.opentune.playback.EqualizerInfo
import io.github.yyan115.opentune.ui.EqualizerSheet
import io.github.yyan115.opentune.ui.OpenTuneTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The list of presets shows four entries and part of the fifth, so that the listener sees that the list scrolls.
 * This holds on a small and on a large screen and at every size of the text, because the height of the list follows the height of an entry.
 */
@RunWith(AndroidJUnit4::class)
class EqualizerSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun check(density: Float, fontScale: Float) {
        val info = EqualizerInfo.read()
        assumeTrue("This device has no equalizer with enough presets", info != null && info.presets.size >= 6)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) { OpenTuneTheme("dark") { EqualizerSheet(Settings(equalizer = true, equalizerUsesCustom = true), {}, {}) } }
        }
        compose.onNode(hasText("Custom") and hasClickAction()).performClick()
        compose.waitForIdle()
        // The first entry is "Flat", and the presets of the device follow it. The text of an entry is in the middle of its row, so an entry that
        // is cut off by a few pixels shows no text at all. The fifth entry has to show a good part of its text, and not all of it.
        // The texts of the entries in the menu, top to bottom. The bounds of a text that is clipped are the part that shows, and are empty when nothing shows.
        val menu = compose.onNode(isPopup(), useUnmergedTree = true).fetchSemanticsNode()
        fun texts(node: SemanticsNode): List<SemanticsNode> = (if (node.config.contains(SemanticsProperties.Text)) listOf(node) else emptyList()) + node.children.flatMap(::texts)
        val boxes = texts(menu).map { it.boundsInRoot }
        val full = boxes.first().height
        val shown = boxes.map { it.height / full }
        val whole = shown.takeWhile { it >= 0.999f }.size
        val part = shown.getOrNull(whole) ?: 0f
        assertTrue("Four entries show whole, and $whole do ($shown)", whole >= 4)
        assertTrue("The next entry shows in part, and shows $part of its text ($shown)", part in 0.15f..0.85f)
    }

    /** The labels of the bands show whole in one line, and the sliders start and end at the same place in every row. */
    private fun checkLabels(density: Float, fontScale: Float) {
        assumeTrue("This device has no equalizer", EqualizerInfo.read() != null)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) { OpenTuneTheme("dark") { EqualizerSheet(Settings(equalizer = true), {}, {}) } }
        }
        compose.waitForIdle()
        val label = Regex("""\d+ ?k?Hz|[+-]?\d+\.\d dB""")
        val labels = compose.onAllNodes(SemanticsMatcher("is a band label") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { label.matches(it.text) } == true }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("The band labels are on the screen", labels.size >= 6)
        labels.forEach { node ->
            val layout = ArrayList<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.first()
            assertTrue("\"${node.config[SemanticsProperties.Text].joinToString()}\" is on one line and not cut off", layout.lineCount == 1 && !layout.didOverflowWidth)
        }
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("The sliders start at the same place", sliders.map { it.left }.distinct().size == 1 && sliders.map { it.right }.distinct().size == 1)
    }

    @Test fun labelsFitOnASmallScreen() = checkLabels(1.5f, 1f)
    @Test fun labelsFitWithTheLargestText() = checkLabels(2.6f, 2f)
    @Test fun labelsFitWithTheLargestTextOnASmallScreen() = checkLabels(1.5f, 2f)

    @Test fun onASmallScreen() = check(1.5f, 1f)
    @Test fun onANormalScreen() = check(2.6f, 1f)
    @Test fun onALargeScreen() = check(3.5f, 1f)
    @Test fun withSmallText() = check(2.6f, 0.85f)
    @Test fun withLargeText() = check(2.6f, 1.5f)
    @Test fun withTheLargestText() = check(2.6f, 2f)
}
