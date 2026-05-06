package com.novarehab.utils

import android.content.Context
import android.media.MediaPlayer
import com.novarehab.core.storage.NovaRehabPaths
import org.json.JSONObject
import java.io.File

data class MusicTrack(
    val file: File,
    val title: String = file.nameWithoutExtension,
    val playCount: Int = 0,
    val lastPlayed: Long = 0
)

class MusicManager(private val context: Context) {

    private val paths = NovaRehabPaths(context.applicationContext)
    private val musicDir = paths.musicDir.also { it.mkdirs() }
    private val historyFile = File(paths.rootDir, "music_history.json")
    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex = 0
    private var playlist = mutableListOf<MusicTrack>()
    private var isPlaying = false
    private var onTrackChange: ((MusicTrack) -> Unit)? = null
    private var onPlayStateChange: ((Boolean) -> Unit)? = null

    val musicPath: File
        get() = musicDir

    fun setOnTrackChange(listener: (MusicTrack) -> Unit) {
        onTrackChange = listener
    }

    fun setOnPlayStateChange(listener: (Boolean) -> Unit) {
        onPlayStateChange = listener
    }

    fun loadPlaylist() {
        val history = loadHistory()
        val audioExtensions = setOf("mp3", "aac", "ogg", "flac", "m4a", "wav")

        val files = musicDir.listFiles()
            ?.filter { it.extension.lowercase() in audioExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        playlist = files.map { file ->
            val hist = history[file.name]
            MusicTrack(
                file = file,
                title = file.nameWithoutExtension,
                playCount = hist?.optInt("count", 0) ?: 0,
                lastPlayed = hist?.optLong("last", 0) ?: 0
            )
        }.toMutableList()

        playlist.sortBy { it.playCount }
        if (currentIndex >= playlist.size) currentIndex = 0
    }

    fun play() {
        if (playlist.isEmpty()) loadPlaylist()
        if (playlist.isEmpty()) return
        playTrack(currentIndex)
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        onPlayStateChange?.invoke(false)
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying = true
        onPlayStateChange?.invoke(true)
    }

    fun next() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playTrack(currentIndex)
    }

    fun previous() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        playTrack(currentIndex)
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        onPlayStateChange?.invoke(false)
    }

    fun isPlaying() = isPlaying

    fun currentTrack(): MusicTrack? = playlist.getOrNull(currentIndex)

    fun trackCount() = playlist.size

    private fun playTrack(index: Int) {
        val track = playlist.getOrNull(index) ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(track.file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { next() }
            }
            isPlaying = true
            onTrackChange?.invoke(track)
            onPlayStateChange?.invoke(true)
            updateHistory(track)
        } catch (_: Exception) {
            if (playlist.size > 1) next() else stop()
        }
    }

    fun copyFromUsb(usbPath: File, onProgress: (Int, Int) -> Unit, onDone: (Int) -> Unit) {
        Thread {
            val importer = UsbMusicImportManager(context)
            val result = importer.importFromUsb(usbPath) { progress ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onProgress(progress.current, progress.total)
                }
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                loadPlaylist()
                onDone(result.copied)
            }
        }.start()
    }

    private fun loadHistory(): Map<String, JSONObject> {
        val result = mutableMapOf<String, JSONObject>()
        try {
            if (!historyFile.exists()) return result
            val json = JSONObject(historyFile.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.getJSONObject(key)
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun updateHistory(track: MusicTrack) {
        try {
            val history = loadHistory().toMutableMap()
            val obj = history[track.file.name] ?: JSONObject()
            obj.put("count", obj.optInt("count", 0) + 1)
            obj.put("last", System.currentTimeMillis())
            history[track.file.name] = obj

            val json = JSONObject()
            history.forEach { (k, v) -> json.put(k, v) }
            historyFile.writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        stop()
    }
}
