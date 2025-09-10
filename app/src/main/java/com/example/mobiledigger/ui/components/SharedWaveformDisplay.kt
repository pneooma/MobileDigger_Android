package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar

@Composable
fun SharedWaveformDisplay(
    sharedState: SharedWaveformState,
    progress: Float, // 0.0 to 1.0
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Local progress state for immediate feedback
    var localProgress by remember { mutableStateOf(progress) }
    
    // Update local progress when external progress changes
    LaunchedEffect(progress) {
        localProgress = progress
    }

    Box(
        modifier = modifier
            .background(
                Color.Gray.copy(alpha = 0.1f),
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
                                animation = tween(durationMillis = 600, easing = LinearEasing),
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
                val unplayedColor = Color.Gray.toArgb()
                
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
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            else -> {
                // No waveform available - show tap area with progress indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Show progress indicator
                    Text(
                        text = "Tap to seek • ${(localProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
