package com.example.mobiledigger.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun WaveformWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚠️ Waveform Generation Warning",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Enabling waveform generation may cause the app to crash on some AIFF files due to native library issues.\n\n" +
                        "The app will attempt to process audio files, but crashes may occur with large or corrupted files.\n\n" +
                        "Do you want to enable it anyway?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

