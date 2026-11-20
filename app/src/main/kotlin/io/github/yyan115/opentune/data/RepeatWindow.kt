package io.github.yyan115.opentune.data

/** The time window of the avoid-repeats setting, counted in minutes. A custom window is entered as days, hours and minutes. */
object RepeatWindow {
    const val MAX_DAYS = 365
    const val MAX_MINUTES = MAX_DAYS * 24 * 60

    /** The windows that can be chosen without typing: off, an hour, six hours, a day and a week. */
    val presets = listOf(0, 60, 360, 1440, 10_080)

    /** A window as days, hours and minutes. */
    data class Parts(val days: Int, val hours: Int, val minutes: Int)

    /** The window of [days], [hours] and [minutes] in minutes, or null when it is not from 1 minute to 365 days. */
    fun minutes(days: Int, hours: Int, minutes: Int): Int? {
        if (days < 0 || hours < 0 || minutes < 0) return null
        val total = days.toLong() * 1440 + hours.toLong() * 60 + minutes.toLong()
        return if (total in 1..MAX_MINUTES) total.toInt() else null
    }

    /** The window of [total] minutes as days, then hours, then minutes. */
    fun parts(total: Int): Parts = Parts(total / 1440, total / 60 % 24, total % 60)

    /** A window that an earlier version kept in hours. */
    fun fromHours(hours: Int): Int = (hours.toLong() * 60).coerceIn(0, MAX_MINUTES.toLong()).toInt()
}
