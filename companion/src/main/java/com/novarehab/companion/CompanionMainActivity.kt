package com.novarehab.companion

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class CompanionMainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvContactInfo: TextView

    private var callState: CompanionCallState = CompanionCallState.WAITING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_companion_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvContactInfo = findViewById(R.id.tvContactInfo)

        findViewById<Button>(R.id.btnAcceptCall).setOnClickListener {
            callState = CompanionCallState.CONNECTED
            updateStatus()
        }

        findViewById<Button>(R.id.btnRejectCall).setOnClickListener {
            callState = CompanionCallState.ENDED
            updateStatus()
        }

        findViewById<Button>(R.id.btnCallLana).setOnClickListener {
            callState = CompanionCallState.WAITING
            tvStatus.text = "Zahteva za klic bo dodana v naslednji fazi."
        }

        updateStatus()
    }

    private fun updateStatus() {
        tvContactInfo.text = "Kontakt: ${CompanionConfig.contactName}"

        tvStatus.text = when (callState) {
            CompanionCallState.WAITING -> "Povezano z Lano"
            CompanionCallState.INCOMING -> "Lana kliče"
            CompanionCallState.CONNECTED -> "Klic vzpostavljen"
            CompanionCallState.ENDED -> "Klic zavrnjen"
        }
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
