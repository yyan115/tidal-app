package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is undone in the live account: what a test added is removed, what it removed is added again, and what was there stays. */
class AccountRestoreRulesTest {
    @Test fun whatATestAddedIsRemovedAndWhatItRemovedComesBack() {
        val changes = AccountRestore.changes(before = setOf("a", "b", "c"), now = setOf("b", "c", "x", "y"))
        assertEquals(setOf("x", "y"), changes.remove)
        assertEquals(setOf("a"), changes.add)
    }

    @Test fun anAccountThatIsAsItWasHasNothingToUndo() {
        assertTrue(AccountRestore.changes(setOf("a", "b"), setOf("b", "a")).isEmpty)
    }

    @Test fun anEmptyAccountStaysEmptyAndFillsAndEmptiesAgain() {
        assertTrue(AccountRestore.changes(emptySet(), emptySet()).isEmpty)
        assertEquals(setOf("x"), AccountRestore.changes(emptySet(), setOf("x")).remove)
        assertEquals(setOf("a"), AccountRestore.changes(setOf("a"), emptySet()).add)
    }

    @Test fun anAccountWithManyItemsKeepsAllOfThemWhenATestAddsOne() {
        val before = (1..500).map { "t$it" }.toSet()
        val changes = AccountRestore.changes(before, before + "new")
        assertEquals(setOf("new"), changes.remove)
        assertTrue(changes.add.isEmpty())
    }
}
