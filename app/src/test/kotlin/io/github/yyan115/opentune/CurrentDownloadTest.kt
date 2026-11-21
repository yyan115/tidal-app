package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import io.github.yyan115.opentune.data.DownloadOrder
import io.github.yyan115.opentune.data.DownloadWorker
import io.github.yyan115.opentune.ui.AppState
import io.github.yyan115.opentune.ui.currentDownload
import io.github.yyan115.opentune.ui.trackIdOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

@UnstableApi
class CurrentDownloadTest {
    private fun work(index: Int, state: WorkInfo.State, progress: Data = Data.EMPTY) =
        WorkInfo(UUID.randomUUID(), state, setOf("audio-download", "track:t$index", DownloadOrder.tag(100L + index)), Data.EMPTY, progress)

    private fun stateOf(vararg downloads: WorkInfo) = AppState(downloads = downloads.toList())

    @Test fun noDownloadIsCurrentWhenNothingTransfers() {
        assertNull(currentDownload(stateOf()))
        assertNull(currentDownload(stateOf(work(0, WorkInfo.State.ENQUEUED), work(1, WorkInfo.State.BLOCKED))))
        assertNull(currentDownload(stateOf(work(0, WorkInfo.State.SUCCEEDED), work(1, WorkInfo.State.FAILED))))
    }

    @Test fun aDownloadThatWaitsForItsTurnIsNotCurrent() {
        val waiting = work(0, WorkInfo.State.RUNNING, workDataOf(DownloadWorker.WAITING to true))
        assertNull(currentDownload(stateOf(waiting)))
    }

    @Test fun theDownloadThatTransfersIsCurrentAndTheOthersWait() {
        val waiting = work(0, WorkInfo.State.RUNNING, workDataOf(DownloadWorker.WAITING to true))
        val transferring = work(1, WorkInfo.State.RUNNING, workDataOf("progress" to 40))
        assertEquals(transferring.id, currentDownload(stateOf(waiting, transferring, work(2, WorkInfo.State.ENQUEUED)))?.id)
    }

    @Test fun aDownloadThatJustStartedIsCurrentAtZeroPercent() {
        val started = work(3, WorkInfo.State.RUNNING)
        assertEquals(started.id, currentDownload(stateOf(started))?.id)
    }

    @Test fun theTrackOfADownloadIsReadFromItsTag() {
        assertEquals("t7", trackIdOf(work(7, WorkInfo.State.RUNNING)))
    }
}
