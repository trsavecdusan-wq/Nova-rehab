package com.novarehab.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.utils.MusicImportProgress
import com.novarehab.utils.MusicImportResult
import com.novarehab.utils.MusicManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager
import com.novarehab.utils.UsbMusicImportManager

class MusicActivity : AppCompatActivity() {

    private lateinit var musicManager: MusicManager
    private lateinit var prefs: PrefsManager
    private lateinit var usbImporter: UsbMusicImportManager
    private lateinit var stats: StatsManager
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackInfo: TextView
    private lateinit var tvCount: TextView
    private lateinit var btnPlayPause: Button
    private var importInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        musicManager = MusicManager(this)
        prefs = PrefsManager(this)
        usbImporter = UsbMusicImportManager(this)
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
                btnPlayPause.text = if (playing) "PAVZA" else "PREDVAJAJ"
                btnPlayPause.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (playing) 0xFFB71C1C.toInt() else 0xFF1B5E20.toInt()
                )

                if (!playing) {
                    stats.log(StatEvent.MUSIC_STOP)
                }
            }
        }

        musicManager.loadPlaylist()
        updateTrackInfo()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A2E.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }

        val tvTitle = TextView(this).apply {
            text = "GLASBA"
            textSize = 20f
            setTextColor(0xFFE94560.toInt())
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

        val tvNote = TextView(this).apply {
            text = "GLASBA"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(40), 0, dp(20))
            }
        }
        root.addView(tvNote)

        tvTrackTitle = TextView(this).apply {
            text = "Ni glasbe"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }
        root.addView(tvTrackTitle)

        tvTrackInfo = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(40))
            }
        }
        root.addView(tvTrackInfo)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(20))
            }
        }

        val btnPrev = Button(this).apply {
            text = "<<"
            textSize = 24f
            setBackgroundColor(0xFF0F3460.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
            setOnClickListener { musicManager.previous() }
        }

        btnPlayPause = Button(this).apply {
            text = "PREDVAJAJ"
            textSize = 16f
            setBackgroundColor(0xFF1B5E20.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
            setOnClickListener {
                if (musicManager.isPlaying()) {
                    musicManager.pause()
                } else if (musicManager.trackCount() > 0) {
                    musicManager.resume()
                } else {
                    musicManager.play()
                }
            }
        }

        val btnNext = Button(this).apply {
            text = ">>"
            textSize = 24f
            setBackgroundColor(0xFF0F3460.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
            setOnClickListener { musicManager.next() }
        }

        controls.addView(btnPrev)
        controls.addView(btnPlayPause)
        controls.addView(btnNext)
        root.addView(controls)

        tvCount = TextView(this).apply {
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

        val btnUsb = Button(this).apply {
            text = "UVOZI GLASBO Z USB"
            textSize = 16f
            setBackgroundColor(0xFF16213E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(20), 0, 0)
            }
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
            tvTrackTitle.text = if (musicManager.trackCount() == 0) {
                "Dodaj glasbo prek USB"
            } else {
                "Pritisni PREDVAJAJ"
            }
            tvTrackInfo.text = "Skupaj ${musicManager.trackCount()} pesmi"
        }

        tvCount.text = "Skupaj ${musicManager.trackCount()} pesmi"
    }

    private fun scanAndCopyUsb() {
        if (importInProgress) return

        if (!prefs.isUsbMusicImportEnabled()) {
            Toast.makeText(this, "USB uvoz glasbe je izklopljen.", Toast.LENGTH_LONG).show()
            return
        }

        val usbPath = usbImporter.findReadableUsbRoots().firstOrNull()
        if (usbPath == null) {
            Toast.makeText(this, "USB ključek ni najden. Priključite USB ključek.", Toast.LENGTH_LONG).show()
            return
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        val tvTitle = TextView(this).apply {
            text = "Kopiram glasbo"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val tvFile = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
        }
        val tvCountProgress = TextView(this).apply {
            text = "0 / 0"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(tvTitle)
            addView(tvFile)
            addView(tvCountProgress)
            addView(progressBar)
        }

        val progressDialog = AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create()

        importInProgress = true
        progressDialog.show()

        Thread {
            val result = usbImporter.importFromUsb(usbPath) { progress ->
                runOnUiThread {
                    renderImportProgress(progress, progressBar, tvFile, tvCountProgress)
                }
            }

            runOnUiThread {
                importInProgress = false
                progressDialog.dismiss()
                musicManager.loadPlaylist()
                updateTrackInfo()
                showImportResult(result)
            }
        }.start()
    }

    private fun renderImportProgress(
        progress: MusicImportProgress,
        progressBar: ProgressBar,
        tvFile: TextView,
        tvCountProgress: TextView
    ) {
        tvFile.text = progress.fileName
        tvCountProgress.text = "${progress.current} / ${progress.total}"
        progressBar.progress = if (progress.total == 0) 0 else (progress.current * 100 / progress.total)
    }

    private fun showImportResult(result: MusicImportResult) {
        if (result.message != null && result.copied == 0) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Uvoz glasbe je končan.")
            .setMessage(
                buildString {
                    appendLine("Kopirano: ${result.copied}")
                    appendLine("Preskočeno: ${result.skipped}")
                    if (result.duplicates > 0) appendLine("Dvojniki: ${result.duplicates}")
                    if (!result.message.isNullOrBlank()) append(result.message)
                }.trim()
            )
            .setPositiveButton("PREDVAJAJ GLASBO") { _, _ ->
                if (musicManager.trackCount() > 0) {
                    musicManager.play()
                } else {
                    Toast.makeText(this, "Ni glasbe za predvajanje.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("ZAPRI", null)
            .show()
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
