package com.example.mobiledigger.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

/**
 * Waveforms the app already computed, keyed by track URI, so the notification can use them as
 * cover art (alone, or drawn over the track's own cover) without a second decode. Small, in-memory, same process.
 */
object WaveformShare {
    private const val MAX = 6
    private val cache = object : LinkedHashMap<String, IntArray>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > MAX
    }

    @Synchronized fun put(uri: String, samples: IntArray) { if (samples.isNotEmpty()) cache[uri] = samples }
    @Synchronized fun get(uri: String): IntArray? = cache[uri]

    // Where the system player's seek bar lands on the picture. The Android 13+ media card scales
    // the art to the card's width and centres it vertically; measured on a Pixel (Android 16):
    // the track runs from 13.0% to 66.1% of the width, at 62.5% of the height.
    private const val SEEK_START = 0.130f
    private const val SEEK_END = 0.661f
    private const val SEEK_Y = 0.6255f
    // Half-height of the tallest bar: stays clear of the artist line above and the card edge below
    private const val MAX_HALF = 0.056f

    /**
     * Square picture of the waveform laid exactly under the notification's seek bar, mirrored
     * around the seek line, so the moving dot shows where you are in the track.
     * [samples] are 0..100. With a [cover], the cover is the background (cropped to a square
     * and darkened, most of all around the seek line) and the waveform is drawn over it.
     */
    fun render(samples: IntArray, size: Int = 1024, cover: Bitmap? = null): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (cover != null && cover.width > 0 && cover.height > 0) {
            // Centre-crop the cover to a square and fill the picture with it
            val side = minOf(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            c.drawBitmap(cover, src, RectF(0f, 0f, size.toFloat(), size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            // Darken: a little everywhere, a lot in the band around the seek line, so the bars
            // (and the card's own text) stay readable on any cover.
            val bandTop = size * (SEEK_Y - MAX_HALF * 2.2f)
            val bandBot = size * (SEEK_Y + MAX_HALF * 2.2f)
            c.drawPaint(Paint().apply { color = 0x59000000 })
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, size.toFloat(),
                    intArrayOf(0x00000000, 0x00000000, 0x99000000.toInt(), 0x99000000.toInt(), 0x00000000),
                    floatArrayOf(0f, bandTop / size - 0.08f, bandTop / size, bandBot / size, (bandBot / size + 0.08f).coerceAtMost(1f)),
                    Shader.TileMode.CLAMP)
            })
        } else {
            c.drawPaint(Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF111827.toInt(), 0xFF0B1020.toInt(), Shader.TileMode.CLAMP)
            })
        }
        if (samples.isEmpty()) return bmp

        val x0 = size * SEEK_START
        val x1 = size * SEEK_END
        val mid = size * SEEK_Y
        val maxH = size * MAX_HALF
        // ~2.7 px per bar on the card; each bar is the loudest sample in its slice
        val bars = 150.coerceAtMost(samples.size)
        val step = (x1 - x0) / bars
        val barW = step * 0.6f
        val peak = samples.maxOrNull()?.coerceAtLeast(1) ?: 1
        // Mastered club tracks sit near full level everywhere; stretch from the quiet parts
        // (5th percentile, softened) to the peak so breakdowns and drops are visible.
        val lo = (samples.sorted()[samples.size / 20] * 0.6f).coerceAtMost(peak * 0.8f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x0, 0f, x1, 0f, 0xFF22C55E.toInt(), 0xFF22D3EE.toInt(), Shader.TileMode.CLAMP)
        }
        val r = RectF()
        for (i in 0 until bars) {
            val a = i * samples.size / bars
            val b = ((i + 1) * samples.size / bars).coerceAtLeast(a + 1).coerceAtMost(samples.size)
            var v = 0
            for (j in a until b) if (samples[j] > v) v = samples[j]
            val h = (maxH * (0.08f + 0.92f * ((v - lo) / (peak - lo)).coerceIn(0f, 1f))).coerceAtLeast(size * 0.003f)
            // Bar centred on its time slot, so bar i sits under the seek dot at that moment
            val x = x0 + i * step + (step - barW) / 2
            r.set(x, mid - h, x + barW, mid + h)
            c.drawRoundRect(r, barW / 2, barW / 2, paint)
        }
        return bmp
    }
}
