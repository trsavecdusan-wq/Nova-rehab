package com.novarehab.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

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
        val stations = prefs.getRadioStations()
        val nameFields = listOf(
            binding.etStation1Name, binding.etStation2Name, binding.etStation3Name,
            binding.etStation4Name, binding.etStation5Name, binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url, binding.etStation2Url, binding.etStation3Url,
            binding.etStation4Url, binding.etStation5Url, binding.etStation6Url
        )
        stations.forEachIndexed { index, station ->
            if (index < nameFields.size) {
                nameFields[index].setText(station.name)
                urlFields[index].setText(station.url)
            }
        }
        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())
        binding.etNewPin.setText("")
        binding.etReportEmail.setText(prefs.getReportEmail())
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Nastavitve shranjene", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun saveSettings() {
        val nameFields = listOf(
            binding.etStation1Name, binding.etStation2Name, binding.etStation3Name,
            binding.etStation4Name, binding.etStation5Name, binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url, binding.etStation2Url, binding.etStation3Url,
            binding.etStation4Url, binding.etStation5Url, binding.etStation6Url
        )
        val stations = mutableListOf<RadioStation>()
        nameFields.forEachIndexed { index, nameField ->
            val name = nameField.text.toString().trim()
            val url = urlFields[index].text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                stations.add(RadioStation(name, url))
            }
        }
        prefs.saveRadioStations(stations)
        prefs.saveServerIp(binding.etServerIp.text.toString().trim())
        prefs.saveServerPort(binding.etServerPort.text.toString().trim())
        prefs.saveReportEmail(binding.etReportEmail.text.toString().trim())
        val newPin = binding.etNewPin.text.toString().trim()
        if (newPin.length == 4) prefs.savePin(newPin)
    }
}
