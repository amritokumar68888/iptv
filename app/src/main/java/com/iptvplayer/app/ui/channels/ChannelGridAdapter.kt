package com.iptvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iptvplayer.app.R
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.databinding.ItemChannelGridBinding

class ChannelGridAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelGridAdapter.ViewHolder>(Diff()) {

    private var focusedIndex: Int = -1

    fun setFocused(index: Int) {
        val old = focusedIndex
        focusedIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class ViewHolder(private val b: ItemChannelGridBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(channel: Channel, position: Int) {
            b.tvChannelName.text = channel.name
            // First letter avatar
            b.tvLetter.text = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            // EPG placeholder
            b.tvEpg.text = "No Program found"

            val isFocused = position == focusedIndex
            // Gradient border + "3D" lift on focus
            b.root.setBackgroundResource(
                if (isFocused) R.drawable.bg_channel_card_focused else R.drawable.bg_channel_card
            )
            val density = b.root.resources.displayMetrics.density
            b.root.animate()
                .scaleX(if (isFocused) 1.04f else 1f)
                .scaleY(if (isFocused) 1.04f else 1f)
                .setDuration(140)
                .start()
            b.root.elevation = if (isFocused) 10f * density else 0f

            b.root.setOnClickListener { onClick(channel) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemChannelGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id && a.url == b.url
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
