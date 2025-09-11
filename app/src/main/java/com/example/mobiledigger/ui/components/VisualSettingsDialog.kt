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
import com.example.mobiledigger.utils.HapticType

@Composable
fun VisualSettingsDialog(
    visualSettingsManager: VisualSettingsManager,
    onDismiss: () -> Unit
) {
    val settings by visualSettingsManager.settings
    var currentSettings by remember { mutableStateOf(settings) }
    var showColorPicker by remember { mutableStateOf<String?>(null) }
    val hapticFeedback = HapticFeedback.rememberHapticFeedback()
    
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
                
                // Color Settings Section
                VisualSettingsSection(
                    title = "Colors",
                    content = {
                        ColorSettingItem(
                            label = "Primary Color",
                            color = currentSettings.primaryColor,
                            onColorChange = { currentSettings = currentSettings.copy(primaryColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        ColorSettingItem(
                            label = "Secondary Color",
                            color = currentSettings.secondaryColor,
                            onColorChange = { currentSettings = currentSettings.copy(secondaryColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        ColorSettingItem(
                            label = "Accent Color",
                            color = currentSettings.accentColor,
                            onColorChange = { currentSettings = currentSettings.copy(accentColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        ColorSettingItem(
                            label = "Like Color",
                            color = currentSettings.likeColor,
                            onColorChange = { currentSettings = currentSettings.copy(likeColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        ColorSettingItem(
                            label = "Dislike Color",
                            color = currentSettings.dislikeColor,
                            onColorChange = { currentSettings = currentSettings.copy(dislikeColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        
                        SliderSettingItem(
                            label = "Background Opacity",
                            value = currentSettings.backgroundOpacity,
                            range = 0.1f..1.0f,
                            onValueChange = { currentSettings = currentSettings.copy(backgroundOpacity = it) }
                        )
                        SliderSettingItem(
                            label = "Surface Opacity",
                            value = currentSettings.surfaceOpacity,
                            range = 0.1f..1.0f,
                            onValueChange = { currentSettings = currentSettings.copy(surfaceOpacity = it) }
                        )
                    }
                )
                
                // Typography Settings Section
                VisualSettingsSection(
                    title = "Typography",
                    content = {
                        SliderSettingItem(
                            label = "Title Size",
                            value = currentSettings.titleSize,
                            range = 16f..32f,
                            onValueChange = { currentSettings = currentSettings.copy(titleSize = it) }
                        )
                        SliderSettingItem(
                            label = "Body Size",
                            value = currentSettings.bodySize,
                            range = 12f..24f,
                            onValueChange = { currentSettings = currentSettings.copy(bodySize = it) }
                        )
                        SliderSettingItem(
                            label = "Caption Size",
                            value = currentSettings.captionSize,
                            range = 8f..18f,
                            onValueChange = { currentSettings = currentSettings.copy(captionSize = it) }
                        )
                        SliderSettingItem(
                            label = "Button Size",
                            value = currentSettings.buttonSize,
                            range = 10f..20f,
                            onValueChange = { currentSettings = currentSettings.copy(buttonSize = it) }
                        )
                        SliderSettingItem(
                            label = "Global Scale Factor",
                            value = currentSettings.scaleFactor,
                            range = 0.5f..2.0f,
                            onValueChange = { currentSettings = currentSettings.copy(scaleFactor = it) }
                        )
                    }
                )
                
                // Layout Settings Section
                VisualSettingsSection(
                    title = "Layout",
                    content = {
                        SliderSettingItem(
                            label = "Card Corner Radius",
                            value = currentSettings.cardCornerRadius,
                            range = 0f..24f,
                            onValueChange = { currentSettings = currentSettings.copy(cardCornerRadius = it) }
                        )
                        SliderSettingItem(
                            label = "Button Corner Radius",
                            value = currentSettings.buttonCornerRadius,
                            range = 0f..16f,
                            onValueChange = { currentSettings = currentSettings.copy(buttonCornerRadius = it) }
                        )
                        SliderSettingItem(
                            label = "Small Padding",
                            value = currentSettings.paddingSmall,
                            range = 4f..16f,
                            onValueChange = { currentSettings = currentSettings.copy(paddingSmall = it) }
                        )
                        SliderSettingItem(
                            label = "Medium Padding",
                            value = currentSettings.paddingMedium,
                            range = 8f..32f,
                            onValueChange = { currentSettings = currentSettings.copy(paddingMedium = it) }
                        )
                        SliderSettingItem(
                            label = "Large Padding",
                            value = currentSettings.paddingLarge,
                            range = 16f..48f,
                            onValueChange = { currentSettings = currentSettings.copy(paddingLarge = it) }
                        )
                    }
                )
                
                // Waveform Settings Section
                VisualSettingsSection(
                    title = "Waveform",
                    content = {
                        SliderSettingItem(
                            label = "Main Waveform Height",
                            value = currentSettings.waveformHeight,
                            range = 40f..120f,
                            onValueChange = { currentSettings = currentSettings.copy(waveformHeight = it) }
                        )
                        SliderSettingItem(
                            label = "Mini Waveform Height",
                            value = currentSettings.miniWaveformHeight,
                            range = 30f..80f,
                            onValueChange = { currentSettings = currentSettings.copy(miniWaveformHeight = it) }
                        )
                        SliderSettingItem(
                            label = "Bar Width",
                            value = currentSettings.waveformBarWidth,
                            range = 0.5f..3f,
                            onValueChange = { currentSettings = currentSettings.copy(waveformBarWidth = it) }
                        )
                        SliderSettingItem(
                            label = "Bar Gap",
                            value = currentSettings.waveformGap,
                            range = 0f..2f,
                            onValueChange = { currentSettings = currentSettings.copy(waveformGap = it) }
                        )
                        SliderSettingItem(
                            label = "Waveform Opacity",
                            value = currentSettings.waveformOpacity,
                            range = 0.1f..1.0f,
                            onValueChange = { currentSettings = currentSettings.copy(waveformOpacity = it) }
                        )
                        ColorSettingItem(
                            label = "Waveform Color",
                            color = currentSettings.waveformColor,
                            onColorChange = { currentSettings = currentSettings.copy(waveformColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                        ColorSettingItem(
                            label = "Played Waveform Color",
                            color = currentSettings.waveformPlayedColor,
                            onColorChange = { currentSettings = currentSettings.copy(waveformPlayedColor = it) },
                            hapticFeedback = hapticFeedback
                        )
                    }
                )
                
                // Player Settings Section
                VisualSettingsSection(
                    title = "Player",
                    content = {
                        SliderSettingItem(
                            label = "Control Button Size",
                            value = currentSettings.controlButtonSize,
                            range = 24f..64f,
                            onValueChange = { currentSettings = currentSettings.copy(controlButtonSize = it) }
                        )
                    }
                )
                
                // Animation Settings Section
                VisualSettingsSection(
                    title = "Animation",
                    content = {
                        SwitchSettingItem(
                            label = "Enable Animations",
                            checked = currentSettings.enableAnimations,
                            onCheckedChange = { currentSettings = currentSettings.copy(enableAnimations = it) }
                        )
                        SliderSettingItem(
                            label = "Animation Speed",
                            value = currentSettings.animationSpeed,
                            range = 0.5f..2.0f,
                            onValueChange = { currentSettings = currentSettings.copy(animationSpeed = it) }
                        )
                        SwitchSettingItem(
                            label = "Haptic Feedback",
                            checked = currentSettings.enableHapticFeedback,
                            onCheckedChange = { currentSettings = currentSettings.copy(enableHapticFeedback = it) }
                        )
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
                            hapticFeedback(HapticType.Medium)
                            currentSettings = VisualSettings()
                        }
                    ) {
                        Text("Reset to Defaults")
                    }
                    
                    Row {
                        TextButton(onClick = {
                            hapticFeedback(HapticType.Light)
                            onDismiss()
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                hapticFeedback(HapticType.Success)
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
private fun ColorSettingItem(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit,
    hapticFeedback: (HapticType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color preview
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Simple color picker using predefined colors
            val predefinedColors = listOf(
                Color(0xFF10B981), // Green
                Color(0xFF2196F3), // Blue
                Color(0xFFEFB8C8), // Pink
                Color(0xFF4CAF50), // Like Green
                Color(0xFFE91E63), // Dislike Red
                Color(0xFFFF8C00), // Orange
                Color(0xFF9C27B0), // Purple
                Color(0xFF00BCD4)  // Cyan
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                predefinedColors.forEach { predefinedColor ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(predefinedColor, RoundedCornerShape(2.dp))
                            .background(
                                if (color == predefinedColor) Color.White else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                            .pointerInput(predefinedColor) {
                                detectTapGestures {
                                    hapticFeedback(HapticType.Selection)
                                    onColorChange(predefinedColor)
                                }
                            }
                    ) {
                        if (color == predefinedColor) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderSettingItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
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
            Text(
                text = "%.1f".format(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onCheckedChange: (Boolean) -> Unit
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSettingItem(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
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
