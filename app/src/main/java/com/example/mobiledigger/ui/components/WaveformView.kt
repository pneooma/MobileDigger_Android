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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.utils.WaveformGenerator
import kotlinx.coroutines.launch

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

    // Generate waveform when file or settings change
    LaunchedEffect(currentFile?.uri?.toString(), WaveformGenerator.getCurrentSettings()) {
        println("🔄 WaveformView: LaunchedEffect triggered for file: ${currentFile?.name}")
        println("🔗 URI key: ${currentFile?.uri?.toString()}")
        println("🎛️ Settings: ${WaveformGenerator.getCurrentSettings()}")
        if (currentFile != null) {
            println("🎵 Starting waveform generation for: ${currentFile.name}")
            println("🔗 URI: ${currentFile.uri}")
            isLoading = true
            waveformData = null // Clear previous data
            
            try {
                // Use URI directly like spectrogram generation
                println("📁 Using URI directly: ${currentFile.uri}")
                
                // Generate waveform from URI (same approach as spectrogram)
                val waveform = WaveformGenerator.generateFromUri(
                    context = context,
                    uri = currentFile.uri
                )
                
                waveformData = waveform
                println("✅ Waveform loaded successfully: ${waveform.size} bars")
                println("🎵 First 5 amplitudes: ${waveform.take(5).joinToString()}")
            } catch (e: Exception) {
                println("❌ Error generating waveform: ${e.message}")
                e.printStackTrace()
                // Create a test waveform on error
                waveformData = IntArray(50) { (Math.random() * 100).toInt() }
                println("🧪 Using test waveform due to error")
            } finally {
                isLoading = false
                println("🏁 Waveform generation completed")
            }
        } else {
            println("📭 No current file, clearing waveform data")
            waveformData = null
        }
    }

    // Debug logging for UI states
    LaunchedEffect(isLoading, waveformData) {
        println("🎨 WaveformView UI State - isLoading: $isLoading, hasData: ${waveformData != null}")
    }

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
            
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawWaveform(
                    amplitudes = waveformData!!,
                    progress = progress,
                    canvasSize = size,
                    playedColor = playedColor,
                    unplayedColor = unplayedColor
                )
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

private fun DrawScope.drawWaveform(
    amplitudes: IntArray,
    progress: Float,
    canvasSize: androidx.compose.ui.geometry.Size,
    playedColor: Color,
    unplayedColor: Color
) {
    if (amplitudes.isEmpty()) return

    // React Native Audio Waveform inspired candlestick rendering
    val settings = WaveformGenerator.getCurrentSettings()
    val totalBars = amplitudes.size
    val candleWidth = settings.candleWidth.dp.toPx()
    val candleSpacing = 1.dp.toPx() // Small gap between candles
    val totalCandleWidth = candleWidth + candleSpacing
    
    // Calculate how many candles can fit in the available width
    val maxCandlesInWidth = (canvasSize.width / totalCandleWidth).toInt()
    val effectiveBars = minOf(totalBars, maxCandlesInWidth)
    
    // Calculate actual spacing to fill the width properly
    val availableWidth = canvasSize.width
    val actualCandleWidth = (availableWidth / effectiveBars) - candleSpacing
    
    val maxBarHeight = canvasSize.height * 0.9f // Use more of the available height
    val centerY = canvasSize.height / 2f
    val progressX = canvasSize.width * progress

    println("🎨 Drawing waveform: totalBars=$totalBars, effectiveBars=$effectiveBars, candleWidth=$actualCandleWidth, canvasWidth=${canvasSize.width}")
    println("🎵 Amplitude range: min=${amplitudes.minOrNull()}, max=${amplitudes.maxOrNull()}, first 5: ${amplitudes.take(5).joinToString()}")

    // Draw candlesticks (React Native Audio Waveform approach)
    for (i in 0 until effectiveBars) {
        val amplitude = amplitudes[i]
        val x = i * (actualCandleWidth + candleSpacing)
        
        // Apply height scaling (React Native Audio Waveform approach)
        val normalizedAmplitude = (amplitude / 100f).coerceIn(0f, 1f)
        val scaledHeight = normalizedAmplitude * maxBarHeight * settings.candleHeightScale
        val finalBarHeight = maxOf(scaledHeight, 2.dp.toPx()) // Minimum height for visibility

        val isPlayed = x < progressX
        val color = if (isPlayed) playedColor else unplayedColor

        val top = centerY - finalBarHeight / 2

        // Draw candlestick (individual bar)
        drawRoundRect(
            color = color,
            topLeft = Offset(x, top),
            size = androidx.compose.ui.geometry.Size(actualCandleWidth, finalBarHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(actualCandleWidth / 4)
        )
    }

    // Draw progress cursor with gradient effect
    val cursorWidth = 3.dp.toPx()
    drawLine(
        color = Color.Red.copy(alpha = 0.8f),
        start = Offset(progressX - cursorWidth/2, 0f),
        end = Offset(progressX - cursorWidth/2, canvasSize.height),
        strokeWidth = cursorWidth
    )

    // Add subtle glow effect to cursor
    drawLine(
        color = Color.Red.copy(alpha = 0.3f),
        start = Offset(progressX - cursorWidth, 0f),
        end = Offset(progressX - cursorWidth, canvasSize.height),
        strokeWidth = cursorWidth * 2
    )
}
