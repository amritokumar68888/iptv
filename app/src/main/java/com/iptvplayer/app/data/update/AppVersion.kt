package com.iptvplayer.app.data.update

import android.content.Context
import android.os.Build

/** ইনস্টল করা app-এর versionCode / versionName বের করার helper। */
object AppVersion {

    fun code(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun name(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: ""
    }
}
