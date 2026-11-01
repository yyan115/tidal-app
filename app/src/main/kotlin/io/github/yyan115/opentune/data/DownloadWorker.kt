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
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.StreamManifest
import io.github.yyan115.opentune.playback.AudioSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InterruptedIOException

@UnstableApi
class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app get() = applicationContext as OpenTuneApp
    private var lastProgress = -1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val encoded = inputData.getString("track") ?: return@withContext Result.failure()
        val track = JsonCodec.decodeTrack(JSONObject(encoded))
        if (track.isLocal || app.library.get(track.id)?.isLocal == true) return@withContext Result.success()
        if (app.settings.state.value.offline) return@withContext Result.retry()
        var temporary: File? = null
        var completedFile: File? = null
        var dash: DashDownloader? = null
        try {
            setForeground(notification(track.title, 0))
            val stream = app.tidal.stream(track.id, app.settings.state.value.quality)
            val localUri = if (stream.dashManifest != null) {
                val item = MediaItem.Builder().setUri(app.sources.manifestUri(stream)).setMimeType(MimeTypes.APPLICATION_MPD).build()
                val downloader = DashDownloader.Factory(app.sources.downloadSource()).setExecutor(java.util.concurrent.Executor { it.run() }).create(item)
                dash = downloader
                try {
                    downloader.download { _, _, percent ->
                        if (isStopped || app.settings.state.value.offline) throw InterruptedIOException("Download paused")
                        progress(percent.toInt())
                    }
                    app.sources.saveOffline(track.id, stream)
                    "opentune-offline://${AudioSources.safeId(track.id)}"
                } finally { if (isStopped) downloader.cancel() }
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
                            if (length > 0) progress((total * 100 / length).toInt())
                        }
                        output.fd.sync()
                    } }
                    check(total > 0 && (length <= 0 || total == length)) { "The download was interrupted." }
                }
                val header = ByteArray(4).also { bytes -> part.inputStream().use { it.read(bytes) } }
                val extension = if (header.contentEquals("fLaC".toByteArray())) ".flac" else ".m4a"
                val destination = File(directory, AudioSources.safeId(track.id) + extension)
                check(part.renameTo(destination)) { "The audio file could not be saved." }
                completedFile = destination
                android.net.Uri.fromFile(destination).toString()
            }
            app.library.save(track.copy(localUri = localUri, quality = stream.qualityLabel))
            Result.success()
        } catch (error: Exception) {
            temporary?.delete()
            completedFile?.delete()
            dash?.let { runCatching { it.remove() } }
            if (dash != null) app.sources.offlineFile(track.id).delete()
            if (isStopped) throw error
            if (app.settings.state.value.offline) return@withContext Result.retry()
            Result.failure(workDataOf("error" to downloadErrorCode(error)))
        }
    }

    private fun progress(value: Int) {
        val percent = value.coerceIn(0, 100)
        if (percent > lastProgress) {
            lastProgress = percent
            setProgressAsync(workDataOf("progress" to percent))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notification(applicationContext.getString(R.string.download_preparing), 0)
    private fun notification(title: String, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", applicationContext.getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(applicationContext.getString(R.string.download_notification_text))
            .setProgress(100, percent, percent == 0).setOngoing(true).build()
        val notificationId = 2100 + (id.hashCode() and 1023)
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }
}
