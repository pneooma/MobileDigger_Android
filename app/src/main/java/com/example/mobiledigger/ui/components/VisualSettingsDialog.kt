package com.example.mobiledigger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobiledigger.ui.theme.VisualSettings
import com.example.mobiledigger.ui.theme.VisualSettingsManager
import com.example.mobiledigger.ui.theme.WaveformAnimationType
import com.example.mobiledigger.ui.theme.ThemeManager
import com.example.mobiledigger.ui.theme.appFontFamilies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    visualSettingsManager: VisualSettingsManager,
    themeManager: ThemeManager,
    onDismiss: () -> Unit
) {
    val settings by visualSettingsManager.settings
    var currentSettings by remember { mutableStateOf(settings) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Visual", "About")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
                    textAlign = TextAlign.Center
                )

                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 13.sp, maxLines = 1) }
                        )
                    }
                }

                // Tab content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(8.dp))

                    when (selectedTab) {
                        0 -> GeneralTab(currentSettings, themeManager) { currentSettings = it }
                        1 -> VisualTab(currentSettings) { currentSettings = it }
                        2 -> AboutTab()
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { currentSettings = VisualSettings() }) {
                        Text("Reset", fontSize = 12.sp)
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = {
                            visualSettingsManager.updateSettings(currentSettings)
                            onDismiss()
                        }) { Text("Apply", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(
    settings: VisualSettings,
    themeManager: ThemeManager,
    onUpdate: (VisualSettings) -> Unit
) {
    // Dark Mode
    SettingsSwitch("Dark Mode", themeManager.isDarkMode.value) {
        themeManager.toggleDarkMode()
    }

    // Enable Theme
    SettingsSwitch("Enable Theme", !themeManager.useDynamicColor.value) {
        themeManager.toggleDynamicColor()
    }

    // Theme selector
    val selectedTheme by themeManager.selectedTheme
    var showThemePicker by remember { mutableStateOf(false) }
    Button(
        onClick = { showThemePicker = !showThemePicker },
        modifier = Modifier.fillMaxWidth().height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(selectedTheme.primary, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Theme: ${selectedTheme.name}", fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(if (showThemePicker) "▲" else "▼", fontSize = 12.sp)
        }
    }
    if (showThemePicker) {
        val themes = themeManager.availableThemes
        themes.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { theme ->
                    val isSel = theme.name == selectedTheme.name
                    OutlinedButton(
                        onClick = { themeManager.selectTheme(theme.name) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSel) 2.dp else 1.dp,
                            if (isSel) theme.primary else MaterialTheme.colorScheme.outline
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Box(Modifier.size(10.dp).background(theme.primary, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(theme.name, fontSize = 9.sp, maxLines = 1, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Behavior
    Text("Behavior", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))

    SettingsSwitch("Auto-play first track after selecting source", settings.autoPlayFirstAfterSelect) {
        onUpdate(settings.copy(autoPlayFirstAfterSelect = it))
    }
    SettingsSwitch("Auto-scan last source on start", settings.autoScanLastSourceOnStart) {
        onUpdate(settings.copy(autoScanLastSourceOnStart = it))
    }
    SettingsSwitch("Show toolbar icon labels", settings.showToolbarLabels) {
        onUpdate(settings.copy(showToolbarLabels = it))
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Font family
    Text("Font", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    val fontNames = appFontFamilies.keys.toList()
    fontNames.chunked(3).forEach { rowFonts ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rowFonts.forEach { fontName ->
                val isSelected = fontName == settings.fontFamily
                OutlinedButton(
                    onClick = { onUpdate(settings.copy(fontFamily = fontName)) },
                    modifier = Modifier.weight(1f).height(30.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(fontName, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
            repeat(3 - rowFonts.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun VisualTab(
    settings: VisualSettings,
    onUpdate: (VisualSettings) -> Unit
) {
    // Waveform Heights
    Text("Waveform", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))

    CompactSlider("Main Height", settings.waveformHeight, 40f..225f) {
        onUpdate(settings.copy(waveformHeight = it))
    }
    CompactSlider("Mini Height", settings.miniWaveformHeight, 30f..195f) {
        onUpdate(settings.copy(miniWaveformHeight = it))
    }
    CompactSlider("Row Height", settings.rowWaveformHeight, 30f..200f) {
        onUpdate(settings.copy(rowWaveformHeight = it))
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Waveform color (0 = Default / per-song color)
    Text("Waveform Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    val waveformColorOptions = listOf(
        "Default" to 0,
        "Pink" to Color(0xFFFF6EC7).toArgb(),
        "Red" to Color(0xFFE53935).toArgb(),
        "Orange" to Color(0xFFFB8C00).toArgb(),
        "Yellow" to Color(0xFFFDD835).toArgb(),
        "Green" to Color(0xFF43A047).toArgb(),
        "Cyan" to Color(0xFF00ACC1).toArgb(),
        "Blue" to Color(0xFF1E88E5).toArgb(),
        "Purple" to Color(0xFF8E24AA).toArgb(),
        "White" to Color(0xFFFFFFFF).toArgb()
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        waveformColorOptions.forEach { (label, argb) ->
            val isSelected = settings.waveformColor == argb
            val swatchColor = if (argb == 0) MaterialTheme.colorScheme.primary else Color(argb)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(swatchColor, CircleShape)
                        .border(
                            androidx.compose.foundation.BorderStroke(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            ),
                            CircleShape
                        )
                        .clickable { onUpdate(settings.copy(waveformColor = argb)) }
                )
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 8.sp, maxLines = 1)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Animations
    Text("Animations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))

    SettingsSwitch("Enable Animations", settings.enableAnimations) {
        onUpdate(settings.copy(enableAnimations = it))
    }

    // Waveform animation type
    Text("Waveform Show/Hide Animation:", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    val animTypes = WaveformAnimationType.entries
    val animLabels = mapOf(
        WaveformAnimationType.EXPAND to "Expand",
        WaveformAnimationType.SLIDE_UP to "Slide Up",
        WaveformAnimationType.SLIDE_DOWN to "Slide Down",
        WaveformAnimationType.FADE to "Fade",
        WaveformAnimationType.SCALE to "Scale",
        WaveformAnimationType.RANDOM to "Random",
        WaveformAnimationType.NONE to "None"
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        animTypes.forEach { type ->
            val isSelected = type == settings.waveformAnimationType
            OutlinedButton(
                onClick = { onUpdate(settings.copy(waveformAnimationType = type)) },
                modifier = Modifier.height(28.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    animLabels[type] ?: type.name,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    CompactSlider("Animation Speed", settings.animationSpeed, 0.05f..3.0f) {
        onUpdate(settings.copy(animationSpeed = it))
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Mini Player
    Text("Mini Player", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    SettingsSwitch("Enable Mini Player", settings.enableMiniPlayer) {
        onUpdate(settings.copy(enableMiniPlayer = it))
    }
}

@Composable
private fun AboutTab() {
    Spacer(Modifier.height(16.dp))
    Text("MobileDigger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text("groovy's child", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text("Gestures", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("• Swipe card left to reject", style = MaterialTheme.typography.bodySmall)
    Text("• Swipe card right to like", style = MaterialTheme.typography.bodySmall)
    Text("• Tap and drag waveform to seek", style = MaterialTheme.typography.bodySmall)
    Text("• Long-press filename to rename", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(if (value < 10f) "%.1f".format(value) else "%.0f".format(value), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(40.dp)
        )
    }
}
