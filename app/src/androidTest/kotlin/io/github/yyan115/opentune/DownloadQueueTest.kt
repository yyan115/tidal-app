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

    private fun request(constraints: Constraints = Constraints.NONE, song: Track = track) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf("track" to JsonCodec.encodeTrack(song).toString())).setConstraints(constraints)
        .addTag("audio-download").addTag("track:${song.id}").build()

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
        // The queue opens from its row, or from the card of the download that runs, which takes the place of the row while a download transfers.
        val entry = (hasText("Download queue") and hasClickAction()) or hasTestTag("current-download")
        compose.waitUntil(10_000) { compose.onAllNodes(entry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-list").performScrollToNode(entry)
        compose.onAllNodes(entry).onFirst().performClick()
        compose.onNodeWithTag("download-queue").assertIsDisplayed()
    }

    @Test fun cancelAllStopsEveryWaitingDownloadAfterTheListenerAnswers() {
        val waiting = Constraints.Builder().setRequiresDeviceIdle(true).build()
        listOf(Track("2200002", "Second Song", "Queue Artist"), Track("2200003", "Third Song", "Queue Artist")).forEach { song ->
            app.library.save(song)
            manager.enqueueUniqueWork("download:${song.id}", ExistingWorkPolicy.REPLACE, request(waiting, song)).result.get()
        }
        try {
            openTheQueue()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Third Song").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Cancel all").assertIsEnabled().performClick()
            compose.onNodeWithText("Cancel 3 downloads?").assertIsDisplayed()
            compose.onNodeWithText("Finished downloads stay on the device.").assertIsDisplayed()
            compose.onNodeWithText("Keep").performClick()
            compose.onAllNodesWithText("Cancel 3 downloads?").assertCountEquals(0)
            compose.onNodeWithText("Queued Song").assertIsDisplayed()
            compose.onNodeWithText("Cancel all").performClick()
            compose.onNode(hasText("Cancel all") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("The queue is empty").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            listOf("2200002", "2200003").forEach { app.library.removeLocal(it) }
        }
    }

    @Test fun theCancelAllButtonStaysOnThePageAndIsDisabledWhenThereIsNothingToCancel() {
        openTheQueue()
        compose.onNodeWithText("Cancel all").assertIsEnabled()
        val bounds = compose.onNodeWithText("Cancel all").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Cancel download").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("The queue is empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel all").assertIsNotEnabled()
        org.junit.Assert.assertEquals("The button does not move", bounds, compose.onNodeWithText("Cancel all").fetchSemanticsNode().boundsInRoot)
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
