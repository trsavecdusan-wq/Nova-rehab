package com.novarehab.companion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.SurfaceViewRenderer

class CompanionMainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var btnAcceptCall: Button
    private lateinit var btnRejectCall: Button
    private lateinit var btnCallContact: Button
    private lateinit var btnSendImage: Button
    private lateinit var btnSelectContact: Button
    private lateinit var btnMinimize: Button
    private lateinit var btnExit: Button
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var contactConfigManager: ContactConfigManager

    private var callState: CompanionCallState = CompanionCallState.IDLE
    private var callManager: CompanionCallManager? = null
    private val mediaSender = CompanionMediaSender(CompanionConfig.signalingBaseUrl)
    private val statusHandler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var outgoingDialing = false
    private var selectedContactId: String? = null
    private var selectedContactName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_companion_main)

        contactConfigManager = ContactConfigManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        tvContactInfo = findViewById(R.id.tvContactInfo)
        btnAcceptCall = findViewById(R.id.btnAcceptCall)
        btnRejectCall = findViewById(R.id.btnRejectCall)
        btnCallContact = findViewById(R.id.btnCallLana)
        btnSendImage = findViewById(R.id.btnSendImage)
        btnSelectContact = findViewById(R.id.btnSelectContact)
        btnMinimize = findViewById(R.id.btnMinimize)
        btnExit = findViewById(R.id.btnExit)
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
        btnMinimize.setOnClickListener { minimizeCompanion() }
        btnExit.setOnClickListener { showExitConfirmation() }

        requestVideoPermissions()
        initializeCompanion(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initializeCompanion(intent)
    }

    private fun initializeCompanion(startIntent: Intent?) {
        try {
            if (tryImportSharedConfig(startIntent)) {
                Toast.makeText(this, "Nastavitev iz tablice je uvoĂ„Ä…Ă„Äľena.", Toast.LENGTH_LONG).show()
            }

            val currentConfig = contactConfigManager.getCurrentOrNull()
            if (currentConfig == null) {
                contactConfigManager.clear()
                showContactSelector(force = true)
                return
            }

            startCompanionWithConfig(currentConfig, restartCallManager = false)
        } catch (error: Exception) {
            Log.e(TAG, "Napaka pri zagonu Companion aplikacije", error)
            contactConfigManager.clear()
            tvStatus.text = "Izberi kontakt"
            setConnectionState("Povezava ne deluje", false)
            showContactSelector(force = true)
        }
    }

    private fun tryImportSharedConfig(startIntent: Intent?): Boolean {
        val action = startIntent?.action.orEmpty()
        val payload = when {
            action == Intent.ACTION_SEND -> startIntent?.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim().orEmpty()

        if (payload.isBlank()) return false

        return contactConfigManager.importFromSharedPayload(payload)
            .onSuccess { config ->
                CompanionConfig.update(config)
            }
            .onFailure { error ->
                Log.e(TAG, "Uvoz nastavitve iz tablice ni uspel", error)
                Toast.makeText(this, error.localizedMessage ?: "Uvoz nastavitve ni uspel.", Toast.LENGTH_LONG).show()
            }
            .isSuccess
    }

    private fun startCompanionWithConfig(
        config: CompanionContactConfig,
        restartCallManager: Boolean
    ) {
        try {
            applySelectedContact(config, restartCallManager)
            runCatching { startWaitingForCallSafely() }
                .onFailure { error ->
                    Log.e(TAG, "Napaka pri zagonu po izbiri kontakta ${config.contactId}", error)
                    tvStatus.text = CompanionContactText.idleStatus(CompanionConfig.contactName)
                    setConnectionState("Povezava ne deluje", false)
                }
        } catch (error: Exception) {
            Log.e(TAG, "Napaka pri aktivaciji kontakta ${config.contactId}", error)
            contactConfigManager.clear()
            Toast.makeText(this, "Shranjena nastavitev kontakta ni veljavna. Izberite kontakt znova.", Toast.LENGTH_LONG).show()
            showContactSelector(force = true)
        }
    }

    private fun applySelectedContact(
        config: CompanionContactConfig,
        restartCallManager: Boolean
    ) {
        contactConfigManager.save(config)
        val normalized = contactConfigManager.getCurrentOrNull() ?: config
        CompanionConfig.update(normalized)
        selectedContactId = normalized.contactId
        selectedContactName = normalized.contactName
        refreshContactUi()

        if (restartCallManager) {
            recreateCallManagerSafely()
        }
    }

    private fun refreshContactUi() {
        val name = CompanionConfig.contactName
        val language = CompanionConfig.preferredLanguageCode.uppercase()
        val patient = CompanionConfig.patientName
        tvContactInfo.text = "Kontakt: $name  Ä‚ËĂ˘â€šÂ¬Ă‹Â  Pacient: $patient  Ä‚ËĂ˘â€šÂ¬Ă‹Â  Jezik: $language"
        btnCallContact.text = CompanionContactText.callButtonText(name)
        btnSelectContact.text = "NASTAVITVE KONTAKTA"
    }

    private fun showContactSettings() {
        val items = arrayOf(
            "RoÄ‚â€žÄąÂ¤na izbira kontakta",
            "Uvozi nastavitev iz tablice",
            "Ponastavi izbiro kontakta",
            "PrekliÄ‚â€žÄąÂ¤i"
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nastavitve kontakta")
            .setItems(items) { dialogInterface, which ->
                when (which) {
                    0 -> showContactSelector(force = false)
                    1 -> showImportConfigDialog()
                    2 -> resetSelectedContact()
                    else -> dialogInterface.dismiss()
                }
            }
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun showContactSelector(force: Boolean) {
        val contacts = contactConfigManager.availableContacts
        val labels = contacts.map { it.contactName }.toTypedArray()
        val preselectedId = contactConfigManager.getCurrentOrNull()?.contactId ?: selectedContactId
        val selectedIndex = intArrayOf(contacts.indexOfFirst { it.contactId == preselectedId }.takeIf { it >= 0 } ?: 0)

        selectedContactId = contacts.getOrNull(selectedIndex[0])?.contactId
        selectedContactName = contacts.getOrNull(selectedIndex[0])?.contactName

        val dialog = AlertDialog.Builder(this)
            .setTitle("Izberi kontakt")
            .setSingleChoiceItems(labels, selectedIndex[0]) { _, which ->
                selectedIndex[0] = which
                selectedContactId = contacts.getOrNull(which)?.contactId
                selectedContactName = contacts.getOrNull(which)?.contactName
            }
            .setCancelable(!force)
            .setPositiveButton("Shrani") { dialogInterface, _ ->
                val alert = dialogInterface as AlertDialog
                val checkedIndex = alert.listView.checkedItemPosition.takeIf { it >= 0 } ?: selectedIndex[0]
                val config = contacts.getOrNull(checkedIndex) ?: contacts.first()
                selectedContactId = config.contactId
                selectedContactName = config.contactName
                startCompanionWithConfig(config, restartCallManager = true)
                alert.dismiss()
            }
            .setNegativeButton(if (force) null else "PrekliÄŤi", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun showImportConfigDialog() {
        val input = EditText(this).apply {
            hint = "Prilepi nastavitveni zapis iz tablice"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFD0D8E8.toInt())
            setBackgroundColor(0xFF16213E.toInt())
            setPadding(32, 24, 32, 24)
            minLines = 4
            maxLines = 8
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Uvozi nastavitev iz tablice")
            .setMessage("Prilepite besedilo, ki ga poĂ„Ä…Ă‹â€ˇlje tablica.")
            .setView(input)
            .setPositiveButton("Uvozi") { _, _ ->
                val payload = input.text?.toString().orEmpty().trim()
                if (payload.isBlank()) {
                    Toast.makeText(this, "Nastavitveni zapis je prazen.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                contactConfigManager.importFromSharedPayload(payload)
                    .onSuccess { config ->
                        startCompanionWithConfig(config, restartCallManager = true)
                        Toast.makeText(this, "Nastavitev je uvoĂ„Ä…Ă„Äľena.", Toast.LENGTH_LONG).show()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "RoÄ‚â€žÄąÂ¤ni uvoz nastavitve ni uspel", error)
                        Toast.makeText(this, error.localizedMessage ?: "Uvoz nastavitve ni uspel.", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("PrekliÄ‚â€žÄąÂ¤i", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun resetSelectedContact() {
        contactConfigManager.clear()
        CompanionConfig.update(contactConfigManager.availableContacts.last())
        selectedContactId = null
        selectedContactName = null
        callManager?.endCall()
        callManager = null
        callState = CompanionCallState.IDLE
        tvStatus.text = "Izberi kontakt"
        setConnectionState("Povezava ne deluje", false)
        showContactSelector(force = true)
    }

    private fun startWaitingForCallSafely() {
        callState = CompanionCallState.IDLE
        createCallManager()
        callManager?.startWaitingForCall()
        updateStatus()
    }

    private fun recreateCallManagerSafely() {
        runCatching {
            callManager?.endCall()
        }.onFailure {
            Log.e(TAG, "Varno ustavljanje starega klicnega upravljalnika ni uspelo", it)
        }
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
                    updateConnectionStateFromMessage(text)
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
                    if (!destroyed && CompanionConfig.incomingCallsEnabled && contactConfigManager.isConfigured()) {
                        tvStatus.postDelayed({
                            if (!destroyed) startWaitingForCallSafely()
                        }, 1500L)
                    }
                }

                override fun onTabletMessage(text: String) {
                    val message = "${CompanionConfig.contactName}: $text"
                    tvStatus.text = message
                    setConnectionState("Povezava deluje", true)
                    Toast.makeText(this@CompanionMainActivity, message, Toast.LENGTH_LONG).show()
                }

                override fun onError(text: String) {
                    outgoingDialing = false
                    if (text.contains("zased", ignoreCase = true) || text.contains("busy", ignoreCase = true)) {
                        callState = CompanionCallState.BUSY
                        updateStatus()
                    }
                    tvStatus.text = text
                    setConnectionState(
                        if (text.contains("zased", ignoreCase = true) || text.contains("busy", ignoreCase = true)) "Zasedeno" else "Povezava ne deluje",
                        text.contains("zased", ignoreCase = true) || text.contains("busy", ignoreCase = true)
                    )
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

    private fun updateStatus() {
        val name = CompanionConfig.contactName
        refreshContactUi()

        tvStatus.text = when (callState) {
            CompanionCallState.IDLE -> CompanionContactText.idleStatus(name)
            CompanionCallState.RINGING -> if (outgoingDialing) CompanionContactText.callingStatus(name) else CompanionContactText.waitingStatus(name)
            CompanionCallState.ACTIVE -> CompanionContactText.acceptedStatus(name)
            CompanionCallState.BUSY -> CompanionContactText.busyStatus(name)
            CompanionCallState.MISSED -> CompanionContactText.rejectedStatus(name)
        }

        when (callState) {
            CompanionCallState.IDLE -> setConnectionState("Povezava deluje", true)
            CompanionCallState.RINGING -> setConnectionState(if (outgoingDialing) "Klic poslan" else "Ä‚â€žÄąĹˇakam odgovor", true)
            CompanionCallState.ACTIVE -> setConnectionState("Sprejeto", true)
            CompanionCallState.BUSY -> setConnectionState("Zasedeno", false)
            CompanionCallState.MISSED -> setConnectionState("Zavrnjeno", false)
        }

        btnAcceptCall.visibility = if (callState == CompanionCallState.RINGING) View.VISIBLE else View.GONE
        btnRejectCall.visibility = if (callState == CompanionCallState.RINGING || callState == CompanionCallState.ACTIVE) View.VISIBLE else View.GONE
        btnRejectCall.text = if (callState == CompanionCallState.ACTIVE) "PREKINI KLIC" else "ZAVRNI KLIC"
        btnCallContact.visibility = if (callState == CompanionCallState.IDLE) View.VISIBLE else View.GONE
        btnExit.isEnabled = callState != CompanionCallState.ACTIVE
        btnExit.alpha = if (btnExit.isEnabled) 1f else 0.55f
    }

    private fun updateConnectionStateFromMessage(text: String) {
        val normalized = text.lowercase()
        when {
            normalized.contains("povezava ni uspela") || normalized.contains("prekinjena") ->
                setConnectionState("Povezava ne deluje", false)
            normalized.contains("klic poslan") ->
                setConnectionState("Klic poslan", true)
            normalized.contains("Ä‚â€žÄąÂ¤akam") ->
                setConnectionState("Ä‚â€žÄąĹˇakam odgovor", true)
            normalized.contains("sprejela") || normalized.contains("vzpostavljen") || normalized.contains("povezujem") ->
                setConnectionState("Sprejeto", true)
            normalized.contains("zavr") ->
                setConnectionState("Zavrnjeno", false)
            normalized.contains("zased") || normalized.contains("busy") ->
                setConnectionState("Zasedeno", false)
            else -> setConnectionState("Povezava deluje", true)
        }
    }

    private fun setConnectionState(text: String, healthy: Boolean) {
        tvConnectionState.text = text
        tvConnectionState.setTextColor(if (healthy) 0xFFB8D8FF.toInt() else 0xFFFFC27A.toInt())
    }

    private fun showBackOptions() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("NovaRehab Companion")
            .setMessage("Zapri aplikacijo ali pomanjĂ„Ä…Ă‹â€ˇaj?")
            .setPositiveButton("POMANJĂ„Ä…Ă‚Â AJ") { _, _ -> minimizeCompanion() }
            .setNegativeButton("ZAPRI") { _, _ -> closeCompanion() }
            .setNeutralButton("PREKLIÄ‚â€žÄąĹˇI", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    private fun showExitConfirmation() {
        if (callState == CompanionCallState.ACTIVE) {
            Toast.makeText(this, "Najprej zakljuÄ‚â€žÄąÂ¤ite klic.", Toast.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("NovaRehab Companion")
            .setMessage("Ali Ă„Ä…Ă„Äľelite zapreti NovaRehab Companion?")
            .setPositiveButton("ZAPRI") { _, _ -> closeCompanion() }
            .setNegativeButton("PREKLIÄ‚â€žÄąĹˇI", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(0xFF1A1A2E.toInt()))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.setTextColor(Color.WHITE)
        dialog.listView?.apply {
            setBackgroundColor(0xFF1A1A2E.toInt())
            divider = ColorDrawable(0xFF333355.toInt())
            dividerHeight = 1
            post {
                for (index in 0 until childCount) {
                    (getChildAt(index) as? TextView)?.setTextColor(Color.WHITE)
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
    }

    private fun minimizeCompanion() {
        moveTaskToBack(true)
    }

    private fun closeCompanion() {
        finishAffinity()
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
                    Log.e(TAG, "PoĂ„Ä…Ă‹â€ˇiljanje slike ni uspelo", e)
                    runOnUiThread {
                        Toast.makeText(this, e.localizedMessage ?: "PoĂ„Ä…Ă‹â€ˇiljanje slike ni uspelo.", Toast.LENGTH_LONG).show()
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

    override fun onBackPressed() {
        showBackOptions()
    }

    override fun onDestroy() {
        destroyed = true
        statusHandler.removeCallbacksAndMessages(null)
        runCatching { callManager?.endCall() }
            .onFailure { Log.e(TAG, "Varno zapiranje klica ni uspelo", it) }
        callManager = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CompanionMain"
    }
}
