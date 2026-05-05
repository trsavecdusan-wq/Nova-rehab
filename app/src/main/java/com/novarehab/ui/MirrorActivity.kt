package com.novarehab.ui

import android.Manifest
import android.content.pm.PackageManager
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

class MirrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMirrorBinding
    private val handler = Handler(Looper.getMainLooper())
    private val cameraPermissionRequest = 200
    private lateinit var cameraManager: MirrorCameraManager
    private lateinit var mediaGalleryRepository: MediaGalleryRepository

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

        binding.btnCloseMirror.setOnClickListener { finish() }
        binding.btnSwitchCamera.setOnClickListener {
            resetCloseTimer()
            cameraManager.toggleCamera(this, binding.previewView, ::showCameraError)
        }
        binding.btnCaptureMirror.setOnClickListener {
            resetCloseTimer()
            cameraManager.capture(
                executor = ContextCompat.getMainExecutor(this),
                onSaved = { file ->
                    mediaGalleryRepository.saveCameraCapture(file)
                    runOnUiThread {
                        Toast.makeText(this, "Slika shranjena v galerijo.", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Shranjevanje slike ni uspelo: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequest
            )
        }

        resetCloseTimer()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Dovoljenje za kamero zavrnjeno", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        cameraManager.resetToFrontCamera(this, binding.previewView, ::showCameraError)
    }

    private fun showCameraError(error: Throwable) {
        Toast.makeText(this, "Napaka pri kameri: ${error.message}", Toast.LENGTH_LONG).show()
    }

    private fun resetCloseTimer() {
        handler.removeCallbacksAndMessages(null)
        val timeout = intent.getLongExtra("mirror_timeout_ms", 60_000L).coerceIn(10_000L, 300_000L)
        handler.postDelayed({ finish() }, timeout)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
