package com.novarehab.ui

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.utils.PrefsManager
import java.io.File

class VideoCallActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var gridContacts: GridLayout
    private lateinit var contactGridScreen: View
    private lateinit var confirmScreen: View
    private lateinit var callScreen: View

    private lateinit var imgConfirmContact: ImageView
    private lateinit var tvConfirmName: TextView
    private lateinit var tvConfirmLanguage: TextView

    private lateinit var imgCallContact: ImageView
    private lateinit var tvCallName: TextView
    private lateinit var tvCallStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var selectedContact: VideoContact? = null

    private data class VideoContact(
        val index: Int,
        val name: String,
        val language: String
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

        imgCallContact = findViewById(R.id.imgCallContact)
        tvCallName = findViewById(R.id.tvCallName)
        tvCallStatus = findViewById(R.id.tvCallStatus)

        findViewById<Button>(R.id.btnVideoBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnCancelCall).setOnClickListener {
            showContactGrid()
        }

        findViewById<Button>(R.id.btnStartCall).setOnClickListener {
            showCallState()
        }

        findViewById<Button>(R.id.btnEndCall).setOnClickListener {
            showContactGrid()
        }

        renderContacts()
        showContactGrid()
    }

    private fun renderContacts() {
        gridContacts.removeAllViews()

        val savedContacts = prefs.getContacts()

        for (index in 0 until 6) {
            val saved = savedContacts.getOrNull(index)
            val contact = VideoContact(
                index = index,
                name = saved?.name?.trim().orEmpty().ifBlank { "Kontakt ${index + 1}" },
                language = saved?.language?.trim().orEmpty().ifBlank { "sl" }
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
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            })

            addView(TextView(this@VideoCallActivity).apply {
                text = languageLabel(contact.language)
                textSize = 18f
                setTextColor(0xFFB8D8FF.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(4), dp(2), dp(4), dp(8))
                }
            })

            setOnClickListener {
                showConfirm(contact)
            }
        }
    }

    private fun showContactGrid() {
        handler.removeCallbacksAndMessages(null)
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

    private fun showCallState() {
        val contact = selectedContact ?: return

        loadContactImage(imgCallContact, contact.index)
        tvCallName.text = contact.name
        tvCallStatus.text = "Kličem..."

        contactGridScreen.visibility = View.GONE
        confirmScreen.visibility = View.GONE
        callScreen.visibility = View.VISIBLE

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (callScreen.visibility == View.VISIBLE) {
                tvCallStatus.text = "Klic vzpostavljen"
            }
        }, 1500L)
    }

    private fun loadContactImage(imageView: ImageView, index: Int) {
        val file = File(getExternalFilesDir(null), "contacts/contact_$index.png")
        if (file.exists()) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            imageView.setImageResource(R.drawable.ic_contact_default)
            imageView.setColorFilter(0xFFB8D8FF.toInt())
        }
    }

    private fun languageLabel(code: String): String {
        return when (code.lowercase()) {
            "uk" -> "Ukrajinščina"
            "en" -> "Angleščina"
            "de" -> "Nemščina"
            "hr" -> "Hrvaščina"
            "sr" -> "Srbščina"
            else -> "Slovenščina"
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
