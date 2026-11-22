package io.github.yyan115.opentune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.OpenTuneIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The rules are tested on screens that break them, because a rule that finds nothing would also pass on a screen that is fine. */
@RunWith(AndroidJUnit4::class)
class LayoutRulesTest {
    @get:Rule val compose = createComposeRule()
    private var density = 1f

    private fun problems(content: @androidx.compose.runtime.Composable () -> Unit): List<String> {
        compose.setContent { density = LocalDensity.current.density; content() }
        compose.waitForIdle()
        return LayoutRules.problems(compose)
    }

    @Test fun aGoodScreenHasNoProblem() {
        val found = problems { Box(Modifier.fillMaxSize()) {
            Text("Title"); Box(Modifier.offset(y = 60.dp).size(60.dp).clickable { }) { Text("Play") }
            Icon(OpenTuneIcons.Close, "Close", Modifier.offset(y = 140.dp).size(48.dp).clickable { })
        } }
        assertEquals(emptyList<String>(), found)
    }

    @Test fun textThatLiesOnTextIsFound() {
        val found = problems { Box(Modifier.fillMaxSize()) { Text("First line of the title"); Text("Second line of the title") } }
        assertTrue("An overlap is reported: $found", found.any { it.startsWith("overlap") })
    }

    @Test fun textBeyondTheEdgeOfTheScreenIsFound() {
        val found = problems { Box(Modifier.fillMaxSize()) { Text("A text that starts near the right edge and runs beyond it", Modifier.offset(x = 250.dp)) } }
        assertTrue("A text outside the screen is reported: $found", found.any { it.startsWith("outside the screen") })
    }

    @Test fun twoControlsOnTopOfEachOtherAreFound() {
        val found = problems { Box(Modifier.fillMaxSize()) { Box(Modifier.size(60.dp).clickable { }) { Text("Save") }; Box(Modifier.offset(x = 10.dp).size(60.dp).clickable { }) { Text("Share") } } }
        assertTrue("Controls on each other are reported: $found", found.any { it.startsWith("controls on top of each other") })
    }

    @Test fun aControlWithNoNameIsFound() {
        val found = problems { Box(Modifier.fillMaxSize()) { Icon(OpenTuneIcons.Close, null, Modifier.size(48.dp).clickable { }) } }
        assertTrue("A control without a name is reported: $found", found.any { it.startsWith("a control with no name") })
    }
}
