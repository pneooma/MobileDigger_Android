package com.example.mobiledigger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobiledigger.ui.theme.VisualSettings
import com.example.mobiledigger.ui.theme.VisualSettingsManager
import com.example.mobiledigger.utils.HapticFeedback
import com.example.mobiledigger.ui.theme.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsDialog(
    visualSettingsManager: VisualSettingsManager,
    onDismiss: () -> Unit
) {
    val settings by visualSettingsManager.settings
    var currentSettings by remember { mutableStateOf(settings) }
    var showColorPicker by remember { mutableStateOf<String?>(null) }
    val hapticFeedback = HapticFeedback.rememberHapticFeedback(currentSettings) // Pass currentSettings
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visual Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Waveform Settings Section (modified)
                VisualSettingsSection(
                    title = "Waveform",
                    content = {
                        SliderSettingItem(
                            label = "Main Waveform Height",
                            value = currentSettings.waveformHeight,
                            range = 40f..150f,
                            onValueChange = { currentSettings = currentSettings.copy(waveformHeight = it) },
                            onReset = { currentSettings = currentSettings.copy(waveformHeight = settings.waveformHeight) }
                        )
                        SliderSettingItem(
                            label = "Mini Waveform Height",
                            value = currentSettings.miniWaveformHeight,
                            range = 30f..130f,
                            onValueChange = { currentSettings = currentSettings.copy(miniWaveformHeight = it) },
                            onReset = { currentSettings = currentSettings.copy(miniWaveformHeight = settings.miniWaveformHeight) }
                        )
                        SliderSettingItem(
                            label = "Row Waveform Height",
                            value = currentSettings.rowWaveformHeight,
                            range = 30f..130f,
                            onValueChange = { currentSettings = currentSettings.copy(rowWaveformHeight = it) },
                            onReset = { currentSettings = currentSettings.copy(rowWaveformHeight = settings.rowWaveformHeight) }
                        )
                        // Removed Bar Width
                        // Removed Bar Gap
                        // Removed Waveform Opacity
                        // Removed Waveform Color
                        // Removed Played Waveform Color
                    }
                )
                
                // Animation Settings Section
                VisualSettingsSection(
                    title = "Animation",
                    content = {
                        SwitchSettingItem(
                            label = "Enable Animations",
                            checked = currentSettings.enableAnimations,
                            onCheckedChange = { currentSettings = currentSettings.copy(enableAnimations = it) },
                            onReset = { currentSettings = currentSettings.copy(enableAnimations = settings.enableAnimations) }
                        )
                        SliderSettingItem(
                            label = "Animation Speed",
                            value = currentSettings.animationSpeed,
                            range = 0.5f..2.0f,
                            onValueChange = { currentSettings = currentSettings.copy(animationSpeed = it) },
                            onReset = { currentSettings = currentSettings.copy(animationSpeed = settings.animationSpeed) }
                        )
                        SwitchSettingItem(
                            label = "Haptic Feedback",
                            checked = currentSettings.enableHapticFeedback,
                            onCheckedChange = { currentSettings = currentSettings.copy(enableHapticFeedback = it) },
                            onReset = { currentSettings = currentSettings.copy(enableHapticFeedback = settings.enableHapticFeedback) }
                        )
                        // New Haptic Feedback Type Dropdown
                        if (currentSettings.enableHapticFeedback) {
                            DropdownSettingItem(
                                label = "Haptic Feedback Type",
                                value = currentSettings.hapticFeedbackType.name,
                                options = HapticFeedbackType.entries.map { it.name },
                                onValueChange = { newType ->
                                    currentSettings = currentSettings.copy(hapticFeedbackType = HapticFeedbackType.valueOf(newType))
                                },
                                onReset = { currentSettings = currentSettings.copy(hapticFeedbackType = settings.hapticFeedbackType) }
                            )
                            // New Haptic Feedback Intensity Slider
                            SliderSettingItem(
                                label = "Haptic Feedback Intensity",
                                value = currentSettings.hapticFeedbackIntensity,
                                range = 0.0f..1.0f,
                                onValueChange = { currentSettings = currentSettings.copy(hapticFeedbackIntensity = it) },
                                onReset = { currentSettings = currentSettings.copy(hapticFeedbackIntensity = settings.hapticFeedbackIntensity) }
                            )
                        }
                    }
                )
                
                }
                
                // Fixed action buttons at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            hapticFeedback() // Simplified call
                            currentSettings = VisualSettings()
                        }
                    ) {
                        Text("Reset to Defaults")
                    }
                    
                    Row {
                        TextButton(onClick = {
                            hapticFeedback() // Simplified call
                            onDismiss()
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                hapticFeedback() // Simplified call
                                visualSettingsManager.updateSettings(currentSettings)
                                onDismiss()
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualSettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SliderSettingItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%.1f".format(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onReset) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchSettingItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            IconButton(onClick = onReset) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSettingItem(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
