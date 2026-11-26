package com.example.mobiledigger.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showDeskRemote by remember { mutableStateOf(false) }
    val deskRemote by com.example.mobiledigger.remote.DeskRemote.state.collectAsState()
    if (showDeskRemote) com.example.mobiledigger.ui.screens.DeskRemoteDialog { showDeskRemote = false }

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
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: title + close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Pill tabs with icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                        .padding(4.dp)
                ) {
                    PillTab("General", Icons.Default.Tune, selectedTab == 0, Modifier.weight(1f)) { selectedTab = 0 }
                    PillTab("Visual", Icons.Default.Palette, selectedTab == 1, Modifier.weight(1f)) { selectedTab = 1 }
                    PillTab("History", Icons.Default.History, selectedTab == 3, Modifier.weight(1f)) { selectedTab = 3 }
                    PillTab("About", Icons.Default.Info, selectedTab == 2, Modifier.weight(1f)) { selectedTab = 2 }
                }

                // Tab content with crossfade
                Crossfade(targetState = selectedTab, label = "settingsTab", modifier = Modifier.weight(1f)) { tab ->
                    // History has its own scrolling list (can't sit inside the scrolling column)
                    if (tab == 3) { com.example.mobiledigger.ui.screens.SortHistoryPanel(); return@Crossfade }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(6.dp))
                        when (tab) {
                            0 -> {
                                // Desk remote: control playback from a nearby computer over Bluetooth
                                OutlinedCard(
                                    onClick = { showDeskRemote = true },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Bluetooth, contentDescription = null,
                                            tint = if (deskRemote.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Desk remote (Bluetooth)", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (deskRemote.on) "On · PIN ${deskRemote.pin}" + if (deskRemote.unlocked > 0) " · computer connected" else ""
                                                else "Control next / like / dislike from your computer",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(if (deskRemote.on) "Open" else "Set up", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                GeneralTab(currentSettings, themeManager) { currentSettings = it }
                            }
                            1 -> VisualTab(currentSettings) { currentSettings = it }
                            2 -> AboutTab()
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { currentSettings = VisualSettings() }) {
                        Text("Reset", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                visualSettingsManager.updateSettings(currentSettings)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp)
                        ) { Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "pillBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillFg"
    )
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/** Grouped section card — gives settings the modern "grouped list" look. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun GeneralTab(
    settings: VisualSettings,
    themeManager: ThemeManager,
    onUpdate: (VisualSettings) -> Unit
) {
    SectionCard("Appearance") {
        SettingsSwitch("Dark Mode", themeManager.isDarkMode.value) {
            themeManager.toggleDarkMode()
        }
        SettingsSwitch("Enable Theme", !themeManager.useDynamicColor.value) {
            themeManager.toggleDynamicColor()
        }

        // Theme selector
        val selectedTheme by themeManager.selectedTheme
        var showThemePicker by remember { mutableStateOf(false) }
        Button(
            onClick = { showThemePicker = !showThemePicker },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(selectedTheme.primary, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Theme: ${selectedTheme.name}", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(if (showThemePicker) "▲" else "▼", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showThemePicker) {
            Spacer(Modifier.height(4.dp))
            val themes = themeManager.availableThemes
            themes.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { theme ->
                        val isSel = theme.name == selectedTheme.name
                        OutlinedButton(
                            onClick = { themeManager.selectTheme(theme.name) },
                            modifier = Modifier.weight(1f).height(30.dp),
                            shape = RoundedCornerShape(10.dp),
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
    }

    SectionCard("Behavior") {
        SettingsSwitch("Auto-play first track after selecting source", settings.autoPlayFirstAfterSelect) {
            onUpdate(settings.copy(autoPlayFirstAfterSelect = it))
        }
        SettingsSwitch("Auto-scan last source on start", settings.autoScanLastSourceOnStart) {
            onUpdate(settings.copy(autoScanLastSourceOnStart = it))
        }
        SettingsSwitch("Show toolbar icon labels", settings.showToolbarLabels) {
            onUpdate(settings.copy(showToolbarLabels = it))
        }
    }

    SectionCard("Font") {
        val fontNames = appFontFamilies.keys.toList()
        fontNames.chunked(3).forEach { rowFonts ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowFonts.forEach { fontName ->
                    val isSelected = fontName == settings.fontFamily
                    OutlinedButton(
                        onClick = { onUpdate(settings.copy(fontFamily = fontName)) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        shape = RoundedCornerShape(10.dp),
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
}

@Composable
private fun VisualTab(
    settings: VisualSettings,
    onUpdate: (VisualSettings) -> Unit
) {
    SectionCard("Waveform") {
        CompactSlider("Main Height", settings.waveformHeight, 40f..225f) {
            onUpdate(settings.copy(waveformHeight = it))
        }
        CompactSlider("Mini Height", settings.miniWaveformHeight, 30f..195f) {
            onUpdate(settings.copy(miniWaveformHeight = it))
        }
        CompactSlider("Row Height", settings.rowWaveformHeight, 30f..200f) {
            onUpdate(settings.copy(rowWaveformHeight = it))
        }
    }

    SectionCard("Waveform Color") {
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }

    SectionCard("Animations") {
        SettingsSwitch("Enable Animations", settings.enableAnimations) {
            onUpdate(settings.copy(enableAnimations = it))
        }
        Text("Waveform Show/Hide Animation", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    shape = RoundedCornerShape(50),
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
        Spacer(Modifier.height(6.dp))
        CompactSlider("Overall speed (× all below)", settings.animationSpeed, 0.05f..3.0f) {
            onUpdate(settings.copy(animationSpeed = it))
        }
        Text("Durations in milliseconds. Each is divided by the overall speed; 0 = instant.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        MsSlider("Like / Reject swipe out", settings.animSwipeOutMs, 0f..2000f) { onUpdate(settings.copy(animSwipeOutMs = it)) }
        MsSlider("Row fade when sorted (buttons)", settings.animRemoveMs, 0f..2000f) { onUpdate(settings.copy(animRemoveMs = it)) }
        MsSlider("List rows sliding into place", settings.animReflowMs, 0f..2000f) { onUpdate(settings.copy(animReflowMs = it)) }
        MsSlider("Unfinished swipe springs back", settings.animSettleMs, 0f..1500f) { onUpdate(settings.copy(animSettleMs = it)) }
        MsSlider("Next track slides in", settings.animArriveMs, 0f..1500f) { onUpdate(settings.copy(animArriveMs = it)) }
        MsSlider("Main player show / hide", settings.animPanelMs, 0f..2500f) { onUpdate(settings.copy(animPanelMs = it)) }
        MsSlider("Playing row expands", settings.animRowExpandMs, 0f..2000f) { onUpdate(settings.copy(animRowExpandMs = it)) }
        MsSlider("Waveform show / hide", settings.animWaveformMs, 0f..2000f) { onUpdate(settings.copy(animWaveformMs = it)) }
        MsSlider("Play / pause icon", settings.animPlayPauseMs, 0f..1000f) { onUpdate(settings.copy(animPlayPauseMs = it)) }
        TextButton(onClick = {
            val d = com.example.mobiledigger.ui.theme.VisualSettings()
            onUpdate(settings.copy(
                animationSpeed = d.animationSpeed, animSwipeOutMs = d.animSwipeOutMs, animRemoveMs = d.animRemoveMs,
                animReflowMs = d.animReflowMs, animSettleMs = d.animSettleMs, animArriveMs = d.animArriveMs,
                animPanelMs = d.animPanelMs, animRowExpandMs = d.animRowExpandMs, animWaveformMs = d.animWaveformMs,
                animPlayPauseMs = d.animPlayPauseMs
            ))
        }) { Text("Reset animation timings") }
    }

    SectionCard("Mini Player") {
        SettingsSwitch("Enable Mini Player", settings.enableMiniPlayer) {
            onUpdate(settings.copy(enableMiniPlayer = it))
        }
    }
}

@Composable
private fun AboutTab() {
    Spacer(Modifier.height(12.dp))
    Text("MobileDigger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text("v${com.example.mobiledigger.BuildConfig.VERSION_NAME} · groovy's child", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))

    SectionCard("What it does") {
        Text(
            "Dig through your music fast: listen, like or reject, and every decision is sorted into folders for you. Made for clearing big download piles and finding the gems.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    SectionCard("Features") {
        Text("• Plays MP3, WAV, FLAC, AAC, OGG, M4A, AIFF", style = MaterialTheme.typography.bodySmall)
        Text("• Like / dislike triage with automatic file sorting", style = MaterialTheme.typography.bodySmall)
        Text("• Waveform seeking and spectrogram quality check", style = MaterialTheme.typography.bodySmall)
        Text("• Streams from a Raspberry Pi library over Wi-Fi", style = MaterialTheme.typography.bodySmall)
        Text("• Full controls on lock screen and headphones", style = MaterialTheme.typography.bodySmall)
        Text("• Rename, search, sort, filter and bulk actions", style = MaterialTheme.typography.bodySmall)
    }

    SectionCard("Gestures") {
        Text("• Swipe a track left to reject, right to like", style = MaterialTheme.typography.bodySmall)
        Text("• Tap or drag the waveform to seek", style = MaterialTheme.typography.bodySmall)
        Text("• Long-press the filename to rename", style = MaterialTheme.typography.bodySmall)
        Text("• Headphones: next = dislike, previous = like", style = MaterialTheme.typography.bodySmall)
    }

    SectionCard("Good to know") {
        Text("• Pick a Source folder to dig and a Destination for the sorted results", style = MaterialTheme.typography.bodySmall)
        Text("• Pi tracks stream in place — files never move on the Pi", style = MaterialTheme.typography.bodySmall)
        Text("• Deleting from Rejected is permanent", style = MaterialTheme.typography.bodySmall)
        Text("• Everything stays on your device — no accounts, no tracking", style = MaterialTheme.typography.bodySmall)
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "Built with libVLC · Media3 · Jetpack Compose",
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

/** Duration slider in 10 ms steps, showing the value and the effective time after the overall speed. */
@Composable
private fun MsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${value.toInt()} ms", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
        Slider(
            value = value,
            onValueChange = { onValueChange((it / 10f).toInt() * 10f) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }
}
