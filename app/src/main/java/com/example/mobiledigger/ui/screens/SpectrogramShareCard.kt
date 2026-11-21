package com.example.mobiledigger.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.audio.SpectrogramInfo
import com.example.mobiledigger.model.MusicFile
import java.util.Locale

// ---------------------------------------------------------------- shared display helpers

/** Spectrogram colour map (must match AudioManager.powerToColor): 0 = −90 dB … 1 = 0 dB. */
internal val SPEK_COLORS = intArrayOf(
    android.graphics.Color.BLACK,
    android.graphics.Color.rgb(0, 0, 139),
    android.graphics.Color.rgb(138, 43, 226),
    android.graphics.Color.RED,
    android.graphics.Color.YELLOW
)
internal val SPEK_STOPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
internal const val SPEK_DB_RANGE = 90

/** Card / window colours taken from the active app theme (ARGB ints so the Canvas card can use them). */
data class SpekColors(
    val bg: Int, val panel: Int, val chip: Int, val stroke: Int,
    val text: Int, val muted: Int, val accent: Int, val warn: Int, val waveOff: Int, val onWarn: Int
)

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun spekColorsFromTheme(): SpekColors {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val s = cs.surface
    fun mix(c: androidx.compose.ui.graphics.Color, a: Float) =
        c.copy(alpha = a).compositeOver(s).toArgb()
    return SpekColors(
        bg = s.toArgb(),
        panel = mix(cs.surfaceVariant, 0.45f),
        chip = mix(cs.surfaceVariant, 0.8f),
        stroke = mix(cs.onSurface, 0.14f),
        text = cs.onSurface.toArgb(),
        muted = cs.onSurfaceVariant.toArgb(),
        accent = cs.primary.toArgb(),
        warn = 0xFFF59E0B.toInt(),            // cutoff = amber in every theme (it's a warning colour)
        waveOff = mix(cs.onSurface, 0.25f),
        onWarn = 0xFF1A1A1A.toInt()
    )
}

/** Time-axis step (seconds) giving about 6 labels for [seconds] of audio. */
internal fun timeStepFor(seconds: Float): Int =
    listOf(5, 10, 20, 30, 60, 120, 300, 600, 900).firstOrNull { seconds / it <= 7 } ?: 1200

internal fun formatKhz(hz: Int): String {
    val k = hz / 1000f
    if (hz == 0) return "0"
    return if (hz % 1000 == 0) "${hz / 1000}k" else String.format(Locale.US, "%.1fk", k)
}

internal fun formatKhzLong(hz: Int): String = String.format(Locale.US, "%.1f kHz", hz / 1000f)

internal fun formatClock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

internal fun channelLabel(ch: Int) = when (ch) { 1 -> "Mono"; 2 -> "Stereo"; else -> "$ch ch" }

/** Evenly spaced "nice" frequency ticks (0 … maxFreq) for a linear axis. */
internal fun frequencyTicks(maxFreqHz: Int): List<Int> {
    val step = listOf(1000, 2000, 4000, 5000, 10000).firstOrNull { maxFreqHz / it <= 6 } ?: 10000
    return (0..maxFreqHz step step).toList()
}

/** Best bitrate we can honestly show: container metadata for lossy, size/duration otherwise. */
internal fun displayBitrateKbps(info: SpectrogramInfo?, metaKbps: Int, sizeBytes: Long, durationMs: Long): Int {
    val lossy = info != null && info.bitsPerSample == null
    if (lossy && metaKbps > 0) return metaKbps
    if (durationMs > 0 && sizeBytes > 0) return (sizeBytes * 8 / durationMs).toInt()
    return metaKbps
}

// ---------------------------------------------------------------- share card

/**
 * Renders the shareable spectrogram card (1080 px wide, height fits the content).
 * Fixed dark "studio" palette so shared images look the same regardless of app theme.
 */
internal fun renderSpectrogramShareCard(
    spectrogram: ImageBitmap,
    musicFile: MusicFile,
    durationMs: Long,
    info: SpectrogramInfo?,
    metaBitrateKbps: Int,
    fallbackSampleRate: Int,
    fallbackChannels: Int,
    waveformData: IntArray?,
    colors: SpekColors
): Bitmap {
    val W = 1080
    val pad = 48f
    val inner = W - 2 * pad

    // Colours follow the app theme the user picked.
    val bg = colors.bg
    val panel = colors.panel
    val stroke = colors.stroke
    val text = colors.text
    val muted = colors.muted
    val accent = colors.accent
    val warn = colors.warn

    val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold = Typeface.create("sans-serif", Typeface.BOLD)

    fun tp(size: Float, color: Int, face: Typeface = regular, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align }

    val sampleRate = info?.sampleRate ?: fallbackSampleRate
    val channels = info?.channels ?: fallbackChannels
    val analyzed = info?.analyzedSeconds ?: (spectrogram.width / 10f)
    val maxFreq = info?.maxFreqHz ?: (sampleRate / 2).coerceAtLeast(1)
    val ext = musicFile.name.substringAfterLast('.', "").uppercase()
    val title = musicFile.name.substringBeforeLast('.')
    val bitrate = displayBitrateKbps(info, metaBitrateKbps, musicFile.size, durationMs)

    val maxH = 3600
    val bmp = Bitmap.createBitmap(W, maxH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(bg)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = stroke }

    fun panelRect(top: Float, height: Float): RectF {
        val r = RectF(pad, top, W - pad, top + height)
        fill.color = panel; c.drawRoundRect(r, 24f, 24f, fill)
        c.drawRoundRect(r, 24f, 24f, line)
        return r
    }

    var y = pad

    // ---- header strip
    val eyebrow = tp(24f, accent, bold).apply { letterSpacing = 0.18f }
    val brand = tp(24f, muted, medium, Paint.Align.RIGHT).apply { letterSpacing = 0.12f }
    y += 28f
    c.drawText("SPECTRUM ANALYSIS", pad, y, eyebrow)
    c.drawText("MOBILEDIGGER", W - pad, y, brand)
    y += 36f

    // ---- title (max 2 lines)
    // Full title, never truncated: step the size down for long names so they stay ≤ 3 lines,
    // and if it's still longer, just let it wrap onto more lines (card height grows).
    val titlePaint = tp(54f, text, bold)
    fun titleLayoutAt(size: Float): StaticLayout {
        titlePaint.textSize = size
        return StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, inner.toInt())
            .setLineSpacing(0f, 1.05f).build()
    }
    var titleLayout = titleLayoutAt(54f)
    for (size in floatArrayOf(48f, 42f, 38f)) {
        if (titleLayout.lineCount <= 3) break
        titleLayout = titleLayoutAt(size)
    }
    c.save(); c.translate(pad, y); titleLayout.draw(c); c.restore()
    y += titleLayout.height + 14f

    val subtitle = listOfNotNull(
        ext.ifBlank { null },
        if (durationMs > 0) formatClock(durationMs / 1000f) else null,
        if (musicFile.size > 0) formatBytes(musicFile.size) else null
    ).joinToString("  ·  ")
    val subPaint = tp(30f, muted, medium)
    y += 30f
    c.drawText(subtitle, pad, y, subPaint)
    y += 34f

    // ---- format chips (wrap)
    val chips = buildList {
        if (sampleRate > 0) add(formatKhzLong(sampleRate))
        when {
            info?.bitsPerSample != null -> add("${info.bitsPerSample}-bit${if (info.isFloat) " float" else ""}")
            info != null -> add("Lossy ${info.codec}".trim())
        }
        if (channels > 0) add(channelLabel(channels))
        if (bitrate > 0) add("$bitrate kbps")
    }
    val chipPaint = tp(28f, text, medium)
    val chipH = 58f
    var cx = pad
    for (chip in chips) {
        val w = chipPaint.measureText(chip) + 44f
        if (cx + w > W - pad) { cx = pad; y += chipH + 14f }
        val r = RectF(cx, y, cx + w, y + chipH)
        fill.color = colors.chip; c.drawRoundRect(r, chipH / 2, chipH / 2, fill)
        c.drawRoundRect(r, chipH / 2, chipH / 2, line)
        c.drawText(chip, cx + 22f, y + chipH / 2 - (chipPaint.ascent() + chipPaint.descent()) / 2, chipPaint)
        cx += w + 14f
    }
    y += chipH + 32f

    // ---- stat tiles
    data class Tile(val label: String, val value: String, val note: String, val color: Int)
    val cutoff = info?.cutoffHz
    val tiles = listOf(
        if (info == null) Tile("CUTOFF", "—", "not measured", muted)
        else if (cutoff != null) Tile("CUTOFF", "≈ ${formatKhzLong(cutoff)}", "sharp high-cut", warn)
        else Tile("CUTOFF", "None", "no sharp high-cut", accent),
        Tile("BANDWIDTH", if (info != null) "≈ ${formatKhzLong(info.bandwidthHz)}" else "—", "of ${formatKhzLong(maxFreq)} max", text),
        Tile("ANALYZED", formatClock(analyzed),
            if (durationMs > 0) "of ${formatClock(durationMs / 1000f)}" else "first ${AudioManager.SPECTROGRAM_MAX_SECONDS / 60} min", text)
    )
    val gap = 18f
    val tileW = (inner - gap * 2) / 3
    val tileH = 150f
    val tLabel = tp(22f, muted, bold).apply { letterSpacing = 0.14f }
    val tNote = tp(24f, muted)
    tiles.forEachIndexed { i, t ->
        val x = pad + i * (tileW + gap)
        val r = RectF(x, y, x + tileW, y + tileH)
        fill.color = panel; c.drawRoundRect(r, 22f, 22f, fill); c.drawRoundRect(r, 22f, 22f, line)
        fill.color = t.color; c.drawRoundRect(RectF(x + 22f, y + 24f, x + 30f, y + 48f), 4f, 4f, fill)
        c.drawText(t.label, x + 42f, y + 44f, tLabel)
        val vPaint = tp(42f, t.color, bold)
        var vs = 42f
        while (vPaint.measureText(t.value) > tileW - 44f && vs > 26f) { vs -= 2f; vPaint.textSize = vs }
        c.drawText(t.value, x + 22f, y + 98f, vPaint)
        c.drawText(t.note, x + 22f, y + 132f, tNote)
    }
    y += tileH + 28f

    // ---- spectrogram panel
    val axisPaint = tp(23f, muted, medium, Paint.Align.RIGHT)
    val leftGutter = 74f
    val rightGutter = 92f
    val plotTop = y + 30f
    val plotH = 620f
    val plotL = pad + leftGutter
    val plotR = W - pad - rightGutter
    val plotW = plotR - plotL
    val panelH = 30f + plotH + 64f
    panelRect(y, panelH)

    val src = spectrogram.asAndroidBitmap()
    val dst = RectF(plotL, plotTop, plotR, plotTop + plotH)
    c.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

    val grid = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1.5f }
    for (f in frequencyTicks(maxFreq)) {
        val gy = plotTop + plotH - plotH * f / maxFreq
        if (f > 0 && f < maxFreq) c.drawLine(plotL, gy, plotR, gy, grid)
        c.drawText(formatKhz(f), plotL - 12f, gy - (axisPaint.ascent() + axisPaint.descent()) / 2, axisPaint)
    }
    val timeStep = timeStepFor(analyzed)
    val timeAxis = tp(23f, muted, medium, Paint.Align.CENTER)
    var t = 0
    while (t <= analyzed.toInt()) {
        val gx = plotL + plotW * t / analyzed
        if (t > 0 && gx < plotR - 4f) c.drawLine(gx, plotTop, gx, plotTop + plotH, grid)
        if (gx < plotR - 40f || t == 0) c.drawText(formatClock(t.toFloat()), gx, plotTop + plotH + 36f, timeAxis)
        t += timeStep
    }
    c.drawText(formatClock(analyzed), plotR, plotTop + plotH + 36f, timeAxis)
    c.drawRect(dst, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = stroke })

    // cutoff marker
    if (cutoff != null && cutoff < maxFreq) {
        val cy = plotTop + plotH - plotH * cutoff / maxFreq
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = warn; strokeWidth = 3f; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
        }
        c.drawLine(plotL, cy, plotR, cy, dash)
        val tag = "cutoff ${formatKhz(cutoff)}"
        val tagPaint = tp(22f, colors.onWarn, bold)
        val tw = tagPaint.measureText(tag) + 20f
        val tagTop = (cy - 38f).coerceAtLeast(plotTop + 4f)
        val r = RectF(plotR - tw - 10f, tagTop, plotR - 10f, tagTop + 32f)
        fill.color = warn; c.drawRoundRect(r, 8f, 8f, fill)
        c.drawText(tag, r.left + 10f, r.centerY() - (tagPaint.ascent() + tagPaint.descent()) / 2, tagPaint)
    }

    // dB colour bar
    val barL = plotR + 22f
    val barR = barL + 16f
    fill.shader = LinearGradient(0f, plotTop + plotH, 0f, plotTop, SPEK_COLORS, SPEK_STOPS, Shader.TileMode.CLAMP)
    c.drawRoundRect(RectF(barL, plotTop, barR, plotTop + plotH), 6f, 6f, fill)
    fill.shader = null
    val dbPaint = tp(21f, muted, medium)
    for (db in 0 downTo -SPEK_DB_RANGE step 30) {
        val by = plotTop + plotH * (-db) / SPEK_DB_RANGE
        c.drawText("$db", barR + 8f, by - (dbPaint.ascent() + dbPaint.descent()) / 2, dbPaint)
    }
    y += panelH + 24f

    // ---- waveform of the analysed span, on the same x-range as the spectrogram plot
    if (waveformData != null && waveformData.isNotEmpty()) {
        val wfH = 130f
        val r = panelRect(y, wfH + 40f)
        val mid = y + 20f + wfH / 2
        val peak = (waveformData.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val fraction = if (durationMs > 0) (analyzed * 1000f / durationMs).coerceIn(0.01f, 1f) else 1f
        val end = (waveformData.size * fraction).toInt().coerceIn(1, waveformData.size)
        val on = Paint().apply { color = accent; strokeWidth = 2f }
        val ww = (plotR - plotL).toInt()
        val per = end.toFloat() / ww
        for (i in 0 until ww step 3) {
            val s0 = (i * per).toInt()
            val e0 = ((i + 3) * per).toInt().coerceIn(s0 + 1, end)
            var m = 0
            for (k in s0 until e0) if (waveformData[k] > m) m = waveformData[k]
            val a = (m / peak) * (wfH / 2)
            c.drawLine(plotL + i, mid - a, plotL + i, mid + a, on)
        }
        val note = tp(22f, muted, medium)
        c.drawText("Waveform of ${formatClock(0f)}–${formatClock(analyzed)}", plotL, r.bottom + 30f, note)
        y = r.bottom + 48f

        // Whole-track overview with a pointer at how far the spectrogram reaches
        if (durationMs > 0 && fraction < 0.995f) {
            val oh = 50f
            val omid = y + oh / 2
            val off = Paint().apply { color = colors.waveOff; strokeWidth = 2f }
            val per2 = waveformData.size.toFloat() / ww
            for (i in 0 until ww step 3) {
                val s0 = (i * per2).toInt()
                val e0 = ((i + 3) * per2).toInt().coerceIn(s0 + 1, waveformData.size)
                var m = 0
                for (k in s0 until e0) if (waveformData[k] > m) m = waveformData[k]
                val a = (m / peak) * (oh / 2)
                c.drawLine(plotL + i, omid - a, plotL + i, omid + a, if (i.toFloat() / ww <= fraction) on else off)
            }
            val px = plotL + ww * fraction
            val pointer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text; strokeWidth = 4f }
            c.drawLine(px, y, px, y + oh, pointer)
            val tri = android.graphics.Path().apply {
                moveTo(px, y + oh + 4f); lineTo(px - 13f, y + oh + 24f); lineTo(px + 13f, y + oh + 24f); close()
            }
            c.drawPath(tri, pointer)
            c.drawText("▲ Spectrogram shows the first ${formatClock(analyzed)} of ${formatClock(durationMs / 1000f)}", plotL, y + oh + 56f, note)
            y += oh + 76f
        }
    }

    // ---- footer
    c.drawLine(pad, y, W - pad, y, line)
    y += 46f
    val footL = tp(26f, text, medium)
    val footR = tp(22f, muted, regular, Paint.Align.RIGHT)
    fill.color = accent; c.drawCircle(pad + 8f, y - 9f, 8f, fill)
    c.drawText("MobileDigger for Android", pad + 28f, y, footL)
    c.drawText("groovy's child", W - pad, y, footR)
    y += 38f
    val fine = tp(21f, muted)
    val wholeTrack = durationMs > 0 && analyzed * 1000f >= durationMs - 1500
    val fineText = "Linear frequency scale · ${SPEK_DB_RANGE} dB range · " +
        if (wholeTrack) "whole track analyzed" else "first ${formatClock(analyzed)} analyzed"
    val fineLayout = StaticLayout.Builder.obtain(fineText, 0, fineText.length, fine, inner.toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    c.save(); c.translate(pad, y - 18f); fineLayout.draw(c); c.restore()
    y += fineLayout.height + pad - 10f

    val finalH = y.toInt().coerceAtMost(maxH)
    val out = Bitmap.createBitmap(bmp, 0, 0, W, finalH)
    if (out != bmp) bmp.recycle()
    return out
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
