package com.novarehab.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioService
import com.novarehab.service.UpdateService
import com.novarehab.utils.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        setupRadioButtons()
        setupBottomButtons()
        startUpdateService()
    }

    private fun setupRadioButtons() {
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
            if (index < stations.size) {
                val station = stations[index]
                button.text = station.name
                button.setOnClickListener {
                    playStation(station.url, station.name)
                    updateRadioButtonStates(index)
                }
            }
        }

        binding.btnRadioStop.setOnClickListener {
            stopRadio()
            updateRadioButtonStates(-1)
        }
    }

    private fun setupBottomButtons() {
        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        binding.btnMirror.setOnClickListener {
            startActivity(Intent(this, MirrorActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            showPinDialog()
        }
    }

    private fun playStation(url: String, name: String) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_URL, url)
            putExtra(RadioService.EXTRA_NAME, name)
        }
        startForegroundService(intent)
        binding.tvNowPlaying.text = "▶ $name"
        binding.tvNowPlaying.visibility = View.VISIBLE
    }

    private fun stopRadio() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        binding.tvNowPlaying.visibility = View.GONE
    }

    private fun updateRadioButtonStates(activeIndex: Int) {
        val radioButtons = listOf(
            binding.btnRadio1,
            binding.btnRadio2,
            binding.btnRadio3,
            binding.btnRadio4,
            binding.btnRadio5,
            binding.btnRadio6
        )
        radioButtons.forEachIndexed { index, button ->
            button.isSelected = (index == activeIndex)
        }
    }

    private fun showPinDialog() {
        val dialog = PinDialog(this) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        dialog.show()
    }

    private fun startUpdateService() {
        val intent = Intent(this, UpdateService::class.java)
        startService(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
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
}
