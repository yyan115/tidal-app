package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.core.Credit
import io.github.yyan115.opentune.core.Contributor
import io.github.yyan115.opentune.core.SearchResult
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A screen that loads data keeps its controls where they are while the data arrives. A control does not appear, vanish or move in the part of the screen
 * above the list, so a page does not flash while it loads. Each sequence shows a screen in one state and then in the next, in the same composition.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ScreenTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Sequence<S>(val name: String, val steps: List<Pair<String, S>>, val stable: List<String>, val render: @Composable (MainViewModel, S) -> Unit)

    private val songs = Fixtures.tracks(30)
    private val release = Fixtures.playlist
    private val sequences: List<Sequence<*>> = listOf(
        Sequence("DetailScreen loading to loaded", listOf("loading" to AppState(detail = release, detailLoading = true), "loaded" to AppState(detail = release, detailTracks = songs)),
            listOf("Play", "Download all tracks")) { vm, s -> DetailScreen(s, vm, { _, _ -> }) },
        Sequence("DetailScreen loading to empty", listOf("loading" to AppState(detail = release, detailLoading = true), "empty" to AppState(detail = release)),
            listOf("Play", "Download all tracks")) { vm, s -> DetailScreen(s, vm, { _, _ -> }) },
        Sequence("DetailScreen loading to failed", listOf("loading" to AppState(detail = release, detailLoading = true), "failed" to AppState(detail = release, error = Fixtures.failure)),
            listOf("Play", "Download all tracks")) { vm, s -> DetailScreen(s, vm, { _, _ -> }) },
        Sequence("DetailScreen loaded to everything on the device", listOf("loaded" to AppState(detail = release, detailTracks = songs), "on the device" to AppState(detail = release, detailTracks = Fixtures.tracks(30, onDevice = 30))),
            listOf("Play", "Download all tracks")) { vm, s -> DetailScreen(s, vm, { _, _ -> }) },
        Sequence("SearchScreen searching to results", listOf("searching" to AppState(signedIn = true, searchQuery = "number", searching = true),
            "results" to AppState(signedIn = true, searchQuery = "number", search = SearchResult(tracks = songs.take(8), albums = Fixtures.albums))), listOf("text field")) { vm, s -> SearchScreen(s, vm, {}, {}) },
        Sequence("SearchScreen searching to no result", listOf("searching" to AppState(signedIn = true, searchQuery = "zzzz", searching = true), "no result" to AppState(signedIn = true, searchQuery = "zzzz")),
            listOf("text field")) { vm, s -> SearchScreen(s, vm, {}, {}) },
        Sequence("CreditsSheet loading to credits", listOf("loading" to CreditsUi("An album", loading = true), "credits" to CreditsUi("An album", loading = false, credits = (1..8).map { Credit("Role $it", listOf(Contributor("Person $it", "$it"))) })),
            listOf("Credits")) { _, s -> CreditsSheet(s, {}, {}) },
        Sequence("CreditsSheet loading to none", listOf("loading" to CreditsUi("An album", loading = true), "none" to CreditsUi("An album", loading = false)), listOf("Credits")) { _, s -> CreditsSheet(s, {}, {}) },
    )

    /** What the screen shows above the list: each named control or title with where it is. */
    private fun controls(): Map<String, Rect> {
        compose.waitForIdle()
        val found = LinkedHashMap<String, Rect>()
        fun walk(node: SemanticsNode) {
            val clickable = node.config.contains(SemanticsActions.OnClick) || node.config.contains(SemanticsActions.SetText)
            val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() }
                // A field that takes text is found as the field, whatever it holds.
                .ifBlank { if (node.config.contains(SemanticsActions.SetText)) "text field" else "" }
            if (clickable && node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty && name.isNotBlank()) found.putIfAbsent(name, node.boundsInRoot)
            // A title is a text that no finger touches, and it too must stay where it is.
            if (!clickable && node.config.contains(SemanticsProperties.Text) && node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty && name.isNotBlank()) found.putIfAbsent(name, node.boundsInRoot)
            node.children.forEach(::walk)
        }
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::walk)
        return found
    }

    private class Shown(val sequence: Sequence<Any?>, val value: Any?)
    private val shown = mutableStateOf<Shown?>(null)
    private var contentSet = false

    @Suppress("UNCHECKED_CAST")
    private fun check(sequence: Sequence<*>): List<String> {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val typed = sequence as Sequence<Any?>
        // One composition shows every sequence. A change of the value keeps the screen where it is, and a change of the sequence builds a new screen.
        if (!contentSet) { contentSet = true; compose.setContent { OpenTuneTheme("dark") { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            shown.value?.let { now -> androidx.compose.runtime.key(now.sequence) { now.sequence.render(model, now.value) } } } } } }
        compose.runOnIdle { shown.value = Shown(typed, typed.steps.first().second) }
        val problems = mutableListOf<String>()
        var before = controls()
        for ((index, step) in typed.steps.withIndex().drop(1)) {
            val fromName = typed.steps[index - 1].first
            compose.runOnIdle { shown.value = Shown(typed, step.second) }
            val after = controls()
            // The controls that are named as stable must be on the screen in both states, and the part above the lowest of them must be the same.
            // A name that is given in part, such as the start of the hint of a field, finds the control that begins with it.
            fun find(map: Map<String, Rect>, name: String) = map[name] ?: map.entries.firstOrNull { it.key.startsWith(name) }?.value
            val zoneBottom = typed.stable.mapNotNull { find(before, it)?.bottom ?: find(after, it)?.bottom }.maxOrNull()
            for (name in typed.stable) {
                if (find(before, name) == null) problems += "${sequence.name}: \"$name\" is not on the screen while it is $fromName, only ${before.keys.take(10)} are"
                if (find(after, name) == null) problems += "${sequence.name}: \"$name\" is not on the screen once it is ${step.first}"
                val a = find(before, name); val b = find(after, name)
                if (a != null && b != null && (kotlin.math.abs(a.top - b.top) > 2f || kotlin.math.abs(a.left - b.left) > 2f)) problems += "${sequence.name}: \"$name\" moves from $a to $b when it goes from $fromName to ${step.first}"
            }
            if (zoneBottom != null) {
                for ((name, rect) in before) if (rect.bottom <= zoneBottom + 1f && name !in after) problems += "${sequence.name}: \"$name\" is above the list and vanishes when the data arrives"
                for ((name, rect) in after) if (rect.bottom <= zoneBottom + 1f && name !in before) problems += "${sequence.name}: \"$name\" is above the list and appears when the data arrives"
            }
            before = after
        }
        return problems
    }

    @Test fun noControlAboveTheListAppearsVanishesOrMovesWhileTheDataArrives() {
        val problems = sequences.flatMap { check(it) }
        assertTrue("A screen flashes while it loads:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test fun theCheckFindsAControlThatAppearsWhenTheDataArrives() {
        // The old page showed Download only after the songs were there. This page does it on purpose, to prove that the check sees it.
        val flashing = Sequence("a page that flashes", listOf("loading" to false, "loaded" to true), listOf("Play")) { _, loaded: Boolean ->
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.Button({}) { androidx.compose.material3.Text("Play") }
                if (loaded) androidx.compose.material3.Button({}) { androidx.compose.material3.Text("Download") }
            }
        }
        val problems = check(flashing)
        assertTrue("The check reports it: $problems", problems.any { it.contains("\"Download\"") && it.contains("appears") })
    }
}
