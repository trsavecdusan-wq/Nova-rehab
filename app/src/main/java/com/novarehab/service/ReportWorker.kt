package com.novarehab.service

import android.content.Context
import androidx.work.*
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.StatsManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class ReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = PrefsManager(applicationContext)
        val stats = StatsManager(applicationContext)

        val yesterday = getYesterday()
        val report = stats.getDailyReport(yesterday)
        val reportText = stats.getReportAsText(report)

        var sent = false

        if (prefs.getReportMail1().isNotEmpty() && prefs.isReportMail1Enabled()) {
            sent = sendViaGmailApi(
                to = prefs.getReportMail1(),
                subject = "Nova Rehab poročilo — $yesterday",
                body = reportText,
                prefs = prefs
            ) || sent
        }

        if (prefs.getReportMail2().isNotEmpty() && prefs.isReportMail2Enabled()) {
            sent = sendViaGmailApi(
                to = prefs.getReportMail2(),
                subject = "Nova Rehab poročilo — $yesterday",
                body = reportText,
                prefs = prefs
            ) || sent
        }

        return if (sent) Result.success() else Result.retry()
    }

    // Pošlje mail prek Gmail SMTP prek direktne socket povezave (brez JavaMail)
    private fun sendViaGmailApi(to: String, subject: String, body: String, prefs: PrefsManager): Boolean {
        return try {
            val user = prefs.getGmailUser()
            val pass = prefs.getGmailAppPassword().replace(" ", "")
            if (user.isEmpty() || pass.isEmpty()) return false

            // Sestavi raw email
            val rawEmail = buildRawEmail(from = user, to = to, subject = subject, body = body)
            val encoded = Base64.getUrlEncoder().encodeToString(rawEmail.toByteArray(Charsets.UTF_8))

            // Pošlji prek Gmail API
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // Pridobi access token prek OAuth2 (App Password → Basic Auth prek SMTP relay)
            // Ker App Password ne dela z Gmail API REST, uporabimo SMTP prek SSL socket
            sendViaSMTPSocket(user, pass, to, subject, body)
        } catch (e: Exception) {
            false
        }
    }

    private fun sendViaSMTPSocket(
        from: String, password: String, to: String,
        subject: String, body: String
    ): Boolean {
        return try {
            val socket = javax.net.ssl.SSLSocketFactory.getDefault()
                .createSocket("smtp.gmail.com", 465) as javax.net.ssl.SSLSocket
            socket.startHandshake()

            val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(socket.outputStream, "UTF-8"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.inputStream, "UTF-8"))

            fun send(cmd: String) { writer.write("$cmd\r\n"); writer.flush() }
            fun read(): String = reader.readLine() ?: ""

            read() // 220 greeting
            send("EHLO novarehab")
            repeat(10) { read() } // read EHLO response lines

            send("AUTH LOGIN")
            read() // 334
            send(Base64.getEncoder().encodeToString(from.toByteArray()))
            read() // 334
            send(Base64.getEncoder().encodeToString(password.toByteArray()))
            val authResp = read()
            if (!authResp.startsWith("235")) { socket.close(); return false }

            send("MAIL FROM:<$from>")
            read()
            send("RCPT TO:<$to>")
            read()
            send("DATA")
            read() // 354

            val msg = buildRawEmail(from, to, subject, body)
            send(msg)
            send(".")
            val dataResp = read()

            send("QUIT")
            socket.close()

            dataResp.startsWith("250")
        } catch (e: Exception) {
            false
        }
    }

    private fun buildRawEmail(from: String, to: String, subject: String, body: String): String {
        val sb = StringBuilder()
        sb.append("From: Nova Rehab <$from>\r\n")
        sb.append("To: $to\r\n")
        sb.append("Subject: $subject\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n")
        sb.append("Content-Transfer-Encoding: 8bit\r\n")
        sb.append("\r\n")
        sb.append(body.replace("\n", "\r\n"))
        return sb.toString()
    }

    private fun getYesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
    }

    companion object {
        private const val WORK_NAME = "daily_report"

        fun scheduleDaily(context: Context, hourOfDay: Int) {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<ReportWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun schedule(context: Context, hourOfDay: Int) {
            scheduleDaily(context, hourOfDay)
        }
    }
}
