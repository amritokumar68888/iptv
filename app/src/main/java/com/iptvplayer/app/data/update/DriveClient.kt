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
     * না পারলে exception throw করে।
     */
    fun followConfirm(originalUrl: String, html: String): String {
        val confirm = Regex("confirm=([0-9A-Za-z_\\-]+)")
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Drive confirm token পাওয়া যায়নি")
        val uuid = Regex("uuid=([0-9A-Za-z_\\-]+)").find(html)?.groupValues?.get(1)
        val id = Regex("[?&]id=([0-9A-Za-z_\\-]+)")
            .find(originalUrl)?.groupValues?.get(1)
            ?: throw IllegalStateException("Drive file id পাওয়া যায়নি")

        val sb = StringBuilder("https://drive.usercontent.google.com/download")
            .append("?id=").append(id)
            .append("&export=download")
            .append("&confirm=").append(confirm)
        if (uuid != null) sb.append("&uuid=").append(uuid)
        return sb.toString()
    }
}
