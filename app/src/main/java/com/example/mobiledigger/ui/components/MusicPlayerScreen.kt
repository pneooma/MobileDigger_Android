package com.example.mobiledigger.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.ui.components.SharedWaveformState
import com.example.mobiledigger.ui.components.rememberSharedWaveformState
import com.example.mobiledigger.ui.components.SharedWaveformDisplay
import com.example.mobiledigger.ui.components.WaveformWithToggle
import com.example.mobiledigger.ui.components.VisualSettingsDialog
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
import com.example.mobiledigger.ui.components.ScrollableWaveformView
import com.example.mobiledigger.utils.HapticFeedback
import com.example.mobiledigger.utils.WaveformGenerator
import com.example.mobiledigger.util.rememberOptimizedAnimationSpecs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay // For deprecated icon
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.animation.togetherWith


import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType

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

private fun toCamelotKey(key: String): String {
    val parts = key.trim().split(" ")
    if (parts.size != 2) return key
    val root = parts[0].replace("♯", "#").replace("♭", "b").uppercase(Locale.ROOT)
    val mode = parts[1].lowercase(Locale.ROOT)
    val camelotMapMajor = mapOf(
        "C" to "8B", "G" to "9B", "D" to "10B", "A" to "11B", "E" to "12B",
        "B" to "1B", "F#" to "2B", "C#" to "3B", "G#" to "4B", "D#" to "5B",
        "A#" to "6B", "F" to "7B"
    )
    val camelotMapMinor = mapOf(
        "A" to "8A", "E" to "9A", "B" to "10A", "F#" to "11A", "C#" to "12A",
        "G#" to "1A", "D#" to "2A", "A#" to "3A", "F" to "4A", "C" to "5A",
        "G" to "6A", "D" to "7A"
    )
    val out = if (mode.contains("major")) camelotMapMajor[root] else camelotMapMinor[root]
    return out ?: key
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showDeleteRejectedPrompt by viewModel.showDeleteRejectedPrompt.collectAsState()
    val currentPlaylistTab by viewModel.currentPlaylistTab.collectAsState()
    val likedFiles by viewModel.likedFiles.collectAsState()
    val rejectedFiles by viewModel.rejectedFiles.collectAsState()
    val currentPlaylistFiles by viewModel.currentPlaylistFiles.collectAsState()
    val currentPlayingFile by viewModel.currentPlayingFile.collectAsState()
    val isTransitioning by viewModel.isTransitioning.collectAsState()
    val lastSortedAction by viewModel.lastSortedAction.collectAsState()
    
    // Local state for spectrogram visibility
    var showSpectrogram by remember { mutableStateOf(false) }
    
    // Local state for analyze prompt dialog
    var showAnalyzePrompt by remember { mutableStateOf(false) }
    
    // State to trigger spectrogram after delay
    var triggerSpectrogramAfterDelay by remember { mutableStateOf(false) }
    
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.selectFolder(it) }
    }
    
    val destinationFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.selectDestinationFolder(it) }
    }
    
    
    
    var showLoveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showThemeSelectionDialog by remember { mutableStateOf(false) }
    var showVisualSettingsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteDialogStep by remember { mutableStateOf(0) }
    var showRescanSourceDialog by remember { mutableStateOf(false) } // Added state for rescan source dialog
    var showSearchInput by remember { mutableStateOf(false) } // New state to control search input visibility
    val searchFocusRequester = remember { FocusRequester() }
    
    // Info message dropdown state
    var showInfoMessage by remember { mutableStateOf(false) }
    var infoMessageText by remember { mutableStateOf("") }
    var infoMessageType by remember { mutableStateOf("info") } // "info", "success", "error"
    var showSubfolderDialog by remember { mutableStateOf(false) } // New state for subfolder selection
    
    // Helper function to show info messages with delay after waveform appearance
    fun showInfoMessage(message: String, type: String = "info") {
        scope.launch {
            // Wait for waveform to appear first (delay after file operations)
            delay(1500) // 1.5 second delay after waveform appearance
            
            infoMessageText = message
            infoMessageType = type
            showInfoMessage = true
            
            // Auto-hide after 3 seconds
            delay(3000)
            showInfoMessage = false
        }
    }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var deleteActionType by remember { mutableStateOf<PlaylistTab?>(null) } // State for delete all confirmation
    var showShareZipDialog by remember { mutableStateOf(false) } // State for ZIP sharing confirmation
    var showReadmeDialog by remember { mutableStateOf(false) } // State for README dialog
    var showSubfolderDropdown by remember { mutableStateOf(false) } // State for subfolder dropdown
    var showSubfolderManagementDialog by remember { mutableStateOf(false) } // State for subfolder management dialog
    var showSubfolderSelectionDialog by remember { mutableStateOf(false) } // State for subfolder selection dialog from playlist
    var showLikedSubfoldersViewDialog by remember { mutableStateOf(false) } // New: multi-select liked subfolders to view
    var selectedLikedSubfolders by remember { mutableStateOf(setOf<String>()) }

    val zipInProgress by viewModel.zipInProgress.collectAsState()
    val zipProgress by viewModel.zipProgress.collectAsState()
    
    val currentSearchText by viewModel.searchText.collectAsState() // Moved here
    val searchResults by viewModel.searchResults.collectAsState() // Moved here
    
    // Subfolder management state - Lazy loaded to avoid unnecessary recomposition
    val subfolderHistory by remember { viewModel.subfolderHistory }.collectAsState()
    val availableSubfolders by remember { viewModel.availableSubfolders }.collectAsState()
    val subfolderFileCounts by remember { viewModel.subfolderFileCounts }.collectAsState()
    val showSubfolderCreateDialog by remember { viewModel.showSubfolderDialog }.collectAsState()
    val newSubfolderName by remember { viewModel.newSubfolderName }.collectAsState()
    val showSubfolderManagementDialogState by remember { viewModel.showSubfolderManagementDialog }.collectAsState()
    
    // Multi-selection state
    val selectedIndices by viewModel.selectedIndices.collectAsState()
    val isMultiSelectionMode by viewModel.isMultiSelectionMode.collectAsState()
    
    // Shared waveform state - generated once and used by both main and mini players
    val currentFile = currentPlayingFile // Use the actually playing file instead of playlist-based file
    val sharedWaveformState = rememberSharedWaveformState(currentFile, context)
    
    // Visual settings
    val visualSettings by viewModel.visualSettingsManager.settings
    val animationSpecs = rememberOptimizedAnimationSpecs()
    
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
        val playlistName = when (deleteActionType) {
            PlaylistTab.LIKED -> "Liked"
            PlaylistTab.TODO -> "To Do"
            else -> "files"
        }
        val msg = when (showDeleteDialogStep) {
            1 -> "Are you sure you want to delete ALL files from $playlistName?"
            2 -> "Really sure? This action will permanently remove all files."
            3 -> "This cannot be undone. Are you absolutely certain?"
            else -> "FINAL WARNING: Delete ALL $playlistName files? This is your last chance!"
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialogStep = 0; deleteActionType = null },
            title = { Text("⚠️ Delete $playlistName Files") },
            text = { 
                Column {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (showDeleteDialogStep >= 3) MaterialTheme.colorScheme.error else Color.Unspecified,
                        fontWeight = if (showDeleteDialogStep >= 3) FontWeight.Bold else FontWeight.Normal
                    )
                    if (showDeleteDialogStep >= 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This action is IRREVERSIBLE!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (showDeleteDialogStep < 4) showDeleteDialogStep++
                    else {
                            when (deleteActionType) {
                                PlaylistTab.LIKED -> viewModel.deleteAllLiked(4)
                                PlaylistTab.TODO -> viewModel.deleteAllTodo(4)
                                else -> {}
                            }
                        showDeleteDialogStep = 0
                            deleteActionType = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showDeleteDialogStep >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) { 
                    Text(
                        text = if (showDeleteDialogStep >= 4) "DELETE FOREVER" else "Continue",
                        color = if (showDeleteDialogStep >= 3) MaterialTheme.colorScheme.onError else Color.Unspecified,
                        fontWeight = if (showDeleteDialogStep >= 4) FontWeight.Bold else FontWeight.Normal
                    ) 
                }
            },
            dismissButton = { 
                TextButton(onClick = { showDeleteDialogStep = 0; deleteActionType = null }) { 
                    Text("Cancel") 
                } 
            }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("Delete Rejected Files", color = MaterialTheme.colorScheme.onError) 
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteRejectedPrompt() }) { 
                    Text("Keep Files") 
                }
            }
        )
    }

    // Delete All Files Confirmation Dialog (Double Warning)
    if (showDeleteAllDialog) {
        val actionType = deleteActionType ?: currentPlaylistTab
        val playlistName = when (actionType) {
            PlaylistTab.REJECTED -> "Rejected"
            PlaylistTab.TODO -> "To Do"
            PlaylistTab.LIKED -> "Liked"
        }
        val fileCount = when (actionType) {
            PlaylistTab.REJECTED -> rejectedFiles.size
            PlaylistTab.TODO -> musicFiles.size
            PlaylistTab.LIKED -> likedFiles.size
        }
        
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false; deleteActionType = null },
            title = { Text("⚠️ WARNING: Delete ALL $playlistName Files?") },
            text = { 
                Column {
                    Text(
                        "This will PERMANENTLY delete ALL files in the '$playlistName' folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Files: $fileCount items will be deleted",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action CANNOT be undone. Are you absolutely sure?",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showDeleteAllDialog = false
                        when (deleteActionType) {
                            PlaylistTab.REJECTED -> viewModel.deleteRejectedFiles()
                            PlaylistTab.TODO -> {
                                deleteActionType = PlaylistTab.TODO
                                showDeleteDialogStep = 1 // Use 4-step confirmation for TODO files
                            }
                            PlaylistTab.LIKED -> {
                                deleteActionType = PlaylistTab.LIKED
                                showDeleteDialogStep = 1 // Use 4-step confirmation for LIKED files
                            }
                            null -> {
                                // Fallback to current playlist tab if action type is not set
                                when (currentPlaylistTab) {
                                    PlaylistTab.REJECTED -> viewModel.deleteRejectedFiles()
                                    PlaylistTab.TODO -> {
                                        deleteActionType = PlaylistTab.TODO
                                        showDeleteDialogStep = 1
                                    }
                                    PlaylistTab.LIKED -> {
                                        deleteActionType = PlaylistTab.LIKED
                                        showDeleteDialogStep = 1
                                    }
                                }
                            }
                        }
                        deleteActionType = null // Reset the action type
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("DELETE ALL", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { 
                    Text("Cancel", color = MaterialTheme.colorScheme.primary) 
                }
            }
        )
    }

    // Share Liked Files as ZIP Confirmation Dialog
    if (showShareZipDialog) {
        AlertDialog(
            onDismissRequest = { showShareZipDialog = false },
            title = { Text("📦 Share Liked Files as ZIP") },
            text = { 
                Column {
                    Text(
                        "This will create a ZIP archive containing all your liked songs.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Files to include: ${likedFiles.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The ZIP file will be created and you can share it via any app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showShareZipDialog = false
                        viewModel.startShareLikedZip(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) { 
                    Text("CREATE ZIP", color = MaterialTheme.colorScheme.onTertiary) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareZipDialog = false }) { 
                    Text("Cancel") 
                }
            }
        )
    }

    // README Dialog
    if (showReadmeDialog) {
        AlertDialog(
            onDismissRequest = { showReadmeDialog = false },
            title = { Text("MobileDigger README") },
            text = { 
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "GETTING STARTED:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Select Source Folder: Tap the \"Source\" button to choose where your music files are stored")
                        Text("• Select Destination Folder: Tap the \"Destination\" button to choose where sorted files will be moved")
                        Text("• Rescan Source: Use the large green \"Rescan Source\" button to load music files from your last selected source folder.")
                    }
                    
                    item {
                        Text(
                            "WORKFLOW:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Listen to songs and swipe left (dislike) or right (like) to sort them")
                        Text("• Liked songs go to the \"Liked\" folder")
                        Text("• Disliked songs go to the \"Rejected\" folder")
                        Text("• Use the playlist tabs to navigate between To Do, Liked, and Rejected files")
                    }
                    
                    item {
                        Text(
                            "FEATURES:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Swipe gestures for quick sorting")
                        Text("• Search functionality across all playlists")
                        Text("• Spectrogram analysis for audio files")
                        Text("• Share liked songs as ZIP archive")
                        Text("• Multi-selection for batch operations")
                        Text("• Waveform visualization")
                        Text("• Haptic feedback")
                    }
                    
                    item {
                        Text(
                            "GESTURES:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Swipe card left/right to sort")
                        Text("• Tap and drag waveform to seek")
                        Text("• Long press for multi-selection mode")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReadmeDialog = false }) {
                    Text("Got it!")
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
    
    // Handle delayed spectrogram trigger
    LaunchedEffect(triggerSpectrogramAfterDelay) {
        if (triggerSpectrogramAfterDelay) {
            delay(1000) // Wait 1 second for file to be properly loaded
            if (currentPlayingFile != null) {
                showSpectrogram = true
            }
            triggerSpectrogramAfterDelay = false
        }
    }
    
    // Analyze prompt dialog when file is opened externally
    LaunchedEffect(Unit) {
        while (true) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val shouldShowPrompt = prefs.getBoolean("show_analyze_prompt", false)
            if (shouldShowPrompt && currentPlayingFile != null) {
                // Clear the flag
                prefs.edit().apply {
                    putBoolean("show_analyze_prompt", false)
                    apply()
                }
                // Show the prompt dialog
                showAnalyzePrompt = true
            }
            delay(500) // Check every 500ms
        }
    }
    
    // Analyze prompt dialog
    if (showAnalyzePrompt) {
        AlertDialog(
            onDismissRequest = { showAnalyzePrompt = false },
            title = { Text("File Opened") },
            text = { Text("What would you like to do with this file?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAnalyzePrompt = false
                        // Trigger spectrogram after delay to ensure file is properly loaded
                        triggerSpectrogramAfterDelay = true
                    }
                ) {
                    Text("Generate Spectrogram")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAnalyzePrompt = false }
                ) {
                    Text("Just Listen")
                }
            }
        )
    }
    
    
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
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
                    Text("Enable Theme", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = !viewModel.themeManager.useDynamicColor.value,
                            onCheckedChange = { viewModel.themeManager.toggleDynamicColor() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Theme Selection - Using Button to open theme selection dialog
                    val selectedTheme by viewModel.themeManager.selectedTheme
                    
                    Button(
                        onClick = { showThemeSelectionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(
                                        selectedTheme.primary,
                                                        CircleShape
                                                    )
                                            )
                            Text("Theme: ${selectedTheme.name}")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Theme")
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
    
    // Theme Selection Dialog
    if (showThemeSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showThemeSelectionDialog = false },
            title = { Text("Select Theme") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(AvailableThemes.size) { index ->
                        val theme = AvailableThemes[index]
                        Card(
                            onClick = {
                                viewModel.themeManager.setSelectedTheme(theme)
                                showThemeSelectionDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (theme == viewModel.themeManager.selectedTheme.value) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            theme.primary,
                                            CircleShape
                                        )
                                )
                                Column {
                                    Text(
                                        text = theme.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (theme == viewModel.themeManager.selectedTheme.value) 
                                            FontWeight.Bold 
                                        else 
                                            FontWeight.Normal
                                    )
                                    Text(
                                        text = "Primary: #${theme.primary.toArgb().toUInt().toString(16).uppercase().takeLast(6)}, Secondary: #${theme.secondary.toArgb().toUInt().toString(16).uppercase().takeLast(6)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (theme == viewModel.themeManager.selectedTheme.value) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeSelectionDialog = false }) {
                    Text("Close")
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

    // Handle error messages with better display under header
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            showInfoMessage(message, "info")
            viewModel.clearError()
        }
    }
    

    Scaffold(
        modifier = Modifier
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
                start = 6.dp,
                top = paddingValues.calculateTopPadding() / 2 + 6.dp,
                end = 6.dp,
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
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.5f
                    ),
                    color = GreenAccent
                )
                Text(
                    text = "groovy's child",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * 0.5f,
                        lineHeight = MaterialTheme.typography.headlineSmall.fontSize * 0.5f // Compact line height
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
        Text(
            text = ":: v9.96 ::",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * 0.4f,
                lineHeight = MaterialTheme.typography.headlineSmall.fontSize * 0.4f // Compact line height
            ),
            fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Calculate responsive spacing based on screen width - reduced to prevent text wrapping
            val buttonSpacing = when {
                screenWidth < 400.dp -> 2.dp
                screenWidth < 600.dp -> 3.dp
                screenWidth < 800.dp -> 4.dp
                else -> 6.dp
            }
            
            // Home button (always visible) - resets to initial state
            IconButton(
                onClick = { 
                    // Reset to initial state by clearing source folder selection
                    viewModel.resetToInitialState()
                },
                modifier = Modifier.padding(end = buttonSpacing)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Settings button (always visible)
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.padding(end = buttonSpacing)
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
                modifier = Modifier.padding(end = buttonSpacing)
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
                modifier = Modifier.padding(end = buttonSpacing)
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Rescan Source",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Added README/Info button
            IconButton(
                onClick = { showReadmeDialog = true },
                modifier = Modifier.padding(end = buttonSpacing)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "README",
                    tint = Color(0xFF4CAF50) // Green color to match README theme
                )
            }
            
            if (hasSourceFolder) { // Show actions when music files are loaded
                var menuExpanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
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
                            onClick = { 
                                menuExpanded = false
                                deleteActionType = PlaylistTab.REJECTED
                                showDeleteAllDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                        HorizontalDivider(); HorizontalDivider(); HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Liked Files", color = Color.Red, fontWeight = FontWeight.Bold) },
                            onClick = { 
                                menuExpanded = false
                                deleteActionType = PlaylistTab.LIKED
                                showDeleteDialogStep = 1
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("README", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold) },
                            onClick = { menuExpanded = false; showReadmeDialog = true },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF4CAF50)) }
                        )
                        
                    }
            }
        }
        
        // Animated Info Message Display (positioned absolutely on top, fade in/out only)
        if (showInfoMessage) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(LocalDensity.current) { 86.dp.toPx().toInt() }) // ~under header (moved up by 10dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .shadow(8.dp, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = when (infoMessageType) {
                            "success" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f)
                            "error" -> MaterialTheme.colorScheme.error.copy(alpha = 0.95f)
                            "loading" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp), // reduced inner padding
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center // center contents
                    ) {
                        Icon(
                            when (infoMessageType) {
                                "success" -> Icons.Default.CheckCircle
                                "error" -> Icons.Default.Error
                                "loading" -> Icons.Default.HourglassEmpty
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (infoMessageType) {
                                "success" -> MaterialTheme.colorScheme.onTertiary
                                "error" -> MaterialTheme.colorScheme.onError
                                "loading" -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = infoMessageText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = when (infoMessageType) {
                                "success" -> MaterialTheme.colorScheme.onTertiary
                                "error" -> MaterialTheme.colorScheme.onError
                                "loading" -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
        
        // ZIP progress indicator at top of screen
        if (zipInProgress) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Creating ZIP archive...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Listen to a nice song because it will take a while. 🎵",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { zipProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$zipProgress%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Button(
                            onClick = { viewModel.cancelZipCreation() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
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
                        Icons.AutoMirrored.Filled.ArrowForward,
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
                        Icons.AutoMirrored.Filled.ArrowForward,
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
                            modifier = Modifier
                                .height(80.dp) // Double the height
                                .padding(horizontal = 16.dp), // Add horizontal padding for width
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF2E7D32), // Darker green
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(16.dp))
                            val lastSource = viewModel.preferences.getSourceRootUri()
                            val lastLabel = lastSource?.let { uri ->
                                kotlin.runCatching { android.net.Uri.parse(uri).path ?: uri }.getOrNull()?.let { path ->
                                    val parts = path.trim('/').split('/')
                                    val tail = parts.takeLast(2)
                                    if (tail.isNotEmpty()) tail.joinToString("/") else path
                                }
                            } ?: "(none)"
                            Text("Rescan: $lastLabel", fontSize = 18.sp)
                        }
                    }
                    // Recent Sources (max 10) buttons, two per row
                    val recentSources by viewModel.recentSourceUris.collectAsState()
                    if (recentSources.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val rows = recentSources.take(10).chunked(2)
                        rows.forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { uriString ->
                                    val label = kotlin.runCatching { android.net.Uri.parse(uriString).path ?: uriString }.getOrNull()?.let { p ->
                                        val parts = p.trim('/').split('/')
                                        val tail = parts.takeLast(2)
                                        if (tail.isNotEmpty()) tail.joinToString("/") else p
                                    } ?: uriString
                                    OutlinedButton(onClick = { viewModel.setSourceFolderPreference(android.net.Uri.parse(uriString)) }, modifier = Modifier.weight(1f)) {
                                        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
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
                
                // Auto-focus and show keyboard when search input becomes visible
                LaunchedEffect(showSearchInput) {
                    if (showSearchInput) {
                        delay(100) // Small delay to ensure UI is ready
                        searchFocusRequester.requestFocus()
                    }
                }

                // Auto-expand search dropdown when results arrive (no click needed)
                LaunchedEffect(searchResults) {
                    searchExpanded = currentSearchText.isNotBlank() && searchResults.isNotEmpty()
                }

                ExposedDropdownMenuBox(
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = !searchExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentSearchText,
                        onValueChange = { newValue ->
                            viewModel.updateSearchText(newValue)
                            if (newValue.isBlank()) {
                                    viewModel.clearSearchResults()
                                    searchExpanded = false
                            }
                        },
                        label = { Text("Search Music") },
                        trailingIcon = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (currentSearchText.isNotBlank()) {
                                    IconButton(onClick = {
                                        viewModel.searchMusicImmediate(currentSearchText)
                                        searchExpanded = true
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
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
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .focusRequester(searchFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    val code = event.nativeKeyEvent.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        if (currentSearchText.isNotBlank()) {
                            viewModel.searchMusicImmediate(currentSearchText)
                            searchExpanded = true
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                return@onPreviewKeyEvent false
            }
                    )
                    if (searchExpanded && searchResults.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = searchExpanded,
                            onDismissRequest = { searchExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            searchResults.forEach { musicFile ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = musicFile.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "Source: ${musicFile.sourcePlaylist.toString()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.playFile(musicFile)
                                        searchExpanded = false
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                // Optimized scroll detection - minimize recomposition frequency
                val isScrolled by remember {
                    derivedStateOf {
                        // Use less granular detection to reduce recompositions
                        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main scrollable content
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .heightIn(max = playlistMaxHeight),
                        // Performance optimizations
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        // Add performance hints for large lists
                        userScrollEnabled = true,
                        reverseLayout = false
                    ) {
                        // Current song info
                        item {
                            val currentFile = currentPlayingFile // Use the actually playing file
            if (currentFile != null) {
                                val file = currentFile
                                // Animation states for swipe feedback - Performance optimized for 120Hz
                                var dragOffset by remember { mutableStateOf(0f) }
                                var isAnimating by remember { mutableStateOf(false) }
                                var swipeDirection by remember { mutableStateOf(0) } // -1 left, 0 none, 1 right
                                
                                // Song transition fade animation for 120Hz displays
                                val transitionAlpha by animateFloatAsState(
                                    targetValue = if (isTransitioning) 0.3f else 1f,
                                    animationSpec = tween(
                                        durationMillis = 150, // Fast for 120Hz
                                        easing = FastOutSlowInEasing
                                    ),
                                    label = "transition_fade"
                                )
                                
                                // Cache animation spec optimized for 120Hz displays
                                val animationSpec = remember(visualSettings.enableAnimations, visualSettings.animationSpeed) {
                                    if (visualSettings.enableAnimations) {
                                        val speedFactor = 1f / visualSettings.animationSpeed
                                        // Optimized spring for 120Hz with higher stiffness
                                        spring<Float>(
                                            dampingRatio = Spring.DampingRatioMediumBouncy * speedFactor,
                                            stiffness = Spring.StiffnessMediumLow * speedFactor * 1.5f // Faster for 120Hz
                                        )
                                    } else { 
                                        tween<Float>(durationMillis = 0) // Snap immediately if animations are disabled
                                    }
                                }
                                
                                val animatedOffset by animateFloatAsState(
                                    targetValue = if (isAnimating) dragOffset else 0f,
                                    animationSpec = animationSpec,
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = GroovyBlue,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                                
                                // Clear previous analysis on track change; do not auto-analyze
                                LaunchedEffect(file.uri) {
                                    viewModel.audioManager.clearAnalysis()
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .graphicsLayer {
                                            translationX = animatedOffset
                                            scaleX = 1f + abs(animatedOffset) / 2000f // Slight scale effect
                                            scaleY = 1f + abs(animatedOffset) / 2000f
                                            alpha = transitionAlpha // Smooth fade during song transitions
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
                                                        try {
                                                            when {
                                                                dragOffset > 0 -> viewModel.sortCurrentFile(SortAction.LIKE)
                                                                dragOffset < 0 -> viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                            }
                                                        } catch (e: Exception) {
                                                            // Handle crash gracefully
                                                            println("Error in swipe gesture: ${e.message}")
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
                                                fadeIn(animationSpec = animationSpecs.songNameTween) + slideInVertically() togetherWith
                                                fadeOut(animationSpec = animationSpecs.songNameTween) + slideOutVertically()
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
                                        // Metadata row under title:| Time | Bitrate | Size | BPM | Key |
                                        val analysis by viewModel.audioManager.analysisResult.collectAsState()
                                        val durationText = formatTime(duration)
                                        val bitrateText = "${calculateBitrate(file.size, duration)} kbps"
                                        val sizeText = formatFileSize(file.size)
                                        val bpmText = analysis?.bpm?.let { "${(it + 0.5f).toInt()}" } ?: "—"
                                        val keyText = analysis?.key?.let { toCamelotKey(it) } ?: "—"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "| Time $durationText | $bitrateText | $sizeText | BPM: $bpmText | Key $keyText |",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val isAnalyzing by viewModel.audioManager.isAnalyzing.collectAsState()
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        currentPlayingFile?.let { viewModel.audioManager.analyzeFile(it, force = true) }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(22.dp),
                                                enabled = !isAnalyzing
                                            ) {
                                                if (isAnalyzing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Analyze",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
                                        // Shared Waveform with toggle (replaces progress bar)
                                        val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                        WaveformWithToggle(
                                            sharedState = sharedWaveformState,
                                            progress = progressPercent,
                                            onSeek = { seekProgress ->
                                                val seekPosition = (seekProgress * duration).toLong()
                                                viewModel.seekTo(seekPosition)
                                            },
                                            songUri = currentFile?.uri.toString(),
                                            waveformHeight = visualSettings.waveformHeight.toInt(),
                                            currentPosition = currentPosition,
                                            totalDuration = duration,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Hide current position
                                            
                                            // Hide total duration
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Compact Playback Controls with Star Rating
    Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
    ) {
                                            // Left side: Music controls with modern Android 16 style
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Previous button with modern style
                Card(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(38.dp), // Reduced from 48dp to 38dp (20% smaller)
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            modifier = Modifier.size(19.dp), // Reduced from 24dp to 19dp (20% smaller)
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                                                
                                                // Play/Pause button with modern FAB style
                                                FloatingActionButton(
                                                    onClick = { viewModel.playPause() }, 
                                                    modifier = Modifier.size(45.dp), // Reduced from 56dp to 45dp (20% smaller)
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    elevation = FloatingActionButtonDefaults.elevation(
                                                        defaultElevation = 6.dp,
                                                        pressedElevation = 8.dp
                                                    )
                                                ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    if (isPlaying) "Pause" else "Play",
                                                        modifier = Modifier.size(22.dp) // Reduced from 28dp to 22dp (20% smaller)
                                                )
                                            }
                                            
                                                // Next button with modern style
                                                Card(
                                                    onClick = { 
                                                    hapticFeedback()
                                                viewModel.next() 
                                                    },
                                                    modifier = Modifier.size(38.dp), // Reduced from 48dp to 38dp (20% smaller)
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.SkipNext,
                                                            "Next",
                                                            modifier = Modifier.size(19.dp), // Reduced from 24dp to 19dp (20% smaller)
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Center: Spacer for alignment
                                            Spacer(modifier = Modifier.weight(1f))
                                            
                                            // Right side: Share and SpEK
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                            // Share to WhatsApp Button
                                                IconButton(onClick = { viewModel.shareToWhatsApp() }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Share, contentDescription = "Share to WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                                            }
                                            
                                                // Spectrogram Button with Text
                                            IconButton(
                                                onClick = { 
                                                    showSpectrogram = true
                                                }, 
                                                    modifier = Modifier.size(44.dp)
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
                                            
                                            // Subfolder dropdown button (available in all playlists)
                                            Box {
                                                IconButton(
                                                    onClick = { showSubfolderDropdown = true },
                                                    modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = "Move to Subfolder",
                                                        tint = Color(0xFF4CAF50), // Green color
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                
                                                // Subfolder dropdown menu
                                                DropdownMenu(
                                                    expanded = showSubfolderDropdown,
                                                    onDismissRequest = { showSubfolderDropdown = false }
                                                ) {
                                                    // Context-aware subfolder options based on current playlist
                                                    when (currentPlaylistTab) {
                                                        PlaylistTab.TODO -> {
                                                            // For TODO playlist: show options to move to liked subfolders (this will also like the file)
                                                            DropdownMenuItem(
                                                                text = { 
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Add,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF4CAF50)
                                                                        )
                                                                        Text("Add new subfolder")
                                                                    }
                                                                },
                                                                onClick = { 
                                                                    showSubfolderDropdown = false
                                                                    viewModel.showSubfolderDialog()
                                                                }
                                                            )
                                                            
                                                            // Available subfolders with file counts
                                                            availableSubfolders.forEach { subfolder ->
                                                                val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text("$subfolder ($fileCount files)")
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                    }
                                                                )
                                                            }
                                                            
                                                            
                                                            // Recent subfolders (from history)
                                                            subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text("$subfolder (0 files)")
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        
                                                        PlaylistTab.REJECTED -> {
                                                            // For REJECTED playlist: show options to move to liked subfolders (this will also like the file)
                                                            DropdownMenuItem(
                                                                text = { 
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Add,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF4CAF50)
                                                                        )
                                                                        Text("Add new subfolder")
                                                                    }
                                                                },
                                                                onClick = { 
                                                                    showSubfolderDropdown = false
                                                                    viewModel.showSubfolderDialog()
                                                                }
                                                            )
                                                            
                                                            // Available subfolders with file counts
                                                            availableSubfolders.forEach { subfolder ->
                                                                val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text("$subfolder ($fileCount files)")
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                    }
                                                                )
                                                            }
                                                            
                                                            // Recent subfolders (from history)
                                                            subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text("$subfolder (0 files)")
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        
                                                        PlaylistTab.LIKED -> {
                                                            // For LIKED playlist: show full subfolder management options
                                                            DropdownMenuItem(
                                                                text = { 
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Add,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF4CAF50)
                                                                        )
                                                                        Text("Add new subfolder")
                                                                    }
                                                                },
                                                                onClick = { 
                                                                    showSubfolderDropdown = false
                                                                    viewModel.showSubfolderDialog()
                                                                }
                                                            )
                                                            
                                                            // Current file's subfolder info (only for liked files)
                                                            val currentSubfolder = viewModel.getCurrentFileSubfolder()
                                                            if (currentSubfolder != null) {
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text(
                                                                            "Remove from \"$currentSubfolder\" to Root",
                                                                            color = Color(0xFFFF5722) // Orange color
                                                                        )
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileFromSubfolderToRoot()
                                                                    }
                                                                )
                                                            }
                                                            
                                                            // Available subfolders with file counts
                                                            availableSubfolders.forEach { subfolder ->
                                                                val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                val isCurrentSubfolder = currentSubfolder == subfolder
                                                                
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text(
                                                                            if (isCurrentSubfolder) "Currently in \"$subfolder\" ($fileCount files)"
                                                                            else "Move to \"$subfolder\" ($fileCount files)"
                                                                        )
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        if (!isCurrentSubfolder) {
                                                                            viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                        }
                                                                    },
                                                                    enabled = !isCurrentSubfolder
                                                                )
                                                            }
                                                            
                                                            
                                                            // Recent subfolders (from history)
                                                            subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text("Move to \"$subfolder\" (0 files)")
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Manage subfolders option
                                                    DropdownMenuItem(
                                                        text = { 
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Delete,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFFF5722) // Orange color
                                                                )
                                                                Text("Manage subfolders")
                                                            }
                                                        },
                                                        onClick = { 
                                                            showSubfolderDropdown = false
                                                            viewModel.showSubfolderManagementDialog()
                                                        }
                                                    )
                                                }
                                            }
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
                                            try {
                                                hapticFeedback()
                                                if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                    viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                } else {
                                                    viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                }
                                            } catch (e: Exception) {
                                                // Handle crash gracefully
                                                println("Error in main dislike button: ${e.message}")
                                            }
                                        },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = NoButton,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Animated content for icon and text
                                        val alpha by animateFloatAsState(targetValue = if (visualSettings.enableAnimations && lastSortedAction == SortAction.DISLIKE) 0.5f else 1f, animationSpec = animationSpecs.buttonPressTween)
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
                                            try {
                                                hapticFeedback()
                                                CrashLogger.log("MusicPlayerScreen", "🔍 Main LIKE button clicked")
                                                CrashLogger.log("MusicPlayerScreen", "🔍 Multi-selection mode: $isMultiSelectionMode")
                                                CrashLogger.log("MusicPlayerScreen", "🔍 Selected indices: $selectedIndices")
                                                
                                                if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                    CrashLogger.log("MusicPlayerScreen", "📁 Calling sortSelectedFiles(LIKE) for ${selectedIndices.size} files")
                                                    viewModel.sortSelectedFiles(SortAction.LIKE)
                                                } else {
                                                    CrashLogger.log("MusicPlayerScreen", "📁 Calling sortCurrentFile(LIKE)")
                                                    viewModel.sortCurrentFile(SortAction.LIKE)
                                                }
                                            } catch (e: Exception) {
                                                // Handle crash gracefully
                                                CrashLogger.log("MusicPlayerScreen", "💥 Error in main like button", e)
                                                println("Error in main like button: ${e.message}")
                                            }
                                        },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = YesButton,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Animated content for icon and text
                                        val alpha by animateFloatAsState(targetValue = if (visualSettings.enableAnimations && lastSortedAction == SortAction.LIKE) 0.5f else 1f, animationSpec = animationSpecs.buttonPressTween)
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
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    // Icon column - centered
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.weight(0.3f)
                                                    ) {
                                                    when (tab) {
                                                        PlaylistTab.TODO -> {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                                                contentDescription = "To Do",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                        }
                                                        PlaylistTab.LIKED -> {
                                                            Icon(
                                                                Icons.Default.Favorite,
                                                                contentDescription = "Liked",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                        }
                                                        PlaylistTab.REJECTED -> {
                                                            Icon(
                                                                Icons.Default.ThumbDown,
                                                                contentDescription = "Rejected",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = if (isSelected) Color.White else Color.Black
                                                            )
                                                            }
                                                        }
                                                    }
                                                    
                                                    // No spacing between columns
                                                    Spacer(modifier = Modifier.width(0.dp))
                                                    
                                                    // Text column - centered
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.weight(0.7f)
                                                    ) {
                                                    Text(
                                                        text = when (tab) {
                                                                PlaylistTab.TODO -> "To Do"
                                                                PlaylistTab.LIKED -> "Liked"
                                                                PlaylistTab.REJECTED -> "Rejected"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) Color.White else Color.Black,
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = when (tab) {
                                                                PlaylistTab.TODO -> "(${musicFiles.size} files)"
                                                                PlaylistTab.LIKED -> "(${likedFiles.size} files)"
                                                                PlaylistTab.REJECTED -> "(${rejectedFiles.size} files)"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f),
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Always show action button - changes based on playlist
                                    if (currentPlaylistTab == PlaylistTab.LIKED) {
                                        // Share as ZIP button for liked files
                                        IconButton(
                                            onClick = { 
                                                // Show confirmation dialog for ZIP sharing
                                                showShareZipDialog = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Archive,
                                                contentDescription = "Share Liked as ZIP",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        // Delete all button for TODO and REJECTED playlists
                                        IconButton(
                                            onClick = { 
                                                // Set the action type based on current playlist tab
                                                deleteActionType = currentPlaylistTab
                                                // Show double warning confirmation
                                                showDeleteAllDialog = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = when (currentPlaylistTab) {
                                                    PlaylistTab.REJECTED -> "Delete All Rejected Files"
                                                    PlaylistTab.TODO -> "Delete All To Do Files"
                                                    else -> "Delete All Files"
                                                },
                                                tint = Color.Red,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                                        title = { 
                                            Text(
                                                when (currentPlaylistTab) {
                                                    PlaylistTab.TODO -> "Select Source Subfolder"
                                                    PlaylistTab.LIKED -> "Select Liked Subfolder"
                                                    PlaylistTab.REJECTED -> "Select Source Subfolder"
                                                }
                                            )
                                        },
                                        text = {
                                            when (currentPlaylistTab) {
                                                PlaylistTab.TODO, PlaylistTab.REJECTED -> {
                                                    // Show source folder subfolders for TODO and REJECTED
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
                                                }
                                                PlaylistTab.LIKED -> {
                                                    // Multi-select view of liked subfolders
                                                    val availableSubfolders by viewModel.availableSubfolders.collectAsState()
                                                    val subfolderFileCounts by viewModel.subfolderFileCounts.collectAsState()
                                                    var selectedViewSubfolders by remember { mutableStateOf(setOf<String>()) }
                                                    
                                                    if (availableSubfolders.isEmpty()) {
                                                        Text("No subfolders found in the liked folder.")
                                                    } else {
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            // View all liked files
                                                                TextButton(onClick = {
                                                                    viewModel.loadLikedFiles()
                                                                    showSubfolderDialog = false
                                                                }) {
                                                                    Text("../ (All Liked Files)", fontWeight = FontWeight.Bold)
                                                                }
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                "Select one or more subfolders to view:",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(Modifier.height(6.dp))
                                                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                                                itemsIndexed(availableSubfolders) { _, name ->
                                                                    val checked = selectedViewSubfolders.contains(name)
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                selectedViewSubfolders = if (checked) selectedViewSubfolders - name else selectedViewSubfolders + name
                                                                            }
                                                                            .padding(vertical = 2.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                            Checkbox(
                                                                                checked = checked,
                                                                                onCheckedChange = {
                                                                                    selectedViewSubfolders = if (checked) selectedViewSubfolders - name else selectedViewSubfolders + name
                                                                                }
                                                                            )
                                                                            Text(name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                                                                        }
                                                                        Text(
                                                                            "${subfolderFileCounts[name] ?: 0}",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            Spacer(Modifier.height(10.dp))
                                                            Button(
                                                                onClick = {
                                                                    val list = selectedViewSubfolders.toList()
                                                                    if (list.isNotEmpty()) {
                                                                        viewModel.loadFilesFromLikedSubfolders(list)
                                                                    }
                                                                    showSubfolderDialog = false
                                                                },
                                                                enabled = selectedViewSubfolders.isNotEmpty(),
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) { Text("View", color = MaterialTheme.colorScheme.onPrimary) }
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
                            
                            // MobileDigger Instructions Card - show when app starts (no files loaded)
                            if (musicFiles.isEmpty() && likedFiles.isEmpty() && rejectedFiles.isEmpty()) {
                                item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .height(400.dp), // Fixed height for scrollable content
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface // Use theme background
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp)
                                    ) {
                                        Text(
                                            text = "Must Know about MobileDigger Application",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface, // Use theme contrast text
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        // Scrollable content
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            item {
                                                Text(
                                                    text = "GETTING STARTED:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Select Source Folder: Tap the \"Source\" button to choose where your music files are stored\n" +
                                                            "• Select Destination Folder: Tap the \"Destination\" button to choose where sorted files will be moved\n" +
                                                            "• Rescan Source: Use the large green \"Rescan Source\" button to load music files from your last selected source folder.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "WORKFLOW:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Swipe left or right on the main player or miniplayer for like/dislike a song\n" +
                                                            "• Controls like play, next, like/dislike are available in the notifications area also. The playback works in the background too\n" +
                                                            "• Play Music: Tap any song in the playlist to start playing\n" +
                                                            "• Like Songs: Tap the ❤️ button to move songs to your \"Liked\" playlist\n" +
                                                            "• Reject Songs: Tap the 👎 button to move songs to your \"Rejected\" playlist\n" +
                                                            "• Rate Songs: Use the ⭐ star rating system (1-5 stars) to rate your music\n" +
                                                            "• This tag will be visible in the song's metadata, in the comments line\n" +
                                                            "• Search: Use the search icon in the header to find specific songs",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "PLAYLIST NAVIGATION:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• To Do: Your main playlist with unsorted music files\n" +
                                                            "  * Subfolder selection from the root source folder\n" +
                                                            "• Liked: Songs you've marked as favorites\n" +
                                                            "• Rejected: Songs you don't want to keep",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "SPECTROGRAM ANALYSIS:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Generate visual spectrograms of your music files\n" +
                                                            "• Share spectrogram reports with detailed audio analysis",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "FILE ORGANIZATION:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Automatic file sorting into liked/rejected folders\n" +
                                                            "• Multi-select mode for batch operations. Accessible from the \"Actions\" menu in top right corner\n" +
                                                            "• ZIP creation for sharing liked songs",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "CUSTOMIZATION:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Adjustable waveform heights (main and mini waveforms)\n" +
                                                            "• Multiple themes and color schemes\n" +
                                                            "• Haptic feedback controls",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "SEARCH & FILTER:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Real-time search across all playlists\n" +
                                                            "• Search by filename, artist, or song title\n" +
                                                            "• Click to play a song from the search results\n" +
                                                            "• Auto-focus search with keyboard support",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                            
                                            item {
                                                Text(
                                                    text = "SHARING OPTIONS:",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface, // Theme contrast text
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "• Share liked songs as ZIP archive\n" +
                                                            "  * This will zip and share the entire Liked Folder\n" +
                                                            "• Export liked songs list as TXT file\n" +
                                                            "• Share spectrogram analysis reports",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // Theme text with slight transparency
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                        
                        // Conditionally display search results or current playlist files (reverted to original)
                        val displayFiles = currentPlaylistFiles // Search results handled within dropdown
                        
                        // Playlist Items (dynamic sizing) - Performance optimized
                        itemsIndexed(
                            items = currentPlaylistFiles, 
                            key = { _, mf -> "${mf.uri}_${mf.subfolder ?: ""}" } // More unique key including subfolder
                        ) { index, item ->
                            // Cache expensive calculations
                            val isCurrent = remember(currentPlayingFile?.uri, item.uri) { 
                                currentPlayingFile?.uri == item.uri 
                            }
                            val isCompactScreen = remember(screenWidth, screenHeight) { 
                                screenWidth < 600.dp || screenHeight < 800.dp 
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (isCompactScreen) 6.dp else 10.dp, 
                                        vertical = if (isCompactScreen) 1.dp else 2.dp
                                    )
                                    .clickable {
                                        if (isMultiSelectionMode) {
                                            viewModel.toggleSelection(index)
                                        } else {
                                            viewModel.jumpTo(index) // Jump to this item in the current playlist
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isCurrent -> when (currentPlaylistTab) {
                                            PlaylistTab.TODO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            PlaylistTab.LIKED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                            PlaylistTab.REJECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        }
                                        else -> when (currentPlaylistTab) {
                                            PlaylistTab.TODO -> MaterialTheme.colorScheme.surface
                                            PlaylistTab.LIKED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
                                            PlaylistTab.REJECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
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
                                        // Filename
                                        Text(
                                            text = item.name,
                                            style = if (isCurrent) {
                                                // For current song, use larger font but allow multiple lines
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                                )
                                            } else {
                                                // For other songs, use smaller font to fit more text
                                                MaterialTheme.typography.bodySmall.copy(
                                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 0.9f
                                                )
                                            },
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                        // Removed time display in playlist row for performance

                                            
                                            // Rating display removed
                                        }
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
                                                    onClick = { 
                                                        CrashLogger.log("MusicPlayerScreen", "🔍 Playlist LIKE button clicked for ${selectedIndices.size} files")
                                                        viewModel.sortSelectedFiles(SortAction.LIKE)
                                                        showInfoMessage("Moved to Liked", "success")
                                                    },
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
                                                    onClick = { 
                                                        CrashLogger.log("MusicPlayerScreen", "🔍 Playlist REJECT button clicked for ${selectedIndices.size} files")
                                                        viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                        showInfoMessage("Moved to Rejected", "error")
                                                    },
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
                                                    try {
                                                        viewModel.sortAtIndex(index, SortAction.LIKE) 
                                                    } catch (e: Exception) {
                                                        // Handle crash gracefully
                                                        println("Error in like button: ${e.message}")
                                                    }
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
                                                    try {
                                                        viewModel.sortAtIndex(index, SortAction.DISLIKE) 
                                                    } catch (e: Exception) {
                                                        // Handle crash gracefully
                                                        println("Error in dislike button: ${e.message}")
                                                    }
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
                                            
                                            // Move to folder button (available in all playlists)
                                            IconButton(
                                                onClick = { 
                                                    // Set the current file to this item without playing it
                                                    val currentFiles = when (currentPlaylistTab) {
                                                        PlaylistTab.TODO -> musicFiles
                                                        PlaylistTab.LIKED -> likedFiles
                                                        PlaylistTab.REJECTED -> rejectedFiles
                                                    }
                                                    if (index < currentFiles.size) {
                                                        viewModel.setCurrentFileWithoutPlaying(currentFiles[index])
                                                    }
                                                    showSubfolderSelectionDialog = true
                                                },
                                                modifier = Modifier.size(if (isCompactScreen) 36.dp else 48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = "Move to Subfolder",
                                                    tint = Color(0xFF4CAF50), // Green color
                                                    modifier = Modifier.size(if (isCompactScreen) 18.dp else 24.dp)
                                                )
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
                                    if (speedFactor == 1f) {
                                        animationSpecs.bouncySpring
                                    } else {
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy * speedFactor,
                                            stiffness = Spring.StiffnessLow * speedFactor
                                        )
                                    }
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
                                                    try {
                                                        when {
                                                            miniDragOffset > 0 -> viewModel.sortCurrentFile(SortAction.LIKE)
                                                            miniDragOffset < 0 -> viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                        }
                                                    } catch (e: Exception) {
                                                        // Handle crash gracefully
                                                        println("Error in miniplayer swipe gesture: ${e.message}")
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
                                                    fadeIn(animationSpec = animationSpecs.songNameTween) + slideInVertically() togetherWith
                                                    fadeOut(animationSpec = animationSpecs.songNameTween) + slideOutVertically()
                                                },
                                                label = "Miniplayer Song Name Animation",
                                                modifier = Modifier.fillMaxWidth()
                                            ) { targetName ->
                                            val title = targetName.ifEmpty { "Unknown Track" }
                                            val desiredLines = when {
                                                title.length <= 28 -> 2
                                                title.length <= 56 -> 3
                                                else -> 4
                                            }
                                            val fontScale = when (desiredLines) {
                                                2 -> 0.9f    // bigger when fewer lines
                                                3 -> 0.75f
                                                else -> 0.62f
                                            }
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = desiredLines,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            }
                                            // Miniplayer metadata rows under title
                                            val miniAnalysis by viewModel.audioManager.analysisResult.collectAsState()
                                            val miniDurationText = formatTime(duration)
                                            val miniBitrateText = "${calculateBitrate(file.size, duration)} kbps"
                                            val miniSizeText = formatFileSize(file.size)
                                            val miniBpm = miniAnalysis?.bpm?.let { "${(it + 0.5f).toInt()}" } ?: "—"
                                            val miniKey = miniAnalysis?.key?.let { toCamelotKey(it) } ?: "—"
                                            // Row 1: Time | Bitrate | Size
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "| Time $miniDurationText | $miniBitrateText | $miniSizeText |",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            // Row 2: BPM | Key | Analyze
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "| BPM: $miniBpm | Key $miniKey |",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val miniAnalyzing by viewModel.audioManager.isAnalyzing.collectAsState()
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            currentPlayingFile?.let { viewModel.audioManager.analyzeFile(it, force = true) }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(22.dp),
                                                    enabled = !miniAnalyzing
                                                ) {
                                                    if (miniAnalyzing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(14.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Analyze",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Right column: Control buttons in two rows
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            // First row: PREV PLAY NEXT with modern style
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Previous button - mini style
                                                Card(
                                                    onClick = { 
                                                    hapticFeedback()
                                                viewModel.previous() 
                                                    },
                                                    modifier = Modifier.size(41.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.SkipPrevious, 
                                                            "Previous", 
                                                            modifier = Modifier.size(22.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                
                                                // Play/Pause button - mini FAB style
                                                FloatingActionButton(
                                                    onClick = { 
                                                    hapticFeedback()
                                                viewModel.playPause() 
                                                    }, 
                                                    modifier = Modifier.size(29.dp),
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    elevation = FloatingActionButtonDefaults.elevation(
                                                        defaultElevation = 4.dp,
                                                        pressedElevation = 6.dp
                                                    )
                                                ) {
        Icon(
                                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    if (isPlaying) "Pause" else "Play",
                                                        modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            
                                                // Next button - mini style
                                                Card(
                                                    onClick = { 
                                                    hapticFeedback()
                                                viewModel.next() 
                                                    },
                                                    modifier = Modifier.size(41.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.SkipNext, 
                                                            "Next", 
                                                            modifier = Modifier.size(22.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Second row: DISLIKE LIKE SPEK
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                        Icons.Default.ThumbDown, 
                                                        contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Reject All Selected" else "Dislike", 
                                                        tint = NoButton,
                                                    modifier = Modifier.size(19.dp)
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
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                        Icons.Default.Favorite, 
                                                        contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Like All Selected" else "Like", 
                                                        tint = YesButton,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                            
                                                // Spectrogram Text Button in mini player
                                            IconButton(
                                                onClick = { 
                                                    // Toggle spectrogram popup
                                                    showSpectrogram = !showSpectrogram
                                                }, 
                                                modifier = Modifier.size(38.dp)
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
                                    
                                    // Mini player: shared waveform with toggle (replaces progress bar)
                                    val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                    WaveformWithToggle(
                                        sharedState = sharedWaveformState,
                                        progress = progressPercent,
                                        onSeek = { seekProgress ->
                                            val seekPosition = (seekProgress * duration).toLong()
                                            viewModel.seekTo(seekPosition)
                                        },
                                        songUri = currentFile?.uri.toString(),
                                        waveformHeight = visualSettings.miniWaveformHeight.toInt(),
                                        currentPosition = currentPosition,
                                        totalDuration = duration,
                                        modifier = Modifier
                                            .fillMaxWidth()
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
    
    // Subfolder creation dialog
    if (showSubfolderCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSubfolderDialog() },
            title = { Text("Create New Subfolder") },
            text = {
                Column {
                    Text("Enter a name for the new subfolder:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSubfolderName,
                        onValueChange = { viewModel.setNewSubfolderName(it) },
                        label = { Text("Subfolder Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createNewSubfolder() },
                    enabled = newSubfolderName.trim().isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSubfolderDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Subfolder management dialog
    if (showSubfolderManagementDialogState) {
        var selectedSubfolders by remember { mutableStateOf(setOf<String>()) }
        
        AlertDialog(
            onDismissRequest = { 
                viewModel.hideSubfolderManagementDialog()
                selectedSubfolders = emptySet()
            },
            title = { Text("Manage Subfolders") },
            text = {
                Column {
                    Text("Select subfolders to remove from memory:")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show all subfolders from history
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(subfolderHistory) { subfolder ->
                            val isSelected = selectedSubfolders.contains(subfolder)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        selectedSubfolders = if (isSelected) {
                                            selectedSubfolders - subfolder
                                        } else {
                                            selectedSubfolders + subfolder
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else 
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { 
                                            selectedSubfolders = if (isSelected) {
                                                selectedSubfolders - subfolder
                                            } else {
                                                selectedSubfolders + subfolder
                                            }
                                        }
                                    )
                                    Text(
                                        text = subfolder,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        if (selectedSubfolders.isNotEmpty()) {
                            viewModel.removeSubfoldersFromHistory(selectedSubfolders.toList())
                        }
                        viewModel.hideSubfolderManagementDialog()
                        selectedSubfolders = emptySet()
                    },
                    enabled = selectedSubfolders.isNotEmpty()
                ) {
                    Text("Remove Selected")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.hideSubfolderManagementDialog()
                    selectedSubfolders = emptySet()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Subfolder selection dialog for playlist items
    if (showSubfolderSelectionDialog) {
        // Update subfolder info when dialog is shown
        LaunchedEffect(showSubfolderSelectionDialog) {
            if (showSubfolderSelectionDialog) {
                viewModel.updateSubfolderInfo()
            }
        }
        
        AlertDialog(
            onDismissRequest = { showSubfolderSelectionDialog = false },
            title = { 
                Text(
                    when (currentPlaylistTab) {
                        PlaylistTab.TODO -> "Move to Liked Subfolder"
                        PlaylistTab.REJECTED -> "Move to Liked Subfolder"
                        PlaylistTab.LIKED -> "Move to Subfolder"
                    }
                )
            },
            text = {
                var selectedViewSubfolders by remember { mutableStateOf(setOf<String>()) }
                Column {
                    Text("Select a subfolder to move the current file:")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add new subfolder option
                    TextButton(
                        onClick = {
                            showSubfolderSelectionDialog = false
                            viewModel.showSubfolderDialog()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                            Text("Create new subfolder")
                        }
                    }
                    
                    // Available subfolders with file counts (tap to move current file)
                    if (availableSubfolders.isNotEmpty()) {
                        Text(
                            "Available subfolders:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        availableSubfolders.forEach { subfolder ->
                            val fileCount = subfolderFileCounts[subfolder] ?: 0
                            TextButton(
                                onClick = {
                                    showSubfolderSelectionDialog = false
                                    viewModel.moveCurrentFileToSubfolder(subfolder)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$subfolder")
                                    Text(
                                        "$fileCount files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // Multi-select to VIEW liked subfolders (aggregate)
                        if (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) {
                            Text(
                                "Or select multiple subfolders to view in Liked:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            availableSubfolders.forEach { name ->
                                val checked = selectedViewSubfolders.contains(name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedViewSubfolders = if (checked) selectedViewSubfolders - name else selectedViewSubfolders + name
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                selectedViewSubfolders = if (checked) selectedViewSubfolders - name else selectedViewSubfolders + name
                                            }
                                        )
                                        Text(name, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Text(
                                        "${subfolderFileCounts[name] ?: 0}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val list = selectedViewSubfolders.toList()
                                    if (list.isNotEmpty()) {
                                        showSubfolderSelectionDialog = false
                                        viewModel.loadFilesFromLikedSubfolders(list)
                                    }
                                },
                                enabled = selectedViewSubfolders.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    
                    // Recent subfolders (from history) - empty folders
                    val recentSubfolders = subfolderHistory.filter { it !in availableSubfolders }
                    if (recentSubfolders.isNotEmpty()) {
                        Text(
                            "Recent subfolders (empty):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        recentSubfolders.forEach { subfolder ->
                            TextButton(
                                onClick = {
                                    showSubfolderSelectionDialog = false
                                    viewModel.moveCurrentFileToSubfolder(subfolder)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$subfolder")
                                    Text(
                                        "0 files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubfolderSelectionDialog = false }) { Text("Close") } }
        )
    }


}
