package com.iptvplayer.app.ui.channels

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.data.parser.M3uParser
import com.iptvplayer.app.databinding.ActivityChannelListBinding
import com.iptvplayer.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ChannelListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE   = "title"
        const val EXTRA_M3U_URL = "m3u_url"
        const val MODE_ALL      = "all"
        const val MODE_RECENT   = "recent"
        const val EXTRA_MODE    = "mode"
    }

    private lateinit var binding: ActivityChannelListBinding

    private var isGridView = false
    private var channelList: List<Channel> = emptyList()

    private lateinit var listAdapter: ChannelListAdapter
    private lateinit var gridAdapter: ChannelGridAdapter

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title  = intent.getStringExtra(EXTRA_TITLE)  ?: getString(R.string.all_channels)
        val m3uUrl = intent.getStringExtra(EXTRA_M3U_URL) ?: ""
        val mode   = intent.getStringExtra(EXTRA_MODE)   ?: MODE_ALL

        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        listAdapter = ChannelListAdapter { channel -> openPlayer(channel) }
        gridAdapter = ChannelGridAdapter { channel -> openPlayer(channel) }

        setListView()

        binding.btnToggleView.setOnClickListener {
            isGridView = !isGridView
            if (isGridView) setGridView() else setListView()
        }

        if (m3uUrl.isNotEmpty()) {
            loadChannels(m3uUrl, mode)
        }
    }

    private fun setListView() {
        binding.btnToggleView.setImageResource(R.drawable.ic_grid)
        binding.recyclerChannels.layoutManager = LinearLayoutManager(this)
        binding.recyclerChannels.adapter = listAdapter
        if (channelList.isNotEmpty()) listAdapter.submitList(channelList)
    }

    private fun setGridView() {
        binding.btnToggleView.setImageResource(R.drawable.ic_list)
        binding.recyclerChannels.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerChannels.adapter = gridAdapter
        if (channelList.isNotEmpty()) gridAdapter.submitList(channelList)
    }

    private fun loadChannels(url: String, mode: String) {
        binding.progressBar.visibility    = View.VISIBLE
        binding.recyclerChannels.visibility = View.GONE
        binding.tvEmpty.visibility         = View.GONE

        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        response.body?.string() ?: throw Exception("Empty response")
                    }
                }

                val allChannels = withContext(Dispatchers.Default) {
                    M3uParser.parse(content, 1L)
                }

                // Save total count to SharedPrefs for home screen display
                if (mode == MODE_ALL) {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit().putInt("channel_count", allChannels.size).apply()
                }

                channelList = when (mode) {
                    MODE_RECENT -> {
                        val recent = RecentManager.get(this@ChannelListActivity)
                        if (recent.isEmpty()) allChannels.takeLast(14) else recent
                    }
                    else -> allChannels
                }

                binding.progressBar.visibility = View.GONE

                if (channelList.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.recyclerChannels.visibility = View.VISIBLE
                    if (isGridView) gridAdapter.submitList(channelList)
                    else listAdapter.submitList(channelList)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                Toast.makeText(this@ChannelListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPlayer(channel: Channel) {
        RecentManager.add(this, channel)

        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME,  channel.name)
            putExtra(PlayerActivity.EXTRA_CHANNEL_URL,   channel.url)
            putExtra(PlayerActivity.EXTRA_CHANNEL_LOGO,  channel.logoUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, channelList.indexOf(channel))
            putStringArrayListExtra(PlayerActivity.EXTRA_CHANNEL_LIST_NAMES,
                ArrayList(channelList.map { it.name }))
            putStringArrayListExtra(PlayerActivity.EXTRA_CHANNEL_LIST_URLS,
                ArrayList(channelList.map { it.url }))
        })
    }
}
