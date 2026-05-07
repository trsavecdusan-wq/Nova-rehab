package com.novarehab.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.FileProvider
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.databinding.ActivitySettingsBinding
import com.novarehab.service.ReportWorker
import com.novarehab.utils.ApiConfigManager
import com.novarehab.utils.ConfigExportImportManager
import com.novarehab.utils.ConfigImportMode
import com.novarehab.utils.Contact
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import com.novarehab.utils.SpeechCacheManager
import com.novarehab.utils.SettingsBackupManager
import com.novarehab.utils.UpdateManager
import java.io.File

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_API_FILE = 501
        private const val REQUEST_EXPORT_CONFIG_FILE = 502
        private const val REQUEST_IMPORT_CONFIG_FILE = 503
        private const val REQUEST_EXPORT_STATS_FILE = 504
        private const val PREF_SETTINGS_SCROLL_Y = "settings_scroll_y"
        private const val COMPANION_CONFIG_PREFIX = "NOVAREHAB_COMPANION_CONFIG:"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private lateinit var apiConfig: ApiConfigManager
    private lateinit var paths: NovaRehabPaths
    private lateinit var backupManager: SettingsBackupManager
    private lateinit var configTransferManager: ConfigExportImportManager
    private val uiStatePrefs by lazy { getSharedPreferences("nova_ui_state", MODE_PRIVATE) }

    private val contactLangButtons = mutableListOf<Button>()
    private val contactImageButtons = mutableListOf<ImageButton>()
    private val contactIncomingSwitches = mutableListOf<Switch>()
    private val contactOutgoingSwitches = mutableListOf<Switch>()

    private var pendingImageIndex = -1

    private lateinit var spinnerDefaultSpeechLang: Button
    private lateinit var spinnerFallbackSpeechLang: Button
    private lateinit var spinnerSpeechRate: Button
    private lateinit var spinnerSpeechPitch: Button
    private lateinit var spinnerSpeechVolume: Button
    private lateinit var spinnerSpeechProviderMode: Button
    private lateinit var spinnerSpeechResponseMode: Button
    private lateinit var spinnerSpeechStylePreset: Button
    private lateinit var spinnerSpeechResponseFormat: Button
    private lateinit var spinnerPatientLang1: Button
    private lateinit var spinnerPatientLang2: Button
    private lateinit var spinnerCommIconsPerPage: Button
    private lateinit var spinnerCommSubmenuTimeout: Button
    private lateinit var spinnerHardwareVolumeMode: Button
    private lateinit var switchAutoLanguage: Switch
    private lateinit var switchHardwareVolumeControl: Switch
    private lateinit var switchAutoSortIcons: Switch
    private lateinit var switchOpenAiTtsEnabled: Switch
    private lateinit var switchLocalFallbackEnabled: Switch
    private lateinit var switchSpeechRehabilitationMode: Switch
    private lateinit var switchSpeechShortSentenceMode: Switch
    private lateinit var btnCheckUpdateNow: Button
    private lateinit var btnRestorePreviousVersion: Button
    private lateinit var btnShareCompanionApp: Button
    private lateinit var btnExportSettings: Button
    private lateinit var btnImportSettings: Button
    private lateinit var btnExportStats: Button
    private lateinit var btnCreateBackup: Button
    private lateinit var btnRestoreBackupNow: Button
    private lateinit var btnTestHybridTts: Button
    private lateinit var btnClearSpeechCache: Button
    private lateinit var tvConfigTransferInfo: TextView
    private lateinit var tvSpeechDiagnostics: TextView
    private lateinit var tvCommPagingDiagnostics: TextView
    private lateinit var etSpeechModel: EditText
    private lateinit var etSpeechTestPhrase: EditText
    private lateinit var etSpeechPauseWords: EditText
    private lateinit var etSpeechPauseSentences: EditText
    private lateinit var etSpeechClarity: EditText
    private lateinit var etSpeechWarmth: EditText
    private lateinit var etSpeechCalmness: EditText

    private fun companionContacts(): List<CompanionShareContact> {
        val contacts = prefs.getContacts()
        val contactIds = listOf("c01", "c02", "c03", "c04", "c05", "c06")
        val fallbackNames = listOf("Zana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
        val fallbackLanguages = listOf("uk", "uk", "uk", "uk", "uk", "sl")
        val patientName = prefs.getPatientName().ifBlank { "Lana" }

        return (0 until 6).map { index ->
            val savedContact = contacts.getOrNull(index)
            CompanionShareContact(
                contactId = contactIds[index],
                displayName = savedContact?.name?.takeIf { it.isNotBlank() } ?: fallbackNames[index],
                preferredLanguage = savedContact?.language?.takeIf { it.isNotBlank() } ?: fallbackLanguages[index],
                roomId = "novarehab_${contactIds[index]}",
                patientName = patientName
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        apiConfig = ApiConfigManager(this)
        paths = NovaRehabPaths(this)
        backupManager = SettingsBackupManager(this)
        configTransferManager = ConfigExportImportManager(this)

        addLanguageSettingsPanel()
        addSpeechSettingsPanel()
        addUpdateSettingsPanel()
        addCompanionSharePanel()
        addConfigTransferPanel()
        loadSettings()
        styleSettingsUi()
        installSectionNavigation()
        restoreSettingsScrollPosition()
        setupButtons()
    }

    private fun langOptions() = arrayOf(
        "Slovenscina",
        "Ukrajinscina",
        "Anglescina",
        "Nemscina",
        "Hrvascina",
        "Srbscina"
    )

    private fun langCode(position: Int): String = when (position) {
        1 -> "uk"
        2 -> "en"
        3 -> "de"
        4 -> "hr"
        5 -> "sr"
        else -> "sl"
    }

    private fun langIndex(code: String): Int = when (code) {
        "uk" -> 1
        "en" -> 2
        "de" -> 3
        "hr" -> 4
        "sr" -> 5
        else -> 0
    }

    private fun timeoutSeconds(position: Int): Long = when (position) {
        0 -> 8L
        2 -> 15L
        3 -> 20L
        4 -> 30L
        5 -> 60L
        else -> 12L
    }

    private fun timeoutIndex(seconds: Long): Int = when (seconds) {
        8L -> 0
        15L -> 2
        20L -> 3
        30L -> 4
        60L -> 5
        else -> 1
    }

    private fun newLangSpinner(title: String): Button {
        return createPickerButton(title, langOptions())
    }

    private data class PickerState(
        val title: String,
        val items: List<String>,
        var selectedIndex: Int = 0
    )

    private fun createPickerButton(title: String, items: Array<String>): Button {
        return Button(this).apply {
            SettingsUiStyler.stylePickerRow(this, resources.displayMetrics.density)
            isAllCaps = false
            tag = PickerState(title = title, items = items.toList(), selectedIndex = 0)
            updatePickerButtonText(this)
            setOnClickListener {
                showPickerForButton(this)
            }
        }
    }

    private fun showPickerForButton(button: Button) {
        val state = button.tag as? PickerState ?: return
        SettingsUiStyler.showFullscreenPicker(
            this,
            state.title,
            state.items,
            state.selectedIndex
        ) { selectedIndex ->
            state.selectedIndex = selectedIndex
            updatePickerButtonText(button)
        }
    }

    private fun setPickerSelection(button: Button, index: Int) {
        val state = button.tag as? PickerState ?: return
        state.selectedIndex = index.coerceIn(0, state.items.lastIndex.coerceAtLeast(0))
        updatePickerButtonText(button)
    }

    private fun getPickerSelection(button: Button): Int {
        return (button.tag as? PickerState)?.selectedIndex ?: 0
    }

    private fun getPickerValue(button: Button): String {
        val state = button.tag as? PickerState ?: return ""
        return state.items.getOrNull(state.selectedIndex).orEmpty()
    }

    private fun updatePickerButtonText(button: Button) {
        val state = button.tag as? PickerState ?: return
        button.text = state.items.getOrNull(state.selectedIndex).orEmpty().ifBlank { state.title }
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

    private fun themedDialogAdapter(items: Array<String>): ListAdapter {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).also { styleSpinnerText(it, true) }
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

    private fun speechRateOptions() = arrayOf("0.80x", "0.88x", "0.96x", "1.00x", "1.06x")

    private fun speechPitchOptions() = arrayOf("0.8x", "0.9x", "1.0x", "1.1x", "1.2x")

    private fun speechVolumeOptions() = arrayOf("70 %", "80 %", "90 %", "100 %")

    private fun speechProviderOptions() = arrayOf("Samodejno", "OpenAI govor", "Lokalni Android govor")

    private fun speechResponseModeOptions() = arrayOf(
        "Samodejno uravnotezeno",
        "Najprej hiter lokalni govor",
        "OpenAI samo iz predpomnilnika",
        "Vedno najprej OpenAI"
    )

    private fun speechStylePresetOptions() = arrayOf(
        "Rehabilitacijski pomocnik",
        "Miren govor",
        "Topel govor",
        "Pocasen in jasen govor",
        "Topel skrbnik",
        "Zelo preprost govor",
        "Jasna ukrajinscina",
        "Jasna slovenscina"
    )

    private fun speechResponseFormatOptions() = arrayOf("Stisnjen zvok (MP3)", "Visoka kakovost (WAV)")

    private fun hardwareVolumeModeOptions() = arrayOf(
        "Obicajno delovanje Androida",
        "Urejanje glasnosti govora",
        "Ponovi zadnji stavek",
        "Ustavi trenutni govor",
        "Naslednja ali prejsnja stran ikon"
    )

    private fun hardwareVolumeModeValue(position: Int): String = when (position) {
        1 -> "speech_volume_control"
        2 -> "repeat_last_phrase"
        3 -> "stop_current_speech"
        4 -> "next_previous_icon_page"
        else -> "normal_android"
    }

    private fun hardwareVolumeModeIndex(value: String): Int = when (value) {
        "speech_volume_control" -> 1
        "repeat_last_phrase" -> 2
        "stop_current_speech" -> 3
        "next_previous_icon_page" -> 4
        else -> 0
    }

    private fun speechProviderValue(position: Int): String = when (position) {
        1 -> "openai_tts"
        2 -> "local_android_tts"
        else -> "hybrid_auto"
    }

    private fun speechProviderIndex(value: String): Int = when (value) {
        "openai_tts" -> 1
        "local_android_tts" -> 2
        else -> 0
    }

    private fun speechResponseModeValue(position: Int): String = when (position) {
        1 -> "fast_local_first"
        2 -> "openai_if_cached"
        3 -> "openai_preferred"
        else -> "hybrid_auto"
    }

    private fun speechResponseModeIndex(value: String): Int = when (value) {
        "fast_local_first" -> 1
        "openai_if_cached" -> 2
        "openai_preferred" -> 3
        else -> 0
    }

    private fun speechStylePresetValue(position: Int): String = when (position) {
        1 -> "calm"
        2 -> "warm"
        3 -> "slow_clear"
        4 -> "warm_caregiver"
        5 -> "very_simple"
        6 -> "ukrainian_clear"
        7 -> "slovenian_clear"
        else -> "rehabilitation_assistant"
    }

    private fun speechStylePresetIndex(value: String): Int = when (value) {
        "calm" -> 1
        "warm" -> 2
        "slow_clear" -> 3
        "warm_caregiver" -> 4
        "very_simple" -> 5
        "ukrainian_clear" -> 6
        "slovenian_clear" -> 7
        else -> 0
    }

    private fun speechResponseFormatIndex(value: String): Int = when {
        value.contains("wav", ignoreCase = true) -> 1
        else -> 0
    }

    private fun speechRateValue(position: Int): Float = when (position) {
        0 -> 0.80f
        2 -> 0.96f
        3 -> 1.00f
        4 -> 1.06f
        else -> 0.88f
    }

    private fun speechRateIndex(value: Float): Int = when {
        value <= 0.81f -> 0
        value <= 0.89f -> 1
        value <= 0.97f -> 2
        value <= 1.01f -> 3
        else -> 4
    }

    private fun speechPitchValue(position: Int): Float = when (position) {
        0 -> 0.8f
        1 -> 0.9f
        3 -> 1.1f
        4 -> 1.2f
        else -> 1.0f
    }

    private fun speechPitchIndex(value: Float): Int = when {
        value <= 0.85f -> 0
        value <= 0.95f -> 1
        value <= 1.05f -> 2
        value <= 1.15f -> 3
        else -> 4
    }

    private fun speechVolumeValue(position: Int): Float = when (position) {
        0 -> 0.7f
        1 -> 0.8f
        2 -> 0.9f
        else -> 1.0f
    }

    private fun speechVolumeIndex(value: Float): Int = when {
        value <= 0.75f -> 0
        value <= 0.85f -> 1
        value <= 0.95f -> 2
        else -> 3
    }

    private fun addLanguageSettingsPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Pacient"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            text = "Stevilo komunikacijskih ikon na stran:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })

        spinnerCommIconsPerPage = createPickerButton(
            "Stevilo komunikacijskih ikon na stran",
            arrayOf("4 ikone", "9 ikon", "16 ikon", "25 ikon")
        )
        panel.addView(spinnerCommIconsPerPage)

        val autoSortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        autoSortRow.addView(TextView(this).apply {
            text = "Samodejno razvrscanje ikon"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        switchAutoSortIcons = Switch(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
        }
        autoSortRow.addView(switchAutoSortIcons)
        panel.addView(autoSortRow)

        panel.addView(TextView(this).apply {
            text = "Cas izhoda iz podmenija:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })

        spinnerCommSubmenuTimeout = createPickerButton(
            "Cas izhoda iz podmenija",
            arrayOf("8 sekund", "12 sekund", "15 sekund", "20 sekund", "30 sekund", "60 sekund")
        )
        panel.addView(spinnerCommSubmenuTimeout)

        tvCommPagingDiagnostics = TextView(this).apply {
            text = "Diagnostika komunikacijskih strani"
            setTextColor(0xFFB8D8FF.toInt())
            textSize = 12f
            setPadding(0, dp(10), 0, dp(8))
        }
        panel.addView(tvCommPagingDiagnostics)

        switchHardwareVolumeControl = Switch(this).apply {
            text = "Uporabi fizicne tipke za glasnost za komunikacijo"
            setTextColor(0xFFFFFFFF.toInt())
        }
        panel.addView(switchHardwareVolumeControl)

        panel.addView(TextView(this).apply {
            text = "Nacin tipk za glasnost:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerHardwareVolumeMode = createPickerButton(
            "Nacin tipk za glasnost",
            hardwareVolumeModeOptions()
        )
        panel.addView(spinnerHardwareVolumeMode)

        panel.addView(TextView(this).apply {
            text = "Privzeti jezik govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerDefaultSpeechLang = newLangSpinner("Privzeti jezik govora")
        panel.addView(spinnerDefaultSpeechLang)

        panel.addView(TextView(this).apply {
            text = "Jezik, ki ga pacient razume 1:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerPatientLang1 = newLangSpinner("Jezik, ki ga pacient razume 1")
        panel.addView(spinnerPatientLang1)

        panel.addView(TextView(this).apply {
            text = "Jezik, ki ga pacient razume 2:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerPatientLang2 = newLangSpinner("Jezik, ki ga pacient razume 2")
        panel.addView(spinnerPatientLang2)

        val autoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        autoRow.addView(TextView(this).apply {
            text = "Samodejno zaznavanje jezika sogovornika"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        switchAutoLanguage = Switch(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
        }
        autoRow.addView(switchAutoLanguage)
        panel.addView(autoRow)

        rootLayout.addView(panel, 5)
    }

    private fun addSpeechSettingsPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Govor"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            text = "Jezik lokalnega govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerFallbackSpeechLang = newLangSpinner("Rezervni jezik lokalnega govora")
        panel.addView(spinnerFallbackSpeechLang)

        panel.addView(TextView(this).apply {
            text = "Hitrost govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechRate = createPickerButton("Hitrost govora", speechRateOptions())
        panel.addView(spinnerSpeechRate)

        panel.addView(TextView(this).apply {
            text = "Visina glasu:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechPitch = createPickerButton("Visina glasu", speechPitchOptions())
        panel.addView(spinnerSpeechPitch)

        panel.addView(TextView(this).apply {
            text = "Glasnost govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechVolume = createPickerButton("Glasnost govora", speechVolumeOptions())
        panel.addView(spinnerSpeechVolume)

        btnTestHybridTts = Button(this).apply {
            text = "TEST GOVORA"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF7A3E00.toInt())
        }
        panel.addView(btnTestHybridTts)

        val advancedToggle = Button(this).apply {
            text = "ODPRI NAPREDNE NASTAVITVE"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333355.toInt())
        }
        panel.addView(advancedToggle)

        val advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        advancedPanel.addView(TextView(this).apply {
            text = "Ponudnik govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechProviderMode = createPickerButton(
            "Ponudnik govora",
            speechProviderOptions()
        )
        advancedPanel.addView(spinnerSpeechProviderMode)

        advancedPanel.addView(TextView(this).apply {
            text = "Nacin odziva govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechResponseMode = createPickerButton(
            "Nacin odziva govora",
            speechResponseModeOptions()
        )
        advancedPanel.addView(spinnerSpeechResponseMode)

        advancedPanel.addView(TextView(this).apply {
            text = "Slog OpenAI govora:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechStylePreset = createPickerButton(
            "Slog OpenAI govora",
            speechStylePresetOptions()
        )
        advancedPanel.addView(spinnerSpeechStylePreset)

        advancedPanel.addView(TextView(this).apply {
            text = "OpenAI model:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechModel = EditText(this).apply { hint = "Privzeti model" }
        advancedPanel.addView(etSpeechModel)

        advancedPanel.addView(TextView(this).apply {
            text = "Format zvoka:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        spinnerSpeechResponseFormat = createPickerButton(
            "Format zvoka",
            speechResponseFormatOptions()
        )
        advancedPanel.addView(spinnerSpeechResponseFormat)

        switchOpenAiTtsEnabled = Switch(this).apply { text = "OpenAI govor vklopljen" }
        advancedPanel.addView(switchOpenAiTtsEnabled)

        switchLocalFallbackEnabled = Switch(this).apply { text = "Lokalni rezervni govor vklopljen" }
        advancedPanel.addView(switchLocalFallbackEnabled)

        switchSpeechRehabilitationMode = Switch(this).apply { text = "Rehabilitacijski nacin govora" }
        advancedPanel.addView(switchSpeechRehabilitationMode)

        switchSpeechShortSentenceMode = Switch(this).apply { text = "Kratki stavki" }
        advancedPanel.addView(switchSpeechShortSentenceMode)

        advancedPanel.addView(TextView(this).apply {
            text = "Pavza med besedami (ms):"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechPauseWords = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        advancedPanel.addView(etSpeechPauseWords)

        advancedPanel.addView(TextView(this).apply {
            text = "Pavza med stavki (ms):"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechPauseSentences = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        advancedPanel.addView(etSpeechPauseSentences)

        advancedPanel.addView(TextView(this).apply {
            text = "Jasnost izgovorjave (0-100):"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechClarity = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        advancedPanel.addView(etSpeechClarity)

        advancedPanel.addView(TextView(this).apply {
            text = "Toplina govora (0-100):"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechWarmth = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        advancedPanel.addView(etSpeechWarmth)

        advancedPanel.addView(TextView(this).apply {
            text = "Mirnost govora (0-100):"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechCalmness = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        advancedPanel.addView(etSpeechCalmness)

        advancedPanel.addView(TextView(this).apply {
            text = "Testni stavek:"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        etSpeechTestPhrase = EditText(this).apply { setText("Zelim vodo.") }
        advancedPanel.addView(etSpeechTestPhrase)

        btnClearSpeechCache = Button(this).apply {
            text = "POCISTI GOVORNI PREDPOMNILNIK"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A1942.toInt())
        }
        advancedPanel.addView(btnClearSpeechCache)

        tvSpeechDiagnostics = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        advancedPanel.addView(tvSpeechDiagnostics)

        advancedToggle.setOnClickListener {
            advancedPanel.visibility = if (advancedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            advancedToggle.text = if (advancedPanel.visibility == View.VISIBLE) "SKRIJ NAPREDNE NASTAVITVE" else "ODPRI NAPREDNE NASTAVITVE"
        }

        panel.addView(advancedPanel)

        val insertIndex = 6.coerceAtMost(rootLayout.childCount)
        rootLayout.addView(panel, insertIndex)
    }
    private fun addUpdateSettingsPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Posodobitve"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        btnCheckUpdateNow = Button(this).apply {
            text = "PREVERI POSODOBITEV"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
            textSize = 15f
        }
        panel.addView(btnCheckUpdateNow)

        btnRestorePreviousVersion = Button(this).apply {
            text = "OBNOVI PREJSNJO VERZIJO"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0F3460.toInt())
            textSize = 15f
        }
        panel.addView(btnRestorePreviousVersion)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }

    private fun addCompanionSharePanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Video klici in stiki"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        btnShareCompanionApp = Button(this).apply {
            text = "POSLJI NASTAVITEV ZA TELEFON"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A1942.toInt())
            textSize = 14f
        }
        panel.addView(btnShareCompanionApp)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }

    private fun addConfigTransferPanel() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        panel.addView(TextView(this).apply {
            text = "Backup / izvoz / uvoz"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        tvConfigTransferInfo = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        panel.addView(tvConfigTransferInfo)

        btnExportSettings = Button(this).apply {
            text = "IZVOZI NASTAVITVE"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
        }
        panel.addView(btnExportSettings)

        btnImportSettings = Button(this).apply {
            text = "UVOZI NASTAVITVE"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0F3460.toInt())
        }
        panel.addView(btnImportSettings)

        btnExportStats = Button(this).apply {
            text = "IZVOZI STATISTIKO"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A1942.toInt())
        }
        panel.addView(btnExportStats)

        btnCreateBackup = Button(this).apply {
            text = "USTVARI BACKUP"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333355.toInt())
        }
        panel.addView(btnCreateBackup)

        btnRestoreBackupNow = Button(this).apply {
            text = "OBNOVI BACKUP"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF6A2B2B.toInt())
        }
        panel.addView(btnRestoreBackupNow)

        val insertIndex = (rootLayout.childCount - 1).coerceAtLeast(0)
        rootLayout.addView(panel, insertIndex)
    }
    private fun loadSettings() {
        loadRadioSettings()
        loadContactSettings()

        binding.etApiBaseUrl.setText(apiConfig.getApiBaseUrl())
        binding.etOpenAiKey.setText(apiConfig.getApiToken())
        updateApiStatus()

        val voices = arrayOf("marin", "cedar", "nova", "shimmer", "alloy", "echo", "fable", "onyx")
        binding.spinnerTtsVoice.tag = PickerState("Glas", voices.toList(), voices.indexOf(prefs.getTtsVoice()).coerceAtLeast(0))
        SettingsUiStyler.stylePickerRow(binding.spinnerTtsVoice, resources.displayMetrics.density)
        updatePickerButtonText(binding.spinnerTtsVoice)
        binding.spinnerTtsVoice.setOnClickListener { showPickerForButton(binding.spinnerTtsVoice) }

        binding.etGmailUser.setText(prefs.getGmailUser())
        binding.etGmailPass.setText(prefs.getGmailAppPassword())
        binding.etReportMail1.setText(prefs.getReportMail1())
        binding.etReportMail2.setText(prefs.getReportMail2())
        binding.switchMail1.isChecked = prefs.isReportMail1Enabled()
        binding.switchMail2.isChecked = prefs.isReportMail2Enabled()
        binding.etReportHour.setText(prefs.getReportHour().toString())

        binding.switchNavigation.isChecked = prefs.isNavigationEnabled()
        binding.etHomeAddress.setText(prefs.getHomeAddress())
        binding.etPatientName.setText(prefs.getPatientName())

        setPickerSelection(spinnerCommIconsPerPage,
            when (prefs.getCommIconsPerPage()) {
                4 -> 0
                9 -> 1
                16 -> 2
                25 -> 3
                else -> 1
            }
        )
        switchAutoSortIcons.isChecked = prefs.isAutoSortCommunicationIconsEnabled()
        setPickerSelection(spinnerCommSubmenuTimeout, timeoutIndex(prefs.getCommSubmenuTimeoutSeconds()))
        switchHardwareVolumeControl.isChecked = prefs.isHardwareVolumeControlEnabled()
        setPickerSelection(spinnerHardwareVolumeMode, hardwareVolumeModeIndex(prefs.getHardwareVolumeButtonMode()))

        setPickerSelection(spinnerDefaultSpeechLang, langIndex(prefs.getDefaultSpeechLanguage()))
        setPickerSelection(spinnerFallbackSpeechLang, langIndex(prefs.getFallbackSpeechLanguage()))
        setPickerSelection(spinnerSpeechRate, speechRateIndex(prefs.getTtsSpeed()))
        setPickerSelection(spinnerSpeechPitch, speechPitchIndex(prefs.getTtsPitch()))
        setPickerSelection(spinnerSpeechVolume, speechVolumeIndex(prefs.getTtsVolume()))
        setPickerSelection(spinnerSpeechProviderMode, speechProviderIndex(prefs.getSpeechProviderMode()))
        setPickerSelection(spinnerSpeechResponseMode, speechResponseModeIndex(prefs.getSpeechResponseMode()))
        setPickerSelection(spinnerSpeechStylePreset, speechStylePresetIndex(prefs.getSpeechStylePreset()))
        setPickerSelection(spinnerSpeechResponseFormat, speechResponseFormatIndex(prefs.getTtsResponseFormat()))
        switchOpenAiTtsEnabled.isChecked = prefs.isOpenAiTtsEnabled()
        switchLocalFallbackEnabled.isChecked = prefs.isLocalTtsFallbackEnabled()
        switchSpeechRehabilitationMode.isChecked = prefs.isSpeechRehabilitationModeEnabled()
        switchSpeechShortSentenceMode.isChecked = prefs.isSpeechShortSentenceModeEnabled()
        etSpeechModel.setText(prefs.getTtsModel())
        etSpeechPauseWords.setText(prefs.getSpeechPauseBetweenWordsMs().toString())
        etSpeechPauseSentences.setText(prefs.getSpeechPauseBetweenSentencesMs().toString())
        refreshCommPagingDiagnostics()
        etSpeechClarity.setText(prefs.getSpeechPronunciationClarity().toString())
        etSpeechWarmth.setText(prefs.getSpeechEmotionalWarmth().toString())
        etSpeechCalmness.setText(prefs.getSpeechCalmness().toString())
        setPickerSelection(spinnerPatientLang1, langIndex(prefs.getPatientLanguage1()))
        setPickerSelection(spinnerPatientLang2, langIndex(prefs.getPatientLanguage2()))
        switchAutoLanguage.isChecked = prefs.isAutoLanguageEnabled()

        binding.etServerIp.setText(prefs.getServerIp())
        binding.etServerPort.setText(prefs.getServerPort())
        binding.etNewPin.setText("")
        binding.etKioskMinutes.setText(prefs.getKioskReturnMinutes().toString())
        refreshSpeechDiagnostics()
        refreshConfigTransferInfo()
    }

    private fun loadRadioSettings() {
        val stations = prefs.getRadioStations()
        val nameFields = listOf(
            binding.etStation1Name,
            binding.etStation2Name,
            binding.etStation3Name,
            binding.etStation4Name,
            binding.etStation5Name,
            binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url,
            binding.etStation2Url,
            binding.etStation3Url,
            binding.etStation4Url,
            binding.etStation5Url,
            binding.etStation6Url
        )

        stations.forEachIndexed { index, station ->
            if (index < nameFields.size) {
                nameFields[index].setText(station.name)
                urlFields[index].setText(station.url)
            }
        }
    }

    private fun loadContactSettings() {
        val contacts = prefs.getContacts()
        val defaultNames = listOf("Zana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
        val defaultLanguages = listOf("uk", "uk", "uk", "uk", "uk", "sl")

        val nameFields = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneFields = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )
        val languageContainers = listOf(
            binding.langContainer1,
            binding.langContainer2,
            binding.langContainer3,
            binding.langContainer4,
            binding.langContainer5,
            binding.langContainer6
        )
        val imageContainers = listOf(
            binding.imgContainer1,
            binding.imgContainer2,
            binding.imgContainer3,
            binding.imgContainer4,
            binding.imgContainer5,
            binding.imgContainer6
        )

        contactLangButtons.clear()
        contactImageButtons.clear()
        contactIncomingSwitches.clear()
        contactOutgoingSwitches.clear()
        languageContainers.forEach { it.removeAllViews() }
        imageContainers.forEach { it.removeAllViews() }

        for (index in 0 until 6) {
            val contact = contacts.getOrNull(index)
            nameFields[index].setText(contact?.name?.takeIf { it.isNotBlank() } ?: defaultNames[index])
            phoneFields[index].setText(contact?.phone.orEmpty())

            val langButton = createPickerButton("Jezik kontakta", arrayOf("Slovenscina", "Ukrajinscina"))
            val language = contact?.language ?: defaultLanguages[index]
            setPickerSelection(langButton, if (language == "uk") 1 else 0)
            languageContainers[index].addView(langButton)
            contactLangButtons.add(langButton)

            val incomingSwitch = Switch(this).apply {
                text = "Dohodni video klici"
                textSize = 12f
                setTextColor(0xFFB8D8FF.toInt())
                isChecked = prefs.isContactIncomingCallEnabled(index)
            }
            languageContainers[index].addView(incomingSwitch)
            contactIncomingSwitches.add(incomingSwitch)

            val outgoingSwitch = Switch(this).apply {
                text = "Odhodni video klici"
                textSize = 12f
                setTextColor(0xFFB8D8FF.toInt())
                isChecked = prefs.isContactOutgoingCallEnabled(index)
            }
            languageContainers[index].addView(outgoingSwitch)
            contactOutgoingSwitches.add(outgoingSwitch)

            val imageButton = ImageButton(this).apply {
                val file = paths.contactImageFile(index)
                if (file.exists()) {
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                } else {
                    setImageResource(android.R.drawable.ic_menu_camera)
                }

                setBackgroundColor(0xFF333355.toInt())
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(0, 4, 0, 4)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER

                setOnClickListener {
                    pendingImageIndex = index
                    startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, 301)
                }
            }

            imageContainers[index].addView(imageButton)
            contactImageButtons.add(imageButton)
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            finish()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnIconSettings.setOnClickListener {
            startActivity(Intent(this, IconSettingsActivity::class.java))
        }

        btnCheckUpdateNow.setOnClickListener {
            UpdateManager.checkForUpdateNow(this)
        }

        btnRestorePreviousVersion.setOnClickListener {
            UpdateManager.openBackupInstaller(this)
        }

        btnShareCompanionApp.setOnClickListener {
            showCompanionSharePicker()
        }

        btnExportSettings.setOnClickListener {
            showExportSettingsOptions()
        }

        btnImportSettings.setOnClickListener {
            openConfigImportPicker()
        }

        btnExportStats.setOnClickListener {
            openStatisticsExportPicker()
        }

        btnCreateBackup.setOnClickListener {
            val ok = backupManager.backupNow()
            refreshConfigTransferInfo()
            Toast.makeText(
                this,
                if (ok) "Backup je ustvarjen." else "Backup ni uspel.",
                if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }

        btnRestoreBackupNow.setOnClickListener {
            val ok = backupManager.restoreIfAvailable()
            if (ok) {
                loadSettings()
                refreshConfigTransferInfo()
            }
            Toast.makeText(
                this,
                if (ok) "Backup je obnovljen." else "Backup ni na voljo.",
                if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }

        binding.btnChooseApiFile.setOnClickListener {
            openApiFilePicker()
        }

        binding.btnTestLocalTts.setOnClickListener {
            saveSpeechSettings()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speakAndroid(
                etSpeechTestPhrase.text.toString().trim().ifBlank { "Zelim vodo." },
                langCode(getPickerSelection(spinnerDefaultSpeechLang))
            ) {
                tts.destroy()
                refreshSpeechDiagnostics()
            }
            Toast.makeText(this, "Test lokalnega govora.", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestApiTts.setOnClickListener {
            saveApiFields()
            saveSpeechSettings()
            val key = apiConfig.getApiToken()
            val baseUrl = apiConfig.getApiBaseUrl()

            if (!apiConfig.isApiConfigured()) {
                Toast.makeText(this, "API ni nastavljen.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val voice = getPickerValue(binding.spinnerTtsVoice)
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            tts.initLocalTts()
            tts.speakOpenAiOnly(
                etSpeechTestPhrase.text.toString().trim().ifBlank { "Zelim vodo." },
                langCode(getPickerSelection(spinnerDefaultSpeechLang)),
                key,
                voice,
                baseUrl
            ) {
                tts.destroy()
                refreshSpeechDiagnostics()
            }
        }

        btnTestHybridTts.setOnClickListener {
            saveApiFields()
            saveSpeechSettings()
            val tts = com.novarehab.utils.OpenAiTtsManager(this)
            val phrase = etSpeechTestPhrase.text.toString().trim().ifBlank { "Zelim vodo." }
            tts.speak(
                phrase,
                langCode(getPickerSelection(spinnerDefaultSpeechLang)),
                apiConfig.getApiToken(),
                getPickerValue(binding.spinnerTtsVoice),
                apiConfig.getApiBaseUrl()
            ) {
                tts.destroy()
                refreshSpeechDiagnostics()
            }
        }

        btnClearSpeechCache.setOnClickListener {
            val cacheManager = SpeechCacheManager(this)
            cacheManager.clearCache()
            refreshSpeechDiagnostics()
            Toast.makeText(this, "Govorni predpomnilnik je pociscen.", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestApi.setOnClickListener {
            saveApiFields()
            updateApiStatus()
            apiConfig.testApiConnection { success, message ->
                updateApiStatus(if (success) "API shranjen" else message)
                Toast.makeText(this, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }

        binding.btnInstallTts.setOnClickListener {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                Toast.makeText(
                    this,
                    "Ce slovenskega glasu ni, namesti RHVoice iz Trgovine Play.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=RHVoice&c=apps")))
                } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=RHVoice&c=apps")))
                }
            }
        }

        binding.btnTestMail.setOnClickListener {
            saveSettings()
            ReportWorker.schedule(this, prefs.getReportHour())
            Toast.makeText(this, "Porocilo bo poslano ob ${prefs.getReportHour()}:00", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCompanionSharePicker() {
        val contacts = companionContacts()
        val names = contacts.map { it.displayName }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Izberi sogovornika")
            .setAdapter(themedDialogAdapter(names)) { _, which ->
                val contact = contacts.getOrNull(which)
                if (contact == null) {
                    Toast.makeText(this, "Neznan sogovornik.", Toast.LENGTH_LONG).show()
                    return@setAdapter
                }

                shareCompanionApp(contact)
            }
            .setNegativeButton("Preklici", null)
            .create()

        dialog.show()
        styleAlertDialog(dialog)
    }

    private fun shareCompanionApp(contact: CompanionShareContact) {
        val url = buildCompanionApkUrl(contact.contactId)
        if (url == null) {
            Toast.makeText(this, "Neznan kontakt, povezava ni bila ustvarjena.", Toast.LENGTH_LONG).show()
            return
        }

        val configJson = org.json.JSONObject().apply {
            put("contact_id", contact.contactId)
            put("contact_name", contact.displayName)
            put("room_id", contact.roomId)
            put("preferred_language", contact.preferredLanguage)
            put("patient_name", contact.patientName)
        }.toString()

        val message = """
            Namesti aplikacijo NovaRehab Companion in nato uvozi nastavitev.

            1. Klikni povezavo za prenos:
            $url

            2. Po namestitvi v Companion odpri:
            Uvozi nastavitev iz tablice

            3. Prilepi ta zapis:
            ${COMPANION_CONFIG_PREFIX}$configJson
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NovaRehab Companion - ${contact.displayName}")
            putExtra(Intent.EXTRA_TEXT, message)
        }

        startActivity(Intent.createChooser(intent, "Poslji nastavitev"))
    }

    private fun buildCompanionApkUrl(contactId: String): String? {
        val allowedIds = companionContacts().map { it.contactId }.toSet()
        if (contactId !in allowedIds) return null

        return "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/companion-debug.apk"
    }

    private fun saveApiFields() {
        val token = binding.etOpenAiKey.text.toString().trim()
        val baseUrl = binding.etApiBaseUrl.text.toString().trim().ifBlank {
            if (token.isNotBlank()) "https://api.openai.com" else ""
        }

        binding.etApiBaseUrl.setText(baseUrl)
        apiConfig.saveApiBaseUrl(baseUrl)
        apiConfig.saveApiToken(token)

        val message = "API shranjen: baseUrl length=${apiConfig.getApiBaseUrl().length}, token length=${apiConfig.getApiToken().length}"
        Log.d("NovaRehabApi", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateApiStatus()
    }

    private fun styleSettingsUi() {
        SettingsUiStyler.apply(binding.root, resources.displayMetrics.density)
    }

    private fun applyDarkSettingsStyle(view: View) {
        SettingsUiStyler.apply(view, resources.displayMetrics.density)
    }

    private fun styleViewTree(view: View) {
        when (view) {
            is EditText -> styleEditText(view)
            is Switch -> SettingsUiStyler.styleSwitch(view)
            is Button -> SettingsUiStyler.styleButton(view, resources.displayMetrics.density)
            is TextView -> styleTextView(view)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                styleViewTree(view.getChildAt(index))
            }
        }
    }

    private fun styleEditText(editText: EditText) {
        SettingsUiStyler.styleEditText(editText, resources.displayMetrics.density)
    }

    private fun styleTextView(textView: TextView) {
        SettingsUiStyler.styleTextView(textView)
    }

    private fun styleAlertDialog(dialog: AlertDialog) {
        SettingsUiStyler.styleDialog(dialog)
    }

    private fun installSectionNavigation() {
        val rootLayout = (binding.root.getChildAt(0) as? LinearLayout) ?: return
        if (rootLayout.findViewWithTag<View>("settings-section-nav") != null) return

        val nav = LinearLayout(this).apply {
            tag = "settings-section-nav"
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        nav.addView(TextView(this).apply {
            text = "Sekcije nastavitev"
            SettingsUiStyler.styleSectionTitle(this)
        })

        listOf(
            "Pacient" to "Ime pacienta",
            "Komunikator" to "Komunikacijske ikone",
            "Govor" to "Govor",
            "OpenAI API" to "OpenAI API",
            "Radio in glasba" to "Radio in glasba",
            "Video klici in stiki" to "Video klici in stiki",
            "Posodobitve" to "Posodobitve",
            "Backup / izvoz / uvoz" to "Backup / izvoz / uvoz",
            "Admin / PIN" to "Admin / PIN"
        ).forEach { (label, anchor) ->
            nav.addView(Button(this).apply {
                text = label
                SettingsUiStyler.styleButton(this, resources.displayMetrics.density)
                setBackgroundColor(0xFF0F3460.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { scrollToSection(anchor) }
            })
        }

        rootLayout.addView(nav, 1)
    }

    private fun scrollToSection(anchorText: String) {
        val rootLayout = binding.root.getChildAt(0) as? ViewGroup ?: return
        val anchor = findTextViewRecursive(rootLayout, anchorText) ?: return
        binding.root.post {
            binding.root.smoothScrollTo(0, anchor.top.coerceAtLeast(0))
        }
    }

    private fun findTextViewRecursive(group: ViewGroup, text: String): TextView? {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView && child.text?.toString()?.contains(text, ignoreCase = true) == true) {
                return child
            }
            if (child is ViewGroup) {
                val nested = findTextViewRecursive(child, text)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun restoreSettingsScrollPosition() {
        binding.root.post {
            binding.root.scrollTo(0, uiStatePrefs.getInt(PREF_SETTINGS_SCROLL_Y, 0))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun refreshConfigTransferInfo() {
        val exportAt = prefs.getLastConfigExportAt()
        val exportSize = prefs.getLastConfigExportSize()
        val backupAt = paths.settingsBackupFile.takeIf { it.exists() }?.lastModified() ?: 0L
        val importAt = prefs.getLastConfigImportAt()
        tvConfigTransferInfo.text = buildString {
            appendLine("Zadnji izvoz: ${formatDate(exportAt)}")
            appendLine("Velikost izvoza: ${formatSize(exportSize)}")
            appendLine("Zadnji backup: ${formatDate(backupAt)}")
            append("Zadnji uvoz: ${formatDate(importAt)}")
        }
    }

    private fun refreshSpeechDiagnostics() {
        if (!::tvSpeechDiagnostics.isInitialized) return
        tvSpeechDiagnostics.text = buildString {
            appendLine("Povprecni zamik govora: ${prefs.getSpeechAverageDelayMs()} ms")
            appendLine("Zadetki cache: ${prefs.getSpeechCacheHitRatePercent()} %")
            appendLine("Zadnji vir: ${prefs.getLastSpeechSource()}")
            append("Velikost cache: ${formatSize(SpeechCacheManager(this@SettingsActivity).cacheSize())}")
        }
    }

    private fun refreshCommPagingDiagnostics() {
        if (!::tvCommPagingDiagnostics.isInitialized) return

        val language = prefs.getDefaultSpeechLanguage().ifBlank { "sl" }
        val items = com.novarehab.communication.data.CommunicationRepository.getMainItems(
            context = this,
            language = language,
            customIcons = prefs.getCustomCommIcons()
        )
        val pageSize = prefs.getCommIconsPerPage()
        val pageCount = maxOf(1, kotlin.math.ceil(items.size.toDouble() / pageSize).toInt())
        val page1Count = minOf(pageSize, items.size)
        val page2Count = if (items.size > pageSize) minOf(pageSize, items.size - pageSize) else 0
        val customMainCount = prefs.getCustomCommIcons().count { it.enabled && it.showOnMain }

        tvCommPagingDiagnostics.text = buildString {
            appendLine("Diagnostika komunikacijskih strani")
            appendLine("Skupno glavnih ikon: ${items.size}")
            appendLine("Omogocene glavne ikone: ${items.size}")
            appendLine("Velikost strani: $pageSize")
            appendLine("Stevilo strani: $pageCount")
            appendLine("Stran 1: $page1Count")
            appendLine("Stran 2: $page2Count")
            appendLine("Dodatne glavne ikone po meri: $customMainCount")
            appendLine("Ce je glavnih ikon 12 in je stran 9, je druga stran pravilno 3.")
            append("Skrite ikone in razlog: preveri dnevnik NovaRehabPaging (onemogocena, brez ID ali brez slike).")
        }
    }
    private fun showExportSettingsOptions() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Izvozi nastavitve")
            .setItems(arrayOf("Shrani ZIP", "Deli ZIP")) { _, which ->
                if (which == 0) openConfigExportPicker() else shareConfigZip()
            }
            .setNegativeButton("Preklici", null)
            .create()
        dialog.show()
        styleAlertDialog(dialog)
    }

    private fun openConfigExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, configTransferManager.defaultExportName())
        }
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG_FILE)
    }

    private fun openStatisticsExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, configTransferManager.defaultStatisticsName())
        }
        startActivityForResult(intent, REQUEST_EXPORT_STATS_FILE)
    }

    private fun openConfigImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG_FILE)
    }

    private fun shareConfigZip() {
        runCatching {
            val file = configTransferManager.createShareZip()
            val uri = configTransferManager.shareUriFor(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NovaRehab export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Deli NovaRehab export"))
            refreshConfigTransferInfo()
        }.onFailure {
            Toast.makeText(this, it.localizedMessage ?: "Izvoz ni uspel.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportPreview(uri: Uri) {
        runCatching {
            val preview = configTransferManager.inspectImportBundle(uri)
            val summary = buildString {
                appendLine("Stevilo datotek: ${preview.entryCount}")
                appendLine("Ikone: ${if (preview.hasIcons) "DA" else "NE"}")
                appendLine("Kontakti: ${if (preview.hasContacts) "DA" else "NE"}")
                appendLine("Statistika: ${if (preview.hasStatistics) "DA" else "NE"}")
                append("Nastavitve: ${if (preview.hasSettings) "DA" else "NE"}")
            }
            val items = arrayOf("Polni uvoz", "Samo ikone", "Samo kontakti", "Samo statistika")
            val dialog = AlertDialog.Builder(this)
                .setTitle("Uvoz nastavitev")
                .setMessage(summary)
                .setItems(items) { _, which ->
                    val mode = when (which) {
                        1 -> ConfigImportMode.ICONS
                        2 -> ConfigImportMode.CONTACTS
                        3 -> ConfigImportMode.STATISTICS
                        else -> ConfigImportMode.FULL
                    }
                    performImport(uri, mode)
                }
                .setNegativeButton("Preklici", null)
                .create()
            dialog.show()
            styleAlertDialog(dialog)
        }.onFailure {
            Toast.makeText(this, it.localizedMessage ?: "Uvozne datoteke ni bilo mogoce pregledati.", Toast.LENGTH_LONG).show()
        }
    }

    private fun performImport(uri: Uri, mode: ConfigImportMode) {
        configTransferManager.importFromUri(uri, mode)
            .onSuccess {
                refreshConfigTransferInfo()
                loadSettings()
                Toast.makeText(this, "Uvoz je uspel.", Toast.LENGTH_LONG).show()
            }
            .onFailure {
                refreshConfigTransferInfo()
                Toast.makeText(this, it.localizedMessage ?: "Uvoz ni uspel.", Toast.LENGTH_LONG).show()
            }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return "ni podatka"
        return java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    private fun formatSize(size: Long): String {
        if (size <= 0L) return "ni podatka"
        val kb = size / 1024.0
        return if (kb < 1024) String.format(java.util.Locale.US, "%.1f KB", kb)
        else String.format(java.util.Locale.US, "%.2f MB", kb / 1024.0)
    }
    private fun saveSpeechSettings() {
        prefs.saveTtsVoice(getPickerValue(binding.spinnerTtsVoice))
        prefs.saveDefaultSpeechLanguage(langCode(getPickerSelection(spinnerDefaultSpeechLang)))
        prefs.saveFallbackSpeechLanguage(langCode(getPickerSelection(spinnerFallbackSpeechLang)))
        prefs.saveTtsSpeed(speechRateValue(getPickerSelection(spinnerSpeechRate)))
        prefs.saveTtsPitch(speechPitchValue(getPickerSelection(spinnerSpeechPitch)))
        prefs.saveTtsVolume(speechVolumeValue(getPickerSelection(spinnerSpeechVolume)))
        prefs.saveSpeechProviderMode(speechProviderValue(getPickerSelection(spinnerSpeechProviderMode)))
        prefs.saveSpeechResponseMode(speechResponseModeValue(getPickerSelection(spinnerSpeechResponseMode)))
        prefs.saveSpeechStylePreset(speechStylePresetValue(getPickerSelection(spinnerSpeechStylePreset)))
        prefs.saveTtsResponseFormat(if (getPickerSelection(spinnerSpeechResponseFormat) == 1) "wav" else "mp3")
        prefs.saveTtsModel(etSpeechModel.text.toString().trim().ifBlank { "gpt-4o-mini-tts" })
        prefs.saveOpenAiTtsEnabled(switchOpenAiTtsEnabled.isChecked)
        prefs.saveLocalTtsFallbackEnabled(switchLocalFallbackEnabled.isChecked)
        prefs.saveSpeechRehabilitationModeEnabled(switchSpeechRehabilitationMode.isChecked)
        prefs.saveSpeechShortSentenceModeEnabled(switchSpeechShortSentenceMode.isChecked)
        prefs.saveSpeechPauseBetweenWordsMs(etSpeechPauseWords.text.toString().trim().toIntOrNull() ?: 0)
        prefs.saveSpeechPauseBetweenSentencesMs(etSpeechPauseSentences.text.toString().trim().toIntOrNull() ?: 120)
        prefs.saveSpeechPronunciationClarity(etSpeechClarity.text.toString().trim().toIntOrNull() ?: 80)
        prefs.saveSpeechEmotionalWarmth(etSpeechWarmth.text.toString().trim().toIntOrNull() ?: 70)
        prefs.saveSpeechCalmness(etSpeechCalmness.text.toString().trim().toIntOrNull() ?: 80)
    }

    private fun updateApiStatus(forced: String? = null) {
        binding.tvApiStatus.text = forced ?: apiConfig.getStatusText()
    }

    private fun openApiFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/json", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_API_FILE)
    }

    private fun importApiFile(uri: Uri) {
        val raw = try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        } catch (_: Exception) {
            Toast.makeText(this, "API datoteke ni bilo mogoce prebrati.", Toast.LENGTH_LONG).show()
            return
        }

        val parsed = parseApiFile(raw)
        if (parsed.baseUrl.isBlank() && parsed.token.isBlank()) {
            Toast.makeText(this, "API datoteka ne vsebuje prepoznavnega URL-ja ali tokena.", Toast.LENGTH_LONG).show()
            return
        }

        val baseUrl = parsed.baseUrl.ifBlank {
            if (parsed.token.isNotBlank()) "https://api.openai.com" else ""
        }

        if (baseUrl.isNotBlank()) {
            binding.etApiBaseUrl.setText(baseUrl)
            apiConfig.saveApiBaseUrl(baseUrl)
        }

        if (parsed.token.isNotBlank()) {
            binding.etOpenAiKey.setText(parsed.token)
            apiConfig.saveApiToken(parsed.token)
        }

        Toast.makeText(this, "API podatki so shranjeni.", Toast.LENGTH_SHORT).show()
        updateApiStatus()
    }

    private fun parseApiFile(raw: String): ImportedApiConfig {
        val lines = raw
            .replace("\uFEFF", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim().trim(',', '"', '\'') }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        val baseUrl = lines.firstNotNullOfOrNull { line ->
            val value = valueAfterSeparator(line)
            when {
                line.contains("base", ignoreCase = true) && value.startsWith("http", ignoreCase = true) -> value
                line.startsWith("http", ignoreCase = true) -> line
                else -> null
            }
        }.orEmpty()

        val token = lines.firstNotNullOfOrNull { line ->
            val value = valueAfterSeparator(line)
            when {
                line.contains("token", ignoreCase = true) -> value
                line.contains("key", ignoreCase = true) -> value
                line.startsWith("Bearer ", ignoreCase = true) -> line.removePrefix("Bearer ").trim()
                !line.startsWith("http", ignoreCase = true) && line.length >= 12 -> line
                else -> null
            }
        }.orEmpty()

        return ImportedApiConfig(baseUrl, token)
    }

    private fun valueAfterSeparator(line: String): String {
        val cleaned = line.trim().trim(',', '"', '\'')

        val eqIndex = cleaned.indexOf('=')
        if (eqIndex >= 0) {
            return cleaned.substring(eqIndex + 1)
                .trim()
                .trim(',', '"', '\'')
                .removePrefix("Bearer ")
                .trim()
        }

        val colonIndex = cleaned.indexOf(':')
        if (colonIndex >= 0 && !cleaned.startsWith("http", ignoreCase = true)) {
            return cleaned.substring(colonIndex + 1)
                .trim()
                .trim(',', '"', '\'')
                .removePrefix("Bearer ")
                .trim()
        }

        return cleaned
            .trim()
            .trim(',', '"', '\'')
            .removePrefix("Bearer ")
            .trim()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_API_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importApiFile(uri)
            return
        }

        if (requestCode == REQUEST_EXPORT_CONFIG_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            configTransferManager.exportToUri(uri)
                .onSuccess {
                    refreshConfigTransferInfo()
                    Toast.makeText(
                        this,
                        "Izvoz nastavitev je koncan (${formatSize(it)}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    Toast.makeText(this, it.localizedMessage ?: "Izvoz ni uspel.", Toast.LENGTH_LONG).show()
                }
            return
        }

        if (requestCode == REQUEST_EXPORT_STATS_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            configTransferManager.exportStatisticsToUri(uri)
                .onSuccess {
                    Toast.makeText(
                        this,
                        "Izvoz statistike je koncan (${formatSize(it)}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    Toast.makeText(this, it.localizedMessage ?: "Izvoz statistike ni uspel.", Toast.LENGTH_LONG).show()
                }
            return
        }

        if (requestCode == REQUEST_IMPORT_CONFIG_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            showImportPreview(uri)
            return
        }

        if (requestCode == 301 && resultCode == RESULT_OK && pendingImageIndex >= 0) {
            val uri = data?.data ?: return

            try {
                val dir = paths.contactsDir
                dir.mkdirs()

                contentResolver.openInputStream(uri)?.use { input ->
                    paths.contactImageFile(pendingImageIndex).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val bitmap = BitmapFactory.decodeFile(paths.contactImageFile(pendingImageIndex).absolutePath)
                contactImageButtons.getOrNull(pendingImageIndex)?.setImageBitmap(bitmap)
                SettingsBackupManager(this).backupNow()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveSettings() {
        saveRadioSettings()
        saveContactSettings()

        saveApiFields()
        saveSpeechSettings()

        prefs.saveGmailUser(binding.etGmailUser.text.toString().trim())
        prefs.saveGmailAppPassword(binding.etGmailPass.text.toString().trim())
        prefs.saveReportMail1(binding.etReportMail1.text.toString().trim())
        prefs.saveReportMail2(binding.etReportMail2.text.toString().trim())
        prefs.saveReportMail1Enabled(binding.switchMail1.isChecked)
        prefs.saveReportMail2Enabled(binding.switchMail2.isChecked)
        prefs.saveReportHour(binding.etReportHour.text.toString().trim().toIntOrNull() ?: 8)

        prefs.saveNavigationEnabled(binding.switchNavigation.isChecked)
        prefs.saveHomeAddress(binding.etHomeAddress.text.toString().trim())
        prefs.savePatientName(binding.etPatientName.text.toString().trim())

        prefs.saveCommIconsPerPage(
            when (getPickerSelection(spinnerCommIconsPerPage)) {
                0 -> 4
                2 -> 16
                3 -> 25
                else -> 9
            }
        )
        prefs.saveAutoSortCommunicationIconsEnabled(switchAutoSortIcons.isChecked)
        prefs.saveCommSubmenuTimeoutSeconds(timeoutSeconds(getPickerSelection(spinnerCommSubmenuTimeout)))
        prefs.saveHardwareVolumeControlEnabled(switchHardwareVolumeControl.isChecked)
        prefs.saveHardwareVolumeButtonMode(hardwareVolumeModeValue(getPickerSelection(spinnerHardwareVolumeMode)))

        prefs.savePatientLanguage1(langCode(getPickerSelection(spinnerPatientLang1)))
        prefs.savePatientLanguage2(langCode(getPickerSelection(spinnerPatientLang2)))
        prefs.saveAutoLanguageEnabled(switchAutoLanguage.isChecked)

        prefs.saveServerIp(binding.etServerIp.text.toString().trim())
        prefs.saveServerPort(binding.etServerPort.text.toString().trim())
        prefs.saveKioskReturnMinutes(binding.etKioskMinutes.text.toString().trim().toLongOrNull() ?: 5L)

        val pin = binding.etNewPin.text.toString().trim()
        if (pin.length == 4) {
            prefs.savePin(pin)
        }

        val backupOk = SettingsBackupManager(this).backupNow()
        Toast.makeText(
            this,
            if (backupOk) "Nastavitve shranjene in kopirane v Download/NovaRehab" else "Nastavitve shranjene",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveRadioSettings() {
        val nameFields = listOf(
            binding.etStation1Name,
            binding.etStation2Name,
            binding.etStation3Name,
            binding.etStation4Name,
            binding.etStation5Name,
            binding.etStation6Name
        )
        val urlFields = listOf(
            binding.etStation1Url,
            binding.etStation2Url,
            binding.etStation3Url,
            binding.etStation4Url,
            binding.etStation5Url,
            binding.etStation6Url
        )

        val stations = mutableListOf<RadioStation>()
        nameFields.forEachIndexed { index, field ->
            val name = field.text.toString().trim()
            val url = urlFields[index].text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                stations.add(RadioStation(name, url))
            }
        }

        prefs.saveRadioStations(stations)
    }

    private fun saveContactSettings() {
        val nameFields = listOf(
            binding.etContact1Name,
            binding.etContact2Name,
            binding.etContact3Name,
            binding.etContact4Name,
            binding.etContact5Name,
            binding.etContact6Name
        )
        val phoneFields = listOf(
            binding.etContact1Phone,
            binding.etContact2Phone,
            binding.etContact3Phone,
            binding.etContact4Phone,
            binding.etContact5Phone,
            binding.etContact6Phone
        )

        val defaultNames = listOf("Zana", "Dedek", "Inna", "Julija", "Kuma", "Dusan")
        val emojis = listOf("\uD83D\uDC64", "\uD83D\uDC64", "\uD83D\uDC64", "\uD83D\uDC64", "\uD83D\uDC64", "\uD83D\uDC64")
        val contacts = mutableListOf<Contact>()

        nameFields.forEachIndexed { index, field ->
            val name = field.text.toString().trim().ifEmpty { defaultNames[index] }
            val phone = phoneFields[index].text.toString().trim()
            val lang = if (getPickerSelection(contactLangButtons.getOrNull(index) ?: Button(this)) == 1) "uk" else "sl"

            prefs.saveContactIncomingCallEnabled(index, contactIncomingSwitches.getOrNull(index)?.isChecked ?: true)
            prefs.saveContactOutgoingCallEnabled(index, contactOutgoingSwitches.getOrNull(index)?.isChecked ?: true)

            contacts.add(Contact(name, phone, emojis.getOrElse(index) { "\uD83D\uDC64" }, lang))
        }

        prefs.saveContacts(contacts)
    }

    override fun onPause() {
        super.onPause()
        uiStatePrefs.edit().putInt(PREF_SETTINGS_SCROLL_Y, binding.root.scrollY).apply()
    }

    private data class CompanionShareContact(
        val contactId: String,
        val displayName: String,
        val preferredLanguage: String,
        val roomId: String,
        val patientName: String
    )

    private data class ImportedApiConfig(
        val baseUrl: String,
        val token: String
    )
}















