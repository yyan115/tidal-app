package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalog renders the screens that the registry lists, and each of them in more than one state where it has more than one. */
@UnstableApi
class ScreenCatalogTest {
    @Test fun theCatalogHoldsEveryScreenOfTheRegistryAndNothingElse() {
        val rendered = ScreenCatalog.cases().map { it.screen }.toSet()
        assertEquals("The catalog and the registry list the same screens", ScreenRegistry.rendered, rendered)
    }

    @Test fun theScreensThatLoadDataAreRenderedWhileTheyLoadAndAfterIt() {
        val states = ScreenCatalog.cases().groupBy({ it.screen }, { it.state })
        for (screen in listOf("DetailScreen", "SearchScreen", "CreditsSheet")) assertTrue("$screen has a loading state: ${states[screen]}", states[screen].orEmpty().any { it.contains("load") || it.contains("search") })
    }
}
