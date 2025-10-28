package com.example.mobiledigger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistSeekBar(
    progress: Float, // 0.0 to 1.0
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    progressColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tapProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(tapProgress)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val progressX = progress * canvasWidth
            
            // Draw background
            drawRect(
                color = backgroundColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
            )
            
            // Draw progress line
            drawLine(
                color = progressColor,
                start = Offset(progressX, 0f),
                end = Offset(progressX, canvasHeight),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}
