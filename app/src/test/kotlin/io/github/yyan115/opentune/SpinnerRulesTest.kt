package io.github.yyan115.opentune

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A spinner has a name for a screen reader, so a button that shows only a spinner while it works still has one. Every spinner is the one in Components. */
class SpinnerRulesTest {
    private val sources = File("src/main/kotlin/io/github/yyan115/opentune/ui").listFiles { f -> f.extension == "kt" }.orEmpty()

    private val raw = sources.filter { it.name != "Components.kt" }.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
        if (line.contains("CircularProgressIndicator(")) "${file.name}:${i + 1}" else null } }

    @Test fun theSourcesAreFound() { assertTrue("Found ${sources.size} files", sources.size > 20) }

    @Test fun noScreenDrawsASpinnerWithoutAName() {
        assertTrue("These places call CircularProgressIndicator directly and must call Spinner: $raw", raw.isEmpty())
    }
}
