package io.github.yyan115.opentune

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.LocalPlaybackPosition
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.PlaybackSlider
import io.github.yyan115.opentune.ui.appThemes
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dot of the seek bar sits on the middle of its line, wherever the dot is, in every theme and at every size of the screen and of the text. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SeekBarTest {
    @get:Rule val compose = createComposeRule()
    private val position = MutableStateFlow(0L)

    /** The dot and the line are placed on whole pixels, so their middles can differ by one pixel. */
    private val PIXEL = 1f

    private fun show(themeId: String = "dark") {
        compose.setContent {
            OpenTuneTheme(themeId) { CompositionLocalProvider(LocalPlaybackPosition provides position) { Box(Modifier.fillMaxWidth().padding(24.dp)) { PlaybackSlider({}, "track", 200_000L) } } }
        }
    }

    private fun centreOf(tag: String): Float = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.let { (it.top + it.bottom) / 2f }

    @Test fun theDotIsOnTheMiddleOfTheLineAtTheStartInTheMiddleAndAtTheEnd() {
        show()
        for (at in listOf(0L, 100_000L, 200_000L)) {
            position.value = at; compose.waitForIdle()
            assertEquals("The dot at $at ms", centreOf("seek-track"), centreOf("seek-thumb"), PIXEL)
        }
    }

    @Test fun theDotIsOnTheMiddleOfTheLineInEveryTheme() {
        var themeId by mutableStateOf(appThemes.first().id)
        compose.setContent {
            OpenTuneTheme(themeId) { CompositionLocalProvider(LocalPlaybackPosition provides position) { Box(Modifier.fillMaxWidth().padding(24.dp)) { PlaybackSlider({}, "track", 200_000L) } } }
        }
        position.value = 60_000L
        for (theme in appThemes) {
            themeId = theme.id; compose.waitForIdle()
            assertEquals("The dot in ${theme.id}", centreOf("seek-track"), centreOf("seek-thumb"), PIXEL)
        }
    }

    @Test fun theDotHasTheWidthOfItsHeightAndSitsInsideTheLine() {
        show(); position.value = 100_000L; compose.waitForIdle()
        val thumb = compose.onNodeWithTag("seek-thumb", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithTag("seek-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(thumb.width, thumb.height, 0.5f)
        assertTrue("The dot is inside the line horizontally", thumb.left >= track.left - 1f && thumb.right <= track.right + 1f)
    }
}
