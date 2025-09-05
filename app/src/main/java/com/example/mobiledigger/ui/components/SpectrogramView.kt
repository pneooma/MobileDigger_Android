package com.example.mobiledigger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
import kotlinx.coroutines.launch

@Composable
fun SpectrogramView(
    musicFile: MusicFile?,
    audioManager: AudioManager,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    var showPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Simple approach: only show popup when requested
    if (showPopup) {
        SpectrogramPopupDialog(
            musicFile = musicFile,
            audioManager = audioManager,
            onDismiss = { 
                showPopup = false 
            }
        )
    }
    
    // Show a simple button to generate spectrogram
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (showTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spectrogram",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = { 
                            showPopup = true 
                        }
                    ) {
                        Text("Generate Spectrogram")
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