package com.example.mobiledigger.ui.screens
import com.example.mobiledigger.util.CrashLogger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.model.MusicFile
import androidx.compose.ui.viewinterop.AndroidView
import com.masoudss.lib.WaveformSeekBar
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import java.util.Locale

// Data class for audio properties
data class AudioProperties(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val bitrate: Int
)

// Function to extract audio properties from music file
private fun extractAudioProperties(musicFile: MusicFile?, context: Context): AudioProperties {
    if (musicFile == null) {
        return AudioProperties(0, 0, 0, 0)
    }
    
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, musicFile.uri)
        
        val sampleRate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
        val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000) ?: 0
        val channels = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0
        
        retriever.release()
        
        // Try to get bit depth from file extension or estimate
        val bitDepth = when (musicFile.name.lowercase().substringAfterLast('.')) {
            "wav", "aif", "aiff" -> 16 // Most common for uncompressed
            "flac" -> 24 // FLAC often uses 24-bit
            "mp3" -> 16 // MP3 is typically 16-bit
            else -> 16 // Default assumption
        }
        
        // Try to get actual channel count
        val actualChannels = try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, musicFile.uri, emptyMap<String, String>())
            
            var channelCount = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        break
                    }
                }
            }
            extractor.release()
            if (channelCount > 0) channelCount else 2 // Default to stereo
        } catch (e: Exception) {
            2 // Default to stereo
        }
        
        AudioProperties(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = actualChannels,
            bitrate = bitrate
        )
    } catch (e: Exception) {
        // Fallback values
        AudioProperties(
            sampleRate = 44100,
            bitDepth = 16,
            channels = 2,
            bitrate = 0
        )
    }
}

// Studio palette shared with the share card (renderSpectrogramShareCard) so the in-app
// view and the shared image look the same, independent of the app theme.
// (follows the active app theme — see spekColorsFromTheme)
private val SpekBg: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().bg)
private val SpekPanel: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().panel)
private val SpekChip: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().chip)
private val SpekStroke: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().stroke)
private val SpekText: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().text)
private val SpekMuted: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().muted)
private val SpekAccent: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().accent)
private val SpekWarn: Color @Composable @ReadOnlyComposable get() = Color(spekColorsFromTheme().warn)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpectrogramPopupScreen(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    onDismiss: () -> Unit,
    spectrogramBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isLoading: Boolean,
    onShare: () -> Unit,
    actualDuration: Long,
    waveformData: IntArray? = null,
    isConverting: Boolean = false,
    conversionProgress: Float = 0f,
    isGeneratingSpectrogram: Boolean = false,
    spectrogramProgress: Float = 0f,
    spectrogramInfo: com.example.mobiledigger.audio.SpectrogramInfo? = null
) {
    val context = LocalContext.current
    // Metadata lookup once per file (was re-run on every recomposition, on the main thread).
    val metaProps = remember(musicFile?.uri) { extractAudioProperties(musicFile, context) }
    val configuration = LocalConfiguration.current
    val info = spectrogramInfo

    val fileName = musicFile?.name ?: ""
    val title = fileName.substringBeforeLast('.', fileName)
    val ext = fileName.substringAfterLast('.', "").uppercase()
    val sampleRate = info?.sampleRate ?: metaProps.sampleRate
    val channels = info?.channels ?: metaProps.channels
    val bitrate = displayBitrateKbps(info, metaProps.bitrate, musicFile?.size ?: 0L, actualDuration)
    val analyzed = info?.analyzedSeconds ?: spectrogramBitmap?.let { it.width / 10f }
        ?: if (actualDuration > 0) minOf(actualDuration / 1000f, AudioManager.SPECTROGRAM_MAX_SECONDS.toFloat())
           else AudioManager.SPECTROGRAM_MAX_SECONDS.toFloat()
    val maxFreq = info?.maxFreqHz ?: (sampleRate.takeIf { it > 0 }?.div(2) ?: 22050)
    val wholeTrack = actualDuration > 0 && analyzed * 1000f >= actualDuration - 1500

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (configuration.screenHeightDp - 24).dp)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = SpekBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, SpekStroke)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ---- top bar
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "SPECTRUM ANALYSIS",
                        color = SpekAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onShare, enabled = spectrogramBitmap != null && !isLoading) {
                        Icon(Icons.Default.Share, contentDescription = "Share spectrogram",
                            tint = if (spectrogramBitmap != null && !isLoading) SpekText else SpekMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SpekText)
                    }
                }

                // ---- full title (never truncated) + subtitle
                Text(
                    text = title,
                    color = SpekText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        ext.ifBlank { null },
                        if (actualDuration > 0) formatClock(actualDuration / 1000f) else null,
                        if ((musicFile?.size ?: 0L) > 0) formatFileSize(musicFile!!.size) else null
                    ).joinToString("  ·  "),
                    color = SpekMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))

                // ---- format chips
                val chips = buildList {
                    if (sampleRate > 0) add(formatKhzLong(sampleRate))
                    when {
                        info?.bitsPerSample != null -> add("${info.bitsPerSample}-bit${if (info.isFloat) " float" else ""}")
                        info != null -> add("Lossy ${info.codec}".trim())
                    }
                    if (channels > 0) add(channelLabel(channels))
                    if (bitrate > 0) add("$bitrate kbps")
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { chip ->
                        Text(
                            chip, color = SpekText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SpekChip)
                                .border(1.dp, SpekStroke, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ---- stat tiles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val cutoff = info?.cutoffHz
                    SpekTile(
                        label = "CUTOFF",
                        value = when { info == null -> "…"; cutoff != null -> "≈ ${formatKhzLong(cutoff)}"; else -> "None" },
                        note = when { info == null -> "measuring"; cutoff != null -> "sharp high-cut"; else -> "no sharp high-cut" },
                        color = when { info == null -> SpekMuted; cutoff != null -> SpekWarn; else -> SpekAccent },
                        modifier = Modifier.weight(1f)
                    )
                    SpekTile(
                        label = "BANDWIDTH",
                        value = info?.let { "≈ ${formatKhzLong(it.bandwidthHz)}" } ?: "…",
                        note = "of ${formatKhzLong(maxFreq)} max",
                        color = SpekText,
                        modifier = Modifier.weight(1f)
                    )
                    SpekTile(
                        label = "ANALYZED",
                        value = formatClock(analyzed),
                        note = if (actualDuration > 0) "of ${formatClock(actualDuration / 1000f)}" else "max 3 min",
                        color = SpekText,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ---- spectrogram panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SpekPanel)
                        .border(1.dp, SpekStroke, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Analyzing spectrum…", color = SpekText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            LinearProgressIndicator(
                                progress = { spectrogramProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = SpekAccent, trackColor = SpekStroke
                            )
                            Text("${(spectrogramProgress * 100).toInt()}%", color = SpekMuted, fontSize = 12.sp)
                        }
                    } else if (spectrogramBitmap != null) {
                        SpekPlot(spectrogramBitmap, maxFreq, analyzed, info?.cutoffHz)
                    } else {
                        Text("No spectrogram available", color = SpekMuted, fontSize = 14.sp)
                    }
                }

                // ---- waveform (whole track, analyzed span highlighted)
                if (waveformData != null && waveformData.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val fraction = if (actualDuration > 0) (analyzed * 1000f / actualDuration).coerceIn(0f, 1f) else 1f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SpekPanel)
                            .border(1.dp, SpekStroke, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        val waveOn = SpekAccent
                        val waveOff = Color(spekColorsFromTheme().waveOff)
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            val step = 3.dp.toPx()
                            val bars = (size.width / step).toInt().coerceAtLeast(1)
                            val peak = (waveformData.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                            val per = waveformData.size.toFloat() / bars
                            val mid = size.height / 2
                            for (i in 0 until bars) {
                                val s = (i * per).toInt()
                                val e = ((i + 1) * per).toInt().coerceIn(s + 1, waveformData.size)
                                var m = 0
                                for (k in s until e) if (waveformData[k] > m) m = waveformData[k]
                                val a = (m / peak) * mid
                                val x = i * step + step / 2
                                drawLine(
                                    color = if (i.toFloat() / bars <= fraction) waveOn else waveOff,
                                    start = androidx.compose.ui.geometry.Offset(x, mid - a),
                                    end = androidx.compose.ui.geometry.Offset(x, mid + a),
                                    strokeWidth = step * 0.6f
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (wholeTrack) "whole track analyzed" else "▲ analyzed: first ${formatClock(analyzed)}",
                            color = SpekMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }

                // ---- footnote
                Spacer(Modifier.height(10.dp))
                Text(
                    "Linear frequency scale · $SPEK_DB_RANGE dB range · " +
                        if (wholeTrack) "whole track analyzed" else "first ${formatClock(analyzed)} analyzed (max ${AudioManager.SPECTROGRAM_MAX_SECONDS / 60} min)",
                    color = SpekMuted, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SpekTile(label: String, value: String, note: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SpekPanel)
            .border(1.dp, SpekStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 11.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, color = SpekMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(note, color = SpekMuted, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
    }
}

/** Spectrogram + real linear kHz axis, time axis, gridlines, cutoff marker and dB colour bar. */
@Composable
private fun SpekPlot(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    maxFreq: Int,
    analyzed: Float,
    cutoffHz: Int?
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pal = spekColorsFromTheme()
    val mutedArgb = pal.muted
    val warnCol = Color(pal.warn)
    val onWarnArgb = pal.onWarn
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedArgb
            textSize = with(density) { 10.sp.toPx() }
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val leftG = with(density) { 30.dp.toPx() }
        val rightG = with(density) { 34.dp.toPx() }
        val bottomG = with(density) { 18.dp.toPx() }
        val topPad = with(density) { 6.dp.toPx() }
        val l = leftG
        val r = size.width - rightG
        val t = topPad
        val b = size.height - bottomG
        val w = r - l
        val h = b - t
        val nc = drawContext.canvas.nativeCanvas

        drawImage(
            image = bitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
            dstOffset = androidx.compose.ui.unit.IntOffset(l.toInt(), t.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
        )
        val grid = Color.White.copy(alpha = 0.2f)
        val half = (label.ascent() + label.descent()) / 2

        label.textAlign = android.graphics.Paint.Align.RIGHT
        for (f in frequencyTicks(maxFreq)) {
            val y = b - h * f / maxFreq
            if (f in 1 until maxFreq) drawLine(grid, androidx.compose.ui.geometry.Offset(l, y), androidx.compose.ui.geometry.Offset(r, y), 1f)
            nc.drawText(formatKhz(f), l - 6f, (y - half).coerceIn(t - half * 2, b), label)
        }

        label.textAlign = android.graphics.Paint.Align.CENTER
        val step = when { analyzed > 150 -> 30; analyzed > 60 -> 20; analyzed > 20 -> 10; else -> 5 }
        val ty = b + with(density) { 13.dp.toPx() }
        var s = 0
        val endLabelW = label.measureText(formatClock(analyzed))
        while (s < analyzed) {
            val x = l + w * s / analyzed
            if (s > 0) drawLine(grid, androidx.compose.ui.geometry.Offset(x, t), androidx.compose.ui.geometry.Offset(x, b), 1f)
            if (x < r - endLabelW) nc.drawText(formatClock(s.toFloat()), x, ty, label)
            s += step
        }
        nc.drawText(formatClock(analyzed), r, ty, label)

        if (cutoffHz != null && cutoffHz < maxFreq) {
            val y = b - h * cutoffHz / maxFreq
            drawLine(
                warnCol, androidx.compose.ui.geometry.Offset(l, y), androidx.compose.ui.geometry.Offset(r, y),
                strokeWidth = with(density) { 1.5.dp.toPx() },
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
            )
            val tag = "cutoff ${formatKhz(cutoffHz)}"
            val tp = android.graphics.Paint(label).apply {
                color = onWarnArgb; textAlign = android.graphics.Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val pad = with(density) { 5.dp.toPx() }
            val tw = tp.measureText(tag) + pad * 2
            val th = with(density) { 16.dp.toPx() }
            val top = (y - th - 4f).coerceAtLeast(t + 2f)
            drawRoundRect(warnCol, androidx.compose.ui.geometry.Offset(r - tw - 6f, top),
                androidx.compose.ui.geometry.Size(tw, th), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            nc.drawText(tag, r - tw - 6f + pad, top + th / 2 - (tp.ascent() + tp.descent()) / 2, tp)
        }

        // dB colour bar
        val barL = r + with(density) { 6.dp.toPx() }
        val barW = with(density) { 6.dp.toPx() }
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                SPEK_COLORS.reversed().map { Color(it) }, startY = t, endY = b
            ),
            topLeft = androidx.compose.ui.geometry.Offset(barL, t),
            size = androidx.compose.ui.geometry.Size(barW, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2, barW / 2)
        )
        label.textAlign = android.graphics.Paint.Align.LEFT
        for (db in 0 downTo -SPEK_DB_RANGE step 30) {
            val y = t + h * (-db) / SPEK_DB_RANGE
            nc.drawText("$db", barL + barW + 4f, (y - half).coerceIn(t - half * 2, b), label)
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun getFileExtension(fileName: String): String {
    return fileName.substringAfterLast('.', "Unknown").uppercase()
}

@Composable
fun SpectrogramPopupDialog(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    onDismiss: () -> Unit,
    waveformData: IntArray? = null
) {
    var spectrogramBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var spectrogramInfo by remember { mutableStateOf<com.example.mobiledigger.audio.SpectrogramInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Track conversion and spectrogram generation progress
    val isConverting by audioManager.isConverting.collectAsState()
    val conversionProgress by audioManager.conversionProgress.collectAsState()
    val isGeneratingSpectrogram by audioManager.isGeneratingSpectrogram.collectAsState()
    val spectrogramProgress by audioManager.spectrogramProgress.collectAsState()
    
    // Generate spectrogram when dialog opens
    LaunchedEffect(musicFile?.uri) {
        if (musicFile != null) {
            CrashLogger.log("Debug", "SpectrogramPopupDialog: Starting spectrogram generation for ${musicFile.name}")
            isLoading = true
            try {
                val bitmap = audioManager.generateSpectrogram(musicFile)
                CrashLogger.log("Debug", "SpectrogramPopupDialog: Received bitmap: ${bitmap != null}")
                spectrogramBitmap = bitmap
                spectrogramInfo = audioManager.getSpectrogramInfo(musicFile)
            } catch (e: Exception) {
                CrashLogger.log("Debug", "SpectrogramPopupDialog: Exception during generation: ${e.message}")
                e.printStackTrace()
                spectrogramBitmap = null
            } finally {
                isLoading = false
                CrashLogger.log("Debug", "SpectrogramPopupDialog: Generation completed, isLoading = false")
            }
        }
    }
    
    // Extract duration if not available
    val actualDuration = remember(musicFile?.uri) {
        // Always try to extract duration, but use existing duration if available and > 0
        val existingDuration = musicFile?.duration ?: 0L
        if (existingDuration > 0) {
            existingDuration
        } else {
            // Try multiple methods to extract duration
            try {
                // Method 1: MediaMetadataRetriever
                val retriever = android.media.MediaMetadataRetriever()
                musicFile?.uri?.let { uri ->
                    retriever.setDataSource(context, uri)
                }
                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                val durationMs = duration?.toLong() ?: 0L
                
                if (durationMs > 0) {
                    durationMs
                } else {
                    // Method 2: MediaExtractor for AIF files and other formats
                    try {
                        val extractor = android.media.MediaExtractor()
                        musicFile?.uri?.let { uri ->
                            extractor.setDataSource(context, uri, emptyMap<String, String>())
                        }
                        
                        var totalDuration = 0L
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                            if (mime?.startsWith("audio/") == true) {
                                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                                    val trackDuration = format.getLong(android.media.MediaFormat.KEY_DURATION)
                                    totalDuration = maxOf(totalDuration, trackDuration)
                                }
                            }
                        }
                        extractor.release()
                        totalDuration
                    } catch (e2: Exception) {
                        // Method 3: Estimate from file size for AIF files
                        if (musicFile?.name?.lowercase()?.endsWith(".aif") == true || musicFile?.name?.lowercase()?.endsWith(".aiff") == true) {
                            // Rough estimate: AIF files are typically uncompressed
                            // Assume 44.1kHz, 16-bit, stereo = 176,400 bytes per second
                            val bytesPerSecond = 44100 * 2 * 2 // sample rate * channels * bytes per sample
                            ((musicFile?.size ?: 0L) * 1000L) / bytesPerSecond
                        } else {
                            0L
                        }
                    }
                }
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    // Share: render the spectrogram card off the main thread, then hand it to the chooser.
    val cardColors = spekColorsFromTheme() // read in composition; used by the share card below
    fun shareSpectrogram() {
        val bmp = spectrogramBitmap ?: return
        val file0 = musicFile ?: return
        scope.launch {
            try {
                val outFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val props = extractAudioProperties(file0, context)
                    val card = renderSpectrogramShareCard(
                        spectrogram = bmp,
                        musicFile = file0,
                        durationMs = actualDuration,
                        info = spectrogramInfo,
                        metaBitrateKbps = props.bitrate,
                        fallbackSampleRate = props.sampleRate,
                        fallbackChannels = props.channels,
                        waveformData = waveformData,
                        colors = cardColors
                    )
                    val dir = File(context.cacheDir, "shared_spek").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val f = File(dir, "spek_${file0.name.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9]+"), "_")}.png")
                    FileOutputStream(f).use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    card.recycle()
                    f
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", outFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Spectrogram: ${file0.name}")
                    putExtra(Intent.EXTRA_SUBJECT, "Spectrogram – ${file0.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share spectrogram"))
            } catch (e: Exception) {
                CrashLogger.log("Debug", "Spectrogram share failed", e)
            }
        }
    }
    
    SpectrogramPopupScreen(
        musicFile = musicFile,
        audioManager = audioManager,
        onDismiss = onDismiss,
        spectrogramBitmap = spectrogramBitmap,
        isLoading = isLoading,
        onShare = { shareSpectrogram() },
        actualDuration = actualDuration,
        waveformData = waveformData,
        isConverting = isConverting,
        conversionProgress = conversionProgress,
        isGeneratingSpectrogram = isGeneratingSpectrogram,
        spectrogramProgress = spectrogramProgress,
        spectrogramInfo = spectrogramInfo
    )
}
