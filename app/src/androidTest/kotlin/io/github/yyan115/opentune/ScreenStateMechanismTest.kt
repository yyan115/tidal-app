package io.github.yyan115.opentune

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The way the root of the app keeps a screen while another screen is shown over it, tried on plain lists, so that the result does not
 * depend on any screen of the app: how far a list is scrolled, a tab that was chosen, and a text that was typed.
 */
@RunWith(AndroidJUnit4::class)
class ScreenStateMechanismTest {
    @get:Rule val compose = createComposeRule()

    private val heights = listOf(48, 72, 56, 96, 64)

    @Composable private fun Rows() {
        LazyColumn(Modifier.fillMaxSize().testTag("list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(100) { number -> Text("Row $number", Modifier.height(heights[number % heights.size].dp).padding(8.dp)) }
        }
    }

    private fun topOf(text: String) = compose.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot.top

    /** Shows the screen named by [key] with the holder of the app, and forgets the screens that are not [onStack]. */
    @Composable private fun Stack(key: String, onStack: Set<String>, screen: @Composable (String) -> Unit) {
        val holder = rememberSaveableStateHolder()
        val kept = remember { mutableSetOf<String>() }
        LaunchedEffect(onStack) { kept.filter { it !in onStack }.forEach { holder.removeState(it); kept.remove(it) } }
        holder.SaveableStateProvider(key) { SideEffect { kept.add(key) }; screen(key) }
    }

    @Test fun aListInTheMiddleKeepsItsPlaceWhileAnotherScreenIsOverIt() {
        var key by mutableStateOf("list")
        compose.setContent { Stack(key, setOf("list", "other")) { if (it == "list") Rows() else Text("Other") } }
        compose.onNodeWithTag("list").performScrollToNode(hasText("Row 40"))
        val before = topOf("Row 40")
        key = "other"; compose.waitForIdle()
        key = "list"; compose.waitForIdle()
        assertEquals(before, topOf("Row 40"), 0.5f)
    }

    @Test fun aListAtTheEndKeepsItsPlaceWhileAnotherScreenIsOverIt() {
        var key by mutableStateOf("list")
        compose.setContent { Stack(key, setOf("list", "other")) { if (it == "list") Rows() else Text("Other") } }
        compose.onNodeWithTag("list").performScrollToNode(hasText("Row 99"))
        val before = topOf("Row 99")
        key = "other"; compose.waitForIdle()
        key = "list"; compose.waitForIdle()
        assertEquals(before, topOf("Row 99"), 0.5f)
    }

    @Test fun aScreenThatWasClosedStartsAtTheTopAgain() {
        var key by mutableStateOf("list")
        var onStack by mutableStateOf(setOf("list", "other"))
        compose.setContent { Stack(key, onStack) { if (it == "list") Rows() else Text("Other") } }
        compose.onNodeWithTag("list").performScrollToNode(hasText("Row 40"))
        key = "other"; compose.waitForIdle()
        onStack = setOf("other"); compose.waitForIdle()
        key = "list"; onStack = setOf("list"); compose.waitForIdle()
        compose.onNodeWithText("Row 0").assertIsDisplayed()
    }

    @Test fun aTabAndATypedTextAreKeptWithTheScreen() {
        var key by mutableStateOf("search")
        compose.setContent {
            Stack(key, setOf("search", "other")) { screen ->
                if (screen == "search") {
                    var tab by rememberSaveable { mutableStateOf(0) }
                    var text by rememberSaveable { mutableStateOf("") }
                    Column {
                        Text("Tab $tab"); Text("Text $text")
                        TextButton(onClick = { tab = 2; text = "abc" }) { Text("Change") }
                    }
                } else Text("Other")
            }
        }
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithText("Tab 2").assertExists()
        key = "other"; compose.waitForIdle()
        key = "search"; compose.waitForIdle()
        compose.onNodeWithText("Tab 2").assertExists()
        compose.onNodeWithText("Text abc").assertExists()
    }

    @Test fun aScreenKeepsItsPlaceWhenTheActivityIsRecreated() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Stack("list", setOf("list")) { Rows() } }
        compose.onNodeWithTag("list").performScrollToNode(hasText("Row 40"))
        val before = topOf("Row 40")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(before, topOf("Row 40"), 0.5f)
    }
}
