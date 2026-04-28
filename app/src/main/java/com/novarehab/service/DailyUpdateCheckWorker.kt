package com.novarehab.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novarehab.R
import com.novarehab.ui.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DailyUpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val metadata = fetchUpdateMetadata() ?: return Result.success()
            val remoteVersionCode = metadata.optLong("versionCode", 0L)
            val currentVersionCode = getCurrentVersionCode()

            if (remoteVersionCode > currentVersionCode) {
                showUpdateNotification(
                    versionName = metadata.optString("versionName", ""),
                    apkUrl = metadata.optString("apkUrl", ""),
                    message = metadata.optString("message", "Nova verzija je na voljo.")
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchUpdateMetadata(): JSONObject? {
        val request = Request.Builder()
            .url(UPDATE_METADATA_URL)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun getCurrentVersionCode(): Long {
        val info = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun showUpdateNotification(versionName: String, apkUrl: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(manager)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_SHOW_UPDATE
            putExtra(EXTRA_UPDATE_VERSION_NAME, versionName)
            putExtra(EXTRA_UPDATE_APK_URL, apkUrl)
            putExtra(EXTRA_UPDATE_MESSAGE, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            4001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NovaRehab posodobitev je na voljo")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "NovaRehab posodobitve",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Obvestila o novih verzijah aplikacije NovaRehab"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_SHOW_UPDATE = "com.novarehab.action.SHOW_UPDATE"
        const val EXTRA_UPDATE_VERSION_NAME = "updateVersionName"
        const val EXTRA_UPDATE_APK_URL = "updateApkUrl"
        const val EXTRA_UPDATE_MESSAGE = "updateMessage"

        private const val UPDATE_METADATA_URL =
            "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/app-version.json"

        private const val CHANNEL_ID = "novarehab_updates"
        private const val NOTIFICATION_ID = 4100

        private val httpClient = OkHttpClient()
    }
}
