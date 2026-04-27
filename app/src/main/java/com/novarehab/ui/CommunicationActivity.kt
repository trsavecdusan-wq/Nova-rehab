package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.service.RadioService
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.OpenAiTranslateManager
import com.novarehab.utils.OpenAiTtsManager
import com.novarehab.utils.PrefsManager
import java.io.File

data class CommItem(
    val id: String,
    val iconRes: Int,
    val labelSl: String,
    val speechSl: String
)

data class CommPage(
    val id: String,
    val titleSl: String,
    val items: MutableList<CommItem>
)

class CommunicationActivity : AppCompatActivity() {

    private lateinit var ttsManager: OpenAiTtsManager
    private lateinit var translateManager: OpenAiTranslateManager
    private lateinit var commPrefs: PrefsManager
    private lateinit var iconTextManager: IconTextManager
    private lateinit var tvLastMessage: TextView
    private lateinit var gridButtons: GridLayout
    private lateinit var tabLayout: LinearLayout

    private var currentPageIndex = 0
    private var activeLang = "sl"

    private val pages: MutableList<CommPage> = mutableListOf(
        CommPage("potrebe", "POTREBE", mutableListOf(
            CommItem("piti", R.drawable.comm_piti, "PITI", "Žejna sem, prosim prinesite mi piti"),
            CommItem("jesti", R.drawable.comm_jesti, "JESTI", "Lačna sem, bi rada jedla"),
            CommItem("kopalnica", R.drawable.comm_kopalnica, "KOPALNICA", "Potrebujem v kopalnico"),
            CommItem("zdravilo", R.drawable.comm_zdravilo, "ZDRAVILO", "Čas je za zdravilo"),
            CommItem("utrujena", R.drawable.comm_utrujena, "UTRUJENA", "Utrujena sem, rada bi počivala"),
            CommItem("pomoc", R.drawable.comm_pomoc, "POMOČ", "Potrebujem pomoč, prosim pridite")
        )),
        CommPage("pocutje", "POČUTJE", mutableListOf(
            CommItem("dobro", R.drawable.comm_dobro, "DOBRO", "Dobro se počutim"),
            CommItem("slabo", R.drawable.comm_slabo, "SLABO", "Ne počutim se dobro"),
            CommItem("bolecina", R.drawable.comm_bolecina, "BOLEČINA", "Imam bolečine"),
            CommItem("mraz", R.drawable.comm_mraz, "MRAZ", "Mrzlica mi je"),
            CommItem("vroce", R.drawable.comm_vroce, "VROČE", "Vroče mi je"),
            CommItem("tesnoba", R.drawable.comm_tesnoba, "TESNOBA", "Tesnobno se počutim")
        )),
        CommPage("prosnje", "PROŠNJE", mutableListOf(
            CommItem("tv", R.drawable.comm_tv, "TELEVIZIJA", "Prosim vklopite televizijo"),
            CommItem("okno", R.drawable.comm_okno, "OKNO", "Prosim odprite okno"),
            CommItem("telefon", R.drawable.comm_telefon, "TELEFON", "Prosim prinesite mi telefon"),
            CommItem("postelja", R.drawable.comm_postelja, "POSTELJA", "Rada bi ležala v postelji"),
            CommItem("pridi_sem", R.drawable.comm_pridi_sem, "PRIDI SEM", "Prosim pridi sem k meni"),
            CommItem("pocakaj", R.drawable.comm_pocakaj, "POČAKAJ", "Počakaj prosim, ne odhajaj")
        )),
        CommPage("custva", "ČUSTVA", mutableListOf(
            CommItem("hvala", R.drawable.comm_hvala, "HVALA", "Hvala lepa"),
            CommItem("vesela", R.drawable.comm_vesela, "VESELA", "Vesela sem"),
            CommItem("zalostna", R.drawable.comm_zalostna, "ŽALOSTNA", "Žalostna sem"),
            CommItem("jezna", R.drawable.comm_jezna, "JEZNA", "Jezna sem"),
            CommItem("strah", R.drawable.comm_strah, "STRAH", "Prestrašena sem"),
            CommItem("objemi", R.drawable.comm_objemi, "OBJEMI", "Bi me objel?")
        ))
    )

    private fun customIconDir(): File = File(getExternalFilesDir(null), "icons")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_communication)

        customIconDir().mkdirs()

        ttsManager = OpenAiTtsManager(this)
        translateManager = OpenAiTranslateManager(this)
        commPrefs = PrefsManager(this)
        iconTextManager = IconTextManager(this)

        tvLastMessage = findViewById(R.id.tvLastMessage)
        gridButtons = findViewById(R.id.gridButtons)
        tabLayout = findViewById(R.id.tabLayout)

        findViewById<Button>(R.id.btnCommBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddPage).setOnClickListener { showAddPageDialog() }

        val btnLang = findViewById<Button>(R.id.btnLangToggle)
        btnLang.text = "SL"
        btnLang.setOnClickListener {
            activeLang = if (activeLang == "sl") commPrefs.getPatientLanguage2() else "sl"
            if (activeLang.isBlank()) activeLang = "sl"
            btnLang.text = activeLang.uppercase()
            renderTabs()
            renderPage(currentPageIndex)
        }

        renderTabs()
        renderPage(0)
    }

    private fun renderTabs() {
        tabLayout.removeAllViews()

        pages.forEachIndexed { index, page ->
            val btn = Button(this).apply {
                text = page.titleSl
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (index == currentPageIndex) 0xFFe94560.toInt() else 0xFF0f3460.toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(4, 4, 4, 4) }
                minWidth = 180
                setOnClickListener {
                    currentPageIndex = index
                    renderTabs()
                    renderPage(index)
                }
            }
            tabLayout.addView(btn)
        }
    }

    private fun renderPage(index: Int) {
        gridButtons.removeAllViews()

        val page = pages.getOrNull(index) ?: return
        val allItems = page.items.toMutableList()

        if (page.id == "prosnje") {
            commPrefs.getCustomCommIcons()
                .filter { it.title.isNotBlank() || it.text.isNotBlank() }
                .forEach { custom ->
                    allItems.add(
                        CommItem(
                            id = custom.id,
                            iconRes = android.R.drawable.ic_menu_gallery,
                            labelSl = custom.title.ifBlank { "DODATNO" },
                            speechSl = custom.text.ifBlank { custom.title }
                        )
                    )
                }
        }

        allItems.forEach { item ->
            val cell = createItemCell(item)
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(6, 6, 6, 6)
            }
            cell.layoutParams = lp
            gridButtons.addView(cell)
        }
    }

    private fun createItemCell(item: CommItem): LinearLayout {
        val visibleText = iconTextManager.getText(item.id).ifBlank { item.speechSl }
        val labelText = item.labelSl

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF16213e.toInt())
            isClickable = true
            isFocusable = true

            val imgView = ImageView(this@CommunicationActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply {
                    weight = 1f
                    setMargins(8, 8, 8, 4)
                }

                val customFile = File(customIconDir(), "${item.id}.png")
                if (customFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(customFile.absolutePath)
                    setImageDrawable(BitmapDrawable(resources, bmp))
                } else {
                    setImageResource(item.iconRes)
                }
            }
            addView(imgView)

            val label = TextView(this@CommunicationActivity).apply {
                text = labelText
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 4, 6) }
            }
            addView(label)

            setOnClickListener {
                speakWithOptionalTranslation(visibleText)
            }

            setOnLongClickListener {
                showItemOptions(item)
                true
            }
        }
    }

    private fun speakWithOptionalTranslation(slovenianText: String) {
        val cleanText = slovenianText.trim()
        if (cleanText.isEmpty()) return

        if (activeLang == "sl") {
            speak(cleanText, "sl")
            tvLastMessage.text = "\"$cleanText\""
            tvLastMessage.visibility = View.VISIBLE
            return
        }

        translateManager.translate(
            text = cleanText,
            targetLanguage = activeLang,
            apiKey = commPrefs.getOpenAiKey()
        ) { translated ->
            val finalText = translated.ifBlank { cleanText }
            speak(finalText, activeLang)
            tvLastMessage.text = "\"$finalText\""
            tvLastMessage.visibility = View.VISIBLE
        }
    }

    private fun speak(text: String, language: String) {
        if (text.isBlank()) return

        startService(Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_DUCK
        })

        ttsManager.speak(
            text = text,
            language = language,
            apiKey = commPrefs.getOpenAiKey(),
            voice = commPrefs.getTtsVoice(),
            onDone = {
                startService(Intent(this, RadioService::class.java).apply {
                    action = RadioService.ACTION_UNDUCK
                })
            }
        )
    }

    private fun showItemOptions(item: CommItem) {
        val options = arrayOf("Zamenjaj sliko iz galerije", "Ponastavi na privzeto ikono")
        android.app.AlertDialog.Builder(this)
            .setTitle(item.labelSl)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageForItem(item)
                    1 -> resetItemIcon(item)
                }
            }
            .show()
    }

    private fun pickImageForItem(item: CommItem) {
        pendingIconItemId = item.id
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    private fun resetItemIcon(item: CommItem) {
        File(customIconDir(), "${item.id}.png").delete()
        renderPage(currentPageIndex)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val itemId = pendingIconItemId ?: return

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val outFile = File(customIconDir(), "$itemId.png")

                inputStream?.use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                renderPage(currentPageIndex)
                Toast.makeText(this, "Slika shranjena", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Napaka pri shranjevanju slike", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddPageDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etName = EditText(this).apply {
            hint = "Ime strani"
            setTextColor(0xFF000000.toInt())
        }
        layout.addView(etName)

        android.app.AlertDialog.Builder(this)
            .setTitle("Dodaj novo stran")
            .setView(layout)
            .setPositiveButton("Dodaj") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newId = name.lowercase().replace(" ", "_")
                    pages.add(CommPage(newId, name.uppercase(), mutableListOf()))
                    currentPageIndex = pages.size - 1
                    renderTabs()
                    renderPage(currentPageIndex)
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
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

    companion object {
        private const val REQUEST_PICK_IMAGE = 201
        private var pendingIconItemId: String? = null
    }
}
