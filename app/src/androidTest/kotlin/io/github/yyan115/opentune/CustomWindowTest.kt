package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.CustomWindow
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The fields of a custom time window sit side by side, and one under the other when the text is large. Their digits are never cut off. */
@RunWith(AndroidJUnit4::class)
class CustomWindowTest {
    @get:Rule val compose = createComposeRule()
    private val tags = listOf("repeats-days", "repeats-hours", "repeats-minutes")

    private fun show(minutes: Int, fontScale: Float, themeId: String = "dark") {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OpenTuneTheme(themeId) { Surface(color = MaterialTheme.colorScheme.background) { CustomWindow(minutes, {}) } }
            }
        }
    }

    private fun boundsOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun digitsAreWhole(tag: String): Boolean {
        val results = ArrayList<TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.isNotEmpty() && results.all { !it.didOverflowWidth && it.lineCount == 1 }
    }

    @Test fun theFieldsStandSideBySideAtTheNormalTextSize() {
        show(minutes = 2 * 1440 + 30, fontScale = 1f)
        val boxes = tags.map(::boundsOf)
        assertEquals("The fields are in one row", 1, boxes.map { it.top }.distinct().size)
        assertTrue("The fields are in order, from left to right", boxes.zipWithNext().all { (a, b) -> a.right <= b.left })
        tags.forEach { assertTrue("$it shows its digits", digitsAreWhole(it)) }
    }

    @Test fun theFieldsStandOneUnderTheOtherAtTheLargestTextSize() {
        show(minutes = 365 * 1440, fontScale = 2f)
        val boxes = tags.map(::boundsOf)
        assertEquals("The fields have the same left edge", 1, boxes.map { it.left }.distinct().size)
        assertTrue("The fields are in order, from top to bottom", boxes.zipWithNext().all { (a, b) -> a.bottom <= b.top })
        tags.forEach { assertTrue("$it shows its digits", digitsAreWhole(it)) }
    }

    @Test fun theDigitsShowWholeInEveryThemeAtTheLargestTextSize() {
        val theme = mutableStateOf(appThemes.first().id)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) { OpenTuneTheme(theme.value) { Surface(color = MaterialTheme.colorScheme.background) { CustomWindow(9999 % 1440 + 1440, {}) } } }
        }
        for (item in appThemes) {
            theme.value = item.id; compose.waitForIdle()
            tags.forEach { assertTrue("$it shows its digits in ${item.id}", digitsAreWhole(it)) }
            val problems = Readability.problems(compose)
            assertTrue("The fields are hard to read in ${item.id}: $problems", problems.isEmpty())
        }
    }
}
