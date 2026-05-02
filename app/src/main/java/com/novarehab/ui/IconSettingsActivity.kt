package com.novarehab.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.R
import com.novarehab.utils.CustomCommIcon
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.SettingsBackupManager
import java.io.File

class IconSettingsActivity : AppCompatActivity() {

    private var pendingIconId: String? = null
    private val requestImage = 401
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
        "voda" to R.drawable.comm_piti,
        "caj" to R.drawable.comm_piti,
        "sok" to R.drawable.comm_piti,
        "zajtrk" to R.drawable.comm_jesti,
        "kosilo" to R.drawable.comm_jesti,
        "prigrizek" to R.drawable.comm_jesti,
        "slabost" to R.drawable.comm_slabo,
        "pomoc_pridi" to R.drawable.comm_pridi_sem,
        "pomoc_dvigni" to R.drawable.comm_pomoc,
        "pomoc_polozaj" to R.drawable.comm_postelja,
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
        container.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(container)
        setContentView(scroll)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56)
        )

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

        val note = TextView(this)
        note.text = "Vpiši besedilo za govor. Podmeni vklopiš posebej pri vsaki glavni ikoni."
        note.textSize = 13f
        note.setTextColor(0xFFAAAAAA.toInt())
        note.setPadding(0, 0, 0, dp(12))
        container.addView(note)

        container.addView(createTimeoutRow())
        addSeparator(container)

        val iconMgr = IconTextManager(this)
        allIcons.forEach { (id, defaultRes) ->
            container.addView(createIconRow(id, defaultRes, iconMgr))
            addSeparator(container)
        }

        val customTitle = TextView(this).apply {
            text = "Dodatne ikone"
            textSize = 18f
            setTextColor(0xFFe94560.toInt())
            setPadding(0, dp(20), 0, dp(8))
        }
        container.addView(customTitle)

        for (i in 1..18) {
            container.addView(createCustomIconRow(i))
            addSeparator(container)
        }
    }

    private fun createTimeoutRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, dp(10)) }

        val label = TextView(this)
        label.text = "Čas izhoda iz podmenija (sekunde)"
        label.textSize = 13f
        label.setTextColor(0xFFFFFFFF.toInt())
        label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(label)

        val etTimeout = EditText(this)
        etTimeout.setText(prefs.getCommSubmenuTimeoutSeconds().toString())
        etTimeout.inputType = InputType.TYPE_CLASS_NUMBER
        etTimeout.textSize = 15f
        etTimeout.gravity = Gravity.CENTER
        etTimeout.setTextColor(0xFFFFFFFF.toInt())
        etTimeout.setHintTextColor(0xFF777799.toInt())
        etTimeout.setBackgroundColor(0xFF16213e.toInt())
        etTimeout.layoutParams = LinearLayout.LayoutParams(dp(88), dp(48)).apply {
            setMargins(dp(8), 0, dp(8), 0)
        }
        row.addView(etTimeout)

        val btnSave = Button(this)
        btnSave.text = "SHRANI"
        btnSave.textSize = 12f
        btnSave.setBackgroundColor(0xFF1b5e20.toInt())
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.layoutParams = LinearLayout.LayoutParams(dp(96), dp(48))
        btnSave.setOnClickListener {
            val seconds = etTimeout.text.toString().toLongOrNull() ?: 15L
            prefs.saveCommSubmenuTimeoutSeconds(seconds)
            etTimeout.setText(prefs.getCommSubmenuTimeoutSeconds().toString())
            SettingsBackupManager(this).backupNow()
            Toast.makeText(this, "Čas shranjen", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnSave)

        return row
    }

    private fun addSeparator(container: LinearLayout) {
        val sep = android.view.View(this)
        sep.setBackgroundColor(0xFF222244.toInt())
        sep.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        ).apply { setMargins(0, dp(4), 0, dp(4)) }
        container.addView(sep)
    }

    private fun createIconRow(id: String, defaultRes: Int, mgr: IconTextManager): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }

        val img = ImageView(this)
        img.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            setMargins(0, 0, dp(12), 0)
        }
        img.scaleType = ImageView.ScaleType.FIT_CENTER

        val customFile = File(getExternalFilesDir(null), "icons/$id.png")
        if (customFile.exists()) {
            img.setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
        } else {
            img.setImageResource(defaultRes)
        }

        img.setOnClickListener {
            pendingIconId = id
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, requestImage)
        }
        row.addView(img)

        val textBox = LinearLayout(this)
        textBox.orientation = LinearLayout.VERTICAL
        textBox.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val etText = EditText(this)
        etText.setText(mgr.getText(id))
        etText.hint = "Besedilo za govor"
        etText.textSize = 13f
        etText.setSingleLine(false)
        etText.minLines = 1
        etText.maxLines = 3
        etText.setTextColor(0xFFFFFFFF.toInt())
        etText.setHintTextColor(0xFF777799.toInt())
        etText.setBackgroundColor(0xFF16213e.toInt())
        etText.setPadding(dp(10), dp(8), dp(10), dp(8))
        textBox.addView(etText)

        val submenuSwitch = Switch(this).apply {
            text = "PODMENI VKLOPLJEN"
            textSize = 12f
            setTextColor(0xFFB8D8FF.toInt())
            isChecked = prefs.isCommSubmenuEnabled(id, false)
        }
        textBox.addView(submenuSwitch)

        val etSubmenuPrompt = EditText(this).apply {
            setText(mgr.getSubmenuPrompt(id))
            hint = "Vprašanje po kliku, npr. Kaj želiš piti?"
            textSize = 12f
            setSingleLine(false)
            minLines = 1
            maxLines = 2
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF777799.toInt())
            setBackgroundColor(0xFF0F3460.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        textBox.addView(etSubmenuPrompt)
        row.addView(textBox)

        val btnSave = Button(this)
        btnSave.text = "OK"
        btnSave.textSize = 13f
        btnSave.setBackgroundColor(0xFF1b5e20.toInt())
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            setMargins(dp(8), 0, 0, 0)
        }
        btnSave.setOnClickListener {
            mgr.setText(id, etText.text.toString().trim())
            mgr.setSubmenuPrompt(id, etSubmenuPrompt.text.toString().trim())
            prefs.saveCommSubmenuEnabled(id, submenuSwitch.isChecked)
            SettingsBackupManager(this).backupNow()
            Toast.makeText(this, "Shranjeno", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnSave)

        val btnReset = Button(this)
        btnReset.text = "<-"
        btnReset.textSize = 13f
        btnReset.setBackgroundColor(0xFF333355.toInt())
        btnReset.setTextColor(0xFFFFFFFF.toInt())
        btnReset.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            setMargins(dp(4), 0, 0, 0)
        }
        btnReset.setOnClickListener {
            File(getExternalFilesDir(null), "icons/$id.png").delete()
            img.setImageResource(defaultRes)
            SettingsBackupManager(this).backupNow()
            Toast.makeText(this, "Ikona ponastavljena", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnReset)

        return row
    }

    private fun createCustomIconRow(index: Int): LinearLayout {
        val id = "custom_$index"
        val saved = prefs.getCustomCommIcons().firstOrNull { it.id == id }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }

        val img = ImageView(this)
        img.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            setMargins(0, 0, dp(12), 0)
        }
        img.scaleType = ImageView.ScaleType.FIT_CENTER

        val iconFile = File(getExternalFilesDir(null), "icons/$id.png")
        if (iconFile.exists()) {
            img.setImageBitmap(BitmapFactory.decodeFile(iconFile.absolutePath))
        } else {
            img.setImageResource(android.R.drawable.ic_menu_add)
        }

        img.setOnClickListener {
            pendingIconId = id
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, requestImage)
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
        etTitle.setHintTextColor(0xFF777799.toInt())
        etTitle.setBackgroundColor(0xFF16213e.toInt())
        etTitle.setPadding(dp(8), dp(4), dp(8), dp(4))

        val etSpeech = EditText(this)
        etSpeech.setText(saved?.text ?: "")
        etSpeech.hint = "Besedilo za govor"
        etSpeech.textSize = 12f
        etSpeech.setTextColor(0xFFFFFFFF.toInt())
        etSpeech.setHintTextColor(0xFF777799.toInt())
        etSpeech.setBackgroundColor(0xFF16213e.toInt())
        etSpeech.setPadding(dp(8), dp(4), dp(8), dp(4))

        texts.addView(etTitle)
        texts.addView(etSpeech)
        row.addView(texts)

        val btnSave = Button(this)
        btnSave.text = "OK"
        btnSave.textSize = 13f
        btnSave.setBackgroundColor(0xFF1b5e20.toInt())
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            setMargins(dp(8), 0, 0, 0)
        }
        btnSave.setOnClickListener {
            val list = prefs.getCustomCommIcons().filterNot { it.id == id }.toMutableList()
            val titleText = etTitle.text.toString().trim()
            val speechText = etSpeech.text.toString().trim()

            if (titleText.isNotEmpty() || speechText.isNotEmpty()) {
                list.add(CustomCommIcon(id, titleText, speechText, "sl"))
            }

            prefs.saveCustomCommIcons(list.sortedBy { it.id })
            SettingsBackupManager(this).backupNow()
            Toast.makeText(this, "Shranjeno", Toast.LENGTH_SHORT).show()
        }
        row.addView(btnSave)

        val btnReset = Button(this)
        btnReset.text = "<-"
        btnReset.textSize = 13f
        btnReset.setBackgroundColor(0xFF333355.toInt())
        btnReset.setTextColor(0xFFFFFFFF.toInt())
        btnReset.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            setMargins(dp(4), 0, 0, 0)
        }
        btnReset.setOnClickListener {
            prefs.saveCustomCommIcons(prefs.getCustomCommIcons().filterNot { it.id == id })
            File(getExternalFilesDir(null), "icons/$id.png").delete()
            SettingsBackupManager(this).backupNow()
            recreate()
        }
        row.addView(btnReset)

        return row
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestImage && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val id = pendingIconId ?: return

            try {
                val dir = File(getExternalFilesDir(null), "icons")
                dir.mkdirs()

                contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "$id.png").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                SettingsBackupManager(this).backupNow()
                Toast.makeText(this, "Slika zamenjana", Toast.LENGTH_SHORT).show()
                recreate()
            } catch (_: Exception) {
                Toast.makeText(this, "Napaka pri shranjevanju slike", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
