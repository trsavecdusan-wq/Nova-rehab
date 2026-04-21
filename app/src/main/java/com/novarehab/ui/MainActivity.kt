package com.novarehab.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.location.Location
import android.media.AudioManagerListener
import android.location.Location
import android.media.AudioManagerManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    private val PERMISSIONS_REQUEST = 100

    // Radio
    private var currentStation = -1
    private var radioPlaying = false

    // Kiosk — auto return timer
    private val kioskHandler = Handler(Looper.getMainLooper())
    private var kioskRunnable: Runnable? = null

    // GPS hitrost
    private var locationManager: LocationManager? = null

    // Speed gesture detector za dvojni klik
    private lateinit var speedGestureDetector: GestureDetector

    // TTS za komunikacijo
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Privzete komunikacijske ikone (prvih 12)
    private val commItems = listOf(
        Triple("pomoc",     R.drawable.comm_pomoc,     "Potrebujem pomoč, prosim pridite"),
        Triple("piti",      R.drawable.comm_piti,      "Žejna sem, prosim prinesite mi piti"),
        Triple("jesti",     R.drawable.comm_jesti,     "Lačna sem, bi rada jedla"),
        Triple("bolecina",  R.drawable.comm_bolecina,  "Imam bolečine"),
        Triple("kopalnica", R.drawable.comm_kopalnica, "Potrebujem v kopalnico"),
        Triple("dobro",     R.drawable.comm_dobro,     "Dobro se počutim"),
        Triple("slabo",     R.drawable.comm_slabo,     "Ne počutim se dobro"),
        Triple("utrujena",  R.drawable.comm_utrujena,  "Utrujena sem, rada bi počivala"),
        Triple("mraz",      R.drawable.comm_mraz,      "Mrzlica mi je"),
        Triple("vroce",     R.drawable.comm_vroce,     "Vroče mi je"),
        Triple("hvala",     R.drawable.comm_hvala,     "Hvala lepa"),
        Triple("pridi_sem", R.drawable.comm_pridi_sem, "Prosim pridi sem k meni")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KIOSK: zaslon vedno prižgan, polnoekranski
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        requestAllPermissions()
        setupRadio()
        setupSpeedDisplay()
        setupCommGrid()
        setupVideoCallButton()
        setupKioskMode()

        // TTS za komunikacijo
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("sl", "SI"))
                tts?.setSpeechRate(0.85f)
                ttsReady = true
            }
        }

        setupVolumeControls()
        startService(Intent(this, UpdateService::class.java))
    }

    // ── RADIO ────────────────────────────────────────────────────────────────

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
                    if (currentStation == index && radioPlaying) {
                        stopRadio()
                    } else {
                        playStation(index)
                    }
                }
            }
        }

        // START/STOP toggle gumb
        binding.btnRadioToggle.setOnClickListener {
            if (radioPlaying) stopRadio() else {
                if (currentStation >= 0) playStation(currentStation)
                else playStation(0)
            }
        }

        // Dolg pritisk na RADIO naslov → nastavitve
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
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_URL, station.url)
            putExtra(RadioService.EXTRA_NAME, station.name)
        }
        startForegroundService(intent)
        currentStation = index
        radioPlaying = true
        updateRadioUI()
    }

    private fun stopRadio() {
        startService(Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        })
        radioPlaying = false
        updateRadioUI()
    }

    private fun updateRadioUI() {
        // Toggle gumb barva
        binding.btnRadioToggle.apply {
            text = if (radioPlaying) "⏹" else "▶"
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (radioPlaying) 0xFFb71c1c.toInt() else 0xFF1b5e20.toInt()
            )
        }
        // Aktivna postaja se obarva rdeče
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

    // ── HITROST + NAVIGACIJA ──────────────────────────────────────────────────

    private fun setupSpeedDisplay() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }

        // Dvojni klik na hitrost → navigacija
        speedGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                openNavigation()
                return true
            }
        })

        binding.tvSpeed.setOnTouchListener { v, event ->
            speedGestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val speedKmh = (location.speed * 3.6).toInt()
                        binding.tvSpeed.text = speedKmh.toString()
                    }
                }
            )
        } catch (e: Exception) {}
    }

    private fun openNavigation() {
        val homeAddress = prefs.getHomeAddress()
        if (homeAddress.isEmpty()) {
            Toast.makeText(this, "Nastavi domači naslov v Nastavitvah", Toast.LENGTH_SHORT).show()
            return
        }
        // Google Maps satelitski pogled, 1km radius, navigacija do doma
        val uri = Uri.parse("google.navigation:q=${Uri.encode(homeAddress)}&mode=w")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Google Maps ni nameščen
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(homeAddress)}&travelmode=walking")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    // ── KOMUNIKACIJSKE IKONE NA GLAVNI STRANI ────────────────────────────────

    private fun setupCommGrid() {
        val grid = binding.gridCommMain
        grid.removeAllViews()

        commItems.forEach { (id, iconRes, speech) ->
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

                // Slika
                val img = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val imgLp = LinearLayout.LayoutParams(0, 0).apply {
                        weight = 1f
                        width = LinearLayout.LayoutParams.MATCH_PARENT
                        setMargins(6, 6, 6, 2)
                    }
                    layoutParams = imgLp
                    // Custom slika ali vgrajena
                    val customFile = File(getExternalFilesDir(null), "icons/$id.png")
                    if (customFile.exists()) {
                        setImageBitmap(android.graphics.BitmapFactory.decodeFile(customFile.absolutePath))
                    } else {
                        setImageResource(iconRes)
                    }
                }
                addView(img)

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    speakComm(speech)
                }
            }
            grid.addView(cell)
        }

        // Gumb za celoten komunikacijski modul (zadnja celica) — opcijsko
        // Prezrimo za zdaj, 12 ikon zapolni mrežo
    }

    fun speakComm(text: String) {
        if (!ttsReady) return
        // Utišaj radio med govorom
        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_DUCK
            })
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "main_comm")
        // Po koncu govora obnovi radio z zamikom
        val delayMs = (text.length * 80 + 1500).toLong()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (radioPlaying) {
                startService(Intent(this, RadioService::class.java).apply {
                    action = RadioService.ACTION_UNDUCK
                })
            }
        }, delayMs)
    }

    // ── VIDEO KLIC ────────────────────────────────────────────────────────────

    private fun setupVideoCallButton() {
        binding.btnVideoCall.setOnClickListener {
            // Ustavi radio med video klicem
            if (radioPlaying) stopRadio()
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
    }

    // ── KIOSK NAČIN ──────────────────────────────────────────────────────────

    private fun setupKioskMode() {
        // Prepreči izhod z Back tipko
    }

    override fun onBackPressed() {
        // Kiosk: Back tipka ne dela nič
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

    // ── PIN DIALOG → NASTAVITVE ───────────────────────────────────────────────

    private fun showPinDialog() {
        val dialog = PinDialog(this) {
            // Po pravilnem PIN-u pokaži meni
            showAdminMenu()
        }
        dialog.show()
    }

    private fun showAdminMenu() {
        val options = arrayOf("Nastavitve aplikacije", "Izhod v Android (začasno)")
        android.app.AlertDialog.Builder(this)
            .setTitle("Administrator")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> exitToAndroid()
                }
            }
            .show()
    }

    private fun exitToAndroid() {
        // Pokaži home screen, nastavi timer za vrnitev
        val returnMinutes = prefs.getKioskReturnMinutes()
        moveTaskToBack(true)
        // Timer za vrnitev
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        kioskRunnable = Runnable {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
        kioskHandler.postDelayed(kioskRunnable!!, returnMinutes * 60 * 1000L)
    }

    // ── PERMISSIONS ───────────────────────────────────────────────────────────

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Radio se ustavi ko zapustiš app (ne samo ko greš v drug modul)
        // Preverimo ali gremo v naš lasten modul ali ven iz app
    }

    override fun onStop() {
        super.onStop()
        // Če aplikacija ni več v ospredju (pravi izhod), ustavi radio
        if (!isChangingConfigurations) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.appTasks
            if (tasks.isEmpty() || tasks[0].taskInfo.numActivities == 0) {
                stopRadio()
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        locationManager?.removeUpdates {}
        super.onDestroy()
    }
}
