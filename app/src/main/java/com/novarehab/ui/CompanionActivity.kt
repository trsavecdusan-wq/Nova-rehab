package com.novarehab.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.SurfaceViewRenderer

class CompanionActivity : AppCompatActivity() {

    private lateinit var etRoomId: EditText
    private lateinit var tvStatus: TextView
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer

    private var callManager: VideoCallManager? = null
    private var pendingConnect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        tvStatus = TextView(this).apply {
            text = "Companion sprejemnik"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(tvStatus)

        etRoomId = EditText(this).apply {
            hint = "roomId"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
            setSingleLine(true)
            setText(intent.getStringExtra(RehabCallExtras.EXTRA_ROOM_ID).orEmpty())
        }
        root.addView(etRoomId)

        val btnConnect = Button(this).apply {
            text = "POVEŽI IN ODGOVORI"
            textSize = 22f
            setOnClickListener { connectWithPermission() }
        }
        root.addView(btnConnect)

        remoteRenderer = SurfaceViewRenderer(this)
        root.addView(remoteRenderer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        localRenderer = SurfaceViewRenderer(this)
        root.addView(localRenderer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            220
        ))

        val btnEnd = Button(this).apply {
            text = "PREKINI"
            textSize = 22f
            setOnClickListener { endCall() }
        }
        root.addView(btnEnd)

        setContentView(root)
    }

    private fun connectWithPermission() {
        if (hasCallPermissions()) {
            connectReceiver()
        } else {
            pendingConnect = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                REQUEST_CALL_PERMISSIONS
            )
        }
    }

    private fun connectReceiver() {
        val roomId = etRoomId.text.toString().trim()
        if (roomId.isEmpty()) {
            Toast.makeText(this, "Vnesi roomId.", Toast.LENGTH_LONG).show()
            return
        }

        tvStatus.text = "Čakam klic..."

        callManager?.close(clearRoom = false)
        callManager = VideoCallManager(
            context = this,
            roomId = roomId,
            localRenderer = localRenderer,
            remoteRenderer = remoteRenderer,
            listener = object : VideoCallManager.Listener {
                override fun onStatus(text: String) {
                    runOnUiThread { tvStatus.text = text }
                }

                override fun onConnected() {
                    runOnUiThread { tvStatus.text = "Klic vzpostavljen" }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        tvStatus.text = "Napaka"
                        Toast.makeText(this@CompanionActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        callManager?.connectAsReceiver()
    }

    private fun endCall() {
        callManager?.close(clearRoom = false)
        callManager = null
        tvStatus.text = "Klic prekinjen"
        remoteRenderer.visibility = View.VISIBLE
        localRenderer.visibility = View.VISIBLE
    }

    private fun hasCallPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CALL_PERMISSIONS && pendingConnect) {
            pendingConnect = false
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                connectReceiver()
            } else {
                Toast.makeText(this, "Kamera in mikrofon sta potrebna.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        callManager?.close(clearRoom = false)
        callManager = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CALL_PERMISSIONS = 601
    }
}
