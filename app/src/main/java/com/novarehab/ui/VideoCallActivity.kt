package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.service.RadioService
import com.novarehab.utils.OpenAiTtsManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.Contact
import java.io.File

class VideoCallActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var ttsManager: OpenAiTtsManager
    private var activeContact: Contact? = null

    private lateinit var mainContent: View
    private lateinit var callConfirmOverlay: View
    private lateinit var activeCallOverlay: View
    private lateinit var imgConfirmContact: ImageView
    private lateinit var tvConfirmName: TextView
    private lateinit var tvConfirmLang: TextView
    private lateinit var tvCallingContact: TextView
    private lateinit var gridQuickMessages: GridLayout

    private val quickMessagesSl = listOf(
        "Slišim te"        to R.drawable.comm_dobro,
        "Ne slišim te"     to R.drawable.comm_slabo,
        "Počakaj"          to R.drawable.comm_pocakaj,
        "Hvala"            to R.drawable.comm_hvala,
        "Potrebujem pomoč" to R.drawable.comm_pomoc,
        "Nasvidenje"       to R.drawable.comm_vesela
    )
    private val quickMessagesUk = listOf(
        "Я чую тебе"             to R.drawable.comm_dobro,
        "Не чую тебе"            to R.drawable.comm_slabo,
        "Зачекай"                to R.drawable.comm_pocakaj,
        "Дякую"                  to R.drawable.comm_hvala,
        "Мені потрібна допомога" to R.drawable.comm_pomoc,
        "До побачення"           to R.drawable.comm_vesela
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_call)

        prefs = PrefsManager(this)
        ttsManager = OpenAiTtsManager(this)

        startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_STOP })

        mainContent        = findViewById(R.id.mainContent)
        callConfirmOverlay = findViewById(R.id.callConfirmOverlay)
        activeCallOverlay  = findViewById(R.id.activeCallOverlay)
        imgConfirmContact  = findViewById(R.id.imgConfirmContact)
        tvConfirmName      = findViewById(R.id.tvConfirmName)
        tvConfirmLang      = findViewById(R.id.tvConfirmLang)
        tvCallingContact   = findViewById(R.id.tvCallingContact)
        gridQuickMessages  = findViewById(R.id.gridQuickMessages)

        findViewById<Button>(R.id.btnConfirmCall).setOnClickListener { executeCall() }
        findViewById<Button>(R.id.btnCancelCall).setOnClickListener  { hideAllOverlays() }
        findViewById<Button>(R.id.btnEndCall).setOnClickListener     { endCall() }
        findViewById<Button>(R.id.btnCallBack).setOnClickListener    { finish() }

        loadContacts()
    }

    private fun loadContacts() {
        val grid = findViewById<GridLayout>(R.id.gridContacts)
        val contacts = prefs.getContacts()
        grid.removeAllViews()
        for (i in 0 until 6) {
            val contact = contacts.getOrNull(i)
            val cell = createContactCell(contact, i)
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(6, 6, 6, 6)
            }
            cell.layoutParams = lp
            grid.addView(cell)
        }
    }

    private fun createContactCell(contact: Contact?, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(
                if (contact != null && contact.phone.isNotEmpty()) 0xFF1a3a6b.toInt()
                else 0xFF222244.toInt()
            )
            val imgView = ImageView(this@VideoCallActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ).also { it.setMargins(14, 14, 14, 4) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                loadContactImage(this, index)
            }
            addView(imgView)
            val tvName = TextView(this@VideoCallActivity).apply {
                text = contact?.name ?: "Kontakt ${index + 1}"
                textSize = 15f
                setTextColor(if (contact?.phone?.isNotEmpty() == true) 0xFFFFFFFF.toInt() else 0xFF666688.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(4, 0, 4, 2) }
            }
            addView(tvName)
            val tvLang = TextView(this@VideoCallActivity).apply {
                text = if (contact?.language == "uk") "\uD83C\uDDFA\uD83C\uDDE6" else "\uD83C\uDDF8\uD83C\uDDEE"
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(4, 0, 4, 8) }
            }
            addView(tvLang)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (contact != null && contact.phone.isNotEmpty()) showCallConfirm(contact, index)
                else Toast.makeText(this@VideoCallActivity, "Nastavi številko v Nastavitvah", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCallConfirm(contact: Contact, index: Int) {
        activeContact = contact
        tvConfirmName.text = contact.name
        tvConfirmLang.text = if (contact.language == "uk") "\uD83C\uDDFA\uD83C\uDDE6 Ukrajinščina" else "\uD83C\uDDF8\uD83C\uDDEE Slovenščina"
        loadContactImage(imgConfirmContact, index)
        callConfirmOverlay.visibility = View.VISIBLE
    }

    private fun hideAllOverlays() {
        callConfirmOverlay.visibility = View.GONE
        activeCallOverlay.visibility  = View.GONE
    }

    private fun executeCall() {
        val contact = activeContact ?: return
        callConfirmOverlay.visibility = View.GONE
        showActiveCallOverlay(contact)
        val phone = contact.phone.replace("+", "").replace(" ", "")
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$phone")
                setPackage("com.whatsapp")
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("viber://chat?number=$phone")
                })
            } catch (e2: Exception) {
                startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contact.phone}")
                })
            }
        }
    }

    private fun showActiveCallOverlay(contact: Contact) {
        tvCallingContact.text = "\uD83D\uDCDE  ${contact.name}"
        gridQuickMessages.removeAllViews()
        val messages = if (contact.language == "uk") quickMessagesUk else quickMessagesSl
        messages.forEach { (text, iconRes) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF0f3460.toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(5, 5, 5, 5)
                }
                addView(ImageView(this@VideoCallActivity).apply {
                    setImageResource(iconRes)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                    ).also { it.setMargins(8, 8, 8, 2) }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                })
                addView(TextView(this@VideoCallActivity).apply {
                    this.text = text
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(2, 0, 2, 5) }
                })
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    ttsManager.speak(text, contact.language, prefs.getOpenAiKey(), prefs.getTtsVoice())
                }
            }
            gridQuickMessages.addView(cell)
        }
        activeCallOverlay.visibility = View.VISIBLE
    }

    private fun endCall() {
        ttsManager.stop()
        activeCallOverlay.visibility = View.GONE
    }

    private fun loadContactImage(imageView: ImageView, index: Int) {
        val file = File(getExternalFilesDir(null), "contacts/contact_$index.png")
        if (file.exists()) imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        else { imageView.setImageResource(R.drawable.ic_contact_default); imageView.setColorFilter(0xFFaaaaff.toInt()) }
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
        ttsManager.destroy()
        super.onDestroy()
    }
}
