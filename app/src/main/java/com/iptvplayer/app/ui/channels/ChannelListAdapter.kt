package com.iptvplayer.app.ui.channels

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.data.logo.ChannelLogoStore
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

            // Channel icon: generated gradient initial-avatar, real logo on top if present
            b.tvLetter.visibility = View.GONE
            val avatar = ChannelAvatar.drawable(channel.name, b.root.context)
            b.ivLogo.setImageDrawable(avatar)
            val mapped = ChannelLogoStore.logoFor(b.root.context, channel.name)
            val logo = ChannelLogoStore.normalizeLogoUrl(channel.logoUrl.ifBlank { mapped ?: "" })
            if (logo.isNotEmpty()) {
                Glide.with(b.root.context)
                    .load(logo)
                    .placeholder(avatar)
                    .error(avatar)
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

    class Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
