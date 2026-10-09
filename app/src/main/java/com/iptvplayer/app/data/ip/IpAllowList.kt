package com.iptvplayer.app.data.ip

import android.content.Context
import com.iptvplayer.app.data.update.DriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Remote IP allow-list — কোন কোন public IP অ্যাপ চালাতে পারবে।
 *
 * ────────────────────────────────────────────────────────────────────────────
 *  কীভাবে IP বদলাবেন (বিস্তারিত: UPDATE_SETUP.md)
 * ────────────────────────────────────────────────────────────────────────────
 *  ১) `update/allowed-ips.txt` file টা এডিট করুন (GitHub web-এ পেন্সিল আইকনে
 *     ক্লিক করলেই হয়, git লাগে না) — অথবা Google Drive-এ রাখুন
 *  ২) Save করুন
 *
 *  ব্যস। গ্রাহক পরেরবার অ্যাপ খুললেই নতুন list ব্যবহার হবে — নতুন APK লাগবে না।
 *
 *  File format (এক লাইনে একটা entry, `#` দিয়ে comment):
 *      # Sky OTT allowed IPs
 *      103.7.4.12          ← exact IP
 *      103.7.4.13          ← ২য় IP (অনেকগুলো দেওয়া যাবে)
 *      192.168.1.*         ← শেষে * দিলে ওই prefix-এর সব IP
 *
 *  ⚠️ কোনো entry না পেলে বা network fail করলে সর্বশেষ কাজ করা list-টাই
 *     ব্যবহার হবে। file টা খালি করলে কেউ ঢুকতে পারবে না (fail-closed)।
 */
object IpAllowList {

    /**
     * Default: এই repo-র GitHub-hosted file।
     * GitHub web-এ সরাসরি এডিট করা যায় — push/token কিছু লাগে না।
     */
    const val IPS_URL =
        "https://raw.githubusercontent.com/amritokumar68888/iptv/main/update/allowed-ips.txt"

    /**
     * Google Drive-এ রাখতে চাইলে উপরেরটার বদলে এটা দিন:
     *   "https://drive.google.com/uc?export=download&id=<ALLOWED_IPS_FILE_ID>"
     * (file টা "Anyone with the link" করে share করতে হবে)
     */
    const val IPS_URL_DRIVE_OVERRIDE = ""

    /** কোনো list পাওয়া না গেলে (একেবারে প্রথমবার + offline) এটাই ব্যবহার হবে */
    val DEFAULT_IPS = listOf("103.7.4.12")

    private const val PREFS = "ip_allowlist_prefs"
    private const val KEY_TEXT = "ips_text"
    private const val KEY_TIME = "ips_time"

    private val url: String
        get() = IPS_URL_DRIVE_OVERRIDE.ifBlank { IPS_URL }

    // ── Network refresh ───────────────────────────────────────────────────────

    /**
     * Remote file থেকে list নামিয়ে cache করে।
     * @return true = নতুন list পাওয়া ও সংরক্ষণ করা হয়েছে
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = fetchText(url)
            if (text.isBlank()) return@withContext false

            // কিছু entry পেলেই সংরক্ষণ করো (ফাঁকা/ভুল file হলে পুরনোটা থাকবে)
            if (parse(text).isEmpty()) return@withContext false

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT, text)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /** এখন কার্যকর list: remote cache → bundled default। */
    fun current(context: Context): List<String> {
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TEXT, "") ?: ""
        val fromCache = parse(cached)
        return if (fromCache.isNotEmpty()) fromCache else DEFAULT_IPS
    }

    /** Public IP টা allow-list-এ আছে কি না। */
    fun isAllowed(context: Context, ip: String): Boolean {
        val candidate = ip.trim()
        if (candidate.isEmpty()) return false
        return current(context).any { matches(it, candidate) }
    }

    /** Dialog-এ দেখানোর জন্য সংক্ষিপ্ত text। */
    fun describe(context: Context): String {
        val list = current(context)
        if (list.isEmpty()) return "—"
        val shown = list.take(3).joinToString(", ")
        return if (list.size > 3) "$shown (+${list.size - 3} more)" else shown
    }

    /** কতক্ষণ আগে update হয়েছিল (জন্য debug/UI)। */
    fun lastUpdated(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TIME, 0L)

    // ── Parsing ──────────────────────────────────────────────────────────────

    /**
     * Text → list of entries।
     * Plain text (প্রতি লাইনে একটা), comma-separated, বা JSON array — সবই চলে।
     */
    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        // JSON array হলে ["1.2.3.4","5.6.7.8"]
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return Regex("\"([^\"]+)\"")
                .findAll(trimmed)
                .map { it.groupValues[1].trim() }
                .filter { isValidEntry(it) }
                .toList()
        }

        return raw.lineSequence()
            .map { it.substringBefore('#').trim() }   // comment বাদ
            .flatMap { it.split(',', ';', ' ', '\t').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && isValidEntry(it) }
            .distinct()
            .toList()
    }

    /** Entry টা বৈধ কি না — exact IPv4, অথবা শেষে `*` দেওয়া prefix। */
    fun isValidEntry(entry: String): Boolean {
        val e = entry.trim()
        if (e.isEmpty()) return false
        return if (e.endsWith(".*")) isIPv4(e.dropLast(2) + ".0") && e.count { it == '.' } == 3
        else isIPv4(e)
    }

    private fun matches(entry: String, ip: String): Boolean = when {
        entry == ip -> true
        entry.endsWith(".*") -> ip.startsWith(entry.dropLast(1))  // "103.7.4." prefix
        else -> false
    }

    private fun isIPv4(s: String): Boolean {
        val parts = s.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private fun fetchText(url: String): String {
        val cacheBusted = "$url" + (if (url.contains("?")) "&" else "?") +
                "_=${System.currentTimeMillis()}"

        var response = DriveClient.ok
            .newCall(Request.Builder().url(cacheBusted).build())
            .execute()

        // Drive confirm page হলে follow করো
        if (DriveClient.isHtml(response)) {
            val html = response.body?.string().orEmpty()
            response.close()
            response = DriveClient.ok
                .newCall(Request.Builder().url(DriveClient.followConfirm(url, html)).build())
                .execute()
        }

        response.use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            return it.body?.string() ?: ""
        }
    }
}
