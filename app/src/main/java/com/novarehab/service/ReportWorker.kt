package com.novarehab.service

import android.content.Context
import androidx.work.*
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.StatsManager
import java.util.concurrent.TimeUnit
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*

class ReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = PrefsManager(applicationContext)
        val stats = StatsManager(applicationContext)

        // Generiraj včerajšnje poročilo
        val yesterday = getYesterday()
        val report = stats.getDailyReport(yesterday)
        val reportText = stats.getReportAsText(report)

        var sent = false

        // Pošlji na mail 1
        val mail1 = prefs.getReportMail1()
        if (mail1.isNotEmpty() && prefs.isReportMail1Enabled()) {
            sent = sendEmail(
                to = mail1,
                subject = "Nova Rehab poročilo — $yesterday",
                body = reportText,
                prefs = prefs
            ) || sent
        }

        // Pošlji na mail 2
        val mail2 = prefs.getReportMail2()
        if (mail2.isNotEmpty() && prefs.isReportMail2Enabled()) {
            sent = sendEmail(
                to = mail2,
                subject = "Nova Rehab poročilo — $yesterday",
                body = reportText,
                prefs = prefs
            ) || sent
        }

        return if (sent) Result.success() else Result.retry()
    }

    private fun sendEmail(to: String, subject: String, body: String, prefs: PrefsManager): Boolean {
        return try {
            val gmailUser = prefs.getGmailUser()
            val gmailPass = prefs.getGmailAppPassword()
            if (gmailUser.isEmpty() || gmailPass.isEmpty()) return false

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.ssl.trust", "smtp.gmail.com")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(gmailUser, gmailPass)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(gmailUser, "Nova Rehab"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setText(body, "UTF-8")
            }

            Transport.send(message)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getYesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
    }

    companion object {
        private const val WORK_NAME = "daily_report"

        fun schedule(context: Context, hourOfDay: Int) {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

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
    }
}
