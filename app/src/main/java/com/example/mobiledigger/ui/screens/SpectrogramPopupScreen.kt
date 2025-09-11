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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.ui.components.SpectrogramView

@Composable
fun SpectrogramPopupScreen(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    onDismiss: () -> Unit,
    spectrogramBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isLoading: Boolean,
    onShare: () -> Unit,
    actualDuration: Long
) {
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
                // Header with title, share button, and close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spectrogram Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row {
                        // Share button
                        IconButton(
                            onClick = onShare
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Spectrogram",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Close button
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
                }
                
                // File details section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Spectrogram Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "Frequency Range: 20Hz - 22kHz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "Dynamic Range: -60dB to 0dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "Resolution: BALANCED (1024 samples, 128 bins)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "Analysis Duration: 4 minutes maximum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        
                        Text(
                            text = "Analysis Time: Check logs for timing details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Large spectrogram display with external axis labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp) // Further reduced height for compact display
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                
                // Generated Waveform Picture Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Generated Waveform",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Waveform display area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                // Display waveform using the same waveform component
                                musicFile?.let { file ->
                                    SpectrogramView(
                                        musicFile = file,
                                        audioManager = audioManager,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: run {
                                    Text(
                                        text = "No waveform available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
    duration: Long
): Bitmap {
    val width = 800
    val height = 1000
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Background
    canvas.drawColor(android.graphics.Color.WHITE)
    
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = android.graphics.Color.BLACK
    }
    
    val smallPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 16f
        color = android.graphics.Color.BLACK
    }
    
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 32f
        color = android.graphics.Color.BLACK
        isFakeBoldText = true
    }
    
    var yPos = 50f
    
    // Title
    canvas.drawText("SPECTROGRAM ANALYSIS", 50f, yPos, titlePaint)
    yPos += 60f
    
    // File Information
    canvas.drawText("File: ${musicFile.name}", 50f, yPos, paint)
    yPos += 30f
    
    // Use the analyzed duration (2 minutes max) for the image
    val imageEffectiveDuration = minOf(duration, 240000L) // Cap at 4 minutes (240 seconds)
    
    canvas.drawText("Duration: ${formatTime(imageEffectiveDuration / 1000f)}", 50f, yPos, paint)
    yPos += 30f
    
    canvas.drawText("Size: ${formatFileSize(musicFile.size)}", 50f, yPos, paint)
    yPos += 30f
    
    canvas.drawText("Format: ${getFileExtension(musicFile.name)}", 50f, yPos, paint)
    yPos += 30f
    
    // Analysis Parameters
    yPos += 20f
    canvas.drawText("Analysis Parameters:", 50f, yPos, paint)
    yPos += 30f
    
    canvas.drawText("• Frequency Range: 20Hz - 22kHz", 70f, yPos, smallPaint)
    yPos += 25f
    
    canvas.drawText("• Dynamic Range: -60dB to 0dB", 70f, yPos, smallPaint)
    yPos += 25f
    
    canvas.drawText("• Resolution: BALANCED (1024 samples, 128 bins)", 70f, yPos, smallPaint)
    yPos += 25f
    
    canvas.drawText("• Analysis Duration: 4 minutes maximum", 70f, yPos, smallPaint)
    yPos += 25f
    
    canvas.drawText("• Analysis Type: Power Spectrum", 70f, yPos, smallPaint)
    yPos += 25f
    
    // Spectrogram Image
    yPos += 20f
    val spectrogramWidth = 700
    val spectrogramHeight = 400
    val spectrogramRect = android.graphics.Rect(50, yPos.toInt(), 750, (yPos + spectrogramHeight).toInt())
    
    // Draw spectrogram with border
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRect(spectrogramRect, borderPaint)
    
    // Scale and draw the spectrogram bitmap
    val scaledSpectrogram = Bitmap.createScaledBitmap(
        spectrogramBitmap.asAndroidBitmap(),
        spectrogramWidth,
        spectrogramHeight,
        true
    )
    canvas.drawBitmap(scaledSpectrogram, 50f, yPos, null)
    
    // Add axis labels
    yPos += spectrogramHeight + 10f
    
    // Y-axis labels - evenly distributed across spectrogram height
    val spectrogramTop = yPos - spectrogramHeight
    val labelSpacing = spectrogramHeight / 8f // 8 labels total for cleaner display
    
    // Frequency labels (kHz) on left - doubled frequency steps
                        val freqLabels = listOf("22k", "18k", "14k", "10k", "6k", "2k", "1k", "20")
    canvas.drawText("kHz", 20f, spectrogramTop - 10f, smallPaint)
    freqLabels.forEachIndexed { index, label ->
        val labelY = spectrogramTop + (index * labelSpacing) + (labelSpacing / 2f)
        canvas.drawText(label, 20f, labelY, smallPaint)
    }
    
    // dB labels on right
    val dbLabels = listOf("0", "-12", "-24", "-36", "-48", "-60")
    canvas.drawText("dB", 760f, spectrogramTop - 10f, smallPaint)
    dbLabels.forEachIndexed { index, label ->
        val labelY = spectrogramTop + (index * labelSpacing) + (labelSpacing / 2f)
        canvas.drawText(label, 760f, labelY, smallPaint)
    }
    
    // Time labels - use analyzed duration (2 minutes max)
    val timeLabels = listOf(
        formatTime(0f),
        formatTime((imageEffectiveDuration / 1000f) * 0.25f),
        formatTime((imageEffectiveDuration / 1000f) * 0.5f),
        formatTime((imageEffectiveDuration / 1000f) * 0.75f),
        formatTime(imageEffectiveDuration / 1000f)
    )
    
    for (i in timeLabels.indices) {
        val xPos = 50f + (i * 175f)
        canvas.drawText(timeLabels[i], xPos, yPos + 20f, smallPaint)
    }
    
    // Footer
    yPos += 60f
    canvas.drawText("Generated by MobileDigger Audio Analyzer", 50f, yPos, smallPaint)
    yPos += 20f
    canvas.drawText("Analysis Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", 50f, yPos, smallPaint)
    
    return bitmap
}

@Composable
fun SpectrogramPopupDialog(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    onDismiss: () -> Unit
) {
    var spectrogramBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
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
                        actualDuration
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
                            actualDuration
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
        actualDuration = actualDuration
    )
}
