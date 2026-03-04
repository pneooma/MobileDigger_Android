package com.example.mobiledigger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.ui.theme.WaveformAnimationType

@Composable
fun WaveformWithToggle(
    sharedState: SharedWaveformState,
    progress: Float,
    onSeek: (Float) -> Unit,
    songUri: String,
    modifier: Modifier = Modifier,
    waveformHeight: Int = 100,
    currentPosition: Long = 0L,
    totalDuration: Long = 0L,
    fileName: String = "",
    opacity: Float = 0.8f,
    customWaveformColor: Color? = null,
    animationType: WaveformAnimationType = WaveformAnimationType.EXPAND,
    animationDurationMs: Int = 300
) {
    val context = LocalContext.current
    var isWaveformEnabled by remember { mutableStateOf(isWaveformGenerationEnabled(context)) }

    // Get color for this song
    val songColor = remember(songUri) { getColorForSong(songUri) }

    Box(modifier = modifier) {
        // Waveform display with appropriate background
        SharedWaveformDisplay(
            sharedState = sharedState,
            progress = progress,
            onSeek = onSeek,
            currentPosition = currentPosition,
            totalDuration = totalDuration,
            fileName = fileName,
            opacity = opacity,
            customWaveformColor = customWaveformColor,
            animationType = animationType,
            animationDurationMs = animationDurationMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(waveformHeight.dp),
            backgroundColor = if (isWaveformEnabled) {
                Color.Transparent
            } else {
                songColor.copy(alpha = 0.3f)
            }
        )

        // Toggle button in top-left corner
        IconButton(
            onClick = {
                if (isWaveformEnabled) {
                    setWaveformGenerationEnabled(context, false)
                    isWaveformEnabled = false
                } else {
                    setWaveformGenerationEnabled(context, true)
                    isWaveformEnabled = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SignalCellularAlt,
                contentDescription = if (isWaveformEnabled) "Disable Waveform" else "Enable Waveform",
                tint = if (isWaveformEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
