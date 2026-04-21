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
import java.io.File

class IconSettingsActivity : AppCompatActivity() {

    private var pendingIconId: String? = null
    private val REQUEST_IMAGE = 401

    // Vse ikone z ID-ji
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

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF1a1a2e.toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(container)
        setContentView(scroll)

        // Glava
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56.dp)
        }
        val title = TextView(this).apply {
            text = "Uredi ikone"
            textSize = 18f
            setTextColor(0xFFe94560.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnBack = Button(this).apply {
            text = "NAZAJ"
            setBackgroundColor(0xFF333355.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { finish() }
        }
        header.addView(title)
        header.addView(btnBack)
        container.addView(header)

        // Seznam ikon
        val iconMgr = IconTextManager(this)
        allIcons.forEach { (id, defaultRes) ->
            val row = createIconRow(id, defaultRes, iconMgr)
            container.addView(row)
            // Separator
            val sep = View(this).apply {
                setBackgroundColor(0xFF222244.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 4, 0, 4) }
            }
            container.addView(sep)
        }
    }

    private fun createIconRow(id: String, defaultRes: Int, mgr: IconTextManager): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }

            // Slika ikone
            val img = ImageView(this@IconSettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(72.dp, 72.dp).apply { setMargins(0, 0, 12, 0) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                val customFile = File(getExternalFilesDir(null), "icons/$id.png")
                if (customFile.exists()) setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                else setImageResource(defaultRes)
                // Klik = zamenjaj sliko
                setOnClickListener {
                    pendingIconId = id
                    startActivityForResult(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                        REQUEST_IMAGE
                    )
                }
            }
            addView(img)

            // Teksti
            val texts = LinearLayout(this@IconSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val etSl = EditText(this@IconSettingsActivity).apply {
                setText(mgr.getText(id, "sl"))
                hint = "Slovensko besedilo"
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666688.toInt())
                setBackgroundColor(0xFF16213e.toInt())
                setPadding(8, 6, 8, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 4) }
            }
            val etUk = EditText(this@IconSettingsActivity).apply {
                setText(mgr.getText(id, "uk"))
                hint = "Ukrajiinsko besedilo"
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666688.toInt())
                setBackgroundColor(0xFF16213e.toInt())
                setPadding(8, 6, 8, 6)
            }
            texts.addView(etSl)
            texts.addView(etUk)
            addView(texts)

            // Gumb shrani
            val btnSave = Button(this@IconSettingsActivity).apply {
                text = "✓"
                textSize = 16f
                setBackgroundColor(0xFF1b5e20.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { setMargins(8, 0, 0, 0) }
                setOnClickListener {
                    mgr.setText(id, "sl", etSl.text.toString())
                    mgr.setText(id, "uk", etUk.text.toString())
                    Toast.makeText(this@IconSettingsActivity, "Shranjeno", Toast.LENGTH_SHORT).show()
                }
            }
            addView(btnSave)

            // Gumb ponastavi sliko
            val btnReset = Button(this@IconSettingsActivity).apply {
                text = "↺"
                textSize = 16f
                setBackgroundColor(0xFF333355.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { setMargins(4, 0, 0, 0) }
                setOnClickListener {
                    File(getExternalFilesDir(null), "icons/$id.png").delete()
                    img.setImageResource(defaultRes)
                    Toast.makeText(this@IconSettingsActivity, "Ikona ponastavljena", Toast.LENGTH_SHORT).show()
                }
            }
            addView(btnReset)
        }
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
                Toast.makeText(this, "Napaka: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
