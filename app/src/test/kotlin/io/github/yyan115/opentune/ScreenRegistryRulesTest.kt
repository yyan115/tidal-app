package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A screen that is added to the app has to be rendered by the catalog, or named in the registry with what checks it instead. */
class ScreenRegistryRulesTest {
    private val sources = File("src/main/kotlin/io/github/yyan115/opentune/ui").listFiles { f -> f.extension == "kt" }.orEmpty()
    private val screens = sources.flatMap { file -> file.readLines().mapNotNull { Regex("""^fun ([A-Z][A-Za-z]*(Screen|Page|Sheet|Dialog))\b""").find(it)?.groupValues?.get(1) } }.toSet()

    @Test fun theScreensAreFound() { assertTrue("Found $screens", screens.size > 20) }

    @Test fun everyScreenIsRenderedByTheCatalogOrNamedWithItsReason() {
        val unlisted = screens - ScreenRegistry.rendered - ScreenRegistry.elsewhere.keys
        assertTrue("List these in ScreenRegistry, with fixtures in the catalog or a reason: $unlisted", unlisted.isEmpty())
    }

    @Test fun theRegistryNamesNoScreenThatIsGone() {
        val gone = (ScreenRegistry.rendered + ScreenRegistry.elsewhere.keys) - screens
        assertTrue("These are not screens any more: $gone", gone.isEmpty())
    }

    @Test fun everyReasonSaysWhatChecksTheScreenInstead() {
        for ((name, reason) in ScreenRegistry.elsewhere) assertTrue("$name needs a reason", reason.length > 20)
    }

    @Test fun aScreenIsInOneListOnly() { assertEquals(emptySet<String>(), ScreenRegistry.rendered.intersect(ScreenRegistry.elsewhere.keys)) }
}
