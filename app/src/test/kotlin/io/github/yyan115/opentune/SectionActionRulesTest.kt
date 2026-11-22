package io.github.yyan115.opentune

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Only an action that opens a page carries an arrow, and the call says so. */
class SectionActionRulesTest {
    private val sources = File("src/main/kotlin/io/github/yyan115/opentune/ui").listFiles { f -> f.extension == "kt" }.orEmpty()

    private val calls = sources.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
        if (line.contains("SectionTitle(") && !line.contains("fun SectionTitle")) Triple(file.name, i + 1, line) else null } }

    @Test fun theCallsAreFound() { assertTrue("Found ${calls.size} calls", calls.size > 15) }

    @Test fun everyViewAllHasAnArrow() {
        val wrong = calls.filter { it.third.contains("action_view_all") && !it.third.contains("arrow = true") }
        assertTrue("These calls open a page and need arrow = true: $wrong", wrong.isEmpty())
    }

    @Test fun noOtherActionHasAnArrow() {
        val wrong = calls.filter { !it.third.contains("action_view_all") && it.third.contains("arrow = true") }
        assertTrue("These actions open no page and must not carry an arrow: $wrong", wrong.isEmpty())
    }
}
