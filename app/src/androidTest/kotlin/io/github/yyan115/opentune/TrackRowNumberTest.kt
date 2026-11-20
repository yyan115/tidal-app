package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.TrackRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The number of a track in a numbered list is on one line, whatever its length and the size of the text. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TrackRowNumberTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("tidal:1", "A Song With A Rather Long Title To Share The Row", "Artist", "Album", durationMs = 200_000)

    private fun check(index: Int, fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OpenTuneTheme("dark") { Surface(color = MaterialTheme.colorScheme.background) { TrackRow(track, onPlay = {}, onMenu = {}, index = index) } }
            }
        }
        val label = "%02d".format(index + 1)
        val layouts = ArrayList<TextLayoutResult>()
        compose.onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue("$label has a layout", layouts.isNotEmpty())
        assertTrue("$label is on one line and not cut off at scale $fontScale", layouts.all { it.lineCount == 1 && it.getLineRight(0) <= it.size.width + 1f })
    }

    @Test fun twoDigitsAtTheNormalSize() = check(index = 8, fontScale = 1f)
    @Test fun threeDigitsAtTheNormalSize() = check(index = 122, fontScale = 1f)
    @Test fun threeDigitsAtTheLargestSize() = check(index = 122, fontScale = 2f)
    @Test fun fourDigitsAtTheLargestSize() = check(index = 1499, fontScale = 2f)
}
