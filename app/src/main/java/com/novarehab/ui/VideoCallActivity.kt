package com.novarehab.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import com.novarehab.R
import java.util.Locale

class VideoCallActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvContactName: TextView
    private lateinit var tvRoomInfo: TextView
    private lateinit var tvSelectedMessage: TextView
    private lateinit var gridAnswerIcons: GridLayout

    private var contactName: String = "Kontakt"
    private var preferredLanguageCode: String = "sl"
    private var contactId: String = "contact"
    private var roomId: String = "room_contact"

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var fallbackToastShown = false

    private val answers = listOf(
        VideoAnswer("hear_you", R.drawable.comm_dobro, "Slišim te.", "Я тебе чую."),
        VideoAnswer("not_hear_you", R.drawable.comm_slabo, "Ne slišim te.", "Я тебе не чую."),
        VideoAnswer("wait", R.drawable.comm_pocakaj, "Počakaj prosim.", "Зачекай, будь ласка."),
        VideoAnswer("yes", R.drawable.comm_dobro, "Da.", "Так."),
        VideoAnswer("no", R.drawable.comm_slabo, "Ne.", "Ні."),
        VideoAnswer("thanks", R.drawable.comm_hvala, "Hvala.", "Дякую."),
        VideoAnswer("help", R.drawable.comm_pomoc, "Potrebujem pomoč.", "Мені потрібна допомога."),
        VideoAnswer("goodbye", R.drawable.comm_vesela, "Nasvidenje.", "До побачення.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_video_call)

        contactName = intent.getStringExtra(RehabCallExtras.EXTRA_CONTACT_NAME) ?: "Kontakt"
        preferredLanguageCode = intent.getStringExtra(RehabCallExtras.EXTRA_PREFERRED_LANGUAGE_CODE) ?: "sl"
        contactId = intent.getStringExtra(RehabCallExtras.EXTRA_CONTACT_ID) ?: "contact"
        roomId = intent.getStringExtra(RehabCallExtras.EXTRA_ROOM_ID) ?: "room_$contactId"

        tvContactName = findViewById(R.id.tvContactName)
        tvRoomInfo = findViewById(R.id.tvRoomInfo)
        tvSelectedMessage = findViewById(R.id.tvSelectedMessage)
        gridAnswerIcons = findViewById(R.id.gridAnswerIcons)

        tvRoomInfo.visibility = View.GONE

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        tvContactName.text = contactName

        textToSpeech = TextToSpeech(this, this)

        renderAnswerIcons()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            applyTtsLanguage()
        }
    }

    private fun renderAnswerIcons() {
        gridAnswerIcons.removeAllViews()

        answers.forEach { answer ->
            val cell = createAnswerCell(answer)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            cell.layoutParams = params
            gridAnswerIcons.addView(cell)
        }
    }

    private fun createAnswerCell(answer: VideoAnswer): LinearLayout {
        val visibleText = answerText(answer)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF0F3460.toInt())
            isClickable = true
            isFocusable = true

            addView(ImageView(this@VideoCallActivity).apply {
                setImageResource(answer.iconRes)
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
                text = visibleText
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 3
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(4), dp(2), dp(4), dp(6))
                }
            })

            setOnClickListener {
                showAndSpeak(answer)
            }
        }
    }

    private fun showAndSpeak(answer: VideoAnswer) {
        val text = answerText(answer)
        tvSelectedMessage.text = text
        tvSelectedMessage.visibility = View.VISIBLE
        speak(text)
    }

    private fun answerText(answer: VideoAnswer): String {
        return when (preferredLanguageCode.lowercase()) {
            "uk" -> answer.textUk
            else -> answer.textSl
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(this, "Govor še ni pripravljen", Toast.LENGTH_SHORT).show()
            return
        }

        applyTtsLanguage()

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "video_answer_${System.currentTimeMillis()}"
        )
    }

    private fun applyTtsLanguage() {
        val wantedLocale = when (preferredLanguageCode.lowercase()) {
            "uk" -> Locale("uk", "UA")
            "en" -> Locale.ENGLISH
            "de" -> Locale.GERMAN
            "hr" -> Locale("hr", "HR")
            "sr" -> Locale("sr", "RS")
            else -> Locale("sl", "SI")
        }

        val result = textToSpeech?.setLanguage(wantedLocale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (!fallbackToastShown) {
                Toast.makeText(
                    this,
                    "Izbrani glas ni nameščen. Uporabljam slovenski glas.",
                    Toast.LENGTH_LONG
                ).show()
                fallbackToastShown = true
            }
            textToSpeech?.setLanguage(Locale("sl", "SI"))
        }

        textToSpeech?.setSpeechRate(0.9f)
        textToSpeech?.setPitch(1.0f)
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
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
