package io.github.yyan115.opentune

import androidx.media3.common.PlaybackException
import io.github.yyan115.opentune.playback.PlaybackFailures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PlaybackFailuresTest {
    private val unspecified = PlaybackException.ERROR_CODE_UNSPECIFIED

    @Test fun aServerThatCannotBeReachedIsNoConnection() {
        assertTrue(PlaybackFailures.isNoConnection(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, emptyList()))
        assertTrue(PlaybackFailures.isNoConnection(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, emptyList()))
        assertTrue(PlaybackFailures.isNoConnection(unspecified, listOf(UnknownHostException("api.tidal.com"))))
        assertTrue(PlaybackFailures.isNoConnection(unspecified, listOf(IOException("wrapper"), ConnectException("Network is unreachable"))))
        assertTrue(PlaybackFailures.isNoConnection(unspecified, listOf(SocketTimeoutException("timeout"))))
    }

    @Test fun otherFailuresAreNotNoConnection() {
        assertFalse(PlaybackFailures.isNoConnection(unspecified, emptyList()))
        assertFalse(PlaybackFailures.isNoConnection(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, listOf(IOException("Response code: 500"))))
        assertFalse(PlaybackFailures.isNoConnection(PlaybackException.ERROR_CODE_DECODING_FAILED, listOf(IllegalStateException("decoder"))))
    }

    @Test fun aMissingFileIsNeverNoConnection() {
        assertFalse(PlaybackFailures.isNoConnection(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, listOf(FileNotFoundException("gone"))))
        assertFalse(PlaybackFailures.isNoConnection(unspecified, listOf(FileNotFoundException("gone"), ConnectException())))
    }
}
