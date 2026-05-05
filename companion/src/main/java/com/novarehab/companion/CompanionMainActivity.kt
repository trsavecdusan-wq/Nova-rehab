package com.novarehab.companion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var btnCallContact: Button
    private lateinit var btnSendImage: Button
    private lateinit var btnSelectContact: Button
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var contactConfigManager: ContactConfigManager

    private var callState: CompanionCallState = CompanionCallState.IDLE
    private var callManager: CompanionCallManager? = null
    private val mediaSender = CompanionMediaSender(CompanionConfig.signalingBaseUrl)
    private val statusHandler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var outgoingDialing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_companion_main)

        contactConfigManager = ContactConfigManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvContactInfo = findViewById(R.id.tvContactInfo)
        btnAcceptCall = findViewById(R.id.btnAcceptCall)
        btnRejectCall = findViewById(R.id.btnRejectCall)
        btnCallContact = findViewById(R.id.btnCallLana)
        btnSendImage = findViewById(R.id.btnSendImage)
        btnSelectContact = findViewById(R.id.btnSelectContact)
        localRenderer = findViewById(R.id.localRenderer)
        remoteRenderer = findViewById(R.id.remoteRenderer)

        btnAcceptCall.setOnClickListener {
            if (!hasVideoPermissions()) {
                requestVideoPermissions()
                Toast.makeText(this, "Dovoli kamero in mikrofon za video klic.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            callState = CompanionCallState.ACTIVE
            updateStatus()
            callManager?.acceptCall()
        }

        btnRejectCall.setOnClickListener {
            if (callState == CompanionCallState.ACTIVE) {
                callManager?.endCall()
                callState = CompanionCallState.IDLE
            } else {
                callManager?.rejectCall()
                callState = CompanionCallState.MISSED
            }
            updateStatus()
        }

        btnCallContact.setOnClickListener { sendTestCallToTablet() }
        btnSendImage.setOnClickListener { openImagePicker() }
        btnSelectContact.setOnClickListener { showContactSettings() }

        requestVideoPermissions()

        if (!contactConfigManager.isConfigured()) {
            showContactSelector(force = true)
        } else {
            applySelectedContact(contactConfigManager.getCurrent(), restartCallManager = false)
            startWaitingForCall()
        }
    }

    private fun applySelectedContact(
        config: CompanionContactConfig,
        restartCallManager: Boolean
    ) {
        contactConfigManager.save(config)
        CompanionConfig.update(config)
        refreshContactUi()

        if (restartCallManager) {
            recreateCallManager()
        }
    }

    private fun refreshContactUi() {
        val name = CompanionConfig.contactName
        val language = CompanionConfig.preferredLanguageCode.uppercase()
        tvContactInfo.text = "Kontakt: $name  •  Jezik: $language"
        btnCallContact.text = CompanionContactText.callButtonText(name)
        btnSelectContact.text = "NASTAVITVE KONTAKTA"
    }

    private fun showContactSettings() {
        AlertDialog.Builder(this)
            .setTitle("Nastavitve kontakta")
            .setItems(arrayOf("Spremeni kontakt", "Prekliči")) { dialog, which ->
                if (which == 0) {
                    showContactSelector(force = false)
                } else {
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun showContactSelector(force: Boolean) {
        val contacts = contactConfigManager.availableContacts
        val labels = contacts.map { it.contactName }.toTypedArray()
        val selected = contacts.indexOfFirst { it.contactId == contactConfigManager.getCurrent().contactId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Izberi kontakt")
            .setSingleChoiceItems(labels, selected, null)
            .setCancelable(!force)
            .setPositiveButton("Shrani") { dialog, _ ->
                val position = (dialog as AlertDialog).listView.checkedItemPosition
                val config = contacts.getOrNull(position) ?: contacts.last()
                applySelectedContact(config, restartCallManager = true)
                startWaitingForCall()
            }
            .setNegativeButton(if (force) null else "Prekliči", null)
            .show()
    }

    private fun startWaitingForCall() {
        callState = CompanionCallState.IDLE
        createCallManager()
        callManager?.startWaitingForCall()
        updateStatus()
    }

    private fun recreateCallManager() {
        callManager?.endCall()
        callManager = null
        createCallManager()
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
                    outgoingDialing = false
                    callState = CompanionCallState.RINGING
                    updateStatus()
                }

                override fun onCallStarted() {
                    outgoingDialing = false
                    callState = CompanionCallState.ACTIVE
                    updateStatus()
                }

                override fun onCallEnded() {
                    outgoingDialing = false
                    callState = CompanionCallState.IDLE
                    updateStatus()
                    if (!destroyed && CompanionConfig.incomingCallsEnabled) {
                        tvStatus.postDelayed({
                            if (!destroyed) startWaitingForCall()
                        }, 1500L)
                    }
                }

                override fun onTabletMessage(text: String) {
                    val message = "${CompanionConfig.contactName}: $text"
                    tvStatus.text = message
                    Toast.makeText(this@CompanionMainActivity, message, Toast.LENGTH_LONG).show()
                }

                override fun onError(text: String) {
                    outgoingDialing = false
                    if (text.contains("zased", ignoreCase = true) || text.contains("busy", ignoreCase = true)) {
                        callState = CompanionCallState.BUSY
                        updateStatus()
                    }
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
        callManager?.sendOutgoingTestCall(CompanionConfig.contactName)
        outgoingDialing = true
        callState = CompanionCallState.RINGING
        updateStatus()
        statusHandler.postDelayed({
            if (!destroyed && outgoingDialing && callState == CompanionCallState.RINGING) {
                outgoingDialing = false
                updateStatus()
            }
        }, 1800L)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, 701)
    }

    private fun legacyUpdateStatus() {
        val name = CompanionConfig.contactName
        tvContactInfo.text = "Kontakt: $name"
        btnCallContact.text = "POKLIČI $name".uppercase()

        tvStatus.text = when (callState) {
            CompanionCallState.IDLE -> "Pokliči $name"
            CompanionCallState.RINGING -> "Čakam odgovor $name"
            CompanionCallState.ACTIVE -> "$name je sprejel"
            CompanionCallState.BUSY -> "$name je zaseden"
            CompanionCallState.MISSED -> "$name je zavrnil"
        }

        btnAcceptCall.visibility = if (callState == CompanionCallState.RINGING) View.VISIBLE else View.GONE
        btnRejectCall.visibility = if (callState == CompanionCallState.RINGING || callState == CompanionCallState.ACTIVE) View.VISIBLE else View.GONE
        btnRejectCall.text = if (callState == CompanionCallState.ACTIVE) "PREKINI KLIC" else "ZAVRNI KLIC"
        btnCallContact.visibility = if (callState == CompanionCallState.IDLE) View.VISIBLE else View.GONE
    }

    private fun updateStatus() {
        val name = CompanionConfig.contactName
        refreshContactUi()

        tvStatus.text = when (callState) {
            CompanionCallState.IDLE -> CompanionContactText.idleStatus(name)
            CompanionCallState.RINGING -> {
                if (outgoingDialing) CompanionContactText.callingStatus(name)
                else CompanionContactText.waitingStatus(name)
            }
            CompanionCallState.ACTIVE -> CompanionContactText.acceptedStatus(name)
            CompanionCallState.BUSY -> CompanionContactText.busyStatus(name)
            CompanionCallState.MISSED -> CompanionContactText.rejectedStatus(name)
        }

        btnAcceptCall.visibility = if (callState == CompanionCallState.RINGING) View.VISIBLE else View.GONE
        btnRejectCall.visibility = if (callState == CompanionCallState.RINGING || callState == CompanionCallState.ACTIVE) View.VISIBLE else View.GONE
        btnRejectCall.text = if (callState == CompanionCallState.ACTIVE) "PREKINI KLIC" else "ZAVRNI KLIC"
        btnCallContact.visibility = if (callState == CompanionCallState.IDLE) View.VISIBLE else View.GONE
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 701 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            Thread {
                try {
                    mediaSender.sendImage(
                        contentResolver = contentResolver,
                        imageUri = uri,
                        senderId = CompanionConfig.contactId,
                        senderName = CompanionConfig.contactName
                    )
                    runOnUiThread {
                        updateStatus()
                        tvStatus.text = "Slika poslana"
                        Toast.makeText(this, "Slika je bila poslana tablici.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, e.localizedMessage ?: "Pošiljanje slike ni uspelo.", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
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
            )
    }

    override fun onDestroy() {
        destroyed = true
        statusHandler.removeCallbacksAndMessages(null)
        callManager?.endCall()
        callManager = null
        super.onDestroy()
    }
}
