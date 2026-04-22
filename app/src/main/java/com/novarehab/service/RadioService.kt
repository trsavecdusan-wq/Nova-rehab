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
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.novarehab.R
import java.util.concurrent.TimeUnit

class RadioService : Service() {

    private var player: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
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

        // DefaultHttpDataSource z redirect podporo - brez OkHttp
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Počakaj 3s in poskusi znova
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            p.prepare()
                            p.play()
                        }, 3000)
                    }
                })
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url  = intent.getStringExtra(EXTRA_URL)  ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
                requestAudioFocusAndPlay(url, name)
            }
            ACTION_STOP   -> { abandonAudioFocus(); stopPlayback(); stopSelf() }
            ACTION_DUCK   -> player?.volume = 0.1f
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
                    AudioManager.AUDIOFOCUS_LOSS           -> stopPlayback()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.volume = 0.1f
                    AudioManager.AUDIOFOCUS_GAIN           -> player?.volume = 1.0f
                }
            }.build()
        audioManager?.requestAudioFocus(focusRequest!!)
        playStream(url, name)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    private fun playStream(url: String, name: String) {
        player?.apply {
            stop()
            volume = 1.0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
        startForeground(NOTIFICATION_ID, buildNotification(name))
    }

    private fun stopPlayback() {
        player?.stop()
        stopForeground(true)
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
        abandonAudioFocus()
        player?.release()
        player = null
        super.onDestroy()
    }
}
