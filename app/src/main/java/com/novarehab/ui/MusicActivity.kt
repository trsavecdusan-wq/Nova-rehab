package com.novarehab.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.utils.MusicManager
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager
import java.io.File

class MusicActivity : AppCompatActivity() {

    private lateinit var musicManager: MusicManager
    private lateinit var stats: StatsManager
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackInfo: TextView
    private lateinit var btnPlayPause: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        musicManager = MusicManager(this)
        stats = StatsManager(this)

        buildUI()

        musicManager.setOnTrackChange { track ->
            runOnUiThread {
                tvTrackTitle.text = track.title
                tvTrackInfo.text = "Predvajano ${track.playCount}x"
                stats.log(StatEvent.MUSIC_PLAY, track.title)
            }
        }

        musicManager.setOnPlayStateChange { playing ->
            runOnUiThread {
                btnPlayPause.text = if (playing) "⏸ PAVZA" else "▶ PREDVAJAJ"
                btnPlayPause.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (playing) 0xFFb71c1c.toInt() else 0xFF1b5e20.toInt()
                )
                if (!playing) stats.log(StatEvent.MUSIC_STOP)
            }
        }

        musicManager.loadPlaylist()
        updateTrackInfo()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1a1a2e.toInt())
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        // Glava
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }
        val tvTitle = TextView(this).apply {
            text = "🎵 GLASBA"
            textSize = 20f
            setTextColor(0xFFe94560.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnBack = Button(this).apply {
            text = "NAZAJ"
            setBackgroundColor(0xFF333355.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { finish() }
        }
        header.addView(tvTitle)
        header.addView(btnBack)
        root.addView(header)

        // Nota ikona
        val tvNote = TextView(this).apply {
            text = "🎵"
            textSize = 80f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 40, 0, 20) }
        }
        root.addView(tvNote)

        // Naslov pesmi
        tvTrackTitle = TextView(this).apply {
            text = "Ni glasbe"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        root.addView(tvTrackTitle)

        tvTrackInfo = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFaaaaaa.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
        }
        root.addView(tvTrackInfo)

        // Kontrole
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
        }

        val btnPrev = Button(this).apply {
            text = "⏮"
            textSize = 24f
            setBackgroundColor(0xFF0f3460.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(8, 0, 8, 0) }
            setOnClickListener { musicManager.previous() }
        }

        btnPlayPause = Button(this).apply {
            text = "▶ PREDVAJAJ"
            textSize = 16f
            setBackgroundColor(0xFF1b5e20.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply { setMargins(8, 0, 8, 0) }
            setOnClickListener {
                if (musicManager.isPlaying()) musicManager.pause()
                else if (musicManager.trackCount() > 0) musicManager.resume()
                else musicManager.play()
            }
        }

        val btnNext = Button(this).apply {
            text = "⏭"
            textSize = 24f
            setBackgroundColor(0xFF0f3460.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(8, 0, 8, 0) }
            setOnClickListener { musicManager.next() }
        }

        controls.addView(btnPrev)
        controls.addView(btnPlayPause)
        controls.addView(btnNext)
        root.addView(controls)

        // Info o številu pesmi
        val tvCount = TextView(this).apply {
            id = android.R.id.text1
            text = "Skupaj ${musicManager.trackCount()} pesmi"
            textSize = 13f
            setTextColor(0xFF666688.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(tvCount)

        // Gumb za USB kopiranje
        val btnUsb = Button(this).apply {
            text = "📁 Kopiraj z USB ključa"
            textSize = 14f
            setBackgroundColor(0xFF16213e.toInt())
            setTextColor(0xFFaaaaaa.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 0) }
            setOnClickListener { scanAndCopyUsb() }
        }
        root.addView(btnUsb)

        setContentView(root)
    }

    private fun updateTrackInfo() {
        val track = musicManager.currentTrack()
        if (track != null) {
            tvTrackTitle.text = track.title
            tvTrackInfo.text = if (track.playCount > 0) "Predvajano ${track.playCount}x" else ""
        } else {
            tvTrackTitle.text = if (musicManager.trackCount() == 0) "Dodaj glasbo prek USB ali NAS" else "Pritisni ▶"
            tvTrackInfo.text = "Skupaj ${musicManager.trackCount()} pesmi"
        }
    }

    private fun scanAndCopyUsb() {
        // Poišči USB storage
        val possiblePaths = listOf(
            File("/storage/usb"),
            File("/storage/usb0"),
            File("/mnt/usb"),
            File("/mnt/media_rw")
        )

        val usbPath = possiblePaths.firstOrNull { it.exists() && it.listFiles()?.isNotEmpty() == true }

        if (usbPath == null) {
            Toast.makeText(this, "USB ključ ni najden. Priključi USB ključ.", Toast.LENGTH_LONG).show()
            return
        }

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Kopiranje glasbe")
            .setMessage("Kopiram z USB ključa...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        musicManager.copyFromUsb(usbPath,
            onProgress = { current, total ->
                progressDialog.setMessage("Kopiram $current / $total...")
            },
            onDone = { copied ->
                progressDialog.dismiss()
                Toast.makeText(this, "Kopirano $copied novih pesmi. Skupaj ${musicManager.trackCount()}.", Toast.LENGTH_LONG).show()
                updateTrackInfo()
            }
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onDestroy() {
        musicManager.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
