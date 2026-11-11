package com.iptvplayer.app.ui.channels

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
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

            // Channel icon: generated gradient initial-avatar, real logo on top if present
            b.tvLetter.visibility = View.GONE
            val avatar = avatarDrawable(channel.name, b.root.context)
            b.ivLogo.setImageDrawable(avatar)
            val logo = normalizeLogo(channel.logoUrl)
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

    // ── Generated channel avatar (unique gradient + initial) ─────────────────

    private val palette = arrayOf(
        intArrayOf(0xFFFF9A3C.toInt(), 0xFFE8388A.toInt()),
        intArrayOf(0xFF5B4BD6.toInt(), 0xFF1B9AAA.toInt()),
        intArrayOf(0xFF00796B.toInt(), 0xFF2E7D32.toInt()),
        intArrayOf(0xFFC62828.toInt(), 0xFFAD1457.toInt()),
        intArrayOf(0xFF283593.toInt(), 0xFF6A1B9A.toInt()),
        intArrayOf(0xFF6D4C41.toInt(), 0xFFE65100.toInt())
    )
    private val avatarCache = HashMap<String, Bitmap>()

    private fun avatarDrawable(name: String, context: android.content.Context): BitmapDrawable {
        val px = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(72)
        val key = "$name|$px"
        val bitmap = avatarCache.getOrPut(key) { drawAvatar(name, px) }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawAvatar(name: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = palette[((name.hashCode() % palette.size) + palette.size) % palette.size]

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                colors[0], colors[1], Shader.TileMode.CLAMP
            )
        }
        val radius = size / 4.5f
        canvas.drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bg
        )

        val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.52f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val metrics = text.fontMetrics
        val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(letter, size / 2f, y, text)
        return bmp
    }

    class Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
