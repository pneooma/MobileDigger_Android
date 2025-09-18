package com.example.mobiledigger.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.example.mobiledigger.R
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
import com.example.mobiledigger.ui.components.ScrollableWaveformView
import com.example.mobiledigger.ui.components.SharedWaveformState
import com.example.mobiledigger.ui.components.rememberSharedWaveformState
import com.example.mobiledigger.ui.components.SharedWaveformDisplay
import com.example.mobiledigger.ui.components.VisualSettingsDialog
import com.example.mobiledigger.utils.HapticFeedback
import com.example.mobiledigger.utils.WaveformGenerator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay // For deprecated icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with


import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.ui.theme.DislikeRed
import com.example.mobiledigger.ui.theme.GreenAccent
import com.example.mobiledigger.ui.theme.GroovyBlue
import com.example.mobiledigger.ui.theme.LikeGreen
import com.example.mobiledigger.ui.theme.NoButton
import com.example.mobiledigger.ui.theme.YesButton
import com.example.mobiledigger.ui.theme.AvailableThemes
import com.example.mobiledigger.viewmodel.MusicViewModel
import com.example.mobiledigger.viewmodel.PlaylistTab

import kotlin.math.abs
import java.util.Locale

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

private fun calculateBitrate(fileSizeBytes: Long, durationMs: Long): Int {
    if (durationMs <= 0) return 0
    val durationSeconds = durationMs / 1000.0
    val fileSizeBits = fileSizeBytes * 8
    return (fileSizeBits / durationSeconds / 1000).toInt() // Convert to kbps
}

private fun formatFileSize(sizeBytes: Long): String {
    val sizeMB = sizeBytes / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", sizeMB)
}





@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class) // Combined annotations
@Composable
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues()
) {
    val musicFiles by viewModel.musicFiles.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showDeleteRejectedPrompt by viewModel.showDeleteRejectedPrompt.collectAsState()
    val currentPlaylistTab by viewModel.currentPlaylistTab.collectAsState()
    val likedFiles by viewModel.likedFiles.collectAsState()
    val rejectedFiles by viewModel.rejectedFiles.collectAsState()
    val currentPlaylistFiles by viewModel.currentPlaylistFiles.collectAsState()
    
    // Local state for spectrogram visibility
    var showSpectrogram by remember { mutableStateOf(false) }
    
    

    
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.selectFolder(it) }
    }
    
    
    
    val snackbarHostState = remember { SnackbarHostState() }
    var showLoveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showVisualSettingsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteDialogStep by remember { mutableStateOf(0) }
    var showRescanSourceDialog by remember { mutableStateOf(false) } // Added state for rescan source dialog
    var showSearchInput by remember { mutableStateOf(false) } // New state to control search input visibility
    var showSubfolderDialog by remember { mutableStateOf(false) } // New state for subfolder selection

    val zipInProgress by viewModel.zipInProgress.collectAsState()
    val zipProgress by viewModel.zipProgress.collectAsState()
    
    val currentSearchText by viewModel.searchText.collectAsState() // Moved here
    val searchResults by viewModel.searchResults.collectAsState() // Moved here
    
    // Multi-selection state
    val selectedIndices by viewModel.selectedIndices.collectAsState()
    val isMultiSelectionMode by viewModel.isMultiSelectionMode.collectAsState()
    
    // Shared waveform state - generated once and used by both main and mini players
    val currentFile = currentPlaylistFiles.getOrNull(currentIndex)
    val context = LocalContext.current
    val sharedWaveformState = rememberSharedWaveformState(currentFile, context)
    
    // Visual settings
    val visualSettings by viewModel.visualSettingsManager.settings
    
    val hapticFeedback = HapticFeedback.rememberHapticFeedback(visualSettings) // Updated to pass visualSettings
    
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Liked Songs") },
            text = { Text("This action will ZIP all the files in the \"Liked\" folder at this moment and will prompt to be shared via WhatsApp. This action will take time and storage from your device according to the amount of files available.") },
            confirmButton = {
                Button(onClick = { showShareDialog = false; viewModel.startShareLikedZip(true) }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialogStep > 0) {
        val msg = when (showDeleteDialogStep) {
            1 -> "Are you sure you want to delete ALL files from Liked?"
            2 -> "Really sure? This cannot be undone."
            else -> "Final confirmation: Delete ALL Liked files?"
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialogStep = 0 },
            title = { Text("Delete Liked") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = {
                    if (showDeleteDialogStep < 3) showDeleteDialogStep++
                    else {
                        viewModel.deleteAllLiked(3)
                        showDeleteDialogStep = 0
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialogStep = 0 }) { Text("Cancel") } }
        )
    }



    if (showLoveDialog) {
        AlertDialog(
            onDismissRequest = { showLoveDialog = false },
            title = { Text("MobileDigger") },
            text = { Text("this app was created due to the love for music and mobile storage.") },
            confirmButton = {
                Button(onClick = { showLoveDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showDeleteRejectedPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteRejectedPrompt() },
            title = { Text("Delete Rejected Files?") },
            text = { Text("All files have been sorted! Would you like to delete all files from the 'Rejected' folder to free up space?") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.dismissDeleteRejectedPrompt()
                        viewModel.deleteRejectedFiles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { 
                    Text("Delete Rejected Files", color = Color.White) 
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteRejectedPrompt() }) { 
                    Text("Keep Files") 
                }
            }
        )
    }

    // Spectrogram Popup Dialog
    if (showSpectrogram) {
        val currentFile = currentPlaylistFiles.getOrNull(currentIndex)
        SpectrogramPopupDialog(
            musicFile = currentFile,
            audioManager = viewModel.audioManager,
            onDismiss = { showSpectrogram = false },
            waveformData = sharedWaveformState.waveformData
        )
    }
    
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode")
                        Switch(
                            checked = viewModel.themeManager.isDarkMode.value,
                            onCheckedChange = { viewModel.themeManager.toggleDarkMode() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dynamic Colors")
                        Switch(
                            checked = !viewModel.themeManager.useDynamicColor.value,
                            onCheckedChange = { viewModel.themeManager.toggleDynamicColor() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Theme Selection Dropdown
                    var expanded by remember { mutableStateOf(false) }
                    val selectedTheme by viewModel.themeManager.selectedTheme
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedTheme.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Theme") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            AvailableThemes.forEach { theme ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(
                                                        theme.primary,
                                                        CircleShape
                                                    )
                                            )
                                            Text(theme.name)
                                        }
                                    },
                                    onClick = {
                                        viewModel.themeManager.setSelectedTheme(theme)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            hapticFeedback() // Simplified call
                            showSettingsDialog = false
                            showVisualSettingsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Visual Settings")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Gestures:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("• Swipe card left/right to sort")
                    Text("• Tap and drag waveform to seek")
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
    
    // Visual Settings Dialog
    if (showVisualSettingsDialog) {
        VisualSettingsDialog(
            visualSettingsManager = viewModel.visualSettingsManager,
            onDismiss = { showVisualSettingsDialog = false }
        )
    }

    // Waveform Settings Dialog

    // Dynamic sizing based on screen size
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    // val isCompactScreen = screenWidth < 600.dp || screenHeight < 800.dp // isCompactScreen is used, keeping
    val playlistMaxHeight = screenHeight * 0.65f // Playlist takes 65% of screen

    // Handle error messages with transparent background and white bold text
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    

    Scaffold(
        modifier = Modifier,
        snackbarHost = { 
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = snackbarData.visuals.message,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
            .padding(
                start = paddingValues.calculateStartPadding(LayoutDirection.Ltr) / 2 + 16.dp,
                top = paddingValues.calculateTopPadding() / 2 + 6.dp,
                end = paddingValues.calculateEndPadding(LayoutDirection.Ltr) / 2 + 16.dp,
                bottom = paddingValues.calculateBottomPadding() / 2 + 6.dp
            )
        ) {
        // Compute destination launcher and selection flags early (used by header)
        val destLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { viewModel.selectDestinationFolder(it) }
        }
        val hasSourceFolder = musicFiles.isNotEmpty() // This can be false
        val hasDestinationFolder = viewModel.destinationFolder.collectAsState().value != null // This can be false
        val bothFoldersSelected = hasSourceFolder && hasDestinationFolder

        // Header with app title and folders pill
        Row(
        modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MobileDigger",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = GreenAccent
                )
                Text(
                    text = "groovy's child",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Settings button (always visible)
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Added Search button
            IconButton(
                onClick = { 
                    showSearchInput = !showSearchInput 
                    if (!showSearchInput) { // Clear search when hiding input
                        viewModel.updateSearchText("")
                        viewModel.clearSearchResults()
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Added Rescan Source button
            IconButton(
                onClick = { viewModel.rescanSourceFolder() },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Rescan Source",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            if (hasSourceFolder) { // Show actions when music files are loaded
                var menuExpanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actions", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("✓ Source selected") },
                            onClick = { menuExpanded = false; folderLauncher.launch(null) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("✓ Destination selected") },
                            onClick = { menuExpanded = false; destLauncher.launch(null) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Rescan Source") },
                            onClick = { menuExpanded = false; viewModel.rescanSourceFolder() },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isMultiSelectionMode) "Exit Multi-Select" else "Multi-Select Files") },
                            onClick = { menuExpanded = false; viewModel.toggleMultiSelectionMode() },
                            leadingIcon = { Icon(if (isMultiSelectionMode) Icons.Default.Close else Icons.Default.CheckBox, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Share Liked Songs as ZIP") },
                            onClick = { menuExpanded = false; showShareDialog = true },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share TXT with Liked Songs") },
                            onClick = { menuExpanded = false; viewModel.shareLikedFilesTxt() },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Rejected Files") },
                            onClick = { menuExpanded = false; viewModel.deleteRejectedFiles() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                        HorizontalDivider(); HorizontalDivider(); HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Liked Files", color = Color.Red, fontWeight = FontWeight.Bold) },
                            onClick = { menuExpanded = false; showDeleteDialogStep = 1 },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
            }
        }
        // Pinned progress bar just under header
        if (zipInProgress) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { zipProgress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        
        // Subtle message for missing folder selection
        if (hasSourceFolder && !hasDestinationFolder) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { destLauncher.launch(null) }
                    .padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to select destination folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (!hasSourceFolder) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { folderLauncher.launch(null) }
                    .padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to select source folder with music files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Folder selection buttons - minimize after selection
            if (!hasSourceFolder) { // Show folder selection when no music files are loaded
                // Full size buttons when not both selected - organized in two rows
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // First row: Source and Destination folders
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ElevatedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (hasSourceFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasSourceFolder) "✓ Source" else "Source")
                    }
                    

                    ElevatedButton(
                        onClick = { destLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (hasDestinationFolder) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasDestinationFolder) "✓ Destination" else "Destination")
                    }
                }
                    
                    // Second row: Rescan button
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ElevatedButton(
                            onClick = { viewModel.rescanSourceFolder() },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rescan Source")
                        }
                    }
                }
            } else {
                // When both not selected, show full-size buttons (kept below header)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            
            // Search Input Field with Dropdown (moved here from AlertDialog)
            if (showSearchInput) { // Conditionally display search input
                var searchExpanded by remember { mutableStateOf(false) }
                // val currentSearchText by viewModel.searchText.collectAsState() // Removed
                // val searchResults by viewModel.searchResults.collectAsState() // Removed

                ExposedDropdownMenuBox(
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = !searchExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentSearchText,
                        onValueChange = { newValue ->
                            viewModel.updateSearchText(newValue)
                            if (newValue.isNotBlank()) {
                                viewModel.searchMusic(newValue)
                                searchExpanded = true
                            } else {
                                viewModel.clearSearchResults()
                                searchExpanded = false
                            }
                        },
                        label = { Text("Search Music") },
                        trailingIcon = {
                            if (currentSearchText.isNotBlank()) {
                                IconButton(onClick = {
                                    viewModel.updateSearchText("")
                                    viewModel.clearSearchResults()
                                    searchExpanded = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    if (currentSearchText.isNotBlank() && searchResults.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = searchExpanded,
                            onDismissRequest = { searchExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            searchResults.forEach { musicFile ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(musicFile.name)
                                            Text(
                                                "Source: ${musicFile.sourcePlaylist.toString()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.playFile(musicFile)
                                        // After playing, hide search input and clear results
                                        showSearchInput = false 
                                        searchExpanded = false
                                        viewModel.clearSearchResults()
                                        viewModel.updateSearchText("")
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            
            if (isLoading) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading music files...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
            } else {
                val listState = rememberLazyListState()
                val isScrolled by remember {
                    derivedStateOf {
                        // Show miniplayer when we've scrolled past the main player (which contains the waveform)
                        // The main player is the first item (index 0), so when we're at index 1 or beyond,
                        // or significantly scrolled within the first item, the waveform is no longer visible
                        listState.firstVisibleItemIndex > 0 || 
                        (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 400)
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main scrollable content
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .heightIn(max = playlistMaxHeight)
                    ) {
                        // Current song info
                        item {
                            val currentFile = currentPlaylistFiles.getOrNull(currentIndex)
                            if (currentFile != null) {
                                val file = currentFile
                                // Animation states for swipe feedback
                                var dragOffset by remember { mutableStateOf(0f) }
                                var isAnimating by remember { mutableStateOf(false) }
                                var swipeDirection by remember { mutableStateOf(0) } // -1 left, 0 none, 1 right
                                
                                val animatedOffset by animateFloatAsState(
                                    targetValue = if (isAnimating) dragOffset else 0f,
                                    animationSpec = if (visualSettings.enableAnimations) {
                                        val speedFactor = 1f / visualSettings.animationSpeed
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy * speedFactor,
                                            stiffness = Spring.StiffnessLow * speedFactor
                                        )
                                    } else { 
                                        tween(durationMillis = 0) // Snap immediately if animations are disabled
                                    },
                                    finishedListener = {
                                        isAnimating = false
                                        dragOffset = 0f
                                        swipeDirection = 0
                                    }
                                )
                                
                                val cardColor = when (swipeDirection) {
                                    1 -> LikeGreen.copy(alpha = 0.3f)
                                    -1 -> DislikeRed.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                                
                                val borderColor = when (swipeDirection) {
                                    1 -> LikeGreen
                                    -1 -> DislikeRed
                                    else -> Color.Transparent
                                }
                                
                                // Progress counts: played, liked, rejected (moved above main player container)
                                val played = viewModel.preferences.getListened()
                                val yes = viewModel.preferences.getLiked()
                                val no = viewModel.preferences.getRefused()
                                Text(
                                    text = "Played: $played  |  Liked: $yes  |  Rejected: $no",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = GroovyBlue,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .graphicsLayer {
                                            translationX = animatedOffset
                                            scaleX = 1f + abs(animatedOffset) / 2000f // Slight scale effect
                                            scaleY = 1f + abs(animatedOffset) / 2000f
                                        }
                                        .then(
                                            if (swipeDirection != 0) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = borderColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    isAnimating = false
                                                },
                                                onDragEnd = {
                                                    isAnimating = true
                                                    // Execute action if threshold met
                                                    if (abs(dragOffset) > 150) {
                                                        when {
                                                            dragOffset > 0 -> viewModel.sortCurrentFile(SortAction.LIKE)
                                                            dragOffset < 0 -> viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                        }
                                                    }
                                                }
                                            ) { _, dragAmount ->
                                                val (x, _) = dragAmount
                                                dragOffset += x
                                                dragOffset = dragOffset.coerceIn(-300f, 300f) // Limit drag distance
                                                
                                                // Update visual feedback
                                                swipeDirection = when {
                                                    dragOffset > 50 -> 1 // Right swipe (like)
                                                    dragOffset < -50 -> -1 // Left swipe (dislike)
                                                    else -> 0
                                                }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
) {
                                    Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
                                        
                                        // Song info with text wrapping
                                        AnimatedContent(
                                            targetState = file.name,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(800)) + slideInVertically() with
                                                fadeOut(animationSpec = tween(800)) + slideOutVertically()
                                            },
                                            label = "Song Name Animation"
                                        ) { targetName ->
                                            Text(
                                                text = targetName.ifEmpty { "Unknown Track" },
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Track details: Time, Bitrate, Size
                                        val bitrate = calculateBitrate(file.size, file.duration)
                                        val fileSize = formatFileSize(file.size)
                                        Text(
                                            text = ":: Time ${formatTime(file.duration)} :: Bitrate ${bitrate} kbps :: Size $fileSize ::",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
                                        // Shared Waveform (replaces progress bar)
                                        val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                        SharedWaveformDisplay(
                                            sharedState = sharedWaveformState,
                                            progress = progressPercent,
                                            onSeek = { seekProgress ->
                                                val seekPosition = (seekProgress * duration).toLong()
                                                viewModel.seekTo(seekPosition)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(visualSettings.waveformHeight.dp) // Use setting
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatTime(currentPosition),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            
                                            Text(
                                                text = formatTime(duration),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Compact Playback Controls with Volume Popup
    Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
    ) {
                                            IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(40.dp)) {
                                                Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(24.dp))
                                            }
                                            
                                            FloatingActionButton(onClick = { viewModel.playPause() }, modifier = Modifier.size(50.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    if (isPlaying) "Pause" else "Play",
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            
                                            IconButton(onClick = { 
                                                hapticFeedback()
                                                viewModel.next() 
                                            }, modifier = Modifier.size(40.dp)) {
                                                Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(24.dp))
                                            }
                                            
                                            // Share to WhatsApp Button
                                            IconButton(onClick = { viewModel.shareToWhatsApp() }, modifier = Modifier.size(40.dp)) {
                                                Icon(Icons.Default.Share, contentDescription = "Share to WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                                            }
                                            
                                            // Spectrogram Button with Text
                                            IconButton(
                                                onClick = { 
                                                    // Show spectrogram popup dialog
                                                    showSpectrogram = true
                                                }, 
                                                modifier = Modifier.size(50.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = "Sp",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color(0xFFFFB6C1), // Light Pink
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Text(
                                                        text = "eK",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color(0xFFFFB6C1), // Light Pink
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                            
                                            // Rating Button removed as requested
                                        }
                                    }
                                }
                                
                                // Spectrogram view removed - only available in dropdown menu
                            }
                        }
                        
                        
                        // Genre controls removed
                        
                        // Like/Dislike Buttons (compact)
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    ElevatedButton(
                                        onClick = { 
                                            hapticFeedback()
                                            if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                            } else {
                                                viewModel.sortCurrentFile(SortAction.DISLIKE)
                                            }
                                        },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = NoButton,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Animated content for icon and text
                                        val alpha by animateFloatAsState(targetValue = if (visualSettings.enableAnimations && viewModel.lastSortedAction.value == SortAction.DISLIKE) 0.5f else 1f, animationSpec = tween(durationMillis = 150))
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer(alpha = alpha)) {
                                            Icon(Icons.Default.ThumbDown, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "REJECT ALL" else "NO")
                                        }
                                    }
                                    
                                    ElevatedButton(
                                        onClick = { viewModel.undoLastAction() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Undo")
                                    }
                                    
                                    ElevatedButton(
                                        onClick = { 
                                            hapticFeedback()
                                            if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                viewModel.sortSelectedFiles(SortAction.LIKE)
                                            } else {
                                                viewModel.sortCurrentFile(SortAction.LIKE)
                                            }
                                        },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = YesButton,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Animated content for icon and text
                                        val alpha by animateFloatAsState(targetValue = if (visualSettings.enableAnimations && viewModel.lastSortedAction.value == SortAction.LIKE) 0.5f else 1f, animationSpec = tween(durationMillis = 150))
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer(alpha = alpha)) {
                                            Icon(Icons.Default.Favorite, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "LIKE ALL" else "YES")
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Tabbed Playlist Header
                        item {
                            Column {
                                // Custom Pill-Shaped Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Subfolder Selection Button
                                    IconButton(onClick = { showSubfolderDialog = true }) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = "Select Subfolder")
                                    }
                                    PlaylistTab.entries.forEach { tab ->
                                        val isSelected = currentPlaylistTab == tab
                                        val tabColor = when (tab) {
                                            PlaylistTab.TODO -> Color(0xFF2196F3) // Blue
                                            PlaylistTab.LIKED -> Color(0xFF4CAF50) // Green
                                            PlaylistTab.REJECTED -> Color(0xFFFFCDD2) // Pale red
                                        }
                                        val selectedColor = when (tab) {
                                            PlaylistTab.TODO -> Color(0xFF1976D2) // Darker blue
                                            PlaylistTab.LIKED -> Color(0xFF388E3C) // Darker green
                                            PlaylistTab.REJECTED -> Color(0xFFEF9A9A) // Darker pale red
                                        }
                                        
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.switchPlaylistTab(tab) }
                                                .then(
                                                    if (isSelected) {
                                                        Modifier
                                                            .border(
                                                                width = 2.dp,
                                                                color = Color.White,
                                                                shape = RoundedCornerShape(20.dp)
                                                            )
                                                            .shadow(
                                                                elevation = 8.dp,
                                                                shape = RoundedCornerShape(20.dp),
                                                                ambientColor = Color.Black.copy(alpha = 0.3f),
                                                                spotColor = Color.Black.copy(alpha = 0.3f)
                                                            )
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) selectedColor else tabColor
                                            ),
                                            shape = RoundedCornerShape(20.dp) // Pill shape
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    // Add icons for different playlist types
                                                    when (tab) {
                                                        PlaylistTab.TODO -> {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                                                contentDescription = "To Do",
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                        }
                                                        PlaylistTab.LIKED -> {
                                                            Icon(
                                                                Icons.Default.Favorite,
                                                                contentDescription = "Liked",
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                        }
                                                        PlaylistTab.REJECTED -> {
                                                            Icon(
                                                                Icons.Default.ThumbDown,
                                                                contentDescription = "Rejected",
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                        }
                                                    }
                                                    
                                                    Text(
                                                        text = when (tab) {
                                                            PlaylistTab.TODO -> "To Do (${musicFiles.size})"
                                                            PlaylistTab.LIKED -> "Liked (${likedFiles.size})"
                                                            PlaylistTab.REJECTED -> "Rejected (${rejectedFiles.size})"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) Color.White else Color.Black,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Multi-selection controls
                                if (isMultiSelectionMode) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Multi-Selection Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.selectAll() }
                                            ) {
                                                Text("Select All", style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(
                                                onClick = { viewModel.clearSelection() }
                                            ) {
                                                Text("Clear", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                
                                // Subfolder Selection Dialog
                                if (showSubfolderDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showSubfolderDialog = false },
                                        title = { Text("Select Subfolder") },
                                        text = {
                                            val subfolders by viewModel.subfolders.collectAsState()
                                            if (subfolders.isEmpty()) {
                                                Text("No subfolders found in the current source folder.")
                                            } else {
                                                LazyColumn {
                                                    // Add option to select the root source folder
                                                    item {
                                                        TextButton(onClick = {
                                                            viewModel.preferences.getSourceRootUri()?.let { uriString ->
                                                                viewModel.selectFolder(android.net.Uri.parse(uriString))
                                                            }
                                                            showSubfolderDialog = false
                                                        }) {
                                                            Text("../ (Root Folder)", fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    itemsIndexed(subfolders) { index, subfolderUri ->
                                                        TextButton(onClick = {
                                                            viewModel.loadFilesFromSubfolder(subfolderUri)
                                                            showSubfolderDialog = false
                                                        }) {
                                                            Text(viewModel.getFileName(subfolderUri) ?: subfolderUri.lastPathSegment ?: "Unknown Subfolder")
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                                Button(onClick = { showSubfolderDialog = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Show helpful message when no files, but still allow app usage
                        if (currentPlaylistFiles.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = when (currentPlaylistTab) {
                                                PlaylistTab.TODO -> "No music files in To Do playlist"
                                                PlaylistTab.LIKED -> "No liked files yet"
                                                PlaylistTab.REJECTED -> "No rejected files yet"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = when (currentPlaylistTab) {
                                                PlaylistTab.TODO -> "Select a source folder and rescan to load music files"
                                                PlaylistTab.LIKED -> "Like songs from the To Do playlist to see them here"
                                                PlaylistTab.REJECTED -> "Reject songs from the To Do playlist to see them here"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Conditionally display search results or current playlist files (reverted to original)
                        val displayFiles = currentPlaylistFiles // Search results handled within dropdown

                        if (displayFiles.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (currentSearchText.isNotBlank()) {
                                                "No songs found matching \"$currentSearchText\""
                                            } else {
                                                when (currentPlaylistTab) {
                                                    PlaylistTab.TODO -> "No music files in To Do playlist"
                                                    PlaylistTab.LIKED -> "No liked files yet"
                                                    PlaylistTab.REJECTED -> "No rejected files yet"
                                                }
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (currentSearchText.isNotBlank()) {
                                                "Try a different search term or clear the search."
                                            } else {
                                                when (currentPlaylistTab) {
                                                    PlaylistTab.TODO -> "Select a source folder and rescan to load music files"
                                                    PlaylistTab.LIKED -> "Like songs from the To Do playlist to see them here"
                                                    PlaylistTab.REJECTED -> "Reject songs from the To Do playlist to see them here"
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Playlist Items (dynamic sizing)
                        itemsIndexed(currentPlaylistFiles, key = { _, mf -> mf.uri }) { index, item ->
                            val isCurrent = index == currentIndex
                            val isCompactScreen = screenWidth < 600.dp || screenHeight < 800.dp // Moved inside for correct scope if needed
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (isCompactScreen) 6.dp else 10.dp, 
                                        vertical = if (isCompactScreen) 1.dp else 2.dp
                                    )
                                    .pointerInput(Unit) { 
                                        detectTapGestures(
                                            onTap = { 
                                                if (isMultiSelectionMode) {
                                                    viewModel.toggleSelection(index)
                                                } else {
                                                    viewModel.jumpTo(index)
                                                }
                                            }
                                        ) 
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isCurrent -> when (currentPlaylistTab) {
                                            PlaylistTab.TODO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            PlaylistTab.LIKED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                            PlaylistTab.REJECTED -> Color(0xFFFFCDD2).copy(alpha = 0.1f)
                                        }
                                        else -> when (currentPlaylistTab) {
                                            PlaylistTab.TODO -> MaterialTheme.colorScheme.surface
                                            PlaylistTab.LIKED -> Color(0xFF4CAF50).copy(alpha = 0.05f)
                                            PlaylistTab.REJECTED -> Color(0xFFFFCDD2).copy(alpha = 0.05f)
                                        }
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = if (isCompactScreen) 8.dp else 10.dp, 
                                            vertical = if (isCompactScreen) 6.dp else 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Checkbox for multi-selection
                                    if (isMultiSelectionMode) {
                                        Checkbox(
                                            checked = selectedIndices.contains(index),
                                            onCheckedChange = { viewModel.toggleSelection(index) },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                        Text(
                                            text = "Time: ${formatTime(item.duration)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                            
                                            // Rating display removed
                                        }
                                        
                                        // Genre display removed
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 2.dp else 4.dp)) {
                                        if (isMultiSelectionMode) {
                                            // Multi-selection mode: show selected count and bulk action buttons
                                            if (selectedIndices.isNotEmpty()) {
                                                Text(
                                                    text = "${selectedIndices.size} selected",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.sortSelectedFiles(SortAction.LIKE) },
                                                    modifier = Modifier.size(if (isCompactScreen) 36.dp else 48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        contentDescription = "Like Selected",
                                                        tint = YesButton,
                                                        modifier = Modifier.size(if (isCompactScreen) 18.dp else 24.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.sortSelectedFiles(SortAction.DISLIKE) },
                                                    modifier = Modifier.size(if (isCompactScreen) 36.dp else 48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ThumbDown,
                                                        contentDescription = "Reject Selected",
                                                        tint = NoButton,
                                                        modifier = Modifier.size(if (isCompactScreen) 18.dp else 24.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Normal mode: individual file actions
                                            if (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.REJECTED) {
                                                IconButton(
                                                    onClick = { 
                                                        viewModel.sortAtIndex(index, SortAction.LIKE) 
                                                    },
                                                    modifier = Modifier.size(if (isCompactScreen) 36.dp else 48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Favorite, 
                                                        contentDescription = "Yes", 
                                                        tint = YesButton,
                                                        modifier = Modifier.size(if (isCompactScreen) 18.dp else 24.dp)
                                                    )
                                                }
                                            }
                                            if (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) {
                                                IconButton(
                                                    onClick = { 
                                                        viewModel.sortAtIndex(index, SortAction.DISLIKE) 
                                                    },
                                                    modifier = Modifier.size(if (isCompactScreen) 36.dp else 48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ThumbDown, 
                                                        contentDescription = "No", 
                                                        tint = NoButton,
                                                        modifier = Modifier.size(if (isCompactScreen) 18.dp else 24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Enhanced Sticky minimized player when scrolled - positioned correctly in BoxScope
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolled,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(1f),
                        label = "Miniplayer Animated Visibility"
                    ) {
                        val currentFile = currentPlaylistFiles.getOrNull(currentIndex)
                        currentFile?.let { file ->
                            // Animation states for miniplayer swipe feedback
                            var miniDragOffset by remember { mutableStateOf(0f) }
                            var miniIsAnimating by remember { mutableStateOf(false) }
                            var miniSwipeDirection by remember { mutableStateOf(0) } // -1 left, 0 none, 1 right
                            
                            val miniAnimatedOffset by animateFloatAsState(
                                targetValue = if (miniIsAnimating) miniDragOffset else 0f,
                                animationSpec = if (visualSettings.enableAnimations) {
                                    val speedFactor = 1f / visualSettings.animationSpeed
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy * speedFactor,
                                        stiffness = Spring.StiffnessLow * speedFactor
                                    )
                                } else { 
                                    tween(durationMillis = 0) // Snap immediately if animations are disabled
                                },
                                finishedListener = {
                                    miniIsAnimating = false
                                    miniDragOffset = 0f
                                    miniSwipeDirection = 0
                                }
                            )
                            
                            val miniCardColor = when (miniSwipeDirection) {
                                1 -> LikeGreen.copy(alpha = 0.3f)
                                -1 -> DislikeRed.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            
                            val miniBorderColor = when (miniSwipeDirection) {
                                1 -> LikeGreen
                                -1 -> DislikeRed
                                else -> Color.Transparent
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .graphicsLayer {
                                        translationX = miniAnimatedOffset
                                        scaleX = 1f + abs(miniAnimatedOffset) / 3000f // Slightly more subtle scale for miniplayer
                                        scaleY = 1f + abs(miniAnimatedOffset) / 3000f
                                    }
                                    .then(
                                        if (miniSwipeDirection != 0) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = miniBorderColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                miniIsAnimating = false
                                            },
                                            onDragEnd = {
                                                miniIsAnimating = true
                                                // Execute action if threshold met
                                                if (abs(miniDragOffset) > 100) { // Slightly lower threshold for miniplayer
                                                    when {
                                                        miniDragOffset > 0 -> viewModel.sortCurrentFile(SortAction.LIKE)
                                                        miniDragOffset < 0 -> viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                    }
                                                }
                                            }
                                        ) { _, dragAmount ->
                                            val (x, _) = dragAmount
                                            miniDragOffset += x
                                            miniDragOffset = miniDragOffset.coerceIn(-200f, 200f) // Smaller drag distance for miniplayer
                                            
                                            // Update visual feedback
                                            miniSwipeDirection = when {
                                                miniDragOffset > 30 -> 1 // Right swipe (like) - lower threshold
                                                miniDragOffset < -30 -> -1 // Left swipe (dislike) - lower threshold
                                                else -> 0
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = miniCardColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Main content - Song name and controls in 2 columns
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 13.dp, vertical = 9.dp), // Increased by 10%
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left column: Song info
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            AnimatedContent(
                                                targetState = file.name,
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(800)) + slideInVertically() with
                                                    fadeOut(animationSpec = tween(800)) + slideOutVertically()
                                                },
                                                label = "Miniplayer Song Name Animation",
                                                modifier = Modifier.fillMaxWidth()
                                            ) { targetName ->
                                                Text(
                                                    text = targetName.ifEmpty { "Unknown Track" },
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        
                                        // Right column: Control buttons in two rows
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            // First row: PREV PLAY NEXT
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = { 
                                                    hapticFeedback()
                                                    viewModel.previous() 
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(18.dp))
                                                }
                                                
                                                IconButton(onClick = { 
                                                    hapticFeedback()
                                                    viewModel.playPause() 
                                                }, modifier = Modifier.size(40.dp)) {
                                                    Icon(
                                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        if (isPlaying) "Pause" else "Play",
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                
                                                IconButton(onClick = { 
                                                    hapticFeedback()
                                                    viewModel.next() 
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            
                                            // Second row: DISLIKE LIKE SPEK
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Dislike button in mini player
                                                IconButton(
                                                    onClick = { 
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                        }
                                                    }, 
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ThumbDown, 
                                                        contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Reject All Selected" else "Dislike", 
                                                        tint = NoButton,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                
                                                // Like button in mini player
                                                IconButton(
                                                    onClick = { 
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.LIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.LIKE)
                                                        }
                                                    }, 
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Favorite, 
                                                        contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Like All Selected" else "Like", 
                                                        tint = YesButton,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                
                                                // Spectrogram Text Button in mini player
                                                IconButton(
                                                    onClick = { 
                                                        // Toggle spectrogram popup
                                                        showSpectrogram = !showSpectrogram
                                                    }, 
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = "Sp",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = Color(0xFFFFB6C1), // Light Pink
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Text(
                                                            text = "eK",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = Color(0xFFFFB6C1), // Light Pink
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Mini player: shared waveform (replaces progress bar)
                                    val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                    SharedWaveformDisplay(
                                        sharedState = sharedWaveformState,
                                        progress = progressPercent,
                                        onSeek = { seekProgress ->
                                            val seekPosition = (seekProgress * duration).toLong()
                                            viewModel.seekTo(seekPosition)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(visualSettings.miniWaveformHeight.dp) // Use setting
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }


}
