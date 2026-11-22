package io.github.yyan115.opentune

import java.util.Random

/**
 * A short list of combinations of settings in which every combination of any [strength] settings occurs at least once.
 * All combinations of n settings with several values each are too many to run. Studies of software failures find that most failures need
 * only one or two settings to be set in a certain way and the worst studied needed four, so a set of this kind finds nearly all of them
 * with a small number of runs. The method is greedy and the random numbers are seeded, so the same settings always give the same list.
 */
object CoveringArray {
    fun <T> generate(settings: List<List<T>>, strength: Int, seed: Long = 7): List<List<T>> {
        require(strength in 1..settings.size)
        val random = Random(seed)
        // Every combination of [strength] settings with one value each that has to occur.
        val missing = LinkedHashSet<List<Pair<Int, Int>>>()
        fun combos(from: Int, picked: List<Int>) {
            if (picked.size == strength) {
                fun values(at: Int, chosen: List<Pair<Int, Int>>) {
                    if (at == picked.size) { missing += chosen; return }
                    for (v in settings[picked[at]].indices) values(at + 1, chosen + (picked[at] to v))
                }
                values(0, emptyList()); return
            }
            for (i in from until settings.size) combos(i + 1, picked + i)
        }
        combos(0, emptyList())
        val rows = ArrayList<IntArray>()
        fun gain(row: IntArray): Int {
            var count = 0
            fun walk(from: Int, picked: List<Int>) {
                if (picked.size == strength) { if (picked.map { it to row[it] } in missing) count++; return }
                for (i in from until row.size) walk(i + 1, picked + i)
            }
            walk(0, emptyList()); return count
        }
        fun cover(row: IntArray) {
            fun walk(from: Int, picked: List<Int>) {
                if (picked.size == strength) { missing.remove(picked.map { it to row[it] }); return }
                for (i in from until row.size) walk(i + 1, picked + i)
            }
            walk(0, emptyList())
        }
        while (missing.isNotEmpty()) {
            // Start from a combination that is missing, fill the other settings with the value that adds most, and keep the best of several tries.
            var best: IntArray? = null; var bestGain = -1
            repeat(24) {
                val seedTuple = missing.elementAt(random.nextInt(missing.size))
                val row = IntArray(settings.size) { -1 }
                seedTuple.forEach { (index, value) -> row[index] = value }
                for (index in settings.indices.shuffled(random)) if (row[index] < 0) {
                    var choice = 0; var choiceGain = -1
                    for (v in settings[index].indices.shuffled(random)) {
                        row[index] = v
                        val filled = IntArray(row.size) { if (row[it] < 0) 0 else row[it] }
                        val g = gain(filled)
                        if (g > choiceGain) { choiceGain = g; choice = v }
                    }
                    row[index] = choice
                }
                val g = gain(row)
                if (g > bestGain) { bestGain = g; best = row }
            }
            cover(best!!); rows += best!!
        }
        return rows.map { row -> row.mapIndexed { index, value -> settings[index][value] } }
    }
}
