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

    // ── Google Play Protect ───────────────────────────────────────────────────

    /**
     * ⚠️ জেনে রাখুন: **Play Protect-এর স্ক্যান কোনো app থেকে বন্ধ করা যায় না।**
     * এটা Google Play services-এর ভেতরে চলে; আপনার app-এর কোনো API/permission
     * এর উপর কাজ করে না। যে tutorial "code দিয়ে bypass" বলে, সেটা মিথ্যা।
     *
     * যা **করা যায়**: গ্রাহককে Play Protect-এর সেটিংসে **সরাসরি পৌঁছে দেওয়া**,
     * যাতে দুটো tap-এ সে "Scan apps with Play Protect" বন্ধ করতে পারে।
     * এখানেই করে সেটাই — এটা ব্যবহারকারীর নিজের নিরাপত্তা-সিদ্ধান্ত।
     *
     * ⚠️ আসল সমাধান: **Google Play Store-এ publish** করা। Play থেকে install
     * করা app-কে Play Protect কখনো ব্লক করে না।
     */
    fun openPlayProtectSettings(context: Context): Boolean {
        // Play Protect screen বিভিন্ন device/OEM-এ বিভিন্ন নামে থাকে,
        // তাই ক্রমে চেষ্টা করা হয় — প্রথমটা যেটা কাজ করে।
        val attempts: List<Intent> = buildList {
            // ১) Play Protect (Verify Apps) — Play services-এর ভেতরের activity
            add(Intent().setComponent(
                android.content.ComponentName(
                    "com.google.android.gms",
                    "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"
                )
            ))
            // ২) একই thing, implicit action দিয়ে (কিছু device-এ এটা লাগে)
            add(Intent("com.google.android.gms.security.settings.VerifyAppsSettingsActivity"))

            // ৩) Play Store-এর Play Protect screen
            add(Intent().setComponent(
                android.content.ComponentName(
                    "com.android.vending",
                    "com.google.android.finsky.protect.ProtectSettingsActivity"
                )
            ))

            // ৪) সাধারণ Security settings (play protect-ও এখানেই থাকে)
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))

            // ৫) শেষ উপায়: Play services app-এর info page
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.google.android.gms")
            })
        }

        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // এই activity নেই — পরেরটায় যাও
            }
        }

        // ৬) একেবারে শেষ: Settings খুলে দাও
        return try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Device-এ Google Play services আছে কি না — Play Protect-এর relevance বোঝার
     * জন্য (না থাকলে Play Protect ব্লক করার সুযোগই নেই)।
     */
    fun hasPlayServices(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.google.android.gms", 0) != null
    } catch (e: Exception) {
        false
    }
}
