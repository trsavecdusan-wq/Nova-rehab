package com.novarehab.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.novarehab.R
import com.novarehab.communication.data.CommunicationRepository
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.utils.CustomCommIcon
import com.novarehab.utils.IconTextManager
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.SettingsBackupManager
import java.io.File

class IconSettingsActivity : AppCompatActivity() {

    private var pendingIconId: String? = null
    private val requestImage = 401
    private val requestCamera = 402
    private lateinit var prefs: PrefsManager
    private lateinit var paths: NovaRehabPaths
    private var pendingCameraUri: Uri? = null
    private var pendingCameraTargetId: String? = null

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
        paths = NovaRehabPaths(this)

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
        title.text = "Urejanje ikon"
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
        note.text = "Vnesi besedilo za govor. Podmeni vklopis posebej pri vsaki glavni ikoni."
        note.textSize = 13f
        note.setTextColor(0xFFAAAAAA.toInt())
        note.setPadding(0, 0, 0, dp(12))
        container.addView(note)
        container.addView(createBackupControls())
        addSeparator(container)

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

        applyDarkSettingsStyle(scroll)
        replaceSpinnersWithFullscreenPickers(scroll)
    }

    private fun applyDarkSettingsStyle(view: View) {
        SettingsUiStyler.apply(view, resources.displayMetrics.density)
    }
    private fun createBackupControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }

            addView(Button(this@IconSettingsActivity).apply {
                text = "USTVARI BACKUP"
                textSize = 13f
                setBackgroundColor(0xFF1b5e20.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                setOnClickListener {
                    val iconBackupOk = prefs.getCustomCommIcons().also { prefs.saveCustomCommIcons(it) }
                    val settingsBackupOk = SettingsBackupManager(this@IconSettingsActivity).backupNow()
                    Toast.makeText(
                        this@IconSettingsActivity,
                        if (settingsBackupOk || iconBackupOk.isNotEmpty()) "Varnostna kopija je shranjena."
                        else "Varnostna kopija ni uspela.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

            addView(Button(this@IconSettingsActivity).apply {
                text = "OBNOVI BACKUP"
                textSize = 13f
                setBackgroundColor(0xFF333355.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                setOnClickListener {
                    val restoredIcons =
                        com.novarehab.communication.data.PersonalIconBankManager(this@IconSettingsActivity).restoreBackup()
                    val restoredSettings = SettingsBackupManager(this@IconSettingsActivity).restoreIfAvailable()
                    Toast.makeText(
                        this@IconSettingsActivity,
                        if (restoredIcons || restoredSettings) "Obnovitev je uspela."
                        else "Ni najdene varnostne kopije.",
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }
            })
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
        label.text = "Cas izhoda iz podmenija (sekunde)"
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
        etTimeout.setHintTextColor(0xFFD0D8E8.toInt())
        etTimeout.setBackgroundResource(R.drawable.bg_settings_input)
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
            Toast.makeText(this, "Cas je shranjen", Toast.LENGTH_SHORT).show()
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
        val saved = prefs.getCustomCommIcons().firstOrNull { it.id == id }
        val resolvedChildren = resolvedChildrenFor(id, saved).toMutableList()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                setMargins(0, 0, dp(12), 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val customFile = paths.customIconFile(id)
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

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textBox.addView(createReadonlyInfo("ID", id, "ID je povezava ikone. Ce se spremeni, lahko podmeniji nehajo delovati."))

        val etText = EditText(this).apply {
            setText(mgr.getText(id))
            hint = "Besedilo za govor"
            textSize = 13f
            setSingleLine(false)
            minLines = 1
            maxLines = 3
        }
        styleEditText(etText)
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
            hint = "Vprasanje po kliku, na primer Kaj zelis piti?"
            textSize = 12f
            setSingleLine(false)
            minLines = 1
            maxLines = 2
        }
        styleEditText(etSubmenuPrompt)
        textBox.addView(etSubmenuPrompt)

        val enabledSwitch = Switch(this).apply {
            text = "IKONA AKTIVNA"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = saved?.enabled ?: true
        }
        textBox.addView(enabledSwitch)

        val showOnMainSwitch = Switch(this).apply {
            text = "PRIKAZI NA GLAVNEM ZASLONU"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = saved?.showOnMain ?: defaultMainIds().contains(id)
        }
        textBox.addView(showOnMainSwitch)

        textBox.addView(TextView(this).apply {
            text = "Ce je izklopljeno, ikona ni vidna."
            textSize = 11f
            setTextColor(0xFFD0D8E8.toInt())
        })

        val childrenLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
        textBox.addView(childrenLabel)

        val diagnosticsView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFB8D8FF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
        textBox.addView(diagnosticsView)

        textBox.addView(TextView(this).apply {
            text = "Ikona je vidna samo, ce ima enabled=true in je dodana na glavni zaslon ali kot podikona druge ikone."
            textSize = 11f
            setTextColor(0xFFFFCC80.toInt())
            setPadding(0, 0, 0, dp(6))
        })

        val childButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        childButtons.addView(actionButton("DODAJ PODIKONO") {
            showChildPickerDialog(id, resolvedChildren, remove = false) {
                refreshChildrenLabel(childrenLabel, resolvedChildren)
                refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, defaultRes != 0 || paths.customIconFile(id).exists())
            }
        })
        childButtons.addView(actionButton("ODSTRANI PODIKONO") {
            showChildPickerDialog(id, resolvedChildren, remove = true) {
                refreshChildrenLabel(childrenLabel, resolvedChildren)
                refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, defaultRes != 0 || paths.customIconFile(id).exists())
            }
        })
        textBox.addView(childButtons)

        refreshChildrenLabel(childrenLabel, resolvedChildren)
        refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, defaultRes != 0 || paths.customIconFile(id).exists())
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshDiagnostics(diagnosticsView, id, isChecked, showOnMainSwitch.isChecked, resolvedChildren, defaultRes != 0 || paths.customIconFile(id).exists())
        }
        showOnMainSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, isChecked, resolvedChildren, defaultRes != 0 || paths.customIconFile(id).exists())
        }

        row.addView(textBox)

        val sideButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        }

        val btnSave = Button(this).apply {
            text = "OK"
            textSize = 13f
            setBackgroundColor(0xFF1b5e20.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(52)).apply { setMargins(0, 0, 0, dp(4)) }
            setOnClickListener {
                mgr.setText(id, etText.text.toString().trim())
                mgr.setSubmenuPrompt(id, etSubmenuPrompt.text.toString().trim())
                prefs.saveCommSubmenuEnabled(id, submenuSwitch.isChecked)
                saveIconConfig(
                    id = id,
                    title = saved?.title.orEmpty(),
                    text = saved?.text.orEmpty(),
                    language = saved?.language ?: "sl",
                    imagePath = paths.customIconFile(id).takeIf { it.exists() }?.absolutePath ?: saved?.imagePath.orEmpty(),
                    enabled = enabledSwitch.isChecked,
                    pinnedMain = saved?.pinnedMain ?: false,
                    pinnedVideo = saved?.pinnedVideo ?: false,
                    showOnMain = showOnMainSwitch.isChecked,
                    children = resolvedChildren
                )
                SettingsBackupManager(this@IconSettingsActivity).backupNow()
                Toast.makeText(this@IconSettingsActivity, "Shranjeno", Toast.LENGTH_SHORT).show()
            }
        }
        sideButtons.addView(btnSave)

        val btnReset = Button(this).apply {
            text = "<-"
            textSize = 13f
            setBackgroundColor(0xFF333355.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(52))
            setOnClickListener {
                paths.customIconFile(id).delete()
                img.setImageResource(defaultRes)
                SettingsBackupManager(this@IconSettingsActivity).backupNow()
                Toast.makeText(this@IconSettingsActivity, "Ikona ponastavljena", Toast.LENGTH_SHORT).show()
            }
        }
        sideButtons.addView(btnReset)
        row.addView(sideButtons)

        return row
    }
    private fun createCustomIconRow(index: Int): LinearLayout {
        val id = "custom_$index"
        val saved = prefs.getCustomCommIcons().firstOrNull { it.id == id }
        val resolvedChildren = resolvedChildrenFor(id, saved).toMutableList()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                setMargins(0, 0, dp(12), 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val iconFile = paths.customIconFile(id)
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

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        texts.addView(createReadonlyInfo("ID", id, "ID je povezava ikone. Ce se spremeni, lahko podmeniji nehajo delovati."))

        val etTitle = EditText(this).apply {
            setText(saved?.title ?: "")
            hint = "Napis na ikoni"
            textSize = 12f
        }
        styleEditText(etTitle)

        val etSpeech = EditText(this).apply {
            setText(saved?.text ?: "")
            hint = "Besedilo za govor"
            textSize = 12f
        }
        styleEditText(etSpeech)

        val langSpinner = Spinner(this).apply {
            adapter = themedSpinnerAdapter("SL", "UK")
            styleSpinner(this)
            setSelection(if ((saved?.language ?: "sl") == "uk") 1 else 0)
        }

        val enabledSwitch = Switch(this).apply {
            text = "IKONA AKTIVNA"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = saved?.enabled ?: true
        }

        val showOnMainSwitch = Switch(this).apply {
            text = "PRIKAZI NA GLAVNEM ZASLONU"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = saved?.showOnMain ?: true
        }

        val pinnedVideoSwitch = Switch(this).apply {
            text = "PIN VIDEO"
            textSize = 11f
            setTextColor(0xFFB8D8FF.toInt())
            isChecked = saved?.pinnedVideo ?: false
        }

        texts.addView(etTitle)
        texts.addView(etSpeech)
        texts.addView(langSpinner)
        texts.addView(enabledSwitch)
        texts.addView(showOnMainSwitch)
        texts.addView(pinnedVideoSwitch)
        texts.addView(TextView(this).apply {
            text = "Ce je izklopljeno, ikona ni vidna."
            textSize = 11f
            setTextColor(0xFFD0D8E8.toInt())
        })

        val childrenLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
        texts.addView(childrenLabel)

        val diagnosticsView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFB8D8FF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
        texts.addView(diagnosticsView)

        texts.addView(TextView(this).apply {
            text = "Ikona je vidna samo, ce ima enabled=true in je dodana na glavni zaslon ali kot podikona druge ikone."
            textSize = 11f
            setTextColor(0xFFFFCC80.toInt())
            setPadding(0, 0, 0, dp(6))
        })

        val childButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        childButtons.addView(actionButton("DODAJ PODIKONO") {
            showChildPickerDialog(id, resolvedChildren, remove = false) {
                refreshChildrenLabel(childrenLabel, resolvedChildren)
                refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, paths.customIconFile(id).exists() || saved?.imagePath?.let { File(it).exists() } == true)
            }
        })
        childButtons.addView(actionButton("ODSTRANI PODIKONO") {
            showChildPickerDialog(id, resolvedChildren, remove = true) {
                refreshChildrenLabel(childrenLabel, resolvedChildren)
                refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, paths.customIconFile(id).exists() || saved?.imagePath?.let { File(it).exists() } == true)
            }
        })
        texts.addView(childButtons)

        refreshChildrenLabel(childrenLabel, resolvedChildren)
        refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, showOnMainSwitch.isChecked, resolvedChildren, paths.customIconFile(id).exists() || saved?.imagePath?.let { File(it).exists() } == true)
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshDiagnostics(diagnosticsView, id, isChecked, showOnMainSwitch.isChecked, resolvedChildren, paths.customIconFile(id).exists() || saved?.imagePath?.let { File(it).exists() } == true)
        }
        showOnMainSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshDiagnostics(diagnosticsView, id, enabledSwitch.isChecked, isChecked, resolvedChildren, paths.customIconFile(id).exists() || saved?.imagePath?.let { File(it).exists() } == true)
        }

        row.addView(texts)

        val sideButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        }

        sideButtons.addView(actionButton("GAL") {
            pendingIconId = id
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, requestImage)
        })
        sideButtons.addView(actionButton("ARH") { showArchivePicker(id) })
        sideButtons.addView(actionButton("FOTO") { launchCameraForIcon(id) })

        sideButtons.addView(Button(this).apply {
            text = "OK"
            textSize = 13f
            setBackgroundColor(0xFF1b5e20.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(52)).apply { setMargins(0, 0, 0, dp(4)) }
            setOnClickListener {
                saveIconConfig(
                    id = id,
                    title = etTitle.text.toString().trim(),
                    text = etSpeech.text.toString().trim(),
                    language = if (langSpinner.selectedItemPosition == 1) "uk" else "sl",
                    imagePath = paths.customIconFile(id).takeIf { it.exists() }?.absolutePath.orEmpty(),
                    enabled = enabledSwitch.isChecked,
                    pinnedMain = saved?.pinnedMain ?: false,
                    pinnedVideo = pinnedVideoSwitch.isChecked,
                    showOnMain = showOnMainSwitch.isChecked,
                    children = resolvedChildren
                )
                SettingsBackupManager(this@IconSettingsActivity).backupNow()
                Toast.makeText(this@IconSettingsActivity, "Shranjeno", Toast.LENGTH_SHORT).show()
            }
        })

        sideButtons.addView(Button(this).apply {
            text = "<-"
            textSize = 13f
            setBackgroundColor(0xFF333355.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(52))
            setOnClickListener {
                prefs.saveCustomCommIcons(prefs.getCustomCommIcons().filterNot { it.id == id })
                paths.customIconFile(id).delete()
                SettingsBackupManager(this@IconSettingsActivity).backupNow()
                recreate()
            }
        })
        row.addView(sideButtons)

        return row
    }
    private fun createReadonlyInfo(label: String, value: String, description: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@IconSettingsActivity).apply {
                text = "$label: $value"
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(TextView(this@IconSettingsActivity).apply {
                text = description
                textSize = 11f
                setTextColor(0xFFD0D8E8.toInt())
                setPadding(0, 0, 0, dp(6))
            })
        }
    }

    private fun defaultMainIds(): Set<String> {
        return CommunicationRepository.getMainItems(this, "sl").map { it.id }.toSet()
    }

    private fun defaultChildrenMap(): Map<String, List<String>> {
        return CommunicationRepository.getMainItems(this, "sl")
            .associate { item -> item.id to item.children.map { child -> child.id } }
    }

    private fun resolvedChildrenFor(id: String, saved: CustomCommIcon?): List<String> {
        return when {
            saved != null -> saved.children.distinct()
            else -> defaultChildrenMap()[id].orEmpty().distinct()
        }
    }

    private fun saveIconConfig(
        id: String,
        title: String,
        text: String,
        language: String,
        imagePath: String,
        enabled: Boolean,
        pinnedMain: Boolean,
        pinnedVideo: Boolean,
        showOnMain: Boolean,
        children: List<String>
    ) {
        val current = prefs.getCustomCommIcons().filterNot { it.id == id }.toMutableList()
        current.add(
            CustomCommIcon(
                id = id,
                title = title,
                text = text,
                language = language,
                imagePath = imagePath,
                enabled = enabled,
                pinnedMain = pinnedMain,
                pinnedVideo = pinnedVideo,
                showOnMain = showOnMain,
                children = children.distinct()
            )
        )
        prefs.saveCustomCommIcons(current.sortedBy { it.id })
    }

    private fun refreshChildrenLabel(label: TextView, children: List<String>) {
        label.text = if (children.isEmpty()) {
            "Podikone: ni izbranih"
        } else {
            "Podikone: ${children.joinToString(", ") { displayNameForIcon(it) }}"
        }
    }

    private fun refreshDiagnostics(
        view: TextView,
        id: String,
        enabled: Boolean,
        showOnMain: Boolean,
        currentChildren: List<String>,
        hasValidIcon: Boolean
    ) {
        val hasId = id.isNotBlank()
        val currentMainIds = defaultMainIds().toMutableSet()
        prefs.getCustomCommIcons().forEach { icon ->
            if (icon.showOnMain) currentMainIds += icon.id else currentMainIds.remove(icon.id)
        }
        if (showOnMain) currentMainIds += id else currentMainIds.remove(id)

        val currentChildMap = defaultChildrenMap().toMutableMap()
        prefs.getCustomCommIcons().forEach { icon ->
            currentChildMap[icon.id] = icon.children
        }
        currentChildMap[id] = currentChildren
        val isChild = currentChildMap.any { (parentId, children) -> parentId != id && id in children }

        view.text = listOf(
            "enabled: ${if (enabled) "DA" else "NE"}",
            "ima ID: ${if (hasId) "DA" else "NE"}",
            "je v main seznamu: ${if (id in currentMainIds) "DA" else "NE"}",
            "je podikona: ${if (isChild) "DA" else "NE"}",
            "children: ${if (currentChildren.isEmpty()) "ni" else currentChildren.joinToString(", ")}",
            "image/resource: ${if (hasValidIcon) "OK" else "MANJKA"}"
        ).joinToString("\n")
    }

    private fun showChildPickerDialog(
        parentId: String,
        selectedChildren: MutableList<String>,
        remove: Boolean,
        onChanged: () -> Unit
    ) {
        val candidates = if (remove) {
            selectedChildren.toList()
        } else {
            (allIcons.map { it.first } + (1..18).map { "custom_$it" })
                .distinct()
                .filter { it != parentId && it !in selectedChildren }
        }

        if (candidates.isEmpty()) {
            Toast.makeText(this, if (remove) "Ni podikon za odstraniti." else "Ni vec ikon za dodati.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = candidates.map { "$it - ${displayNameForIcon(it)}" }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (remove) "Odstrani podikono" else "Dodaj podikono")
            .setAdapter(themedSpinnerAdapter(*labels)) { _, which ->
                val chosenId = candidates.getOrNull(which) ?: return@setAdapter
                if (remove) {
                    selectedChildren.remove(chosenId)
                } else {
                    selectedChildren += chosenId
                }
                onChanged()
            }
            .setNegativeButton("Preklici", null)
            .create()

        dialog.show()
        styleAlertDialog(dialog)
    }

    private fun displayNameForIcon(id: String): String {
        val saved = prefs.getCustomCommIcons().firstOrNull { it.id == id }
        if (saved != null) {
            return saved.title.ifBlank { saved.text }.ifBlank { id }
        }
        return allIcons.firstOrNull { it.first == id }?.first ?: id
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestImage && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val id = pendingIconId ?: return

            try {
                saveIconFromUri(id, uri)
                SettingsBackupManager(this).backupNow()
                Toast.makeText(this, "Slika zamenjana", Toast.LENGTH_SHORT).show()
                recreate()
            } catch (_: Exception) {
                Toast.makeText(this, "Napaka pri shranjevanju slike", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (requestCode == requestCamera && resultCode == RESULT_OK) {
            val id = pendingCameraTargetId ?: return
            val uri = pendingCameraUri ?: return
            try {
                saveIconFromUri(id, uri)
                SettingsBackupManager(this).backupNow()
                Toast.makeText(this, "Fotografija je shranjena.", Toast.LENGTH_SHORT).show()
                recreate()
            } catch (_: Exception) {
                Toast.makeText(this, "Napaka pri shranjevanju slike", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setBackgroundColor(0xFF0F3460.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                setMargins(0, 0, dp(4), dp(4))
            }
            minWidth = dp(64)
            setOnClickListener { onClick() }
        }
    }

    private fun showArchivePicker(iconId: String) {
        val files = paths.iconArchiveDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (files.isEmpty()) {
            Toast.makeText(this, "Arhiv ikon je prazen.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = files.map { it.nameWithoutExtension }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Izberi sliko iz arhiva")
            .setAdapter(themedSpinnerAdapter(*labels)) { _, which ->
                val source = files.getOrNull(which) ?: return@setAdapter
                source.copyTo(paths.customIconFile(iconId), overwrite = true)
                SettingsBackupManager(this).backupNow()
                recreate()
            }
            .setNegativeButton("Preklici", null)
            .create()

        dialog.show()
        styleAlertDialog(dialog)
    }

    private fun launchCameraForIcon(iconId: String) {
        val imageFile = paths.archivedIconFile("camera_${iconId}_${System.currentTimeMillis()}")
        imageFile.parentFile?.mkdirs()
        pendingCameraTargetId = iconId
        pendingCameraUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            imageFile
        )

        startActivityForResult(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            requestCamera
        )
    }

    private fun saveIconFromUri(iconId: String, uri: Uri) {
        val target = paths.customIconFile(iconId)
        target.parentFile?.mkdirs()

        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val archiveTarget = paths.archivedIconFile("${iconId}_${System.currentTimeMillis()}")
        archiveTarget.parentFile?.mkdirs()
        target.copyTo(archiveTarget, overwrite = true)
    }

    private fun replaceSpinnersWithFullscreenPickers(root: View) {
        if (root is Spinner) {
            SettingsUiStyler.installFullscreenPickerForSpinner(
                this,
                root,
                derivePickerTitle(root),
                resources.displayMetrics.density
            )
            return
        }

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                replaceSpinnersWithFullscreenPickers(root.getChildAt(index))
            }
        }
    }

    private fun derivePickerTitle(spinner: Spinner): String {
        val parent = spinner.parent as? ViewGroup ?: return "Izberi možnost"
        val spinnerIndex = parent.indexOfChild(spinner)
        for (index in spinnerIndex - 1 downTo 0) {
            val previous = parent.getChildAt(index) as? TextView ?: continue
            val candidate = previous.text?.toString()?.trim().orEmpty()
            if (candidate.isNotBlank()) return candidate.removeSuffix(":")
        }
        return "Izberi možnost"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun styleEditText(editText: EditText) {
        SettingsUiStyler.styleEditText(editText, resources.displayMetrics.density)
    }

    private fun themedSpinnerAdapter(vararg items: String): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).also { styleSpinnerText(it, false) }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).also { styleSpinnerText(it, true) }
            }
        }
    }

    private fun styleSpinnerText(view: View, dropdown: Boolean) {
        val textView = view as? TextView ?: return
        textView.setTextColor(0xFFFFFFFF.toInt())
        if (dropdown) {
            textView.setBackgroundColor(0xFF16213E.toInt())
            textView.setPadding(dp(12), dp(12), dp(12), dp(12))
        } else {
            textView.setBackgroundColor(0x00000000)
            textView.setPadding(dp(8), dp(10), dp(8), dp(10))
        }
    }

    private fun styleSpinner(spinner: Spinner) {
        SettingsUiStyler.styleSpinner(spinner)
    }

    private fun styleAlertDialog(dialog: AlertDialog) {
        SettingsUiStyler.styleDialog(dialog)
    }
}














