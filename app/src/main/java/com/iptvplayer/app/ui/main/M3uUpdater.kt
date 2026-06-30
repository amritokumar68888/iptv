package com.iptvplayer.app.ui.main

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Google Drive থেকে m3u file download করে local cache-এ save করে।
 * App open হলে check করে — নতুন version থাকলে update করে।
 *
 * ── Setup ──────────────────────────────────────────────────────────────────
 * 1. Google Drive-এ amrito.m3u upload করুন
 * 2. File → Share → Anyone with link → Viewer
 * 3. Link: https://drive.google.com/file/d/FILE_ID/view
 * 4. নিচের DRIVE_FILE_ID-তে FILE_ID বসান
 */
object M3uUpdater {

    // ── এখানে আপনার Google Drive FILE_ID বসান ────────────────────────────────
    private const val DRIVE_FILE_ID = "1MlBIpcKSx52U-Zek1g2hStMdEgUjMRrK"

    private val DRIVE_URL get() = "https://drive.google.com/uc?export=download&id=$DRIVE_FILE_ID"

    const val CACHE_FILE  = "amrito_cache.m3u"
    private const val PREFS_NAME  = "m3u_prefs"
    private const val KEY_HASH    = "m3u_hash"

    // প্রতিবার app open হলে check করবে (no interval)
    // private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * App open হলে call করুন।
     * নতুন m3u থাকলে cache update করে।
     */
    suspend fun checkAndUpdate(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cacheFile = File(context.filesDir, CACHE_FILE)

        // প্রতিবার check করবে
        try {
            // Drive থেকে download
            val driveUrl = prefs.getString("custom_drive_url", DRIVE_URL) ?: DRIVE_URL
            val request = Request.Builder().url(driveUrl).build()
            val remoteContent = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                response.body?.string() ?: return@withContext
            }

            // Valid m3u check
            if (!remoteContent.trimStart().startsWith("#EXTM3U")) return@withContext

            val remoteHash = remoteContent.hashCode().toString()
            val localHash  = prefs.getString(KEY_HASH, "") ?: ""

            if (remoteHash != localHash || !cacheFile.exists()) {
                // নতুন content — save করো
                cacheFile.writeText(remoteContent)
                prefs.edit()
                    .putString(KEY_HASH, remoteHash)
                    .apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Cache file পড়ো — নেই হলে null */
    fun readCache(context: Context): String? {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        return if (cacheFile.exists()) cacheFile.readText() else null
    }

    /** Custom Drive URL set করুন */
    fun setDriveUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("custom_drive_url", url).apply()
    }

    /** Force re-check next time */
    fun forceRefresh(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TIME, 0L).apply()
    }

    fun isFileIdSet() = DRIVE_FILE_ID != "REPLACE_WITH_YOUR_FILE_ID"
}
