package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.DownloadOrder
import io.github.yyan115.opentune.data.DownloadWorker
import io.github.yyan115.opentune.downloadErrorText

/** The downloads that are in the queue: the ones that run or wait, and the ones that failed. */
fun queuedDownloads(state: AppState): List<WorkInfo> = state.downloads.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
    .sortedBy { DownloadOrder.orderOf(it.tags) ?: Long.MAX_VALUE }

/** Whether a download of the queue transfers data now. Downloads take turns, so a download that Android runs may still wait for its turn. */
@UnstableApi
fun isTransferring(work: WorkInfo): Boolean = work.state == WorkInfo.State.RUNNING && !work.progress.getBoolean(DownloadWorker.WAITING, false)

/** The download that transfers data now, if there is one. */
@UnstableApi
fun currentDownload(state: AppState): WorkInfo? = queuedDownloads(state).firstOrNull(::isTransferring)

/** The id of the track of a download. */
fun trackIdOf(work: WorkInfo): String? = work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")

/**
 * The card of the download that runs now, in the list of what is on the device. It shows the track and how far its download is,
 * and the number of downloads that wait for their turn. A tap opens the queue.
 */
@Composable
fun CurrentDownloadCard(track: Track?, percent: Int, waiting: Int, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("current-download")) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OpenTuneIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(track?.title ?: stringResource(R.string.downloads_unnamed), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(stringResource(R.string.download_current_status, percent), waiting.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_waiting, it, it) }).joinToString(" · "),
                        Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(OpenTuneIcons.ChevronRight, null)
            }
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), drawStopIndicator = {})
        }
    }
}

/** The queue of downloads. Each download can be cancelled, and a failed one can be tried again. */
@UnstableApi
@Composable
fun DownloadQueuePage(state: AppState, vm: MainViewModel) {
    val context = LocalContext.current
    val queue = queuedDownloads(state)
    LazyColumn(Modifier.fillMaxSize().testTag("download-queue"), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (queue.isEmpty()) item {
            CenteredInFreeSpace(.6f) {
                Text(stringResource(R.string.downloads_queue_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(queue, key = { it.id }) { work ->
            val track = state.downloadTracks[trackIdOf(work)]
            val transferring = isTransferring(work)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track?.title ?: stringResource(R.string.downloads_unnamed), style = MaterialTheme.typography.titleSmall)
                        Text(when (work.state) {
                            WorkInfo.State.RUNNING -> if (transferring) stringResource(R.string.download_progress, work.progress.getInt("progress", 0)) else stringResource(R.string.download_queued)
                            WorkInfo.State.FAILED -> context.downloadErrorText(work.outputData.getString("error"))
                            WorkInfo.State.BLOCKED -> stringResource(R.string.download_waiting)
                            else -> if (state.settings.downloadOnWifi) stringResource(R.string.download_queued_wifi) else stringResource(R.string.download_queued)
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!work.state.isFinished) IconButton(onClick = { vm.cancelDownload(work) }) { Icon(OpenTuneIcons.Close, stringResource(R.string.download_cancel)) }
                    else if (track != null) TextButton(onClick = { vm.download(track) }) { Text(stringResource(R.string.action_retry)) }
                }
                if (transferring) LinearProgressIndicator(progress = { work.progress.getInt("progress", 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), drawStopIndicator = {})
            }
        }
        // Only a failed download stays in the list, so this is the only case where there is something to clear.
        if (queue.any { it.state == WorkInfo.State.FAILED }) item { TextButton(onClick = vm::clearDownloadHistory, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.downloads_clear)) } }
    }
}
