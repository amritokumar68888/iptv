package com.iptvplayer.app.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.iptvplayer.app.R
import com.iptvplayer.app.data.update.AppVersion
import com.iptvplayer.app.data.update.InstallSource
import com.iptvplayer.app.data.update.PlayUpdateManager
import com.iptvplayer.app.data.update.UpdateChecker
import com.iptvplayer.app.data.update.UpdateConfig
import com.iptvplayer.app.databinding.ActivityMainBinding
import com.iptvplayer.app.ui.channels.ChannelListActivity
import com.iptvplayer.app.ui.channels.RecentManager
import com.iptvplayer.app.ui.update.UpdateDialog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var playUpdateManager: PlayUpdateManager

    companion object {
        private const val TAG = "SkyOTT-Updater"
        private const val PLAY_UPDATE_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        playUpdateManager = PlayUpdateManager(this)

        // Setup UI immediately — don't block on IP check
        setupUi()

        // IP check in background
        checkIpInBackground()

        // Google Drive update in background
        checkForUpdate()

        // App version update check in background
        checkForAppUpdate()
    }

    // ── App (APK) Update ──────────────────────────────────────────────────────

    private fun checkForAppUpdate() {
        if (!UpdateConfig.CHECK_ON_START) return

        // ── Play Store থেকে install করা app → Play In-App Updates ────────────
        // normal app-এর মতো update — কোনো "Install unknown apps" prompt নেই
        if (InstallSource.isFromPlay(this)) {
            setUpPlayCallbacks()
            playUpdateManager.checkAndStart(
                PLAY_UPDATE_REQUEST,
                UpdateConfig.PLAY_FORCE_UPDATE
            )
            return
        }

        // ── Sideload করা app → manifest-based updater ────────────────────────
        // (গ্রাহককে একবার "Install unknown apps" allow করতে হবে)
        lifecycleScope.launch {
            // Auto-check-এ throttle মানা হয় (বারবার network call এড়াতে)
            when (val result = UpdateChecker.checkIfDue(this@MainActivity)) {
                is UpdateChecker.Result.Available -> showUpdateDialog(result)
                UpdateChecker.Result.UpToDate ->
                    setStatus(getString(R.string.already_latest), 4000)
                is UpdateChecker.Result.Failed ->
                    // আগে এখানে চুপচাপ ignore করা হতো — এখন দেখা যাবে
                    setStatus(getString(R.string.check_failed, result.message))
            }
        }
    }

    /** গ্রাহক নিজে "Check for update" চাপলে — throttle ছাড়া সবসময় check করে। */
    private fun manualUpdateCheck() {
        if (InstallSource.isFromPlay(this)) {
            setUpPlayCallbacks()
            setStatus(getString(R.string.checking_update))
            playUpdateManager.checkAndStart(
                PLAY_UPDATE_REQUEST,
                UpdateConfig.PLAY_FORCE_UPDATE
            )
            return
        }
        lifecycleScope.launch {
            setStatus(getString(R.string.checking_update))
            when (val result = UpdateChecker.check(this@MainActivity)) {
                is UpdateChecker.Result.Available -> showUpdateDialog(result)
                UpdateChecker.Result.UpToDate ->
                    setStatus(getString(R.string.already_latest), 4000)
                is UpdateChecker.Result.Failed ->
                    setStatus(getString(R.string.check_failed, result.message))
            }
        }
    }

    private fun setUpPlayCallbacks() {
        playUpdateManager.onProgress = { done, total ->
            val pct = if (total > 0) ((done * 100) / total).toInt() else 0
            setStatus(getString(R.string.update_downloading, pct))
        }
        playUpdateManager.onDownloaded = {
            setStatus(getString(R.string.update_ready))
        }
    }

    private fun showUpdateDialog(available: UpdateChecker.Result.Available) {
        Log.i(
            TAG,
            "Update available: v${available.info.versionName} " +
                    "(build ${available.info.versionCode}) forced=${available.forced}"
        )
        if (isFinishing || isDestroyed) return
        try {
            UpdateDialog(this).show(available.info, available.forced, lifecycleScope)
        } catch (e: Exception) {
            // Dialog দেখাতে ব্যর্থ হলেও যেন app crash না করে
            Log.e(TAG, "Update dialog failed", e)
            setStatus(getString(R.string.check_failed, e.message ?: "dialog error"))
        }
    }

    /**
     * Install করা version দেখায় — কোন build চলছে তা জানার একমাত্র উপায়।
     * (update check কাজ করছে কি না বোঝার জন্য এটাই সবচেয়ে দরকারি তথ্য।)
     */
    private fun showAppVersion() {
        val v = getString(
            R.string.app_version,
            AppVersion.name(this),
            AppVersion.code(this)
        )
        binding.tvAppVersion.text = v
        Log.i(TAG, "Installed version: $v")
    }

    /** Update-check এর status দেখায় (autoHideMs > 0 হলে কিছুক্ষণ পর লুকায়)। */
    private fun setStatus(text: String, autoHideMs: Long = 0) {
        binding.tvCheckResult.visibility = View.VISIBLE
        binding.tvCheckResult.text = text
        if (autoHideMs > 0) {
            binding.tvCheckResult.postDelayed({
                binding.tvCheckResult.visibility = View.GONE
            }, autoHideMs)
        }
    }

    // ── UI Setup (always runs first) ──────────────────────────────────────────

    private fun setupUi() {
        updateCounts()
        showAppVersion()

        binding.rowCheckUpdate.setOnClickListener { manualUpdateCheck() }

        binding.rowAllChannels.setOnClickListener {
            val url = M3uUpdater.getM3uSource(this)
            startActivity(Intent(this, ChannelListActivity::class.java).apply {
                putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.all_channels))
                putExtra(ChannelListActivity.EXTRA_M3U_URL, url)
                putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_ALL)
            })
        }

        binding.rowRecentWatch.setOnClickListener {
            val url = M3uUpdater.getM3uSource(this)
            startActivity(Intent(this, ChannelListActivity::class.java).apply {
                putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.recent_watch))
                putExtra(ChannelListActivity.EXTRA_M3U_URL, url)
                putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_RECENT)
            })
        }
    }

    // ── IP Check (background, non-blocking) ───────────────────────────────────

    private fun checkIpInBackground() {
        lifecycleScope.launch {
            // রিমোট IP list নামাও (fail করলে cache/bundled default চলবে)
            IpChecker.refreshAllowList(this@MainActivity)

            val allowed = IpChecker.isAllowed(this@MainActivity)
            if (!allowed) {
                val ip = IpChecker.getPublicIp() ?: "Unknown"
                showIpBlockedDialog(ip)
            }
        }
    }

    // ── Google Drive Update ───────────────────────────────────────────────────

    private fun checkForUpdate() {
        lifecycleScope.launch {
            binding.tvUpdateStatus.visibility = View.VISIBLE
            binding.tvUpdateStatus.text = "⏳ Updating playlist..."

            val result = M3uUpdater.updateFromDrive(this@MainActivity)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                binding.tvUpdateStatus.text = "✅ Updated! $count channels"
                binding.tvAllCount.text = count.toString()
                prefs.edit().putInt("channel_count", count).apply()
                binding.tvUpdateStatus.postDelayed({
                    binding.tvUpdateStatus.visibility = View.GONE
                }, 3000)
            } else {
                binding.tvUpdateStatus.visibility = View.GONE
            }
        }
    }

    // ── IP Blocked Dialog ─────────────────────────────────────────────────────

    private fun showIpBlockedDialog(currentIp: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.activity_ip_blocked)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        dialog.findViewById<TextView>(R.id.tvCurrentIp).text = "Your IP: $currentIp"

        // Block UI
        binding.rowAllChannels.isClickable = false
        binding.rowRecentWatch.isClickable = false
        binding.rowAllChannels.alpha = 0.4f
        binding.rowRecentWatch.alpha = 0.4f

        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)
        val btnRetry    = dialog.findViewById<MaterialButton>(R.id.btnRetry)

        btnRetry.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnRetry.isEnabled = false
            lifecycleScope.launch {
                // Retry চাপলে IP list আবার নামাও — Drive/GitHub-এ বদলালেই ধরা পড়বে
                IpChecker.refreshAllowList(this@MainActivity)

                val allowed = IpChecker.isAllowed(this@MainActivity)
                val ip      = IpChecker.getPublicIp() ?: "Unknown"
                progressBar.visibility = View.GONE
                btnRetry.isEnabled = true
                if (allowed) {
                    dialog.dismiss()
                    // Restore UI
                    binding.rowAllChannels.isClickable = true
                    binding.rowRecentWatch.isClickable = true
                    binding.rowAllChannels.alpha = 1f
                    binding.rowRecentWatch.alpha = 1f
                } else {
                    dialog.findViewById<TextView>(R.id.tvCurrentIp).text = "Your IP: $ip"
                    dialog.findViewById<TextView>(R.id.tvMessage).text =
                        "এই IP থেকে access permitted নয়।\nAllowed IP: " +
                        IpChecker.describeAllowed(this@MainActivity)
                }
            }
        }
        dialog.show()
    }

    // ── Counts ────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        updateCounts()
        // Play update মাঝপথে থেমে থাকলে/ডাউনলোড শেষ হলে সেটা handle করে
        playUpdateManager.resumeIfPending()
    }

    override fun onDestroy() {
        super.onDestroy()
        playUpdateManager.destroy()
    }

    private fun updateCounts() {
        val savedCount = prefs.getInt("channel_count", 0)
        if (savedCount > 0) binding.tvAllCount.text = savedCount.toString()
        binding.tvRecentCount.text = RecentManager.count(this).toString()
    }

    // ── Back / Exit ───────────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { showExitDialog() }

    private fun showExitDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_exit)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)
        dialog.findViewById<Button>(R.id.btnYes).setOnClickListener { dialog.dismiss(); finishAffinity() }
        dialog.findViewById<Button>(R.id.btnNo).setOnClickListener  { dialog.dismiss() }
        dialog.show()
    }
}
