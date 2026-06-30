package com.iptvplayer.app.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Window
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.iptvplayer.app.R
import com.iptvplayer.app.databinding.ActivityMainBinding
import com.iptvplayer.app.ui.channels.ChannelListActivity
import com.iptvplayer.app.ui.channels.RecentManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    // Hard-coded M3U URL — can be changed here
    private val m3uUrl = "https://raw.githubusercontent.com/amritokumar68888/iptvm3u/main/amrito.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        updateCounts()

        binding.rowAllChannels.setOnClickListener {
            startActivity(
                Intent(this, ChannelListActivity::class.java).apply {
                    putExtra(ChannelListActivity.EXTRA_TITLE, getString(R.string.all_channels))
                    putExtra(ChannelListActivity.EXTRA_M3U_URL, m3uUrl)
                    putExtra(ChannelListActivity.EXTRA_MODE, ChannelListActivity.MODE_ALL)
                }
            )
        }

        binding.rowRecentWatch.setOnClickListener {
            val recent = RecentManager.get(this)
            if (recent.isEmpty()) return@setOnClickListener
            startActivity(
                Intent(this, ChannelListActivity::class.java).apply {
                    putExtra(ChannelListActivity.EXTRA_TITLE, getString(R.string.recent_watch))
                    putExtra(ChannelListActivity.EXTRA_M3U_URL, m3uUrl)
                    putExtra(ChannelListActivity.EXTRA_MODE, ChannelListActivity.MODE_RECENT)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateCounts()
    }

    private fun updateCounts() {
        // Channel count will be shown after first load; start at 0 until cached
        val savedCount = prefs.getInt("channel_count", 0)
        if (savedCount > 0) binding.tvAllCount.text = savedCount.toString()
        binding.tvRecentCount.text = RecentManager.count(this).toString()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitDialog()
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
