package com.iptvplayer.app.data.update

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Google Drive থেকে file download করার জন্য shared OkHttp client.
 *
 * Drive বড় file-এর জন্য একটা "virus scan warning" HTML page দেখায়
 * (Download anyway button)। এই page-টা detect করে actual download URL
 * বানিয়ে দেয়, তাই APK বড় হলেও download কাজ করবে।
 */
internal object DriveClient {

    val ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            private val jar = mutableMapOf<String, MutableList<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = jar.getOrPut(url.host) { mutableListOf() }
                for (c in cookies) {
                    list.removeAll { it.name == c.name }
                    list.add(c)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                jar[url.host] ?: emptyList()
        })
        .build()

    /** Response টা HTML (Drive confirm page) কি না। */
    fun isHtml(response: Response): Boolean =
        (response.header("Content-Type") ?: "").contains("text/html", ignoreCase = true)

    /**
     * Drive-এর confirm page-এর HTML থেকে আসল download URL বানায়।
     *
     * Drive দুই রকম page দেখায়:
     *  ১) নতুন format — একটা GET form:
     *     <form action="https://drive.usercontent.google.com/download">
     *       <input name="id" value="..."><input name="export" value="download">
     *       <input name="confirm" value="t"><input name="uuid" value="...">
     *     </form>
     *  ২) পুরনো format — URL/HTML-এ `confirm=<token>`
     *
     * দুইটাই handle করা হয়। না পারলে exception throw করে।
     */
    fun followConfirm(originalUrl: String, html: String): String {
        // ── ১) নতুন format: form-এর hidden inputs ────────────────────────────
        val form = Regex(
            "<form[^>]*action=\"([^\"]+)\"[^>]*>(.*?)</form>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)

        if (form != null) {
            val action = unescape(form.groupValues[1])
            val inner  = form.groupValues[2]

            val params = LinkedHashMap<String, String>()
            for (chunk in inner.split(Regex("<input", RegexOption.IGNORE_CASE)).drop(1)) {
                val name  = Regex("name=\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)
                val value = Regex("value=\"([^\"]*)\"").find(chunk)?.groupValues?.get(1) ?: ""
                if (!name.isNullOrEmpty()) params[name] = unescape(value)
            }

            if (params.isNotEmpty()) {
                val query = params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
                return "$action?$query"
            }
        }

        // ── ২) পুরনো format: confirm=<token> ────────────────────────────────
        val confirm = Regex("confirm=([0-9A-Za-z_\\-]+)").find(html)?.groupValues?.get(1)
        val id = Regex("[?&]id=([0-9A-Za-z_\\-]+)").find(originalUrl)?.groupValues?.get(1)
        if (confirm != null && id != null) {
            val uuid = Regex("uuid=([0-9A-Za-z_\\-]+)").find(html)?.groupValues?.get(1)
            val sb = StringBuilder("https://drive.usercontent.google.com/download")
                .append("?id=").append(id)
                .append("&export=download")
                .append("&confirm=").append(confirm)
            if (uuid != null) sb.append("&uuid=").append(uuid)
            return sb.toString()
        }

        // ── ৩) Fallback: "Download anyway" link-এর href ─────────────────────
        Regex("href=\"([^\"]*confirm=[^\"]*)\"").find(html)?.groupValues?.get(1)?.let { href ->
            val h = unescape(href)
            return if (h.startsWith("http")) h else "https://drive.usercontent.google.com$h"
        }

        throw IllegalStateException("Drive download link পাওয়া যায়নি (sharing/format যাচাই করুন)")
    }

    private fun unescape(s: String): String =
        s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}

