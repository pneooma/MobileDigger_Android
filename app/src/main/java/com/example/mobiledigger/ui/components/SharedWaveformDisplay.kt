package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.*
import com.example.mobiledigger.util.AnimationUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar

@Composable
fun SharedWaveformDisplay(
    sharedState: SharedWaveformState,
    progress: Float, // 0.0 to 1.0
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.1f), // Allow custom background color
    currentPosition: Long = 0L, // Current playback position in milliseconds
    totalDuration: Long = 0L // Total duration in milliseconds
) {
    val context = LocalContext.current
    val refreshRate = remember { AnimationUtils.getDisplayRefreshRate(context) }
    
    // Local progress state for immediate feedback
    var localProgress by remember { mutableStateOf(progress) }
    
    // Update local progress when external progress changes
    LaunchedEffect(progress) {
        localProgress = progress
    }

    // Format time as MM:SS
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    BoxWithConstraints(
        modifier = modifier
            .background(
                backgroundColor,
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Calculate progress based on tap position
                    val tapProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    localProgress = tapProgress
                    onSeek(tapProgress)
                    println("🎯 Shared waveform tap seek to: ${(tapProgress * 100).toInt()}%")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        
        // Content layer
        Box(modifier = Modifier.fillMaxSize())
        when {
            sharedState.isLoading -> {
                // Loading indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "loadingDots")
                    (0..2).forEach { index ->
                        val animatedAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = AnimationUtils.getOptimizedDuration(600, refreshRate), easing = LinearEasing),
                                initialStartOffset = StartOffset(offsetMillis = index * 200),
                                repeatMode = RepeatMode.Reverse
                            ), label = "dotAlpha$index"
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
            
            sharedState.errorMessage != null -> {
                // Error state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Waveform Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = sharedState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            sharedState.waveformData != null -> {
                // Waveform display using WaveformSeekBar
                val playedColor = MaterialTheme.colorScheme.primary.toArgb()
                val unplayedColor = MaterialTheme.colorScheme.outline.toArgb()
                
                AndroidView(
                    factory = { ctx ->
                        WaveformSeekBar(ctx).apply {
                            // Configure WaveformSeekBar appearance
                            waveBackgroundColor = unplayedColor
                            waveProgressColor = playedColor
                            waveWidth = 1f
                            waveGap = 0f
                            waveCornerRadius = 2f
                            wavePaddingTop = 1
                            wavePaddingBottom = 1
                            
                            // Set the waveform data
                            sample = sharedState.waveformData!!
                            
                            // Set initial progress
                            this.progress = localProgress * 100f
                            
                            // Disable built-in gesture handling - make it purely visual
                            onProgressChanged = null
                            
                            // Disable touch events on the WaveformSeekBar
                            isEnabled = false
                        }
                    },
                    update = { waveformSeekBar ->
                        // Update progress when it changes externally
                        waveformSeekBar.progress = localProgress * 100f
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp) // Add padding to waveform
                )
            }
            
            else -> {
                // No waveform available - always show message (for AIFF files)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Always show "No waveform for AIFF" message
                    Text(
                        text = "No waveform for AIFF files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // OVERLAY LAYER: Always-visible green progress line (on top of everything)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp) // Add padding to waveform
        ) {
            val progressX = localProgress * size.width
            // Draw shadow/outline for better visibility
            drawLine(
                color = Color.Black.copy(alpha = 0.5f),
                start = Offset(progressX - 0.5.dp.toPx(), 0f),
                end = Offset(progressX - 0.5.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.5f),
                start = Offset(progressX + 0.5.dp.toPx(), 0f),
                end = Offset(progressX + 0.5.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx()
            )
            // Draw main green line (thinner)
            drawLine(
                color = Color.Green,
                start = Offset(progressX, 0f),
                end = Offset(progressX, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        
        // OVERLAY LAYER: Elapsed time display (at bottom, left of seek line, in red)
        if (currentPosition > 0L) {
            val timeWidth = 45.dp // Approximate width of time text
            val seekLinePosition = containerWidth * localProgress
            // Position to the left of the seek line, with 4dp gap
            val timeOffset = (seekLinePosition - timeWidth - 4.dp).coerceAtLeast(4.dp)
            
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red, // Changed to red
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = timeOffset,
                        y = (-4).dp // 4dp from bottom
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}
