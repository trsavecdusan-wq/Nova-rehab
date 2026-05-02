package com.novarehab.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.service.ReportWorker
import com.novarehab.utils.ApiConfigManager
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import com.novarehab.utils.SettingsBackupManager
import com.novarehab.utils.UpdateManager
import java.io.File

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_API_FILE = 501
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private lateinit var apiConfig: ApiConfigManager

    private val contactLangSpinners = mutableListOf<Spinner>()
    private val contactImageButtons = mutableListOf<ImageButton>()
    private val contactIncomingSwitches = mutableListOf<Switch>()
    private val contactOutgoingSwitches = mutableListOf<Switch>()

    private var pendingImageIndex = -1

    private lateinit var spinnerDefaultSpeechLang: Spinner
    private lateinit var spinnerPatientLang1: Spinner
    private lateinit var spinnerPatientLang2: Spinner
    private lateinit var spinnerCommIconsPerPage: Spinner
    private lateinit var spinnerCommSubmenuTimeout: Spinner
    private lateinit var switchAutoLanguage: Switch
    private lateinit var btnCheckUpdateNow: Button
    private lateinit var btnRestorePreviousVersion: Button
    private lateinit var btnShareCompanionApp: Button

    private fun companionContacts(): List<CompanionShareContact> {
        val contacts = prefs.getContacts()
        val fallbackNames = listOf("Kontakt 1", "Kontakt 2", "Kontakt 3", "Kontakt 4", "Kontakt 5", "Kontakt 6")

        return (0 until 6).map { index ->
            val slot = index + 1
            CompanionShareContact(
                contactId = "contact$slot",
                displayName = contacts.getOrNull(index)?.name?.takeIf { it.isNotBlank() } ?: fallbackNames[index]
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        apiConfig = ApiConfigManager(this)

        addLanguageSettingsPanel()
        addUpdateSettingsPanel()
        addCompanionSharePanel()
        loadSettings()
        setupButtons()
    }

    private fun langOptions() = arrayOf(
        "🇸🇮 Slovenščina",
        "🇺🇦 Ukrajinščina",
        "🇬🇧 Angleščina",
        "🇩🇪 Nemščina",
        "🇭🇷 Hrvaščina",
        "🇷🇸 Srbščina"
    )

    private fun langCode(position: Int): String = when (position) {
        1 -> "uk"
        2 -> "en"
        3 -> "de"
        4 -> "hr"
        5 -> "sr"
        else -> "sl"
    }

    private fun langIndex(code: String): Int = when (code) {
        "uk" -> 1
        "en" -> 2
        "de" -> 3
        "hr" -> 4
        "sr" -> 5
        else -> 0
    }

    private fun timeoutSeconds(position: Int): Long = when (position) {
        0 -> 8L
        2 -> 15L
        3 -> 20L
        4 -> 30L
        5 -> 60L
        else -> 12L
    }

    private fun timeoutIndex(seconds: Long): Int = when (seconds) {
        8L -> 0
        15L -> 2
        20L -> 3
        30L -> 4
        60L -> 5
        else -> 1
    }

    private fun newLangSpinner(): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                langOptions()
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    private fun addLanguageSettingsPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Jeziki pacienta in govor"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            text = "Število komunikacijskih ikon na stran:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })

        spinnerCommIconsPerPage = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("6 ikon", "8 ikon", "9 ikon", "12 ikon", "15 ikon", "18 ikon")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        panel.addView(spinnerCommIconsPerPage)

        panel.addView(TextView(this).apply {
            text = "Čas izhoda iz podmenija:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })

        spinnerCommSubmenuTimeout = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("8 sekund", "12 sekund", "15 sekund", "20 sekund", "30 sekund", "60 sekund")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        panel.addView(spinnerCommSubmenuTimeout)

        panel.addView(TextView(this).apply {
            text = "Privzeti jezik izgovora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerDefaultSpeechLang = newLangSpinner()
        panel.addView(spinnerDefaultSpeechLang)

        panel.addView(TextView(this).apply {
            text = "Jezik, ki ga pacient razume 1:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerPatientLang1 = newLangSpinner()
        panel.addView(spinnerPatientLang1)

        panel.addView(TextView(this).apply {
            text = "Jezik, ki ga pacient razume 2:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerPatientLang2 = newLangSpinner()
        panel.addView(spinnerPatientLang2)

        val autoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        autoRow.addView(TextView(this).apply {
            text = "Samodejno zaznavanje jezika sogovornika"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        switchAutoLanguage = Switch(this)
        autoRow.addView(switchAutoLanguage)
        panel.addView(autoRow)

        rootLayout.addView(panel, 5)
    }

    private fun addUpdateSettingsPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Posodobitve aplikacije"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        btnCheckUpdateNow = Button(this).apply {
            text = "PREVERI POSODOBITEV"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
            textSize = 15f
        }
        panel.addView(btnCheckUpdateNow)

        btnRestorePreviousVersion = Button(this).apply {
            text = "OBNOVI PREJŠNJO VERZIJO"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0F3460.toInt())
            textSize = 15f
        }
        panel.addView(btnRestorePreviousVersion)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }

    private fun addCompanionSharePanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Aplikacije za sogovornike"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        btnShareCompanionApp = Button(this).apply {
            text = "POŠLJI APLIKACIJO ZA SOGOVORNIKA"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A1942.toInt())
            textSize = 14f
        }
        panel.addView(btnShareCompanionApp)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }

    private fun loadSettings() {
        loadRadioSettings()
        loadContactSettings()

        binding.etApiBaseUrl.setText(apiConfig.getApiBaseUrl())
        binding.etOpenAiKey.setText(apiConfig.getApiToken())
        updateApiStatus()

        val voices = arrayOf("nova", "shimmer", "alloy", "echo", "fable", "onyx")
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voices)
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTtsVoice.adapter = voiceAdapter
        binding.spinnerTtsVoice.setSelection(voices.indexOf(prefs.getTtsVoice()).coerceAtLeast(0))

        binding.etGmailUser.setText(prefs.getGmailUser())
        binding.etGmailPass.setText(prefs.getGmailAppPassword())
        binding.etReportMail1.setText(prefs.getReportMail1())
        binding.etReportMail2.setText(prefs.getReportMail2())
        binding.switchMail1.isChecked = prefs.isReportMail1Enabled()
        binding.switchMail2.isChecked = prefs.isReportMail2Enabled()
        binding.etReportHour.setText(prefs.getReportHour().toString())

        binding.switchNavigation.isChecked = prefs.isNavigationEnabled()
        binding.etHomeAddress.setText(prefs.getHomeAddress())
        binding.etPatientName.setText(prefs.getPatientName())

        spinnerCommIconsPerPage.setSelection(
            when (prefs.getCommIconsPerPage()) {
                6 -> 0
                8 -> 1
                9 -> 2
                12 -> 3
                15 -> 4
                18 -> 5
                else -> 2
            }
        )
        spinnerCommSubmenuTimeout.setSelection(timeoutIndex(prefs.getCommSubmenuTimeoutSeconds()))

        spinnerDefaultSpeechLang.setSelection(langIndex(prefs.getDefaultSpeechLanguage()))
        spinnerPatientLang1.setSelection(langIndex(prefs.getPatientLanguage1()))
        spinnerPatientLang2.setSelection(langIndex(prefs.getPatientLanguage2()))
        switchAutoLanguage.isChecked = prefs.isAutoLanguageEnabled()

        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())
        binding.etNewPin.setText("")
        binding.etKioskMinutes.setText(prefs.getKioskReturnMinutes().toString())
    }

    private fun loadRadioSettings() {
        val stations = prefs.getRadioStations()
        val nameFields = listOf(
            binding.etStation1Name,
            binding.etStation2Name,
            binding.etStation3Name,
            binding.etStation4Name,
            binding.etStation5Name,
            binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url,
            binding.etStation2Url,
            binding.etStation3Url,
            binding.etStation4Url,
            binding.etStation5Url,
            binding.etStation6Url
        )

        stations.forEachIndexed { index, station ->
            if (index < nameFields.size) {
                nameFields[index].setText(station.name)
                urlFields[index].setText(station.url)
            }
        }
    }

    private fun loadContactSettings() {
        val contacts = prefs.getContacts()
        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dušan")
        val defaultLanguages = listOf("uk", "uk", "uk", "uk", "uk", "sl")

        val nameFields = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneFields = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )
        val languageContainers = listOf(
            binding.langContainer1,
            binding.langContainer2,
            binding.langContainer3,
            binding.langContainer4,
            binding.langContainer5,
            binding.langContainer6
        )
        val imageContainers = listOf(
            binding.imgContainer1,
            binding.imgContainer2,
            binding.imgContainer3,
            binding.imgContainer4,
            binding.imgContainer5,
            binding.imgContainer6
        )

        contactLangSpinners.clear()
        contactImageButtons.clear()
        contactIncomingSwitches.clear()
        contactOutgoingSwitches.clear()
        languageContainers.forEach { it.removeAllViews() }
        imageContainers.forEach { it.removeAllViews() }

        for (index in 0 until 6) {
            val contact = contacts.getOrNull(index)
            nameFields[index].setText(contact?.name?.takeIf { it.isNotBlank() } ?: defaultNames[index])
            phoneFields[index].setText(contact?.phone.orEmpty())

            val spinner = Spinner(this).apply {
                val options = arrayOf("🇸🇮 Slovenščina", "🇺🇦 Ukrajinščina")
                adapter = ArrayAdapter(
                    this@SettingsActivity,
                    android.R.layout.simple_spinner_item,
                    options
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                val language = contact?.language ?: defaultLanguages[index]
                setSelection(if (language == "uk") 1 else 0)
            }
            languageContainers[index].addView(spinner)
            contactLangSpinners.add(spinner)

            val incomingSwitch = Switch(this).apply {
                text = "Dohodni video klici"
                textSize = 12f
                setTextColor(0xFFB8D8FF.toInt())
                isChecked = prefs.isContactIncomingCallEnabled(index)
            }
            languageContainers[index].addView(incomingSwitch)
            contactIncomingSwitches.add(incomingSwitch)

            val outgoingSwitch = Switch(this).apply {
                text = "Odhodni video klici"
                textSize = 12f
                setTextColor(0xFFB8D8FF.toInt())
                isChecked = prefs.isContactOutgoingCallEnabled(index)
            }
            languageContainers[index].addView(outgoingSwitch)
            contactOutgoingSwitches.add(outgoingSwitch)

            val imageButton = ImageButton(this).apply {
                val file = File(getExternalFilesDir(null), "contacts/contact_${index + 1}.png")
                if (file.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                } else {
                    setImageResource(android.R.drawable.ic_menu_camera)
                }

                setBackgroundColor(0xFF333355.toInt())
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(0, 4, 0, 4)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER

                setOnClickListener {
                    pendingImageIndex = index
                    startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, 301)
                }
            }

            imageContainers[index].addView(imageButton)
            contactImageButtons.add(imageButton)
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            finish()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnIconSettings.setOnClickListener {
            startActivity(Intent(this, IconSettingsActivity::class.java))
        }

        btnCheckUpdateNow.setOnClickListener {
            UpdateManager.checkForUpdateNow(this)
        }

        btnRestorePreviousVersion.setOnClickListener {
            UpdateManager.openBackupInstaller(this)
        }

        btnShareCompanionApp.setOnClickListener {
            showCompanionSharePicker()
        }

        binding.btnChooseApiFile.setOnClickListener {
            openApiFilePicker()
        }

        binding.btnTestLocalTts.setOnClickListener {
            saveApiFields()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speakAndroid("Zdravo, to je test lokalnega govora.", "sl") {
                tts.destroy()
            }
            Toast.makeText(this, "Test lokalnega govora.", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestApiTts.setOnClickListener {
            saveApiFields()
            val key = apiConfig.getApiToken()
            val baseUrl = apiConfig.getApiBaseUrl()

            if (!apiConfig.isApiConfigured()) {
                Toast.makeText(this, "API ni nastavljen.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val voice = binding.spinnerTtsVoice.selectedItem.toString()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speak("Zdravo, to je test API govora aplikacije Nova Rehab.", "sl", key, voice, baseUrl) {
                tts.destroy()
            }
        }

        binding.btnTestApi.setOnClickListener {
            saveApiFields()
            updateApiStatus()
            apiConfig.testApiConnection { success, message ->
                updateApiStatus(if (success) "API shranjen" else message)
                Toast.makeText(this, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }

        binding.btnInstallTts.setOnClickListener {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                Toast.makeText(
                    this,
                    "Če slovenskega glasu ni, namesti RHVoice iz Trgovine Play.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=RHVoice&c=apps")))
                } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=RHVoice&c=apps")))
                }
            }
        }

        binding.btnTestMail.setOnClickListener {
            saveSettings()
            ReportWorker.schedule(this, prefs.getReportHour())
            Toast.makeText(this, "Poročilo bo poslano ob ${prefs.getReportHour()}:00", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCompanionSharePicker() {
        val contacts = companionContacts()
        val names = contacts.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Izberi sogovornika")
            .setItems(names) { _, which ->
                val contact = contacts.getOrNull(which)
                if (contact == null) {
                    Toast.makeText(this, "Neznan sogovornik.", Toast.LENGTH_LONG).show()
                    return@setItems
                }

                shareCompanionApp(contact)
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun shareCompanionApp(contact: CompanionShareContact) {
        val url = buildCompanionApkUrl(contact.contactId)
        if (url == null) {
            Toast.makeText(this, "Neznan kontakt, povezava ni bila ustvarjena.", Toast.LENGTH_LONG).show()
            return
        }

        val message = """
            Namesti aplikacijo NovaRehab Companion za video klic z Lano.

            1. Klikni povezavo:
            $url

            2. Prenesi APK.

            3. Po prenosu klikni datoteko in potrdi namestitev.

            Če telefon vpraša za dovoljenje namestitve iz tega vira, dovoli.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NovaRehab Companion - ${contact.displayName}")
            putExtra(Intent.EXTRA_TEXT, message)
        }

        startActivity(Intent.createChooser(intent, "Pošlji povezavo"))
    }

    private fun buildCompanionApkUrl(contactId: String): String? {
        val allowedIds = companionContacts().map { it.contactId }.toSet()
        if (contactId !in allowedIds) return null

        val baseUrl = "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/"
        return baseUrl + "companion-$contactId-debug.apk"
    }

    private fun saveApiFields() {
        val token = binding.etOpenAiKey.text.toString().trim()
        val baseUrl = binding.etApiBaseUrl.text.toString().trim().ifBlank {
            if (token.isNotBlank()) "https://api.openai.com" else ""
        }

        binding.etApiBaseUrl.setText(baseUrl)
        apiConfig.saveApiBaseUrl(baseUrl)
        apiConfig.saveApiToken(token)

        val message = "API shranjen: baseUrl length=${apiConfig.getApiBaseUrl().length}, token length=${apiConfig.getApiToken().length}"
        Log.d("NovaRehabApi", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateApiStatus()
    }

    private fun updateApiStatus(forced: String? = null) {
        binding.tvApiStatus.text = forced ?: apiConfig.getStatusText()
    }

    private fun openApiFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/json", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_API_FILE)
    }

    private fun importApiFile(uri: Uri) {
        val raw = try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        } catch (_: Exception) {
            Toast.makeText(this, "API datoteke ni bilo mogoče prebrati.", Toast.LENGTH_LONG).show()
            return
        }

        val parsed = parseApiFile(raw)
        if (parsed.baseUrl.isBlank() && parsed.token.isBlank()) {
            Toast.makeText(this, "API datoteka ne vsebuje prepoznavnega URL-ja ali tokena.", Toast.LENGTH_LONG).show()
            return
        }

        val baseUrl = parsed.baseUrl.ifBlank {
            if (parsed.token.isNotBlank()) "https://api.openai.com" else ""
        }

        if (baseUrl.isNotBlank()) {
            binding.etApiBaseUrl.setText(baseUrl)
            apiConfig.saveApiBaseUrl(baseUrl)
        }

        if (parsed.token.isNotBlank()) {
            binding.etOpenAiKey.setText(parsed.token)
            apiConfig.saveApiToken(parsed.token)
        }

        Toast.makeText(this, "API podatki so shranjeni.", Toast.LENGTH_SHORT).show()
        updateApiStatus()
    }

    private fun parseApiFile(raw: String): ImportedApiConfig {
        val lines = raw
            .replace("\uFEFF", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim().trim(',', '"', '\'') }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        val baseUrl = lines.firstNotNullOfOrNull { line ->
            val value = valueAfterSeparator(line)
            when {
                line.contains("base", ignoreCase = true) && value.startsWith("http", ignoreCase = true) -> value
                line.startsWith("http", ignoreCase = true) -> line
                else -> null
            }
        }.orEmpty()

        val token = lines.firstNotNullOfOrNull { line ->
            val value = valueAfterSeparator(line)
            when {
                line.contains("token", ignoreCase = true) -> value
                line.contains("key", ignoreCase = true) -> value
                line.startsWith("Bearer ", ignoreCase = true) -> line.removePrefix("Bearer ").trim()
                !line.startsWith("http", ignoreCase = true) && line.length >= 12 -> line
                else -> null
            }
        }.orEmpty()

        return ImportedApiConfig(baseUrl, token)
    }

    private fun valueAfterSeparator(line: String): String {
        val cleaned = line.trim().trim(',', '"', '\'')

        val eqIndex = cleaned.indexOf('=')
        if (eqIndex >= 0) {
            return cleaned.substring(eqIndex + 1)
                .trim()
                .trim(',', '"', '\'')
                .removePrefix("Bearer ")
                .trim()
        }

        val colonIndex = cleaned.indexOf(':')
        if (colonIndex >= 0 && !cleaned.startsWith("http", ignoreCase = true)) {
            return cleaned.substring(colonIndex + 1)
                .trim()
                .trim(',', '"', '\'')
                .removePrefix("Bearer ")
                .trim()
        }

        return cleaned
            .trim()
            .trim(',', '"', '\'')
            .removePrefix("Bearer ")
            .trim()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_API_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importApiFile(uri)
            return
        }

        if (requestCode == 301 && resultCode == RESULT_OK && pendingImageIndex >= 0) {
            val uri = data?.data ?: return

            try {
                val dir = File(getExternalFilesDir(null), "contacts")
                dir.mkdirs()

                contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "contact_${pendingImageIndex + 1}.png").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val bitmap = BitmapFactory.decodeFile(File(dir, "contact_${pendingImageIndex + 1}.png").absolutePath)
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bitmap)
                SettingsBackupManager(this).backupNow()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveSettings() {
        saveRadioSettings()
        saveContactSettings()

        saveApiFields()
        prefs.saveTtsVoice(binding.spinnerTtsVoice.selectedItem.toString())

        prefs.saveGmailUser(binding.etGmailUser.text.toString().trim())
        prefs.saveGmailAppPassword(binding.etGmailPass.text.toString().trim())
        prefs.saveReportMail1(binding.etReportMail1.text.toString().trim())
        prefs.saveReportMail2(binding.etReportMail2.text.toString().trim())
        prefs.saveReportMail1Enabled(binding.switchMail1.isChecked)
        prefs.saveReportMail2Enabled(binding.switchMail2.isChecked)
        prefs.saveReportHour(binding.etReportHour.text.toString().trim().toIntOrNull() ?: 8)

        prefs.saveNavigationEnabled(binding.switchNavigation.isChecked)
        prefs.saveHomeAddress(binding.etHomeAddress.text.toString().trim())
        prefs.savePatientName(binding.etPatientName.text.toString().trim())

        prefs.saveCommIconsPerPage(
            when (spinnerCommIconsPerPage.selectedItemPosition) {
                0 -> 6
                1 -> 8
                3 -> 12
                4 -> 15
                5 -> 18
                else -> 9
            }
        )
        prefs.saveCommSubmenuTimeoutSeconds(timeoutSeconds(spinnerCommSubmenuTimeout.selectedItemPosition))

        prefs.saveDefaultSpeechLanguage(langCode(spinnerDefaultSpeechLang.selectedItemPosition))
        prefs.savePatientLanguage1(langCode(spinnerPatientLang1.selectedItemPosition))
        prefs.savePatientLanguage2(langCode(spinnerPatientLang2.selectedItemPosition))
        prefs.saveAutoLanguageEnabled(switchAutoLanguage.isChecked)

        prefs.saveServerIp(binding.etServerIp.text.toString().trim())
        prefs.saveServerPort(binding.etServerPort.text.toString().trim())
        prefs.saveKioskReturnMinutes(binding.etKioskMinutes.text.toString().trim().toLongOrNull() ?: 5L)

        val pin = binding.etNewPin.text.toString().trim()
        if (pin.length == 4) {
            prefs.savePin(pin)
        }

        val backupOk = SettingsBackupManager(this).backupNow()
        Toast.makeText(
            this,
            if (backupOk) "Nastavitve shranjene in kopirane v Download/NovaRehab" else "Nastavitve shranjene",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveRadioSettings() {
        val nameFields = listOf(
            binding.etStation1Name,
            binding.etStation2Name,
            binding.etStation3Name,
            binding.etStation4Name,
            binding.etStation5Name,
            binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url,
            binding.etStation2Url,
            binding.etStation3Url,
            binding.etStation4Url,
            binding.etStation5Url,
            binding.etStation6Url
        )

        val stations = mutableListOf<RadioStation>()
        nameFields.forEachIndexed { index, field ->
            val name = field.text.toString().trim()
            val url = urlFields[index].text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                stations.add(RadioStation(name, url))
            }
        }

        prefs.saveRadioStations(stations)
    }

    private fun saveContactSettings() {
        val nameFields = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneFields = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )

        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dušan")
        val emojis = listOf("👩", "👨", "👧", "🧑", "👨‍⚕️", "🧑‍💼")
        val contacts = mutableListOf<Contact>()

        nameFields.forEachIndexed { index, field ->
            val name = field.text.toString().trim().ifEmpty { defaultNames[index] }
            val phone = phoneFields[index].text.toString().trim()
            val lang = if (contactLangSpinners.getOrNull(index)?.selectedItemPosition == 1) "uk" else "sl"

            prefs.saveContactIncomingCallEnabled(index, contactIncomingSwitches.getOrNull(index)?.isChecked ?: true)
            prefs.saveContactOutgoingCallEnabled(index, contactOutgoingSwitches.getOrNull(index)?.isChecked ?: true)

            contacts.add(Contact(name, phone, emojis.getOrElse(index) { "👤" }, lang))
        }

        prefs.saveContacts(contacts)
    }

    private data class CompanionShareContact(
        val contactId: String,
        val displayName: String
    )

    private data class ImportedApiConfig(
        val baseUrl: String,
        val token: String
    )
}
