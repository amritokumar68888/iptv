package com.iptvplayer.app.ui.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.databinding.ActivityPlayerBinding
import com.iptvplayer.app.ui.channels.ChannelListAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private var channelName: String = ""
    private var channelUrl: String = ""
    private var currentIndex: Int = -1
    private var channelList: List<Channel> = emptyList()

    // true = landscape fullscreen, false = portrait windowed
    private var isLandscape = true

    private lateinit var overlayAdapter: ChannelListAdapter

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    companion object {
        const val EXTRA_CHANNEL_NAME        = "channel_name"
        const val EXTRA_CHANNEL_URL         = "channel_url"
        const val EXTRA_CHANNEL_LOGO        = "channel_logo"
        const val EXTRA_CHANNEL_INDEX       = "channel_index"
        const val EXTRA_CHANNEL_LIST_NAMES  = "channel_list_names"
        const val EXTRA_CHANNEL_LIST_URLS   = "channel_list_urls"
        const val EXTRA_CHANNEL_LIST_LOGOS  = "channel_list_logos"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start in landscape fullscreen
        goLandscape()

        channelName  = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        channelUrl   = intent.getStringExtra(EXTRA_CHANNEL_URL)  ?: ""
        currentIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, -1)

        val names  = intent.getStringArrayListExtra(EXTRA_CHANNEL_LIST_NAMES) ?: arrayListOf()
        val urls   = intent.getStringArrayListExtra(EXTRA_CHANNEL_LIST_URLS)  ?: arrayListOf()
        val logos  = intent.getStringArrayListExtra(EXTRA_CHANNEL_LIST_LOGOS) ?: arrayListOf()
        channelList = names.zip(urls).mapIndexed { idx, (name, url) ->
            Channel(id = idx.toLong(), name = name, url = url,
                logoUrl = logos.getOrElse(idx) { "" })
        }

        binding.tvChannelName.text = "${currentIndex + 1}-$channelName"
        updateTime()

        // Default logo — app logo দেখাবে
        binding.ivChannelLogo.setImageResource(R.drawable.logo)

        // ── Buttons ──────────────────────────────────────────────────────────
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChannelList.setOnClickListener { toggleChannelList() }
        binding.btnPrev.setOnClickListener { navigateChannel(-1) }
        binding.btnNext.setOnClickListener { navigateChannel(+1) }
        binding.btnEpgList.setOnClickListener { toggleChannelList() }
        binding.btnCloseOverlay.setOnClickListener { hideChannelList() }

        binding.btnPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) { p.pause(); binding.btnPlayPause.setImageResource(R.drawable.ic_play) }
                else             { p.play();  binding.btnPlayPause.setImageResource(R.drawable.ic_pause) }
            }
        }

    // Aspect / Fullscreen toggle button
    binding.btnAspect.setOnClickListener { cycleAspectMode() }

        binding.btnLock.setOnClickListener {
            hideControls()
            Toast.makeText(this, "Screen locked. Tap to unlock.", Toast.LENGTH_SHORT).show()
        }

        binding.btnSubtitle.setOnClickListener {
            Toast.makeText(this, "No subtitles available", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener { showQualityDialog() }

        // ── Touch ────────────────────────────────────────────────────────────
        val gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleControls(); return true
                }
            })
        binding.playerView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
        binding.root.setOnTouchListener      { _, event -> gestureDetector.onTouchEvent(event); true }

        // ── Channel overlay ───────────────────────────────────────────────────
        overlayAdapter = ChannelListAdapter { channel ->
            hideChannelList()
            switchToChannel(channelList.indexOf(channel))
        }
        binding.recyclerChannelList.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = overlayAdapter
        }
        overlayAdapter.submitList(channelList)
        overlayAdapter.selectedUrl = channelUrl

        if (channelUrl.isNotEmpty()) initializePlayer(channelUrl)
        else { Toast.makeText(this, getString(R.string.error_invalid_url), Toast.LENGTH_LONG).show(); finish() }

        showControls()
    }

    // ── Orientation / Fullscreen ──────────────────────────────────────────────

    private var aspectModeIndex = 0  // 0=zoom(full), 1=fit(letterbox), 2=fill(stretch)
    private val aspectModes = listOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val aspectLabels = listOf("Zoom (Full)", "Fit (Letterbox)", "Stretch")

    private fun cycleAspectMode() {
        aspectModeIndex = (aspectModeIndex + 1) % aspectModes.size
        binding.playerView.resizeMode = aspectModes[aspectModeIndex]
        Toast.makeText(this, aspectLabels[aspectModeIndex], Toast.LENGTH_SHORT).show()
    }

    private fun goLandscape() {
        isLandscape = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        hideSystemUi()
        binding.btnAspect.setImageResource(R.drawable.ic_fullscreen_exit)
    }

    private fun goPortrait() {
        isLandscape = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        showSystemUi()
        binding.btnAspect.setImageResource(R.drawable.ic_fullscreen)
    }

    private fun toggleOrientation() {
        if (isLandscape) goPortrait() else goLandscape()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isLandscape) hideSystemUi()
    }

    // ── Quality Dialog ────────────────────────────────────────────────────────

    private fun showQualityDialog() {
        val exo = player ?: run {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show(); return
        }
        val tracks      = exo.currentTracks
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

        val qualities = arrayOf("Auto", "1080p", "720p", "480p", "360p", "240p")
        val heights   = intArrayOf(0, 1080, 720, 480, 360, 240)

        if (videoGroups.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Video Quality")
                .setItems(qualities) { _, which ->
                    if (which == 0) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters
                            .buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    } else {
                        exo.trackSelectionParameters = exo.trackSelectionParameters
                            .buildUpon()
                            .setMaxVideoSize(Int.MAX_VALUE, heights[which])
                            .setMinVideoSize(0, 0).build()
                    }
                    Toast.makeText(this, "Quality: ${qualities[which]}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
            return
        }

        val labels = mutableListOf("Auto")
        videoGroups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                val fmt = group.getTrackFormat(ti)
                val br  = if (fmt.bitrate > 0) " (${fmt.bitrate / 1000}kbps)" else ""
                labels.add(if (fmt.height > 0) "${fmt.height}p$br" else "Track ${gi+1}-${ti+1}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Video Quality")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    Toast.makeText(this, "Quality: Auto", Toast.LENGTH_SHORT).show()
                } else {
                    var idx = which - 1
                    outer@ for (group in videoGroups) {
                        for (ti in 0 until group.length) {
                            if (idx-- == 0) {
                                exo.trackSelectionParameters = exo.trackSelectionParameters
                                    .buildUpon()
                                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, ti))
                                    .build()
                                Toast.makeText(this, "Quality: ${labels[which]}", Toast.LENGTH_SHORT).show()
                                break@outer
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── Controls visibility ───────────────────────────────────────────────────

    private fun showControls() {
        binding.topBar.visibility    = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideControls() {
        binding.topBar.visibility    = View.GONE
        binding.bottomBar.visibility = View.GONE
        resetButtonHighlights()
        focusedButtonIndex = 3 // reset to play button
    }

    private fun toggleControls() {
        if (binding.topBar.visibility == View.VISIBLE) {
            hideHandler.removeCallbacks(hideRunnable); hideControls()
        } else showControls()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 6000)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateChannel(direction: Int) {
        if (channelList.isEmpty() || currentIndex == -1) return
        switchToChannel((currentIndex + direction + channelList.size) % channelList.size)
    }

    private fun switchToChannel(index: Int) {
        if (index < 0 || index >= channelList.size) return
        currentIndex = index
        val ch = channelList[index]
        channelName = ch.name; channelUrl = ch.url
        binding.tvChannelName.text = "${currentIndex + 1}-$channelName"
        binding.tvError.visibility = View.GONE
        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)

        // Channel logo — channel-এর logo থাকলে সেটা, না হলে app logo
        if (ch.logoUrl.isNotEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(ch.logoUrl)
                .placeholder(R.drawable.logo)
                .error(R.drawable.logo)
                .fitCenter()
                .into(binding.ivChannelLogo)
        } else {
            binding.ivChannelLogo.setImageResource(R.drawable.logo)
        }

        player?.release(); player = null
        initializePlayer(ch.url)
        overlayAdapter.selectedUrl = ch.url
        binding.recyclerChannelList.scrollToPosition(currentIndex)
        showControls()
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun toggleChannelList() {
        if (binding.channelListOverlay.visibility == View.VISIBLE) hideChannelList()
        else showChannelList()
    }

    private fun showChannelList() {
        binding.channelListOverlay.visibility = View.VISIBLE
        if (currentIndex >= 0) binding.recyclerChannelList.scrollToPosition(currentIndex)
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun hideChannelList() { binding.channelListOverlay.visibility = View.GONE }

    // ── ExoPlayer ─────────────────────────────────────────────────────────────

    private fun initializePlayer(url: String) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    binding.progressBuffering.visibility =
                        if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY)
                        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
                override fun onPlayerError(error: PlaybackException) {
                    binding.progressBuffering.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = getString(R.string.error_playback, error.message)
                    showControls()
                    hideHandler.removeCallbacks(hideRunnable)
                }
            })
        }
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    // Bottom bar button IDs in order for remote navigation
    private val controlButtons by lazy {
        listOf(
            binding.btnLock,
            binding.btnAspect,
            binding.btnPrev,
            binding.btnPlayPause,
            binding.btnNext,
            binding.btnEpgList,
            binding.btnSubtitle,
            binding.btnSettings
        )
    }
    private var focusedButtonIndex = 3 // default: btnPlayPause

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            // ── OK / Enter → menu open OR focused button click ────────────────
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                when {
                    // Channel overlay খোলা → close
                    binding.channelListOverlay.visibility == View.VISIBLE -> {
                        hideChannelList()
                    }
                    // Controls দেখা আছে → focused button click
                    binding.bottomBar.visibility == View.VISIBLE -> {
                        controlButtons.getOrNull(focusedButtonIndex)?.performClick()
                        scheduleHide()
                    }
                    // Controls hidden → show করো
                    else -> showControls()
                }
                true
            }

            // ── MENU button → channel list toggle ─────────────────────────────
            KeyEvent.KEYCODE_MENU -> {
                toggleChannelList()
                true
            }

            // ── UP → আগের channel ─────────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                if (binding.channelListOverlay.visibility == View.VISIBLE) {
                    val lm = binding.recyclerChannelList.layoutManager
                            as? androidx.recyclerview.widget.LinearLayoutManager
                    val pos = (lm?.findFirstVisibleItemPosition() ?: 1) - 1
                    if (pos >= 0) binding.recyclerChannelList.smoothScrollToPosition(pos)
                } else {
                    navigateChannel(-1)
                }
                true
            }

            // ── DOWN → পরের channel ───────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (binding.channelListOverlay.visibility == View.VISIBLE) {
                    val lm = binding.recyclerChannelList.layoutManager
                            as? androidx.recyclerview.widget.LinearLayoutManager
                    val pos = (lm?.findLastVisibleItemPosition() ?: 0) + 1
                    if (pos < channelList.size) binding.recyclerChannelList.smoothScrollToPosition(pos)
                } else {
                    navigateChannel(+1)
                }
                true
            }

            // ── LEFT → আগের control button-এ focus ───────────────────────────
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (binding.channelListOverlay.visibility == View.VISIBLE) {
                    // overlay-এ থাকলে কিছু না
                } else {
                    if (binding.bottomBar.visibility != View.VISIBLE) {
                        showControls()
                    } else {
                        focusedButtonIndex = (focusedButtonIndex - 1 + controlButtons.size) % controlButtons.size
                        highlightButton(focusedButtonIndex)
                        scheduleHide()
                    }
                }
                true
            }

            // ── RIGHT → পরের control button-এ focus ──────────────────────────
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.channelListOverlay.visibility == View.VISIBLE) {
                    // overlay-এ থাকলে কিছু না
                } else {
                    if (binding.bottomBar.visibility != View.VISIBLE) {
                        showControls()
                    } else {
                        focusedButtonIndex = (focusedButtonIndex + 1) % controlButtons.size
                        highlightButton(focusedButtonIndex)
                        scheduleHide()
                    }
                }
                true
            }

            // ── Play/Pause ────────────────────────────────────────────────────
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                binding.btnPlayPause.performClick()
                true
            }

            // ── Volume keys → system ─────────────────────────────────────────
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> false

            // ── Back ──────────────────────────────────────────────────────────
            KeyEvent.KEYCODE_BACK -> {
                when {
                    binding.channelListOverlay.visibility == View.VISIBLE -> { hideChannelList(); true }
                    binding.bottomBar.visibility == View.VISIBLE -> { hideControls(); true }
                    else -> super.onKeyDown(keyCode, event)
                }
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Highlight the focused button with orange tint, reset others to white */
    private fun highlightButton(index: Int) {
        controlButtons.forEachIndexed { i, btn ->
            val color = if (i == index) getColor(R.color.colorOrange) else android.graphics.Color.WHITE
            btn.setColorFilter(color)
        }
    }

    /** Reset all button tints to white */
    private fun resetButtonHighlights() {
        controlButtons.forEach { it.setColorFilter(android.graphics.Color.WHITE) }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    @SuppressLint("SimpleDateFormat")
    private fun updateTime() {
        binding.tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        Handler(Looper.getMainLooper()).postDelayed({ updateTime() }, 30_000)
    }

    // ── PiP ───────────────────────────────────────────────────────────────────

    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        if (isInPiP) hideControls()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStart()   { super.onStart();   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player?.play() }
    override fun onStop()    { super.onStop();    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) player?.pause() }
    override fun onDestroy() { super.onDestroy(); hideHandler.removeCallbacksAndMessages(null); player?.release(); player = null }
}
