package com.novarehab.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.utils.PrefsManager
import org.webrtc.SurfaceViewRenderer
import java.io.File

class VideoCallActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var videoCallManager: VideoCallManager? = null

    private lateinit var contactGridScreen: View
    private lateinit var confirmScreen: View
    private lateinit var callScreen: View

    private lateinit var gridContacts: GridLayout

    private lateinit var imgConfirmContact: ImageView
    private lateinit var tvConfirmName: TextView
    private lateinit var tvConfirmLanguage: TextView

    private lateinit var tvCallName: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer

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

        contactGridScreen = findViewById(R.id.contactGridScreen)
        confirmScreen = findViewById(R.id.confirmScreen)
        callScreen = findViewById(R.id.callScreen)

        gridContacts = findViewById(R.id.gridContacts)

        imgConfirmContact = findViewById(R.id.imgConfirmContact)
        tvConfirmName = findViewById(R.id.tvConfirmName)
        tvConfirmLanguage = findViewById(R.id.tvConfirmLanguage)

        tvCallName = findViewById(R.id.tvCallName)
        tvCallStatus = findViewById(R.id.tvCallStatus)
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
            videoCallManager?.endCall()
        }

        requestVideoPermissions()
        renderContacts()
        showContactGrid()
    }

    private fun renderContacts() {
        gridContacts.removeAllViews()

        val savedContacts = prefs.getContacts()
        val defaultIds = listOf("zana", "dedek", "inna", "julija", "kuma", "dusan")
        val defaultNames = listOf("Žana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
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
                text = languageLabel(contact.language)
                textSize = 18f
                setTextColor(0xFFB8D8FF.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener {
                showConfirm(contact)
            }
        }
    }

    private fun showContactGrid() {
        selectedContact = null
        contactGridScreen.visibility = View.VISIBLE
        confirmScreen.visibility = View.GONE
        callScreen.visibility = View.GONE
    }

    private fun showConfirm(contact: VideoContact) {
        selectedContact = contact
        loadContactImage(imgConfirmContact, contact.index)
        tvConfirmName.text = contact.name
        tvConfirmLanguage.text = languageLabel(contact.language)

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
                }

                override fun onCallStarted() {
                    callScreen.visibility = View.VISIBLE
                }

                override fun onCallEnded() {
                    showContactGrid()
                }

                override fun onError(text: String) {
                    Toast.makeText(this@VideoCallActivity, text, Toast.LENGTH_LONG).show()
                    tvCallStatus.text = text
                }
            }
        )
        videoCallManager?.startOutgoingCall(contact.roomId)
    }

    private fun loadContactImage(imageView: ImageView, index: Int) {
        val file = File(getExternalFilesDir(null), "contacts/contact_${index + 1}.png")
        if (file.exists()) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            imageView.setImageResource(R.drawable.ic_contact_default)
            imageView.setColorFilter(0xFFB8D8FF.toInt())
        }
    }

    private fun languageLabel(code: String): String {
        return when (code.lowercase()) {
            "uk", "ua" -> "Ukrajinščina"
            "en" -> "Angleščina"
            "de" -> "Nemščina"
            "hr" -> "Hrvaščina"
            "sr" -> "Srbščina"
            else -> "Slovenščina"
        }
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
        videoCallManager?.endCall()
        videoCallManager = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val SIGNALING_BASE_URL = "https://novarehab-dfcb9-default-rtdb.europe-west1.firebasedatabase.app"

    }
}
