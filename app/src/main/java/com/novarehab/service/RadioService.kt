package com.novarehab.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.novarehab.R

class RadioService : Service() {

    private var player: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var retryCount = 0
    private var currentUrl = ""
    private var currentName = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val CHANNEL_ID = "radio_channel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PLAY   = "ACTION_PLAY"
        const val ACTION_STOP   = "ACTION_STOP"
        const val ACTION_DUCK   = "ACTION_DUCK"
        const val ACTION_UNDUCK = "ACTION_UNDUCK"
        const val EXTRA_URL     = "EXTRA_URL"
        const val EXTRA_NAME    = "EXTRA_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        buildPlayer()
    }

    private fun buildPlayer() {
        // HTTP data source z vsemi potrebnimi nastavitvami za radio
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0")
            setConnectTimeoutMs(20_000)
            setReadTimeoutMs(20_000)
            setAllowCrossProtocolRedirects(true)
            setKeepPostFor302Redirects(true)
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        // LoadControl - optimizirano za radio streaming (ne za video)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,   // min buffer 10s
                30_000,   // max buffer 30s
                2_000,    // buffer za start 2s
                2_000     // buffer za restart po buffering 2s
            )
            .build()

        player?.release()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Samodejni ponovni poskus (max 3x)
                        if (retryCount < 3) {
                            retryCount++
                            handler.postDelayed({
                                p.prepare()
                                p.play()
                            }, 3000L * retryCount)
                        }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) retryCount = 0
                    }
                })
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url  = intent.getStringExtra(EXTRA_URL)  ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
                currentUrl = url
                currentName = name
                retryCount = 0
                requestAudioFocusAndPlay(url, name)
            }
            ACTION_STOP -> {
                handler.removeCallbacksAndMessages(null)
                abandonAudioFocus()
                stopPlayback()
                stopSelf()
            }
            ACTION_DUCK   -> player?.volume = 0.15f
            ACTION_UNDUCK -> player?.volume = 1.0f
        }
        return START_STICKY
    }

    private fun requestAudioFocusAndPlay(url: String, name: String) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focus ->
                when (focus) {
                    AudioManager.AUDIOFOCUS_LOSS           -> { stopPlayback() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.volume = 0.15f
                    AudioManager.AUDIOFOCUS_GAIN           -> player?.volume = 1.0f
                }
            }.build()

        val result = audioManager?.requestAudioFocus(focusRequest!!)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playStream(url, name)
        }
    }

    private fun playStream(url: String, name: String) {
        player?.apply {
            stop()
            clearMediaItems()
            volume = 1.0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
        startForeground(NOTIFICATION_ID, buildNotification(name))
    }

    private fun stopPlayback() {
        player?.stop()
        player?.clearMediaItems()
        stopForeground(true)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    private fun buildNotification(name: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova Rehab Radio")
            .setContentText("▶ $name")
            .setSmallIcon(R.drawable.ic_radio)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Radio predvajanje", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        abandonAudioFocus()
        player?.release()
        player = null
        super.onDestroy()
    }
}
