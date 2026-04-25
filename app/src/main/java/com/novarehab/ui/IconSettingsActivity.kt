package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.CustomCommIcon
import java.io.File

class IconSettingsActivity : AppCompatActivity() {

    private var pendingIconId: String? = null
    private val REQUEST_IMAGE = 401
    private lateinit var prefs: PrefsManager

    private val allIcons = listOf(
        "pomoc" to R.drawable.comm_pomoc,
        "piti" to R.drawable.comm_piti,
        "jesti" to R.drawable.comm_jesti,
        "bolecina" to R.drawable.comm_bolecina,
        "kopalnica" to R.drawable.comm_kopalnica,
        "dobro" to R.drawable.comm_dobro,
        "slabo" to R.drawable.comm_slabo,
        "utrujena" to R.drawable.comm_utrujena,
        "mraz" to R.drawable.comm_mraz,
        "vroce" to R.drawable.comm_vroce,
        "hvala" to R.drawable.comm_hvala,
        "pridi_sem" to R.drawable.comm_pridi_sem,
        "pocakaj" to R.drawable.comm_pocakaj,
        "zdravilo" to R.drawable.comm_zdravilo,
        "telefon" to R.drawable.comm_telefon,
        "tv" to R.drawable.comm_tv,
        "postelja" to R.drawable.comm_postelja,
        "okno" to R.drawable.comm_okno,
        "vesela" to R.drawable.comm_vesela,
        "zalostna" to R.drawable.comm_zalostna,
        "jezna" to R.drawable.comm_jezna,
        "strah" to R.drawable.comm_strah,
        "tesnoba" to R.drawable.comm_tesnoba,
        "objemi" to R.drawable.comm_objemi
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        prefs = PrefsManager(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF1a1a2e.toInt())
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(16, 16, 16, 16)
        scroll.addView(container)
        setContentView(scroll)

        // Glava
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        header.layoutParams = lp

        val title = TextView(this)
        title.text = "Uredi ikone"
        title.textSize = 18f
        title.setTextColor(0xFFe94560.toInt())
        title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val btnBack = Button(this)
        btnBack.text = "NAZAJ"
        btnBack.setBackgroundColor(0xFF333355.toInt())
        btnBack.setTextColor(0xFFFFFFFF.toInt())
        btnBack.setOnClickListener { finish() }

        header.addView(title)
        header.addView(btnBack)
        container.addView(header)

        val iconMgr = IconTextManager(this)

        allIcons.forEach { (id, defaultRes) ->
            container.addView(createIconRow(id, defaultRes, iconMgr))
            addSeparator(container)
        }

        val customTitle = TextView(this).apply {
            text = "Dodatne ikone"
            textSize = 18f
            setTextColor(0xFFe94560.toInt())
            setPadding(0, 20, 0, 8)
        }
        container.addView(customTitle)
        for (i in 1..12) {
            container.addView(createCustomIconRow(i))
            addSeparator(container)
        }
    }

    private fun addSeparator(container: LinearLayout) {
        val sep = android.view.View(this)
        sep.setBackgroundColor(0xFF222244.toInt())
        sep.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 4, 0, 4) }
        container.addView(sep)
    }

    private fun createCustomIconRow(index: Int): LinearLayout {
        val id = "custom_$index"
        val saved = prefs.getCustomCommIcons().firstOrNull { it.id == id }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 6, 0, 6) }

        val img = ImageView(this)
        img.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(0, 0, 12, 0) }
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        val iconFile = File(getExternalFilesDir(null), "icons/$id.png")
        if (iconFile.exists()) img.setImageBitmap(BitmapFactory.decodeFile(iconFile.absolutePath)) else img.setImageResource(android.R.drawable.ic_menu_add)
        img.setOnClickListener {
            pendingIconId = id
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, REQUEST_IMAGE)
        }
        row.addView(img)

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val etTitle = EditText(this)
        etTitle.setText(saved?.title ?: "")
        etTitle.hint = "Napis na ikoni"
        etTitle.textSize = 12f
        etTitle.setTextColor(0xFFFFFFFF.toInt())
        etTitle.setHintTextColor(0xFF666688.toInt())
        etTitle.setBackgroundColor(0xFF16213e.toInt())
        etTitle.setPadding(8, 4, 8, 4)

        val etSpeech = EditText(this)
        etSpeech.setText(saved?.text ?: "")
        etSpeech.hint = "Besedilo za govor; lahko slovensko, aplikacija prevede"
        etSpeech.textSize = 12f
        etSpeech.setTextColor(0xFFFFFFFF.toInt())
        etSpeech.setHintTextColor(0xFF666688.toInt())
        etSpeech.setBackgroundColor(0xFF16213e.toInt())
        etSpeech.setPadding(8, 4, 8, 4)

        texts.addView(etTitle)
        texts.addView(etSpeech)
        row.addView(texts)

        val btnSave = Button(this)
        btnSave.text = "✓"
        btnSave.textSize = 16f
        btnSave.setBackgroundColor(0xFF1b5e20.toInt())
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(8, 0, 0, 0) }
        btnSave.setOnClickListener {
            val list = prefs.getCustomCommIcons().filterNot { it.id == id }.toMutableList()
            val titleText = etTitle.text.toString().trim()
            val speechText = etSpeech.text.toString().trim()
            if (titleText.isNotEmpty() || speechText.isNotEmpty()) list.add(CustomCommIcon(id, titleText, speechText, "sl"))
            prefs.saveCustomCommIcons(list.sortedBy { it.id })
            Toast.makeText(this, "Shranjeno", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnSave)

        val btnReset = Button(this)
        btnReset.text = "↺"
        btnReset.textSize = 16f
        btnReset.setBackgroundColor(0xFF333355.toInt())
        btnReset.setTextColor(0xFFFFFFFF.toInt())
        btnReset.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(4, 0, 0, 0) }
        btnReset.setOnClickListener {
            prefs.saveCustomCommIcons(prefs.getCustomCommIcons().filterNot { it.id == id })
            File(getExternalFilesDir(null), "icons/$id.png").delete()
            recreate()
        }
        row.addView(btnReset)
        return row
    }

    private fun createIconRow(id: String, defaultRes: Int, mgr: IconTextManager): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 6, 0, 6) }

        val img = ImageView(this)
        img.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(0, 0, 12, 0) }
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        val customFile = File(getExternalFilesDir(null), "icons/$id.png")
        if (customFile.exists()) img.setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
        else img.setImageResource(defaultRes)
        img.setOnClickListener {
            pendingIconId = id
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, REQUEST_IMAGE)
        }
        row.addView(img)

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val etSl = EditText(this)
        etSl.setText(mgr.getText(id, "sl"))
        etSl.hint = "Slovensko"
        etSl.textSize = 12f
        etSl.setTextColor(0xFFFFFFFF.toInt())
        etSl.setHintTextColor(0xFF666688.toInt())
        etSl.setBackgroundColor(0xFF16213e.toInt())
        etSl.setPadding(8, 4, 8, 4)
        etSl.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 4) }

        val etUk = EditText(this)
        etUk.setText(mgr.getText(id, "uk"))
        etUk.hint = "Ukrainsko"
        etUk.textSize = 12f
        etUk.setTextColor(0xFFFFFFFF.toInt())
        etUk.setHintTextColor(0xFF666688.toInt())
        etUk.setBackgroundColor(0xFF16213e.toInt())
        etUk.setPadding(8, 4, 8, 4)

        texts.addView(etSl)
        texts.addView(etUk)
        row.addView(texts)

        val btnSave = Button(this)
        btnSave.text = "✓"
        btnSave.textSize = 16f
        btnSave.setBackgroundColor(0xFF1b5e20.toInt())
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(8, 0, 0, 0) }
        btnSave.setOnClickListener {
            mgr.setText(id, "sl", etSl.text.toString())
            mgr.setText(id, "uk", etUk.text.toString())
            Toast.makeText(this, "Shranjeno", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnSave)

        val btnReset = Button(this)
        btnReset.text = "↺"
        btnReset.textSize = 16f
        btnReset.setBackgroundColor(0xFF333355.toInt())
        btnReset.setTextColor(0xFFFFFFFF.toInt())
        btnReset.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(4, 0, 0, 0) }
        btnReset.setOnClickListener {
            File(getExternalFilesDir(null), "icons/$id.png").delete()
            img.setImageResource(defaultRes)
            Toast.makeText(this, "Ikona ponastavljena", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnReset)

        return row
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val id = pendingIconId ?: return
            try {
                val dir = File(getExternalFilesDir(null), "icons")
                dir.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "$id.png").outputStream().use { output -> input.copyTo(output) }
                }
                Toast.makeText(this, "Slika zamenjana", Toast.LENGTH_SHORT).show()
                recreate()
            } catch (e: Exception) {
                Toast.makeText(this, "Napaka", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
