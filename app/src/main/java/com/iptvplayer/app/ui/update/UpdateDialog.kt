package com.iptvplayer.app.ui.update

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.iptvplayer.app.R
import com.iptvplayer.app.data.update.UpdateInfo

/**
 * "New update available" dialog — changelog দেখায় এবং update link browser-এ খোলে।
 *
 * App নিজে APK download/install করে না (REQUEST_INSTALL_PACKAGES নেই) —
 * এতে Play Protect-এর "unknown app installer" সন্দেহ কমে। গ্রাহক browser থেকে
 * APK download করে normal ভাবে install করবে।
 */
class UpdateDialog(private val activity: Activity) {

    private var dialog: Dialog? = null

    fun show(
        info: UpdateInfo,
        forced: Boolean,
        @Suppress("UNUSED_PARAMETER") scope: LifecycleCoroutineScope
    ) {

        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_update)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.setCancelable(!forced)
        d.setCanceledOnTouchOutside(false)

        val tvVersion = d.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvChangelog = d.findViewById<TextView>(R.id.tvUpdateChangelog)
        val btnLater = d.findViewById<Button>(R.id.btnLater)
        val btnUpdateNow = d.findViewById<Button>(R.id.btnUpdateNow)

        tvVersion.text = activity.getString(R.string.update_version, info.versionName, info.versionCode)
        tvChangelog.text = info.changelog.ifBlank { activity.getString(R.string.update_no_changelog) }

        // Force update হলে "Later" button থাকবে না
        btnLater.visibility = if (forced) View.GONE else View.VISIBLE
        btnLater.setOnClickListener { d.dismiss() }

        btnUpdateNow.setOnClickListener {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                if (!forced) d.dismiss()
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_failed, e.message ?: "No browser"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        dialog = d
        d.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
