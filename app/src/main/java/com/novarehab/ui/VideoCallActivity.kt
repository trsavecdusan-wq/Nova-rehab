package com.novarehab.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.novarehab.R
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.utils.ApiConfigManager
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.OpenAiTranslateManager
import com.novarehab.utils.OpenAiTtsManager
import com.novarehab.utils.PrefsManager
import com.novarehab.video.communication.VideoCommunicationManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
class VideoCallActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var paths: NovaRehabPaths
    private lateinit var apiConfig: ApiConfigManager
    private lateinit var ttsManager: OpenAiTtsManager
    private lateinit var translateManager: OpenAiTranslateManager
    private lateinit var videoCommunicationManager: VideoCommunicationManager
    private var videoCallManager: VideoCallManager? = null
    private val httpClient = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private lateinit var contactGridScreen: View
    private lateinit var confirmScreen: View
    private lateinit var callScreen: View

    private lateinit var gridContacts: GridLayout

    private lateinit var imgConfirmContact: ImageView
    private lateinit var tvConfirmName: TextView
    private lateinit var tvConfirmLanguage: TextView

    private lateinit var tvCallName: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var pagerCallCommunication: ViewPager2
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer

    private val callTimeoutHandler = Handler(Looper.getMainLooper())
    private var callTimeoutRunnable: Runnable? = null
    private var selectedContact: VideoContact? = null

    private data class VideoContact(
        val index: Int,
        val id: String,
        val name: String,
        val language: String,
        val roomId: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_video_call)

        prefs = PrefsManager(this)
        paths = NovaRehabPaths(this)
        apiConfig = ApiConfigManager(this)
        ttsManager = OpenAiTtsManager(this)
        translateManager = OpenAiTranslateManager(this)
        videoCommunicationManager = VideoCommunicationManager(this)

        contactGridScreen = findViewById(R.id.contactGridScreen)
        confirmScreen = findViewById(R.id.confirmScreen)
        callScreen = findViewById(R.id.callScreen)

        gridContacts = findViewById(R.id.gridContacts)

        imgConfirmContact = findViewById(R.id.imgConfirmContact)
        tvConfirmName = findViewById(R.id.tvConfirmName)
        tvConfirmLanguage = findViewById(R.id.tvConfirmLanguage)

        tvCallName = findViewById(R.id.tvCallName)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        pagerCallCommunication = findViewById(R.id.gridCallCommunication)
        localRenderer = findViewById(R.id.localRenderer)
        remoteRenderer = findViewById(R.id.remoteRenderer)

        findViewById<Button>(R.id.btnVideoBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnCancelCall).setOnClickListener {
            showContactGrid()
        }

        findViewById<Button>(R.id.btnStartCall).setOnClickListener {
            startCall()
        }

        findViewById<Button>(R.id.btnEndCall).setOnClickListener {
            cancelCallTimeout()
            videoCallManager?.endCall()
        }

        requestVideoPermissions()
        renderContacts()
        showContactGrid()
    }

    private fun renderContacts() {
        gridContacts.removeAllViews()

        val savedContacts = prefs.getContacts()
        val defaultIds = listOf("c01", "c02", "c03", "c04", "c05", "c06")
        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dušan")
        val defaultLanguages = listOf("uk", "uk", "uk", "uk", "uk", "sl")

        for (index in 0 until 6) {
            val saved = savedContacts.getOrNull(index)
            val contactId = defaultIds[index]

            val contact = VideoContact(
                index = index,
                id = contactId,
                name = saved?.name?.trim().orEmpty().ifBlank { defaultNames[index] },
                language = saved?.language?.trim().orEmpty().ifBlank { defaultLanguages[index] },
                roomId = "novarehab_$contactId"
            )

            val cell = createContactCell(contact)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }

            cell.layoutParams = params
            gridContacts.addView(cell)
        }
    }

    private fun createContactCell(contact: VideoContact): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF16213E.toInt())
            isClickable = true
            isFocusable = true

            addView(ImageView(this@VideoCallActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    setMargins(dp(10), dp(8), dp(10), dp(4))
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                loadContactImage(this, contact.index)
            })

            addView(TextView(this@VideoCallActivity).apply {
                text = contact.name
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
                includeFontPadding = false
            })

            addView(TextView(this@VideoCallActivity).apply {
                text = languageFlag(contact.language)
                textSize = 26f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener {
                if (!prefs.isContactOutgoingCallEnabled(contact.index)) {
                    Toast.makeText(
                        this@VideoCallActivity,
                        "Odhodni video klici za ta kontakt so izklopljeni.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                showConfirm(contact)
            }
        }
    }

    private fun showContactGrid() {
        cancelCallTimeout()
        selectedContact = null
        contactGridScreen.visibility = View.VISIBLE
        confirmScreen.visibility = View.GONE
        callScreen.visibility = View.GONE
    }

    private fun showConfirm(contact: VideoContact) {
        selectedContact = contact
        loadContactImage(imgConfirmContact, contact.index)
        tvConfirmName.text = contact.name
        tvConfirmLanguage.text = languageFlag(contact.language)

        contactGridScreen.visibility = View.GONE
        confirmScreen.visibility = View.VISIBLE
        callScreen.visibility = View.GONE
    }

    private fun startCall() {
        val contact = selectedContact ?: return

        if (!hasVideoPermissions()) {
            requestVideoPermissions()
            Toast.makeText(this, "Dovoli kamero in mikrofon za video klic.", Toast.LENGTH_LONG).show()
            return
        }

        tvCallName.text = contact.name
        tvCallStatus.text = "Kličem..."
        renderCallCommunication(contact)

        contactGridScreen.visibility = View.GONE
        confirmScreen.visibility = View.GONE
        callScreen.visibility = View.VISIBLE

        videoCallManager?.endCall()
        videoCallManager = VideoCallManager(
            context = this,
            localRenderer = localRenderer,
            remoteRenderer = remoteRenderer,
            signalingBaseUrl = SIGNALING_BASE_URL,
            listener = object : VideoCallManager.Listener {
                override fun onStatus(text: String) {
                    tvCallStatus.text = text
                    if (text.contains("vzpostavljen", ignoreCase = true)) {
                        cancelCallTimeout()
                    }
                }

                override fun onCallStarted() {
                    callScreen.visibility = View.VISIBLE
                }

                override fun onCallEnded() {
                    cancelCallTimeout()
                    showContactGrid()
                }

                override fun onError(text: String) {
                    cancelCallTimeout()
                    Toast.makeText(this@VideoCallActivity, text, Toast.LENGTH_LONG).show()
                    tvCallStatus.text = text
                }
            }
        )

        startCallTimeout()
        videoCallManager?.startOutgoingCall(contact.roomId)
    }

    private fun renderCallCommunication(contact: VideoContact) {
        val allItems = videoCommunicationManager.buildOverlayItems(
            language = contact.language,
            customIcons = prefs.getCustomCommIcons()
        )

        pagerCallCommunication.adapter = CommPageAdapter(
            context = this,
            items = allItems,
            pageSize = prefs.getCommIconsPerPage(),
            getLang = { contact.language },
            onItemSelected = { item ->
                handleCallCommunicationItem(item, contact)
            }
        )
    }

    private fun handleCallCommunicationItem(item: CommunicationItem, contact: VideoContact) {
        videoCommunicationManager.logSelection(item, contact.language)
        val iconTextManager = IconTextManager(this)
        val savedText = iconTextManager.getText(item.id).ifBlank { item.ttsText }
        val submenuEnabled = prefs.isCommSubmenuEnabled(item.id, false)

        if (submenuEnabled && item.children.isNotEmpty()) {
            val prompt = iconTextManager.getSubmenuPrompt(item.id)
                .ifBlank { item.questionText }
                .ifBlank { item.ttsText }
            speakVideoAnswer(savedText, contact.language) {
                speakVideoAnswer(prompt, contact.language) {
                    showCallCommunicationSubmenu(item, contact)
                }
            }
        } else {
            sendVideoAnswerToCompanion(contact.roomId, savedText)
            speakVideoAnswer(savedText, contact.language)
        }
    }

    private fun showCallCommunicationSubmenu(parent: CommunicationItem, contact: VideoContact) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xF21A1A2E.toInt())
        }

        overlay.addView(TextView(this).apply {
            text = parent.shortLabel.ifBlank { parent.label }
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        val popup = PopupWindow(
            overlay,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            true
        )

        val items = parent.children.take(6)
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        overlay.addView(ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(grid)
        })

        for (slot in 0 until 6) {
            grid.addView(createCallSubmenuCell(items.getOrNull(slot), contact, popup))
        }

        overlay.addView(Button(this).apply {
            text = "NAZAJ"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333355.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
            setOnClickListener { popup.dismiss() }
        })

        val timeout = Runnable { if (popup.isShowing) popup.dismiss() }
        callTimeoutHandler.postDelayed(timeout, prefs.getCommSubmenuTimeoutSeconds() * 1000L)
        popup.setOnDismissListener { callTimeoutHandler.removeCallbacks(timeout) }
        popup.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0)
    }

    private fun createCallSubmenuCell(
        item: CommunicationItem?,
        contact: VideoContact,
        popup: PopupWindow
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF16213E.toInt())
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }

            if (item == null) {
                visibility = View.INVISIBLE
                return@apply
            }

            addView(ImageView(this@VideoCallActivity).apply {
                val customFile = paths.customIconFile(item.id)
                if (customFile.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                } else {
                    setImageResource(item.iconRes)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    setMargins(dp(8), dp(6), dp(8), dp(4))
                }
            })

            addView(TextView(this@VideoCallActivity).apply {
                text = item.shortLabel.ifBlank { item.label }
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
            })

            setOnClickListener {
                isEnabled = false
                animate().scaleX(1.18f).scaleY(1.18f).setDuration(120L).start()
                val iconTextManager = IconTextManager(this@VideoCallActivity)
                val answerText = iconTextManager.getText(item.id).ifBlank { item.ttsText }
                sendVideoAnswerToCompanion(contact.roomId, answerText)
                speakVideoAnswer(answerText, contact.language) {
                    popup.dismiss()
                }
            }
        }
    }

    private fun sendVideoAnswerToCompanion(roomId: String, text: String) {
        if (text.isBlank()) return

        Thread {
            runCatching {
                val body = JSONObject()
                    .put("from", "tablet")
                    .put("speaker", "Lana")
                    .put("text", text)
                    .put("updatedAt", System.currentTimeMillis())
                    .toString()
                    .toRequestBody(jsonType)

                val request = Request.Builder()
                    .url(roomUrl(roomId, "communicationMessage"))
                    .put(body)
                    .build()

                httpClient.newCall(request).execute().close()
            }
        }.start()
    }

    private fun speakVideoAnswer(text: String, targetLanguage: String, onDone: () -> Unit = {}) {
        val token = apiConfig.getApiToken()
        val baseUrl = apiConfig.getApiBaseUrl()
        val voice = prefs.getTtsVoice()

        fun speakFinal(finalText: String) {
            Toast.makeText(this, finalText, Toast.LENGTH_SHORT).show()
            ttsManager.speak(finalText, targetLanguage, token, voice, baseUrl, onDone)
        }

        if (targetLanguage == "sl") {
            speakFinal(text)
            return
        }

        translateManager.translate(text, targetLanguage, token, baseUrl) { translated ->
            speakFinal(translated.ifBlank { text })
        }
    }

    private fun loadContactImage(imageView: ImageView, index: Int) {
        val file = paths.contactImageFile(index)

        if (file.exists()) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            imageView.setImageResource(R.drawable.ic_contact_default)
            imageView.setColorFilter(0xFFB8D8FF.toInt())
        }
    }

    private fun languageFlag(code: String): String {
        return when (code.lowercase()) {
            "uk", "ua" -> "🇺🇦"
            "en" -> "🇬🇧"
            "de" -> "🇩🇪"
            "hr" -> "🇭🇷"
            "sr" -> "🇷🇸"
            else -> "🇸🇮"
        }
    }

    private fun startCallTimeout() {
        cancelCallTimeout()
        callTimeoutRunnable = Runnable {
            Toast.makeText(this, "Klicani ni dosegljiv.", Toast.LENGTH_LONG).show()
            videoCallManager?.endCall()
            showContactGrid()
        }
        callTimeoutHandler.postDelayed(callTimeoutRunnable!!, 45000L)
    }

    private fun cancelCallTimeout() {
        callTimeoutRunnable?.let { callTimeoutHandler.removeCallbacks(it) }
        callTimeoutRunnable = null
    }

    private fun requestVideoPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 501)
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
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onDestroy() {
        cancelCallTimeout()
        videoCallManager?.endCall()
        videoCallManager = null
        ttsManager.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun roomUrl(roomId: String, child: String = ""): String {
        val base = SIGNALING_BASE_URL.trimEnd('/')
        val path = if (child.isBlank()) roomId else "$roomId/$child"
        return "$base/calls/$path.json"
    }

    companion object {
        private const val SIGNALING_BASE_URL =
            "https://novarehab-dfcb9-default-rtdb.europe-west1.firebasedatabase.app"
    }
}
