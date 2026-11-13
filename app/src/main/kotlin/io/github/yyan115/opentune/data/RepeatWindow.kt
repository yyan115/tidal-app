package io.github.yyan115.opentune.data

/** The time window of the avoid-repeats setting, counted in hours. A custom window is a number of hours or days. */
object RepeatWindow {
    const val MAX_DAYS = 365
    private const val MAX_HOURS = MAX_DAYS * 24

    /** The windows that can be chosen without typing. Zero turns the setting off. */
    val presets = listOf(0, 1, 6, 24, 168)

    /** The window in hours for [value] hours or days, or null when it is not a number from 1 hour to 365 days. */
    fun hours(value: Int?, days: Boolean): Int? {
        if (value == null || value < 1) return null
        val hours = if (days) value.toLong() * 24 else value.toLong()
        return if (hours <= MAX_HOURS) hours.toInt() else null
    }

    /** Whether a window of [hours] is shown in days. This holds for a whole number of days. */
    fun inDays(hours: Int): Boolean = hours > 0 && hours % 24 == 0

    /** The number to show for a window of [hours], in days when [days] is true. */
    fun number(hours: Int, days: Boolean): Int = if (days) hours / 24 else hours

    /** The same span of time counted in the other unit. Days round up, so the window is never shorter than it was. */
    fun convert(value: Int, toDays: Boolean): Int =
        if (toDays) ((value + 23) / 24).coerceIn(1, MAX_DAYS) else (value.toLong() * 24).coerceAtMost(MAX_HOURS.toLong()).toInt()
}
