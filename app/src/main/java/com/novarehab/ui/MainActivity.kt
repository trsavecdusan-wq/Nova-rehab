package com.novarehab.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioService
import com.novarehab.service.UpdateService
import com.novarehab.utils.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private var currentStation = -1
    private var radioPlaying = false

    private val kioskHandler = Handler(Looper.getMainLooper())
    private var kioskRunnable: Runnable? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var speedGestureDetector: GestureDetector
    var activeLang = "sl"

    private val allCommItems = listOf(
        Triple("pomoc",     R.drawable.comm_pomoc,     Pair("Potrebujem pomoč, prosim pridite", "Мені потрібна допомога, будь ласка")),
        Triple("piti",      R.drawable.comm_piti,      Pair("Žejna sem, prinesite mi piti", "Я хочу пити")),
        Triple("jesti",     R.drawable.comm_jesti,     Pair("Lačna sem, bi rada jedla", "Я голодна, хочу їсти")),
        Triple("bolecina",  R.drawable.comm_bolecina,  Pair("Imam bolečine", "У мене болить")),
        Triple("kopalnica", R.drawable.comm_kopalnica, Pair("Potrebujem v kopalnico", "Мені потрібно в туалет")),
        Triple("dobro",     R.drawable.comm_dobro,     Pair("Dobro se počutim", "Я почуваюся добре")),
        Triple("slabo",     R.drawable.comm_slabo,     Pair("Ne počutim se dobro", "Я погано почуваюся")),
        Triple("utrujena",  R.drawable.comm_utrujena,  Pair("Utrujena sem", "Я втомилась")),
        Triple("mraz",      R.drawable.comm_mraz,      Pair("Mrzlica mi je", "Мені холодно")),
        Triple("vroce",     R.drawable.comm_vroce,     Pair("Vroče mi je", "Мені жарко")),
        Triple("hvala",     R.drawable.comm_hvala,     Pair("Hvala lepa", "Дякую щиро")),
        Triple("pridi_sem", R.drawable.comm_pridi_sem, Pair("Prosim pridi sem k meni", "Будь ласка, підійдіть до мене")),
        Triple("pocakaj",   R.drawable.comm_pocakaj,   Pair("Počakaj prosim", "Зачекай будь ласка")),
        Triple("zdravilo",  R.drawable.comm_zdravilo,  Pair("Čas je za zdravilo", "Час приймати ліки")),
        Triple("telefon",   R.drawable.comm_telefon,   Pair("Prinesite mi telefon", "Принесіть телефон")),
        Triple("tv",        R.drawable.comm_tv,        Pair("Vklopite televizijo", "Увімкніть телевізор")),
        Triple("postelja",  R.drawable.comm_postelja,  Pair("Rada bi ležala", "Хочу лягти")),
        Triple("okno",      R.drawable.comm_okno,      Pair("Odprite okno", "Відкрийте вікно")),
        Triple("vesela",    R.drawable.comm_vesela,    Pair("Vesela sem", "Я рада")),
        Triple("zalostna",  R.drawable.comm_zalostna,  Pair("Žalostna sem", "Мені сумно")),
        Triple("jezna",     R.drawable.comm_jezna,     Pair("Jezna sem", "Я сердита")),
        Triple("strah",     R.drawable.comm_strah,     Pair("Prestrašena sem", "Мені страшно")),
        Triple("tesnoba",   R.drawable.comm_tesnoba,   Pair("Tesnobno se počutim", "Мені тривожно")),
        Triple("objemi",    R.drawable.comm_objemi,    Pair("Bi me objel?", "Обійми мене"))
    )

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
        setupCommPager()
        setupVideoCallButton()
        setupVolumeSeekBar()
        setupClock()
        updatePatientName()

        // Poskusi Google TTS engine, sicer privzeti
        val googleTts = "com.google.android.tts"
        tts = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Nastavi jezik - če ni SL, vzemi privzetega
                val sl = tts?.setLanguage(java.util.Locale("sl", "SI"))
                if (sl == TextToSpeech.LANG_MISSING_DATA || sl == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(java.util.Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)
                ttsReady = true
            }
        }, googleTts)

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
            val name = stations.getOrNull(index)?.name ?: "P${index+1}"
            button.text = name.take(8)
            button.setOnClickListener {
                if (currentStation == index && radioPlaying) stopRadio()
                else playStation(index)
            }
        }
        // Radio ikona kot START/STOP
        binding.btnRadioToggle.setOnClickListener {
            if (radioPlaying) stopRadio()
            else playStation(if (currentStation >= 0) currentStation else 0)
        }
        binding.btnRadioToggle.setOnLongClickListener {
            showPinDialog()
            true
        }
    }

    private fun playStation(index: Int) {
        val stations = prefs.getRadioStations()
        if (index >= stations.size) return
        val station = stations[index]
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
        // Radio ikona menja barvo: zelena=predvaja, temna=stop
        binding.btnRadioToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (radioPlaying) 0xFFe94560.toInt() else 0xFF1b5e20.toInt()
        )
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
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val home = prefs.getHomeAddress()
                if (home.isEmpty()) {
                    // Pokaži dialog za vnos naslova
                    showHomeAddressDialog()
                } else {
                    openNavigation()
                }
                return true
            }
        })
        binding.tvSpeed.isClickable = true
        binding.tvSpeed.isFocusable = true
        binding.tvSpeed.setOnTouchListener { _, event ->
            speedGestureDetector.onTouchEvent(event)
            true
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startGps()
    }

    private fun showHomeAddressDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "npr. Dunajska cesta 1, Ljubljana"
            setPadding(40, 20, 40, 20)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Nastavi domači naslov")
            .setMessage("Vnesi naslov za navigacijo:")
            .setView(input)
            .setPositiveButton("Shrani") { _, _ ->
                val addr = input.text.toString().trim()
                if (addr.isNotEmpty()) {
                    prefs.saveHomeAddress(addr)
                    openNavigation()
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun startGps() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f) { loc ->
                    // Pokaži 0 če hitrost < 1 km/h (šum GPS)
                    val kmh = (loc.speed * 3.6).toInt()
                    binding.tvSpeed.text = if (kmh < 2) "0" else kmh.toString()
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
        // Satelitski pogled + navigacija
        try {
            // Odpri Google Maps v satelitskem načinu z navigacijo
            val uri = android.net.Uri.parse(
                "google.navigation:q=${android.net.Uri.encode(home)}&mode=w&layer=satellite"
            )
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            })
        } catch (e: Exception) {
            try {
                val uri = android.net.Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination=${android.net.Uri.encode(home)}&travelmode=walking&layer=satellite"
                )
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {}
        }
    }

    // ── KOMUNIKACIJA — VIEWPAGER (swipe) ─────────────────────────────────────

    private fun setupCommPager() {
        val adapter = CommPageAdapter(
            context = this,
            items = allCommItems,
            getLang = { activeLang },
            onSpeak = { text -> speakComm(text) }
        )
        binding.viewPagerComm.adapter = adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>
    }

    fun speakComm(text: String) {
        if (text.isEmpty()) return
        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_DUCK })
        }
        if (ttsReady) {
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "c${System.currentTimeMillis()}")
            Handler(Looper.getMainLooper()).postDelayed({
                if (radioPlaying) startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_UNDUCK })
            }, (text.length * 90 + 2000).toLong())
        } else {
            // TTS še ni pripravljen - inicializiraj znova
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "c${System.currentTimeMillis()}")
                }
            }
        }
    }

    // ── GLASNOST ─────────────────────────────────────────────────────────────

    private fun setupVolumeSeekBar() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        fun updateIndicator() {
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val pct = (cur.toFloat() / max * 5).toInt().coerceIn(0, 5)
            binding.tvVolLevel.text = "●".repeat(pct) + "○".repeat(5 - pct)
        }

        binding.btnVolUp.setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            updateIndicator()
        }
        binding.btnVolDown.setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            updateIndicator()
        }
        updateIndicator()
    }

    // ── URA + IME PACIENTA ────────────────────────────────────────────────────

    private fun setupClock() {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        clockRunnable = object : Runnable {
            override fun run() {
                binding.tvClock.text = fmt.format(Date())
                clockHandler.postDelayed(this, 30000L)
            }
        }
        clockHandler.post(clockRunnable!!)
    }

    private fun updatePatientName() {
        val name = prefs.getPatientName()
        binding.tvPatientName.text = name
        binding.tvPatientName.visibility = if (name.isEmpty()) View.GONE else View.VISIBLE
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
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO)
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
        if (requestCode == 100) startGps()
    }

    override fun onResume() {
        super.onResume()
        updatePatientName()

    }

    override fun onDestroy() {
        stopRadio()
        tts?.stop(); tts?.shutdown()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        clockRunnable?.let { clockHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Ustavi radio ko aplikacija gre v ozadje (ne samo med moduli)
        if (isFinishing) stopRadio()
    }
}
