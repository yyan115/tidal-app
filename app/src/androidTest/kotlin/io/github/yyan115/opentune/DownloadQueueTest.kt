package io.github.yyan115.opentune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.DownloadRequests
import io.github.yyan115.opentune.data.DownloadWorker
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The download queue has its own page, a download follows the switch for mobile data, and a failed download can be cleared. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadQueueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val manager get() = WorkManager.getInstance(app)
    private val track = Track("2200001", "Queued Song", "Queue Artist")
    private val name = "download:${track.id}"

    private fun request(constraints: Constraints = Constraints.NONE) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf("track" to JsonCodec.encodeTrack(track).toString())).setConstraints(constraints)
        .addTag("audio-download").addTag("track:${track.id}").build()

    @Before fun queueADownloadThatWaits() {
        compose.runOnIdle { model.settings { it.copy(theme = "dark", welcomeDone = true, offline = false, downloadOnWifi = true) } }
        app.library.save(track)
        // The download waits for a device that is idle, which a device in a test is not.
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request(Constraints.Builder().setRequiresDeviceIdle(true).build())).result.get()
    }

    @After fun clean() {
        manager.cancelAllWorkByTag("audio-download").result.get()
        manager.pruneWork().result.get()
        app.library.removeLocal(track.id)
    }

    private fun openTheQueue() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Download queue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(hasText("Download queue"))
        compose.onNode(hasText("Download queue") and hasClickAction()).performClick()
        compose.onNodeWithTag("download-queue").assertIsDisplayed()
    }

    @Test fun theQueueOpensFromOnDeviceAndACancelledDownloadLeavesIt() {
        openTheQueue()
        compose.onNodeWithText("Queued Song").assertIsDisplayed()
        compose.onNodeWithText("Queued \u00b7 Wi-Fi only").assertIsDisplayed()
        compose.onAllNodesWithText("Clear failed downloads").assertCountEquals(0)
        compose.onNodeWithContentDescription("Cancel download").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("The queue is empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("device-list").assertIsDisplayed()
        compose.onAllNodesWithText("Download queue").assertCountEquals(0)
    }

    @Test fun aFailedDownloadCanBeTriedAgainAndClearedAndOnlyThenTheButtonShows() {
        // Nobody is signed in, so this download fails.
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request()).result.get()
        openTheQueue()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Clear failed downloads").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Clear failed downloads").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("The queue is empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Clear failed downloads").assertCountEquals(0)
    }

    @Test fun turningOnMobileDataQueuesAWaitingDownloadAgainWithTheNewRule() {
        val before = manager.getWorkInfosForUniqueWork(name).get().single { it.state == WorkInfo.State.ENQUEUED }.id
        compose.runOnIdle { model.settings { it.copy(downloadOnWifi = false) } }
        compose.waitUntil(15_000) { manager.getWorkInfosForUniqueWork(name).get().let { list -> list.isNotEmpty() && list.none { it.id == before } } }
        assertTrue("The waiting download was queued again", manager.getWorkInfosForUniqueWork(name).get().none { it.id == before })
    }

    @Test fun applyingTheSameRuleTwiceKeepsOneWaitingDownload() {
        // Applying the rule again queues the waiting download again and never adds a second one.
        DownloadRequests.applyNetworkRule(app, wifiOnly = false)
        DownloadRequests.applyNetworkRule(app, wifiOnly = false)
        compose.waitUntil(15_000) { manager.getWorkInfosForUniqueWork(name).get().isNotEmpty() }
        assertTrue(manager.getWorkInfosForUniqueWork(name).get().size <= 1)
    }
}
