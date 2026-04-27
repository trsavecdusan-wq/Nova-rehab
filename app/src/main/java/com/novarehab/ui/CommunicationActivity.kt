package com.novarehab.ui

import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.utils.PrefsManager
import java.io.File

class CommunicationActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var gridContacts: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_communication)

        prefs = PrefsManager(this)
        gridContacts = findViewById(R.id.gridContacts)

        findViewById<Button>(R.id.btnCommBack).setOnClickListener {
            finish()
        }

        renderContacts()
    }

    private fun renderContacts() {
        gridContacts.removeAllViews()

        val contacts = buildRehabContacts()

        contacts.forEachIndexed { index, contact ->
            val cell = createContactCell(contact, index)
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

    private fun buildRehabContacts(): List<RehabCallContact> {
        val savedContacts = prefs.getContacts()

        return (0 until 6).map { index ->
            val saved = savedContacts.getOrNull(index)
            val id = "contact_${index + 1}"
            val name = saved?.name?.trim().orEmpty().ifBlank { "Kontakt ${index + 1}" }
            val language = saved?.language?.trim().orEmpty().ifBlank { "sl" }
            val roomId = saved?.phone?.trim().orEmpty().ifBlank { "room_$id" }

            RehabCallContact(
                id = id,
                displayName = name,
                imageRes = R.drawable.ic_contact_default,
                preferredLanguageCode = language,
                callRoomId = roomId
            )
        }
    }

    private fun createContactCell(contact: RehabCallContact, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF16213E.toInt())
            isClickable = true
            isFocusable = true

            addView(ImageView(this@CommunicationActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    setMargins(dp(10), dp(8), dp(10), dp(4))
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                loadContactImage(this, index, contact.imageRes)
            })

            addView(TextView(this@CommunicationActivity).apply {
                text = contact.displayName
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

            addView(TextView(this@CommunicationActivity).apply {
                text = languageLabel(contact.preferredLanguageCode)
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
                openVideoCall(contact)
            }
        }
    }

    private fun openVideoCall(contact: RehabCallContact) {
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra(RehabCallExtras.EXTRA_CONTACT_NAME, contact.displayName)
            putExtra(RehabCallExtras.EXTRA_PREFERRED_LANGUAGE_CODE, contact.preferredLanguageCode)
            putExtra(RehabCallExtras.EXTRA_CONTACT_ID, contact.id)
            putExtra(RehabCallExtras.EXTRA_ROOM_ID, contact.callRoomId)
        }
        startActivity(intent)
    }

    private fun loadContactImage(imageView: ImageView, index: Int, fallbackRes: Int) {
        val file = File(getExternalFilesDir(null), "contacts/contact_${index + 1}.png")
        if (file.exists()) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            imageView.setImageResource(fallbackRes)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
