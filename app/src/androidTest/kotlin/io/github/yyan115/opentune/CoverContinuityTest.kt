package io.github.yyan115.opentune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.CoverCarousel
import io.github.yyan115.opentune.ui.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theLoadedPreviewSurvivesBecomingCurrentAndGoingBack() {
        val tracks = (0..2).map { Track("cover-$it", "Track $it", "Artist") }
        var playback by mutableStateOf(PlaybackState(queue = tracks, nextIndex = 1))
        val mounts = mutableMapOf<String, Int>()
        val disposals = mutableMapOf<String, Int>()
        compose.setContent {
            MaterialTheme {
                CoverCarousel(playback, Modifier.size(240.dp),
                    onNext = { playback = playback.copy(index = 1, previousIndex = 0, nextIndex = 2) },
                    onPrevious = { playback = playback.copy(index = 0, previousIndex = -1, nextIndex = 1) }) { track, _ ->
                    // Recreating this subtree also recreates Artwork's remembered image request/painter.
                    DisposableEffect(Unit) {
                        mounts[track.id] = mounts.getOrDefault(track.id, 0) + 1
                        onDispose { disposals[track.id] = disposals.getOrDefault(track.id, 0) + 1 }
                    }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(1, playback.index)
            assertEquals("The incoming image must not reload", 1, mounts[tracks[1].id])
            assertEquals(0, disposals.getOrDefault(tracks[1].id, 0))
        }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(0, playback.index)
            assertEquals("The previous image must stay loaded too", 1, mounts[tracks[0].id])
            assertEquals(0, disposals.getOrDefault(tracks[0].id, 0))
        }
    }

    @Test fun repeatQueueKeepsTheExactPreviewOnEitherSide() {
        val tracks = List(2) { Track("same-song", "Track", "Artist") }
        var playback by mutableStateOf(PlaybackState(queue = tracks, previousIndex = 1, nextIndex = 1))
        var imageNumber = 0
        compose.setContent {
            MaterialTheme {
                val advance = {
                    val index = 1 - playback.index
                    playback = playback.copy(index = index, previousIndex = 1 - index, nextIndex = 1 - index)
                }
                CoverCarousel(playback, Modifier.size(240.dp), onNext = advance, onPrevious = advance) { _, _ ->
                    val image = remember { ++imageNumber }
                    Box(Modifier.fillMaxSize().testTag("image-$image"))
                }
            }
        }
        fun imageAt(position: String) = compose.onNodeWithTag("cover-$position")
            .onChild().fetchSemanticsNode().config[SemanticsProperties.TestTag]
        val nextImage = imageAt("next")
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeLeft() }
        assertEquals(nextImage, imageAt("current"))
        val previousImage = imageAt("previous")
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeRight() }
        assertEquals(previousImage, imageAt("current"))
    }

}
