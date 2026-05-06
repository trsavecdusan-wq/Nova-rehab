package com.novarehab.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.novarehab.R

object SettingsUiStyler {
    private const val COLOR_TEXT = -0x1
    private const val COLOR_HINT = -0x2f2718
    private const val COLOR_SURFACE = -0xe5d2
    private const val COLOR_DIVIDER = -0xccccab

    fun apply(view: View, density: Float) {
        when (view) {
            is EditText -> styleEditText(view, density)
            is Spinner -> styleSpinner(view)
            is Switch -> styleSwitch(view)
            is TextView -> styleTextView(view)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                apply(view.getChildAt(index), density)
            }
        }
    }

    fun styleEditText(editText: EditText, density: Float) {
        editText.setTextColor(COLOR_TEXT)
        editText.setHintTextColor(COLOR_HINT)
        editText.setLinkTextColor(COLOR_TEXT)
        editText.setBackgroundResource(R.drawable.bg_settings_input)
        editText.minHeight = dp(52, density)
        editText.minimumHeight = dp(52, density)
        editText.setPadding(dp(14, density), dp(12, density), dp(14, density), dp(12, density))
    }

    fun styleSpinner(spinner: Spinner) {
        spinner.setBackgroundResource(R.drawable.bg_settings_input)
        spinner.setPopupBackgroundDrawable(ColorDrawable(COLOR_SURFACE))
        spinner.post {
            (spinner.selectedView as? TextView)?.let { selected ->
                selected.setTextColor(COLOR_TEXT)
                selected.setPadding(selected.paddingLeft, selected.paddingTop, selected.paddingRight, selected.paddingBottom)
            }
        }
    }

    fun styleSwitch(switchView: Switch) {
        switchView.setTextColor(COLOR_TEXT)
        switchView.setPadding(switchView.paddingLeft, dp(6, switchView.resources.displayMetrics.density), switchView.paddingRight, dp(6, switchView.resources.displayMetrics.density))
    }

    fun styleTextView(textView: TextView) {
        if (textView is Button || textView is EditText || textView is Switch) return
        val red = Color.red(textView.currentTextColor)
        val green = Color.green(textView.currentTextColor)
        val blue = Color.blue(textView.currentTextColor)
        val brightness = (red + green + blue) / 3
        if (brightness < 160) {
            textView.setTextColor(COLOR_TEXT)
        }
        textView.setLineSpacing(0f, 1.08f)
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
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(COLOR_TEXT)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(COLOR_TEXT)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(COLOR_TEXT)
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
