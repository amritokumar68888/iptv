package com.iptvplayer.app.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.iptvplayer.app.R
import com.iptvplayer.app.databinding.ActivityMainBinding
import com.iptvplayer.app.ui.channels.ChannelListActivity
import com.iptvplayer.app.ui.channels.RecentManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Step 1: IP check
        checkIpAndInit()
    }

    // ── IP Check ──────────────────────────────────────────────────────────────

    private fun checkIpAndInit() {
        binding.rowAllChannels.isEnabled  = false
        binding.rowRecentWatch.isEnabled  = false

        lifecycleScope.launch {
            val allowed = IpChecker.isAllowed()
            if (allowed) {
                setupUi()
                // Step 2: Google Drive update (background)
                checkForUpdate()
            } else {
                val ip = IpChecker.getPublicIp() ?: "Unknown"
                showIpBlockedDialog(ip)
            }
        }
    }

    // ── Google Drive Update ───────────────────────────────────────────────────

    private fun checkForUpdate() {
        lifecycleScope.launch {
            // Show updating indicator
            binding.tvUpdateStatus.visibility = View.VISIBLE
            binding.tvUpdateStatus.text = "⏳ Updating playlist..."

            val result = M3uUpdater.updateFromDrive(this@MainActivity)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                binding.tvUpdateStatus.text = "✅ Updated! $count channels"
                // Update count display
                binding.tvAllCount.text = count.toString()
                prefs.edit().putInt("channel_count", count).apply()
                // Hide after 3 seconds
                binding.tvUpdateStatus.postDelayed({
                    binding.tvUpdateStatus.visibility = View.GONE
                }, 3000)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                if (err.contains("File ID set করা হয়নি")) {
                    // Drive not configured — silent, use asset file
                    binding.tvUpdateStatus.visibility = View.GONE
                } else {
                    binding.tvUpdateStatus.text = "⚠️ Update failed: $err"
                    binding.tvUpdateStatus.postDelayed({
                        binding.tvUpdateStatus.visibility = View.GONE
                    }, 4000)
                }
            }
        }
    }

    // ── Normal UI Setup ───────────────────────────────────────────────────────

    private fun setupUi() {
        binding.rowAllChannels.isEnabled  = true
        binding.rowRecentWatch.isEnabled  = true
        updateCounts()

        binding.rowAllChannels.setOnClickListener {
            val url = M3uUpdater.getM3uSource(this)
            startActivity(Intent(this, ChannelListActivity::class.java).apply {
                putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.all_channels))
                putExtra(ChannelListActivity.EXTRA_M3U_URL, url)
                putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_ALL)
            })
        }

        binding.rowRecentWatch.setOnClickListener {
            if (RecentManager.get(this).isEmpty()) return@setOnClickListener
            val url = M3uUpdater.getM3uSource(this)
            startActivity(Intent(this, ChannelListActivity::class.java).apply {
                putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.recent_watch))
                putExtra(ChannelListActivity.EXTRA_M3U_URL, url)
                putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_RECENT)
            })
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

        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)
        val btnRetry    = dialog.findViewById<MaterialButton>(R.id.btnRetry)

        btnRetry.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnRetry.isEnabled = false
            lifecycleScope.launch {
                val allowed = IpChecker.isAllowed()
                val ip      = IpChecker.getPublicIp() ?: "Unknown"
                progressBar.visibility = View.GONE
                btnRetry.isEnabled = true
                if (allowed) {
                    dialog.dismiss()
                    setupUi()
                    checkForUpdate()
                } else {
                    dialog.findViewById<TextView>(R.id.tvCurrentIp).text = "Your IP: $ip"
                    dialog.findViewById<TextView>(R.id.tvMessage).text =
                        "এই IP থেকে access permitted নয়।\nAllowed IP: ${IpChecker.ALLOWED_IP}"
                }
            }
        }
        dialog.show()
    }

    // ── Counts ────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        updateCounts()
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
