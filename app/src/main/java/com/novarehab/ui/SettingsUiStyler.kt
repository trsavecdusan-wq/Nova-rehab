package com.novarehab.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import com.novarehab.R

object SettingsUiStyler {
    private const val COLOR_TEXT = -0x1
    private const val COLOR_SECONDARY = -0x2f2718
    private const val COLOR_SURFACE = -0xe5d2
    private const val COLOR_DIVIDER = -0xccccab

    fun apply(view: View, density: Float) {
        when (view) {
            is EditText -> styleEditText(view, density)
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

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
