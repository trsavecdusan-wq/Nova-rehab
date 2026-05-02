package com.novarehab.companion

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.SurfaceViewRenderer

class CompanionMainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var btnAcceptCall: Button
    private lateinit var btnRejectCall: Button
    private lateinit var btnCallLana: Button
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer

    private var callState: CompanionCallState = CompanionCallState.WAITING
    private var callManager: CompanionCallManager? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_companion_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvContactInfo = findViewById(R.id.tvContactInfo)
        btnAcceptCall = findViewById(R.id.btnAcceptCall)
        btnRejectCall = findViewById(R.id.btnRejectCall)
        btnCallLana = findViewById(R.id.btnCallLana)
        localRenderer = findViewById(R.id.localRenderer)
        remoteRenderer = findViewById(R.id.remoteRenderer)

        btnAcceptCall.setOnClickListener {
            if (!hasVideoPermissions()) {
                requestVideoPermissions()
                Toast.makeText(this, "Dovoli kamero in mikrofon za video klic.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            callState = CompanionCallState.CONNECTED
            updateStatus()
            callManager?.acceptCall()
        }

        btnRejectCall.setOnClickListener {
            if (callState == CompanionCallState.CONNECTED) {
                callManager?.endCall()
            } else {
                callState = CompanionCallState.ENDED
                updateStatus()
                callManager?.rejectCall()
            }
        }

        btnCallLana.setOnClickListener {
            sendTestCallToTablet()
        }

        requestVideoPermissions()
        createCallManager()
        updateStatus()

        if (CompanionConfig.incomingCallsEnabled) {
            startWaitingForCall()
        } else {
            tvStatus.text = "Dohodni klici so izklopljeni"
        }
    }

    private fun startWaitingForCall() {
        createCallManager()
        callManager?.startWaitingForCall()
    }

    private fun createCallManager() {
        if (callManager != null) return

        callManager = CompanionCallManager(
            context = this,
            localRenderer = localRenderer,
            remoteRenderer = remoteRenderer,
            signalingBaseUrl = CompanionConfig.signalingBaseUrl,
            roomId = CompanionConfig.roomId,
            listener = object : CompanionCallManager.Listener {
                override fun onStatus(text: String) {
                    tvStatus.text = text
                }

                override fun onIncomingCall() {
                    callState = CompanionCallState.INCOMING
                    updateStatus()
                }

                override fun onCallStarted() {
                    callState = CompanionCallState.CONNECTED
                    updateStatus()
                }

                override fun onCallEnded() {
                    callState = CompanionCallState.WAITING
                    updateStatus()

                    if (!destroyed && CompanionConfig.incomingCallsEnabled) {
                        tvStatus.postDelayed({
                            if (!destroyed) startWaitingForCall()
                        }, 1500L)
                    }
                }

                override fun onTabletMessage(text: String) {
                    val message = "Lana: $text"
                    tvStatus.text = message
                    Toast.makeText(this@CompanionMainActivity, message, Toast.LENGTH_LONG).show()
                }

                override fun onError(text: String) {
                    tvStatus.text = text
                    Toast.makeText(this@CompanionMainActivity, text, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun sendTestCallToTablet() {
        if (!CompanionConfig.outgoingCallsEnabled) {
            Toast.makeText(this, "Odhodni klici so izklopljeni.", Toast.LENGTH_LONG).show()
            return
        }

        createCallManager()
        tvStatus.text = "Čakam odgovor Lane"
        callManager?.sendOutgoingTestCall(CompanionConfig.contactName)
    }

    private fun updateStatus() {
        tvContactInfo.text = "Klic Lani"

        tvStatus.text = when (callState) {
            CompanionCallState.WAITING -> "Čakam povezavo"
            CompanionCallState.INCOMING -> "Lana kliče"
            CompanionCallState.CONNECTED -> "Klic vzpostavljen"
            CompanionCallState.ENDED -> "Klic zavrnjen"
        }

        btnAcceptCall.visibility = if (callState == CompanionCallState.INCOMING) View.VISIBLE else View.GONE
        btnRejectCall.visibility = if (
            callState == CompanionCallState.INCOMING ||
            callState == CompanionCallState.CONNECTED
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        btnRejectCall.text = if (callState == CompanionCallState.CONNECTED) {
            "PREKINI KLIC"
        } else {
            "ZAVRNI KLIC"
        }

        btnCallLana.visibility = if (
            callState == CompanionCallState.WAITING &&
            CompanionConfig.outgoingCallsEnabled
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun requestVideoPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 601)
        }
    }

    private fun hasVideoPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
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
            )
    }

    override fun onDestroy() {
        destroyed = true
        callManager?.endCall()
        callManager = null
        super.onDestroy()
    }
}
