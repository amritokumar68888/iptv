package com.iptvplayer.app.ui.channels

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptvplayer.app.R
import com.iptvplayer.app.data.health.StreamHealthChecker
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.data.parser.M3uParser
import com.iptvplayer.app.databinding.ActivityChannelListBinding
import com.iptvplayer.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ChannelListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE   = "title"
        const val EXTRA_M3U_URL = "m3u_url"
        const val MODE_ALL      = "all"
        const val MODE_RECENT   = "recent"
        const val EXTRA_MODE    = "mode"
        const val EXTRA_OPEN_SEARCH = "open_search"
        const val EXTRA_SORT    = "sort_mode"
        const val ASSET_PREFIX  = "asset://"

        const val SORT_NAME_ASC  = "name_asc"
        const val SORT_NAME_DESC = "name_desc"
        const val SORT_GROUP     = "group"
    }

    private lateinit var binding: ActivityChannelListBinding

    private var isGridView      = false
    private var isSearchVisible = false
    private var allChannels: List<Channel> = emptyList()
    private var channelList: List<Channel> = emptyList()

    // Remote health check (dead channel বাদ দেওয়ার জন্য)
    private var healthJob: Job? = null
    private val hiddenUrls = mutableSetOf<String>()   // যেগুলো dead পাওয়া গেছে / cache-এ dead
    private var currentQuery = ""

    // Remote navigation focus index
    private var focusedIndex = 0

    // ── Sort / group filter / toolbar focus ─────────────────────────────────
    private enum class SortMode { NAME_ASC, NAME_DESC, GROUP }
    private var sortMode = SortMode.NAME_ASC
    private var groupFilter: String? = null          // null = সব group
    private var baseTitle: String = ""

    /** -1 = channel list-এ focus, 0..N = toolbar-এর button-এ focus (remote) */
    private var toolbarFocus = -1

    private val toolbarItems: List<View> by lazy {
        listOf(binding.btnBack, binding.btnSearch, binding.btnToggleView,
            binding.btnSortList, binding.ivDropdown)
    }

    private lateinit var listAdapter: ChannelListAdapter
    private lateinit var gridAdapter: ChannelGridAdapter

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title  = intent.getStringExtra(EXTRA_TITLE)  ?: getString(R.string.all_channels)
        val m3uUrl = intent.getStringExtra(EXTRA_M3U_URL) ?: ""
        val mode   = intent.getStringExtra(EXTRA_MODE)   ?: MODE_ALL
        val openSearchOnStart = intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)

        sortMode = when (intent.getStringExtra(EXTRA_SORT)) {
            SORT_NAME_DESC -> SortMode.NAME_DESC
            SORT_GROUP     -> SortMode.GROUP
            else           -> SortMode.NAME_ASC
        }

        baseTitle = title
        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        listAdapter = ChannelListAdapter { channel -> openPlayer(channel) }
        gridAdapter = ChannelGridAdapter { channel -> openPlayer(channel) }

        // RecyclerView — disable built-in focus so we manage it manually
        binding.recyclerChannels.descendantFocusability =
            android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.recyclerChannels.isFocusable = false

        setListView()

        // Remote channel-logo map (raw GitHub + CDN fallback) — load in background
        lifecycleScope.launch {
            if (com.iptvplayer.app.data.logo.ChannelLogoStore.refresh(this@ChannelListActivity)) {
                listAdapter.notifyDataSetChanged()
                gridAdapter.notifyDataSetChanged()
            }
        }

        binding.btnToggleView.setOnClickListener {
            isGridView = !isGridView
            if (isGridView) setGridView() else setListView()
        }

        binding.btnSearch.setOnClickListener {
            if (isSearchVisible) closeSearch() else openSearch()
        }

        binding.btnSortList.setOnClickListener { showSortDialog() }
        binding.ivDropdown.setOnClickListener { showGroupFilterDialog() }

        binding.btnClearSearch.setOnClickListener { binding.etSearch.text.clear() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterChannels(s?.toString() ?: "") }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                true
            } else false
        }

        if (m3uUrl.isNotEmpty()) loadChannels(m3uUrl, mode)
        if (openSearchOnStart) openSearch()
    }

    // ── Remote key handling ───────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isSearchVisible) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (toolbarFocus >= 0) exitToolbarFocus() else moveFocus(+1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                when {
                    toolbarFocus >= 0 -> { /* toolbar-এ আছি, উপরে কিছু নেই */ }
                    focusedIndex == 0  -> enterToolbarFocus()
                    else               -> moveFocus(-1)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (toolbarFocus >= 0) moveToolbarFocus(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (toolbarFocus >= 0) moveToolbarFocus(+1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                when {
                    toolbarFocus >= 0 -> {
                        toolbarItems.getOrNull(toolbarFocus)?.performClick()
                        exitToolbarFocus()
                    }
                    channelList.isNotEmpty() -> openPlayer(channelList[focusedIndex])
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (toolbarFocus >= 0) exitToolbarFocus()
                else finish()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    // ── Toolbar focus (remote) ────────────────────────────────────────────────

    private fun enterToolbarFocus() {
        toolbarFocus = 0
        HighlightToolbar()
    }

    private fun exitToolbarFocus() {
        toolbarFocus = -1
        resetToolbarHighlight()
        listAdapter.setFocused(focusedIndex)
        gridAdapter.setFocused(focusedIndex)
        binding.recyclerChannels.scrollToPosition(focusedIndex)
    }

    private fun moveToolbarFocus(direction: Int) {
        if (toolbarItems.isEmpty()) return
        toolbarFocus = (toolbarFocus + direction + toolbarItems.size) % toolbarItems.size
        HighlightToolbar()
    }

    private fun HighlightToolbar() {
        val orange = ContextCompat.getColor(this, R.color.colorOrange)
        val normal = ContextCompat.getColor(this, R.color.colorChannelName)
        toolbarItems.forEachIndexed { i, v ->
            val focused = i == toolbarFocus
            (v as? ImageView)?.apply {
                setColorFilter(if (focused) orange else normal)
                setBackgroundResource(
                    if (focused) R.drawable.bg_icon_chip_active else R.drawable.bg_icon_chip
                )
            }
        }
    }

    private fun resetToolbarHighlight() {
        val normal = ContextCompat.getColor(this, R.color.colorChannelName)
        toolbarItems.forEach { (it as? ImageView)?.apply {
            setColorFilter(normal)
            setBackgroundResource(R.drawable.bg_icon_chip)
        } }
    }

    private fun moveFocus(direction: Int) {
        if (channelList.isEmpty()) return
        val newIndex = (focusedIndex + direction).coerceIn(0, channelList.size - 1)
        focusedIndex = newIndex
        listAdapter.setFocused(focusedIndex)
        gridAdapter.setFocused(focusedIndex)
        binding.recyclerChannels.scrollToPosition(focusedIndex)
    }

    // ── Search / sort / group filter ──────────────────────────────────────────

    private fun openSearch() {
        isSearchVisible = true
        toolbarFocus = -1
        resetToolbarHighlight()
        binding.searchBar.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        isSearchVisible = false
        toolbarFocus = -1
        resetToolbarHighlight()
        binding.searchBar.visibility = View.GONE
        binding.etSearch.text.clear()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        // সরাসরি allChannels দেখালে dead channel ফিরে আসত — তাই filter দিয়ে যাও
        filterChannels("")
    }

    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_group)
        )
        val modes = arrayOf(SortMode.NAME_ASC, SortMode.NAME_DESC, SortMode.GROUP)
        val checked = modes.indexOf(sortMode)

        AlertDialog.Builder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                sortMode = modes[which]
                dialog.dismiss()
                filterChannels(currentQuery)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showGroupFilterDialog() {
        val groups = allChannels.map { it.group }.distinct()
            .sortedBy { it.lowercase() }
        val items = ArrayList<String>(groups.size + 1)
        items.add(getString(R.string.all_groups))
        items.addAll(groups)
        val checked = groupFilter?.let { g -> groups.indexOf(g) + 1 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.filter_group)
            .setSingleChoiceItems(items.toTypedArray(), checked) { dialog, which ->
                groupFilter = if (which == 0) null else groups[which - 1]
                dialog.dismiss()
                filterChannels(currentQuery)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sorted(list: List<Channel>): List<Channel> = when (sortMode) {
        SortMode.NAME_ASC  -> list.sortedBy { it.name.lowercase() }
        SortMode.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
        SortMode.GROUP     -> list.sortedWith(
            compareBy({ it.group.lowercase() }, { it.name.lowercase() })
        )
    }

    private fun filterChannels(query: String) {
        currentQuery = query

        var base = allChannels
        groupFilter?.let { g -> base = base.filter { it.group == g } }
        if (query.isNotEmpty()) {
            base = base.filter { it.name.contains(query, ignoreCase = true) }
        }

        val filtered = sorted(base).filterNot { it.url in hiddenUrls }
        channelList = filtered
        focusedIndex = 0
        listAdapter.setFocused(0)
        gridAdapter.setFocused(0)
        showChannels(filtered)

        binding.tvTitle.text =
            if (groupFilter != null) "$baseTitle · $groupFilter" else baseTitle

        binding.tvEmpty.text = if (filtered.isEmpty() && query.isNotEmpty()) {
            "\"$query\" পাওয়া যায়নি"
        } else {
            getString(R.string.no_channels)
        }
    }

    // ── Stream health check (dead channel বাদ) ────────────────────────────────

    /**
     * List দেখানোর আগে/পরে dead channel ছেঁকে বাদ দেয়।
     *
     *  ১) cache-এ যেগুলো dead লেখা আছে — সাথে সাথে বাদ
     *  ২) বাকিগুলো background-এ check হয়; যেই fail করে সেই সাথে বাদ পড়ে
     */
    private fun applyHealthFilterAndVerify() {
        if (allChannels.isEmpty()) return

        // ১) আগে থেকে জানা dead গুলো
        val knownDead = allChannels.filter { StreamHealthChecker.knownDead(this, it.url) }
        if (knownDead.isNotEmpty()) {
            hiddenUrls.addAll(knownDead.map { it.url })
            filterChannels(currentQuery)
        }

        // ২) নতুনগুলো check করো
        healthJob?.cancel()
        val toCheck = allChannels.filterNot { it.url in hiddenUrls }
        if (toCheck.isEmpty()) return

        val total = minOf(toCheck.size, StreamHealthChecker.MAX_CHECKS)
        binding.tvChecking.visibility = View.VISIBLE
        binding.tvChecking.text = getString(R.string.checking_streams, 0, total)

        healthJob = lifecycleScope.launch {
            val dead = StreamHealthChecker.verifyAll(
                context = this@ChannelListActivity,
                channels = toCheck
            ) { checked, all, deadChannel ->
                withContext(Dispatchers.Main) {
                    binding.tvChecking.text = getString(R.string.checking_streams, checked, all)

                    if (StreamHealthChecker.knownDead(this@ChannelListActivity, deadChannel.url)) {
                        hiddenUrls.add(deadChannel.url)
                        filterChannels(currentQuery)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                hiddenUrls.addAll(dead.map { it.url })
                filterChannels(currentQuery)

                val working = allChannels.count { it.url !in hiddenUrls }
                if (working == 0) {
                    binding.tvChecking.text = getString(R.string.no_working_channels)
                } else if (dead.isEmpty()) {
                    binding.tvChecking.visibility = View.GONE
                } else {
                    binding.tvChecking.text =
                        getString(R.string.streams_checked, working, allChannels.size)
                }
            }
        }
    }

    // ── View modes ────────────────────────────────────────────────────────────

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
            // Focus first item initially
            listAdapter.setFocused(focusedIndex)
            gridAdapter.setFocused(focusedIndex)
        }
    }

    // ── Load channels ─────────────────────────────────────────────────────────

    private fun loadChannels(url: String, mode: String) {
        binding.progressBar.visibility      = View.VISIBLE
        binding.recyclerChannels.visibility = View.GONE
        binding.tvEmpty.visibility          = View.GONE

        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    when {
                        url.startsWith(ASSET_PREFIX) -> {
                            val fileName = url.removePrefix(ASSET_PREFIX)
                            assets.open(fileName).bufferedReader().use { it.readText() }
                        }
                        url.startsWith("file://") -> {
                            File(url.removePrefix("file://")).readText()
                        }
                        else -> {
                            val request = Request.Builder().url(url).build()
                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                                response.body?.string() ?: throw Exception("Empty response")
                            }
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
                hiddenUrls.clear()
                currentQuery = ""
                // Default = All groups; user চাইলে dropdown থেকে filter করবে
                groupFilter = null
                focusedIndex = 0

                binding.progressBar.visibility = View.GONE
                filterChannels("")

                // dead channel গুলো background-এ বাদ দাও
                applyHealthFilterAndVerify()

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility     = View.VISIBLE
                Toast.makeText(
                    this@ChannelListActivity, "Error: ${e.message}", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Open player ───────────────────────────────────────────────────────────

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
        })
    }

    override fun onBackPressed() {
        if (isSearchVisible) closeSearch() else super.onBackPressed()
    }
}
