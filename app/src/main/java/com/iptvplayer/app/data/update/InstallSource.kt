package com.iptvplayer.app.data.update

import android.content.Context
import android.os.Build

/**
 * অ্যাপটা কোন source থেকে install হয়েছে তা বের করে।
 * Play Store থেকে হলে "normal app" update path ব্যবহার করা যায়
 * (কোনো unknown-source prompt নেই)।
 */
object InstallSource {

    /** Play Store-এর installer package name */
    private const val PLAY_STORE = "com.android.vending"

    /** true = Google Play Store থেকে install করা হয়েছে */
    fun isFromPlay(context: Context): Boolean {
        val installer = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer == PLAY_STORE
    }
}
