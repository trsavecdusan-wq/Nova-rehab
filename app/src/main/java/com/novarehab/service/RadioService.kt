package com.novarehab.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer

object RadioService {

    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // prepreči spam retry (in piskanje)
    private var retryCount = 0
    private val maxRetries = 3

    private fun ensurePlayer(context: Context) {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (retryCount < maxRetries) {
                            retryCount++
                            // poskusi znova čez 3 sekunde
                            mainHandler.postDelayed({
                                player?.prepare()
                                player?.playWhenReady = true
                            }, 3000)
                        } else {
                            stop()
                        }
                    }
                })
            }
        }
    }

    fun play(context: Context, url: String) {
        stop() // vedno najprej ustavi staro

        if (url.startsWith("music://")) {
            // lokalna glasba (USB) – trenutno ne implementiramo
            return
        }

        ensurePlayer(context)

        val mediaItem = MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        retryCount = 0
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        retryCount = 0
    }

    fun release() {
        player?.release()
        player = null
    }
}
