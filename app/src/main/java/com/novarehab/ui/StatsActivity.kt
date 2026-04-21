package com.novarehab.ui

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.novarehab.utils.StatsManager

class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF1a1a2e.toInt())
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(20, 20, 20, 20)
        scroll.addView(container)
        setContentView(scroll)

        val stats = StatsManager(this)

        // Glava
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        )
        val tvTitle = TextView(this)
        tvTitle.text = "📊 STATISTIKA"
        tvTitle.textSize = 20f
        tvTitle.setTextColor(0xFFe94560.toInt())
        tvTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tvTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btnBack = Button(this)
        btnBack.text = "NAZAJ"
        btnBack.setBackgroundColor(0xFF333355.toInt())
        btnBack.setTextColor(0xFFFFFFFF.toInt())
        btnBack.setOnClickListener { finish() }
        header.addView(tvTitle)
        header.addView(btnBack)
        container.addView(header)

        // Današnje poročilo
        val today = StatsManager.todayDate()
        val todayReport = stats.getDailyReport(today)
        addSection(container, "Danes ($today)", stats.getReportAsText(todayReport))

        // Zadnjih 7 dni
        addSection(container, "Zadnjih 30 dni", buildSummary(stats))
    }

    private fun addSection(container: LinearLayout, title: String, content: String) {
        val tvTitle = TextView(this)
        tvTitle.text = title
        tvTitle.textSize = 16f
        tvTitle.setTextColor(0xFFe94560.toInt())
        tvTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tvTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 20, 0, 8) }
        container.addView(tvTitle)

        val tvContent = TextView(this)
        tvContent.text = content
        tvContent.textSize = 13f
        tvContent.setTextColor(0xFFCCCCCC.toInt())
        tvContent.setBackgroundColor(0xFF16213e.toInt())
        tvContent.setPadding(16, 12, 16, 12)
        tvContent.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        container.addView(tvContent)
    }

    private fun buildSummary(stats: StatsManager): String {
        val reports = stats.getLast30Days()
        if (reports.isEmpty()) return "Ni podatkov"

        val sb = StringBuilder()
        var totalApp = 0; var totalRadio = 0; var totalComm = 0; var totalNav = 0
        reports.forEach {
            totalApp += it.appMinutes
            totalRadio += it.radioMinutes
            totalComm += it.commIconCount
            totalNav += it.navCount
        }
        sb.appendLine("Aktivnih dni: ${reports.size}")
        sb.appendLine("Skupni čas: ${totalApp} min (${totalApp/60}h)")
        sb.appendLine("Radio skupaj: ${totalRadio} min")
        sb.appendLine("Komunikacija: ${totalComm} sporočil")
        sb.appendLine("Navigacija: ${totalNav}x")

        // Najpogostejše komunikacijske ikone
        val allIcons = mutableMapOf<String, Int>()
        reports.forEach { r -> r.commIcons.forEach { (k, v) -> allIcons[k] = (allIcons[k] ?: 0) + v } }
        if (allIcons.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Top 5 sporočil:")
            allIcons.entries.sortedByDescending { it.value }.take(5).forEach {
                sb.appendLine("  • ${it.key}: ${it.value}x")
            }
        }
        return sb.toString()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
