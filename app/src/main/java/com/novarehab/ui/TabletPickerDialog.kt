package com.novarehab.ui

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object TabletPickerDialog {
    fun show(
        activity: androidx.appcompat.app.AppCompatActivity,
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
            SettingsUiStyler.styleSectionTitle(this)
        })

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
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
                SettingsUiStyler.stylePickerRow(this, density)
                setBackgroundColor(if (index == selectedIndex) 0xFFE94560.toInt() else 0xFF16213E.toInt())
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
            text = "PREKLICI"
            SettingsUiStyler.styleButton(this, density)
            setBackgroundColor(0xFF333355.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
        )
        dialog.show()
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
