package com.iptvplayer.app.ui.update

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.iptvplayer.app.R
import com.iptvplayer.app.data.update.ApkDownloader
import com.iptvplayer.app.data.update.InstallHelper
import com.iptvplayer.app.data.update.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * "New update available" dialog — changelog দেখায়, download progress দেখায়,
 * download শেষ হলে নিজে থেকে installer খুলে দেয়।
 */
class UpdateDialog(private val activity: Activity) {

    private var dialog: Dialog? = null
    private var downloadJob: Job? = null

    fun show(info: UpdateInfo, forced: Boolean, scope: LifecycleCoroutineScope) {

        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_update)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.setCancelable(!forced)
        d.setCanceledOnTouchOutside(false)

        val tvVersion     = d.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvChangelog   = d.findViewById<TextView>(R.id.tvUpdateChangelog)
        val progressGroup = d.findViewById<LinearLayout>(R.id.progressGroup)
        val progressBar   = d.findViewById<ProgressBar>(R.id.progressUpdate)
        val tvProgress    = d.findViewById<TextView>(R.id.tvUpdateProgress)
        val buttonGroup   = d.findViewById<LinearLayout>(R.id.buttonGroup)
        val btnLater      = d.findViewById<Button>(R.id.btnLater)
        val btnUpdateNow  = d.findViewById<Button>(R.id.btnUpdateNow)

        tvVersion.text = activity.getString(R.string.update_version, info.versionName, info.versionCode)
        tvChangelog.text = info.changelog.ifBlank { activity.getString(R.string.update_no_changelog) }

        // Force update হলে "Later" button থাকবে না
        btnLater.visibility = if (forced) View.GONE else View.VISIBLE
        btnLater.setOnClickListener { d.dismiss() }

        btnUpdateNow.setOnClickListener {
            // Install permission নেই → settings খুলে দাও
            if (!InstallHelper.canInstall(activity)) {
                InstallHelper.requestInstallPermission(activity)
                return@setOnClickListener
            }

            buttonGroup.visibility = View.GONE
            progressGroup.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            tvProgress.text = activity.getString(R.string.update_downloading, 0)

            downloadJob = scope.launch {
                try {
                    val apk = ApkDownloader.download(activity, info.apkUrl) { done, total ->
                        activity.runOnUiThread {
                            if (total > 0) {
                                val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                                progressBar.progress = pct
                                tvProgress.text = activity.getString(R.string.update_downloading, pct)
                            } else {
                                tvProgress.text = activity.getString(
                                    R.string.update_downloaded_mb, done / 1024 / 1024
                                )
                            }
                        }
                    }

                    tvProgress.text = activity.getString(R.string.update_installing)
                    InstallHelper.install(activity, apk)
                    d.dismiss()

                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    tvProgress.text = activity.getString(
                        R.string.update_failed, e.message ?: "Unknown error"
                    )
                    buttonGroup.visibility = View.VISIBLE
                    btnUpdateNow.text = activity.getString(R.string.retry)
                }
            }
        }

        dialog = d
        d.show()
    }

    fun dismiss() {
        downloadJob?.cancel()
        dialog?.dismiss()
    }
}
