package com.example.mobiledigger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobiledigger.audio.*
import com.example.mobiledigger.audio.ColorScheme as AudioColorScheme

@Composable
fun SpectrogramConfigDialog(
    currentConfig: SpectrogramConfig,
    onConfigChange: (SpectrogramConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var localConfig by remember { mutableStateOf(currentConfig) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
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
                        text = "Spectrogram Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                // Configuration sections
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ResolutionSection(
                            currentResolution = localConfig.resolution,
                            onResolutionChange = { localConfig = localConfig.copy(resolution = it) }
                        )
                    }
                    
                    item {
                        ColorSchemeSection(
                            currentScheme = localConfig.colorScheme,
                            onSchemeChange = { localConfig = localConfig.copy(colorScheme = it) }
                        )
                    }
                    
                    item {
                        WindowFunctionSection(
                            currentFunction = localConfig.windowFunction,
                            onFunctionChange = { localConfig = localConfig.copy(windowFunction = it) }
                        )
                    }
                    
                    item {
                        AnalysisTypeSection(
                            currentType = localConfig.analysisType,
                            onTypeChange = { localConfig = localConfig.copy(analysisType = it) }
                        )
                    }
                    
                    item {
                        PresetsSection(
                            onPresetSelected = { localConfig = it }
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            onConfigChange(localConfig)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionSection(
    currentResolution: SpectrogramResolution,
    onResolutionChange: (SpectrogramResolution) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Resolution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SpectrogramResolution.values().forEach { resolution ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentResolution == resolution,
                            onClick = { onResolutionChange(resolution) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentResolution == resolution,
                        onClick = null
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = resolution.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${resolution.width}×${resolution.height} pixels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSchemeSection(
    currentScheme: AudioColorScheme,
    onSchemeChange: (AudioColorScheme) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Color Scheme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SpectrogramColorMapper().let { colorMapper ->
                AudioColorScheme.values().forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentScheme == scheme,
                                onClick = { onSchemeChange(scheme) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentScheme == scheme,
                            onClick = null
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = colorMapper.getColorSchemeDescription(scheme),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowFunctionSection(
    currentFunction: WindowFunction,
    onFunctionChange: (WindowFunction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Window Function",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            WindowFunction.values().forEach { function ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentFunction == function,
                            onClick = { onFunctionChange(function) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentFunction == function,
                        onClick = null
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = function.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = getWindowFunctionDescription(function),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisTypeSection(
    currentType: AnalysisType,
    onTypeChange: (AnalysisType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Analysis Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AnalysisType.values().forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentType == type,
                            onClick = { onTypeChange(type) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentType == type,
                        onClick = null
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = type.name.replace("_", " "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = getAnalysisTypeDescription(type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsSection(
    onPresetSelected: (SpectrogramConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPresetSelected(SpectrogramPresets.SPEK_DEFAULT) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Spek Default", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = { onPresetSelected(SpectrogramPresets.REAL_TIME) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Real-time", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = { onPresetSelected(SpectrogramPresets.ULTRA_DETAILED) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ultra Detailed", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun getWindowFunctionDescription(function: WindowFunction): String {
    return when (function) {
        WindowFunction.HANNING -> "Good general purpose, smooth transitions"
        WindowFunction.HAMMING -> "Slightly better frequency resolution"
        WindowFunction.BLACKMAN -> "Better side lobe suppression"
        WindowFunction.RECTANGULAR -> "No windowing, may cause artifacts"
        WindowFunction.KAISER -> "Adjustable parameter, professional use"
    }
}

private fun getAnalysisTypeDescription(type: AnalysisType): String {
    return when (type) {
        AnalysisType.POWER_SPECTRUM -> "Magnitude squared (recommended)"
        AnalysisType.MAGNITUDE_SPECTRUM -> "Linear magnitude"
        AnalysisType.PHASE_SPECTRUM -> "Phase information"
        AnalysisType.COMPLEX_SPECTRUM -> "Full complex spectrum"
    }
}
