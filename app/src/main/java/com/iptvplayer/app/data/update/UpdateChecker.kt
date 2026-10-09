package com.iptvplayer.app.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Remote manifest check করে নতুন version আছে কি না দেখে।
 */
object UpdateChecker {

    private const val PREFS = "app_update_prefs"
    private const val KEY_LAST_CHECK = "last_check_ms"

    sealed class Result {
        /** নতুন version নেই */
        object UpToDate : Result()

        /** নতুন version পাওয়া গেছে — info সহ */
        data class Available(val info: UpdateInfo, val forced: Boolean) : Result()

        /** check করা যায়নি (network/config সমস্যা) */
        data class Failed(val message: String) : Result()
    }

    /** Auto-check — interval-এর ভিতরে আগে check হয়ে থাকলে skip করবে। */
    suspend fun checkIfDue(context: Context): Result =
        if (isDue(context)) check(context) else Result.UpToDate

    /** সবসময় check করবে (manual / Settings থেকে)। */
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        if (!UpdateConfig.isConfigured) {
            return@withContext Result.Failed("Update system এখনো configure করা হয়নি")
        }
        try {
            val json = JSONObject(fetchText(UpdateConfig.manifestUrl))
            val info = UpdateInfo.fromJson(json)

            if (info.versionCode <= 0) {
                return@withContext Result.Failed("Manifest-এ সঠিক versionCode নেই")
            }
            if (info.apkUrl.isBlank()) {
                return@withContext Result.Failed("Manifest-এ apkUrl নেই")
            }

            markChecked(context)

            val current = AppVersion.code(context)
            if (info.versionCode > current) {
                val forced = info.forceUpdate || current < info.minSupportedVersionCode
                Result.Available(info, forced)
            } else {
                Result.UpToDate
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Update check ব্যর্থ হয়েছে")
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun isDue(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
        val interval = UpdateConfig.CHECK_INTERVAL_HOURS * 60L * 60L * 1000L
        return System.currentTimeMillis() - last > interval
    }

    private fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    private fun fetchText(url: String): String {
        var response = DriveClient.ok.newCall(Request.Builder().url(url).build()).execute()

        // Drive confirm page হলে follow করো
        if (DriveClient.isHtml(response)) {
            val html = response.body?.string().orEmpty()
            response.close()
            response = DriveClient.ok.newCall(
                Request.Builder().url(DriveClient.followConfirm(url, html)).build()
            ).execute()
        }

        response.use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            return it.body?.string()?.takeIf { s -> s.isNotBlank() }
                ?: throw IllegalStateException("Manifest খালি এসেছে")
        }
    }
}
