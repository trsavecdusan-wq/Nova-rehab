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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioBrowserService
import com.novarehab.service.RadioService
import com.novarehab.service.ReportWorker
import com.novarehab.service.UpdateService
import com.novarehab.utils.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var stats: StatsManager
    private lateinit var ttsManager: OpenAiTtsManager
    private var langDetector: LanguageDetector? = null

    private var currentStation = -1
    private var radioPlaying = false
    var activeLang = "sl"

    private val kioskHandler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private var kioskRunnable: Runnable? = null
    private var clockRunnable: Runnable? = null

    private lateinit var speedGestureDetector: GestureDetector
    private var tvLangIndicator: TextView? = null

    private val allCommItems = listOf(
        Triple("pomoc",     R.drawable.comm_pomoc,     Pair("Potrebujem pomoč, prosim pridite",     "Мені потрібна допомога, будь ласка")),
        Triple("piti",      R.drawable.comm_piti,      Pair("Žejna sem, prinesite mi piti",         "Я хочу пити")),
        Triple("jesti",     R.drawable.comm_jesti,     Pair("Lačna sem, bi rada jedla",             "Я голодна, хочу їсти")),
        Triple("bolecina",  R.drawable.comm_bolecina,  Pair("Imam bolečine",                        "У мене болить")),
        Triple("kopalnica", R.drawable.comm_kopalnica, Pair("Potrebujem v kopalnico",               "Мені потрібно в туалет")),
        Triple("dobro",     R.drawable.comm_dobro,     Pair("Dobro se počutim",                     "Я почуваюся добре")),
        Triple("slabo",     R.drawable.comm_slabo,     Pair("Ne počutim se dobro",                  "Я погано почуваюся")),
        Triple("utrujena",  R.drawable.comm_utrujena,  Pair("Utrujena sem, rada bi počivala",       "Я втомилась")),
        Triple("mraz",      R.drawable.comm_mraz,      Pair("Mrzlica mi je",                        "Мені холодно")),
        Triple("vroce",     R.drawable.comm_vroce,     Pair("Vroče mi je",                          "Мені жарко")),
        Triple("hvala",     R.drawable.comm_hvala,     Pair("Hvala lepa",                           "Дякую щиро")),
        Triple("pridi_sem", R.drawable.comm_pridi_sem, Pair("Prosim pridi sem k meni",              "Будь ласка, підійдіть до мене")),
        Triple("pocakaj",   R.drawable.comm_pocakaj,   Pair("Počakaj prosim",                       "Зачекай будь ласка")),
        Triple("zdravilo",  R.drawable.comm_zdravilo,  Pair("Čas je za zdravilo",                   "Час приймати ліки")),
        Triple("telefon",   R.drawable.comm_telefon,   Pair("Prinesite mi telefon",                 "Принесіть телефон")),
        Triple("tv",        R.drawable.comm_tv,        Pair("Vklopite televizijo",                  "Увімкніть телевізор")),
        Triple("postelja",  R.drawable.comm_postelja,  Pair("Rada bi ležala",                       "Хочу лягти")),
        Triple("okno",      R.drawable.comm_okno,      Pair("Odprite okno",                         "Відкрийте вікно")),
        Triple("vesela",    R.drawable.comm_vesela,    Pair("Vesela sem",                           "Я рада")),
        Triple("zalostna",  R.drawable.comm_zalostna,  Pair("Žalostna sem",                         "Мені сумно")),
        Triple("jezna",     R.drawable.comm_jezna,     Pair("Jezna sem",                            "Я сердита")),
        Triple("strah",     R.drawable.comm_strah,     Pair("Prestrašena sem",                      "Мені страшно")),
        Triple("tesnoba",   R.drawable.comm_tesnoba,   Pair("Tesnobno se počutim",                  "Мені тривожно")),
        Triple("objemi",    R.drawable.comm_objemi,    Pair("Bi me objel?",                         "Обійми мене"))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        stats = StatsManager(this)
        ttsManager = OpenAiTtsManager(this)
        ttsManager.initLocalTts()

        requestAllPermissions()
        setupRadio()
        setupSpeed()
        setupCommPager()
        setupVideoCallButton()
        setupVolumeControls()
        setupClock()
        updatePatientName()
        // LanguageDetector začasno izklopljen - povzroča zvoke
        // setupLanguageDetector()
        scheduleReports()

        stats.log(StatEvent.APP_START)
        startService(Intent(this, UpdateService::class.java))
    }

    // ── RADIO ─────────────────────────────────────────────────────────────────

    private fun setupRadio() {
        refreshRadioButtons()
        RadioBrowserService.fetchStations(this,
            onSuccess = { refreshRadioButtons() },
            onError = {}
        )
    }

    private fun refreshRadioButtons() {
        val stations = prefs.getRadioStations()
        val radioButtons = listOf(
            binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
            binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
        )
        radioButtons.forEachIndexed { index, button ->
            val station = stations.getOrNull(index)
            button.text = station?.name?.take(10) ?: "P${index+1}"
            button.setOnClickListener {
                if (station?.url == "music://local") {
                    startActivity(Intent(this, MusicActivity::class.java))
                } else if (currentStation == index && radioPlaying) {
                    stopRadio()
                } else {
                    playStation(index)
                }
            }
        }
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
        val station = stations.getOrNull(index) ?: return
        if (station.url == "music://local") return
        startForegroundService(Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_URL, station.url)
            putExtra(RadioService.EXTRA_NAME, station.name)
        })
        currentStation = index
        radioPlaying = true
        updateRadioUI()
        stats.log(StatEvent.RADIO_PLAY, station.name)
    }

    fun stopRadio() {
        startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_STOP })
        if (radioPlaying) stats.log(StatEvent.RADIO_STOP)
        radioPlaying = false
        updateRadioUI()
    }

    private fun updateRadioUI() {
        binding.btnRadioToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (radioPlaying) 0xFFe94560.toInt() else 0xFF1b5e20.toInt()
        )
        listOf(binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
               binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
        ).forEachIndexed { index, btn ->
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
                if (!prefs.isNavigationEnabled()) {
                    Toast.makeText(this@MainActivity, "Navigacija je izklopljena v Nastavitvah", Toast.LENGTH_SHORT).show()
                } else if (prefs.getHomeAddress().isEmpty()) {
                    showHomeAddressDialog()
                } else {
                    stats.log(StatEvent.NAV_START)
                    startActivity(Intent(this@MainActivity, NavigationActivity::class.java))
                }
                return true
            }
        })
        binding.tvSpeed.isClickable = true
        binding.tvSpeed.isFocusable = true
        binding.tvSpeed.setOnTouchListener { _, event -> speedGestureDetector.onTouchEvent(event); true }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startGps()
    }

    private fun startGps() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f) { loc ->
                    // Filtriraj samo po GPS natančnosti, ne po hitrosti
                    // accuracy < 10m = zanesljiv GPS signal
                    val kmh = (loc.speed * 3.6).toInt()
                    binding.tvSpeed.text = if (loc.accuracy > 10f || !loc.hasSpeed()) "0" else kmh.toString()
                }
            }
        } catch (e: Exception) {}
    }

    private fun showHomeAddressDialog() {
        val input = EditText(this).apply {
            hint = "npr. Dunajska cesta 1, Ljubljana"
            setPadding(40, 20, 40, 20)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Nastavi domači naslov")
            .setView(input)
            .setPositiveButton("Shrani") { _, _ ->
                val addr = input.text.toString().trim()
                if (addr.isNotEmpty()) {
                    prefs.saveHomeAddress(addr)
                    stats.log(StatEvent.NAV_START)
                    startActivity(Intent(this, NavigationActivity::class.java))
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    // ── KOMUNIKACIJA ──────────────────────────────────────────────────────────

    private fun setupCommPager() {
        val adapter = CommPageAdapter(
            context = this,
            items = allCommItems,
            getLang = { activeLang },
            onSpeak = { text -> speakComm(text) }
        )
        @Suppress("UNCHECKED_CAST")
        binding.viewPagerComm.adapter = adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>
    }

    fun speakComm(text: String) {
        if (text.isEmpty()) return

        // Vizualni feedback - pokaži besedilo
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

        // Utišaj radio
        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_DUCK })
        }

        // Zaustavi zaznavanje med govorom
        ttsManager.speak(
            text = text,
            language = activeLang,
            apiKey = prefs.getOpenAiKey(),
            voice = prefs.getTtsVoice(),
            onDone = {
                if (radioPlaying) {
                    startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_UNDUCK })
                }
            }
        )

        stats.log(StatEvent.COMM_ICON, text.take(30))
    }

    // ── ZAZNAVANJE JEZIKA ─────────────────────────────────────────────────────

    private fun setupLanguageDetector() {
        // Izklopljeno - SpeechRecognizer povzroča zvoke ob napakah
        // Jezik se preklopi ročno prek gumba v komunikacijskem modulu
    }

    private fun updateLangIndicator() {
        tvLangIndicator?.text = if (activeLang == "uk") "🇺🇦" else "🇸🇮"
    }

    // ── GLASNOST ──────────────────────────────────────────────────────────────

    private fun setupVolumeControls() {
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

    // ── URA + IME ─────────────────────────────────────────────────────────────

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
            stats.log(StatEvent.CALL_OUT)
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
    }

    // ── MAIL POROCILA ─────────────────────────────────────────────────────────

    private fun scheduleReports() {
        val hour = prefs.getReportHour()
        if (prefs.getReportMail1().isNotEmpty() || prefs.getReportMail2().isNotEmpty()) {
            ReportWorker.scheduleDaily(this, hour)
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
            .setItems(arrayOf("Nastavitve", "Statistika", "Izhod v Android")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> startActivity(Intent(this, StatsActivity::class.java))
                    2 -> exitToAndroid()
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
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
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
        if (requestCode == 100) {
            if (grantResults.getOrNull(permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION))
                == PackageManager.PERMISSION_GRANTED) startGps()
            if (grantResults.getOrNull(permissions.indexOf(Manifest.permission.RECORD_AUDIO))
                == PackageManager.PERMISSION_GRANTED) setupLanguageDetector()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) { stopRadio(); stats.log(StatEvent.APP_STOP) }
    }

    override fun onDestroy() {
        ttsManager.destroy()
        langDetector?.stop()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        clockRunnable?.let { clockHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
