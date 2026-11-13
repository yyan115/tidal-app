package io.github.yyan115.opentune.core

/** What the version number of the app says about its stage. */
object AppVersion {
    /** Whether [version] belongs to a prerelease. Every version below 1.0.0 is one, and so is a test version of any number. */
    fun isPrerelease(version: String): Boolean {
        val number = version.removePrefix("v")
        return number.substringBefore('.') == "0" || '-' in number
    }
}
