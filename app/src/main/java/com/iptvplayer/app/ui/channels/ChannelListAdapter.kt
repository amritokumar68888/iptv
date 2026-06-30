package com.iptvplayer.app.ui.channels

import android.graphics.Color
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

            // Highlight: playing = dark red, focused by remote = orange tint
            when {
                channel.url == selectedUrl ->
                    b.root.setBackgroundColor(Color.parseColor("#8B0000"))
                position == focusedIndex ->
                    b.root.setBackgroundColor(Color.parseColor("#1AE87722"))
                else ->
                    b.root.setBackgroundColor(Color.TRANSPARENT)
            }

            // Logo
            if (channel.logoUrl.isNotEmpty()) {
                Glide.with(b.root.context)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_tv)
                    .error(R.drawable.ic_tv)
                    .fitCenter()
                    .timeout(8000)
                    .into(b.ivLogo)
            } else {
                b.ivLogo.setImageResource(R.drawable.ic_tv)
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
