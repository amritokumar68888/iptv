package com.iptvplayer.app.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
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

    // ── M3U Source — Google Drive থেকে auto-update হবে ──────────────────────
    // M3uUpdater.kt-এ DRIVE_URL-এ আপনার Google Drive FILE_ID বসান
    // Local asset শুধু fallback হিসেবে থাকবে
    private var m3uUrl = "asset://amrito.m3u"  // default fallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Background-এ m3u update check করো
        lifecycleScope.launch {
            try {
                M3uUpdater.checkAndUpdate(applicationContext)
                // Cache ready → cached URL use করো
                m3uUrl = "cached://amrito_cache.m3u"
            } catch (e: Exception) {
                // Fallback to asset
                m3uUrl = "asset://amrito.m3u"
            }
        }

        // IP check করে তারপর UI setup করব
        checkIpAndInit()
    }

    // ── IP Verification ───────────────────────────────────────────────────────

    private fun checkIpAndInit() {
        // Show loading state
        binding.rowAllChannels.isEnabled  = false
        binding.rowRecentWatch.isEnabled  = false

        lifecycleScope.launch {
            val allowed = IpChecker.isAllowed()
            if (allowed) {
                // ✅ IP allowed — setup normal UI
                setupUi()
            } else {
                // ❌ IP blocked — show block screen
                val ip = IpChecker.getPublicIp() ?: "Unknown"
                showIpBlockedDialog(ip)
            }
        }
    }

    // ── Normal UI Setup ───────────────────────────────────────────────────────

    private fun setupUi() {
        binding.rowAllChannels.isEnabled  = true
        binding.rowRecentWatch.isEnabled  = true

        updateCounts()

        binding.rowAllChannels.setOnClickListener {
            startActivity(
                Intent(this, ChannelListActivity::class.java).apply {
                    putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.all_channels))
                    putExtra(ChannelListActivity.EXTRA_M3U_URL, m3uUrl)
                    putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_ALL)
                }
            )
        }

        binding.rowRecentWatch.setOnClickListener {
            val recent = RecentManager.get(this)
            if (recent.isEmpty()) return@setOnClickListener
            startActivity(
                Intent(this, ChannelListActivity::class.java).apply {
                    putExtra(ChannelListActivity.EXTRA_TITLE,   getString(R.string.recent_watch))
                    putExtra(ChannelListActivity.EXTRA_M3U_URL, m3uUrl)
                    putExtra(ChannelListActivity.EXTRA_MODE,    ChannelListActivity.MODE_RECENT)
                }
            )
        }
    }

    // ── IP Blocked Dialog ─────────────────────────────────────────────────────

    private fun showIpBlockedDialog(currentIp: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.activity_ip_blocked)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        // Set current IP
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
    override fun onBackPressed() {
        showExitDialog()
    }

    // ── dispatchKeyEvent: ENTER = DPAD_CENTER ────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            val newEvent = KeyEvent(
                event.downTime, event.eventTime,
                event.action, KeyEvent.KEYCODE_DPAD_CENTER,
                event.repeatCount, event.metaState
            )
            return super.dispatchKeyEvent(newEvent)
        }
        return super.dispatchKeyEvent(event)
    }

    // ── TV Remote Key Handling ────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            // OK / Enter / D-pad center → open All Channels
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (binding.rowAllChannels.isFocused || binding.rowAllChannels.hasFocus()) {
                    binding.rowAllChannels.performClick()
                } else if (binding.rowRecentWatch.isFocused || binding.rowRecentWatch.hasFocus()) {
                    binding.rowRecentWatch.performClick()
                } else {
                    binding.rowAllChannels.performClick()
                }
                true
            }
            // D-pad down → focus recent watch
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                binding.rowRecentWatch.requestFocus()
                true
            }
            // D-pad up → focus all channels
            KeyEvent.KEYCODE_DPAD_UP -> {
                binding.rowAllChannels.requestFocus()
                true
            }
            // Back → exit dialog
            KeyEvent.KEYCODE_BACK -> {
                showExitDialog(); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showExitDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_exit)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)

        dialog.findViewById<Button>(R.id.btnYes).setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }
        dialog.findViewById<Button>(R.id.btnNo).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
