package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.OpenTuneTheme
import io.github.yyan115.opentune.ui.appThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen in every state, on the settings of the phone that the covering array lists, under the layout rules and the contrast rule.
 * The settings are the theme, the size of the text, the size of the screen and the direction of the text.
 */
@UnstableApi
@RunWith(org.junit.runners.Parameterized::class)
class ScreenMatrixTest(private val screen: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Size(val name: String, val width: Int, val height: Int)

    companion object {
        /** One test for each screen of the registry, so that the report names the screen that breaks a rule. */
        @JvmStatic @org.junit.runners.Parameterized.Parameters(name = "{0}") fun screens(): List<String> = ScreenRegistry.rendered.sorted().filter { name -> InstrumentationRegistry.getArguments().getString("matrixScreens")?.split(",")?.contains(name) ?: true }
        private val rows: List<List<Any>> by lazy { CoveringArray.generate(settings(), argument("matrixStrength", 2)) }
        private fun settings(): List<List<Any>> = listOf(appThemes.map { it.id }, listOf(1.0f, 1.3f, 2.0f), listOf(Size("small", 320, 568), Size("normal", 393, 852), Size("wide", 600, 960)), listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
        private fun argument(name: String, default: Int) = InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull() ?: default
    }


    @Test fun everyScreenInEveryStateKeepsTheRulesOnEverySetting() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val stride = argument("matrixStride", 1)
        // A message of the system that rises over the top of the screen hides a text, and the check would then report a text that is fine.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put global heads_up_notifications_enabled 0").close()
        val failures = mutableListOf<String>()
        var renders = 0
        var saved = 0
        val started = System.currentTimeMillis()
        class Shown(val case: ScreenCase, val row: List<Any>, val tick: Int)
        val shown = androidx.compose.runtime.mutableStateOf<Shown?>(null)
        compose.setContent {
            val now = shown.value
            if (now != null) androidx.compose.runtime.key(now.tick) {
                val theme = now.row[0] as String; val scale = now.row[1] as Float; val size = now.row[2] as Size; val direction = now.row[3] as LayoutDirection
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), LocalLayoutDirection provides direction) {
                    OpenTuneTheme(theme) {
                        // The app paints its background itself, so the screen is shown on it as it is on a phone.
                        androidx.compose.material3.Surface(Modifier.size(size.width.dp, size.height.dp).testTag("matrix-screen"), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { now.case.content(model) }
                    }
                }
            }
        }
        for ((caseIndex, case) in ScreenCatalog.cases().filter { it.screen == screen }.withIndex()) {
            for ((rowIndex, row) in rows.withIndex()) {
                if ((rowIndex + caseIndex) % stride != 0) continue
                val size = row[2] as Size
                val where = "${case.screen} (${case.state}) in ${row[0]}, text ${row[1]}x, ${size.name} screen, ${if (row[3] == LayoutDirection.Rtl) "right to left" else "left to right"}"
                renders++
                shown.value = Shown(case, row, renders)
                compose.waitForIdle()
                try {
                    val layout = LayoutRules.problems(compose)
                    val contrast = if (rowIndex % 6 == 0) Readability.problems(compose) else emptyList()
                    // The first few screens that break a rule are kept as pictures, so that the report can be read against what was on the screen.
                    if ((layout.isNotEmpty() || contrast.isNotEmpty()) && saved < 12) { saved++; Readability.problems(compose, keep = "matrix-${case.screen}-${case.state}-$renders".replace(' ', '-')) }
                    layout.forEach { failures += "$where: $it" }; contrast.forEach { failures += "$where: $it" }
                } catch (error: Throwable) { failures += "$where: the rules could not run: ${error.javaClass.simpleName} ${error.message?.take(120)}" }
            }
        }
        android.util.Log.i("ScreenMatrix", "$renders renders in ${(System.currentTimeMillis() - started) / 1000} s, ${failures.size} problems")
        // The message groups the problems by screen and by rule, with the first example of each, so that a long list can be read.
        fun kind(line: String) = line.substringAfter(": ").let { rest -> listOf("overlap", "outside the screen", "a control with no name", "controls on top of each other", "has contrast").firstOrNull { rest.contains(it) } ?: rest.take(30) }
        val groups = failures.distinct().groupBy { it.substringBefore(" (") + " / " + kind(it) }
        failures.distinct().forEach { android.util.Log.w("ScreenMatrix", it) }
        assertTrue("$renders renders, ${failures.size} problems in ${groups.size} groups:\n" + groups.entries.sortedByDescending { it.value.size }.joinToString("\n") { "${it.value.size} x ${it.key}, for example: ${it.value.first().take(260)}" }, failures.isEmpty())
    }
}
