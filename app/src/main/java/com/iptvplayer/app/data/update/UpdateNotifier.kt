package com.iptvplayer.app.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iptvplayer.app.R
import com.iptvplayer.app.ui.main.MainActivity

/**
 * Update-এর notification — গ্রাহক যাতে নিজে check না করেই জেনে যায়।
 *
 * ⚠️ Android 13+ এ `POST_NOTIFICATIONS` runtime permission দরকার —
 *    না থাকলে notification দেখানো যায় না (MainActivity চেয়ে নেয়)।
 */
object UpdateNotifier {

    private const val CHANNEL_ID = "app_update"
    private const val CHANNEL_NAME = "App update"
    private const val NOTIFICATION_ID = 1001

    /** Android 8+ এর জন্য channel — একবার তৈরি হলেই হয়। */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH   // heads-up (উপরে ভেসে ওঠে)
        ).apply {
            description = "নতুন version এলে জানানো হয়"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** Notification দেখানোর অনুমতি আছে কি না (Android 13+ এ runtime)। */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * "নতুন update এসেছে" notification দেখায়। Notification-এ চাপ দিলে
     * MainActivity খোলে, যেটা নিজে থেকেই আবার check করে dialog দেখায়।
     */
    fun notifyAvailable(context: Context, info: UpdateInfo, forced: Boolean) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, 0, tapIntent, flags)

        val body = context.getString(
            R.string.notification_update_text,
            info.versionName,
            info.versionCode
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(context.getString(R.string.notification_update_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (info.changelog.isNotBlank()) "$body\n\n${info.changelog}" else body
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setOngoing(forced)                       // force update → সরানো যাবে না
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // permission নেই — চুপচাপ ignore (dialog তো আছেই)
        }
    }

    /** Update হয়ে গেলে / দরকার না থাকলে notification সরিয়ে দেয়। */
    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Notification tap করে এলে এই extra টা থাকে। */
    const val EXTRA_OPEN_UPDATE = "open_update"
}
