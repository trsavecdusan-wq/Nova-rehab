package com.novarehab.ui

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.camera.MirrorCameraManager
import com.novarehab.databinding.ActivityMirrorBinding
import com.novarehab.media_messaging.repository.MediaGalleryRepository
import com.novarehab.utils.StatEvent
import com.novarehab.utils.StatsManager

class MirrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMirrorBinding
    private val handler = Handler(Looper.getMainLooper())
    private val cameraPermissionRequest = 200
    private lateinit var cameraManager: MirrorCameraManager
    private lateinit var mediaGalleryRepository: MediaGalleryRepository
    private lateinit var statsManager: StatsManager
    private var backCameraResetRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityMirrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraManager = MirrorCameraManager(this)
        mediaGalleryRepository = MediaGalleryRepository(this)
        statsManager = StatsManager(this)
        statsManager.log(StatEvent.MIRROR_OPEN)

        binding.btnCloseMirror.setOnClickListener { finish() }
        binding.btnSwitchCamera.setOnClickListener {
            resetCloseTimer()
            cameraManager.toggleCamera(this, binding.previewView, ::showCameraError)
            scheduleFrontCameraReturnIfNeeded()
        }
        binding.btnCaptureMirror.setOnClickListener {
            resetCloseTimer()
            cameraManager.capture(
                executor = ContextCompat.getMainExecutor(this),
                onSaved = { file ->
                    runOnUiThread {
                        runCatching {
                            mediaGalleryRepository.saveCameraCapture(file)
                        }.onSuccess { saved ->
                            if (mediaGalleryRepository.isStoredMediaAvailable(saved.localPath)) {
                                cameraManager.resetToFrontCamera(this, binding.previewView, ::showCameraError)
                                cancelBackCameraReset()
                                file.delete()
                                Toast.makeText(this, "Slika shranjena v galerijo", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Shranjevanje slike ni uspelo: ciljna datoteka ni bila najdena.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }.onFailure { error ->
                            Toast.makeText(
                                this,
                                "Shranjevanje slike ni uspelo: ${error.message ?: "neznan razlog"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Shranjevanje slike ni uspelo: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (hasRequiredPermissions()) {
            startCamera()
        } else {
            requestRequiredPermissions()
        }

        resetCloseTimer()
    }

    override fun onResume() {
        super.onResume()
        resetCloseTimer()
        if (hasRequiredPermissions()) {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) {
            if (hasRequiredPermissions()) {
                startCamera()
            } else {
                Toast.makeText(this, "Dovoljenja za kamero ali galerijo niso odobrena.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionRequest
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        cameraManager.resetToFrontCamera(this, binding.previewView, ::showCameraError)
        cancelBackCameraReset()
    }

    private fun showCameraError(error: Throwable) {
        Toast.makeText(this, "Napaka pri kameri: ${error.message}", Toast.LENGTH_LONG).show()
    }

    private fun resetCloseTimer() {
        handler.removeCallbacksAndMessages(null)
        val timeout = intent.getLongExtra("mirror_timeout_ms", 60_000L).coerceIn(10_000L, 300_000L)
        handler.postDelayed({ finish() }, timeout)
        backCameraResetRunnable?.let { runnable ->
            val backTimeout = intent.getLongExtra("mirror_back_camera_return_ms", 20_000L).coerceIn(5_000L, 120_000L)
            handler.postDelayed(runnable, backTimeout)
        }
    }

    private fun scheduleFrontCameraReturnIfNeeded() {
        cancelBackCameraReset()
        if (!cameraManager.isUsingBackCamera()) return

        backCameraResetRunnable = Runnable {
            cameraManager.resetToFrontCamera(this, binding.previewView, ::showCameraError)
            cancelBackCameraReset()
        }
        resetCloseTimer()
    }

    private fun cancelBackCameraReset() {
        backCameraResetRunnable?.let { handler.removeCallbacks(it) }
        backCameraResetRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        backCameraResetRunnable = null
    }
}


