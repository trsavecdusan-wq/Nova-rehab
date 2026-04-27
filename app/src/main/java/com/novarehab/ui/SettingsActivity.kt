package com.novarehab.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.service.ReportWorker
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private val contactLangSpinners = mutableListOf<Spinner>()
    private val contactImageButtons = mutableListOf<ImageButton>()
    private var pendingImageIndex = -1
    private lateinit var spinnerDefaultSpeechLang: Spinner
    private lateinit var spinnerPatientLang1: Spinner
    private lateinit var spinnerPatientLang2: Spinner
    private lateinit var spinnerCommIconsPerPage: Spinner
    private lateinit var switchAutoLanguage: Switch
    private lateinit var tvAppVersion: TextView
    private lateinit var btnExportBackup: Button
    private lateinit var btnImportBackup: Button
    private lateinit var btnLoadApiFromDownload: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)
        addLanguageSettingsPanel()
        addBackupAndVersionPanel()
        setupOpenAiKeyField()
        loadSettings()
        setupButtons()
        loadOpenAiKeyFromDownload(false)
    }

    private fun langOptions() = arrayOf(
        "Slovenscina",
        "Ukrajinscina",
        "Anglescina",
        "Nemscina",
        "Hrvanscina",
        "Srbscina"
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
            text = "Stevilo komunikacijskih ikon na stran:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })

        spinnerCommIconsPerPage = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("6 ikon", "8 ikon", "12 ikon", "18 ikon")
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        panel.addView(spinnerCommIconsPerPage)

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
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        })

        switchAutoLanguage = Switch(this)
        autoRow.addView(switchAutoLanguage)
        panel.addView(autoRow)

        rootLayout.addView(panel, 5)
    }

    private fun addBackupAndVersionPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Varnostna kopija"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        btnLoadApiFromDownload = Button(this).apply {
            text = "NALOZI API KLJUC IZ DOWNLOAD/API.TXT"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A1942.toInt())
        }
        panel.addView(btnLoadApiFromDownload)

        btnExportBackup = Button(this).apply {
            text = "SHRANI VARNOSTNO KOPIJO"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
        }
        panel.addView(btnExportBackup)

        btnImportBackup = Button(this).apply {
            text = "OBNOVI VARNOSTNO KOPIJO"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0F3460.toInt())
        }
        panel.addView(btnImportBackup)

        tvAppVersion = TextView(this).apply {
            text = getAppVersionText()
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
            setPadding(0, 12, 0, 0)
        }
        panel.addView(tvAppVersion)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }

    private fun setupOpenAiKeyField() {
        binding.etOpenAiKey.apply {
            hint = "Vnesi OpenAI API ključ"
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
    }

    private fun loadSettings() {
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

        stations.forEachIndexed { i, station ->
            if (i < nameFields.size) {
                nameFields[i].setText(station.name)
                urlFields[i].setText(station.url)
            }
        }

        val contacts = prefs.getContacts()
        val nameF = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneF = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )
        val langC = listOf(
            binding.langContainer1,
            binding.langContainer2,
            binding.langContainer3,
            binding.langContainer4,
            binding.langContainer5,
            binding.langContainer6
        )
        val imgC = listOf(
            binding.imgContainer1,
            binding.imgContainer2,
            binding.imgContainer3,
            binding.imgContainer4,
            binding.imgContainer5,
            binding.imgContainer6
        )

        contactLangSpinners.clear()
        contactImageButtons.clear()
        langC.forEach { it.removeAllViews() }
        imgC.forEach { it.removeAllViews() }

        contacts.forEachIndexed { i, contact ->
            if (i < nameF.size) {
                nameF[i].setText(contact.name)
                phoneF[i].setText(contact.phone)
            }
        }

        for (i in 0 until 6) {
            val contact = contacts.getOrNull(i)

            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@SettingsActivity,
                    android.R.layout.simple_spinner_item,
                    arrayOf("Slovenscina", "Ukrajinscina")
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(if (contact?.language == "uk") 1 else 0)
            }
            langC[i].addView(spinner)
            contactLangSpinners.add(spinner)

            val imgBtn = ImageButton(this).apply {
                val f = File(getExternalFilesDir(null), "contacts/contact_$i.png")
                if (f.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
                } else {
                    setImageResource(android.R.drawable.ic_menu_camera)
                }
                setBackgroundColor(0xFF333355.toInt())
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(0, 4, 0, 4)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener {
                    pendingImageIndex = i
                    startActivityForResult(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                        301
                    )
                }
            }
            imgC[i].addView(imgBtn)
            contactImageButtons.add(imgBtn)
        }

        binding.etOpenAiKey.setText(prefs.getOpenAiKey())

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
                12 -> 2
                18 -> 3
                else -> 2
            }
        )

        spinnerDefaultSpeechLang.setSelection(langIndex(prefs.getDefaultSpeechLanguage()))
        spinnerPatientLang1.setSelection(langIndex(prefs.getPatientLanguage1()))
        spinnerPatientLang2.setSelection(langIndex(prefs.getPatientLanguage2()))
        switchAutoLanguage.isChecked = prefs.isAutoLanguageEnabled()

        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())
        binding.etNewPin.setText("")
        binding.etKioskMinutes.setText(prefs.getKioskReturnMinutes().toString())

        tvAppVersion.text = getAppVersionText()
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

        binding.btnTestTts.setOnClickListener {
            if (binding.etOpenAiKey.text.toString().trim().isBlank()) {
                loadOpenAiKeyFromDownload(false)
            }

            prefs.saveOpenAiKey(binding.etOpenAiKey.text.toString().trim())
            val voice = binding.spinnerTtsVoice.selectedItem.toString()
            prefs.saveTtsVoice(voice)

            val key = prefs.getOpenAiKey()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speak("Zdravo, to je test govora aplikacije Rehab.", "sl", key, voice) {
                tts.destroy()
            }
        }

        binding.btnInstallTts.setOnClickListener {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                Toast.makeText(
                    this,
                    "Ce slovenskega glasu ni, namesti RHVoice iz Trgovine Play.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://search?q=RHVoice&c=apps")
                        )
                    )
                } catch (e2: Exception) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/search?q=RHVoice&c=apps")
                        )
                    )
                }
            }
        }

        binding.btnTestMail.setOnClickListener {
            saveSettings()
            ReportWorker.schedule(this, prefs.getReportHour())
            Toast.makeText(
                this,
                "Porocilo bo poslano ob ${prefs.getReportHour()}:00",
                Toast.LENGTH_LONG
            ).show()
        }

        btnLoadApiFromDownload.setOnClickListener {
            loadOpenAiKeyFromDownload(true)
        }

        btnExportBackup.setOnClickListener {
            saveSettings()
            exportBackup(true)
        }

        btnImportBackup.setOnClickListener {
            importBackup()
        }
    }

    private fun loadOpenAiKeyFromDownload(showToast: Boolean): Boolean {
        val key = readOpenAiKeyFromDownload()

        if (key.isBlank()) {
            if (showToast) {
                Toast.makeText(
                    this,
                    "Datoteka Download/api.txt ali Download/api ni najdena",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        prefs.saveOpenAiKey(key)
        binding.etOpenAiKey.setText(key)

        if (showToast) {
            Toast.makeText(
                this,
                "OpenAI API kljuc je nalozen iz Download/api.txt",
                Toast.LENGTH_LONG
            ).show()
        }

        return true
    }

    private fun readOpenAiKeyFromDownload(): String {
        val direct = readOpenAiKeyDirectly()
        if (direct.isNotBlank()) return direct

        val mediaStore = readOpenAiKeyFromMediaStore()
        if (mediaStore.isNotBlank()) return mediaStore

        return ""
    }

    private fun readOpenAiKeyDirectly(): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val candidates = listOf(
            File(downloadDir, "api.txt"),
            File(downloadDir, "api")
        )

        candidates.forEach { file ->
            try {
                if (file.exists() && file.isFile) {
                    return cleanApiKey(file.readText(Charsets.UTF_8))
                }
            } catch (e: Exception) {
            }
        }

        return ""
    }

    private fun readOpenAiKeyFromMediaStore(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )

        val cursor = contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? OR ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("api.txt", "api"),
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
        val cleaned = text
            .replace("\uFEFF", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: ""

        if (cleaned.startsWith("sk-")) return cleaned

        val start = text.indexOf("sk-")
        if (start >= 0) {
            return text.substring(start)
                .replace("\r", "\n")
                .lines()
                .firstOrNull()
                ?.trim()
                ?: ""
        }

        return cleaned
    }

    private fun saveSettings() {
        val nameF = listOf(
            binding.etStation1Name,
            binding.etStation2Name,
            binding.etStation3Name,
            binding.etStation4Name,
            binding.etStation5Name,
            binding.etStation6Name
        )
        val urlF = listOf(
            binding.etStation1Url,
            binding.etStation2Url,
            binding.etStation3Url,
            binding.etStation4Url,
            binding.etStation5Url,
            binding.etStation6Url
        )

        val stations = mutableListOf<RadioStation>()
        nameF.forEachIndexed { i, f ->
            val n = f.text.toString().trim()
            val u = urlF[i].text.toString().trim()
            if (n.isNotEmpty() && u.isNotEmpty()) {
                stations.add(RadioStation(n, u))
            }
        }
        prefs.saveRadioStations(stations)

        val nameC = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneC = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )

        val contacts = mutableListOf<Contact>()
        nameC.forEachIndexed { i, f ->
            val n = f.text.toString().trim()
            val p = phoneC[i].text.toString().trim()
            val lang = if (contactLangSpinners.getOrNull(i)?.selectedItemPosition == 1) {
                "uk"
            } else {
                "sl"
            }

            contacts.add(
                Contact(
                    n.ifEmpty { "Kontakt ${i + 1}" },
                    p,
                    "",
                    lang
                )
            )
        }
        prefs.saveContacts(contacts)

        if (binding.etOpenAiKey.text.toString().trim().isBlank()) {
            loadOpenAiKeyFromDownload(false)
        }

        prefs.saveOpenAiKey(binding.etOpenAiKey.text.toString().trim())
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
                2 -> 12
                3 -> 18
                else -> 8
            }
        )

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

        exportBackup(false)
        Toast.makeText(this, "Nastavitve shranjene", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 301 && resultCode == RESULT_OK && pendingImageIndex >= 0) {
            val uri = data?.data ?: return

            try {
                val dir = File(getExternalFilesDir(null), "contacts")
                dir.mkdirs()

                contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "contact_$pendingImageIndex.png").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val bmp = BitmapFactory.decodeFile(
                    File(dir, "contact_$pendingImageIndex.png").absolutePath
                )
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bmp)
            } catch (e: Exception) {
            }
        }
    }

    private fun exportBackup(showToast: Boolean) {
        try {
            val json = JSONObject()
            json.put("backupVersion", 1)
            json.put("exportedAt", System.currentTimeMillis())
            json.put("appVersion", getAppVersionText())
            json.put("novaRehabPrefs", prefsToJson("nova_rehab_prefs"))
            json.put("iconTextsPrefs", prefsToJson("icon_texts"))
            json.put("stats", exportStats())
            json.put("files", exportExternalFiles())

            val uri = createBackupOutputUri() ?: throw IllegalStateException("Backup file not available")
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Backup output not available")

            if (showToast) {
                Toast.makeText(
                    this,
                    "Varnostna kopija shranjena v Documents/RehabBackup",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            if (showToast) {
                Toast.makeText(
                    this,
                    "Varnostne kopije ni bilo mogoce shraniti",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importBackup() {
        try {
            val uri = findBackupInputUri()
            if (uri == null) {
                Toast.makeText(
                    this,
                    "Varnostna kopija ni najdena v Documents/RehabBackup",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val jsonText = contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: ""

            val json = JSONObject(jsonText)
            restorePrefs("nova_rehab_prefs", json.optJSONObject("novaRehabPrefs") ?: JSONObject())
            restorePrefs("icon_texts", json.optJSONObject("iconTextsPrefs") ?: JSONObject())
            restoreStats(json.optJSONArray("stats") ?: JSONArray())
            restoreExternalFiles(json.optJSONObject("files") ?: JSONObject())

            loadSettings()
            Toast.makeText(this, "Varnostna kopija obnovljena", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Obnova ni uspela", Toast.LENGTH_LONG).show()
        }
    }

    private fun prefsToJson(name: String): JSONObject {
        val source = getSharedPreferences(name, Context.MODE_PRIVATE)
        val json = JSONObject()

        source.all.forEach { entry ->
            val item = JSONObject()
            when (val value = entry.value) {
                is String -> {
                    item.put("type", "String")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "Int")
                    item.put("value", value)
                }
                is Long -> {
                    item.put("type", "Long")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "Float")
                    item.put("value", value.toDouble())
                }
                is Boolean -> {
                    item.put("type", "Boolean")
                    item.put("value", value)
                }
                is Set<*> -> {
                    item.put("type", "StringSet")
                    item.put("value", JSONArray(value.filterIsInstance<String>()))
                }
            }
            json.put(entry.key, item)
        }

        return json
    }

    private fun restorePrefs(name: String, json: JSONObject) {
        val editor = getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        editor.clear()

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue

            when (item.optString("type")) {
                "String" -> editor.putString(key, item.optString("value", ""))
                "Int" -> editor.putInt(key, item.optInt("value", 0))
                "Long" -> editor.putLong(key, item.optLong("value", 0L))
                "Float" -> editor.putFloat(key, item.optDouble("value", 0.0).toFloat())
                "Boolean" -> editor.putBoolean(key, item.optBoolean("value", false))
                "StringSet" -> {
                    val array = item.optJSONArray("value") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.optString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }

        editor.apply()
    }

    private fun exportStats(): JSONArray {
        val result = JSONArray()
        val dbFile = getDatabasePath("nova_stats.db")
        if (!dbFile.exists()) return result

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val cursor = db.rawQuery(
                "SELECT event, value, timestamp FROM stats ORDER BY id ASC",
                null
            )

            cursor.use {
                while (it.moveToNext()) {
                    result.put(
                        JSONObject()
                            .put("event", it.getString(0))
                            .put("value", it.getString(1))
                            .put("timestamp", it.getLong(2))
                    )
                }
            }
        } catch (e: Exception) {
        } finally {
            db?.close()
        }

        return result
    }

    private fun restoreStats(array: JSONArray) {
        val db = openOrCreateDatabase("nova_stats.db", Context.MODE_PRIVATE, null)

        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event TEXT NOT NULL,
                    value TEXT DEFAULT '',
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON stats(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_event ON stats(event)")
            db.delete("stats", null, null)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val values = ContentValues().apply {
                    put("event", item.optString("event", ""))
                    put("value", item.optString("value", ""))
                    put("timestamp", item.optLong("timestamp", System.currentTimeMillis()))
                }
                db.insert("stats", null, values)
            }
        } catch (e: Exception) {
        } finally {
            db.close()
        }
    }

    private fun exportExternalFiles(): JSONObject {
        val json = JSONObject()
        val base = getExternalFilesDir(null) ?: return json
        collectFiles(base, base, json)
        return json
    }

    private fun collectFiles(base: File, dir: File, json: JSONObject) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectFiles(base, file, json)
            } else if (file.isFile && file.length() <= 5L * 1024L * 1024L) {
                val relativePath = base.toURI().relativize(file.toURI()).path
                val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                json.put(relativePath, encoded)
            }
        }
    }

    private fun restoreExternalFiles(json: JSONObject) {
        val base = getExternalFilesDir(null) ?: return
        val keys = json.keys()

        while (keys.hasNext()) {
            val relativePath = keys.next()
            val encoded = json.optString(relativePath, "")
            if (encoded.isBlank()) continue

            try {
                val target = File(base, relativePath)
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
            } catch (e: Exception) {
            }
        }
    }

    private fun createBackupOutputUri(): Uri? {
        val fileName = "rehab_settings_backup.json"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Files.getContentUri("external")
            val relativePath = Environment.DIRECTORY_DOCUMENTS + "/RehabBackup/"

            contentResolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(fileName, relativePath)
            )

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            contentResolver.insert(collection, values)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "RehabBackup"
            )
            dir.mkdirs()
            Uri.fromFile(File(dir, fileName))
        }
    }

    private fun findBackupInputUri(): Uri? {
        val fileName = "rehab_settings_backup.json"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Files.getContentUri("external")
            val relativePath = Environment.DIRECTORY_DOCUMENTS + "/RehabBackup/"
            val projection = arrayOf(MediaStore.MediaColumns._ID)

            val cursor = contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(fileName, relativePath),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } else {
            val file = File(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "RehabBackup"
                ),
                fileName
            )
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    private fun getAppVersionText(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val versionName = info.versionName ?: "?"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val date = SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale.getDefault()
            ).format(Date(info.lastUpdateTime))

            "Verzija APK: $versionName ($versionCode)\nDatum in ura namestitve: $date"
        } catch (e: Exception) {
            "Verzija APK: ni znana"
        }
    }
}
