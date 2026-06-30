package com.iptvplayer.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Google Drive থেকে M3U file download করে local cache-এ রাখে।
 * App খুললে check করে — পরিবর্তন হলে update করে।
 */
object M3uUpdateManager {

    private const val TAG = "M3uUpdateManager"
    private const val CACHE_FILE = "amrito_cached.m3u"
    private const val PREFS_NAME = "m3u_prefs"
    private const val KEY_LAST_HASH = "last_hash"

    // ── আপনার Google Drive File ID এখানে দিন ────────────────────────────────
    // Drive link: https://drive.google.com/file/d/FILE_ID/view
    // Direct URL: https://drive.google.com/uc?export=download&id=FILE_ID
    private const val DRIVE_FILE_ID = "YOUR_GOOGLE_DRIVE_FILE_ID"

    val driveUrl: String
        get() = "https://drive.google.com/uc?export=download&id=$DRIVE_FILE_ID"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * App start-এ call করুন।
     * Returns: true = updated, false = no change / error
     */
    suspend fun checkAndUpdate(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for M3U update from Drive...")

            val request = Request.Builder().url(driveUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Drive request failed: ${response.code}")
                return@withContext false
            }

            val newContent = response.body?.string() ?: return@withContext false

            if (newContent.isBlank() || !newContent.contains("#EXTM3U")) {
                Log.w(TAG, "Invalid M3U content received")
                return@withContext false
            }

            // Hash compare — পরিবর্তন হয়েছে কিনা দেখো
            val newHash = newContent.hashCode().toString()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val oldHash = prefs.getString(KEY_LAST_HASH, "")

            if (newHash == oldHash) {
                Log.d(TAG, "M3U not changed, skip update")
                return@withContext false
            }

            // Cache-এ save করো
            val cacheFile = File(context.filesDir, CACHE_FILE)
            cacheFile.writeText(newContent)

            // Hash save করো
            prefs.edit().putString(KEY_LAST_HASH, newHash).apply()

            Log.d(TAG, "M3U updated! Channels: ${newContent.lines().count { it.startsWith("#EXTINF") }}")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Cached M3U file path — ChannelListActivity-তে এটা use করুন
     */
    fun getCachedFilePath(context: Context): String {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        return if (cacheFile.exists() && cacheFile.length() > 0) {
            "file://${cacheFile.absolutePath}"
        } else {
            // Fallback: assets থেকে
            "asset://amrito.m3u"
        }
    }

    /**
     * Cache আছে কিনা check
     */
    fun hasCachedFile(context: Context): Boolean {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        return cacheFile.exists() && cacheFile.length() > 0
    }
}
