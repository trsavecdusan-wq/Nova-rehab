package com.novarehab.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.databinding.ActivityMainBinding
import com.novarehab.service.RadioService
import com.novarehab.service.UpdateService
import com.novarehab.utils.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private val PERMISSIONS_REQUEST = 100

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

        requestAllPermissions()
        setupRadioButtons()
        setupBottomButtons()
        startUpdateService()
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        permissions.add(Manifest.permission.CAMERA)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, PERMISSIONS_REQUEST)
        }
    }

    private fun setupRadioButtons() {
        val stations = prefs.getRadioStations()
        val radioButtons = listOf(
            binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
            binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
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
        binding.btnCommunication.setOnClickListener {
            startActivity(Intent(this, CommunicationActivity::class.java))
        }
        binding.btnVideoCall.setOnClickListener {
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
        binding.btnSettings.setOnClickListener { showPinDialog() }
    }

    private fun playStation(url: String, name: String) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_URL, url)
            putExtra(RadioService.EXTRA_NAME, name)
        }
        startForegroundService(intent)
        binding.tvNowPlaying.text = "PREDVAJA: $name"
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
            binding.btnRadio1, binding.btnRadio2, binding.btnRadio3,
            binding.btnRadio4, binding.btnRadio5, binding.btnRadio6
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
        startService(Intent(this, UpdateService::class.java))
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
}
