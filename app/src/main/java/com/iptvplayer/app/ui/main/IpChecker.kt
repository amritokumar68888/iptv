package com.iptvplayer.app.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object IpChecker {

    /** Allowed public IP — only this IP can use the app */
    const val ALLOWED_IP = "103.7.4.12"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Returns the device's current public IP address.
     * Uses api.ipify.org — returns plain text IP.
     */
    suspend fun getPublicIp(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            // Response: {"ip":"103.7.4.12"}
            JSONObject(body).getString("ip")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Returns true if current public IP matches the allowed IP */
    suspend fun isAllowed(): Boolean {
        val ip = getPublicIp() ?: return false
        return ip.trim() == ALLOWED_IP
    }
}
