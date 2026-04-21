package com.novarehab.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.novarehab.R
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RadioService : Service() {

    private var player: ExoPlayer? = null
    private val CHANNEL_ID = "radio_channel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_NAME = "EXTRA_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("NovaRehab/1.0")

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
                playStream(url, name)
            }
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun playStream(url: String, name: String) {
        player?.apply {
            stop()
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

    private fun buildNotification(name: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova Rehab Radio")
            .setContentText("▶ $name")
            .setSmallIcon(R.drawable.ic_radio)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Radio predvajanje",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
