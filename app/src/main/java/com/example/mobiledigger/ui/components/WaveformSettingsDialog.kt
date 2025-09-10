package com.example.mobiledigger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.model.WaveformSettings
import com.example.mobiledigger.model.UpdateFrequency
import com.example.mobiledigger.utils.WaveformGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformSettingsDialog(
    onDismiss: () -> Unit,
    onApply: (WaveformSettings) -> Unit
) {
    var currentSettings by remember { mutableStateOf(WaveformGenerator.getCurrentSettings()) }
    var selectedPreset by remember { mutableStateOf("Custom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Waveform Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preset Selection
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    val presets = listOf(
                        "High Quality" to WaveformSettings.HIGH_QUALITY,
                        "Balanced" to WaveformSettings.BALANCED,
                        "Performance" to WaveformSettings.PERFORMANCE,
                        "Custom" to currentSettings
                    )
                    
                    presets.forEach { (name, settings) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPreset == name,
                                    onClick = {
                                        selectedPreset = name
                                        if (name != "Custom") {
                                            currentSettings = settings
                                        }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPreset == name,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Bars: ${settings.barCount}, Candles: ${settings.candleWidth}px, MaxRender: ${settings.maxBarsToRender}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Custom Settings (only show when Custom is selected)
                if (selectedPreset == "Custom") {
                    Text(
                        text = "Custom Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Bar Count
                    Column {
                        Text(
                            text = "Bar Count: ${currentSettings.barCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = currentSettings.barCount.toFloat(),
                            onValueChange = { value ->
                                currentSettings = currentSettings.copy(barCount = value.toInt())
                            },
                            valueRange = 100f..2000f,
                            steps = 18
                        )
                    }

                    // Buffer Size
                    Column {
                        Text(
                            text = "Buffer Size: ${currentSettings.bufferSize}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = currentSettings.bufferSize.toFloat(),
                            onValueChange = { value ->
                                currentSettings = currentSettings.copy(bufferSize = value.toInt())
                            },
                            valueRange = 2048f..16384f,
                            steps = 6
                        )
                    }

                    // Candle Width
                    Column {
                        Text(
                            text = "Candle Width: ${currentSettings.candleWidth}px",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = currentSettings.candleWidth,
                            onValueChange = { value ->
                                currentSettings = currentSettings.copy(candleWidth = value)
                            },
                            valueRange = 1f..8f,
                            steps = 6
                        )
                    }

                    // Candle Height Scale
                    Column {
                        Text(
                            text = "Height Scale: ${currentSettings.candleHeightScale}x",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = currentSettings.candleHeightScale,
                            onValueChange = { value ->
                                currentSettings = currentSettings.copy(candleHeightScale = value)
                            },
                            valueRange = 1f..6f,
                            steps = 4
                        )
                    }

                    // Max Bars to Render
                    Column {
                        Text(
                            text = "Max Bars to Render: ${currentSettings.maxBarsToRender}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = currentSettings.maxBarsToRender.toFloat(),
                            onValueChange = { value ->
                                currentSettings = currentSettings.copy(maxBarsToRender = value.toInt())
                            },
                            valueRange = 100f..1000f,
                            steps = 8
                        )
                    }

                    // Update Frequency
                    Column {
                        Text(
                            text = "Update Frequency: ${currentSettings.updateFrequency.name} (${currentSettings.updateFrequency.milliseconds}ms)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UpdateFrequency.values().forEach { frequency ->
                                FilterChip(
                                    onClick = { 
                                        currentSettings = currentSettings.copy(updateFrequency = frequency)
                                    },
                                    label = { Text(frequency.name) },
                                    selected = currentSettings.updateFrequency == frequency,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Min/Max Amplitude (iOS-style Float values)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Min Amplitude: ${currentSettings.minAmplitude}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = currentSettings.minAmplitude,
                                onValueChange = { value ->
                                    currentSettings = currentSettings.copy(minAmplitude = value)
                                },
                                valueRange = 0.0001f..0.01f,
                                steps = 99
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Max Amplitude: ${currentSettings.maxAmplitude}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = currentSettings.maxAmplitude,
                                onValueChange = { value ->
                                    currentSettings = currentSettings.copy(maxAmplitude = value)
                                },
                                valueRange = 0.5f..2.0f,
                                steps = 14
                            )
                        }
                    }
                }

                // Real-time Preview Info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Current Settings Preview",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Bars: ${currentSettings.barCount} (${if (currentSettings.barCount > 800) "High Detail" else if (currentSettings.barCount > 400) "Medium Detail" else "Low Detail"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• iOS Approach: samplesPerPoint calculation, no oversampling",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Candlestick Rendering: ${currentSettings.candleWidth}px width, ${currentSettings.candleHeightScale}x height scale",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Performance: Max ${currentSettings.maxBarsToRender} bars, ${currentSettings.updateFrequency.milliseconds}ms updates",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Buffer Size: ${currentSettings.bufferSize} bytes (${if (currentSettings.bufferSize > 8192) "High Memory" else if (currentSettings.bufferSize > 4096) "Balanced" else "Low Memory"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(currentSettings)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
