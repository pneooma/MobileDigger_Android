package com.example.mobiledigger.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.ui.components.SpectrogramView
import androidx.compose.ui.viewinterop.AndroidView
import com.masoudss.lib.WaveformSeekBar
import android.graphics.Typeface

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
    spectrogramProgress: Float = 0f
) {
    val context = LocalContext.current
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
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with centered title and buttons on sides
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side - Share button
                    IconButton(
                        onClick = onShare
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Spectrogram",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Center - Title
                    Text(
                        text = "Spectrogram Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Right side - Close button
                    IconButton(
                        onClick = {
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // File details section with two columns
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Left column - File Information
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "File Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // File details
                            musicFile?.let { file ->
                                Text(
                                    text = "Name: ${file.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Text(
                                    text = "Duration: ${formatTime(actualDuration / 1000f)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = "Size: ${formatFileSize(file.size)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = "Format: ${getFileExtension(file.name)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Right column - Audio Properties
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Audio Properties",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Extract and display audio properties
                            val audioProperties = extractAudioProperties(musicFile, context)
                            
                            Text(
                                text = "Sample Rate: ${audioProperties.sampleRate} Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "Bit Depth: ${audioProperties.bitDepth} bits",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "Channels: ${audioProperties.channels}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "Bitrate: ${audioProperties.bitrate} kbps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Large spectrogram display with external axis labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(432.dp) // 20% taller (360 * 1.2 = 432)
                ) {
                    // Y-axis labels (kHz scale) - left side
                    Column(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val freqLabels = listOf("22k", "18k", "14k", "10k", "6k", "2k", "1k", "20")
                        freqLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Spectrogram image container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (isConverting) {
                                        // Show progress for MP3 conversion
                                        Text(
                                            text = "Converting MP3 to WAV...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LinearProgressIndicator(
                                            progress = conversionProgress,
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            text = "${(conversionProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (isGeneratingSpectrogram || (conversionProgress >= 1f && !isConverting)) {
                                        // Show progress for spectrogram generation
                                        Text(
                                            text = if (conversionProgress >= 1f && !isConverting && spectrogramProgress < 1f) 
                                                "Generating Spectrogram..." 
                                            else 
                                                "Generating Spectrogram...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LinearProgressIndicator(
                                            progress = spectrogramProgress,
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            text = "${(spectrogramProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // Show general loading for other stages
                                        Text(
                                            text = "Loading...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else if (spectrogramBitmap != null) {
                            Image(
                                bitmap = spectrogramBitmap,
                                contentDescription = "Spectrogram",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No spectrogram available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Y-axis labels (dB scale) - right side
                    Column(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val dbLabels = listOf("0", "-12", "-24", "-36", "-48", "-60")
                        dbLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Waveform Section - Centered below spectrogram with matching width
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Take remaining space
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Match the spectrogram container width (same as spectrogram Row)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left spacer to match spectrogram Y-axis labels
                        Spacer(modifier = Modifier.width(30.dp))
                        
                        // Waveform card with same width as spectrogram
                        Card(
                            modifier = Modifier
                                .weight(1f) // Same weight as spectrogram image
                                .height(132.dp), // 10% taller (120 * 1.1 = 132)
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                            if (waveformData != null) {
                                // Use the same WaveformSeekBar component as the main player
                                val playedColor = MaterialTheme.colorScheme.primary.toArgb()
                                val unplayedColor = Color.Gray.toArgb()
                                
                                AndroidView(
                                    factory = { ctx ->
                                        WaveformSeekBar(ctx).apply {
                                            // Configure WaveformSeekBar appearance to match main player
                                            waveBackgroundColor = unplayedColor
                                            waveProgressColor = playedColor
                                            waveWidth = 1f
                                            waveGap = 0f
                                            waveCornerRadius = 2f
                                            wavePaddingTop = 1
                                            wavePaddingBottom = 1
                                            
                                            // Set the waveform data
                                            sample = waveformData
                                            
                                            // Show full waveform (100% progress)
                                            this.progress = 100f
                                            
                                            // Disable built-in gesture handling - make it purely visual
                                            onProgressChanged = null
                                            
                                            // Disable touch events on the WaveformSeekBar
                                            isEnabled = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "No waveform data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Right spacer to match spectrogram Y-axis labels
                    Spacer(modifier = Modifier.width(30.dp))
                }
                }
                
                // Warning about 4-minute analysis limitation - inside the Box
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spectrogram analysis limited to first 4 minutes of audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // X-axis time labels (bottom) - outside the image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Use the analyzed duration for time axis labels, skip for AIFF files
                    val isAiffFile = musicFile?.name?.lowercase()?.endsWith(".aif") == true || musicFile?.name?.lowercase()?.endsWith(".aiff") == true
                    val analyzedDurationSeconds = if (isAiffFile) {
                        240f // Skip time display for AIFF files, use fixed 4 minutes
                    } else {
                        actualDuration / 1000f // Use actual duration for other formats
                    }
                    val timeLabels = listOf(
                        formatTime(0f),
                        formatTime(analyzedDurationSeconds * 0.25f),
                        formatTime(analyzedDurationSeconds * 0.5f),
                        formatTime(analyzedDurationSeconds * 0.75f),
                        formatTime(analyzedDurationSeconds)
                    )
                    timeLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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

// Generate comprehensive spectrogram image with all details
private fun generateComprehensiveSpectrogramImage(
    spectrogramBitmap: androidx.compose.ui.graphics.ImageBitmap,
    musicFile: MusicFile,
    duration: Long,
    waveformData: IntArray? = null,
    backgroundColor: Int,
    textColor: Int,
    primaryColor: Int,
    surfaceVariantColor: Int,
    errorContainerColor: Int,
    onErrorContainerColor: Int,
    context: Context
): Bitmap {
    val width = 1200
    val height = 2400 // Increased height for more content
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Paints
    val backgroundPaint = android.graphics.Paint().apply { color = backgroundColor }
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 48f
        color = textColor
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val headerPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 32f
        color = textColor
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val bodyPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 24f * 1.296f // Decreased by 10%
        color = textColor
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val cardPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = surfaceVariantColor
    }
    val warningCardPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = errorContainerColor
    }
    
    // Draw Background
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

    var yPos = 80f

    // Header
    val analysisTitle = "SPECTOGRAM ANALYSIS"
    canvas.drawText(analysisTitle, width / 2f, yPos, titlePaint)
    val titleBounds = android.graphics.Rect()
    titlePaint.getTextBounds(analysisTitle, 0, analysisTitle.length, titleBounds)
    val titleUnderlinePaint = android.graphics.Paint(titlePaint).apply {
        strokeWidth = 3f // Increased thickness
    }
    val underlineY = yPos + titlePaint.descent() + (titlePaint.textSize * 0.1f) // Positioned 10% lower
    canvas.drawLine(width / 2f - titleBounds.width() / 2, underlineY, width / 2f + titleBounds.width() / 2, underlineY, titleUnderlinePaint)


    yPos += 80f * 1.2f // Move down by 20%

    // File details section with two columns
    val infoCardTop = yPos
    val infoCardHeight = 320f
    val infoCardRect = android.graphics.RectF(40f, infoCardTop, width - 40f, infoCardTop + infoCardHeight)
    canvas.drawRoundRect(infoCardRect, 16f, 16f, cardPaint)
    
    val infoContentTop = infoCardTop + 60f

    val cardContentWidth = width - 80f
    val horizontalPadding = 20f
    val middlePadding = 20f
    val columnWidth = ((cardContentWidth - (2 * horizontalPadding) - middlePadding) / 2).toInt()

    // Column 1: File Information
    val col1HeaderX = 40f + horizontalPadding + (columnWidth / 2f)
    val fileInfoTitle = "FILE INFORMATION"
    canvas.drawText(fileInfoTitle, col1HeaderX, infoContentTop, headerPaint)
    val fileInfoBounds = android.graphics.Rect()
    headerPaint.getTextBounds(fileInfoTitle, 0, fileInfoTitle.length, fileInfoBounds)
    val headerUnderlinePaint = android.graphics.Paint(headerPaint).apply {
        strokeWidth = 3f // Increased thickness
    }
    val fileInfoUnderlineY = infoContentTop + headerPaint.descent() + (headerPaint.textSize * 0.1f) // Positioned 10% lower
    canvas.drawLine(col1HeaderX - fileInfoBounds.width() / 2, fileInfoUnderlineY, col1HeaderX + fileInfoBounds.width() / 2, fileInfoUnderlineY, headerUnderlinePaint)

    var yPosCol1 = infoContentTop + 60f
    val col1StartX = 40f + horizontalPadding

    val fileDetails = listOf(
        "Name: ${musicFile.name}",
        "Duration: ${formatTime(duration / 1000f)}",
        "Size: ${formatFileSize(musicFile.size)}",
        "Format: ${getFileExtension(musicFile.name)}"
    )

    fileDetails.forEach { text ->
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, columnWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(col1StartX, yPosCol1)
        staticLayout.draw(canvas)
        canvas.restore()
        yPosCol1 += staticLayout.height + 10
    }

    // Column 2: Audio Properties
    val col2HeaderX = 40f + horizontalPadding + columnWidth + middlePadding + (columnWidth / 2f)
    val audioPropsTitle = "AUDIO PROPERTIES"
    canvas.drawText(audioPropsTitle, col2HeaderX, infoContentTop, headerPaint)
    val audioPropsBounds = android.graphics.Rect()
    headerPaint.getTextBounds(audioPropsTitle, 0, audioPropsTitle.length, audioPropsBounds)
    val audioPropsUnderlineY = infoContentTop + headerPaint.descent() + (headerPaint.textSize * 0.1f) // Positioned 10% lower
    canvas.drawLine(col2HeaderX - audioPropsBounds.width() / 2, audioPropsUnderlineY, col2HeaderX + audioPropsBounds.width() / 2, audioPropsUnderlineY, headerUnderlinePaint)


    var yPosCol2 = infoContentTop + 60f
    val col2StartX = 40f + horizontalPadding + columnWidth + middlePadding

    val audioProperties = extractAudioProperties(musicFile, context)
    val audioDetails = listOf(
        "Sample Rate: ${audioProperties.sampleRate} Hz",
        "Bit Depth: ${audioProperties.bitDepth} bits",
        "Channels: ${audioProperties.channels}",
        "Bitrate: ${audioProperties.bitrate} kbps"
    )

    audioDetails.forEach { text ->
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, columnWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(col2StartX, yPosCol2)
        staticLayout.draw(canvas)
        canvas.restore()
        yPosCol2 += staticLayout.height + 10
    }

    yPos = infoCardTop + infoCardHeight + (infoCardHeight * 0.35f) // Move down by another 20%

    // Spectrogram
    val spectrogramCardTop = yPos
    val spectrogramCardHeight = 680f * 1.35f // Increased height by another 15%
    val spectrogramCardRect = android.graphics.RectF(40f, spectrogramCardTop, width - 40f, spectrogramCardTop + spectrogramCardHeight) // Increased height for time labels and spectrogram
    canvas.drawRoundRect(spectrogramCardRect, 16f, 16f, cardPaint)
    val spectrogramContentTop = spectrogramCardTop + 50f
    val spectrogramHeight = 400f * 1.9f // Increased height to fill new container space
    val spectrogramTop = spectrogramContentTop

    val axisLabelPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 20f * 1.2f // Increased by 20%
        color = textColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Y-axis labels (left - kHz)
    axisLabelPaint.textAlign = android.graphics.Paint.Align.RIGHT
    val freqLabels = listOf("22k", "18k", "14k", "10k", "6k", "2k", "1k", "20")
    val freqLabelX = 100f
    freqLabels.forEachIndexed { index, label ->
        val labelY = spectrogramTop + (spectrogramHeight * index / (freqLabels.size - 1)) + (axisLabelPaint.textSize / 3)
        canvas.drawText(label, freqLabelX, labelY, axisLabelPaint)
    }

    // Y-axis labels (right - dB)
    axisLabelPaint.textAlign = android.graphics.Paint.Align.LEFT
    val dbLabels = listOf("0", "-12", "-24", "-36", "-48", "-60")
    val dbLabelX = width - 100f
    dbLabels.forEachIndexed { index, label ->
        val labelY = spectrogramTop + (spectrogramHeight * index / (dbLabels.size - 1)) + (axisLabelPaint.textSize / 3)
        canvas.drawText(label, dbLabelX, labelY, axisLabelPaint)
    }

    val spectrogramStartX = 120f
    val spectrogramEndX = width - 120f
    val spectrogramWidth = spectrogramEndX - spectrogramStartX

    val scaledSpectrogram = Bitmap.createScaledBitmap(spectrogramBitmap.asAndroidBitmap(), spectrogramWidth.toInt(), spectrogramHeight.toInt(), true)
    canvas.drawBitmap(scaledSpectrogram, spectrogramStartX, spectrogramTop, null)

    // X-axis time labels
    axisLabelPaint.textAlign = android.graphics.Paint.Align.CENTER
    val analyzedDurationSeconds = minOf(duration / 1000f, 240f)
    val timeLabels = listOf(
        formatTime(0f),
        formatTime(analyzedDurationSeconds * 0.25f),
        formatTime(analyzedDurationSeconds * 0.5f),
        formatTime(analyzedDurationSeconds * 0.75f),
        formatTime(analyzedDurationSeconds)
    )
    val timeLabelY = spectrogramTop + spectrogramHeight + 40f
    timeLabels.forEachIndexed { index, label ->
        val labelX = spectrogramStartX + (spectrogramWidth * index / (timeLabels.size - 1))
        canvas.drawText(label, labelX, timeLabelY, axisLabelPaint)
    }

    yPos = spectrogramCardTop + spectrogramCardHeight + 20f // Add space after spectrogram card

    // Waveform
    val waveformCardBottom: Float
    if (waveformData != null) {
        val waveformCardTop = yPos
        val waveformCardHeight = 280f // Increased height to contain taller waveform
        val waveformCardRect = android.graphics.RectF(40f, waveformCardTop, width - 40f, waveformCardTop + waveformCardHeight) // Increased height
        canvas.drawRoundRect(waveformCardRect, 16f, 16f, cardPaint)
        
        val waveformContentTop = waveformCardTop + 50f

        val waveformPaint = android.graphics.Paint().apply {
            color = primaryColor
            strokeWidth = 2f
        }
        val waveformWidth = (width - 160f).toInt()
        val waveformHeight = 100f * 1.5f // Increased height
        val waveformStartX = 80f
        val centerY = waveformContentTop + waveformHeight / 2f
        
        // Improved drawing logic: Group samples to fit the width
        val samples = waveformData.map { it.toFloat() }
        val maxAmplitude = samples.maxOrNull() ?: 1f
        val samplesPerPixel = samples.size.toFloat() / waveformWidth
        
        for (i in 0 until waveformWidth) {
            val start = (i * samplesPerPixel).toInt()
            val end = ((i + 1) * samplesPerPixel).toInt().coerceAtMost(samples.size)
            if (start < end) {
                val peak = samples.subList(start, end).maxOrNull() ?: 0f
                val amplitude = (peak / maxAmplitude) * (waveformHeight / 2f)
                val x = waveformStartX + i
                canvas.drawLine(x, centerY - amplitude, x, centerY + amplitude, waveformPaint)
            }
        }
        waveformCardBottom = waveformCardTop + waveformCardHeight
    } else {
        waveformCardBottom = yPos
    }
    
    // --- Generated With Section ---
    var generatedYPos = waveformCardBottom + 60f // Start 60px below waveform

    val generatedWithPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = textColor
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val mobileDiggerPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 36f
        color = AndroidColor.parseColor("#10B981") // GreenAccent
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val groovyPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 28f
        color = textColor
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val linePaint = android.graphics.Paint().apply {
        color = textColor
        strokeWidth = 2f
    }

    // Calculate heights for precise positioning
    val generatedWithHeight = generatedWithPaint.descent() - generatedWithPaint.ascent()
    val mobileDiggerHeight = mobileDiggerPaint.descent() - mobileDiggerPaint.ascent()
    val groovyHeight = groovyPaint.descent() - groovyPaint.ascent()
    val linePadding = 50f
    val interTextPadding = 40f

    // Draw top line
    canvas.drawLine(40f, generatedYPos, width - 40f, generatedYPos, linePaint)
    generatedYPos += linePaint.strokeWidth + linePadding

    // Draw "Generated with"
    canvas.drawText("Generated with", width / 2f, generatedYPos - generatedWithPaint.ascent(), generatedWithPaint)
    generatedYPos += generatedWithHeight + interTextPadding

    // Draw "MobileDigger for Android"
    canvas.drawText("MobileDigger for Android", width / 2f, generatedYPos - mobileDiggerPaint.ascent(), mobileDiggerPaint)
    generatedYPos += mobileDiggerHeight + interTextPadding
    
    // Draw "groovy's child"
    canvas.drawText("groovy's child", width / 2f, generatedYPos - groovyPaint.ascent(), groovyPaint)
    generatedYPos += groovyHeight + linePadding

    // Draw bottom line
    canvas.drawLine(40f, generatedYPos, width - 40f, generatedYPos, linePaint)
    
    val generatedWithBlockBottom = generatedYPos // This is the bottom of the "Generated With" block

    // --- Centered Warning Section ---
    val warningCardHeight = 100f
    val spaceForWarning = height - generatedWithBlockBottom // Space from bottom of generated block to bottom of image
    val warningYPos = generatedWithBlockBottom + (spaceForWarning - warningCardHeight) / 2

    val warningCardRect = android.graphics.RectF(40f, warningYPos, width - 40f, warningYPos + warningCardHeight)
    canvas.drawRoundRect(warningCardRect, 16f, 16f, warningCardPaint)
    
    val finalWarningTextPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 22f * 1.2f // 20% bigger
        color = onErrorContainerColor
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val warningText = "Spectrogram analysis limited to first 4 minutes of audio"
    val textBounds = android.graphics.Rect()
    finalWarningTextPaint.getTextBounds(warningText, 0, warningText.length, textBounds)
    canvas.drawText(warningText, width / 2f, warningYPos + warningCardHeight / 2 + textBounds.height() / 2, finalWarningTextPaint)

    return bitmap
}

@Composable
fun SpectrogramPopupDialog(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    onDismiss: () -> Unit,
    waveformData: IntArray? = null
) {
    var spectrogramBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeColors = MaterialTheme.colorScheme
    
    // Track conversion and spectrogram generation progress
    val isConverting by audioManager.isConverting.collectAsState()
    val conversionProgress by audioManager.conversionProgress.collectAsState()
    val isGeneratingSpectrogram by audioManager.isGeneratingSpectrogram.collectAsState()
    val spectrogramProgress by audioManager.spectrogramProgress.collectAsState()
    
    // Generate spectrogram when dialog opens
    LaunchedEffect(musicFile?.uri) {
        if (musicFile != null) {
            println("SpectrogramPopupDialog: Starting spectrogram generation for ${musicFile.name}")
            isLoading = true
            try {
                val bitmap = audioManager.generateSpectrogram(musicFile)
                println("SpectrogramPopupDialog: Received bitmap: ${bitmap != null}")
                spectrogramBitmap = bitmap
            } catch (e: Exception) {
                println("SpectrogramPopupDialog: Exception during generation: ${e.message}")
                e.printStackTrace()
                spectrogramBitmap = null
            } finally {
                isLoading = false
                println("SpectrogramPopupDialog: Generation completed, isLoading = false")
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
    
    // Share function - Generate comprehensive image with all details
    fun shareSpectrogram() {
        if (spectrogramBitmap != null && musicFile != null) {
            scope.launch {
                try {
                    // Generate comprehensive image with all details
                    val comprehensiveImage = generateComprehensiveSpectrogramImage(
                        spectrogramBitmap!!,
                        musicFile,
                        actualDuration,
                        waveformData,
                        themeColors.surface.toArgb(),
                        themeColors.onSurface.toArgb(),
                        themeColors.primary.toArgb(),
                        themeColors.surfaceVariant.toArgb(),
                        themeColors.errorContainer.toArgb(),
                        themeColors.onErrorContainer.toArgb(),
                        context
                    )
                    
                    // Save to temporary file
                    val fileName = "spectrogram_analysis_${musicFile.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.png"
                    val file = File(context.cacheDir, fileName)
                    val outputStream = FileOutputStream(file)
                    comprehensiveImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()
                    
                    // Create share intent with FileProvider
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Spectrogram Analysis: ${musicFile.name}")
                        putExtra(Intent.EXTRA_SUBJECT, "Audio Analysis - ${musicFile.name}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // Start share activity
                    context.startActivity(Intent.createChooser(shareIntent, "Share Spectrogram Analysis"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback: try simple file URI
                    try {
                        val comprehensiveImage = generateComprehensiveSpectrogramImage(
                            spectrogramBitmap!!,
                            musicFile,
                            actualDuration,
                            waveformData,
                            themeColors.surface.toArgb(),
                            themeColors.onSurface.toArgb(),
                            themeColors.primary.toArgb(),
                            themeColors.surfaceVariant.toArgb(),
                            themeColors.errorContainer.toArgb(),
                            themeColors.onErrorContainer.toArgb(),
                            context
                        )
                        val fileName = "spectrogram_analysis_${musicFile.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.png"
                        val file = File(context.cacheDir, fileName)
                        val outputStream = FileOutputStream(file)
                        comprehensiveImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.flush()
                        outputStream.close()
                        
                        val uri = Uri.fromFile(file)
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "Spectrogram Analysis: ${musicFile.name}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Spectrogram Analysis"))
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
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
        spectrogramProgress = spectrogramProgress
    )
}
