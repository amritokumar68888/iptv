package com.iptvplayer.app.data.health

import android.content.Context
import com.iptvplayer.app.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Stream health checker — কোন channel-এর source আসলে কাজ করে।
 *
 * ────────────────────────────────────────────────────────────────────────────
 *  কেন দরকার
 * ────────────────────────────────────────────────────────────────────────────
 *  M3U playlist-এ প্রায়ই dead / offline / ভুল URL থাকে। আগে সেগুলো list-এ
 *  দেখাত, আর গ্রাহক tap করলে player-এ error আসত। এখন:
 *    • যে channel একবার fail করে, সেটা **মনে রাখা হয়**
 *    • list-এ আর দেখানো হয় না (স্বয়ংক্রিয়ভাবে বাদ)
 *    • player-এ stream fail করলে **নিজে থেকেই পরের channel-এ চলে যায়**
 *
 *  ⚠️ কোনো channel ভুলে বাদ পড়লে সেটা fixed হয়ে যায় না — cache-এর TTL
 *     শেষ হলে (dead = 30 মিনিট) আবার check হয় এবং live হলে ফিরে আসে।
 */
object StreamHealthChecker {

    private const val PREFS = "stream_health"
    private const val KEY_PREFIX = "s_"

    /** Live মানে কতক্ষণ বিশ্বাস করব */
    private const val ALIVE_TTL_MS = 6 * 60 * 60 * 1000L      // ৬ ঘণ্টা
    /** Dead মানে কতক্ষণ বিশ্বাস করব (সার্ভার আবার up হতে পারে) */
    private const val DEAD_TTL_MS = 30 * 60 * 1000L           // ৩০ মিনিট

    /** একসাথে কতটা URL check হবে (বেশি হলে সার্ভার ban দিতে পারে) */
    private const val CONCURRENCY = 8

    /** একবারে সর্বোচ্চ কতটা URL check করা হবে (playlist বড় হলে time বাঁচাতে) */
    const val MAX_CHECKS = 250

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ── Cache ────────────────────────────────────────────────────────────────

    /** আগে থেকে জানা আছে যে channel dead? */
    fun knownDead(context: Context, url: String): Boolean =
        read(context, url) == STATE_DEAD

    fun markDead(context: Context, url: String) = write(context, url, STATE_DEAD)
    fun markAlive(context: Context, url: String) = write(context, url, STATE_ALIVE)

    /** একটা URL-এর cache মুছে দেয় (জোর করে আবার check করতে)। */
    fun forget(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(url)).apply()
    }

    /** সব health cache মুছে দেয়। */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private const val STATE_ALIVE = "1"
    private const val STATE_DEAD = "0"

    private fun key(url: String) = KEY_PREFIX + url.hashCode()

    /** @return STATE_ALIVE / STATE_DEAD / null (জানা নেই বা TTL শেষ) */
    private fun read(context: Context, url: String): String? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(url), null) ?: return null

        val parts = raw.split(':')
        if (parts.size != 2) return null
        val state = parts[0]
        val ts = parts[1].toLongOrNull() ?: return null

        val ttl = if (state == STATE_ALIVE) ALIVE_TTL_MS else DEAD_TTL_MS
        if (System.currentTimeMillis() - ts > ttl) {
            forget(context, url)
            return null
        }
        return state
    }

    private fun write(context: Context, url: String, state: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(url), "$state:${System.currentTimeMillis()}")
            .apply()
    }

    // ── Single URL check ─────────────────────────────────────────────────────

    /**
     * URL টা কাজ করে কি না।
     *
     * আগে HEAD পাঠানো হয় (হালকা)। অনেক IPTV server HEAD সাপোর্ট করে না
     * (405/403/501 দেয়), তখন প্রথম কয়েক KB GET করে দেখা হয়।
     */
    suspend fun isAlive(url: String): Boolean = withContext(Dispatchers.IO) {
        // লোকাল file কখনো check করার দরকার নেই
        if (url.startsWith("file://") || url.startsWith("asset://")) return@withContext true

        try {
            if (tryHead(url)) return@withContext true
            tryRangeGet(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun tryHead(url: String): Boolean {
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", UA)
            .build()

        client.newCall(req).execute().use { res ->
            // 2xx হলে ভালো; 4xx/5xx হলে GET দিয়ে আবার চেষ্টা করব
            return res.isSuccessful
        }
    }

    private fun tryRangeGet(url: String): Boolean {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Range", "bytes=0-2047")
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return false
            // একটু data পড়ে দেখি — empty হলেও source টা আছে
            res.body?.byteStream()?.use { it.read(ByteArray(2048)) }
            return true
        }
    }

    // ── Batch verify ─────────────────────────────────────────────────────────

    /**
     * Channel গুলো ধাপে ধাপে check করে।
     *
     * @param onProgress প্রতিটা result আসার সাথে সাথে call হয়
     *                   (UI update-এর জন্য; Main thread-এ switch করা caller-এর কাজ)
     * @return যেগুলো dead পাওয়া গেছে
     */
    suspend fun verifyAll(
        context: Context,
        channels: List<Channel>,
        onProgress: suspend (checked: Int, total: Int, dead: Channel) -> Unit = { _, _, _ -> }
    ): List<Channel> = coroutineScope {
        val targets = channels.take(MAX_CHECKS)
        val total = targets.size
        val semaphore = Semaphore(CONCURRENCY)
        val dead = java.util.Collections.synchronizedList(mutableListOf<Channel>())
        val counter = java.util.concurrent.atomic.AtomicInteger(0)

        targets.forEach { ch ->
            launch {
                semaphore.withPermit {
                    // cache-এ ইতিমধ্যে live লেখা থাকলে আবার check না করেই ছেড়ে দাও
                    val cached = read(context, ch.url)
                    val alive = when (cached) {
                        STATE_ALIVE -> true
                        STATE_DEAD -> false
                        else -> isAlive(ch.url)
                    }

                    if (cached == null) {
                        if (alive) markAlive(context, ch.url) else markDead(context, ch.url)
                    }
                    if (!alive) dead.add(ch)

                    val done = counter.incrementAndGet()
                    onProgress(done, total, ch)
                }
            }
        }
        dead.toList()
    }
}
