package com.novarehab.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.novarehab.R

class RadioService : Service() {

    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentUrl: String? = null
    private var currentName: String = "Radio"
    private var retryCount = 0
    private val maxRetries = 3

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensurePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                currentName = intent.getStringExtra(EXTRA_NAME) ?: "Radio"

                if (url.isNullOrBlank() || url.startsWith("music://")) {
                    stopPlayback()
                    stopSelf()
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(currentName))
                    play(url)
                }
            }

            ACTION_STOP -> {
                stopPlayback()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_PAUSE_FOR_SPEECH,
            ACTION_DUCK -> {
                player?.volume = 0.04f
            }

            ACTION_RESUME_AFTER_SPEECH,
            ACTION_UNDUCK -> {
                player?.volume = 1.0f
            }
        }

        return START_STICKY
    }

    private fun ensurePlayer() {
        if (player != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(applicationContext).build().apply {
            setAudioAttributes(audioAttributes, true)
            volume = 1.0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    retryCurrentStation()
                }
            })
        }
    }

    private fun play(url: String) {
        ensurePlayer()
        retryCount = 0
        currentUrl = url

        player?.apply {
            stop()
            clearMediaItems()
            volume = 1.0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    private fun retryCurrentStation() {
        val url = currentUrl ?: return

        if (retryCount >= maxRetries) {
            stopPlayback()
            stopSelf()
            return
        }

        retryCount++
        mainHandler.postDelayed({
            player?.apply {
                stop()
                clearMediaItems()
                volume = 1.0f
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        }, RETRY_DELAY_MS)
    }

    private fun stopPlayback() {
        mainHandler.removeCallbacksAndMessages(null)

        player?.apply {
            stop()
            clearMediaItems()
            volume = 1.0f
        }

        retryCount = 0
        currentUrl = null
    }

    private fun buildNotification(stationName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("Rehab radio")
            .setContentText(stationName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rehab radio",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopPlayback()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "com.novarehab.radio.PLAY"
        const val ACTION_STOP = "com.novarehab.radio.STOP"
        const val ACTION_PAUSE_FOR_SPEECH = "com.novarehab.radio.PAUSE_FOR_SPEECH"
        const val ACTION_RESUME_AFTER_SPEECH = "com.novarehab.radio.RESUME_AFTER_SPEECH"
        const val ACTION_DUCK = "com.novarehab.radio.DUCK"
        const val ACTION_UNDUCK = "com.novarehab.radio.UNDUCK"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_NAME = "extra_name"

        private const val CHANNEL_ID = "nova_rehab_radio"
        private const val NOTIFICATION_ID = 1001
        private const val RETRY_DELAY_MS = 3000L
    }
}
