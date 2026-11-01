package io.github.yyan115.opentune.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yyan115.opentune.MainActivity
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.Normalization
import io.github.yyan115.opentune.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Custom commands that the app sends to the playback service. */
object PlaybackCommands {
    val SleepTimer = SessionCommand("opentune.sleep", Bundle.EMPTY)
    const val ARG_MINUTES = "minutes"
    const val ARG_END_OF_TRACK = "endOfTrack"
    const val EXTRA_ENDS_AT = "sleepEndsAt"
    const val EXTRA_AT_TRACK_END = "sleepAtTrackEnd"
}

@UnstableApi
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var app: OpenTuneApp
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var equalizerSession = C.AUDIO_SESSION_ID_UNSET
    private var sleepJob: Job? = null
    private var sleepAtTrackEnd = false
    private var bitPerfectDevice: AudioDeviceInfo? = null
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = applyBitPerfect()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = applyBitPerfect()
    }

    // Listening time of the current track, for the history and for scrobbling.
    private var listenedTrack: Track? = null
    private var listenStartedAt = 0L
    private var listenedMs = 0L
    private var playingSince = 0L

    override fun onCreate() {
        super.onCreate()
        app = application as OpenTuneApp
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(OpenTuneSourceFactory(app))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finishListening()
                val track = mediaItem?.let(::trackOf)
                if (track != null) {
                    scope.launch(Dispatchers.IO) { runCatching { app.library.played(track) } }
                    startListening(track)
                }
                applyNormalization()
                applyBitPerfect()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playingSince = SystemClock.elapsedRealtime()
                else if (playingSince != 0L) { listenedMs += SystemClock.elapsedRealtime() - playingSince; playingSince = 0 }
            }
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) finishListening() }
            override fun onAudioSessionIdChanged(audioSessionId: Int) { applyEqualizer(); applyBitPerfect() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtTrackEnd) clearSleepTimer()
            }
        })
        app.sources.onGains = { id -> Handler(Looper.getMainLooper()).post { if (player.currentMediaItem?.mediaId == id) applyNormalization() } }
        scope.launch { app.settings.state.collect { applyNormalization(); applyEqualizer(); applyBitPerfect() } }
        if (Build.VERSION.SDK_INT >= 23) audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(PlaybackCommands.SleepTimer).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
            }

            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (customCommand.customAction != PlaybackCommands.SleepTimer.customAction) return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                setSleepTimer(args.getInt(PlaybackCommands.ARG_MINUTES, 0), args.getBoolean(PlaybackCommands.ARG_END_OF_TRACK, false))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
        }).build()
    }

    private fun trackOf(item: MediaItem): Track? = item.mediaMetadata.extras?.getString("track")
        ?.let { runCatching { JsonCodec.decodeTrack(JSONObject(it)) }.getOrNull() }

    /** Turns loud tracks down by their ReplayGain value when the listener asked for it. */
    private fun applyNormalization() {
        val mode = app.settings.state.value.normalization
        val track = player.currentMediaItem?.let(::trackOf)
        if (mode == Normalization.OFF || track == null) { player.volume = 1f; return }
        val gains = app.sources.gains(track.id)
        player.volume = Normalization.volume(mode, gains?.track ?: track.replayGain, gains?.album)
    }

    private fun applyEqualizer() {
        val settings = app.settings.state.value
        val sessionId = player.audioSessionId
        if (!settings.equalizer || sessionId == C.AUDIO_SESSION_ID_UNSET) {
            runCatching { equalizer?.release() }; equalizer = null; equalizerSession = C.AUDIO_SESSION_ID_UNSET; return
        }
        runCatching {
            if (equalizer == null || equalizerSession != sessionId) {
                equalizer?.release()
                equalizer = Equalizer(0, sessionId); equalizerSession = sessionId
            }
            val effect = requireNotNull(equalizer)
            val range = effect.bandLevelRange
            for (band in 0 until effect.numberOfBands) {
                val level = settings.equalizerLevels.getOrNull(band) ?: 0
                effect.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            effect.enabled = true
        }.onFailure { equalizer = null }
    }

    /** Asks Android to send the stream to a USB DAC unchanged. This is experimental and needs Android 14 or later. */
    private fun applyBitPerfect() {
        if (Build.VERSION.SDK_INT < 34) return
        runCatching {
            val attributes = player.audioAttributes.audioAttributesV21.audioAttributes
            val usb = if (app.settings.state.value.bitPerfect) audioManager.getAudioDevicesForAttributes(attributes).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } else null
            bitPerfectDevice?.let { previous -> if (previous.id != usb?.id) { audioManager.clearPreferredMixerAttributes(attributes, previous); bitPerfectDevice = null } }
            if (usb == null) return
            val supported = audioManager.getSupportedMixerAttributes(usb).filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            val rate = player.audioFormat?.sampleRate
            val choice = supported.firstOrNull { it.format.sampleRate == rate } ?: supported.maxByOrNull { it.format.sampleRate } ?: return
            audioManager.setPreferredMixerAttributes(attributes, usb, choice)
            bitPerfectDevice = usb
        }
    }

    private fun setSleepTimer(minutes: Int, endOfTrack: Boolean) {
        clearSleepTimer()
        val extras = Bundle()
        when {
            minutes > 0 -> {
                val endsAt = System.currentTimeMillis() + minutes * 60_000L
                extras.putLong(PlaybackCommands.EXTRA_ENDS_AT, endsAt)
                sleepJob = scope.launch { delay(minutes * 60_000L); player.pause(); clearSleepTimer() }
            }
            endOfTrack -> {
                sleepAtTrackEnd = true; player.pauseAtEndOfMediaItems = true
                extras.putBoolean(PlaybackCommands.EXTRA_AT_TRACK_END, true)
            }
        }
        session?.setSessionExtras(extras)
    }

    private fun clearSleepTimer() {
        sleepJob?.cancel(); sleepJob = null
        if (sleepAtTrackEnd) { sleepAtTrackEnd = false; player.pauseAtEndOfMediaItems = false }
        session?.setSessionExtras(Bundle.EMPTY)
    }

    private fun startListening(track: Track) {
        listenedTrack = track; listenStartedAt = System.currentTimeMillis(); listenedMs = 0
        playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else 0
    }

    /** Stores the finished listen once it is long enough to count: 30 seconds, and half the track or four minutes. */
    private fun finishListening() {
        val track = listenedTrack ?: return
        if (playingSince != 0L) { listenedMs += SystemClock.elapsedRealtime() - playingSince; playingSince = 0 }
        val listened = listenedMs; val startedAt = listenStartedAt
        listenedTrack = null; listenedMs = 0
        val needed = if (track.durationMs > 0) minOf(track.durationMs / 2, 240_000L).coerceAtLeast(30_000L) else 30_000L
        if (listened < needed) return
        scope.launch(Dispatchers.IO) {
            runCatching { app.library.recordPlay(track, listened, startedAt) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = session?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        finishListening()
        if (Build.VERSION.SDK_INT >= 23) audioManager.unregisterAudioDeviceCallback(deviceCallback)
        app.sources.onGains = null
        runCatching { equalizer?.release() }
        scope.cancel(); session?.player?.release(); session?.release(); session = null; super.onDestroy()
    }
}
