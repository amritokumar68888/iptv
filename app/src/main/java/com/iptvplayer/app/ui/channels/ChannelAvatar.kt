package com.iptvplayer.app.ui.channels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable

/**
 * Channel logo না থাকলে নামের প্রথম অক্ষর দিয়ে gradient avatar আঁকে।
 * List ও grid — দুই জায়গাতেই ব্যবহার হয়, তাই cache shared।
 */
object ChannelAvatar {

    private val palette = arrayOf(
        intArrayOf(0xFFFF9A3C.toInt(), 0xFFE8388A.toInt()),
        intArrayOf(0xFF5B4BD6.toInt(), 0xFF1B9AAA.toInt()),
        intArrayOf(0xFF00796B.toInt(), 0xFF2E7D32.toInt()),
        intArrayOf(0xFFC62828.toInt(), 0xFFAD1457.toInt()),
        intArrayOf(0xFF283593.toInt(), 0xFF6A1B9A.toInt()),
        intArrayOf(0xFF6D4C41.toInt(), 0xFFE65100.toInt())
    )

    private val cache = HashMap<String, Bitmap>()

    fun drawable(name: String, context: Context): BitmapDrawable {
        val px = (52 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val key = "$name|$px"
        val bitmap = cache.getOrPut(key) { draw(name, px) }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun draw(name: String, size: Int): Bitmap {
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
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bg)

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
}
