package com.novarehab.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tipi dogodkov
object StatEvent {
    const val APP_START       = "app_start"
    const val APP_STOP        = "app_stop"
    const val RADIO_PLAY      = "radio_play"
    const val RADIO_STOP      = "radio_stop"
    const val COMM_ICON       = "comm_icon"
    const val NAV_START       = "nav_start"
    const val NAV_STOP        = "nav_stop"
    const val CALL_OUT        = "call_out"
    const val MUSIC_PLAY      = "music_play"
    const val MUSIC_STOP      = "music_stop"
    const val GALLERY_OPEN    = "gallery_open"
    const val MIRROR_OPEN     = "mirror_open"
    const val LANG_CHANGE     = "lang_change"
    const val TTS_ERROR       = "tts_error"
    const val TTS_OK          = "tts_ok"
}

data class StatRecord(
    val id: Long = 0,
    val event: String,
    val value: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyReport(
    val date: String,
    val appMinutes: Int,
    val radioMinutes: Int,
    val radioStations: Map<String, Int>,
    val commIconCount: Int,
    val commIcons: Map<String, Int>,
    val navCount: Int,
    val navMinutes: Int,
    val callCount: Int,
    val musicMinutes: Int,
    val langChanges: Int,
    val ttsErrors: Int = 0
)

class StatsManager(context: Context) : SQLiteOpenHelper(context, "nova_stats.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event TEXT NOT NULL,
                value TEXT DEFAULT '',
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_timestamp ON stats(timestamp)")
        db.execSQL("CREATE INDEX idx_event ON stats(event)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS stats")
        onCreate(db)
    }

    fun log(event: String, value: String = "") {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("event", event)
                put("value", value)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert("stats", null, cv)
        } catch (e: Exception) {}
    }

    fun getDailyReport(date: String = todayDate()): DailyReport {
        val db = readableDatabase
        val dayStart = dateToMillis(date, 0)
        val dayEnd = dateToMillis(date, 24)

        val cursor = db.query("stats", null,
            "timestamp >= ? AND timestamp < ?",
            arrayOf(dayStart.toString(), dayEnd.toString()),
            null, null, "timestamp ASC")

        val records = mutableListOf<StatRecord>()
        while (cursor.moveToNext()) {
            records.add(StatRecord(
                id = cursor.getLong(0),
                event = cursor.getString(1),
                value = cursor.getString(2),
                timestamp = cursor.getLong(3)
            ))
        }
        cursor.close()

        return buildReport(date, records)
    }

    private fun buildReport(date: String, records: List<StatRecord>): DailyReport {
        var appMinutes = 0; var radioMinutes = 0; var navMinutes = 0; var musicMinutes = 0
        var commCount = 0; var navCount = 0; var callCount = 0; var langChanges = 0
        val radioStations = mutableMapOf<String, Int>()
        val commIcons = mutableMapOf<String, Int>()

        var appStart = 0L; var radioStart = 0L; var navStart = 0L; var musicStart = 0L
        var ttsErrors = 0

        for (r in records) {
            when (r.event) {
                StatEvent.APP_START   -> appStart = r.timestamp
                StatEvent.APP_STOP    -> if (appStart > 0) { appMinutes += ((r.timestamp - appStart) / 60000).toInt(); appStart = 0 }
                StatEvent.RADIO_PLAY  -> { radioStart = r.timestamp; radioStations[r.value] = (radioStations[r.value] ?: 0) + 1 }
                StatEvent.RADIO_STOP  -> if (radioStart > 0) { radioMinutes += ((r.timestamp - radioStart) / 60000).toInt(); radioStart = 0 }
                StatEvent.COMM_ICON   -> { commCount++; commIcons[r.value] = (commIcons[r.value] ?: 0) + 1 }
                StatEvent.NAV_START   -> { navStart = r.timestamp; navCount++ }
                StatEvent.NAV_STOP    -> if (navStart > 0) { navMinutes += ((r.timestamp - navStart) / 60000).toInt(); navStart = 0 }
                StatEvent.CALL_OUT    -> callCount++
                StatEvent.MUSIC_PLAY  -> musicStart = r.timestamp
                StatEvent.MUSIC_STOP  -> if (musicStart > 0) { musicMinutes += ((r.timestamp - musicStart) / 60000).toInt(); musicStart = 0 }
                StatEvent.LANG_CHANGE -> langChanges++
                StatEvent.TTS_ERROR   -> ttsErrors++
            }
        }

        return DailyReport(date, appMinutes, radioMinutes, radioStations, commCount, commIcons, navCount, navMinutes, callCount, musicMinutes, langChanges, ttsErrors)
    }

    fun getReportAsText(report: DailyReport): String {
        val sb = StringBuilder()
        sb.appendLine("═══ NOVA REHAB POROČILO — ${report.date} ═══")
        sb.appendLine()
        sb.appendLine("📱 Čas uporabe: ${report.appMinutes} min")
        sb.appendLine()
        sb.appendLine("📻 Radio: ${report.radioMinutes} min")
        if (report.radioStations.isNotEmpty()) {
            report.radioStations.entries.sortedByDescending { it.value }.take(3).forEach {
                sb.appendLine("   • ${it.key}: ${it.value}x")
            }
        }
        sb.appendLine()
        sb.appendLine("💬 Komunikacija: ${report.commIconCount} sporočil")
        if (report.commIcons.isNotEmpty()) {
            sb.appendLine("   Najpogostejša:")
            report.commIcons.entries.sortedByDescending { it.value }.take(5).forEach {
                sb.appendLine("   • ${it.key}: ${it.value}x")
            }
        }
        sb.appendLine()
        sb.appendLine("🗺️ Navigacija: ${report.navCount}x, skupaj ${report.navMinutes} min")
        sb.appendLine("📞 Klici: ${report.callCount}x")
        sb.appendLine("🎵 Glasba: ${report.musicMinutes} min")
        sb.appendLine("🌐 Menjava jezika: ${report.langChanges}x")
        sb.appendLine()
        if (report.ttsErrors > 0) {
            sb.appendLine()
            sb.appendLine("⚠️ OPOZORILO: OpenAI TTS je imel ${report.ttsErrors} napak!")
            sb.appendLine("   Možni vzroki: zmanjkalo kredita, ni interneta.")
            sb.appendLine("   Preverite: platform.openai.com → Billing")
        }
        sb.appendLine()
        sb.appendLine("Poslano z Nova Rehab tablice")
        return sb.toString()
    }

    fun getLast30Days(): List<DailyReport> {
        val reports = mutableListOf<DailyReport>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        repeat(30) {
            reports.add(getDailyReport(sdf.format(cal.time)))
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return reports.filter { it.appMinutes > 0 || it.commIconCount > 0 }
    }

    companion object {
        fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        private fun dateToMillis(date: String, hour: Int): Long {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = sdf.parse(date) ?: return 0L
                d.time + (hour * 3600000L)
            } catch (e: Exception) { 0L }
        }
    }
}
