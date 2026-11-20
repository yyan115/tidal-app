package io.github.yyan115.opentune

import io.github.yyan115.opentune.ui.ScreenStack
import io.github.yyan115.opentune.ui.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen that is shown, and the screens that are kept below it, for every combination of the state of the navigation. */
class ScreenStackTest {
    private val destinations = listOf("home", "search", "library", "device")
    private val settingsPages = listOf<SettingsPage?>(null) + SettingsPage.entries
    private val details = listOf<String?>(null, "local:1")
    private val pageLists = listOf(emptyList(), listOf("artist/1"), listOf("artist/1", "album/2"), listOf("artist/1", "album/2", "artist/1"))

    private fun everyCombination(check: (destination: String, settingsOpen: Boolean, page: SettingsPage?, downloads: Boolean, detail: String?, pages: List<String>) -> Unit) {
        for (destination in destinations) for (settingsOpen in listOf(false, true)) for (page in settingsPages) for (downloads in listOf(false, true))
            for (detail in details) for (pages in pageLists) check(destination, settingsOpen, page, downloads, detail, pages)
    }

    @Test fun theScreenThatIsShownIsAlwaysOnTheStack() = everyCombination { destination, settingsOpen, page, downloads, detail, pages ->
        val top = ScreenStack.top(destination, settingsOpen, page, downloads, detail, pages)
        val onStack = ScreenStack.onStack(destination, settingsOpen, page, downloads, detail, pages)
        assertTrue("$top is shown but not kept ($destination, $settingsOpen, $page, $downloads, $detail, $pages)", top in onStack)
    }

    @Test fun theMainScreensAreAlwaysOnTheStack() = everyCombination { destination, settingsOpen, page, downloads, detail, pages ->
        assertTrue(ScreenStack.onStack(destination, settingsOpen, page, downloads, detail, pages).containsAll(destinations))
    }

    @Test fun whatIsShownFollowsTheOrderOfTheScreens() {
        assertEquals("downloads", ScreenStack.top("home", true, null, true, "d", listOf("p")))
        assertEquals("settings", ScreenStack.top("home", true, null, false, "d", listOf("p")))
        assertEquals("settings.Repeats", ScreenStack.top("home", true, SettingsPage.Repeats, false, "d", listOf("p")))
        assertEquals("page:1:b", ScreenStack.top("home", false, null, false, "d", listOf("a", "b")))
        assertEquals("detail:d", ScreenStack.top("home", false, null, false, "d", emptyList()))
        assertEquals("library", ScreenStack.top("library", false, null, false, null, emptyList()))
    }

    @Test fun aPageStaysOnTheStackWhileSettingsIsOpenOverIt() {
        val onStack = ScreenStack.onStack("home", true, null, false, null, listOf("artist/123"))
        assertTrue(ScreenStack.page(0, "artist/123") in onStack)
        assertEquals("settings", ScreenStack.top("home", true, null, false, null, listOf("artist/123")))
    }

    @Test fun settingsLeavesTheStackWhenItIsClosed() = everyCombination { destination, _, page, downloads, detail, pages ->
        val onStack = ScreenStack.onStack(destination, false, page, downloads, detail, pages)
        assertFalse("Settings is closed, but ${onStack.filter { it.startsWith("settings") }} is kept", onStack.any { it.startsWith("settings") })
    }

    @Test fun theSamePageTwiceOnTheStackHasTwoPlaces() {
        val onStack = ScreenStack.onStack("home", false, null, false, null, listOf("artist/1", "album/2", "artist/1"))
        assertEquals(3, onStack.count { it.startsWith("page:") })
    }

    @Test fun aLicenceDocumentKeepsTheLicenceListAndSettingsBelowIt() {
        val onStack = ScreenStack.onStack("home", true, SettingsPage.Apache, false, null, emptyList())
        assertTrue(ScreenStack.settingsPage(SettingsPage.Apache) in onStack)
        assertTrue(ScreenStack.settingsPage(SettingsPage.Licenses) in onStack)
        assertTrue("settings" in onStack)
    }

    @Test fun aClosedPageLeavesTheStack() {
        assertFalse(ScreenStack.page(1, "album/2") in ScreenStack.onStack("home", false, null, false, null, listOf("artist/1")))
    }

    @Test fun theDownloadQueueAndADetailPageStayWhileTheyAreOpen() {
        val onStack = ScreenStack.onStack("device", false, null, true, "local:1", emptyList())
        assertTrue("downloads" in onStack)
        assertTrue(ScreenStack.detail("local:1") in onStack)
        assertFalse("downloads" in ScreenStack.onStack("device", false, null, false, "local:1", emptyList()))
    }
}
