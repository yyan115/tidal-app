package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rules of the source that keep every screen from losing what it remembers, and from showing a frame in the wrong place.
 * They read the source, so a screen that is added later is held to them as well.
 */
class ScreenRulesTest {
    private val ui = File("src/main/kotlin/io/github/yyan115/opentune/ui")
    private val sources = ui.listFiles { file -> file.extension == "kt" }.orEmpty().sortedBy { it.name }

    private fun code(file: File) = file.readLines().mapIndexedNotNull { index, line ->
        val text = line.trimStart()
        if (text.startsWith("import ") || text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")) null else (index + 1) to line
    }

    @Test fun theSourceIsFound() {
        assertTrue("The sources of the screens are not found from ${File(".").absolutePath}", sources.size > 20)
    }

    /** A state made with the constructor is not saved, so a screen that leaves would forget where the list was. */
    @Test fun everyListAndScrollStateIsOneThatIsSaved() {
        val constructors = Regex("""(?<![A-Za-z])(LazyListState|LazyGridState|ScrollState|PagerState|LazyStaggeredGridState)\(""")
        val found = sources.flatMap { file -> code(file).filter { constructors.containsMatchIn(it.second) }.map { "${file.name}:${it.first}" } }
        assertTrue("Use rememberLazyListState, rememberScrollState and the like, which are saved: $found", found.isEmpty())
    }

    /** One place keeps what the screens remember, and it is keyed by the stack of screens that the tests of ScreenStack cover. */
    @Test fun theRootKeepsEveryScreenWithTheStack() {
        val root = File(ui, "Root.kt").readText()
        assertEquals("The root has one holder of the state of the screens", 1, Regex("""SaveableStateProvider\(""").findAll(root).count())
        assertTrue("The holder is keyed by the top of the stack", root.contains("SaveableStateProvider(top)") && root.contains("ScreenStack.top("))
        assertTrue("A screen that left the stack is forgotten", root.contains("removeState(") && root.contains("ScreenStack.onStack("))
    }

    /**
     * The content of a scaffold is made while the scaffold is measured, so a header in its top bar is measured after the screen that depends on
     * its height. A screen that opened was then measured with the height of the header before it, and a list that came back was put in the wrong place.
     */
    @Test fun noHeaderSitsInTheTopBarOfAScaffold() {
        val found = sources.flatMap { file -> code(file).filter { Regex("""topBar\s*=""").containsMatchIn(it.second) }.map { "${file.name}:${it.first}" } }
        assertTrue("Put the header above the scaffold, in the same column: $found", found.isEmpty())
    }

    /**
     * The width of a text follows its characters and its size, so a text never has a fixed width or a fixed height. A number in a column of numbers uses a width that grows.
     * Icons, spacers and buttons can have a fixed size, because their content does not change with the text size.
     */
    @Test fun noTextHasAFixedWidthOrHeight() {
        // The width or height must come after the opening of the Text and before the end of its statement. A spacer next to a text on the same line is not the text.
        val fixed = Regex("""\bText\([^;]*?\.(width|height)\(\d+(\.\d+)?\.dp\)""")
        val offenders = sources.flatMap { file -> code(file).filter { (_, line) -> fixed.containsMatchIn(line) }.map { (number, _) -> "${file.name}:$number" } }
        assertEquals("These texts have a fixed width or height: $offenders", emptyList<String>(), offenders)
    }

    /**
     * A colour that does not come from the theme cannot follow the theme, so only a drawing over a picture or a video may have one: the picture does not change with the theme.
     * Each of those places is listed here, so that a new one is a decision and not an accident.
     */
    @Test fun fixedColoursAreOnlyOverPicturesAndVideo() {
        val fixed = Regex("""Color\(0x|Color\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta)\b""")
        val allowed = mapOf("GalleryHome.kt" to 2, "OpenTuneIcons.kt" to 2, "PageScreens.kt" to 2, "PlayerScreen.kt" to 1, "VideoPlayer.kt" to 10)
        val found = sources.filter { it.name !in setOf("Themes.kt", "Theme.kt") }.associate { file -> file.name to code(file).count { (_, line) -> fixed.containsMatchIn(line) } }.filterValues { it > 0 }
        assertEquals("Fixed colours outside the theme: $found. A drawing over a picture or a video is added to the list of this test, anything else uses the theme.", allowed, found)
    }
}
