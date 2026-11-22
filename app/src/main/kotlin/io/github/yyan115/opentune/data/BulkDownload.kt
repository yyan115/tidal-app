package io.github.yyan115.opentune.data

/** A page that downloads many songs at once asks first, so that one tap does not queue fifty. */
object BulkDownload {
    /** A download of more songs than this asks before it starts. */
    const val CONFIRM_ABOVE = 10

    fun needsConfirmation(count: Int) = count > CONFIRM_ABOVE
}
