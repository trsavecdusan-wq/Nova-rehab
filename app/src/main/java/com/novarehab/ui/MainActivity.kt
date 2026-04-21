package com.novarehab.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioService
import com.novarehab.service.UpdateService
import com.novarehab.utils.PrefsManager
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private var currentStation = -1
    private var radioPlaying = false

    private val kioskHandler = Handler(Looper.getMainLooper())
    private var kioskRunnable: Runnable? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var speedGestureDetector: GestureDetector

    private val commItems = listOf(
        Triple("pomoc",     R.drawable.comm_pomoc,     Pair("Potrebujem pomoč, prosim pridite", "Мені потрібна допомога")),
        Triple("piti",      R.drawable.comm_piti,      Pair("Žejna sem", "Я хочу пити")),
        Triple("jesti",     R.drawable.comm_jesti,     Pair("Lačna sem", "Я голодна")),
        Triple("bolecina",  R.drawable.comm_bolecina,  Pair("Imam bolečine", "У мене болить")),
        Triple("kopalnica", R.drawable.comm_kopalnica, Pair("Potrebujem v kopalnico", "Мені потрібно в туалет")),
        Triple("dobro",     R.drawable.comm_dobro,     Pair("Dobro se počutim", "Я почуваюся добре")),
        Triple("slabo",     R.drawable.comm_slabo,     Pair("Ne počutim se dobro", "Я погано почуваюся")),
        Triple("utrujena",  R.drawable.comm_utrujena,  Pair("Utrujena sem", "Я втомилась")),
        Triple("mraz",      R.drawable.comm_mraz,      Pair("Mrzlica mi je", "Мені холодно")),
        Triple("vroce",     R.drawable.comm_vroce,     Pair("Vroče mi je", "Мені жарко")),
        Triple("hvala",     R.drawable.comm_hvala,     Pair("Hvala lepa", "Дякую")),
        Triple("pridi_sem", R.drawable.comm_pridi_sem, Pair("Prosim pridi sem", "Будь ласка, підійдіть"))
    )

    // Aktivni jezik za komunikacijo - se bo samodejno preklapljal
    var activeLang = "sl"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        requestAllPermissions()
        setupRadio()
        setupSpeed()
        setupCommGrid()
        setupVideoCallButton()
        setupVolumeControls()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }

        startService(Intent(this, UpdateService::class.java))
    }

    // ── RADIO ─────────────────────────────────────────────────────────────────

    private fun setupRadio() {
        val stations = prefs.getRadioStations()
        val radioButtons = listOf(
            binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
            binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
        )
        radioButtons.forEachIndexed { index, button ->
            if (index < stations.size) {
                button.text = stations[index].name
                button.setOnClickListener {
                    if (currentStation == index && radioPlaying) stopRadio()
                    else playStation(index)
                }
            }
        }
        binding.btnRadioToggle.setOnClickListener {
            if (radioPlaying) stopRadio()
            else playStation(if (currentStation >= 0) currentStation else 0)
        }
        binding.tvRadioTitle.setOnLongClickListener {
            showPinDialog()
            true
        }
    }

    private fun playStation(index: Int) {
        val stations = prefs.getRadioStations()
        if (index >= stations.size) return
        val station = stations[index]
        if (station.url == "usb://local") {
            Toast.makeText(this, "USB predvajanje ni podprto", Toast.LENGTH_SHORT).show()
            return
        }
        startForegroundService(Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_URL, station.url)
            putExtra(RadioService.EXTRA_NAME, station.name)
        })
        currentStation = index
        radioPlaying = true
        updateRadioUI()
    }

    fun stopRadio() {
        startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_STOP })
        radioPlaying = false
        updateRadioUI()
    }

    private fun updateRadioUI() {
        binding.btnRadioToggle.apply {
            text = if (radioPlaying) "⏹" else "▶"
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (radioPlaying) 0xFFb71c1c.toInt() else 0xFF1b5e20.toInt()
            )
        }
        val radioButtons = listOf(
            binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
            binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
        )
        radioButtons.forEachIndexed { index, btn ->
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (index == currentStation && radioPlaying) 0xFFe94560.toInt() else 0xFF0f3460.toInt()
            )
        }
    }

    // ── HITROST ───────────────────────────────────────────────────────────────

    private fun setupSpeed() {
        speedGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                openNavigation()
                return true
            }
        })
        binding.tvSpeed.setOnTouchListener { _, event ->
            speedGestureDetector.onTouchEvent(event)
            false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startGps()
        }
    }

    private fun startGps() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                lm.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER, 1000L, 0f
                ) { loc ->
                    binding.tvSpeed.text = (loc.speed * 3.6).toInt().toString()
                }
            }
        } catch (e: Exception) {}
    }

    private fun openNavigation() {
        val home = prefs.getHomeAddress()
        if (home.isEmpty()) {
            Toast.makeText(this, "Nastavi domači naslov v Nastavitvah", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(home)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            })
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${android.net.Uri.encode(home)}")))
        }
    }

    // ── KOMUNIKACIJSKE IKONE ──────────────────────────────────────────────────

    private fun setupCommGrid() {
        val grid = binding.gridCommMain
        grid.removeAllViews()
        commItems.forEach { (id, iconRes, speeches) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(0xFF16213e.toInt())
                val lp = GridLayout.LayoutParams().apply {
                    width = 0; height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(3, 3, 3, 3)
                }
                layoutParams = lp
                val img = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val imgLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f; setMargins(6,6,6,2) }
                    layoutParams = imgLp
                    val customFile = File(getExternalFilesDir(null), "icons/$id.png")
                    if (customFile.exists()) setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                    else setImageResource(iconRes)
                }
                addView(img)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val speech = if (activeLang == "uk") speeches.second else speeches.first
                    speakComm(speech)
                }
            }
            grid.addView(cell)
        }
    }

    fun speakComm(text: String) {
        if (!ttsReady) return
        val locale = if (activeLang == "uk") Locale("uk", "UA") else Locale("sl", "SI")
        tts?.setLanguage(locale)
        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_DUCK })
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "comm")
        Handler(Looper.getMainLooper()).postDelayed({
            if (radioPlaying) {
                startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_UNDUCK })
            }
        }, (text.length * 80 + 1500).toLong())
    }

    // ── GLASNOST ──────────────────────────────────────────────────────────────

    private fun setupVolumeControls() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        fun updateBar() {
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val bar = findViewById<View>(R.id.volumeBar) ?: return
            val pct = cur.toFloat() / max.toFloat()
            val parent = bar.parent as? LinearLayout ?: return
            (bar.layoutParams as LinearLayout.LayoutParams).weight = pct
            (parent.getChildAt(1).layoutParams as LinearLayout.LayoutParams).weight = 1f - pct
            bar.requestLayout()
        }

        binding.btnVolUp.setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            updateBar()
        }
        binding.btnVolDown.setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            updateBar()
        }
        updateBar()
    }

    // ── VIDEO KLIC ────────────────────────────────────────────────────────────

    private fun setupVideoCallButton() {
        binding.btnVideoCall.setOnClickListener {
            if (radioPlaying) stopRadio()
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
    }

    // ── KIOSK ─────────────────────────────────────────────────────────────────

    override fun onBackPressed() { /* blokirano */ }

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

    // ── PIN / NASTAVITVE ──────────────────────────────────────────────────────

    private fun showPinDialog() {
        PinDialog(this) { showAdminMenu() }.show()
    }

    private fun showAdminMenu() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Administrator")
            .setItems(arrayOf("Nastavitve aplikacije", "Izhod v Android")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> exitToAndroid()
                }
            }.show()
    }

    private fun exitToAndroid() {
        moveTaskToBack(true)
        val minutes = prefs.getKioskReturnMinutes()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        kioskRunnable = Runnable {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
        }
        kioskHandler.postDelayed(kioskRunnable!!, minutes * 60 * 1000L)
    }

    // ── PERMISSIONS ───────────────────────────────────────────────────────────

    private fun requestAllPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        else
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
            startGps()
        }
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
