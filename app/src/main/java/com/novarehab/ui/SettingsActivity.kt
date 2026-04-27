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
           
