package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Rules for the text of the app and for the places that send it. */
class StringsRulesTest {
    private val strings = File("src/main/res/values/strings.xml").readText()
    private fun text(name: String) = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(strings)?.groupValues?.get(1)

    @Test fun theStatisticsTextIsASentenceThatEndsWithAFullStop() {
        assertTrue("The text is not found", text("stats_info") != null)
        assertTrue("The text ends with a full stop: ${text("stats_info")}", text("stats_info")!!.endsWith("."))
    }

    @Test fun theMessageAfterAFolderChangeIsShort() {
        assertEquals("Download folder changed.", text("notify_download_folder_changed"))
    }

    /** The permissions page is the first set-up, so its folder change must not be announced. The caller says so. */
    @Test fun thePermissionsPageAsksForNoMessageWhenItChangesTheFolder() {
        val root = File("src/main/kotlin/io/github/yyan115/opentune/ui/Root.kt").readText()
        val call = Regex("""SetupScreen\((.*)\)""").find(root)?.value.orEmpty()
        assertTrue("The root builds the permissions page: $call", call.isNotEmpty())
        assertTrue("The permissions page must not announce the change: $call", call.contains("announce = false"))
    }
}
