package io.github.yyan115.opentune.data

import io.github.yyan115.opentune.downloadErrorCode
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.offline.DashDownloader
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.StreamManifest
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.playback.AudioSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InterruptedIOException

@UnstableApi
class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app get() = applicationContext as OpenTuneApp
    private var lastProgress = -1
    private var title = ""
    private var lastNotified = 0L
    private var inForeground = false

    companion object {
        /** The share of the progress bar that the arrival of the segments takes. The rest is the writing of the file. */
        private const val SEGMENT_SHARE = 85

        /** The key of the progress data that says a download waits for its turn. */
        const val WAITING = "waiting"

        // WorkManager starts every download that it may run. They wait for the ones that were queued before them, and only one transfers at a time.
        private val transfer = Mutex()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val encoded = inputData.getString("track") ?: return@withContext Result.failure()
        val track = JsonCodec.decodeTrack(JSONObject(encoded))
        title = track.title
        if (track.onDevice || app.library.get(track.id)?.onDevice == true) return@withContext Result.success()
        if (app.settings.state.value.offline) return@withContext Result.retry()
        setProgress(workDataOf(WAITING to true))
        waitForTurn()
        transfer.withLock { download(track) }
    }

    /** Waits until every download that was queued before this one is finished. */
    private suspend fun waitForTurn() {
        val me = DownloadOrder.Entry(id.toString(), DownloadOrder.orderOf(tags) ?: Long.MAX_VALUE, active = true)
        WorkManager.getInstance(applicationContext).getWorkInfosByTagFlow("audio-download").first { infos ->
            val all = infos.map { DownloadOrder.Entry(it.id.toString(), DownloadOrder.orderOf(it.tags) ?: Long.MAX_VALUE, active = !it.state.isFinished) }
            DownloadOrder.waitsFor(me, all).isEmpty()
        }
    }

    private suspend fun download(track: Track): Result {
        progress(0)
        var temporary: File? = null
        var dash: DashDownloader? = null
        var stored: DownloadFiles.Stored? = null
        return try {
            enterForeground(track.title)
            val stream = app.tidal.stream(track.id, app.settings.state.value.quality)
            if (stream.dashManifest != null) {
                // The segments arrive first, which is most of the work. Then they become one tagged audio file in the download folder.
                val item = MediaItem.Builder().setUri(app.sources.manifestUri(stream)).setMimeType(MimeTypes.APPLICATION_MPD).build()
                val downloader = DashDownloader.Factory(app.sources.downloadSource()).setExecutor(java.util.concurrent.Executor { it.run() }).create(item)
                dash = downloader
                try {
                    downloader.download { _, _, percent ->
                        if (isStopped || app.settings.state.value.offline) throw InterruptedIOException("Download paused")
                        progress((percent * SEGMENT_SHARE / 100).toInt())
                    }
                    app.sources.saveOffline(track.id, stream)
                } finally { if (isStopped) downloader.cancel() }
                val spec = app.exporter.spec(track, stream)
                val cover = if (spec.mimeType == "audio/flac") app.exporter.cover(track) else null
                stored = app.downloadFiles.create(spec.fileName, spec.mimeType) { output ->
                    app.exporter.writeDash(track, output, cover) { percent -> progress(SEGMENT_SHARE + percent * (100 - SEGMENT_SHARE) / 100) }
                }
                // The file is complete, so the segments are not needed any more.
                runCatching { app.sources.removeOffline(track.id) }
            } else {
                val url = requireNotNull(stream.uri)
                StreamManifest.requireHttps(url)
                val directory = File(app.filesDir, "downloads").apply { mkdirs() }
                val part = File(directory, AudioSources.safeId(track.id) + ".part")
                temporary = part
                app.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "The audio download failed (HTTP ${response.code})." }
                    val length = response.body.contentLength()
                    var total = 0L
                    response.body.byteStream().use { input -> part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped || app.settings.state.value.offline) throw InterruptedIOException("Download paused")
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count); total += count
                            check(total <= 2L * 1024 * 1024 * 1024) { "The audio file exceeds the download limit." }
                            if (length > 0) progress((total * SEGMENT_SHARE / length).toInt())
                        }
                        output.fd.sync()
                    } }
                    check(total > 0 && (length <= 0 || total == length)) { "The download was interrupted." }
                }
                val header = ByteArray(4).also { bytes -> part.inputStream().use { it.read(bytes) } }
                val flac = header.contentEquals("fLaC".toByteArray())
                stored = app.downloadFiles.create(app.exporter.fileName(track, if (flac) "flac" else "m4a"), if (flac) "audio/flac" else "audio/mp4") { output -> part.inputStream().use { it.copyTo(output) } }
                part.delete()
            }
            val file = checkNotNull(stored)
            app.library.save(track.copy(localUri = file.uri.toString(), quality = stream.qualityLabel))
            app.library.recordDownloadFile(file.key, track.id)
            progress(100)
            Result.success()
        } catch (error: Exception) {
            temporary?.delete()
            stored?.let { runCatching { app.downloadFiles.delete(it.uri) } }
            dash?.let { runCatching { it.remove() } }
            if (dash != null) app.sources.offlineFile(track.id).delete()
            if (isStopped) throw error
            if (app.settings.state.value.offline) return Result.retry()
            Result.failure(workDataOf("error" to downloadErrorCode(error)))
        }
    }

    /**
     * Shows the download as the notification of a foreground service. Android 12 and later refuse that for work that starts
     * while the app is in the background, such as a download that waited for Wi-Fi. The download then runs without a notification.
     */
    private suspend fun enterForeground(title: String) {
        inForeground = try { setForeground(notification(title, 0)); true }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { false }
    }

    private fun progress(value: Int) {
        val percent = value.coerceIn(0, 100)
        if (percent > lastProgress) {
            lastProgress = percent
            setProgressAsync(workDataOf("progress" to percent))
            // The notification follows the download. Android drops updates that come too fast, so it changes about once a second.
            val now = android.os.SystemClock.elapsedRealtime()
            if (inForeground && (percent == 100 || now - lastNotified >= 900)) { lastNotified = now; setForegroundAsync(notification(title.ifBlank { applicationContext.getString(R.string.download_preparing) }, percent)) }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notification(applicationContext.getString(R.string.download_preparing), 0)
    private fun notification(title: String, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", applicationContext.getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW))
        val cancel = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = Notification.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(if (percent > 0) applicationContext.getString(R.string.download_notification_progress, percent) else applicationContext.getString(R.string.download_notification_text))
            .setProgress(100, percent, percent == 0).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(applicationContext, R.drawable.ic_notification), applicationContext.getString(R.string.download_cancel), cancel).build()).build()
        val notificationId = 2100 + (id.hashCode() and 1023)
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
