package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun WaveformSeekBar(
    waveformData: FloatArray?,
    progress: Float, // 0.0 to 1.0
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 60.dp,
    waveformColor: Color = MaterialTheme.colorScheme.primary,
    progressColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressLineColor: Color = MaterialTheme.colorScheme.primary,
    progressLineWidth: androidx.compose.ui.unit.Dp = 2.dp
) {
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    val progressLineWidthPx = with(density) { progressLineWidth.toPx() }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )
    
    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .background(backgroundColor, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onProgressChanged(newProgress)
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2
            
            // Draw background waveform as continuous line
            waveformData?.let { data ->
                if (data.isNotEmpty()) {
                    val maxBarHeight = canvasHeight * 0.8f
                    val path = Path()
                    var isFirstPoint = true
                    
                    // Create smooth waveform path
                    for (i in data.indices) {
                        val x = (i.toFloat() / (data.size - 1)) * canvasWidth
                        val amplitude = data[i]
                        val y = centerY - (amplitude * maxBarHeight / 2)
                        
                        if (isFirstPoint) {
                            path.moveTo(x, y)
                            isFirstPoint = false
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    
                    // Draw the waveform line
                    drawPath(
                        path = path,
                        color = waveformColor,
                        style = Stroke(width = 2f)
                    )
                    
                    // Also draw bars for better visibility
                    val barWidth = canvasWidth / data.size
                    for (i in data.indices) {
                        val barHeight = data[i] * maxBarHeight
                        val x = i * barWidth + barWidth / 2
                        
                        // Draw waveform bars with transparency
                        drawLine(
                            color = waveformColor.copy(alpha = 0.3f),
                            start = Offset(x, centerY - barHeight / 2),
                            end = Offset(x, centerY + barHeight / 2),
                            strokeWidth = max(0.5f, barWidth * 0.4f)
                        )
                    }
                }
            }
            
            // Draw progress waveform (played portion) as continuous line
            waveformData?.let { data ->
                if (data.isNotEmpty()) {
                    val progressWidth = canvasWidth * animatedProgress
                    val maxBarHeight = canvasHeight * 0.8f
                    val path = Path()
                    var isFirstPoint = true
                    var hasProgress = false
                    
                    // Create progress waveform path
                    for (i in data.indices) {
                        val x = (i.toFloat() / (data.size - 1)) * canvasWidth
                        if (x <= progressWidth) {
                            val amplitude = data[i]
                            val y = centerY - (amplitude * maxBarHeight / 2)
                            
                            if (isFirstPoint) {
                                path.moveTo(x, y)
                                isFirstPoint = false
                                hasProgress = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }
                    
                    // Draw the progress waveform line
                    if (hasProgress) {
                        drawPath(
                            path = path,
                            color = progressColor,
                            style = Stroke(width = 3f)
                        )
                    }
                    
                    // Also draw progress bars for better visibility
                    val barWidth = canvasWidth / data.size
                    for (i in data.indices) {
                        val x = i * barWidth + barWidth / 2
                        if (x <= progressWidth) {
                            val barHeight = data[i] * maxBarHeight
                            
                            // Draw progress bars with higher opacity
                            drawLine(
                                color = progressColor.copy(alpha = 0.6f),
                                start = Offset(x, centerY - barHeight / 2),
                                end = Offset(x, centerY + barHeight / 2),
                                strokeWidth = max(1f, barWidth * 0.6f)
                            )
                        }
                    }
                }
            }
            
            // Draw vertical progress line
            val progressX = canvasWidth * animatedProgress
            drawLine(
                color = progressLineColor,
                start = Offset(progressX, 0f),
                end = Offset(progressX, canvasHeight),
                strokeWidth = progressLineWidthPx
            )
        }
    }
}

@Composable
fun CompactWaveformSeekBar(
    waveformData: FloatArray?,
    progress: Float,
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 30.dp,
    waveformColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    progressColor: Color = MaterialTheme.colorScheme.secondary,
    progressLineColor: Color = MaterialTheme.colorScheme.primary,
    progressLineWidth: androidx.compose.ui.unit.Dp = 1.dp
) {
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    val progressLineWidthPx = with(density) { progressLineWidth.toPx() }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )
    
    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onProgressChanged(newProgress)
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2
            
            // Draw compact waveform as continuous line
            waveformData?.let { data ->
                if (data.isNotEmpty()) {
                    val maxBarHeight = canvasHeight * 0.6f
                    val path = Path()
                    var isFirstPoint = true
                    
                    // Create smooth waveform path
                    for (i in data.indices) {
                        val x = (i.toFloat() / (data.size - 1)) * canvasWidth
                        val amplitude = data[i]
                        val y = centerY - (amplitude * maxBarHeight / 2)
                        
                        if (isFirstPoint) {
                            path.moveTo(x, y)
                            isFirstPoint = false
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    
                    // Draw the waveform line
                    drawPath(
                        path = path,
                        color = waveformColor,
                        style = Stroke(width = 1.5f)
                    )
                    
                    // Also draw bars for better visibility
                    val barWidth = canvasWidth / data.size
                    for (i in data.indices) {
                        val barHeight = data[i] * maxBarHeight
                        val x = i * barWidth + barWidth / 2
                        
                        // Draw waveform bars with transparency
                        drawLine(
                            color = waveformColor.copy(alpha = 0.2f),
                            start = Offset(x, centerY - barHeight / 2),
                            end = Offset(x, centerY + barHeight / 2),
                            strokeWidth = max(0.3f, barWidth * 0.3f)
                        )
                    }
                }
            }
            
            // Draw progress waveform as continuous line
            waveformData?.let { data ->
                if (data.isNotEmpty()) {
                    val progressWidth = canvasWidth * animatedProgress
                    val maxBarHeight = canvasHeight * 0.6f
                    val path = Path()
                    var isFirstPoint = true
                    var hasProgress = false
                    
                    // Create progress waveform path
                    for (i in data.indices) {
                        val x = (i.toFloat() / (data.size - 1)) * canvasWidth
                        if (x <= progressWidth) {
                            val amplitude = data[i]
                            val y = centerY - (amplitude * maxBarHeight / 2)
                            
                            if (isFirstPoint) {
                                path.moveTo(x, y)
                                isFirstPoint = false
                                hasProgress = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }
                    
                    // Draw the progress waveform line
                    if (hasProgress) {
                        drawPath(
                            path = path,
                            color = progressColor,
                            style = Stroke(width = 2f)
                        )
                    }
                    
                    // Also draw progress bars for better visibility
                    val barWidth = canvasWidth / data.size
                    for (i in data.indices) {
                        val x = i * barWidth + barWidth / 2
                        if (x <= progressWidth) {
                            val barHeight = data[i] * maxBarHeight
                            
                            // Draw progress bars with higher opacity
                            drawLine(
                                color = progressColor.copy(alpha = 0.4f),
                                start = Offset(x, centerY - barHeight / 2),
                                end = Offset(x, centerY + barHeight / 2),
                                strokeWidth = max(0.5f, barWidth * 0.4f)
                            )
                        }
                    }
                }
            }
            
            // Draw vertical progress line
            val progressX = canvasWidth * animatedProgress
            drawLine(
                color = progressLineColor,
                start = Offset(progressX, 0f),
                end = Offset(progressX, canvasHeight),
                strokeWidth = progressLineWidthPx
            )
        }
    }
}
