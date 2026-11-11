package com.iptvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.databinding.ItemChannelListBinding

class ChannelListAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelListAdapter.ViewHolder>(Diff()) {

    var selectedUrl: String = ""
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var focusedIndex: Int = -1

    fun setFocused(index: Int) {
        val old = focusedIndex
        focusedIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class ViewHolder(private val b: ItemChannelListBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(channel: Channel, position: Int) {
            b.tvNumber.text = (position + 1).toString()
            b.tvChannelName.text = channel.name

            val isPlaying = channel.url == selectedUrl
            val isFocused = position == focusedIndex

            // Gradient highlight + 4-side border + "3D" lift on focus
            b.root.setBackgroundResource(
                when {
                    isPlaying -> R.drawable.bg_channel_item_playing
                    isFocused -> R.drawable.bg_channel_item_focused
                    else      -> R.drawable.bg_channel_item_default
                }
            )
            val density = b.root.resources.displayMetrics.density
            b.root.animate()
                .scaleX(if (isFocused) 1.03f else 1f)
                .scaleY(if (isFocused) 1.03f else 1f)
                .setDuration(140)
                .start()
            b.root.elevation = if (isFocused) 10f * density else 0f

            // Channel icon: gradient first-letter avatar; real logo (if any) drawn on top
            b.tvLetter.text = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            b.ivLogo.setImageDrawable(null)
            val logo = normalizeLogo(channel.logoUrl)
            if (logo.isNotEmpty()) {
                Glide.with(b.root.context)
                    .load(logo)
                    .fitCenter()
                    .timeout(8000)
                    .into(b.ivLogo)
            }

            // Click handler on root
            b.root.setOnClickListener { onClick(channel) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemChannelListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    /**
     * Some playlists store an Imgur *page* link (imgur.com/ID.png) instead of the
     * direct image (i.imgur.com/ID.png), so the logo never loads. Normalize it.
     */
    private fun normalizeLogo(url: String): String {
        val u = url.trim()
        if (u.isEmpty()) return ""
        if (u.contains("imgur.com/") && !u.contains("i.imgur.com/")) {
            return u.replace("imgur.com/", "i.imgur.com/")
        }
        return u
    }

    class Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
