package io.github.yyan115.opentune

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.CurrentDownloadCard
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The card of the download that runs shows the track, the progress and the downloads that wait. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CurrentDownloadCardTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("tidal:1", "A Long Song Title That Does Not Fit On One Line Of The Card", "Artist", "Album")
    private var opened = 0

    private fun show(percent: Int, waiting: Int, themeId: String = "dark", fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OpenTuneTheme(themeId) { CurrentDownloadCard(track, percent, waiting) { opened++ } }
            }
        }
    }

    @Test fun theCardNamesTheTrackAndTheProgress() {
        show(42, 0)
        compose.onNodeWithText("Downloading · 42%").assertIsDisplayed()
        compose.onNodeWithText(track.title).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(.42f, 0f..1f))).assertExists()
    }

    @Test fun theCardCountsTheDownloadsThatWait() {
        show(10, 3)
        compose.onNodeWithText("Downloading · 10% · 3 waiting").assertIsDisplayed()
    }

    @Test fun theCardSaysNothingAboutWaitingWhenNothingWaits() {
        show(10, 0)
        compose.onAllNodesWithText("waiting", substring = true).assertCountEquals(0)
    }

    @Test fun aTapOpensTheQueue() {
        show(5, 1)
        compose.onNodeWithTag("current-download").performClick()
        assertEquals(1, opened)
    }

    @Test fun theCardCanBeReadInEveryThemeAndAtTheLargestTextSize() {
        val theme = mutableStateOf(appThemes.first().id)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                OpenTuneTheme(theme.value) { CurrentDownloadCard(track, 64, 12) { } }
            }
        }
        for (item in appThemes) {
            theme.value = item.id; compose.waitForIdle()
            val problems = Readability.problems(compose)
            assertTrue("The card is hard to read in ${item.id}: $problems", problems.isEmpty())
        }
        compose.onNodeWithText("Downloading · 64% · 12 waiting").assertIsDisplayed()
    }

    @Test fun theBarHasNoDotAtItsEnd() {
        show(40, 0)
        compose.waitForIdle()
        val image = compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(.4f, 0f..1f))).captureToImage().asAndroidBitmap()
        val y = image.height / 2
        // The part of the track that is not filled has one colour from its middle to its end. A dot at the end would break it.
        val middle = image.getPixel((image.width * 0.7f).toInt(), y)
        val end = image.getPixel(image.width - 3, y)
        assertEquals("The end of the bar is the colour of the rest of the track", middle, end)
    }
}
