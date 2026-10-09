package com.iptvplayer.app.ui.main

import android.content.Context
import com.iptvplayer.app.data.ip.IpAllowList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Public IP check।
 *
 * Allowed IP-র list এখন **remote** — `update/allowed-ips.txt` (GitHub) বা
 * Google Drive থেকে আসে। তাই নতুন APK build না করেই IP বদলানো যায়
 * (বিস্তারিত: UPDATE_SETUP.md)।
 */
object IpChecker {

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

    /**
     * Remote IP list নামিয়ে cache করে (fail করলে পুরনো list টাই থাকবে)।
     * App চালু হওয়ার সময় ও "Retry" চাপলে call করা হয়।
     */
    suspend fun refreshAllowList(context: Context): Boolean =
        IpAllowList.refresh(context)

    /** Returns true if current public IP is in the allow-list */
    suspend fun isAllowed(context: Context): Boolean {
        val ip = getPublicIp() ?: return false
        return IpAllowList.isAllowed(context, ip)
    }

    /** Dialog-এ দেখানোর জন্য allowed IP গুলোর সংক্ষিপ্ত text */
    fun describeAllowed(context: Context): String = IpAllowList.describe(context)
}

