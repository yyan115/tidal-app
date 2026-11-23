package io.github.yyan115.opentune

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An item of a list that is given exactly a part of the height of the list squeezes its content when the screen is small or the text is large.
 * The content of an empty list goes through CenteredInFreeSpace, which gives it that part of the height as a minimum and lets it grow.
 */
class ListItemRulesTest {
    private val sources = File("src/main/kotlin/io/github/yyan115/opentune/ui").listFiles { f -> f.extension == "kt" }.orEmpty()

    @Test fun theSourcesAreFound() { assertTrue("Found ${sources.size} files", sources.size > 20) }

    @Test fun noScreenGivesAnItemExactlyAPartOfTheListHeight() {
        val calls = sources.filter { it.name != "Components.kt" }.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
            if (line.contains("fillParentMaxHeight(")) "${file.name}:${i + 1}" else null } }
        assertTrue("These places fix the height of a list item and must call CenteredInFreeSpace: $calls", calls.isEmpty())
    }
}
