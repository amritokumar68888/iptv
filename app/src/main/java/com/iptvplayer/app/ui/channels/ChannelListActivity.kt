package com.iptvplayer.app.ui.channels

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
        const val ASSET_PREFIX  = "asset://"
    }

    private lateinit var binding: ActivityChannelListBinding

    private var isGridView      = false
    private var isSearchVisible = false
    private var isSortedAsc     = true
    private var allChannels: List<Channel> = emptyList()
    private var channelList: List<Channel> = emptyList()
    private var focusedIndex    = 0   // currently focused channel index for remote

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

        // Toggle grid/list
        binding.btnToggleView.setOnClickListener {
            isGridView = !isGridView
            if (isGridView) setGridView() else setListView()
        }

        // Dropdown → same as toggle view
        binding.ivDropdown.setOnClickListener {
            isGridView = !isGridView
            if (isGridView) setGridView() else setListView()
        }

        // Sort button → sort by name A-Z / Z-A toggle
        binding.btnSortList.setOnClickListener {
            isSortedAsc = !isSortedAsc
            val sorted = if (isSortedAsc)
                channelList.sortedBy { it.name.uppercase() }
            else
                channelList.sortedByDescending { it.name.uppercase() }
            channelList = sorted
            allChannels = sorted
            showChannels(channelList)
            Toast.makeText(
                this,
                if (isSortedAsc) "A → Z" else "Z → A",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Search button toggle
        binding.btnSearch.setOnClickListener {
            isSearchVisible = !isSearchVisible
            if (isSearchVisible) {
                binding.searchBar.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            } else {
                closeSearch()
            }
        }

        // Clear search
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }

        // Search text watcher — live filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterChannels(s?.toString() ?: "")
            }
        })

        // Search keyboard action
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                true
            } else false
        }

        if (m3uUrl.isNotEmpty()) {
            loadChannels(m3uUrl, mode)
        }
    }

    private fun closeSearch() {
        isSearchVisible = false
        binding.searchBar.visibility = View.GONE
        binding.etSearch.text.clear()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        showChannels(channelList)
    }

    private fun filterChannels(query: String) {
        val filtered = if (query.isEmpty()) {
            allChannels
        } else {
            allChannels.filter { it.name.contains(query, ignoreCase = true) }
        }
        channelList = filtered
        showChannels(filtered)
        if (filtered.isEmpty() && query.isNotEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "\"$query\" পাওয়া যায়নি"
            binding.recyclerChannels.visibility = View.GONE
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

    private fun showChannels(list: List<Channel>) {
        if (list.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerChannels.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerChannels.visibility = View.VISIBLE
            if (isGridView) gridAdapter.submitList(list)
            else listAdapter.submitList(list)
        }
    }

    private fun loadChannels(url: String, mode: String) {
        binding.progressBar.visibility      = View.VISIBLE
        binding.recyclerChannels.visibility = View.GONE
        binding.tvEmpty.visibility          = View.GONE

        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    if (url.startsWith(ASSET_PREFIX)) {
                        val fileName = url.removePrefix(ASSET_PREFIX)
                        assets.open(fileName).bufferedReader().use { it.readText() }
                    } else {
                        val request = Request.Builder().url(url).build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                            response.body?.string() ?: throw Exception("Empty response")
                        }
                    }
                }

                val parsed = withContext(Dispatchers.Default) {
                    M3uParser.parse(content, 1L)
                }

                if (mode == MODE_ALL) {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit().putInt("channel_count", parsed.size).apply()
                }

                allChannels = when (mode) {
                    MODE_RECENT -> {
                        val recent = RecentManager.get(this@ChannelListActivity)
                        if (recent.isEmpty()) parsed.takeLast(14) else recent
                    }
                    else -> parsed
                }
                channelList = allChannels

                binding.progressBar.visibility = View.GONE
                showChannels(channelList)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility     = View.VISIBLE
                Toast.makeText(
                    this@ChannelListActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
            putStringArrayListExtra(
                PlayerActivity.EXTRA_CHANNEL_LIST_NAMES,
                ArrayList(channelList.map { it.name })
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_CHANNEL_LIST_URLS,
                ArrayList(channelList.map { it.url })
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_CHANNEL_LIST_LOGOS,
                ArrayList(channelList.map { it.logoUrl })
            )
        })
    }

    override fun onBackPressed() {
        if (isSearchVisible) {
            closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    // ── dispatchKeyEvent: ENTER কে DPAD_CENTER হিসেবে treat করো ─────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // ENTER → DPAD_CENTER
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, event)
            }
            return true
        }
        // সব key Activity-তে handle করব
        if (event.action == KeyEvent.ACTION_DOWN) {
            return onKeyDown(event.keyCode, event)
        }
        return true
    }

    // ── TV Remote Key Handling ────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            // OK / Enter → play focused channel
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (channelList.isNotEmpty()) {
                    openPlayer(channelList[focusedIndex])
                }
                true
            }

            // UP → আগের channel
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                if (channelList.isNotEmpty()) {
                    focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else 0
                    scrollAndHighlight(focusedIndex)
                }
                true
            }

            // DOWN → পরের channel
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (channelList.isNotEmpty()) {
                    focusedIndex = if (focusedIndex < channelList.size - 1)
                        focusedIndex + 1 else channelList.size - 1
                    scrollAndHighlight(focusedIndex)
                }
                true
            }

            // LEFT → grid/list toggle
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isGridView) { /* already list */ } else {
                    isGridView = false; setListView()
                }
                true
            }

            // RIGHT → grid/list toggle
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isGridView) { /* already grid */ } else {
                    isGridView = true; setGridView()
                }
                true
            }

            // Back
            KeyEvent.KEYCODE_BACK -> {
                if (isSearchVisible) { closeSearch(); true }
                else super.onKeyDown(keyCode, event)
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun scrollAndHighlight(index: Int) {
        binding.recyclerChannels.scrollToPosition(index)
        // Update selected highlight in adapter
        listAdapter.setFocused(index)
    }
}
