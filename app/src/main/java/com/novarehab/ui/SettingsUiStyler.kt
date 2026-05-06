package com.novarehab.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.novarehab.R

object SettingsUiStyler {
    private const val COLOR_TEXT = -0x1
    private const val COLOR_SECONDARY = -0x2f2718
    private const val COLOR_SURFACE = -0xe5d2
    private const val COLOR_SURFACE_ALT = -0xe9e0
    private const val COLOR_DIVIDER = -0xccccab
    private const val COLOR_ACCENT = -0x16bac4

    fun apply(view: View, density: Float) {
        when (view) {
            is EditText -> styleEditText(view, density)
            is Spinner -> styleSpinner(view)
            is Switch -> styleSwitch(view)
            is Button -> styleButton(view, density)
            is TextView -> styleTextView(view)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                apply(view.getChildAt(index), density)
            }
        }
    }

    fun styleTextView(textView: TextView) {
        val red = Color.red(textView.currentTextColor)
        val green = Color.green(textView.currentTextColor)
        val blue = Color.blue(textView.currentTextColor)
        val brightness = (red + green + blue) / 3
        if (brightness < 160) {
            textView.setTextColor(COLOR_TEXT)
        }
        textView.setLineSpacing(0f, 1.1f)
    }

    fun styleEditText(editText: EditText, density: Float) {
        editText.setTextColor(COLOR_TEXT)
        editText.setHintTextColor(COLOR_SECONDARY)
        editText.setLinkTextColor(COLOR_TEXT)
        editText.setBackgroundResource(R.drawable.bg_settings_input)
        editText.minHeight = dp(54, density)
        editText.minimumHeight = dp(54, density)
        editText.gravity = Gravity.CENTER_VERTICAL
        editText.setPadding(dp(14, density), dp(14, density), dp(14, density), dp(14, density))
    }

    fun styleSpinner(spinner: Spinner) {
        spinner.setBackgroundResource(R.drawable.bg_settings_input)
        spinner.setPadding(
            dp(14, spinner.resources.displayMetrics.density),
            dp(8, spinner.resources.displayMetrics.density),
            dp(14, spinner.resources.displayMetrics.density),
            dp(8, spinner.resources.displayMetrics.density)
        )
    }

    fun styleButton(button: Button, density: Float) {
        button.setTextColor(COLOR_TEXT)
        button.minHeight = dp(56, density)
        button.minimumHeight = dp(56, density)
        button.isAllCaps = false
        button.textSize = button.textSize / button.resources.displayMetrics.scaledDensity
        if (button.textSize < 16f) button.textSize = 16f
        button.setPadding(dp(14, density), dp(12, density), dp(14, density), dp(12, density))
    }

    fun styleSwitch(switchView: Switch) {
        switchView.setTextColor(COLOR_TEXT)
    }

    fun styleSectionTitle(textView: TextView) {
        textView.setTextColor(COLOR_TEXT)
        textView.textSize = 20f
        textView.setTypeface(null, Typeface.BOLD)
        textView.setPadding(textView.paddingLeft, textView.paddingTop + 8, textView.paddingRight, textView.paddingBottom + 8)
    }

    fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(0xFF1A1A2E.toInt()))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(COLOR_TEXT)
        dialog.findViewById<TextView>(dialog.context.resources.getIdentifier("alertTitle", "id", "android"))?.setTextColor(COLOR_TEXT)
        dialog.listView?.apply {
            setBackgroundColor(COLOR_SURFACE)
            divider = ColorDrawable(COLOR_DIVIDER)
            dividerHeight = 1
            post {
                for (index in 0 until childCount) {
                    (getChildAt(index) as? TextView)?.setTextColor(COLOR_TEXT)
                }
            }
        }
        dialog.window?.decorView?.let { apply(it, dialog.context.resources.displayMetrics.density) }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(COLOR_TEXT)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(COLOR_TEXT)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(COLOR_TEXT)
    }

    fun stylePickerRow(button: Button, density: Float) {
        button.setBackgroundResource(R.drawable.bg_settings_input)
        button.setTextColor(COLOR_TEXT)
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        button.textSize = 18f
        button.minHeight = dp(60, density)
        button.minimumHeight = dp(60, density)
        button.setPadding(dp(16, density), dp(14, density), dp(16, density), dp(14, density))
    }

    fun installFullscreenPickerForSpinner(
        activity: Activity,
        spinner: Spinner,
        title: String,
        density: Float
    ) {
        if (spinner.getTag(R.id.spinnerTtsVoice) == true) return
        val parent = spinner.parent as? ViewGroup ?: return
        val items = (0 until spinner.count).map { spinner.getItemAtPosition(it)?.toString().orEmpty() }
        if (items.isEmpty()) return

        val button = Button(activity)
        stylePickerRow(button, density)
        val params = cloneLayoutParams(spinner.layoutParams)
        button.layoutParams = params

        fun refreshText() {
            button.text = spinner.selectedItem?.toString().orEmpty().ifBlank { "Izberi možnost" }
        }

        refreshText()
        button.setOnClickListener {
            showFullscreenPicker(activity, title, items, spinner.selectedItemPosition) { selectedIndex ->
                spinner.setSelection(selectedIndex)
                refreshText()
            }
        }

        val index = parent.indexOfChild(spinner)
        parent.addView(button, index + 1)
        spinner.visibility = View.GONE
        spinner.layoutParams = LinearLayout.LayoutParams(0, 0)
        spinner.setTag(R.id.spinnerTtsVoice, true)
    }

    fun showFullscreenPicker(
        activity: Activity,
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0xFF1A1A2E.toInt()))
        dialog.setCancelable(true)

        val density = activity.resources.displayMetrics.density
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(dp(20, density), dp(20, density), dp(20, density), dp(20, density))
        }

        root.addView(TextView(activity).apply {
            text = title
            styleSectionTitle(this)
        })

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(8, density)
                bottomMargin = dp(12, density)
            }
        }

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        items.forEachIndexed { index, item ->
            listContainer.addView(Button(activity).apply {
                text = item
                stylePickerRow(this, density)
                setBackgroundColor(if (index == selectedIndex) COLOR_ACCENT else COLOR_SURFACE_ALT)
                setOnClickListener {
                    onSelected(index)
                    dialog.dismiss()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10, density)
                }
            })
        }

        scroll.addView(listContainer)
        root.addView(scroll)

        root.addView(Button(activity).apply {
            text = "PREKLIČI"
            styleButton(this, density)
            setBackgroundColor(0xFF333355.toInt())
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    private fun cloneLayoutParams(source: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        return when (source) {
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(source)
            null -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else -> ViewGroup.LayoutParams(source.width, source.height)
        }
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
