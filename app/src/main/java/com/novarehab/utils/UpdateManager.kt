package com.novarehab.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object UpdateManager {

    private const val PREFS_NAME = "nova_rehab_update_state"
    private const val KEY_UPDATE_PENDING = "update_pending"
    private const val KEY_UPDATE_SUCCESS = "update_success"

    private const val BACKUP_FILE_NAME = "NovaRehab_last_working.apk"
    private const val UPDATE_FILE_NAME = "NovaRehab_update.apk"

    private val httpClient = OkHttpClient()

    fun markCurrentLaunchSuccessful(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_UPDATE_PENDING, false)
            .putBoolean(KEY_UPDATE_SUCCESS, true)
            .apply()
    }

    fun showRestorePromptIfNeeded(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatePending = prefs.getBoolean(KEY_UPDATE_PENDING, false)
        val updateSuccess = prefs.getBoolean(KEY_UPDATE_SUCCESS, true)
        val backupFile = getBackupApkFile()

        if (!updatePending || updateSuccess || !backupFile.exists() || backupFile.length() <= 0L) {
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Obnova aplikacije")
            .setMessage("Prejšnja verzija je na voljo. Želite obnoviti?")
            .setPositiveButton("OBNOVI PREJŠNJO VERZIJO") { _, _ ->
                openApkInstaller(activity, backupFile)
            }
            .setNegativeButton("Ne zdaj", null)
            .show()
    }

    fun showUpdateDialog(activity: Activity, versionName: String, apkUrl: String, message: String) {
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
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(activity, "Pripravljam posodobitev...", Toast.LENGTH_SHORT).show()

            val result = withContext(Dispatchers.IO) {
                try {
                    saveCurrentApkBackupIfMissing(activity)
                    val updateApk = downloadUpdateApk(apkUrl)
                    validateApk(updateApk)
                    markUpdateInstallStarted(activity)
                    Result.success(updateApk)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { file ->
                    Toast.makeText(activity, "APK je prenesen. Potrdi namestitev.", Toast.LENGTH_LONG).show()
                    openApkInstaller(activity, file)
                },
                onFailure = { error ->
                    Toast.makeText(
                        activity,
                        "Posodobitve ni bilo mogoče pripraviti: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun openBackupInstaller(activity: Activity) {
        val backupFile = getBackupApkFile()
        if (!backupFile.exists() || backupFile.length() <= 0L) {
            Toast.makeText(activity, "Prejšnja verzija ni najdena.", Toast.LENGTH_LONG).show()
            return
        }

        openApkInstaller(activity, backupFile)
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
        if (backupFile.exists() && backupFile.length() > 0L) return

        val sourceApk = File(context.applicationInfo.sourceDir)
        if (!sourceApk.exists() || sourceApk.length() <= 0L) {
            throw IllegalStateException("Trenutne APK datoteke ni mogoče najti")
        }

        backupFile.parentFile?.mkdirs()
        sourceApk.copyTo(backupFile, overwrite = true)

        if (!backupFile.exists() || backupFile.length() <= 0L) {
            throw IllegalStateException("Varnostne kopije APK ni bilo mogoče shraniti")
        }
    }

    private fun downloadUpdateApk(apkUrl: String): File {
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
            updateFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
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

        context.startActivity(intent)
    }

    private fun getNovaRehabDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "NovaRehab")
    }

    private fun getBackupDirectory(): File {
        return File(getNovaRehabDirectory(), "backups")
    }

    private fun getBackupApkFile(): File {
        return File(getBackupDirectory(), BACKUP_FILE_NAME)
    }

    private fun getUpdateApkFile(): File {
        return File(getNovaRehabDirectory(), UPDATE_FILE_NAME)
    }
}
