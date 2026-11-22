package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The set of phone settings that the screen tests use covers every combination of any few settings, with far fewer runs than all combinations. */
class CoveringArrayTest {
    private val settings = listOf(List(12) { "theme$it" }, listOf("1.0", "1.3", "2.0"), listOf("small", "normal", "large", "tablet"), listOf("ltr", "rtl"))

    private fun tuples(rows: List<List<String>>, strength: Int): Set<List<Pair<Int, String>>> {
        val covered = HashSet<List<Pair<Int, String>>>()
        fun combos(from: Int, picked: List<Int>, row: List<String>) {
            if (picked.size == strength) { covered += picked.map { it to row[it] }; return }
            for (i in from until row.size) combos(i + 1, picked + i, row)
        }
        rows.forEach { combos(0, emptyList(), it) }
        return covered
    }

    private fun everyTuple(strength: Int): Int {
        var total = 0
        fun combos(from: Int, picked: List<Int>) {
            if (picked.size == strength) { total += picked.fold(1) { product, i -> product * settings[i].size }; return }
            for (i in from until settings.size) combos(i + 1, picked + i)
        }
        combos(0, emptyList())
        return total
    }

    @Test fun everyPairOfValuesIsInTheSet() {
        val rows = CoveringArray.generate(settings, strength = 2)
        assertEquals("Pairs that are missing", everyTuple(2), tuples(rows, 2).size)
        assertTrue("Few rows: ${rows.size}", rows.size < 12 * 4 * 2)
    }

    @Test fun everyTripleOfValuesIsInTheSet() {
        val rows = CoveringArray.generate(settings, strength = 3)
        assertEquals("Triples that are missing", everyTuple(3), tuples(rows, 3).size)
        assertTrue("At least the 144 rows of the three largest settings, and fewer than all ${12 * 3 * 4 * 2}: ${rows.size}", rows.size in 144 until 12 * 3 * 4 * 2)
    }

    @Test fun theSameSettingsGiveTheSameRows() {
        assertEquals(CoveringArray.generate(settings, 3), CoveringArray.generate(settings, 3))
    }

    @Test fun everyValueOfEverySettingIsUsed() {
        val rows = CoveringArray.generate(settings, strength = 2)
        for ((index, values) in settings.withIndex()) assertEquals(values.toSet(), rows.map { it[index] }.toSet())
    }
}
