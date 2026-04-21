package com.novarehab.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.novarehab.utils.PrefsManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UpdateService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private val TAG = "UpdateService"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkForUpdates()
        return START_NOT_STICKY
    }

    private fun checkForUpdates() {
        scope.launch {
            try {
                val prefs = PrefsManager(this@UpdateService)
                val serverIp = prefs.getServerIp()
                val serverPort = prefs.getServerPort()
                if (serverIp.isEmpty()) return@launch

                val radioUrl = "http://$serverIp:$serverPort/nova-rehab/config/radio.json"
                val request = Request.Builder().url(radioUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: return@use
                        prefs.saveRadioStationsJson(json)
                        Log.d(TAG, "Radio postaje posodobljene")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Napaka: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
