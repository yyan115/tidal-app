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
}
