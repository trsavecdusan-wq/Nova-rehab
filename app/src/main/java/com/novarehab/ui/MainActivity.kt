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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.novarehab.core.config.ApiConfigImportManager
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.learning.LearningProfileManager
import com.novarehab.media_messaging.repository.MediaGalleryRepository
import com.novarehab.media_messaging.service.MediaDownloadWorker
import com.novarehab.media_messaging.ui.MediaInboxBadge
import com.novarehab.service.DailyUpdateCheckWorker
import com.novarehab.service.RadioBrowserService
import com.novarehab.service.RadioService
import com.novarehab.service.ReportWorker
import com.novarehab.service.UpdateService
import com.novarehab.utils.ApiConfigManager
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.LanguageDetector
import com.novarehab.utils.OpenAiTranslateManager
import com.novarehab.utils.OpenAiTtsManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.SettingsBackupManager
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager
import com.novarehab.utils.UpdateManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MAX_SUBMENU_ITEMS = 18
        private const val SIGNALING_BASE_URL =
            "https://novarehab-dfcb9-default-rtdb.europe-west1.firebasedatabase.app"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var apiConfig: ApiConfigManager
    private lateinit var stats: StatsManager
    private lateinit var learningProfile: LearningProfileManager
    private lateinit var mediaGalleryRepository: MediaGalleryRepository
    private lateinit var ttsManager: OpenAiTtsManager
    private lateinit var translateManager: OpenAiTranslateManager

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var langDetector: LanguageDetector? = null

    private var currentStation = -1
    private var radioPlaying = false
    private var keepRadioOnNextStop = false
    var activeLang = "sl"

    private val kioskHandler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val languageReturnHandler = Handler(Looper.getMainLooper())
    private val incomingCallHandler = Handler(Looper.getMainLooper())
    private val mediaInboxHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()
    private val mediaDownloadWorker = MediaDownloadWorker()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private var kioskRunnable: Runnable? = null
    private var clockRunnable: Runnable? = null
    private var languageReturnRunnable: Runnable? = null
    private var incomingCallRunnable: Runnable? = null
    private var mediaInboxRunnable: Runnable? = null
    private var activeIncomingRoomId: String? = null

    private lateinit var speedGestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        apiConfig = ApiConfigManager(this)
        importApiConfigFromDevice()
        SettingsBackupManager(this).restoreIfAvailable()

        stats = StatsManager(this)
        learningProfile = LearningProfileManager(this)
        mediaGalleryRepository = MediaGalleryRepository(this)
        ttsManager = OpenAiTtsManager(this)
        translateManager = OpenAiTranslateManager(this)
        activeLang = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }

        handleUpdateIntent(intent)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("sl", "SI"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
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
        updateGalleryButton()

        if (prefs.isAutoLanguageEnabled()) setupLanguageDetector()

        scheduleReports()
        scheduleDailyUpdateCheck()
        startIncomingCallPolling()
        startMediaInboxPolling()

        stats.log(StatEvent.APP_START)
        startService(Intent(this, UpdateService::class.java))

        UpdateManager.showRestorePromptIfNeeded(this)
        Handler(Looper.getMainLooper()).postDelayed({
            UpdateManager.markCurrentLaunchSuccessful(this)
        }, 15000L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action != DailyUpdateCheckWorker.ACTION_SHOW_UPDATE) return

        UpdateManager.showUpdateDialog(
            activity = this,
            versionName = intent.getStringExtra(DailyUpdateCheckWorker.EXTRA_UPDATE_VERSION_NAME).orEmpty(),
            apkUrl = intent.getStringExtra(DailyUpdateCheckWorker.EXTRA_UPDATE_APK_URL).orEmpty(),
            message = intent.getStringExtra(DailyUpdateCheckWorker.EXTRA_UPDATE_MESSAGE).orEmpty()
        )
    }

    private fun importApiConfigFromDevice() {
        when (ApiConfigImportManager(this).importIfAvailable()) {
            is ApiConfigImportManager.ImportResult.Imported -> {
                Toast.makeText(this, "API nastavitve so bile uvožene.", Toast.LENGTH_LONG).show()
            }
            ApiConfigImportManager.ImportResult.Invalid -> {
                Toast.makeText(this, "API config datoteka ni pravilna.", Toast.LENGTH_LONG).show()
            }
            ApiConfigImportManager.ImportResult.NotFound -> {
                // Lokalni govor in komunikator delujeta tudi brez API datoteke.
            }
        }
    }

    private fun scheduleDailyUpdateCheck() {
        val request = PeriodicWorkRequestBuilder<DailyUpdateCheckWorker>(24, TimeUnit.HOURS).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
            button.text = twoLineButtonText(station?.name ?: "P${index + 1}")
            button.maxLines = 2
            button.isSingleLine = false
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

    private fun twoLineButtonText(text: String): String {
        val clean = text.trim()
        if (clean.length <= 10 || clean.contains("\n")) return clean

        val middle = clean.length / 2
        val split = clean.indices
            .filter { clean[it].isWhitespace() || clean[it] == '-' }
            .minByOrNull { kotlin.math.abs(it - middle) }

        return if (split != null) {
            clean.substring(0, split).trim() + "\n" + clean.substring(split + 1).trim()
        } else {
            clean.take(10) + "\n" + clean.drop(10).take(10)
        }
    }

    private fun playStation(index: Int) {
        val station = prefs.getRadioStations().getOrNull(index) ?: return

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
        ).forEachIndexed { index, button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
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
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f) { location ->
                    val kmh = (location.speed * 3.6).toInt()
                    binding.tvSpeed.text = if (location.accuracy > 10f || !location.hasSpeed()) "0" else kmh.toString()
                }
            }
        } catch (_: Exception) {
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
                val address = input.text.toString().trim()
                if (address.isNotEmpty()) {
                    prefs.saveHomeAddress(address)
                    stats.log(StatEvent.NAV_START)
                    startActivity(Intent(this, NavigationActivity::class.java))
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun getCommItems(): List<CommunicationItem> {
        val language = activeLang.ifBlank { prefs.getDefaultSpeechLanguage().ifBlank { "sl" } }
        val items = CommunicationRepository.load(this, language) +
            CommunicationRepository.customItems(prefs.getCustomCommIcons())

        return sortCommunicationItems(items)
    }

    private fun sortCommunicationItems(items: List<CommunicationItem>): List<CommunicationItem> {
        if (!prefs.isAutoSortCommunicationIconsEnabled()) return items

        val urgentIds = setOf("pomoc", "kopalnica", "bolecina", "slabo")
        return items.sortedWith(
            compareByDescending<CommunicationItem> { it.id in urgentIds || it.pinned }
                .thenByDescending { learningProfile.usageCount(it.id) }
                .thenBy { it.priority }
        )
    }

    private fun setupCommPager() {
        val adapter = CommPageAdapter(
            context = this,
            items = getCommItems(),
            pageSize = prefs.getCommIconsPerPage(),
            getLang = { activeLang },
            onItemSelected = { item -> handleCommunicationItem(item) }
        )

        binding.viewPagerComm.adapter = adapter
    }

    private fun handleCommunicationItem(item: CommunicationItem) {
        learningProfile.recordIconUsed(item.id)
        val iconTextManager = IconTextManager(this)
        val mainText = iconTextManager.getText(item.id).ifBlank { item.ttsText }
        val submenuEnabled = prefs.isCommSubmenuEnabled(item.id, defaultSubmenuEnabled(item.id))

        if (submenuEnabled && item.children.isNotEmpty()) {
            speakComm(mainText, "sl", logEvent = false) {
                val prompt = iconTextManager.getSubmenuPrompt(item.id)
                    .ifBlank { item.questionText }
                    .ifBlank { item.ttsText }
                speakComm(prompt, "sl", logEvent = false) {
                    showCommunicationSubmenu(item)
                }
            }
        } else {
            speakComm(mainText, "sl")
        }
    }

    private fun defaultSubmenuEnabled(id: String): Boolean {
        return false
    }

    private fun showCommunicationSubmenu(parent: CommunicationItem) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xF21A1A2E.toInt())
        }

        overlay.addView(TextView(this).apply {
            text = parent.shortLabel.ifBlank { parent.label }
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(10))
            }
        })

        val submenuItems = parent.children.take(MAX_SUBMENU_ITEMS)
        val pageSize = prefs.getCommIconsPerPage()
        val columns = commGridColumns(pageSize)
        val rows = commGridRows(pageSize)
        val visibleSlots = maxOf(pageSize, Math.ceil(submenuItems.size.toDouble() / pageSize).toInt() * pageSize)
        val totalRows = Math.ceil(visibleSlots.toDouble() / columns).toInt().coerceAtLeast(rows)

        val grid = GridLayout(this).apply {
            columnCount = columns
            rowCount = totalRows
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        overlay.addView(ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(grid)
        })

        val popup = PopupWindow(
            overlay,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            true
        )

        for (slot in 0 until visibleSlots) {
            grid.addView(createSubmenuCell(submenuItems.getOrNull(slot), popup))
        }

        overlay.addView(Button(this).apply {
            text = "NAZAJ"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333355.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply {
                setMargins(0, dp(12), 0, 0)
            }
            setOnClickListener { popup.dismiss() }
        })

        val timeout = Runnable { if (popup.isShowing) popup.dismiss() }
        languageReturnHandler.postDelayed(timeout, prefs.getCommSubmenuTimeoutSeconds() * 1000L)
        popup.setOnDismissListener { languageReturnHandler.removeCallbacks(timeout) }
        popup.showAtLocation(binding.root, Gravity.CENTER, 0, 0)
    }

    private fun createSubmenuCell(item: CommunicationItem?, popup: PopupWindow): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF16213E.toInt())
            isClickable = true
            isFocusable = true

            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }

            if (item == null) {
                visibility = View.INVISIBLE
                return@apply
            }

            addView(ImageView(this@MainActivity).apply {
                val customFile = File(getExternalFilesDir(null), "icons/${item.id}.png")
                if (customFile.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                } else {
                    setImageResource(item.iconRes)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                minimumHeight = dp(72)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    setMargins(dp(8), dp(6), dp(8), dp(4))
                }
            })

            addView(TextView(this@MainActivity).apply {
                text = item.shortLabel.ifBlank { item.label }
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(10))
                }
            })

            setOnClickListener {
                isEnabled = false
                learningProfile.recordIconUsed(item.id)
                animate()
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .setDuration(120L)
                    .start()

                speakComm(item.ttsText, "sl") {
                    popup.dismiss()
                }
            }
        }
    }

    private fun commGridColumns(pageSize: Int): Int = when (pageSize) {
        8 -> 4
        6 -> 3
        else -> 3
    }

    private fun commGridRows(pageSize: Int): Int = when (pageSize) {
        6 -> 2
        8 -> 2
        12 -> 4
        15 -> 5
        18 -> 6
        else -> 3
    }

    fun speakComm(
        text: String,
        sourceLang: String = "sl",
        logEvent: Boolean = true,
        onDone: () -> Unit = {}
    ) {
        if (text.isEmpty()) {
            onDone()
            return
        }

        if (activeLang != prefs.getDefaultSpeechLanguage().ifBlank { "sl" }) {
            scheduleLanguageReturn()
        }

        if (radioPlaying) {
            startService(Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_PAUSE_FOR_SPEECH
            })
        }

        val targetLang = activeLang
        val apiToken = apiConfig.getApiToken()
        val apiBaseUrl = apiConfig.getApiBaseUrl()
        val apiReady = apiConfig.isApiConfigured()
        val voice = prefs.getTtsVoice()

        fun speakFinal(finalText: String) {
            Toast.makeText(this, finalText, Toast.LENGTH_SHORT).show()

            ttsManager.speak(finalText, targetLang, apiToken, voice, apiBaseUrl) {
                if (radioPlaying) {
                    startService(Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_RESUME_AFTER_SPEECH
                    })
                }
                onDone()
            }

            if (logEvent) stats.log(StatEvent.COMM_ICON, finalText.take(30))
        }

        if (targetLang != "sl") {
            if (!apiReady) {
                Toast.makeText(this, "API ni nastavljen.", Toast.LENGTH_LONG).show()
                speakFinal(text)
            } else {
                translateManager.translate(text, targetLang, apiToken, apiBaseUrl) { translated ->
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
        binding.tvPatientName.textSize = 18f
        binding.tvPatientName.gravity = Gravity.CENTER
        binding.tvPatientName.setSingleLine(false)
        binding.tvPatientName.maxLines = 2

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
                text = lang.flag
                textSize = 42f
                gravity = Gravity.CENTER
                isAllCaps = false
                setOnClickListener {
                    selectGuestLanguage(lang)
                    dialog.dismiss()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(118)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(8), dp(8), dp(8), dp(8))
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
        val patientName = prefs.getPatientName().ifBlank { "Lana" }
        val language = languageChoice(activeLang)
        binding.tvPatientName.text = "${language.flag}\n$patientName"
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
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val pct = (current.toFloat() / max * 5).toInt().coerceIn(0, 5)
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
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())

        clockRunnable = object : Runnable {
            override fun run() {
                binding.tvClock.text = format.format(Date())
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

    private fun startIncomingCallPolling() {
        incomingCallRunnable?.let { incomingCallHandler.removeCallbacks(it) }

        incomingCallRunnable = object : Runnable {
            override fun run() {
                checkCompanionIncomingRequests()
                incomingCallHandler.postDelayed(this, 3000L)
            }
        }

        incomingCallHandler.postDelayed(incomingCallRunnable!!, 1500L)
    }

    private fun checkCompanionIncomingRequests() {
        Thread {
            val request = findIncomingCallRequest()
            if (request != null) {
                incomingCallHandler.post {
                    showIncomingCallDialog(request)
                }
            }
        }.start()
    }

    private fun findIncomingCallRequest(): IncomingCallRequest? {
        val ids = listOf("c01", "c02", "c03", "c04", "c05", "c06")
        val contacts = prefs.getContacts()

        ids.forEachIndexed { index, id ->
            if (!prefs.isContactIncomingCallEnabled(index)) return@forEachIndexed

            val roomId = "novarehab_$id"
            if (activeIncomingRoomId == roomId) return@forEachIndexed

            val json = getOutgoingRequest(roomId) ?: return@forEachIndexed
            if (json.optString("status") != "calling") return@forEachIndexed

            val name = json.optString("contactName")
                .ifBlank { contacts.getOrNull(index)?.name }
                .orEmpty()
                .ifBlank { id.uppercase() }

            return IncomingCallRequest(roomId, name)
        }

        return null
    }

    private fun getOutgoingRequest(roomId: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(roomUrl(roomId, "outgoingRequest"))
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val raw = response.body?.string().orEmpty()
                if (raw.isBlank() || raw == "null") null else JSONObject(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showIncomingCallDialog(request: IncomingCallRequest) {
        if (activeIncomingRoomId == request.roomId || isFinishing) return

        activeIncomingRoomId = request.roomId

        android.app.AlertDialog.Builder(this)
            .setTitle("${request.contactName} kliče")
            .setMessage("TESTNI KLIC\nSogovornik želi poklicati Lano.")
            .setPositiveButton("SPREJMI") { _, _ ->
                sendOutgoingRequestStatus(request.roomId, "accepted")
                Toast.makeText(this, "Testni klic sprejet.", Toast.LENGTH_LONG).show()
                activeIncomingRoomId = null
            }
            .setNegativeButton("ZAVRNI") { _, _ ->
                sendOutgoingRequestStatus(request.roomId, "rejected")
                Toast.makeText(this, "Klic zavrnjen.", Toast.LENGTH_SHORT).show()
                activeIncomingRoomId = null
            }
            .setOnCancelListener {
                sendOutgoingRequestStatus(request.roomId, "rejected")
                activeIncomingRoomId = null
            }
            .show()
    }

    private fun sendOutgoingRequestStatus(roomId: String, status: String) {
        Thread {
            try {
                val body = JSONObject()
                    .put("status", status)
                    .put("updatedAt", System.currentTimeMillis())
                    .toString()
                    .toRequestBody(jsonType)

                val request = Request.Builder()
                    .url(roomUrl(roomId, "outgoingRequest"))
                    .patch(body)
                    .build()

                httpClient.newCall(request).execute().use {
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun roomUrl(roomId: String, child: String): String {
        return "${SIGNALING_BASE_URL.trimEnd('/')}/rooms/$roomId/$child.json"
    }

    private fun setupBottomActionButtons() {
        binding.btnGallery.setOnClickListener {
            mediaGalleryRepository.markAllSeen()
            updateGalleryButton()
            startActivity(Intent(this, GalleryActivity::class.java))
        }

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

    private fun startMediaInboxPolling() {
        mediaInboxRunnable?.let { mediaInboxHandler.removeCallbacks(it) }
        mediaInboxRunnable = object : Runnable {
            override fun run() {
                pollIncomingMediaImage()
                mediaInboxHandler.postDelayed(this, 8000L)
            }
        }
        mediaInboxHandler.post(mediaInboxRunnable!!)
    }

    private fun pollIncomingMediaImage() {
        Thread {
            try {
                val allowedIds = setOf("c01", "c02", "c03", "c04", "c05", "c06")
                val payload = mediaDownloadWorker.fetchLatestAllowedImage(SIGNALING_BASE_URL, allowedIds) ?: return@Thread
                val senderName = resolveContactName(payload.senderId, payload.senderName)
                val saved = mediaGalleryRepository.saveIncomingImage(
                    messageId = payload.messageId,
                    senderId = payload.senderId,
                    senderName = senderName,
                    base64Data = payload.base64Data,
                    mimeType = payload.mimeType,
                    receivedAt = payload.createdAt,
                    messageText = payload.messageText
                ) ?: return@Thread

                mediaDownloadWorker.deleteRemoteMessage(SIGNALING_BASE_URL, payload.messageId)

                runOnUiThread {
                    updateGalleryButton()
                    showIncomingImageDialog(saved.senderName, saved.receivedAt)
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun resolveContactName(senderId: String, fallbackName: String): String {
        val ids = listOf("c01", "c02", "c03", "c04", "c05", "c06")
        val index = ids.indexOf(senderId)
        if (index == -1) return fallbackName.ifBlank { "neznanega kontakta" }
        return prefs.getContacts().getOrNull(index)?.name?.takeIf { it.isNotBlank() }
            ?: fallbackName.ifBlank { senderId.uppercase() }
    }

    private fun updateGalleryButton() {
        binding.btnGallery.text = MediaInboxBadge.format(mediaGalleryRepository.unseenCount())
    }

    private fun showIncomingImageDialog(senderName: String, receivedAt: Long) {
        if (isFinishing) return

        val time = android.text.format.DateFormat.format("HH:mm", receivedAt)

        android.app.AlertDialog.Builder(this)
            .setTitle("Prejeta nova slika od: $senderName")
            .setMessage("Čas prejema: $time")
            .setPositiveButton("POKAŽI") { _, _ ->
                mediaGalleryRepository.markAllSeen()
                updateGalleryButton()
                startActivity(Intent(this, GalleryActivity::class.java))
            }
            .setNegativeButton("KASNEJE", null)
            .show()
    }

    private fun scheduleReports() {
        val hour = prefs.getReportHour()
        if (prefs.getReportMail1().isNotEmpty() || prefs.getReportMail2().isNotEmpty()) {
            ReportWorker.scheduleDaily(this, hour)
        }
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
            .setItems(arrayOf("Nastavitve", "Statistika", "Obnovi prejšnjo verzijo", "Izhod v Android")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> startActivity(Intent(this, StatsActivity::class.java))
                    2 -> UpdateManager.openBackupInstaller(this)
                    3 -> exitToAndroid()
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
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
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
        activeLang = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }
        setupGuestLanguageButton()
        updateLanguageFlag()
        setupCommPager()
        updateRadioUI()
        updateGalleryButton()
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
        incomingCallRunnable?.let { incomingCallHandler.removeCallbacks(it) }
        mediaInboxRunnable?.let { mediaInboxHandler.removeCallbacks(it) }

        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    data class LanguageChoice(
        val code: String,
        val flag: String,
        val fullName: String
    )

    private data class IncomingCallRequest(
        val roomId: String,
        val contactName: String
    )
}
