package com.novarehab.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.service.ReportWorker
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import com.novarehab.utils.UpdateManager
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
    private lateinit var btnCheckUpdateNow: Button
    private lateinit var btnRestorePreviousVersion: Button
    private lateinit var btnShareCompanionApp: Button

    private val companionContacts = listOf(
        CompanionShareContact("zana", "Žana"),
        CompanionShareContact("dedek", "Dedek"),
        CompanionShareContact("inna", "Inna"),
        CompanionShareContact("julija", "Julija"),
        CompanionShareContact("kuma", "Kuma"),
        CompanionShareContact("dusan", "Dusan")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

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
        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
        val defaultLanguages = listOf("uk", "uk", "uk", "uk", "uk", "sl")

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

        for (i in 0 until 6) {
            val contact = contacts.getOrNull(i)
            nameF[i].setText(contact?.name?.takeIf { it.isNotBlank() } ?: defaultNames[i])
            phoneF[i].setText(contact?.phone.orEmpty())

            val spinner = Spinner(this).apply {
                val opts = arrayOf("🇸🇮 Slovenščina", "🇺🇦 Ukrajinščina")
                adapter = ArrayAdapter(
                    this@SettingsActivity,
                    android.R.layout.simple_spinner_item,
                    opts
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                val language = contact?.language ?: defaultLanguages[i]
                setSelection(if (language == "uk") 1 else 0)
            }
            langC[i].addView(spinner)
            contactLangSpinners.add(spinner)

            val imgBtn = ImageButton(this).apply {
                val f = File(getExternalFilesDir(null), "contacts/contact_${i + 1}.png")
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
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                Toast.makeText(
                    this,
                    "Če slovenskega glasu ni, namesti RHVoice iz Trgovine Play.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=RHVoice&c=apps")))
                } catch (e2: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=RHVoice&c=apps")))
                }
            }
        }

        binding.btnTestMail.setOnClickListener {
            saveSettings()
            ReportWorker.schedule(this, prefs.getReportHour())
            Toast.makeText(
                this,
                "Poročilo bo poslano ob ${prefs.getReportHour()}:00",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showCompanionSharePicker() {
        val names = companionContacts.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Izberi sogovornika")
            .setItems(names) { _, which ->
                val contact = companionContacts.getOrNull(which)
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
        val allowedIds = companionContacts.map { it.contactId }.toSet()
        if (contactId !in allowedIds) return null

        val baseUrl = "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/"
        return baseUrl + "companion-$contactId-debug.apk"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

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

                val bmp = BitmapFactory.decodeFile(
                    File(dir, "contact_${pendingImageIndex + 1}.png").absolutePath
                )
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bmp)
            } catch (e: Exception) {
            }
        }
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
        nameF.forEachIndexed { i, field ->
            val name = field.text.toString().trim()
            val url = urlF[i].text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                stations.add(RadioStation(name, url))
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

        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
        val emojis = listOf("👩", "👨", "👧", "🧑", "👨‍⚕️", "🧑‍💼")
        val contacts = mutableListOf<Contact>()

        nameC.forEachIndexed { i, field ->
            val name = field.text.toString().trim().ifEmpty { defaultNames[i] }
            val phone = phoneC[i].text.toString().trim()
            val lang = if (contactLangSpinners.getOrNull(i)?.selectedItemPosition == 1) "uk" else "sl"
            contacts.add(Contact(name, phone, emojis.getOrElse(i) { "👤" }, lang))
        }
        prefs.saveContacts(contacts)

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

        Toast.makeText(this, "Nastavitve shranjene ✓", Toast.LENGTH_SHORT).show()
    }

    private data class CompanionShareContact(
        val contactId: String,
        val displayName: String
    )
}
