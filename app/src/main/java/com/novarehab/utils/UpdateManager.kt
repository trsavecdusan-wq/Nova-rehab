package com.novarehab.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.novarehab.core.storage.NovaRehabPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object UpdateManager {

    private const val PREFS_NAME = "nova_rehab_update_state"
    private const val KEY_UPDATE_PENDING = "update_pending"
    private const val KEY_UPDATE_SUCCESS = "update_success"

    private const val BACKUP_FILE_NAME = "NovaRehab_last_working.apk"
    private const val UPDATE_FILE_NAME = "NovaRehab_update.apk"
    private const val UPDATE_PREPARE_TIMEOUT_MS = 120_000L

    private const val UPDATE_METADATA_URL =
        "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/app-version.json"

    private val httpClient = OkHttpClient()

    fun markCurrentLaunchSuccessful(context: Context) {
        appContext = context.applicationContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UPDATE_PENDING, false)
            .putBoolean(KEY_UPDATE_SUCCESS, true)
            .apply()
    }

    fun showRestorePromptIfNeeded(activity: Activity) {
        appContext = activity.applicationContext
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatePending = prefs.getBoolean(KEY_UPDATE_PENDING, false)
        val updateSuccess = prefs.getBoolean(KEY_UPDATE_SUCCESS, true)
        val backupFile = getBackupApkFile()

        if (!updatePending || updateSuccess || !isUsableApk(backupFile)) {
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Obnova aplikacije")
            .setMessage("Prejsnja verzija je na voljo. Zelite obnoviti?")
            .setPositiveButton("OBNOVI PREJSNJO VERZIJO") { _, _ ->
                openApkInstaller(activity, backupFile)
            }
            .setNegativeButton("Ne zdaj", null)
            .show()
    }

    fun checkForUpdateNow(activity: Activity) {
        appContext = activity.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(activity, "Preverjam posodobitev...", Toast.LENGTH_SHORT).show()

            val result = withContext(Dispatchers.IO) {
                try {
                    val metadata = fetchUpdateMetadata()
                    val remoteVersionCode = metadata.optLong("versionCode", 0L)
                    val currentVersionCode = getCurrentVersionCode(activity)
                    Result.success(UpdateInfo(metadata, remoteVersionCode, currentVersionCode))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { info ->
                    if (info.remoteVersionCode > info.currentVersionCode) {
                        showUpdateDialog(
                            activity = activity,
                            versionName = info.metadata.optString("versionName", ""),
                            apkUrl = info.metadata.optString("apkUrl", ""),
                            message = info.metadata.optString("message", "Nova verzija je na voljo.")
                        )
                    } else {
                        Toast.makeText(
                            activity,
                            "Namescena je zadnja verzija.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onFailure = { error ->
                    Toast.makeText(
                        activity,
                        "Posodobitve ni bilo mogoce preveriti: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun showUpdateDialog(activity: Activity, versionName: String, apkUrl: String, message: String) {
        appContext = activity.applicationContext
        if (apkUrl.isBlank()) {
            Toast.makeText(activity, "Povezava do nove APK datoteke ni na voljo.", Toast.LENGTH_LONG).show()
            return
        }

        val text = buildString {
            append(message.ifBlank { "Nova verzija je na voljo." })
            if (versionName.isNotBlank()) {
                append("\n\nVerzija: ")
                append(versionName)
            }
            append("\n\nNamestitev bo odprla Android potrditveno okno.")
            append("\nAplikacija se ne namesca tiho.")
        }

        AlertDialog.Builder(activity)
            .setTitle("NovaRehab posodobitev")
            .setMessage(text)
            .setPositiveButton("PRENESI IN NAMESTI") { _, _ ->
                downloadAndInstallUpdate(activity, apkUrl)
            }
            .setNegativeButton("Kasneje", null)
            .show()
    }

    fun downloadAndInstallUpdate(activity: Activity, apkUrl: String) {
        appContext = activity.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val progressDialog = UpdateProgressDialog(activity).apply {
                show("Prenašanje APK", "Posodobitev se prenaša v napravo.")
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    withTimeout(UPDATE_PREPARE_TIMEOUT_MS) {
                        saveCurrentApkBackupIfMissing(activity)
                        val updateApk = downloadUpdateApk(apkUrl) { progress ->
                            progressDialog.update(
                                title = "Prenašanje APK",
                                message = if (progress in 0..100) {
                                    "Preneseno $progress %."
                                } else {
                                    "Posodobitev se prenaša v napravo."
                                }
                            )
                        }
                        progressDialog.update(
                            title = "Priprava namestitve",
                            message = "Preverjam preneseno datoteko in pripravljam Android namestitev."
                        )
                        validateApk(updateApk)
                        markUpdateInstallStarted(activity)
                        Result.success(updateApk)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { file ->
                    progressDialog.update(
                        title = "Pripravljeno za namestitev",
                        message = "Odpiram Android namestitev. Nato lahko Android nekaj sekund preverja aplikacijo."
                    )
                    openApkInstaller(activity, file)
                    progressDialog.dismiss()
                    Toast.makeText(
                        activity,
                        "Android preverja aplikacijo. To lahko traja nekaj sekund.",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = { error ->
                    progressDialog.dismiss()
                    val message = error.message ?: "Prekoracen cas priprave posodobitve."
                    Toast.makeText(
                        activity,
                        "Posodobitve ni bilo mogoce pripraviti: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun openBackupInstaller(activity: Activity) {
        appContext = activity.applicationContext
        val backupFile = getBackupApkFile()
        if (!isUsableApk(backupFile)) {
            Toast.makeText(activity, "Prejsnja verzija ni najdena.", Toast.LENGTH_LONG).show()
            return
        }

        openApkInstaller(activity, backupFile)
    }

    private fun fetchUpdateMetadata(): JSONObject {
        val request = Request.Builder()
            .url(UPDATE_METADATA_URL)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Streznik je vrnil napako ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IllegalStateException("Datoteka app-version.json je prazna")
            }

            return JSONObject(body)
        }
    }

    private fun getCurrentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun markUpdateInstallStarted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UPDATE_PENDING, true)
            .putBoolean(KEY_UPDATE_SUCCESS, false)
            .apply()
    }

    private fun saveCurrentApkBackupIfMissing(context: Context) {
        val backupFile = getBackupApkFile()
        if (isUsableApk(backupFile)) return

        val sourceApk = File(context.applicationInfo.sourceDir)
        if (!isUsableApk(sourceApk)) {
            throw IllegalStateException("Trenutne APK datoteke ni mogoce najti")
        }

        backupFile.parentFile?.mkdirs()
        sourceApk.copyTo(backupFile, overwrite = true)

        if (!isUsableApk(backupFile)) {
            throw IllegalStateException("Varnostne kopije APK ni bilo mogoce shraniti")
        }
    }

    private fun downloadUpdateApk(apkUrl: String, onProgress: (Int) -> Unit): File {
        val request = Request.Builder()
            .url(apkUrl)
            .get()
            .build()

        val updateFile = getUpdateApkFile()
        updateFile.parentFile?.mkdirs()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Prenos APK ni uspel: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Prenesena datoteka je prazna")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastProgress = -1
            updateFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }

        return updateFile
    }

    private fun validateApk(file: File) {
        if (!file.exists()) {
            throw IllegalStateException("Nova APK datoteka ne obstaja")
        }

        if (file.length() <= 0L) {
            throw IllegalStateException("Nova APK datoteka je prazna")
        }
    }

    private fun openApkInstaller(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Namestitvenega programa ni bilo mogoce odpreti.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isUsableApk(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > 0L
    }

    private fun getNovaRehabDirectory(): File {
        return NovaRehabPaths(appContext).rootDir
    }

    private fun getBackupDirectory(): File {
        return NovaRehabPaths(appContext).apkBackupsDir
    }

    private fun getBackupApkFile(): File {
        return File(getBackupDirectory(), BACKUP_FILE_NAME)
    }

    private fun getUpdateApkFile(): File {
        return File(NovaRehabPaths(appContext).updatesDir, UPDATE_FILE_NAME)
    }

    private data class UpdateInfo(
        val metadata: JSONObject,
        val remoteVersionCode: Long,
        val currentVersionCode: Long
    )

    private class UpdateProgressDialog(private val activity: Activity) {
        private val titleView = TextView(activity).apply {
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        private val messageView = TextView(activity).apply {
            textSize = 18f
            setTextColor(0xFFE0E0E0.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }

        private val dialog: AlertDialog = AlertDialog.Builder(activity)
            .setView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(64, 64, 64, 64)
                    setBackgroundColor(0xFF111111.toInt())
                    gravity = Gravity.CENTER
                    addView(
                        titleView,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        ProgressBar(activity).apply { isIndeterminate = true },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                            topMargin = 32
                            bottomMargin = 32
                        }
                    )
                    addView(
                        messageView,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            )
            .setCancelable(false)
            .create()

        fun show(title: String, message: String) {
            update(title, message)
            dialog.show()
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun update(title: String, message: String) {
            activity.runOnUiThread {
                titleView.text = title
                messageView.text = message
            }
        }

        fun dismiss() {
            activity.runOnUiThread {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }
    }

    private lateinit var appContext: Context
}
