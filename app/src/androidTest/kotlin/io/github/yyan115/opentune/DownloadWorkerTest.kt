package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.DownloadWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A download does not depend on its notification. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadWorkerTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val track = Track("2200010", "Background Song", "Worker test")

    @Test fun aDownloadGoesOnWhenAndroidRefusesItsNotification() {
        assumeTrue("This test needs a device without an account", !app.tidal.signedIn)
        app.settings.update { it.copy(offline = false) }
        // Android 12 and later refuse the foreground service of work that starts while the app is in the background.
        val worker = TestListenableWorkerBuilder<DownloadWorker>(app).setInputData(workDataOf("track" to JsonCodec.encodeTrack(track).toString()))
            .setForegroundUpdater { _, _, _ -> Futures.immediateFailedFuture(IllegalStateException("Not allowed to start a foreground service")) }
            .build()
        val result = runBlocking { worker.doWork() }
        // Nobody is signed in, so the download ends at the request for the stream, which comes after the notification.
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("problem:SIGN_IN_REQUIRED:401", (result as ListenableWorker.Result.Failure).outputData.getString("error"))
    }
}
