package io.github.yyan115.opentune

import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real app, started with a few songs, followed by pressing the controls on every screen that can be reached and on the screens that open from them.
 * Each screen that is found is held to the layout rules, to the colour of the strip behind the three buttons and to the contrast rule, so a screen
 * that nobody listed in the catalog is checked too. A control that would delete, sign out or leave the app is not pressed. It runs only with the
 * instrumentation argument `crawl` set to `true`, and it keeps to a time limit, because it explores by pressing and a phone is slow.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class AppCrawlerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private lateinit var tracks: List<Track>
    private var before = ""

    @Before fun startWithSongs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("crawl") == "true")
        before = BottomStrip.useThreeButtons(); Thread.sleep(1500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put global heads_up_notifications_enabled 0").close()
        val file = File(app.filesDir, "crawl-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("music-clip.flac").use { input -> file.outputStream().use(input::copyTo) }
        tracks = (1..6).map { Track("local:crawl-$it", "Crawl Song $it", "Crawl Artist", "Crawl Album", durationMs = 30_000, localUri = Uri.fromFile(file).toString(), quality = "FLAC") }
        tracks.forEach(app.library::save)
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, visualizer = false) } }
    }

    @After fun clean() {
        if (!::tracks.isInitialized) return
        compose.runOnIdle { model.stopPlayback() }
        tracks.forEach { app.library.removeLocal(it.id) }
        BottomStrip.restore(before)
    }

    /** Controls whose names say that they delete, sign out, change the storage, leave the app or take a long time are left alone. */
    private val leftAlone = listOf("clear", "stop", "remove", "delete", "sign", "connect", "cancel all", "reset", "erase", "add files", "add folder", "choose", "change", "share", "export", "import",
        "backup", "restore", "update", "licence", "github", "privacy", "rescan", "retry", "download", "offline", "language", "close sheet", "drag handle")

    private fun nameOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() }

    private fun roots() = compose.onAllNodes(isRoot()).fetchSemanticsNodes()

    /** A short description of the screen: how many windows there are, which tags are in them and the first texts from the top. */
    private fun signature(): String {
        val tags = linkedSetOf<String>(); val texts = mutableListOf<Pair<Float, String>>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { if (it != "mini-player") tags += it }
            if (node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty) node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.takeIf { it.isNotBlank() && !it.any(Char::isDigit) }?.let { texts += node.boundsInRoot.top to it }
            node.children.forEach(::walk)
        }
        val all = roots(); all.forEach(::walk)
        return "${all.size}|${tags.sorted().joinToString(",")}|${texts.sortedBy { it.first }.take(4).joinToString("/") { it.second.take(24) }}"
    }

    /** The controls that can be pressed on this screen, from the top, with the names that mark them. */
    private fun candidates(): List<String> {
        val found = LinkedHashMap<String, Float>()
        fun walk(node: SemanticsNode) {
            val role = node.config.getOrNull(SemanticsProperties.Role)
            if (node.config.contains(SemanticsActions.OnClick) && node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty && !Readability.isDisabled(node) && Readability.insideItsParents(node)
                && role != Role.Switch && role != Role.Checkbox && role != Role.RadioButton && !node.config.contains(SemanticsActions.SetText)) {
                val name = nameOf(node)
                if (name.isNotBlank() && leftAlone.none { name.lowercase().contains(it) }) found.putIfAbsent(name, node.boundsInRoot.top)
            }
            node.children.forEach(::walk)
        }
        roots().forEach(::walk)
        return found.entries.sortedBy { it.value }.map { it.key }
    }

    private fun press(name: String): Boolean {
        val target = compose.onAllNodes((hasContentDescription(name) or hasText(name)) and hasClickAction()).fetchSemanticsNodes()
        if (target.isEmpty()) return false
        compose.onAllNodes((hasContentDescription(name) or hasText(name)) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle(); Thread.sleep(350); compose.waitForIdle()
        return true
    }

    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle(); Thread.sleep(250) }

    private fun navigationHeight(): Int {
        var height = 0
        compose.runOnIdle { height = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0 }
        return height
    }

    @Test fun everyScreenThatCanBeReachedKeepsTheRules() {
        compose.runOnIdle { model.play(tracks, 0, "Crawl list") }
        compose.waitUntil(15_000) { model.state.value.playback.queue.size == tracks.size }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
        val limit = System.currentTimeMillis() + 170_000
        val seen = linkedSetOf<String>(); val problems = mutableListOf<String>()
        val rootSignature = signature()
        fun inspect(path: String) {
            val signatureNow = signature()
            LayoutRules.problems(compose).forEach { problems += "$path: $it" }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try { BottomStrip.problem(bitmap, navigationHeight())?.let { problems += "$path: $it" } } finally { bitmap.recycle() }
            // A sheet or a pop-up dims the screen behind it, so the contrast of the screen is read only when it has no window above it. The catalog reads the windows themselves.
            if (roots().size == 1) Readability.problems(compose).forEach { problems += "$path: $it" }
            android.util.Log.i("AppCrawler", "screen $path -> $signatureNow")
        }
        fun explore(depth: Int, path: String) {
            val here = signature()
            if (!seen.add(here)) return
            inspect(path)
            if (depth >= 4 || System.currentTimeMillis() > limit) return
            for (name in candidates().take(14)) {
                if (System.currentTimeMillis() > limit) return
                if (!press(name)) continue
                if (signature() != here) {
                    explore(depth + 1, "$path > $name")
                    // Back out of what opened, and stop with this screen if the way back does not lead to it.
                    // The Back key on the first screen would close the app, so it is not pressed there.
                    repeat(3) { val now = signature(); if (now != here && now != rootSignature) back() }
                    if (signature() != here) return
                }
            }
        }
        // The screen that the app starts on is explored first, and what it offers leads to the others, including the menu that switches between the main screens.
        try { explore(0, "start") } catch (gone: IllegalStateException) { android.util.Log.i("AppCrawler", "the app went away: ${gone.message?.take(80)}") }
        android.util.Log.i("AppCrawler", "${seen.size} screens, ${problems.size} problems")
        assertTrue("The crawler reached only ${seen.size} screens: $seen", seen.size >= 5)
        assertTrue("${seen.size} screens were reached and ${problems.size} problems found:\n" + problems.distinct().take(60).joinToString("\n"), problems.isEmpty())
    }
}
