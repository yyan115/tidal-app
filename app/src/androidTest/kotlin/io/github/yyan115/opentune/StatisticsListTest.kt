package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.LibraryStore.PlayCount
import io.github.yyan115.opentune.ui.ListeningStats
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.StatisticsLists
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The listening statistics show every track and every artist, in two lists, and a long name never reaches the number of plays. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class StatisticsListTest {
    @get:Rule val compose = createComposeRule()

    private fun stats(count: Int): ListeningStats {
        val tracks = (1..count).map { PlayCount(Track("t$it", "Song $it", "Artist ${(it - 1) / 3 + 1}", "Album"), count - it + 1, 60_000L) }
        val artists = tracks.groupBy { it.track.artist }.map { (name, entries) -> name to entries.sumOf { it.plays } }.sortedByDescending { it.second }
        return ListeningStats(tracks.sumOf { it.plays }, 12, 30, tracks, artists)
    }

    private fun show(stats: ListeningStats, fontScale: Float = 1f, themeId: String = "dark") {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OpenTuneTheme(themeId) { Surface(color = MaterialTheme.colorScheme.background) { StatisticsLists(stats) } }
            }
        }
    }

    @Test fun everyTrackIsInTheList() {
        show(stats(3000))
        compose.onNodeWithTag("stats-list").performScrollToNode(hasText("Song 3000"))
        compose.onNodeWithText("Song 3000").assertIsDisplayed()
    }

    @Test fun everyArtistIsInTheOtherList() {
        show(stats(3000))
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithTag("stats-list").performScrollToNode(hasText("Artist 1000"))
        compose.onNodeWithText("Artist 1000").assertIsDisplayed()
        compose.onAllNodesWithText("Song 1").assertCountEquals(0)
    }

    @Test fun theListsShowAnOrderNumberTheNameAndTheNumberOfPlays() {
        show(stats(5))
        compose.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Song 1").assertIsDisplayed()
        compose.onNodeWithText("5×").assertIsDisplayed()
    }

    @Test fun aLongNameIsCutBeforeTheNumberOfPlays() {
        val long = stats(3).let { s -> s.copy(tracks = s.tracks.map { it.copy(track = it.track.copy(title = "A title that is far too long to fit in the row of the list ".repeat(3)), plays = 1234) }) }
        for (scale in listOf(1f, 2f)) {
            show(long, scale)
            val title = compose.onAllNodesWithText("A title", substring = true).onFirst().fetchSemanticsNode().boundsInRoot
            val plays = compose.onAllNodesWithText("1234×").onFirst().fetchSemanticsNode().boundsInRoot
            assertTrue("The name ends at ${title.right} and the number starts at ${plays.left} at text scale $scale", title.right <= plays.left)
            break
        }
    }

    @Test fun theListsCanBeReadInEveryTheme() {
        val theme = androidx.compose.runtime.mutableStateOf(appThemes.first().id)
        compose.setContent { OpenTuneTheme(theme.value) { Surface(color = MaterialTheme.colorScheme.background) { StatisticsLists(stats(40)) } } }
        for (item in appThemes) {
            theme.value = item.id; compose.waitForIdle()
            val problems = Readability.problems(compose)
            assertTrue("The statistics are hard to read in ${item.id}: $problems", problems.isEmpty())
        }
    }
}
