package io.github.yyan115.opentune

import org.json.JSONArray
import org.json.JSONObject

/**
 * The structure of a JSON answer: for every path the kinds of value that sit there. The values themselves are not part of it. Two answers have the same
 * structure when every field of the first is in the second and has a kind that the second has too. Null fits every kind, because a field that is empty
 * in one answer is often filled in another.
 */
object ContractShapes {
    fun kinds(value: JSONObject): Map<String, Set<String>> = mutableMapOf<String, MutableSet<String>>().also { collect(value, "", it) }

    private fun collect(value: Any?, path: String, out: MutableMap<String, MutableSet<String>>) {
        when (value) {
            is JSONObject -> value.keys().forEach { key -> collect(value.get(key), "$path.$key", out) }
            is JSONArray -> (0 until value.length()).forEach { collect(value.get(it), "$path[]", out) }
            JSONObject.NULL, null -> out.getOrPut(path) { mutableSetOf() } += "null"
            is String -> out.getOrPut(path) { mutableSetOf() } += "string"
            is Number -> out.getOrPut(path) { mutableSetOf() } += "number"
            is Boolean -> out.getOrPut(path) { mutableSetOf() } += "boolean"
        }
    }

    /** The fields that break a fake built from [expected] when the service answers [actual], and the fields that are new in [actual]. */
    fun compare(name: String, expected: JSONObject, actual: JSONObject): Pair<List<String>, List<String>> {
        val before = kinds(expected)
        val after = kinds(actual)
        val broken = mutableListOf<String>()
        for ((path, wanted) in before) {
            val found = after[path]
            when {
                found == null -> broken += "$name: the field $path is gone"
                wanted.intersect(found).isEmpty() && wanted != setOf("null") && found != setOf("null") -> broken += "$name: the field $path was $wanted and is now $found"
            }
        }
        return broken to after.keys.filter { it !in before }.map { "$name: new field $it" }
    }
}
