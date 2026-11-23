package io.github.yyan115.opentune.playback

import android.content.SharedPreferences

/**
 * Whether this phone may use the output for 24-bit songs. A phone that fails the check of [StreamGuard] stays on 16-bit until its system build changes,
 * because the same decoder would fail again.
 */
class OutputPolicyState(private val store: Store = Store.None, private val build: String = "") {
    interface Store {
        fun read(): String?
        fun write(value: String?)
        object None : Store { override fun read(): String? = null; override fun write(value: String?) {} }
    }

    @Volatile var blockedReason: String? = null
        private set
    val floatAllowed get() = blockedReason == null

    /** Called once, on the thread that found the fault. */
    @Volatile var onBlocked: ((String) -> Unit)? = null

    /** Reads what an earlier run found. A result of another system build is forgotten. */
    fun restore() {
        val saved = store.read() ?: return
        if (saved.startsWith("$build|")) blockedReason = saved.substringAfter('|') else store.write(null)
    }

    fun block(reason: String) {
        if (blockedReason != null) return
        blockedReason = reason
        store.write("$build|$reason")
        onBlocked?.invoke(reason)
    }

    /** Only for tests and for a phone that is checked again. */
    fun clear() { blockedReason = null; store.write(null) }
}

/** The policy of the running app. The app replaces it, at start, with one that remembers its result. */
object OutputPolicy {
    @Volatile var state = OutputPolicyState()
}

/** Keeps the result of the check in the preferences of the app. */
class PreferencesStore(private val preferences: SharedPreferences) : OutputPolicyState.Store {
    override fun read(): String? = preferences.getString(KEY, null)
    override fun write(value: String?) { preferences.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply() }
    private companion object { const val KEY = "float_output_blocked" }
}
