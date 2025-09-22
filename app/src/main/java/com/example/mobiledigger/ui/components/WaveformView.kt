package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.utils.LightweightWaveformGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WaveformView(
    currentFile: MusicFile?,
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var waveformData by remember { mutableStateOf<IntArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Generate waveform when file changes ONLY - Cache settings and move to background
    LaunchedEffect(currentFile?.uri?.toString()) {
        if (currentFile != null) {
            isLoading = true
            waveformData = null // Clear previous data
            
            // Move heavy waveform generation to background thread
            withContext(Dispatchers.IO) {
                try {
                    // Generate waveform from URI in background with memory safety
                    val waveform = LightweightWaveformGenerator.generateFromUri(
                        context = context,
                        uri = currentFile.uri,
                        barsCount = 30 // Limit to 30 bars for memory safety
                    )
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        waveformData = waveform
                        isLoading = false
                    }
                } catch (e: Exception) {
                    // Create a simple waveform on error without expensive operations
                    withContext(Dispatchers.Main) {
                        waveformData = IntArray(30) { if (it % 3 == 0) 60 else 30 } // Simple pattern
                        isLoading = false
                    }
                }
            }
        } else {
            waveformData = null
            isLoading = false
        }
    }

    // Debug logging removed for performance

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp)
            .background(
                Color.Gray.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            // Loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Simple loading dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { index ->
                        val animatedAlpha by rememberInfiniteTransition(label = "loading").animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "loading_$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = animatedAlpha),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        } else if (waveformData != null) {
            // Waveform visualization
            val playedColor = MaterialTheme.colorScheme.primary
            val unplayedColor = MaterialTheme.colorScheme.outline
            
            // Use optimized Canvas with cached drawing operations
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.8f) // Reduce overdraw
            ) {
                // Cache expensive calculations
                if (waveformData!!.isNotEmpty()) {
                    drawOptimizedWaveform(
                        amplitudes = waveformData!!,
                        progress = progress,
                        canvasSize = size,
                        playedColor = playedColor,
                        unplayedColor = unplayedColor
                    )
                }
            }
        } else {
            // No waveform available - show debug info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No waveform available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "File: ${currentFile?.name ?: "None"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "URI: ${currentFile?.uri?.toString()?.take(50) ?: "None"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun DrawScope.drawOptimizedWaveform(
    amplitudes: IntArray,
    progress: Float,
    canvasSize: androidx.compose.ui.geometry.Size,
    playedColor: Color,
    unplayedColor: Color
) {
    if (amplitudes.isEmpty()) return

    // Simplified high-performance waveform rendering
    val maxBars = 50 // Limit bars for performance
    val effectiveBars = minOf(amplitudes.size, maxBars)
    val barWidth = canvasSize.width / effectiveBars
    val maxBarHeight = canvasSize.height * 0.8f
    val centerY = canvasSize.height / 2f
    val progressX = canvasSize.width * progress

    // Use simple rectangles instead of rounded rectangles for performance
    for (i in 0 until effectiveBars) {
        val amplitude = amplitudes[i * amplitudes.size / effectiveBars]
        val x = i * barWidth
        
        // Simplified height calculation
        val normalizedAmplitude = (amplitude / 100f).coerceIn(0.1f, 1f)
        val barHeight = normalizedAmplitude * maxBarHeight * 0.6f

        val isPlayed = x < progressX
        val color = if (isPlayed) playedColor else unplayedColor.copy(alpha = 0.6f)

        val top = centerY - barHeight / 2

        // Draw simple rectangle for maximum performance
        drawRect(
            color = color,
            topLeft = Offset(x, top),
            size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
        )
    }

    // Simplified progress cursor for performance
    val cursorWidth = 2.dp.toPx()
    drawLine(
        color = Color.Red.copy(alpha = 0.9f),
        start = Offset(progressX, 0f),
        end = Offset(progressX, canvasSize.height),
        strokeWidth = cursorWidth
    )
}
