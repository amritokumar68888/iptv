package com.iptvplayer.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Download করা APK install করা ও প্রয়োজনীয় permission handle করা।
 */
object InstallHelper {

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** APK টা Android installer-এ পাঠিয়ে install শুরু করবে। */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** "Install unknown apps" permission দেওয়া আছে কি না। */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

    /** Permission না থাকলে settings page খুলে দেবে (গ্রাহক Allow চাপবে)। */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
