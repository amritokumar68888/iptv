package com.iptvplayer.app.ui.channels

import android.content.Context
import com.iptvplayer.app.data.model.Channel

/**
 * Simple SharedPreferences-based recent channel tracker.
 * Stores up to 20 recent channel names+urls.
 */
object RecentManager {

    private const val PREF_NAME  = "recent_channels"
    private const val KEY_NAMES  = "names"
    private const val KEY_URLS   = "urls"
    private const val MAX_RECENT = 20

    fun add(context: Context, channel: Channel) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val names = prefs.getString(KEY_NAMES, "")!!.split("|||").filter { it.isNotEmpty() }.toMutableList()
        val urls  = prefs.getString(KEY_URLS,  "")!!.split("|||").filter { it.isNotEmpty() }.toMutableList()

        // Remove if already exists
        val idx = urls.indexOf(channel.url)
        if (idx >= 0) { names.removeAt(idx); urls.removeAt(idx) }

        // Add at front
        names.add(0, channel.name)
        urls.add(0, channel.url)

        // Trim
        if (names.size > MAX_RECENT) { names.removeAt(names.lastIndex); urls.removeAt(urls.lastIndex) }

        prefs.edit()
            .putString(KEY_NAMES, names.joinToString("|||"))
            .putString(KEY_URLS,  urls.joinToString("|||"))
            .apply()
    }

    fun get(context: Context): List<Channel> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val names = prefs.getString(KEY_NAMES, "")!!.split("|||").filter { it.isNotEmpty() }
        val urls  = prefs.getString(KEY_URLS,  "")!!.split("|||").filter { it.isNotEmpty() }
        return names.zip(urls).map { (name, url) -> Channel(name = name, url = url) }
    }

    fun count(context: Context): Int = get(context).size
}
