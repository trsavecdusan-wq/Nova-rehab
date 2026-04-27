package com.novarehab.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioBrowserService
import com.novarehab.service.RadioService
import com.novarehab.service.ReportWorker
import com.novarehab.service.UpdateService
import com.novarehab.utils.LanguageDetector
import com.novarehab.utils.OpenAiTranslateManager
import com.novarehab.utils.OpenAiTtsManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var stats: StatsManager
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var langDetector: LanguageDetector? = null
    private lateinit var ttsManager: OpenAiTtsManager
    private lateinit var translateManager: OpenAiTranslateManager

    private var currentStation = -1
    private var radioPlaying = false
    private var keepRadioOnNextStop = false
    var activeLang = "sl"

    private val kioskHandler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val languageReturnHandler = Handler(Looper.getMainLooper())
    private var kioskRunnable: Runnable? = null
    private var clockRunnable: Runnable? = null
    private var languageReturnRunnable: Runnable? = null

    private lateinit var speedGestureDetector: GestureDetector

    private val allCommItems = listOf(
        Triple("pomoc", R.drawable.comm_pomoc, Pair("Potrebujem pomoč, prosim pridite", "")),
        Triple("piti", R.drawable.comm_piti, Pair("Žejna sem, prinesite mi piti", "")),
        Triple("jesti", R.drawable.comm_jesti, Pair("Lačna sem, bi rada jedla", "")),
        Triple("bolecina", R.drawable.comm_bolecina, Pair("Imam bolečine", "")),
        Triple("kopalnica", R.drawable.comm_kopalnica, Pair("Potrebujem v kopalnico", "")),
        Triple("dobro", R.drawable.comm_dobro, Pair("Dobro se počutim", "")),
        Triple("slabo", R.drawable.comm_slabo, Pair("Ne počutim se dobro", "")),
        Triple("utrujena", R.drawable.comm_utrujena, Pair("Utrujena sem, rada bi počivala", "")),
        Triple("mraz", R.drawable.comm_mraz, Pair("Mrzlica mi je", "")),
        Triple("vroce", R.drawable.comm_vroce, Pair("Vroče mi je", "")),
        Triple("hvala", R.drawable.comm_hvala, Pair("Hvala lepa", "")),
        Triple("pridi_sem", R.drawable.comm_pridi_sem, Pair("Prosim pridi sem k meni", "")),
        Triple("pocakaj", R.drawable.comm_pocakaj, Pair("Počakaj prosim", "")),
        Triple("zdravilo", R.drawable.comm_zdravilo, Pair("Čas je za zdravilo", "")),
        Triple("telefon", R.drawable.comm_telefon, Pair("Prinesite mi telefon", "")),
        Triple("tv", R.drawable.comm_tv, Pair("Vklopite televizijo", "")),
        Triple("postelja", R.drawable.comm_postelja, Pair("Rada bi ležala", "")),
        Triple("okno", R.drawable.comm_okno, Pair("Odprite okno", "")),
        Triple("vesela", R.drawable.comm_vesela, Pair("Vesela sem", "")),
        Triple("zalostna", R.drawable.comm_zalostna, Pair("Žalostna sem", "")),
        Triple("jezna", R.drawable.comm_jezna, Pair("Jezna sem", "")),
        Triple("strah", R.drawable.comm_strah, Pair("Prestrašena sem", "")),
        Triple("tesnoba", R.drawable.comm_tesnoba, Pair("Tesnobno se počutim", "")),
        Triple("objemi", R.drawable.comm_objemi, Pair("Bi me objel?", ""))
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
        translateManager = OpenAiTranslateManager(this)
        activeLang = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }

        loadOpenAiKeyFromDownloadIfNeeded()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("sl", "SI"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)
                ttsReady = true
            }
        }

        requestAllPermissions()
        setupRadio()
        setupSpeed()
        setupCommPager()
        setupVideoCallButton()
        setupBottomActionButtons()
        setupVolumeControls()
        setupClock()
        setupGuestLanguageButton()
        updateLanguageFlag()

        if (prefs.isAutoLanguageEnabled()) setupLanguageDetector()
        scheduleReports()

        stats.log(StatEvent.APP_START)
        startService(Intent(this, UpdateService::class.java))
    }

    private fun setupRadio() {
        refreshRadioButtons()
        RadioBrowserService.fetchStations(
            this,
            onSuccess = { refreshRadioButtons() },
            onError = {}
        )
    }

    private fun refreshRadioButtons() {
        val stations = prefs.getRadioStations()
        val radioButtons = listOf(
            binding.btnRadio1,
            binding.btnRadio2,
            binding.btnRadio3,
            binding.btnRadio4,
            binding.btnRadio5,
            binding.btnRadio6
        )

        radioButtons.forEachIndexed { index, button ->
            val station = stations.getOrNull(index)
            button.text = station?.name?.take(10) ?: "P${index + 1}"
            button.setOnClickListener {
                if (station?.url == "music://local") {
                    startActivity(Intent(this, MusicActivity::class.java))
                } else if (radioPlaying && currentStation == index) {
                    stopRadio()
                } else {
                    playStation(index)
                }
            }
        }

        binding.btnRadioToggle.visibility = View.GONE
    }

    private fun playStation(index: Int) {
        val stations = prefs.getRadioStations()
        val station = stations.getOrNull(index) ?: return

        if (station.url == "music://local") {
            startActivity(Intent(this, MusicActivity::class.java))
            return
        }

        if (radioPlaying) stats.log(StatEvent.RADIO_STOP)

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
        startService(Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        })
        if (radioPlaying) stats.log(StatEvent.RADIO_STOP)
        radioPlaying = false
        updateRadioUI()
    }

    private fun updateRadioUI() {
        listOf(
            binding.btnRadio1,
            binding.btnRadio2,
            binding.btnRadio3,
            binding.btnRadio4,
            binding.btnRadio5,
            binding.btnRadio6
        ).forEachIndexed { index, btn ->
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (index == currentStation && radioPlaying) 0xFFe94560.toInt() else 0xFF0f3460.toInt()
            )
        }
    }

    private fun setupSpeed() {
        speedGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                showPinDialog()
            }

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
        binding.tvSpeed.setOnTouchListener { _, event ->
            speedGestureDetector.onTouchEvent(event)
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps()
        }
    }

    private fun startGps() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f) { loc ->
                    val kmh = (loc.speed * 3.6).toInt()
                    binding.tvSpeed.text = if (loc.accuracy > 10f || !loc.hasSpeed()) "0" else kmh.toString()
                }
            }
        } catch (e: Exception) {
        }
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

    private fun getCommItems(): List<Triple<String, Int, Pair<String, String>>> {
        val custom = prefs.getCustomCommIcons()
            .filter { it.text.isNotBlank() || it.title.isNotBlank() }
            .map { item ->
                Triple(item.id, R.drawable.ic_contact_default, Pair(item.text.ifBlank { item.title }, ""))
            }

        return allCommItems + custom
    }

    private fun setupCommPager() {
        val adapter = CommPageAdapter(
            context = this,
            items = getCommItems(),
            pageSize = prefs.getCommIconsPerPage(),
            getLang = { activeLang },
            onSpeak = { text, sourceLang -> speakComm(text, sourceLang) }
        )
        binding.viewPagerComm.adapter = adapter
    }

    fun speakComm(text: String, sourceLang: String = "sl") {
        if (text.isEmpty()) return

        if (activeLang != prefs.getDefaultSpeechLanguage().ifBlank { "sl" }) {
            scheduleLanguageReturn()
        }

        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_PAUSE_FOR_SPEECH
            })
        }

        val targetLang = activeLang
        var apiKey = prefs.getOpenAiKey()

        if (apiKey.isBlank()) {
            loadOpenAiKeyFromDownloadIfNeeded()
            apiKey = prefs.getOpenAiKey()
        }

        val voice = prefs.getTtsVoice()

        fun speakFinal(finalText: String) {
            Toast.makeText(this, finalText, Toast.LENGTH_SHORT).show()

            ttsManager.speak(finalText, targetLang, apiKey, voice) {
                if (radioPlaying) {
                    startService(Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_RESUME_AFTER_SPEECH
                    })
                }
            }

            stats.log(StatEvent.COMM_ICON, finalText.take(30))
        }

        if (targetLang != "sl") {
            if (apiKey.isBlank()) {
                Toast.makeText(
                    this,
                    "API kljuc ni nalozen, zato govor ostane slovenski.",
                    Toast.LENGTH_LONG
                ).show()
                speakFinal(text)
            } else {
                translateManager.translate(text, targetLang, apiKey) { translated ->
                    speakFinal(translated.ifBlank { text })
                }
            }
        } else {
            speakFinal(text)
        }
    }

    private fun setupGuestLanguageButton() {
        binding.tvPatientName.visibility = View.VISIBLE
        binding.tvPatientName.isClickable = true
        binding.tvPatientName.isFocusable = true
        binding.tvPatientName.textSize = 30f
        binding.tvPatientName.gravity = Gravity.CENTER

        binding.tvPatientName.setOnClickListener {
            Toast.makeText(this, "Za spremembo jezika držite zastavo.", Toast.LENGTH_SHORT).show()
        }

        binding.tvPatientName.setOnLongClickListener {
            showGuestLanguageDialog()
            true
        }
    }

    private fun showGuestLanguageDialog() {
        val languages = visibleLanguageChoices()

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        val info = TextView(this).apply {
            text = "Izberi jezik pogovora."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        wrapper.addView(info)

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 1
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Jezik")
            .setView(wrapper)
            .setNegativeButton("Prekliči", null)
            .create()

        languages.forEach { lang ->
            val button = Button(this).apply {
                text = "${lang.flag}\n${lang.fullName}"
                textSize = 20f
                gravity = Gravity.CENTER
                isAllCaps = false
                setOnClickListener {
                    selectGuestLanguage(lang)
                    dialog.dismiss()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 150
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
            }
            grid.addView(button)
        }

        wrapper.addView(grid)
        dialog.show()
    }

    private fun visibleLanguageChoices(): List<LanguageChoice> {
        val first = prefs.getPatientLanguage1().ifBlank { "sl" }
        val second = prefs.getPatientLanguage2().ifBlank { "uk" }

        val choices = mutableListOf<LanguageChoice>()
        choices.add(languageChoice(first))

        if (second != first) {
            choices.add(languageChoice(second))
        }

        if (choices.size < 2) {
            val fallback = if (first == "sl") "uk" else "sl"
            choices.add(languageChoice(fallback))
        }

        return choices.take(2)
    }

    private fun selectGuestLanguage(language: LanguageChoice) {
        activeLang = language.code
        updateLanguageFlag()
        setupCommPager()
        scheduleLanguageReturn()
        Toast.makeText(this, "Izbran jezik: ${language.fullName}", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleLanguageReturn() {
        languageReturnRunnable?.let { languageReturnHandler.removeCallbacks(it) }

        val defaultLang = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }
        val minutes = prefs.getKioskReturnMinutes().coerceAtLeast(1L)

        languageReturnRunnable = Runnable {
            if (activeLang != defaultLang) {
                activeLang = defaultLang
                updateLanguageFlag()
                setupCommPager()
                Toast.makeText(this, "Jezik je vrnjen na privzeti jezik.", Toast.LENGTH_SHORT).show()
            }
        }

        languageReturnHandler.postDelayed(languageReturnRunnable!!, minutes * 60 * 1000L)
    }

    private fun updateLanguageFlag() {
        binding.tvPatientName.text = languageChoice(activeLang).flag
        binding.tvPatientName.visibility = View.VISIBLE
    }

    private fun languageChoice(code: String): LanguageChoice {
        return when (code) {
            "uk" -> LanguageChoice("uk", "🇺🇦", "Ukrajinščina")
            "en" -> LanguageChoice("en", "🇬🇧", "Angleščina")
            "de" -> LanguageChoice("de", "🇩🇪", "Nemščina")
            "hr" -> LanguageChoice("hr", "🇭🇷", "Hrvaščina")
            "sr" -> LanguageChoice("sr", "🇷🇸", "Srbščina")
            else -> LanguageChoice("sl", "🇸🇮", "Slovenščina")
        }
    }

    private fun setupLanguageDetector() {
        langDetector?.stop()
        langDetector = LanguageDetector(this) { detectedLang ->
            val allowed = setOf(prefs.getPatientLanguage1(), prefs.getPatientLanguage2())
            if (detectedLang in allowed) {
                activeLang = detectedLang
                updateLanguageFlag()
                setupCommPager()
                scheduleLanguageReturn()
            }
        }
        langDetector?.start()
    }

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

    private fun setupVideoCallButton() {
        binding.btnVideoCall.setOnClickListener {
            if (radioPlaying) stopRadio()
            stats.log(StatEvent.CALL_OUT)
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
    }

    private fun setupBottomActionButtons() {
        binding.btnMirror.setOnClickListener {
            keepRadioOnNextStop = true
            startActivity(Intent(this, MirrorActivity::class.java))
        }

        binding.btnRelax.setOnClickListener {
            Toast.makeText(
                this,
                "Sprostitev in grafično učenje bova dodala v naslednji fazi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun scheduleReports() {
        val hour = prefs.getReportHour()
        if (prefs.getReportMail1().isNotEmpty() || prefs.getReportMail2().isNotEmpty()) {
            ReportWorker.scheduleDaily(this, hour)
        }
    }

    private fun loadOpenAiKeyFromDownloadIfNeeded() {
        if (prefs.getOpenAiKey().isNotBlank()) return

        val key = findApiKeyInDownload()
        if (key.isNotBlank()) {
            prefs.saveOpenAiKey(key)
        }
    }

    private fun findApiKeyInDownload(): String {
        val direct = findApiKeyDirectFile()
        if (direct.isNotBlank()) return direct

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return findApiKeyInMediaStore()
        }

        return ""
    }

    private fun findApiKeyDirectFile(): String {
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val candidates = listOf(
            File(download, "api"),
            File(download, "api.txt"),
            File(download, "API"),
            File(download, "API.txt")
        )

        candidates.forEach { file ->
            try {
                if (file.exists() && file.isFile) {
                    val key = cleanApiKey(file.readText(Charsets.UTF_8))
                    if (key.isNotBlank()) return key
                }
            } catch (e: Exception) {
            }
        }

        return ""
    }

    private fun findApiKeyInMediaStore(): String {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cursor = contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? OR ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("api", "api.txt"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                try {
                    val text = contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    val key = cleanApiKey(text)
                    if (key.isNotBlank()) return key
                } catch (e: Exception) {
                }
            }
        }

        return ""
    }

    private fun cleanApiKey(text: String): String {
        val normalized = text
            .replace("\uFEFF", "")
            .replace("\r", "\n")

        val start = normalized.indexOf("sk-")
        if (start >= 0) {
            return normalized.substring(start)
                .lines()
                .firstOrNull()
                ?.trim()
                ?: ""
        }

        return normalized.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: ""
    }

    override fun onBackPressed() {
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
            }
            .show()
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

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed, 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.getOrNull(permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)) == PackageManager.PERMISSION_GRANTED) {
                startGps()
            }

            if (
                prefs.isAutoLanguageEnabled() &&
                grantResults.getOrNull(permissions.indexOf(Manifest.permission.RECORD_AUDIO)) == PackageManager.PERMISSION_GRANTED
            ) {
                setupLanguageDetector()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (keepRadioOnNextStop) {
            keepRadioOnNextStop = false
        } else {
            stopRadio()
        }

        if (isFinishing) {
            stats.log(StatEvent.APP_STOP)
        }
    }

    override fun onResume() {
        super.onResume()
        loadOpenAiKeyFromDownloadIfNeeded()
        activeLang = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }
        setupGuestLanguageButton()
        updateLanguageFlag()
        setupCommPager()
        updateRadioUI()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()

        if (::ttsManager.isInitialized) {
            ttsManager.destroy()
        }

        langDetector?.stop()
        kioskRunnable?.let { kioskHandler.removeCallbacks(it) }
        clockRunnable?.let { clockHandler.removeCallbacks(it) }
        languageReturnRunnable?.let { languageReturnHandler.removeCallbacks(it) }

        super.onDestroy()
    }

    data class LanguageChoice(
        val code: String,
        val flag: String,
        val fullName: String
    )
}
