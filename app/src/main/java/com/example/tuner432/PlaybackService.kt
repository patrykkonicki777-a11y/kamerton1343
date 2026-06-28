package com.example.tuner432

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.concurrent.Executors

/**
 * Usługa w tle: MediaSession (ekran blokady, Bluetooth), pitch 432/440,
 * tytuły utworów (ICY + API ZPR) oraz auto-pauza/wznawianie przy Bluetooth.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var injecting: MetadataInjectingPlayer
    private lateinit var audioManager: AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pollExecutor = Executors.newSingleThreadExecutor()
    private var currentNpId: Int = -1
    private var currentStation: String = ""

    // --- Bluetooth: pamięć "grało zanim się rozłączyło" ---
    private var resumeOnReconnect = false
    private var netRetries = 0

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Wyjście audio zniknęło (auto zgaszone / BT rozłączony / słuchawki wyjęte)
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val p = mediaSession?.player ?: return
                if (p.isPlaying) {
                    p.pause()
                    resumeOnReconnect = true
                }
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            if (added == null) return
            val bt = added.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            // Auto wróciło/BT się połączył -> wznów, jeśli wcześniej grało
            if (bt && resumeOnReconnect) {
                mediaSession?.player?.play()
                resumeOnReconnect = false
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val npId = currentNpId
            if (npId <= 0) return
            pollExecutor.execute {
                val track = NowPlayingProvider.fetch(npId)
                if (track != null) {
                    mainHandler.post {
                        if (currentNpId == npId) {
                            val b = MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setStation(currentStation)
                            track.artUrl?.let { b.setArtworkUri(Uri.parse(it)) }
                            injecting.setNowPlaying(b.build())
                        }
                    }
                }
            }
            mainHandler.postDelayed(this, 8_000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Kamerton432/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(attrs, true)
            .build()
        exo.playbackParameters = PlaybackParameters(1f, 432f / 440f)

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updatePoller(item)
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) stopPoller() else updatePoller(exo.currentMediaItem)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) netRetries = 0
                if (state == Player.STATE_IDLE) resumeOnReconnect = false   // user zatrzymał
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Stream na żywo: przy błędzie sieci (timeout itp.) ponów łączenie
                if (exo.playWhenReady && error.errorCode in 2000..2999 && netRetries < 30) {
                    netRetries++
                    mainHandler.postDelayed({
                        try {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        } catch (_: Exception) {}
                    }, 3000)
                }
            }
        })

        injecting = MetadataInjectingPlayer(exo)
        mediaSession = MediaSession.Builder(this, injecting).build()

        // Bluetooth/słuchawki
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
    }

    private fun updatePoller(item: MediaItem?) {
        val extras = item?.mediaMetadata?.extras
        val npId = extras?.getInt("npId", -1) ?: -1
        currentStation = item?.mediaMetadata?.station?.toString()
            ?: item?.mediaMetadata?.title?.toString() ?: ""
        if (npId > 0) {
            if (currentNpId != npId) {
                currentNpId = npId
                mainHandler.removeCallbacks(pollRunnable)
                mainHandler.post(pollRunnable)
            }
        } else {
            stopPoller()
        }
    }

    private fun stopPoller() {
        currentNpId = -1
        mainHandler.removeCallbacks(pollRunnable)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopPoller()
        pollExecutor.shutdown()
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        if (::audioManager.isInitialized) audioManager.unregisterAudioDeviceCallback(deviceCallback)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
