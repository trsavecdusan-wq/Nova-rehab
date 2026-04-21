package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    // Language spinners za vsakega od 6 kontaktov
    private val contactLangSpinners = mutableListOf<Spinner>()
    private val contactImageButtons = mutableListOf<ImageButton>()
    private var pendingImageIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)
        loadSettings()
        setupSaveButton()
        setupBackButton()
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

        // Kontakti — dinamično dodamo spinnerje za jezik v obstoječe contact container
        val contacts = prefs.getContacts()
        val contactNameFields = listOf(binding.etContact1Name, binding.etContact2Name, binding.etContact3Name,
            binding.etContact4Name, binding.etContact5Name, binding.etContact6Name)
        val contactPhoneFields = listOf(binding.etContact1Phone, binding.etContact2Phone, binding.etContact3Phone,
            binding.etContact4Phone, binding.etContact5Phone, binding.etContact6Phone)
        val langContainers = listOf(binding.langContainer1, binding.langContainer2, binding.langContainer3,
            binding.langContainer4, binding.langContainer5, binding.langContainer6)
        val imgContainers = listOf(binding.imgContainer1, binding.imgContainer2, binding.imgContainer3,
            binding.imgContainer4, binding.imgContainer5, binding.imgContainer6)

        contactLangSpinners.clear()
        contactImageButtons.clear()

        contacts.forEachIndexed { i, c ->
            if (i < contactNameFields.size) {
                contactNameFields[i].setText(c.name)
                contactPhoneFields[i].setText(c.phone)
            }
        }

        for (i in 0 until 6) {
            val contact = contacts.getOrNull(i)

            // Spinner za jezik
            val spinner = Spinner(this).apply {
                val options = arrayOf("🇸🇮 Slovenščina (SL)", "🇺🇦 Ukrajinščina (UK)")
                adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, options).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(if (contact?.language == "uk") 1 else 0)
            }
            langContainers[i].addView(spinner)
            contactLangSpinners.add(spinner)

            // Gumb za sliko kontakta
            val imgBtn = ImageButton(this).apply {
                val contactImg = File(getExternalFilesDir(null), "contacts/contact_$i.png")
                if (contactImg.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(contactImg.absolutePath))
                } else {
                    setImageResource(android.R.drawable.ic_menu_camera)
                }
                setBackgroundColor(0xFF333355.toInt())
                val lp = LinearLayout.LayoutParams(80, 80)
                lp.setMargins(0, 4, 0, 4)
                layoutParams = lp
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener {
                    pendingImageIndex = i
                    val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                    startActivityForResult(intent, REQUEST_CONTACT_IMAGE)
                }
            }
            imgContainers[i].addView(imgBtn)
            contactImageButtons.add(imgBtn)
        }

        // Ostalo
        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())
        binding.etNewPin.setText("")
        binding.etReportEmail.setText(prefs.getReportEmail())
        binding.etPatientName.setText(prefs.getPatientName())
        binding.etHomeAddress.setText(prefs.getHomeAddress())
        binding.etKioskMinutes.setText(prefs.getKioskReturnMinutes().toString())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONTACT_IMAGE && resultCode == RESULT_OK && pendingImageIndex >= 0) {
            val uri = data?.data ?: return
            try {
                val dir = File(getExternalFilesDir(null), "contacts")
                dir.mkdirs()
                val outFile = File(dir, "contact_$pendingImageIndex.png")
                contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                // Posodobi gumb
                val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bmp)
                Toast.makeText(this, "Slika shranjena", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Napaka: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Nastavitve shranjene", Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.btnIconSettings.setOnClickListener {
            startActivity(android.content.Intent(this, IconSettingsActivity::class.java))
        }
        // Gumb za test TTS
        binding.btnTestTts.setOnClickListener {
            testTts()
        }
        // Gumb za namestitev TTS podatkov
        binding.btnInstallTts.setOnClickListener {
            startActivity(android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
    }

    private fun testTts() {
        val tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Toast.makeText(this, "TTS OK - govorim...", Toast.LENGTH_SHORT).show()
                }, 500)
            } else {
                Toast.makeText(this, "TTS napaka! Namesti glasovne podatke.", Toast.LENGTH_LONG).show()
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tts.speak("Zdravo, to je test govora", TextToSpeech.QUEUE_FLUSH, null, "test")
        }, 1000)
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun saveSettings() {
        // Radio
        val nameFields = listOf(binding.etStation1Name, binding.etStation2Name, binding.etStation3Name,
            binding.etStation4Name, binding.etStation5Name, binding.etStation6Name)
        val urlFields = listOf(binding.etStation1Url, binding.etStation2Url, binding.etStation3Url,
            binding.etStation4Url, binding.etStation5Url, binding.etStation6Url)
        val stations = mutableListOf<RadioStation>()
        nameFields.forEachIndexed { i, f ->
            val name = f.text.toString().trim()
            val url = urlFields[i].text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) stations.add(RadioStation(name, url))
        }
        prefs.saveRadioStations(stations)

        // Kontakti
        val contactNameFields = listOf(binding.etContact1Name, binding.etContact2Name, binding.etContact3Name,
            binding.etContact4Name, binding.etContact5Name, binding.etContact6Name)
        val contactPhoneFields = listOf(binding.etContact1Phone, binding.etContact2Phone, binding.etContact3Phone,
            binding.etContact4Phone, binding.etContact5Phone, binding.etContact6Phone)
        val contactEmojis = listOf("👩", "👨", "👧", "🧑", "👨‍⚕️", "🧑‍💼")
        val contacts = mutableListOf<Contact>()
        contactNameFields.forEachIndexed { i, f ->
            val name = f.text.toString().trim()
            val phone = contactPhoneFields[i].text.toString().trim()
            val lang = if (contactLangSpinners.getOrNull(i)?.selectedItemPosition == 1) "uk" else "sl"
            contacts.add(Contact(name.ifEmpty { "Kontakt ${i+1}" }, phone, contactEmojis.getOrElse(i){"👤"}, lang))
        }
        prefs.saveContacts(contacts)

        // Ostalo
        prefs.saveServerIp(binding.etServerIp.text.toString().trim())
        prefs.saveServerPort(binding.etServerPort.text.toString().trim())
        prefs.saveReportEmail(binding.etReportEmail.text.toString().trim())
        prefs.savePatientName(binding.etPatientName.text.toString().trim())
        prefs.saveHomeAddress(binding.etHomeAddress.text.toString().trim())
        val kioskMin = binding.etKioskMinutes.text.toString().trim().toLongOrNull() ?: 5L
        prefs.saveKioskReturnMinutes(kioskMin)
        val newPin = binding.etNewPin.text.toString().trim()
        if (newPin.length == 4) prefs.savePin(newPin)
    }

    companion object {
        private const val REQUEST_CONTACT_IMAGE = 301
    }
}
