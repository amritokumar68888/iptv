package com.iptvplayer.app.ui.main

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Auto-update system for M3U playlist from Google Drive.
 *
 * How it works:
 * - App open হলে remote URL থেকে M3U fetch করে
 * - Local cache-এর সাথে compare করে
 * - পরিবর্তন হলে cache update করে
 * - Channel list সবসময় latest থাকে
 */
object PlaylistUpdater {

    private const val PREF_NAME        = "playlist_cache"
    private const val KEY_CONTENT      = "m3u_content"
    private const val KEY_LAST_UPDATED = "last_updated"
    private const val KEY_URL          = "m3u_url"

    // Cache refresh interval — 30 minutes
    private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check and update M3U from remote URL.
     * Returns the latest M3U content (from cache or freshly downloaded).
     */
    suspend fun getLatestContent(context: Context, remoteUrl: String): String {
        return withContext(Dispatchers.IO) {
            val prefs       = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val cachedUrl   = prefs.getString(KEY_URL, "") ?: ""
            val cached      = prefs.getString(KEY_CONTENT, "") ?: ""
            val lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0L)
            val now         = System.currentTimeMillis()

            // Force refresh if URL changed
            val urlChanged = cachedUrl != remoteUrl

            // Check if cache is fresh enough
            val cacheExpired = (now - lastUpdated) > REFRESH_INTERVAL_MS

            if (!urlChanged && !cacheExpired && cached.isNotEmpty()) {
                // Cache still valid — return cached
                return@withContext cached
            }

            // Fetch fresh content
            try {
                val fresh = fetchRemote(remoteUrl)
                if (fresh.isNotEmpty() && fresh.startsWith("#EXTM3U")) {
                    // Save to cache
                    prefs.edit()
                        .putString(KEY_CONTENT, fresh)
                        .putString(KEY_URL, remoteUrl)
                        .putLong(KEY_LAST_UPDATED, now)
                        .apply()
                    fresh
                } else {
                    // Fetch failed or invalid — use cache
                    cached.ifEmpty { fresh }
                }
            } catch (e: Exception) {
                // Network error — use cache
                cached
            }
        }
    }

    /**
     * Force refresh regardless of cache age.
     */
    suspend fun forceRefresh(context: Context, remoteUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val fresh = fetchRemote(remoteUrl)
                if (fresh.isNotEmpty() && fresh.startsWith("#EXTM3U")) {
                    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(KEY_CONTENT, fresh)
                        .putString(KEY_URL, remoteUrl)
                        .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                        .apply()
                    fresh
                } else {
                    getCached(context)
                }
            } catch (e: Exception) {
                getCached(context)
            }
        }
    }

    /** Get cached content without network call */
    fun getCached(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONTENT, "") ?: ""
    }

    /** Check when last updated */
    fun getLastUpdatedTime(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATED, 0L)
    }

    private fun fetchRemote(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }
}
