package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.service.ReportWorker
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import java.io.File

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)
        addLanguageSettingsPanel()
        loadSettings()
        setupButtons()
    }

    private fun langOptions() = arrayOf("🇸🇮 Slovenščina", "🇺🇦 Ukrajinščina", "🇬🇧 Angleščina", "🇩🇪 Nemščina", "🇭🇷 Hrvaščina", "🇷🇸 Srbščina")

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
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, langOptions())
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, arrayOf("6 ikon", "8 ikon", "12 ikon", "18 ikon"))
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        switchAutoLanguage = Switch(this)
        autoRow.addView(switchAutoLanguage)
        panel.addView(autoRow)
        rootLayout.addView(panel, 5)
    }

    private fun loadSettings() {
        // Radio
        val stations = prefs.getRadioStations()
        val nameFields = listOf(binding.etStation1Name, binding.etStation2Name, binding.etStation3Name,
            binding.etStation4Name, binding.etStation5Name, binding.etStation6Name)
        val urlFields = listOf(binding.etStation1Url, binding.etStation2Url, binding.etStation3Url,
            binding.etStation4Url, binding.etStation5Url, binding.etStation6Url)
        stations.forEachIndexed { i, s ->
            if (i < nameFields.size) { nameFields[i].setText(s.name); urlFields[i].setText(s.url) }
        }

        // Kontakti
        val contacts = prefs.getContacts()
        val nameF = listOf(binding.etContact1Name, binding.etContact2Name, binding.etContact3Name,
            binding.etContact4Name, binding.etContact5Name, binding.etContact6Name)
        val phoneF = listOf(binding.etContact1Phone, binding.etContact2Phone, binding.etContact3Phone,
            binding.etContact4Phone, binding.etContact5Phone, binding.etContact6Phone)
        val langC = listOf(binding.langContainer1, binding.langContainer2, binding.langContainer3,
            binding.langContainer4, binding.langContainer5, binding.langContainer6)
        val imgC = listOf(binding.imgContainer1, binding.imgContainer2, binding.imgContainer3,
            binding.imgContainer4, binding.imgContainer5, binding.imgContainer6)

        contactLangSpinners.clear(); contactImageButtons.clear()
        contacts.forEachIndexed { i, c ->
            if (i < nameF.size) { nameF[i].setText(c.name); phoneF[i].setText(c.phone) }
        }
        for (i in 0 until 6) {
            val contact = contacts.getOrNull(i)
            val spinner = Spinner(this).apply {
                val opts = arrayOf("🇸🇮 Slovenščina", "🇺🇦 Ukrajinščina")
                adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, opts)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(if (contact?.language == "uk") 1 else 0)
            }
            langC[i].addView(spinner)
            contactLangSpinners.add(spinner)

            val imgBtn = ImageButton(this).apply {
                val f = File(getExternalFilesDir(null), "contacts/contact_$i.png")
                if (f.exists()) setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
                else setImageResource(android.R.drawable.ic_menu_camera)
                setBackgroundColor(0xFF333355.toInt())
                layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0,4,0,4) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener {
                    pendingImageIndex = i
                    startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, 301)
                }
            }
            imgC[i].addView(imgBtn)
            contactImageButtons.add(imgBtn)
        }

        // OpenAI TTS
        binding.etOpenAiKey.setText(prefs.getOpenAiKey())
        val voices = arrayOf("nova", "shimmer", "alloy", "echo", "fable", "onyx")
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voices)
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTtsVoice.adapter = voiceAdapter
        binding.spinnerTtsVoice.setSelection(voices.indexOf(prefs.getTtsVoice()).coerceAtLeast(0))

        // Mail
        binding.etGmailUser.setText(prefs.getGmailUser())
        binding.etGmailPass.setText(prefs.getGmailAppPassword())
        binding.etReportMail1.setText(prefs.getReportMail1())
        binding.etReportMail2.setText(prefs.getReportMail2())
        binding.switchMail1.isChecked = prefs.isReportMail1Enabled()
        binding.switchMail2.isChecked = prefs.isReportMail2Enabled()
        binding.etReportHour.setText(prefs.getReportHour().toString())

        // Navigacija
        binding.switchNavigation.isChecked = prefs.isNavigationEnabled()
        binding.etHomeAddress.setText(prefs.getHomeAddress())

        // Pacient
        binding.etPatientName.setText(prefs.getPatientName())
        spinnerCommIconsPerPage.setSelection(when (prefs.getCommIconsPerPage()) {
            6 -> 0
            8 -> 1
            12 -> 2
            18 -> 3
            else -> 1
        })
        spinnerDefaultSpeechLang.setSelection(langIndex(prefs.getDefaultSpeechLanguage()))
        spinnerPatientLang1.setSelection(langIndex(prefs.getPatientLanguage1()))
        spinnerPatientLang2.setSelection(langIndex(prefs.getPatientLanguage2()))
        switchAutoLanguage.isChecked = prefs.isAutoLanguageEnabled()

        // Server
        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())

        // PIN
        binding.etNewPin.setText("")

        // Kiosk vrnitev
        binding.etKioskMinutes.setText(prefs.getKioskReturnMinutes().toString())
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveSettings(); finish() }
        binding.btnBack.setOnClickListener { finish() }

        binding.btnIconSettings.setOnClickListener {
            startActivity(Intent(this, IconSettingsActivity::class.java))
        }

        binding.btnTestTts.setOnClickListener {
            val key = binding.etOpenAiKey.text.toString().trim()
            val voice = binding.spinnerTtsVoice.selectedItem.toString()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speak("Zdravo, to je test govora aplikacije Nova Rehab.", "sl", key, voice) {
                tts.destroy()
            }
        }

        binding.btnInstallTts.setOnClickListener {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }

        binding.btnTestMail.setOnClickListener {
            saveSettings()
            ReportWorker.schedule(this, prefs.getReportHour())
            Toast.makeText(this, "Poročilo bo poslano ob ${prefs.getReportHour()}:00", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 301 && resultCode == RESULT_OK && pendingImageIndex >= 0) {
            val uri = data?.data ?: return
            try {
                val dir = File(getExternalFilesDir(null), "contacts"); dir.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "contact_$pendingImageIndex.png").outputStream().use { output -> input.copyTo(output) }
                }
                val bmp = BitmapFactory.decodeFile(File(dir, "contact_$pendingImageIndex.png").absolutePath)
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bmp)
            } catch (e: Exception) {}
        }
    }

    private fun saveSettings() {
        // Radio
        val nameF = listOf(binding.etStation1Name, binding.etStation2Name, binding.etStation3Name,
            binding.etStation4Name, binding.etStation5Name, binding.etStation6Name)
        val urlF = listOf(binding.etStation1Url, binding.etStation2Url, binding.etStation3Url,
            binding.etStation4Url, binding.etStation5Url, binding.etStation6Url)
        val stations = mutableListOf<RadioStation>()
        nameF.forEachIndexed { i, f ->
            val n = f.text.toString().trim(); val u = urlF[i].text.toString().trim()
            if (n.isNotEmpty() && u.isNotEmpty()) stations.add(RadioStation(n, u))
        }
        prefs.saveRadioStations(stations)

        // Kontakti
        val nameC = listOf(binding.etContact1Name, binding.etContact2Name, binding.etContact3Name,
            binding.etContact4Name, binding.etContact5Name, binding.etContact6Name)
        val phoneC = listOf(binding.etContact1Phone, binding.etContact2Phone, binding.etContact3Phone,
            binding.etContact4Phone, binding.etContact5Phone, binding.etContact6Phone)
        val emojis = listOf("👩","👨","👧","🧑","👨‍⚕️","🧑‍💼")
        val contacts = mutableListOf<Contact>()
        nameC.forEachIndexed { i, f ->
            val n = f.text.toString().trim()
            val p = phoneC[i].text.toString().trim()
            val lang = if (contactLangSpinners.getOrNull(i)?.selectedItemPosition == 1) "uk" else "sl"
            contacts.add(Contact(n.ifEmpty { "Kontakt ${i+1}" }, p, emojis.getOrElse(i){"👤"}, lang))
        }
        prefs.saveContacts(contacts)

        // OpenAI TTS
        prefs.saveOpenAiKey(binding.etOpenAiKey.text.toString().trim())
        prefs.saveTtsVoice(binding.spinnerTtsVoice.selectedItem.toString())

        // Mail
        prefs.saveGmailUser(binding.etGmailUser.text.toString().trim())
        prefs.saveGmailAppPassword(binding.etGmailPass.text.toString().trim())
        prefs.saveReportMail1(binding.etReportMail1.text.toString().trim())
        prefs.saveReportMail2(binding.etReportMail2.text.toString().trim())
        prefs.saveReportMail1Enabled(binding.switchMail1.isChecked)
        prefs.saveReportMail2Enabled(binding.switchMail2.isChecked)
        prefs.saveReportHour(binding.etReportHour.text.toString().trim().toIntOrNull() ?: 8)

        // Ostalo
        prefs.saveNavigationEnabled(binding.switchNavigation.isChecked)
        prefs.saveHomeAddress(binding.etHomeAddress.text.toString().trim())
        prefs.savePatientName(binding.etPatientName.text.toString().trim())
        prefs.saveCommIconsPerPage(when (spinnerCommIconsPerPage.selectedItemPosition) {
            0 -> 6
            2 -> 12
            3 -> 18
            else -> 8
        })
        prefs.saveDefaultSpeechLanguage(langCode(spinnerDefaultSpeechLang.selectedItemPosition))
        prefs.savePatientLanguage1(langCode(spinnerPatientLang1.selectedItemPosition))
        prefs.savePatientLanguage2(langCode(spinnerPatientLang2.selectedItemPosition))
        prefs.saveAutoLanguageEnabled(switchAutoLanguage.isChecked)
        prefs.saveServerIp(binding.etServerIp.text.toString().trim())
        prefs.saveServerPort(binding.etServerPort.text.toString().trim())
        prefs.saveKioskReturnMinutes(binding.etKioskMinutes.text.toString().trim().toLongOrNull() ?: 5L)
        val pin = binding.etNewPin.text.toString().trim()
        if (pin.length == 4) prefs.savePin(pin)

        Toast.makeText(this, "Nastavitve shranjene ✓", Toast.LENGTH_SHORT).show()
    }
}
