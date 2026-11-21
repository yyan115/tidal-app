package io.github.yyan115.opentune.playback

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What kind of failure stopped the player. */
object PlaybackFailures {
    /**
     * Whether the failure comes from a lost connection: the player could not reach the server at all.
     * A server that answers with an error, a file that is gone and a file that cannot be read are other failures.
     */
    fun isNoConnection(errorCode: Int, causes: List<Throwable>): Boolean {
        if (causes.any { it is FileNotFoundException }) return false
        if (errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return true
        return causes.any { it is UnknownHostException || it is SocketTimeoutException || it is SocketException }
    }

    fun isNoConnection(error: PlaybackException): Boolean = isNoConnection(error.errorCode, generateSequence<Throwable>(error) { it.cause }.toList())
}
