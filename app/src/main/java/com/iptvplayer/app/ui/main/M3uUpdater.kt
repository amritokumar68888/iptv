package com.iptvplayer.app.ui.main

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object M3uUpdater {

    const val DRIVE_FILE_ID = "1MlBIpcKSx52U-Zek1g2hStMdEgUjMRrK"
    private const val DRIVE_URL =
        "https://drive.google.com/uc?export=download&id=$DRIVE_FILE_ID"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun updateFromDrive(context: Context): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(DRIVE_URL).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}")
                    )
                }

                val content = response.body?.string()
                    ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                if (!content.trim().startsWith("#EXTM3U")) {
                    return@withContext Result.failure(
                        Exception("Invalid M3U format")
                    )
                }

                // Save locally
                context.openFileOutput("amrito_cache.m3u", Context.MODE_PRIVATE)
                    .use { it.write(content.toByteArray()) }

                val count = content.lines()
                    .count { it.trim().startsWith("#EXTINF") }

                Result.success(count)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun getM3uSource(context: Context): String {
        val cached = context.getFileStreamPath("amrito_cache.m3u")
        return if (cached != null && cached.exists() && cached.length() > 100) {
            "file://${cached.absolutePath}"
        } else {
            "asset://amrito.m3u"
        }
    }
}
