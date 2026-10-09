package com.iptvplayer.app.data.update

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play "In-App Updates" API-র wrapper।
 *
 * Play Store থেকে install করা app-এ এটাই normal app-এর মতো update দেয়:
 *  • কোনো "Install unknown apps" prompt নেই
 *  • কোনো permission toggle নেই
 *  • download + install পুরোটাই Google Play নিজে করে
 *
 * (শুধু Play-তে publish করা app-এ কাজ করে। Sideload app-এ fallback লাগবে।)
 */
class PlayUpdateManager(private val activity: Activity) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    /** flexible update-এর download progress (doneBytes, totalBytes) */
    var onProgress: ((Long, Long) -> Unit)? = null

    /** download শেষ — এখন install/restart হওয়ার কথা */
    var onDownloaded: (() -> Unit)? = null

    private var registered = false

    private val stateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING ->
                onProgress?.invoke(state.bytesDownloaded(), state.totalBytesToDownload())

            InstallStatus.DOWNLOADED -> {
                onDownloaded?.invoke()
                // Play-কে install শুরু করতে বলো — normal system install UI আসবে
                manager.completeUpdate()
            }

            InstallStatus.FAILED -> Log.w(TAG, "Play update download failed")
        }
    }

    /**
     * Update check করে দরকার হলে Play-এর update flow শুরু করে।
     *
     * @param immediate true  = বাধ্যতামূলক full-screen update (Play নিজের progress দেখায়)
     *                  false = background (flexible) update — গ্রাহক app চালাতে পারে
     */
    fun checkAndStart(requestCode: Int, immediate: Boolean) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                    return@addOnSuccessListener
                }
                val type = resolveType(info, immediate)
                if (type == NO_UPDATE_TYPE) return@addOnSuccessListener

                if (type == AppUpdateType.FLEXIBLE) registerListenerOnce()

                @Suppress("DEPRECATION")
                manager.startUpdateFlowForResult(info, type, activity, requestCode)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Play update check failed: ${e.message}")
            }
    }

    /** App আবার foreground-এ এলে অসম্পূর্ণ/শেষ হওয়া update handle করে। */
    fun resumeIfPending() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            // IMMEDIATE update মাঝপথে বন্ধ হয়েছিল → আবার শুরু করো
            if (info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                @Suppress("DEPRECATION")
                if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    manager.startUpdateFlowForResult(
                        info, AppUpdateType.IMMEDIATE, activity, RESUME_REQUEST
                    )
                }
            }
            // flexible download শেষ কিন্তু install হয়নি → install করাও
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                manager.completeUpdate()
            }
        }
    }

    /** Activity destroy-এ call করবেন। */
    fun destroy() {
        if (registered) {
            manager.unregisterListener(stateListener)
            registered = false
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun registerListenerOnce() {
        if (!registered) {
            manager.registerListener(stateListener)
            registered = true
        }
    }

    private fun resolveType(info: AppUpdateInfo, immediate: Boolean): Int = when {
        immediate && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
        else -> NO_UPDATE_TYPE
    }

    companion object {
        private const val TAG = "PlayUpdateManager"
        const val NO_UPDATE_TYPE = -1

        /** resume flow-এর জন্য আলাদা request code */
        const val RESUME_REQUEST = 1002
    }
}
