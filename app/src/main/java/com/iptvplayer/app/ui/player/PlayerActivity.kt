package com.iptvplayer.app.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.databinding.ActivityPlayerBinding
import com.iptvplayer.app.ui.channels.ChannelListAdapter
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private lateinit var overlayAdapter: ChannelListAdapter

    // Auto-hide controls after 4 seconds
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    companion object {
        const val EXTRA_CHANNEL_NAME       = "channel_name"
        const val EXTRA_CHANNEL_URL        = "channel_url"
        const val EXTRA_CHANNEL_LOGO       = "channel_logo"
        const val EXTRA_CHANNEL_INDEX      = "channel_index"
        const val EXTRA_CHANNEL_LIST_NAMES = "channel_list_names"
        const val EXTRA_CHANNEL_LIST_URLS  = "channel_list_urls"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelName  = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        channelUrl   = intent.getStringExtra(EXTRA_CHANNEL_URL)  ?: ""
        currentIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, -1)

        val names = intent.getStringArrayListExtra(EXTRA_CHANNEL_LIST_NAMES) ?: arrayListOf()
        val urls  = intent.getStringArrayListExtra(EXTRA_CHANNEL_LIST_URLS)  ?: arrayListOf()
        channelList = names.zip(urls).mapIndexed { idx, (name, url) ->
            Channel(id = idx.toLong(), name = name, url = url)
        }

        binding.tvChannelName.text = "${currentIndex + 1}-${channelName}"
        updateTime()

        // Button listeners
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChannelList.setOnClickListener { toggleChannelList() }
        binding.btnPrev.setOnClickListener { navigateChannel(-1) }
        binding.btnNext.setOnClickListener { navigateChannel(+1) }
        binding.btnEpgList.setOnClickListener { toggleChannelList() }
        binding.btnCloseOverlay.setOnClickListener { hideChannelList() }
        binding.btnPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                } else {
                    p.play()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
            }
        }
        binding.btnLock.setOnClickListener {
            // Lock/unlock - hide controls when locked
            Toast.makeText(this, "Screen locked", Toast.LENGTH_SHORT).show()
        }
        binding.btnAspect.setOnClickListener {
            Toast.makeText(this, "Aspect ratio", Toast.LENGTH_SHORT).show()
        }
        binding.btnSubtitle.setOnClickListener {
            Toast.makeText(this, "Subtitles", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
        }

        // Tap anywhere on screen to show/hide controls
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }
        })

        // Touch on playerView
        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Touch on the root layout (black area)
        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Channel list overlay adapter
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

        if (channelUrl.isNotEmpty()) {
            initializePlayer(channelUrl)
        } else {
            Toast.makeText(this, getString(R.string.error_invalid_url), Toast.LENGTH_LONG).show()
            finish()
        }

        // Show controls briefly on start
        showControls()
    }

    // ── Controls visibility ──────────────────────────────────────────────────

    private fun showControls() {
        binding.topBar.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideControls() {
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
    }

    private fun toggleControls() {
        if (binding.topBar.visibility == View.VISIBLE) {
            hideHandler.removeCallbacks(hideRunnable)
            hideControls()
        } else {
            showControls()
        }
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 6000) // 6 seconds
    }

    // ── Channel navigation ───────────────────────────────────────────────────

    private fun navigateChannel(direction: Int) {
        if (channelList.isEmpty() || currentIndex == -1) return
        val newIndex = (currentIndex + direction + channelList.size) % channelList.size
        switchToChannel(newIndex)
    }

    private fun switchToChannel(index: Int) {
        if (index < 0 || index >= channelList.size) return
        currentIndex = index
        val channel  = channelList[index]
        channelName  = channel.name
        channelUrl   = channel.url
        binding.tvChannelName.text = "${currentIndex + 1}-${channelName}"
        binding.tvError.visibility = View.GONE
        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        player?.release()
        player = null
        initializePlayer(channel.url)
        overlayAdapter.selectedUrl = channel.url
        binding.recyclerChannelList.scrollToPosition(currentIndex)
        showControls()
    }

    // ── Channel list overlay ─────────────────────────────────────────────────

    private fun toggleChannelList() {
        if (binding.channelListOverlay.visibility == View.VISIBLE) hideChannelList()
        else showChannelList()
    }

    private fun showChannelList() {
        binding.channelListOverlay.visibility = View.VISIBLE
        if (currentIndex >= 0) binding.recyclerChannelList.scrollToPosition(currentIndex)
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun hideChannelList() {
        binding.channelListOverlay.visibility = View.GONE
    }

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    private fun initializePlayer(url: String) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    binding.progressBuffering.visibility = when (state) {
                        Player.STATE_BUFFERING -> View.VISIBLE
                        else -> View.GONE
                    }
                    if (state == Player.STATE_READY) {
                        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    binding.progressBuffering.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = getString(R.string.error_playback, error.message)
                    // Error হলে controls সবসময় দেখাবে যাতে channel change করা যায়
                    showControls()
                    // Auto-hide বন্ধ রাখি error state-এ
                    hideHandler.removeCallbacks(hideRunnable)
                }
            })
        }
    }

    // ── Remote key handling ───────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { toggleChannelList(); true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { navigateChannel(-1); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { navigateChannel(+1); true }
            KeyEvent.KEYCODE_BACK -> {
                if (binding.channelListOverlay.visibility == View.VISIBLE) {
                    hideChannelList(); true
                } else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                binding.btnPlayPause.performClick(); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ── Time clock ────────────────────────────────────────────────────────────

    @SuppressLint("SimpleDateFormat")
    private fun updateTime() {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.tvTime.text = sdf.format(Date())
        Handler(Looper.getMainLooper()).postDelayed({ updateTime() }, 30_000)
    }

    // ── PiP ───────────────────────────────────────────────────────────────────

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) hideControls()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player?.play()
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
