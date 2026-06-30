package com.iptvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.databinding.ItemChannelGridBinding

class ChannelGridAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelGridAdapter.ViewHolder>(Diff()) {

    inner class ViewHolder(private val b: ItemChannelGridBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(channel: Channel) {
            b.tvChannelName.text = channel.name
            // First letter avatar
            b.tvLetter.text = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            // EPG placeholder
            b.tvEpg.text = "No Program found"

            b.root.setOnClickListener { onClick(channel) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemChannelGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id && a.url == b.url
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
