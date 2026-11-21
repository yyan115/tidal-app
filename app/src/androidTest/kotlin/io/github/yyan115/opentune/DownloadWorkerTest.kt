package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A download does not depend on its notification, and downloads take turns, one at a time, in the order in which they were queued. */
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

    @Test fun downloadsTakeTurnsAndOnlyOneTransfersAtATime() {
        assumeTrue("This test needs a device without an account", !app.tidal.signedIn)
        app.settings.update { it.copy(offline = false) }
        val inside = AtomicInteger(); val peak = AtomicInteger()
        val timer = Executors.newSingleThreadScheduledExecutor()
        // A download shows its notification once its turn has come. The notification takes a moment here, so that the downloads overlap.
        fun worker(index: Int) = TestListenableWorkerBuilder<DownloadWorker>(app)
            .setInputData(workDataOf("track" to JsonCodec.encodeTrack(track.copy(id = "22000${20 + index}")).toString()))
            .setForegroundUpdater { _, _, _ ->
                val now = inside.incrementAndGet(); peak.updateAndGet { maxOf(it, now) }
                SettableFuture.create<Void>().also { future -> timer.schedule({ inside.decrementAndGet(); future.set(null) }, 300, TimeUnit.MILLISECONDS) }
            }.build()
        try {
            runBlocking { (1..8).map { index -> async(Dispatchers.Default) { worker(index).doWork() } }.awaitAll() }
        } finally { timer.shutdownNow() }
        assertEquals("Two downloads transferred at the same time", 1, peak.get())
    }

    @Test fun queuedDownloadsFinishOneAfterTheOtherInTheOrderOfQueuing() {
        assumeTrue("This test needs a device without an account", !app.tidal.signedIn)
        app.settings.update { it.copy(offline = false, downloadOnWifi = false) }
        val manager = androidx.work.WorkManager.getInstance(app)
        manager.cancelAllWorkByTag("audio-download").result.get()
        val tracks = (0 until 8).map { track.copy(id = "22001${10 + it}", title = "Queued $it") }
        tracks.forEach { app.library.save(it); io.github.yyan115.opentune.data.DownloadRequests.enqueue(app, it, wifiOnly = false) }
        val snapshots = mutableListOf<List<androidx.work.WorkInfo>>()
        runBlocking {
            kotlinx.coroutines.withTimeout(60_000) {
                manager.getWorkInfosByTagFlow("audio-download").first { infos ->
                    snapshots += infos
                    infos.size >= tracks.size && infos.all { it.state.isFinished }
                }
            }
        }
        val observed = snapshots.filter { it.isNotEmpty() }
        assertTrue("The downloads were observed", observed.isNotEmpty())
        for (infos in observed) {
            val byOrder = infos.sortedBy { io.github.yyan115.opentune.data.DownloadOrder.orderOf(it.tags) }
            val firstUnfinished = byOrder.indexOfFirst { !it.state.isFinished }
            if (firstUnfinished >= 0) assertTrue("A download finished before one that was queued earlier: ${byOrder.map { it.state }}", byOrder.drop(firstUnfinished).none { it.state.isFinished })
            assertTrue("More than one download transferred: ${infos.map { it.state to it.progress }}",
                infos.count { it.state == androidx.work.WorkInfo.State.RUNNING && !it.progress.getBoolean(DownloadWorker.WAITING, false) && it.progress.keyValueMap.isNotEmpty() } <= 1)
        }
        tracks.forEach { app.library.removeLocal(it.id) }
        manager.pruneWork().result.get()
    }
}
