package com.iptvplayer.app.data.logo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Remote channel-logo map (`update/channel-logos.json`).
 *
 * Playlist-এ অনেক সময় `tvg-logo` খালি থাকে — তখন চ্যানেলের নাম দিয়ে এই map-এ
 * খোঁজা হয়। পাওয়া গেলে আসল logo দেখানো হয়, না পেলে generated avatar।
 *
 * File format: { "<normalized-name>": "<logo-url>", ... }
 * raw.githubusercontent fail করলে jsDelivr CDN fallback ব্যবহার হয়।
 */
object ChannelLogoStore {

    private const val URL =
        "https://raw.githubusercontent.com/amritokumar68888/iptv/main/update/channel-logos.json"
    private const val URL_FALLBACK =
        "https://cdn.jsdelivr.net/gh/amritokumar68888/iptv@main/update/channel-logos.json"

    private const val PREFS = "channel_logos"
    private const val KEY_JSON = "json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var mem: Map<String, String>? = null

    /** map ডাউনলোড করে cache করে। true = নতুন map পাওয়া গেছে। */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        for (source in listOf(URL, URL_FALLBACK)) {
            try {
                val bust = if (source.contains("?"))
                    "$source&_=${System.currentTimeMillis()}"
                else "$source?_=${System.currentTimeMillis()}"

                val response = client.newCall(Request.Builder().url(bust).build()).execute()
                response.use {
                    if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
                    val body = it.body?.string().orEmpty()
                    if (body.isBlank()) throw IllegalStateException("empty")
                    val obj = JSONObject(body)
                    if (obj.length() == 0) throw IllegalStateException("no entries")

                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_JSON, body).apply()
                    mem = parse(body)
                    return@withContext true
                }
            } catch (e: Exception) {
                // এই source fail — পরেরটায় যাও
            }
        }
        false
    }

    /** চ্যানেলের নাম → logo URL (থাকলে)। */
    fun logoFor(context: Context, name: String): String? {
        val map = mem ?: parse(cached(context)).also { mem = it }
        if (map.isEmpty()) return null
        return map[normKey(name)]
    }

    private fun cached(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, "") ?: ""

    private fun parse(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = HashMap<String, String>(obj.length())
            for (key in obj.keys()) {
                val v = obj.optString(key, "")
                if (v.isNotEmpty()) map[key] = v
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** JSON-এর key-এর সাথে মেলাতে map তৈরির সময় ব্যবহৃত একই normalization। */
    fun normKey(name: String): String {
        var x = name.lowercase()
        x = x.replace(Regex("\\([^)]*\\)"), " ")
        x = Regex("[^a-z0-9]+").replace(x, " ")
        val stop = setOf("hd", "sd", "fhd", "tv", "channel", "live", "official", "the")
        return x.split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in stop }
            .joinToString("")
    }

    /** Imgur page link (imgur.com/ID.png) → direct image (i.imgur.com/ID.png)। */
    fun normalizeLogoUrl(url: String): String {
        val u = url.trim()
        if (u.isEmpty()) return ""
        if (u.contains("imgur.com/") && !u.contains("i.imgur.com/")) {
            return u.replace("imgur.com/", "i.imgur.com/")
        }
        return u
    }
}
