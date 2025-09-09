package com.example.mobiledigger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.audio.RealTimeSpectrumAnalyzer
import com.example.mobiledigger.audio.SpectrogramConfig
import kotlin.math.*

@Composable
fun RealTimeSpectrogramView(
    analyzer: RealTimeSpectrumAnalyzer,
    modifier: Modifier = Modifier
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    val isActive by analyzer.isActive.collectAsState()
    val spectrumData by analyzer.spectrumData.collectAsState()
    val currentConfig = remember { analyzer.getCurrentConfig() }
    
    // Show configuration dialog
    if (showConfigDialog) {
        SpectrogramConfigDialog(
            currentConfig = currentConfig,
            onConfigChange = { newConfig ->
                analyzer.updateConfig(newConfig)
            },
            onDismiss = { showConfigDialog = false }
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Real-time Spectrum Analyzer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    IconButton(
                        onClick = { showConfigDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure"
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            if (isActive) {
                                analyzer.stopAnalysis()
                            } else {
                                analyzer.startAnalysis()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isActive) "Stop Analysis" else "Start Analysis",
                            tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (isActive) Color.Green else Color.Gray,
                            RoundedCornerShape(6.dp)
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (isActive) "Analyzing microphone input" else "Stopped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Real-time spectrum display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isActive && spectrumData != null) {
                        RealTimeSpectrumCanvas(
                            spectrumData = spectrumData!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isActive) "Waiting for audio..." else "Click microphone to start",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Frequency labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "20Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "1kHz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "10kHz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "20kHz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RealTimeSpectrumCanvas(
    spectrumData: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Clear background
        drawRect(Color.Black)
        
        if (spectrumData.isNotEmpty()) {
            // Draw spectrum bars
            val barWidth = width / spectrumData.size
            val maxDb = 0f
            val minDb = -60f
            val dbRange = maxDb - minDb
            
            for (i in spectrumData.indices) {
                val db = spectrumData[i].coerceIn(minDb, maxDb)
                val normalizedHeight = (db - minDb) / dbRange
                val barHeight = normalizedHeight * height
                
                val x = i * barWidth
                val y = height - barHeight
                
                // Color based on frequency and amplitude
                val color = getSpectrumBarColor(i, spectrumData.size, normalizedHeight)
                
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
            
            // Draw grid lines
            drawGridLines(width, height)
        }
    }
}

private fun DrawScope.drawGridLines(width: Float, height: Float) {
    val gridColor = Color.White.copy(alpha = 0.2f)
    val strokeWidth = 1.dp.toPx()
    
    // Horizontal grid lines (dB levels)
    for (i in 0..6) {
        val y = height * i / 6
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = strokeWidth
        )
    }
    
    // Vertical grid lines (frequency markers)
    val freqMarkers = listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f) // Relative positions
    for (marker in freqMarkers) {
        val x = width * marker
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = strokeWidth
        )
    }
}

private fun getSpectrumBarColor(frequencyIndex: Int, totalFrequencies: Int, amplitude: Float): Color {
    // Frequency-based hue (low = red, high = blue)
    val frequencyRatio = frequencyIndex.toFloat() / totalFrequencies
    val hue = (1f - frequencyRatio) * 240f // 240 degrees = blue, 0 degrees = red
    
    // Amplitude-based saturation and value
    val saturation = 0.8f + (amplitude * 0.2f)
    val value = 0.3f + (amplitude * 0.7f)
    
    return Color.hsv(hue, saturation, value)
}

@Composable
fun RealTimeSpectrumLineView(
    spectrumData: FloatArray?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Clear background
        drawRect(Color.Black)
        
        if (spectrumData != null && spectrumData.isNotEmpty()) {
            // Create path for spectrum line
            val path = Path()
            val maxDb = 0f
            val minDb = -60f
            val dbRange = maxDb - minDb
            
            val xStep = width / (spectrumData.size - 1)
            
            for (i in spectrumData.indices) {
                val db = spectrumData[i].coerceIn(minDb, maxDb)
                val normalizedHeight = (db - minDb) / dbRange
                val y = height - (normalizedHeight * height)
                val x = i * xStep
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            // Draw spectrum line
            drawPath(
                path = path,
                color = Color.Green,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw grid lines
            drawGridLines(width, height)
        }
    }
}
