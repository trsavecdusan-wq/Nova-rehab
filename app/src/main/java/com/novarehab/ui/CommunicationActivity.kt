package com.novarehab.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.novarehab.R
import com.novarehab.service.RadioService
import java.io.File
import java.util.Locale

data class CommItem(
    val id: String,           // unikatni ID (tudi ime datoteke za custom sliko)
    val iconRes: Int,         // vgrajena ikona (drawable resource)
    val labelSl: String,      // naziv gumba v slovenščini
    val speechSl: String,     // TTS tekst slovensko
    val speechUk: String      // TTS tekst ukrajinsko
)

data class CommPage(
    val id: String,
    val titleSl: String,
    val titleUk: String,
    val items: MutableList<CommItem>
)

class CommunicationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private lateinit var tvLastMessage: TextView
    private lateinit var gridButtons: GridLayout
    private lateinit var tabContainer: HorizontalScrollView
    private lateinit var tabLayout: LinearLayout

    private var currentPageIndex = 0
    private var activeLang = "sl"  // "sl" ali "uk"

    // Vse strani - razširljivo
    private val pages: MutableList<CommPage> = mutableListOf(
        CommPage("potrebe", "POTREBE", "ПОТРЕБИ", mutableListOf(
            CommItem("piti",      R.drawable.comm_piti,      "PITI",      "Žejna sem, prosim prinesite mi piti",          "Я хочу пити, принесіть мені щось"),
            CommItem("jesti",     R.drawable.comm_jesti,     "JESTI",     "Lačna sem, bi rada jedla",                     "Я голодна, хочу їсти"),
            CommItem("kopalnica", R.drawable.comm_kopalnica, "KOPALNICA", "Potrebujem v kopalnico",                        "Мені потрібно в туалет"),
            CommItem("zdravilo",  R.drawable.comm_zdravilo,  "ZDRAVILO",  "Čas je za zdravilo",                           "Час приймати ліки"),
            CommItem("utrujena",  R.drawable.comm_utrujena,  "UTRUJENA",  "Utrujena sem, rada bi počivala",               "Я втомилась, хочу відпочити"),
            CommItem("pomoc",     R.drawable.comm_pomoc,     "POMOČ",     "Potrebujem pomoč, prosim pridite",             "Мені потрібна допомога, будь ласка, прийдіть")
        )),
        CommPage("pocutje", "POČUTJE", "САМОПОЧУТТЯ", mutableListOf(
            CommItem("dobro",    R.drawable.comm_dobro,    "DOBRO",    "Dobro se počutim",                              "Я почуваюся добре"),
            CommItem("slabo",    R.drawable.comm_slabo,    "SLABO",    "Ne počutim se dobro",                           "Я погано почуваюся"),
            CommItem("bolecina", R.drawable.comm_bolecina, "BOLEČINA", "Imam bolečine",                                 "У мене болить"),
            CommItem("mraz",     R.drawable.comm_mraz,     "MRAZ",     "Mrzlica mi je",                                 "Мені холодно"),
            CommItem("vroce",    R.drawable.comm_vroce,    "VROČE",    "Vroče mi je",                                   "Мені жарко"),
            CommItem("tesnoba",  R.drawable.comm_tesnoba,  "TESNOBA",  "Slabo mi je, tesnobno se počutim",             "Мені тривожно, погано")
        )),
        CommPage("prosnje", "PROŠNJE", "ПРОХАННЯ", mutableListOf(
            CommItem("tv",        R.drawable.comm_tv,        "TELEVIZIJA","Prosim vklopite televizijo",                  "Будь ласка, увімкніть телевізор"),
            CommItem("okno",      R.drawable.comm_okno,      "OKNO",      "Prosim odprite okno",                         "Будь ласка, відкрийте вікно"),
            CommItem("telefon",   R.drawable.comm_telefon,   "TELEFON",   "Prosim prinesite mi telefon",                 "Принесіть мені телефон, будь ласка"),
            CommItem("postelja",  R.drawable.comm_postelja,  "POSTELJA",  "Rada bi ležala v postelji",                   "Я хочу лягти в ліжко"),
            CommItem("pridi_sem", R.drawable.comm_pridi_sem, "PRIDI SEM", "Prosim pridi sem k meni",                    "Будь ласка, підійдіть до мене"),
            CommItem("pocakaj",   R.drawable.comm_pocakaj,   "POČAKAJ",   "Počakaj prosim, ne odhajaj",                 "Зачекай, будь ласка, не йди")
        )),
        CommPage("custva", "ČUSTVA", "ПОЧУТТЯ", mutableListOf(
            CommItem("hvala",    R.drawable.comm_hvala,    "HVALA",    "Hvala lepa",                                    "Дякую вам щиро"),
            CommItem("vesela",   R.drawable.comm_vesela,   "VESELA",   "Vesela sem",                                    "Я рада, мені весело"),
            CommItem("zalostna", R.drawable.comm_zalostna, "ŽALOSTNA", "Žalostna sem",                                  "Мені сумно"),
            CommItem("jezna",    R.drawable.comm_jezna,    "JEZNA",    "Jezna sem",                                     "Я сердита"),
            CommItem("strah",    R.drawable.comm_strah,    "STRAH",    "Prestrašena sem",                               "Мені страшно"),
            CommItem("objemi",   R.drawable.comm_objemi,   "OBJEMI",   "Bi me objel ali objelal?",                      "Обійми мене, будь ласка")
        ))
    )

    // Mapa za custom slike na tablici
    private fun customIconDir(): File =
        File(getExternalFilesDir(null), "icons")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_communication)

        customIconDir().mkdirs()  // ustvari mapo če ne obstaja
        tts = TextToSpeech(this, this)

        tvLastMessage = findViewById(R.id.tvLastMessage)
        gridButtons = findViewById(R.id.gridButtons)
        tabContainer = findViewById(R.id.tabScrollView)
        tabLayout = findViewById(R.id.tabLayout)

        // Gumb nazaj
        findViewById<Button>(R.id.btnCommBack).setOnClickListener { finish() }

        // Gumb za dodajanje strani
        findViewById<Button>(R.id.btnAddPage).setOnClickListener { showAddPageDialog() }

        // Jezik toggle SL/UK
        val btnLang = findViewById<Button>(R.id.btnLangToggle)
        btnLang.setOnClickListener {
            activeLang = if (activeLang == "sl") "uk" else "sl"
            btnLang.text = if (activeLang == "sl") "SL" else "УК"
            updateTtsLanguage()
            renderTabs()
            renderPage(currentPageIndex)
        }

        renderTabs()
        renderPage(0)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateTtsLanguage()
        }
    }

    private fun updateTtsLanguage() {
        val locale = if (activeLang == "sl") Locale("sl", "SI") else Locale("uk", "UA")
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }
        tts?.setSpeechRate(0.85f)
    }

    private fun renderTabs() {
        tabLayout.removeAllViews()
        pages.forEachIndexed { index, page ->
            val btn = Button(this).apply {
                text = if (activeLang == "sl") page.titleSl else page.titleUk
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (index == currentPageIndex) 0xFFe94560.toInt() else 0xFF0f3460.toInt()
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(4, 4, 4, 4) }
                layoutParams = lp
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
        page.items.forEach { item ->
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
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF16213e.toInt())
            isClickable = true
            isFocusable = true

            // Slika
            val imgView = ImageView(this@CommunicationActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = LinearLayout.LayoutParams(0, 0).apply {
                    weight = 1f
                    width = LinearLayout.LayoutParams.MATCH_PARENT
                    setMargins(8, 8, 8, 4)
                }
                layoutParams = lp
                // Preveri custom sliko
                val customFile = File(customIconDir(), "${item.id}.png")
                if (customFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(customFile.absolutePath)
                    setImageDrawable(BitmapDrawable(resources, bmp))
                } else {
                    setImageResource(item.iconRes)
                }
            }
            addView(imgView)

            // Napis
            val label = TextView(this@CommunicationActivity).apply {
                text = item.labelSl
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 4, 6) }
                layoutParams = lp
            }
            addView(label)

            setOnClickListener {
                val speech = if (activeLang == "sl") item.speechSl else item.speechUk
                speak(speech)
                tvLastMessage.text = "\"$speech\""
                tvLastMessage.visibility = View.VISIBLE
            }

            setOnLongClickListener {
                showItemOptions(item)
                true
            }
        }
    }

    private fun speak(text: String) {
        tts?.stop()
        // Utišaj radio med govorom
        startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_DUCK })
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "comm_${System.currentTimeMillis()}")
        // Obnovi radio po govoru (z zamikom)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startService(Intent(this, RadioService::class.java).apply { action = RadioService.ACTION_UNDUCK })
        }, (text.length * 80 + 1500).toLong())
    }

    // Dolg pritisk = opcije za spremembo slike
    private fun showItemOptions(item: CommItem) {
        val options = arrayOf("Zamenjaj sliko (iz galerije)", "Ponastavi na privzeto ikono")
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
        // Shrani trenutni item ID za callback
        pendingIconItemId = item.id
        val intent = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    private fun resetItemIcon(item: CommItem) {
        File(customIconDir(), "${item.id}.png").delete()
        renderPage(currentPageIndex)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
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
            hint = "Ime strani (npr. IGRE)"
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
                    pages.add(CommPage(newId, name.uppercase(), name.uppercase(), mutableListOf()))
                    renderTabs()
                    // Skoči na novo stran
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PICK_IMAGE = 201
        private var pendingIconItemId: String? = null
    }
}
