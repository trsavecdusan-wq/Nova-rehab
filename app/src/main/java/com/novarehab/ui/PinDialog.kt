package com.novarehab.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.novarehab.R
import com.novarehab.utils.PrefsManager

class PinDialog(
    context: Context,
    private val onSuccess: () -> Unit
) : Dialog(context) {

    private var enteredPin = ""
    private lateinit var tvPin: TextView
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_pin)
        prefs = PrefsManager(context)
        tvPin = findViewById(R.id.tvPinDisplay)

        val numButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9
        )
        numButtons.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += index.toString()
                    tvPin.text = "●".repeat(enteredPin.length)
                    if (enteredPin.length == 4) checkPin()
                }
            }
        }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                tvPin.text = "●".repeat(enteredPin.length)
            }
        }
    }

    private fun checkPin() {
        val savedPin = prefs.getPin()
        if (enteredPin == savedPin) {
            dismiss()
            onSuccess()
        } else {
            enteredPin = ""
            tvPin.text = "Napacna PIN koda"
        }
    }
}
