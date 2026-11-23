package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.masoudss.lib.WaveformSeekBar
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.ui.theme.WaveformAnimationType

@Composable
fun SharedWaveformDisplay(
    sharedState: SharedWaveformState,
    progress: Float, // 0.0 to 1.0
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent, // Transparent by default
    currentPosition: Long = 0L, // Current playback position in milliseconds
    /** While true, the playhead glides forward on every display frame between position updates. */
    isPlaying: Boolean = false,
    totalDuration: Long = 0L, // Total duration in milliseconds
    fileName: String = "",
    opacity: Float = 0.8f, // Waveform opacity (default 80%)
    customWaveformColor: Color? = null, // Custom waveform played color, null = use theme
    animationType: WaveformAnimationType = WaveformAnimationType.EXPAND,
    animationDurationMs: Int = 300
) {
    
    // Local progress state for immediate feedback
    var localProgress by remember { mutableStateOf(progress) }
    
    // Position updates arrive ~30×/s; between them, move the playhead on every display frame
    // (60/90/120/144 Hz) from the last known position, so it glides instead of stepping.
    var anchorProgress by remember { mutableFloatStateOf(progress) }
    var anchorDirty by remember { mutableStateOf(true) }
    LaunchedEffect(progress) {
        localProgress = progress
        anchorProgress = progress
        anchorDirty = true
    }
    LaunchedEffect(isPlaying, totalDuration) {
        if (!isPlaying || totalDuration <= 0L) return@LaunchedEffect
        var anchorNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (anchorDirty) { anchorNanos = now; anchorDirty = false }
                // Cap the glide at 250 ms so a stalled player can't run the line away
                val ms = ((now - anchorNanos) / 1_000_000L).coerceAtMost(250L)
                localProgress = (anchorProgress + ms.toFloat() / totalDuration).coerceAtMost(1f)
            }
        }
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
                    CrashLogger.log("Debug", "🎯 Shared waveform tap seek to: ${(tapProgress * 100).toInt()}%")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        
        // Content layer
        Box(modifier = Modifier.fillMaxSize())
        when {
            sharedState.isLoading -> {
                // Loading - show nothing, just empty container
                Box(modifier = Modifier.fillMaxSize())
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
                // Waveform display using WaveformSeekBar with long zoom animation (0% to 100%)
                val playedColor = (customWaveformColor ?: MaterialTheme.colorScheme.primary).toArgb()
                val unplayedColor = MaterialTheme.colorScheme.outline.toArgb()
                
                // Track if waveform just appeared to trigger animation from 0
                var hasAppeared by remember(sharedState.waveformData) { mutableStateOf(false) }
                LaunchedEffect(sharedState.waveformData) {
                    hasAppeared = true
                    val data = sharedState.waveformData
                    val min = data.minOrNull() ?: 0
                    val max = data.maxOrNull() ?: 0
                    val avg = if (data.isNotEmpty()) data.average().toInt() else 0
                    CrashLogger.log("WaveformGenerator", "📺 SharedWaveformDisplay - Received waveform: ${data.size} samples")
                    CrashLogger.log("WaveformGenerator", "📺 SharedWaveformDisplay - Min: $min, Max: $max, Avg: $avg")
                    CrashLogger.log("WaveformGenerator", "📺 SharedWaveformDisplay - First 10: ${data.take(10).joinToString()}")
                }
                
                // Resolve RANDOM to a concrete type per-waveform appearance
                val resolvedAnimationType = remember(sharedState.waveformData) {
                    if (animationType == WaveformAnimationType.RANDOM) WaveformAnimationType.randomAnimatable()
                    else animationType
                }

                // Animated appearance based on animation type
                val animatedValue by animateFloatAsState(
                    targetValue = if (hasAppeared) 1f else 0f,
                    animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing),
                    label = "waveformAnimation"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            when (resolvedAnimationType) {
                                WaveformAnimationType.EXPAND -> {
                                    scaleX = 1f
                                    scaleY = animatedValue
                                }
                                WaveformAnimationType.SLIDE_UP -> {
                                    translationY = (1f - animatedValue) * 100f
                                    alpha = animatedValue
                                }
                                WaveformAnimationType.SLIDE_DOWN -> {
                                    translationY = -(1f - animatedValue) * 100f
                                    alpha = animatedValue
                                }
                                WaveformAnimationType.FADE -> {
                                    alpha = animatedValue
                                }
                                WaveformAnimationType.SCALE -> {
                                    scaleX = animatedValue
                                    scaleY = animatedValue
                                }
                                WaveformAnimationType.NONE, WaveformAnimationType.RANDOM -> {
                                    // No animation (RANDOM already resolved above)
                                }
                            }
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WaveformSeekBar(ctx).apply {
                                // Configure WaveformSeekBar appearance
                                waveBackgroundColor = unplayedColor
                                waveProgressColor = playedColor
                                // Sizes in dp (the view takes px): same look on every screen density
                                val d = ctx.resources.displayMetrics.density
                                waveWidth = 0.4f * d // Thin bars
                                waveGap = 0.4f * d // Small gap so the bars read individually
                                waveCornerRadius = 0.4f * d // Little rounding so thin bars stay crisp
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
                            .graphicsLayer { alpha = opacity } // Set waveform opacity from parameter
                    )
                }
            }
            
            else -> {
                // No waveform available - keep container empty (no message)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Intentionally empty
                }
            }
        }
        
        // OVERLAY LAYER: Filename centered vertically in waveform container
        if (fileName.isNotEmpty()) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.9f, // 10% smaller
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 0.65f // 65% line height (50% + 15%)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
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
        
        // Elapsed time counter removed — time is shown in playlist row icons
    }
}
