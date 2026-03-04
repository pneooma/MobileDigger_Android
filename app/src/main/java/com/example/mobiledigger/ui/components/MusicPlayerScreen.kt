@file:OptIn(ExperimentalFoundationApi::class)
package com.example.mobiledigger.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.example.mobiledigger.R
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.ui.components.SharedWaveformState
import com.example.mobiledigger.ui.components.rememberSharedWaveformState
import com.example.mobiledigger.ui.components.SharedWaveformDisplay
import com.example.mobiledigger.ui.components.WaveformWithToggle
import com.example.mobiledigger.ui.components.SettingsDialog
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
import com.example.mobiledigger.ui.components.ScrollableWaveformView
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
import kotlinx.coroutines.Job
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.ui.theme.DislikeRed
import com.example.mobiledigger.ui.theme.GreenAccent
import com.example.mobiledigger.ui.theme.GroovyBlue
import com.example.mobiledigger.ui.theme.LikeGreen
import com.example.mobiledigger.ui.theme.NoButton
import com.example.mobiledigger.ui.theme.YesButton
import com.example.mobiledigger.ui.theme.AvailableThemes
import com.example.mobiledigger.ui.theme.appFontFamilies
import com.example.mobiledigger.viewmodel.MusicViewModel
import com.example.mobiledigger.viewmodel.PlaylistTab
import com.example.mobiledigger.viewmodel.SortField
import com.example.mobiledigger.viewmodel.SortOrder

import kotlin.math.abs
import kotlin.math.ceil
import java.util.Locale
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll

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


// Sealed class for playlist items (headers and files)
sealed class PlaylistItem {
    data class HeaderItem(val subfolder: String) : PlaylistItem()
    data class FileItem(val file: com.example.mobiledigger.model.MusicFile) : PlaylistItem()
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
    val likedFilter by viewModel.likedFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val losslessOnly by viewModel.losslessOnly.collectAsState()
    // BPM removed

    // Local state for spectrogram visibility
    var showSpectrogram by remember { mutableStateOf(false) }
    
    // Local state for analyze prompt dialog
    var showAnalyzePrompt by remember { mutableStateOf(false) }
    
    // State to trigger spectrogram after delay
    var triggerSpectrogramAfterDelay by remember { mutableStateOf(false) }
    
    // Waveform visibility state (main player)
    var isWaveformVisible by remember { mutableStateOf(true) }
    
    // Main player and playlists visibility states (start hidden by default)
    var isMainPlayerVisible by remember { mutableStateOf(true) }
    var isPlaylistsVisible by remember { mutableStateOf(false) }
    
    // Preserve user preferences for visibility when entering/exiting Multi-Select
    var prevMainPlayerVisible by remember { mutableStateOf<Boolean?>(null) }
    var prevWaveformVisible by remember { mutableStateOf<Boolean?>(null) }
    
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

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectZipSource(it) }
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
    
    // Job for managing notification display
    var notificationJob by remember { mutableStateOf<Job?>(null) }
    
    // Helper function to show info messages with proper replacement
    fun showInfoMessage(message: String, type: String = "info") {
        // Cancel any existing notification job
        notificationJob?.cancel()
        
        notificationJob = scope.launch {
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
    var viewModeForSubfolderDialog by remember { mutableStateOf(false) } // When true, dialog shows VIEW selector; when false, MOVE-only
    var showLikedSubfoldersViewDialog by remember { mutableStateOf(false) } // New: multi-select liked subfolders to view
    var selectedLikedSubfolders by remember { mutableStateOf(setOf<String>()) }

    val zipInProgress by viewModel.zipInProgress.collectAsState()
    val zipProgress by viewModel.zipProgress.collectAsState()
    
    val currentSearchText by viewModel.searchText.collectAsState() // Moved here
    val searchResults by viewModel.searchResults.collectAsState() // Moved here
    val playedButNotActioned by viewModel.playedButNotActioned.collectAsState()
    
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
    val isMonochrome = viewModel.themeManager.selectedTheme.value.name == "Monochrome"
    val monoColor = MaterialTheme.colorScheme.onSurfaceVariant
    val customWaveformColor = remember(visualSettings.waveformColor) {
        if (visualSettings.waveformColor != 0) Color(visualSettings.waveformColor) else null
    }
    val animationSpecs = rememberOptimizedAnimationSpecs()
    

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    
    // Removed periodic analyze prompt polling; manual-only policy applies
    
    // Analyze prompt dialog
    if (showAnalyzePrompt) {
        AlertDialog(
            onDismissRequest = { showAnalyzePrompt = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
        SettingsDialog(
            visualSettingsManager = viewModel.visualSettingsManager,
            themeManager = viewModel.themeManager,
            onDismiss = { showSettingsDialog = false }
        )
    }
    
    // Theme Selection Dialog (now integrated in SettingsDialog)
    if (false && showThemeSelectionDialog) {
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

    


    // Waveform Settings Dialog

    // Dynamic sizing based on screen size
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    // val isCompactScreen = screenWidth < 600.dp || screenHeight < 800.dp // isCompactScreen is used, keeping
    val playlistMaxHeight = screenHeight * 0.65f // Playlist takes 65% of screen
    // Adaptive button size for 2x2 grid in playlist rows
    val adaptiveButtonSize = (screenWidth.value / 14f).coerceIn(24f, 40f).dp
    val adaptiveIconSize = (adaptiveButtonSize.value * 0.56f).dp
    val adaptiveSpekFontSize = (adaptiveButtonSize.value * 0.25f).sp

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
                top = paddingValues.calculateTopPadding() / 2,
                end = 6.dp,
                bottom = paddingValues.calculateBottomPadding() / 2
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

        // Reject count
        val playedButNotActionedCount = remember(playedButNotActioned, currentPlayingFile) {
            val currentUri = currentPlayingFile?.uri
            if (currentUri != null) {
                playedButNotActioned.count { it != currentUri }
            } else {
                playedButNotActioned.size
            }
        }

        // Rename dialogs state
        var showRenameActionDialog by remember { mutableStateOf(false) }
        var showRenameSelectedDialog by remember { mutableStateOf(false) }
        var showRenameCurrentDialog by remember { mutableStateOf(false) }
        var showRenameOneDialog by remember { mutableStateOf(false) }
        var manualRenameText by remember { mutableStateOf("") }
        var pendingIndexForMulti by remember { mutableStateOf<Int?>(null) }
        var pendingIndexForSingle by remember { mutableStateOf<Int?>(null) }
        var renameCase by remember { mutableStateOf(MusicViewModel.RenameCase.MANUAL) }

        // In Multi-Select: hide main player; restore user's prefs on exit
        LaunchedEffect(isMultiSelectionMode) {
            if (isMultiSelectionMode) {
                if (prevMainPlayerVisible == null) prevMainPlayerVisible = isMainPlayerVisible
                if (prevWaveformVisible == null) prevWaveformVisible = isWaveformVisible
                isMainPlayerVisible = false
                isWaveformVisible = true
            } else {
                prevMainPlayerVisible?.let { isMainPlayerVisible = it }
                prevWaveformVisible?.let { isWaveformVisible = it }
                prevMainPlayerVisible = null
                prevWaveformVisible = null
            }
        }

        // Header row: [Logo] [Reject All] [Player] [Playlist]
        if (musicFiles.isNotEmpty() && !isLoading) {
            val labelStyle = MaterialTheme.typography.labelSmall
            var showBulkSendDialog by remember { mutableStateOf(false) }
            val hdrBtnHeight = 29.dp
            val hdrBtnPad = PaddingValues(horizontal = 7.dp, vertical = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // MobileDigger logo + version (first from left)
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "MobileDigger",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.45f,
                            lineHeight = MaterialTheme.typography.headlineMedium.fontSize * 0.45f
                        ),
                        color = if (isMonochrome) monoColor else GreenAccent
                    )
                    Text(
                        text = "v11.09",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                // Reject All button
                if ((currentPlaylistTab == PlaylistTab.TODO && hasDestinationFolder) || isMultiSelectionMode) {
                    OutlinedButton(
                        onClick = {
                            if (playedButNotActionedCount > 0) viewModel.movePlayedButNotActionedToRejected()
                        },
                        modifier = Modifier.height(hdrBtnHeight),
                        border = BorderStroke(1.dp, Color.White),
                        contentPadding = hdrBtnPad,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val rejectColor = if (isMonochrome) monoColor else Color.Red
                            Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(10.dp), tint = rejectColor)
                            Text("Reject $playedButNotActionedCount", style = labelStyle, fontWeight = FontWeight.Bold, maxLines = 1, color = rejectColor)
                        }
                    }
                }
                // Show/Hide Main Player
                OutlinedButton(
                    onClick = { isMainPlayerVisible = !isMainPlayerVisible },
                    enabled = !isMultiSelectionMode,
                    contentPadding = hdrBtnPad,
                    modifier = Modifier.height(hdrBtnHeight)
                ) {
                    Text(if (isMainPlayerVisible) "Hide Main Player" else "Show Main Player", style = labelStyle, maxLines = 1)
                }
                // Show/Hide Playlists
                OutlinedButton(
                    onClick = { isPlaylistsVisible = !isPlaylistsVisible },
                    contentPadding = hdrBtnPad,
                    modifier = Modifier.height(hdrBtnHeight)
                ) {
                    Text(if (isPlaylistsVisible) "Hide Playlists" else "Show Playlists", style = labelStyle, maxLines = 1)
                }
            }

            // Toolbar bar: Home, Settings, Search, Rescan, Info, Sort, Rename, MultiSelect, SpEk
            val tbIconSize = (screenWidth.value / 9f).coerceIn(22f, 36f).dp
            val showLabels = visualSettings.showToolbarLabels
            val tbLabelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 8.sp)
            var filterMenuExpanded by remember { mutableStateOf(false) }

            @Composable
            fun ToolbarButton(icon: @Composable () -> Unit, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp)
                ) {
                    icon()
                    if (showLabels) Text(label, style = tbLabelStyle, maxLines = 1, textAlign = TextAlign.Center)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolbarButton(icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Home", onClick = { viewModel.resetToInitialState() })
                ToolbarButton(icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Settings", onClick = { showSettingsDialog = true })
                ToolbarButton(icon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Search", onClick = { showSearchInput = !showSearchInput; if (!showSearchInput) { viewModel.updateSearchText(""); viewModel.clearSearchResults() } })
                ToolbarButton(icon = { Icon(Icons.Default.Sync, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Rescan", onClick = { viewModel.rescanSourceFolder() })
                Box {
                    ToolbarButton(icon = { Icon(Icons.Default.List, contentDescription = "Sort", tint = if (sortOrder != null || losslessOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(tbIconSize)) }, label = "Sort", onClick = { filterMenuExpanded = true })
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Name"); if (sortOrder?.field == SortField.NAME) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.NAME) !(sortOrder?.ascending ?: true) else true; viewModel.setSortOrder(SortField.NAME, asc) })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("File Size"); if (sortOrder?.field == SortField.FILE_SIZE) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.FILE_SIZE) !(sortOrder?.ascending ?: true) else false; viewModel.setSortOrder(SortField.FILE_SIZE, asc) })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Date Added"); if (sortOrder?.field == SortField.DATE_ADDED) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.DATE_ADDED) !(sortOrder?.ascending ?: true) else false; viewModel.setSortOrder(SortField.DATE_ADDED, asc) })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Duration"); if (sortOrder?.field == SortField.DURATION) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.DURATION) !(sortOrder?.ascending ?: true) else true; viewModel.setSortOrder(SortField.DURATION, asc) })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Extension"); if (sortOrder?.field == SortField.EXTENSION) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.EXTENSION) !(sortOrder?.ascending ?: true) else true; viewModel.setSortOrder(SortField.EXTENSION, asc) })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Subfolder"); if (sortOrder?.field == SortField.SUBFOLDER) { Spacer(Modifier.width(4.dp)); Text(if (sortOrder?.ascending == true) "▲" else "▼", style = labelStyle) } } }, onClick = { val asc = if (sortOrder?.field == SortField.SUBFOLDER) !(sortOrder?.ascending ?: true) else true; viewModel.setSortOrder(SortField.SUBFOLDER, asc) })
                        DropdownMenuItem(text = { Text("Shuffle") }, onClick = { viewModel.setSortOrder(SortField.RANDOM, true) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Lossless Only"); if (losslessOnly) { Spacer(Modifier.width(4.dp)); Text("✓", color = MaterialTheme.colorScheme.primary) } } }, onClick = { viewModel.toggleLosslessOnly() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Clear Sort & Filter") }, onClick = { filterMenuExpanded = false; viewModel.clearSort(); if (losslessOnly) viewModel.toggleLosslessOnly() })
                    }
                }
                ToolbarButton(icon = { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Rename", onClick = { if (currentPlayingFile != null) { manualRenameText = (currentPlayingFile?.name ?: "").substringBeforeLast('.', (currentPlayingFile?.name ?: "")); showRenameCurrentDialog = true } })
                ToolbarButton(icon = { Icon(Icons.Default.Checklist, contentDescription = "Select", tint = if (isMultiSelectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Select", onClick = { viewModel.toggleMultiSelectionMode() })
                ToolbarButton(
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                            val spekColor = if (isMonochrome) monoColor else Color(0xFFFFB6C1)
                            Text("Sp", style = labelStyle.copy(fontWeight = FontWeight.Bold, fontSize = (tbIconSize.value * 0.35f).sp), color = spekColor)
                            Text("eK", style = labelStyle.copy(fontWeight = FontWeight.Bold, fontSize = (tbIconSize.value * 0.35f).sp), color = spekColor)
                        }
                    },
                    label = "SpEk",
                    onClick = { if (currentPlayingFile != null) showSpectrogram = true }
                )
            }

            // Multi-select inline controls — compact grid layout
            if (isMultiSelectionMode) {
                val selectedCount by viewModel.selectedIndices.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val msBtnMod = Modifier.height(26.dp).border(1.dp, Color.White, RoundedCornerShape(16.dp))
                    val msBtnColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    val msPad = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    val msLabelStyle = labelStyle.copy(fontSize = 10.sp)
                    OutlinedButton(onClick = { viewModel.sortSelectedFiles(SortAction.DISLIKE) }, modifier = Modifier.height(26.dp), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isMonochrome) monoColor else Color.Red); Spacer(Modifier.width(3.dp)); Text("Reject", style = msLabelStyle, maxLines = 1)
                    }
                    OutlinedButton(onClick = { showRenameSelectedDialog = true }, modifier = Modifier.height(26.dp), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)); Text("Rename", style = msLabelStyle, maxLines = 1)
                    }
                    OutlinedButton(onClick = { viewModel.sortSelectedFiles(SortAction.LIKE) }, modifier = Modifier.height(26.dp), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = YesButton); Spacer(Modifier.width(3.dp)); Text("Like", style = msLabelStyle, maxLines = 1)
                    }
                    OutlinedButton(onClick = { viewModel.toggleMultiSelectionMode() }, modifier = Modifier.height(26.dp), border = BorderStroke(1.dp, Color.Red), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Text("Exit", style = msLabelStyle, maxLines = 1, color = Color.Red)
                    }
                }
                // Selection count indicator
                Text("${selectedCount.size} selected", style = labelStyle.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 8.dp))
            }
            if (showBulkSendDialog && isMultiSelectionMode) {
                AlertDialog(
                    onDismissRequest = { showBulkSendDialog = false },
                    title = { Text("Send Selected Files To") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showBulkSendDialog = false; viewModel.sortSelectedFiles(SortAction.LIKE) }, modifier = Modifier.fillMaxWidth()) { Text("Liked") }
                            Button(onClick = { showBulkSendDialog = false; viewModel.sortSelectedFiles(SortAction.DISLIKE) }, modifier = Modifier.fillMaxWidth()) { Text("Rejected") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showBulkSendDialog = false }) { Text("Close") } }
                )
            }
        }
        
        // Rename: action chooser dialog
        if (showRenameActionDialog) {
            val pendingIdx = pendingIndexForMulti
            val isPendingCurrent = pendingIdx != null && pendingIdx >= 0 && pendingIdx < currentPlaylistFiles.size && currentPlaylistFiles[pendingIdx].uri == currentPlayingFile?.uri
            AlertDialog(
                onDismissRequest = { showRenameActionDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Choose Action") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPendingCurrent) {
                            Button(onClick = {
                                showRenameActionDialog = false
                                manualRenameText = (currentPlayingFile?.name ?: "").substringBeforeLast('.', (currentPlayingFile?.name ?: ""))
                                showRenameCurrentDialog = true
                            }, modifier = Modifier.fillMaxWidth()) { Text("Rename") }
                        } else {
                            Button(onClick = {
                                showRenameActionDialog = false
                                pendingIndexForSingle = pendingIdx
                                val f = pendingIdx?.let { idx -> currentPlaylistFiles.getOrNull(idx) }
                                manualRenameText = (f?.name ?: "").substringBeforeLast('.', (f?.name ?: ""))
                                showRenameOneDialog = true
                            }, modifier = Modifier.fillMaxWidth()) { Text("Rename This File") }

                            Button(onClick = {
                                showRenameActionDialog = false
                                pendingIndexForMulti?.let { viewModel.enableMultiSelectionMode(it) } ?: viewModel.toggleMultiSelectionMode()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Enter Multi-Select Mode") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showRenameActionDialog = false }) { Text("Close") }
                }
            )
        }

        // Rename selected files dialog
        if (showRenameSelectedDialog) {
            AlertDialog(
                onDismissRequest = { showRenameSelectedDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Rename Selected Files") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            MusicViewModel.RenameCase.TITLE to "All Words With Capital Letter",
                            MusicViewModel.RenameCase.UPPER to "ALL LETTERS UP",
                            MusicViewModel.RenameCase.LOWER to "all letters small"
                        ).forEach { (case, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = renameCase == case, onClick = { renameCase = case })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRenameSelectedDialog = false
                        viewModel.renameSelectedFiles(renameCase)
                    }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameSelectedDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Rename one inactive file dialog
        if (showRenameOneDialog) {
            AlertDialog(
                onDismissRequest = { showRenameOneDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Rename File") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = manualRenameText,
                            onValueChange = { manualRenameText = it },
                            label = { Text("File name (without extension)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        listOf(
                            MusicViewModel.RenameCase.TITLE to "All Words With Capital Letter",
                            MusicViewModel.RenameCase.UPPER to "ALL LETTERS UP",
                            MusicViewModel.RenameCase.LOWER to "all letters small"
                        ).forEach { (case, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = renameCase == case, onClick = { renameCase = case })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRenameOneDialog = false
                        pendingIndexForSingle?.let { idx -> viewModel.renameFileAtIndex(idx, manualRenameText, renameCase) }
                    }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameOneDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Rename current playing file dialog
        if (showRenameCurrentDialog) {
            AlertDialog(
                onDismissRequest = { showRenameCurrentDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Rename Current File") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = manualRenameText,
                            onValueChange = { manualRenameText = it },
                            label = { Text("File name (without extension)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        listOf(
                            MusicViewModel.RenameCase.TITLE to "All Words With Capital Letter",
                            MusicViewModel.RenameCase.UPPER to "ALL LETTERS UP",
                            MusicViewModel.RenameCase.LOWER to "all letters small"
                        ).forEach { (case, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = renameCase == case, onClick = { renameCase = case })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRenameCurrentDialog = false
                        viewModel.renameCurrentPlayingFile(manualRenameText, renameCase)
                    }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameCurrentDialog = false }) { Text("Cancel") }
                }
            )
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
        
            
            Spacer(modifier = Modifier.height(6.dp))
            
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

                    // Open ZIP Archive button
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { zipLauncher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open ZIP Archive")
                        }
                    }

                    // Second row: Rescan button
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val lastSource = viewModel.preferences.getSourceRootUri()
                        val lastFolderName = lastSource?.let { uri ->
                            kotlin.runCatching { android.net.Uri.parse(uri).path ?: uri }.getOrNull()?.let { path ->
                                path.trim('/').split('/').lastOrNull() ?: path
                            }
                        } ?: "(none)"
                        ElevatedButton(
                            onClick = { viewModel.rescanSourceFolder() },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rescan\n$lastFolderName", fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                    // Recent Sources list
                    val recentSources by viewModel.recentSourceUris.collectAsState()
                    if (recentSources.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Recent Sources",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        recentSources.take(10).forEach { uriString ->
                            val label = kotlin.runCatching { android.net.Uri.parse(uriString).path ?: uriString }.getOrNull()?.let { p ->
                                val parts = p.trim('/').split('/')
                                val tail = parts.takeLast(2)
                                if (tail.isNotEmpty()) tail.joinToString("/") else p
                            } ?: uriString
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setSourceFolderPreference(android.net.Uri.parse(uriString)) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Load", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { viewModel.removeRecentSource(uriString) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                    }
                }
                }
                    // Recent Destinations list
                    val recentDestinations by viewModel.recentDestinationUris.collectAsState()
                    if (recentDestinations.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Recent Destinations",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        recentDestinations.take(5).forEach { uriString ->
                            val label = kotlin.runCatching { android.net.Uri.parse(uriString).path ?: uriString }.getOrNull()?.let { p ->
                                val parts = p.trim('/').split('/')
                                val tail = parts.takeLast(2)
                                if (tail.isNotEmpty()) tail.joinToString("/") else p
                            } ?: uriString
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.selectDestinationFolder(android.net.Uri.parse(uriString)) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) { Text("Set", style = MaterialTheme.typography.labelSmall) }
                                IconButton(onClick = { viewModel.removeRecentDestination(uriString) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                
                // Render fixed headers (main player and playlist tabs) outside the scrolling list
                Column(modifier = Modifier.fillMaxSize()) {
                    var suppressMiniOnLeftSwipe by remember { mutableStateOf(false) }
                    var promotingNextUri by remember { mutableStateOf<Uri?>(null) }
                    
                    // Local composable to render the main player section (previously inside the list)
                    @Composable
                    fun MainPlayerSection() {
                        val currentFile = currentPlayingFile
                        if (currentFile != null) {
                            val file = currentFile
                            val scope = rememberCoroutineScope()
                            val mainSwipeOffset = remember { Animatable(0f) }
                            var swipeDirection by remember { mutableStateOf(0) }
                            val transitionAlpha by animateFloatAsState(
                                targetValue = if (isTransitioning) 0.3f else 1f,
                                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                                label = "transition_fade"
                            )
                            val animationSpec = remember(visualSettings.enableAnimations, visualSettings.animationSpeed) {
                                if (visualSettings.enableAnimations) {
                                    val speedFactor = 1f / visualSettings.animationSpeed
                                    spring<Float>(
                                        dampingRatio = Spring.DampingRatioMediumBouncy * speedFactor,
                                        stiffness = Spring.StiffnessMediumLow * speedFactor * 1.5f
                                    )
                                } else {
                                    tween<Float>(durationMillis = 0)
                                }
                            }
                            val animatedOffset = mainSwipeOffset.value
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
                            val played = viewModel.preferences.getListened()
                            val yes = viewModel.preferences.getLiked()
                            val no = viewModel.preferences.getRefused()
                            val totalTracks = when (currentPlaylistTab) {
                                PlaylistTab.TODO -> musicFiles.size
                                PlaylistTab.LIKED -> likedFiles.size
                                PlaylistTab.REJECTED -> rejectedFiles.size
                            }
                            Spacer(Modifier.height(0.dp))
                            val panelAnimMs = (500 / visualSettings.animationSpeed).toInt()
                            AnimatedVisibility(
                                visible = isMainPlayerVisible,
                                enter = expandVertically(animationSpec = tween(panelAnimMs)) + fadeIn(animationSpec = tween(panelAnimMs)),
                                exit = shrinkVertically(animationSpec = tween(panelAnimMs)) + fadeOut(animationSpec = tween(panelAnimMs))
                            ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .offset(y = (-40).dp)
                                    .graphicsLayer {
                                        translationX = animatedOffset
                                        scaleX = 1f + abs(animatedOffset) / 2000f
                                        scaleY = 1f + abs(animatedOffset) / 2000f
                                        alpha = transitionAlpha
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
                                    .then(
                                        run {
                                            val isCurrentInPlaylist = currentPlaylistFiles.any { it.uri == file.uri }
                                            if (isCurrentInPlaylist) Modifier.pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = { },
                                                    onDragEnd = {
                                                        val current = mainSwipeOffset.value
                                                        val threshold = 150f
                                                        val exit = if (current > 0) 520f else -520f
                                                        if (abs(current) > threshold) {
                                                            val prevFile = file
                                                            scope.launch {
                                                                mainSwipeOffset.animateTo(exit, tween(220))
                                                                mainSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                                                if (current < 0) {
                                                                    try {
                                                                        suppressMiniOnLeftSwipe = true
                                                                        try { prevFile?.let { viewModel.removeFromCurrentListByUri(it.uri) } } catch (_: Exception) {}
                                                                        viewModel.playNextAfterRemoval()
                                                                        prevFile?.let { viewModel.sortMusicFile(it, SortAction.DISLIKE) }
                                                                    } catch (_: Exception) { }
                                                                } else if (current > 0) {
                                                                    try { prevFile?.let { viewModel.sortMusicFile(it, SortAction.LIKE) } } catch (_: Exception) { }
                                                                }
                                                            }
                                                        } else {
                                                            scope.launch {
                                                                mainSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                                            }
                                                        }
                                                        swipeDirection = 0
                                                    }
                                                ) { _, dragAmount ->
                                                    val (x, _) = dragAmount
                                                    val newValue = (mainSwipeOffset.value + x * 0.85f).coerceIn(-300f, 300f)
                                                    scope.launch { mainSwipeOffset.snapTo(newValue) }
                                                    swipeDirection = when {
                                                        newValue > 80f -> 1
                                                        newValue < -80f -> -1
                                                        else -> 0
                                                    }
                                                }
                                            } else Modifier
                                        }
                                    ),
                                colors = CardDefaults.cardColors(containerColor = cardColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val durationText = formatTime(duration)
                                    val bitrateText = "${calculateBitrate(file.size, duration)} kbps"
                                    val sizeText = formatFileSize(file.size)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "| $durationText | $bitrateText | $sizeText |",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        OutlinedButton(
                                            onClick = { isWaveformVisible = !isWaveformVisible },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(22.dp)
                                        ) {
                                            Text(
                                                text = if (isWaveformVisible) "Hide Wave" else "Show Wave",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (isWaveformVisible && isMainPlayerVisible) {
                                        val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            WaveformWithToggle(
                                                sharedState = sharedWaveformState,
                                                progress = progressPercent,
                                                onSeek = { seekProgress ->
                                                    val seekPosition = (seekProgress * duration).toLong()
                                                    CrashLogger.log("Debug", "🎯 Seek calculation: progress=$seekProgress, duration=$duration, seekPosition=$seekPosition")
                                                    viewModel.seekTo(seekPosition)
                                                },
                                                songUri = currentFile?.uri.toString(),
                                                waveformHeight = visualSettings.waveformHeight.toInt(),
                                                currentPosition = currentPosition,
                                                totalDuration = duration,
                                                fileName = file.name,
                                                customWaveformColor = customWaveformColor,
                                                animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = (300 / visualSettings.animationSpeed).toInt(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            // BPM overlay removed
                                        }
                                    }
                                    if (isMainPlayerVisible) Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) { }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Card(
                                                onClick = { viewModel.previous() },
                                                modifier = Modifier.size(38.dp),
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
                                                        modifier = Modifier.size(19.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            FloatingActionButton(
                                                onClick = { viewModel.playPause() },
                                                modifier = Modifier.size(45.dp),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                elevation = FloatingActionButtonDefaults.elevation(
                                                    defaultElevation = 6.dp,
                                                    pressedElevation = 8.dp
                                                )
                                            ) {
                                                Icon(
                                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Card(
                                                onClick = { viewModel.next() },
                                                modifier = Modifier.size(38.dp),
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
                                                        modifier = Modifier.size(19.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        // Right side controls: Dislike / Undo / Like  |  Share / Spectrogram / Move
                                        val isCompactMain by remember(screenWidth, screenHeight) {
                                            mutableStateOf(screenWidth < 600.dp || screenHeight < 800.dp)
                                        }
                                        if (true) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                            // Group 2: Dislike / Undo / Like
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Dislike current
                                                Card(
                                                    onClick = {
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                        }
                                                    },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = NoButton
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ThumbDown,
                                                            contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Reject All Selected" else "Dislike",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                                // Undo
                                                Card(
                                                    onClick = { viewModel.undoLastAction() },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.Undo,
                                                            contentDescription = "Undo",
                                                            tint = MaterialTheme.colorScheme.onSecondary,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                                // Like current
                                                Card(
                                                    onClick = {
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.LIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.LIKE)
                                                        }
                                                    },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = YesButton
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Favorite,
                                                            contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Like All Selected" else "Like",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            // Group 3: Share / Spectrogram / Move-to-subfolder
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Share to WhatsApp
                                                Card(
                                                    onClick = { viewModel.shareToWhatsApp() },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color(0xFF25D366)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Share,
                                                            contentDescription = "Share to WhatsApp",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                                // Spectrogram quick button
                                                Card(
                                                    onClick = { showSpectrogram = true },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color(0xFFFFB6C1)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Text(
                                                                text = "Sp",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 8.sp
                                                                ),
                                                                color = Color.White,
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Text(
                                                                text = "eK",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 8.sp
                                                                ),
                                                                color = Color.White,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                                // Move to subfolder (opens dropdown)
                                                Card(
                                                    onClick = { showSubfolderDropdown = true },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Folder,
                                                            contentDescription = "Move to Subfolder",
                                                            tint = MaterialTheme.colorScheme.onSecondary,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                              // Subfolder dropdown menu (context-aware)
                                              Box {
                                                  DropdownMenu(
                                                      expanded = showSubfolderDropdown,
                                                      onDismissRequest = { showSubfolderDropdown = false }
                                                  ) {
                                                      when (currentPlaylistTab) {
                                                          PlaylistTab.TODO, PlaylistTab.REJECTED -> {
                                                              // Available subfolders with file counts
                                                              DropdownMenuItem(
                                                                  text = { 
                                                                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                          Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50))
                                                                          Text("Add new subfolder")
                                                                      }
                                                                  },
                                                                  onClick = { 
                                                                      showSubfolderDropdown = false
                                                                      viewModel.showSubfolderDialog()
                                                                  }
                                                              )
                                                              availableSubfolders.forEach { subfolder ->
                                                                  val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                  DropdownMenuItem(
                                                                      text = { Text("$subfolder ($fileCount files)") },
                                                                      onClick = { 
                                                                          showSubfolderDropdown = false
                                                                          viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                      }
                                                                  )
                                                              }
                                                              // Recent subfolders (from history)
                                                              subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                  DropdownMenuItem(
                                                                      text = { Text("$subfolder (0 files)") },
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
                                                                              color = Color(0xFFFF5722)
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
                                                  }
                                              }
                                            }
                                        } else {
                                            Column(horizontalAlignment = Alignment.End) {
                                                // Top row: Dislike / Undo / Like
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Dislike
                                                    Card(
                                                        onClick = {
                                                            if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                                viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                            } else {
                                                                viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                            }
                                                        },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = NoButton
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.ThumbDown, contentDescription = "Dislike", tint = Color.White, modifier = Modifier.size(19.dp))
                                                        } }
                                                    // Undo
                                                    Card(
                                                        onClick = { viewModel.undoLastAction() },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.secondary
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(19.dp))
                                                        } }
                                                    // Like
                                                    Card(
                                                        onClick = {
                                                            if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                                viewModel.sortSelectedFiles(SortAction.LIKE)
                                                            } else {
                                                                viewModel.sortCurrentFile(SortAction.LIKE)
                                                            }
                                                        },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = YesButton
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Favorite, contentDescription = "Like", tint = Color.White, modifier = Modifier.size(19.dp))
                                                        } }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                // Bottom row: Share / Spectrogram / Move
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Share to WhatsApp
                                                    Card(
                                                        onClick = { viewModel.shareToWhatsApp() },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = Color(0xFF25D366)
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Share, contentDescription = "Share to WhatsApp", tint = Color.White, modifier = Modifier.size(19.dp))
                                                        } }
                                                    // Spectrogram
                                                    Card(
                                                        onClick = { showSpectrogram = true },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = Color(0xFFFFB6C1)
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                                Text("Sp", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp), color = Color.White, textAlign = TextAlign.Center)
                                                                Text("eK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp), color = Color.White, textAlign = TextAlign.Center)
                                                            }
                                                        } }
                                                    // Move to subfolder
                                                    Card(
                                                        onClick = { showSubfolderDropdown = true },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.secondary
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Folder, contentDescription = "Move to Subfolder", tint = Color.White, modifier = Modifier.size(19.dp))
                                                        } }
                                                }
                                                // Dropdown menu (same as above)
                                                Box {
                                                    DropdownMenu(
                                                        expanded = showSubfolderDropdown,
                                                        onDismissRequest = { showSubfolderDropdown = false }
                                                    ) {
                                                        when (currentPlaylistTab) {
                                                            PlaylistTab.TODO, PlaylistTab.REJECTED -> {
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50))
                                                                            Text("Add new subfolder")
                                                                        }
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.showSubfolderDialog()
                                                                    }
                                                                )
                                                                availableSubfolders.forEach { subfolder ->
                                                                    val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                    DropdownMenuItem(
                                                                        text = { Text("$subfolder ($fileCount files)") },
                                                                        onClick = { 
                                                                            showSubfolderDropdown = false
                                                                            viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                        }
                                                                    )
                                                                }
                                                                subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                    DropdownMenuItem(
                                                                        text = { Text("$subfolder (0 files)") },
                                                                        onClick = { 
                                                                            showSubfolderDropdown = false
                                                                            viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                            PlaylistTab.LIKED -> {
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50))
                                                                            Text("Add new subfolder")
                                                                        }
                                                                    },
                                                                    onClick = { 
                                                                        showSubfolderDropdown = false
                                                                        viewModel.showSubfolderDialog()
                                                                    }
                                                                )
                                                                val currentSubfolder = viewModel.getCurrentFileSubfolder()
                                                                if (currentSubfolder != null) {
                                                                    DropdownMenuItem(
                                                                        text = { Text("Remove from \"$currentSubfolder\" to Root", color = Color(0xFFFF5722)) },
                                                                        onClick = { 
                                                                            showSubfolderDropdown = false
                                                                            viewModel.moveCurrentFileFromSubfolderToRoot()
                                                                        }
                                                                    )
                                                                }
                                                                availableSubfolders.forEach { subfolder ->
                                                                    val fileCount = subfolderFileCounts[subfolder] ?: 0
                                                                    val isCurrentSubfolder = currentSubfolder == subfolder
                                                                    DropdownMenuItem(
                                                                        text = { Text(if (isCurrentSubfolder) "Currently in \"$subfolder\" ($fileCount files)" else "Move to \"$subfolder\" ($fileCount files)") },
                                                                        onClick = { 
                                                                            showSubfolderDropdown = false
                                                                            if (!isCurrentSubfolder) {
                                                                                viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                            }
                                                                        },
                                                                        enabled = !isCurrentSubfolder
                                                                    )
                                                                }
                                                                subfolderHistory.filter { it !in availableSubfolders }.forEach { subfolder ->
                                                                    DropdownMenuItem(
                                                                        text = { Text("Move to \"$subfolder\" (0 files)") },
                                                                        onClick = { 
                                                                            showSubfolderDropdown = false
                                                                            viewModel.moveCurrentFileToSubfolder(subfolder)
                                                                        }
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
                            }
                            } // end AnimatedVisibility (main player 1)
                        }
                    }

                    // Local composable to render the playlist tabs header (previously inside the list)
                    @Composable
                    fun PlaylistTabsHeader() {
                        if (isPlaylistsVisible) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .offset(y = (-40).dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlaylistTab.entries.forEach { tab ->
                                        val isSelected = currentPlaylistTab == tab
                                        val tabColor = when (tab) {
                                            PlaylistTab.TODO -> Color(0xFF2196F3)
                                            PlaylistTab.LIKED -> Color(0xFF4CAF50)
                                            PlaylistTab.REJECTED -> Color(0xFFFFCDD2)
                                        }
                                        val selectedColor = when (tab) {
                                            PlaylistTab.TODO -> Color(0xFF1976D2)
                                            PlaylistTab.LIKED -> Color(0xFF388E3C)
                                            PlaylistTab.REJECTED -> Color(0xFFEF9A9A)
                                        }
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.switchPlaylistTab(tab) }
                                                .then(
                                                    if (isSelected) {
                                                        Modifier
                                                            .border(2.dp, Color.White, RoundedCornerShape(20.dp))
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
                                            shape = RoundedCornerShape(20.dp)
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
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Favorite,
                                                                        contentDescription = "Liked",
                                                                        modifier = Modifier.size(16.dp),
                                                                        tint = if (isSelected) Color.White else Color.Black
                                                                    )
                                                                    if (!likedFilter.isNullOrEmpty()) {
                                                                        Icon(
                                                                            Icons.Default.Refresh,
                                                                            contentDescription = "Reload all liked (clear filter)",
                                                                            modifier = Modifier
                                                                                .size(16.dp)
                                                                                .clickable { viewModel.clearLikedFilterAndReload() },
                                                                            tint = if (isSelected) Color.White else Color.Black
                                                                        )
                                                                    } else if (currentPlaylistTab == PlaylistTab.LIKED) {
                                                                        Icon(
                                                                            Icons.Default.Folder,
                                                                            contentDescription = "Select subfolders to view in Liked",
                                                                            modifier = Modifier
                                                                                .size(16.dp)
                                                                                .clickable {
                                                                                    viewModeForSubfolderDialog = true
                                                                                    showSubfolderSelectionDialog = true
                                                                                    viewModel.updateSubfolderInfo()
                                                                                },
                                                                            tint = Color(0xFFFFB6C1)
                                                                        )
                                                                    }
                                                                }
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
                                                    Spacer(modifier = Modifier.width(0.dp))
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
                                    if (currentPlaylistTab == PlaylistTab.LIKED) {
                                        IconButton(
                                            onClick = { showShareZipDialog = true },
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
                                        IconButton(
                                            onClick = { 
                                                deleteActionType = currentPlaylistTab
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
                            }
                        }
                    }
                    
                    // Render fixed sections
                    MainPlayerSection()
                    PlaylistTabsHeader()
                    
                    // Only the playlist list scrolls
                    val renderHeadersInList = false
                    Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .offset(y = (-35).dp),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        userScrollEnabled = true,
                        reverseLayout = false
                    ) {
                        // Current song info
                        if (renderHeadersInList) item {
                            val currentFile = currentPlayingFile // Use the actually playing file
            if (currentFile != null) {
                                val file = currentFile
                                // Animation states for swipe feedback - switched to Animatable for buttery-smooth motion
                                val scope = rememberCoroutineScope()
                                val mainSwipeOffset = remember { Animatable(0f) }
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
                                
                                val animatedOffset = mainSwipeOffset.value
                                
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
                                
                                // Progress counts: total, played, liked, rejected (moved above main player container)
                                val played = viewModel.preferences.getListened()
                                val yes = viewModel.preferences.getLiked()
                                val no = viewModel.preferences.getRefused()
                                val totalTracks = when (currentPlaylistTab) {
                                    PlaylistTab.TODO -> musicFiles.size
                                    PlaylistTab.LIKED -> likedFiles.size
                                    PlaylistTab.REJECTED -> rejectedFiles.size
                                }
                                Spacer(Modifier.height(0.dp)) // moved counts/toggles outside scroll area
                                val panelAnimMs2 = (500 / visualSettings.animationSpeed).toInt()
                                val mainPlayerAlpha2 by animateFloatAsState(
                                    targetValue = if (isMainPlayerVisible) 1f else 0f,
                                    animationSpec = tween(panelAnimMs2),
                                    label = "mainPlayerAlpha2"
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = if (isMainPlayerVisible) 4.dp else 0.dp)
                                        .then(if (mainPlayerAlpha2 == 0f) Modifier.height(0.dp) else Modifier)
                                        .animateItem(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow))
                                        .graphicsLayer {
                                            translationX = animatedOffset
                                            scaleX = 1f + abs(animatedOffset) / 2000f
                                            scaleY = 1f + abs(animatedOffset) / 2000f
                                            alpha = if (isMainPlayerVisible) transitionAlpha else mainPlayerAlpha2
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
                                        .then(
                                            run {
                                                val isCurrentInPlaylist = currentPlaylistFiles.any { it.uri == file.uri }
                                                if (isCurrentInPlaylist) Modifier.pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    // no-op
                                                },
                                                onDragEnd = {
                                                    val current = mainSwipeOffset.value
                                                    val threshold = 150f
                                                    val exit = if (current > 0) 520f else -520f
                                                    if (abs(current) > threshold) {
                                                        val prevFile = file
                                                        scope.launch {
                                                            // 1) Animate out fully
                                                            mainSwipeOffset.animateTo(exit, tween(220))
                                                            // 2) Animate back to rest
                                                            mainSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                                            // 3) Then perform actions
                                                            if (current < 0) {
                                                                try {
                                                                    // Prevent mini-player flash
                                                                    suppressMiniOnLeftSwipe = true
                                                                    // Remove current from list FIRST so index shifts, then advance and sort
                                                                    try { prevFile?.let { viewModel.removeFromCurrentListByUri(it.uri) } } catch (_: Exception) {}
                                                                    viewModel.playNextAfterRemoval()
                                                                    prevFile?.let { viewModel.sortMusicFile(it, SortAction.DISLIKE) }
                                                                } catch (e: Exception) {
                                                                    CrashLogger.log("Debug", "Error in swipe (main) sort/next: ${e.message}")
                                                                }
                                                            } else if (current > 0) {
                                                                try { prevFile?.let { viewModel.sortMusicFile(it, SortAction.LIKE) } } catch (e: Exception) {
                                                                    CrashLogger.log("Debug", "Error in swipe (main) like: ${e.message}")
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            mainSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                                        }
                                                    }
                                                    swipeDirection = 0
                                                }
                                            ) { _, dragAmount ->
                                                val (x, _) = dragAmount
                                                val newValue = (mainSwipeOffset.value + x * 0.85f).coerceIn(-300f, 300f)
                                                scope.launch { mainSwipeOffset.snapTo(newValue) }
                                                swipeDirection = when {
                                                    newValue > 80f -> 1
                                                    newValue < -80f -> -1
                                                    else -> 0
                                                }
                                            }
                                                } else Modifier
                                            }
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
) {
                                    Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
                                        
                                        // Song info removed - now displayed in waveform container
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val durationText = formatTime(duration)
                                        val bitrateText = "${calculateBitrate(file.size, duration)} kbps"
                                        val sizeText = formatFileSize(file.size)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "| $durationText | $bitrateText | $sizeText |",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            OutlinedButton(
                                                onClick = { isWaveformVisible = !isWaveformVisible },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(22.dp)
                                            ) {
                                                Text(
                                                    text = if (isWaveformVisible) "Hide Wave" else "Show Wave",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
                                        // Shared Waveform with toggle (replaces progress bar) - only if visible and controls visible
                                        if (isWaveformVisible && isMainPlayerVisible) {
                                        val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                            WaveformWithToggle(
                                            sharedState = sharedWaveformState,
                                            progress = progressPercent,
                                            onSeek = { seekProgress ->
                                                val seekPosition = (seekProgress * duration).toLong()
                                                    CrashLogger.log("Debug", "🎯 Seek calculation: progress=$seekProgress, duration=$duration, seekPosition=$seekPosition")
                                                viewModel.seekTo(seekPosition)
                                            },
                                                songUri = currentFile?.uri.toString(),
                                                waveformHeight = visualSettings.waveformHeight.toInt(),
                                                currentPosition = currentPosition,
                                                totalDuration = duration,
                                                fileName = file.name, // Pass filename to display in waveform
                                                customWaveformColor = customWaveformColor,
                                                animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = (300 / visualSettings.animationSpeed).toInt(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (isMainPlayerVisible) Row(
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
                                            // Group 1: Playback controls
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
                                            
                                            // Group 2: Like/Undo/Dislike controls
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Dislike
                                                Card(
                                                    onClick = {
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.DISLIKE)
                                                        }
                                                    },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = NoButton
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ThumbDown,
                                                            contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Reject All Selected" else "Dislike",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }

                                                // Undo
                                                Card(
                                                    onClick = { viewModel.undoLastAction() },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.Undo,
                                                            contentDescription = "Undo",
                                                            tint = MaterialTheme.colorScheme.onSecondary,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }

                                                // Like
                                                Card(
                                                    onClick = {
                                                        if (isMultiSelectionMode && selectedIndices.isNotEmpty()) {
                                                            viewModel.sortSelectedFiles(SortAction.LIKE)
                                                        } else {
                                                            viewModel.sortCurrentFile(SortAction.LIKE)
                                                        }
                                                    },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = YesButton
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Favorite,
                                                            contentDescription = if (isMultiSelectionMode && selectedIndices.isNotEmpty()) "Like All Selected" else "Like",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Spacer between groups
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            // Group 3: Share/Spectrogram/Move controls
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                            // Share to WhatsApp Button
                                                Card(
                                                    onClick = { viewModel.shareToWhatsApp() },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color(0xFF25D366)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Share,
                                                            contentDescription = "Share to WhatsApp",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                            }
                                            
                                                // Spectrogram Button with Text
                                                Card(
                                                onClick = { 
                                                    showSpectrogram = true
                                                }, 
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color(0xFFFFB6C1)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = "Sp",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 8.sp
                                                            ),
                                                                color = Color.White,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Text(
                                                            text = "eK",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 8.sp
                                                            ),
                                                                color = Color.White,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Subfolder dropdown button (available in all playlists)
                                                Card(
                                                    onClick = { showSubfolderDropdown = true },
                                                    modifier = Modifier.size(38.dp),
                                                    shape = CircleShape,
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color(0xFF4CAF50)
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = "Move to Subfolder",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                    )
                                                    }
                                                }
                                                }
                                            
                                            // Subfolder dropdown menu
                                            Box {
                                                
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
                        
                        // Removed Like/Undo/Dislike compact row (moved inline with playlist waveform)
                        
                        // Tabbed Playlist Header (hideable)
                        if (renderHeadersInList && isPlaylistsVisible) item {
                            Column {
                                // Custom Pill-Shaped Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Favorite,
                                                                        contentDescription = "Liked",
                                                                        modifier = Modifier.size(16.dp),
                                                                        tint = if (isSelected) Color.White else Color.Black
                                                                    )
                                                                    if (!likedFilter.isNullOrEmpty()) {
                                                                        Icon(
                                                                            Icons.Default.Refresh,
                                                                            contentDescription = "Reload all liked (clear filter)",
                                                                            modifier = Modifier
                                                                                .size(16.dp)
                                                                                .clickable { viewModel.clearLikedFilterAndReload() },
                                                                            tint = if (isSelected) Color.White else Color.Black
                                                                        )
                                                                    } else if (currentPlaylistTab == PlaylistTab.LIKED) {
                                                                        Icon(
                                                                            Icons.Default.Folder,
                                                                            contentDescription = "Select subfolders to view in Liked",
                                                                            modifier = Modifier
                                                                                .size(16.dp)
                                                                                .clickable {
                                                                                    viewModeForSubfolderDialog = true
                                                                                    showSubfolderSelectionDialog = true
                                                                                    // Ensure latest subfolder info
                                                                                    viewModel.updateSubfolderInfo()
                                                                                },
                                                                            tint = Color(0xFFFFB6C1)
                                                                        )
                                                                    }
                                                                }
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
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        
                        // Group files by subfolder for TODO and LIKED playlists
                        val groupedItems: List<PlaylistItem> = if (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) {
                            // Group by subfolder, handle null subfolders
                            val grouped = currentPlaylistFiles.groupBy { it.subfolder }
                            // Create flat list with headers: [Header, File, File, Header, File, ...]
                            buildList {
                                // Files without subfolder first
                                grouped[null]?.let { files ->
                                    addAll(files.map { PlaylistItem.FileItem(it) })
                                }
                                // Then files grouped by subfolder
                                grouped.entries
                                    .filter { it.key != null }
                                    .sortedBy { it.key }
                                    .forEach { (subfolder, files) ->
                                        add(PlaylistItem.HeaderItem(subfolder!!))
                                        addAll(files.map { PlaylistItem.FileItem(it) })
                                    }
                            }
                        } else {
                            // For other playlists, just wrap files
                            currentPlaylistFiles.map { PlaylistItem.FileItem(it) }
                        }
                        
                        // Playlist Items (dynamic sizing) - Performance optimized with grouping
                        itemsIndexed(
                            items = groupedItems, 
                            key = { idx, item -> 
                                when (item) {
                                    is PlaylistItem.HeaderItem -> "header_${item.subfolder}"
                                    is PlaylistItem.FileItem -> "${item.file.uri}_${item.file.subfolder ?: ""}"
                                }
                            }
                        ) { index, playlistItem ->
                            when (playlistItem) {
                                is PlaylistItem.HeaderItem -> {
                                    // Subfolder header with divider
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            thickness = 1.dp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        Text(
                                            text = playlistItem.subfolder,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                is PlaylistItem.FileItem -> {
                                    val item = playlistItem.file
                            // Cache expensive calculations
                            val isCurrent = remember(currentPlayingFile?.uri, item.uri) { 
                                currentPlayingFile?.uri == item.uri 
                            }
                            val isCompactScreen = remember(screenWidth, screenHeight) { 
                                screenWidth < 600.dp || screenHeight < 800.dp 
                            }
                            
                            // Calculate adaptive height based on actual text measurement (only for inactive rows)
                            val adaptiveHeight = remember(item.name, item.subfolder, isCurrent, currentPlaylistTab, visualSettings.rowWaveformHeight, isWaveformVisible, isMainPlayerVisible) {
                                if (isCurrent) {
                                    // If main player waveform is visible, minimize active row height and hide its waveform
                                    if (isWaveformVisible && isMainPlayerVisible) {
                                        40.dp // minimal compact height when main waveform shown
                                    } else {
                                        // Active row height based on waveform height from visual settings + padding
                                        (visualSettings.rowWaveformHeight + 21f).dp
                                    }
                                } else {
                                    // Use a more accurate estimation based on typical screen width and font size
                                    val screenWidthDp = screenWidth.value
                                    val fontSize = 14.sp // MaterialTheme.typography.bodyMedium.fontSize * 0.85f
                                    val charsPerLine = (screenWidthDp * 0.6f / (fontSize.value * 0.6f)).toInt() // Account for 60% width and character width
                                    
                                    val estimatedLines = ceil(item.name.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
                                    
                                    // Calculate height based on actual lines needed (reduced by 20% for inactive rows)
                                    val lineHeight = 16.dp // Slightly larger for better readability
                                    val padding = 12.dp
                                    val calculatedHeight = ((estimatedLines * lineHeight.value) + padding.value) * 0.8f // 20% reduction
                                    
                                    calculatedHeight.dp.coerceAtLeast(40.dp) // Minimum height to ensure visibility
                                }
                            }
                            
                            // Animate row height on promotion (inactive → active)
                            val animatedRowHeight by animateDpAsState(
                                targetValue = adaptiveHeight,
                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                label = "rowHeight"
                            )
                            
                            // Calculate adaptive button size based on row height (only for inactive rows)
                            val adaptiveButtonSize = remember(adaptiveHeight, isCurrent, visualSettings.rowWaveformHeight) {
                                if (isCurrent) {
                                    // Button size proportional to waveform height (roughly 50% of waveform height)
                                    (visualSettings.rowWaveformHeight * 0.5f).dp.coerceAtLeast(36.dp).coerceAtMost(48.dp)
                                } else {
                                    when {
                                        adaptiveHeight.value <= 20 -> 18.dp // 1 line rows (adjusted for 20% reduction)
                                        adaptiveHeight.value <= 32 -> 22.dp // 2 line rows (adjusted for 20% reduction)
                                        adaptiveHeight.value <= 44 -> 26.dp // 3 line rows (adjusted for 20% reduction)
                                        else -> 30.dp // 4 line rows (adjusted for 20% reduction)
                                    }
                                }
                            }
                            
                            // Calculate adaptive icon size based on button size (only for inactive rows)
                            val adaptiveIconSize = remember(adaptiveButtonSize, isCurrent, visualSettings.rowWaveformHeight) {
                                if (isCurrent) {
                                    // Icon size proportional to button size (roughly 55% of button size)
                                    (adaptiveButtonSize.value * 0.55f).dp.coerceAtLeast(20.dp).coerceAtMost(28.dp)
                                } else {
                                    (adaptiveButtonSize.value * 0.55f).dp
                                }
                            }
                            // Consolidated swipe state - Animatable for buttery-smooth motion
                            val scope = rememberCoroutineScope()
                            val destinationFolder by viewModel.destinationFolder.collectAsState()
                            val rowSwipeOffset = remember(item.uri) { Animatable(0f) }
                            var swipeDirection by remember(item.uri) { mutableStateOf(0) } // -1 left, 0 none, 1 right
                            var isSwipeActive by remember(item.uri) { mutableStateOf(false) }
                            val removingUris by viewModel.removingUris.collectAsState()
                            val isRemoving = removingUris.contains(item.uri)
                            var isRowDismissed by remember(item.uri) { mutableStateOf(false) }
                            val rowAlpha by animateFloatAsState(
                                targetValue = if (isRemoving || isRowDismissed) 0f else 1f,
                                animationSpec = tween(300),
                                label = "rowAlpha"
                            )
                            // Remove down-to-up/alpha enter; we'll animate only height on promotion
                            // Optimized thresholds for reliable swiping
                            val swipeIndicatorThreshold = 50f  // Show indicator threshold
                            val swipeTriggerThreshold = 100f  // Medium swipes trigger action
                            val swipeMaxOffset = 200f         // Maximum drag distance
                            val swipeResistance = 0.7f        // More resistance for better control
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Apply placement animation at the ITEM ROOT so all children (overlay + card) glide together
                                    .animateItem(tween(durationMillis = 1500))
                            ) {
                                // Swipe indicators behind the card (match row height for true vertical centering)
                                if (rowSwipeOffset.value != 0f && !isMultiSelectionMode && isSwipeActive) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(animatedRowHeight)
                                            .padding(horizontal = if (isCompactScreen) 6.dp else 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (rowSwipeOffset.value > 0) {
                                            // Right swipe - Like
                                            Icon(
                                                Icons.Default.Favorite,
                                                contentDescription = "Like",
                                                tint = YesButton,
                                                modifier = Modifier.size(if (isCompactScreen) 24.dp else 32.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(if (isCompactScreen) 24.dp else 32.dp))
                                        }
                                        
                                        if (rowSwipeOffset.value < 0) {
                                            // Left swipe - Dislike
                                            Icon(
                                                Icons.Default.ThumbDown,
                                                contentDescription = "Dislike",
                                                tint = NoButton,
                                                modifier = Modifier.size(if (isCompactScreen) 24.dp else 32.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(if (isCompactScreen) 24.dp else 32.dp))
                                        }
                                    }
                                }
                                
                                // Consolidated swipe gesture implementation
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (isCompactScreen) 6.dp else 10.dp, 
                                        vertical = 0.dp
                                    )
                                        .combinedClickable(
                                            onClick = {
                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                if (actualIndex >= 0) {
                                                    if (isCurrent) {
                                                        if (isMultiSelectionMode) viewModel.toggleSelection(actualIndex)
                                                    } else {
                                                        if (!isMultiSelectionMode) viewModel.jumpTo(actualIndex) else viewModel.toggleSelection(actualIndex)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                pendingIndexForMulti = if (actualIndex >= 0) actualIndex else null
                                                manualRenameText = (currentPlayingFile?.name ?: item.name).substringBeforeLast('.', (currentPlayingFile?.name ?: item.name))
                                                showRenameActionDialog = true
                                            }
                                        )
                                        .graphicsLayer {
                                            translationX = rowSwipeOffset.value
                                            translationY = 0f
                                            alpha = rowAlpha
                                        }
                                        .pointerInput(isCurrent) {
                                            if (!isCurrent) return@pointerInput // Only allow swipe on the currently playing row
                                            detectHorizontalDragGestures(
                                                onDragStart = { _ ->
                                                    isSwipeActive = true
                        
                                                },
                                                onDragEnd = {
                                                    if (!isMultiSelectionMode && isSwipeActive) {
                                                        val current = rowSwipeOffset.value
                                                        if (abs(current) > swipeTriggerThreshold) {
                                                            try {
                                                            // Debug logging
                                                                CrashLogger.log("MusicPlayerScreen", "🔍 Swipe gesture: index=$index, file='${item.name}', swipeOffset=$current, action=${if (current > 0) "LIKE" else "DISLIKE"}")
                                                            
                                                                // Capture the file reference at the time of swipe to avoid race conditions
                                                                val fileToSort = item
                                                                val isActiveNow = currentPlayingFile?.uri == item.uri
                                
                                                                if (current < 0) {
                                                                    // Left swipe (DISLIKE)
                                                                    if (destinationFolder == null) {
                                                                        // Bounce back and show error via sort attempt; DO NOT remove or advance
                                                                        scope.launch {
                                                                            try { viewModel.sortMusicFile(fileToSort, SortAction.DISLIKE) } catch (_: Exception) {}
                                                                            rowSwipeOffset.animateTo(
                                                                                0f,
                                                                                spring(
                                                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                                    stiffness = Spring.StiffnessVeryLow
                                                                                )
                                                                            )
                                                                        }
                                                                    } else {
                                                                        // Proceed: swipe-out → remove → reflow → next (if active) → sort
                                                                        val exit = -520f
                                                                        scope.launch {
                                                                            isRowDismissed = true
                                                                            if (isActiveNow) suppressMiniOnLeftSwipe = true
                                                                            launch { rowSwipeOffset.animateTo(exit, tween(180)) }
                                                                            delay(180)
                                                                            try { viewModel.removeFromCurrentListByUri(item.uri) } catch (_: Exception) {}
                                                                    // Hand off the rest to ViewModel so coroutine isn't cancelled when this row is removed
                                                                    viewModel.handleRowSwipeAfterRemoval(fileToSort, SortAction.DISLIKE, isActiveNow)
                                                                        }
                                                                    }
                                                                } else {
                                                                    // Right swipe (LIKE) - no destination guard needed
                                                                    val exit = 520f
                                                                    scope.launch {
                                                                        isRowDismissed = true
                                                                        launch { rowSwipeOffset.animateTo(exit, tween(180)) }
                                                                        delay(180)
                                                                        try { viewModel.removeFromCurrentListByUri(item.uri) } catch (_: Exception) {}
                                                                        // Hand off to ViewModel (stable scope) after removal
                                                                        viewModel.handleRowSwipeAfterRemoval(fileToSort, SortAction.LIKE, false)
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                CrashLogger.log("MusicPlayerScreen", "❌ Swipe gesture error: ${e.message}")
                                                            }
                                                        } else {
                                                            // Incomplete swipe → bounce back to original position
                                                            try {
                                                                scope.launch {
                                                                    rowSwipeOffset.animateTo(
                                                                        0f,
                                                                        spring(
                                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                            stiffness = Spring.StiffnessVeryLow
                                                                        )
                                                                    )
                                                                }
                                                            } catch (e: Exception) {
                                                                CrashLogger.log("MusicPlayerScreen", "❌ Bounce-back error: ${e.message}")
                                                            }
                                                        }
                                                        swipeDirection = 0
                                                        isSwipeActive = false
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                if (!isMultiSelectionMode) {
                                                    val newValue = (rowSwipeOffset.value + dragAmount * swipeResistance).coerceIn(-swipeMaxOffset, swipeMaxOffset)
                                                    scope.launch { rowSwipeOffset.snapTo(newValue) }
                                                    
                                                    val newDirection = when {
                                                        newValue > swipeIndicatorThreshold -> 1
                                                        newValue < -swipeIndicatorThreshold -> -1
                                                        else -> 0
                                                    }
                                                    
                                                    // Haptic feedback when crossing threshold
                                                    if (swipeDirection != newDirection && newDirection != 0) {
                            
                                                    }
                                                    
                                                    swipeDirection = newDirection
                                            }
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = if (isCompactScreen) 8.dp else 10.dp, 
                                            vertical = if (isCompactScreen) 6.dp else 8.dp
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // (moved checkbox inline with filename to avoid overlay)
                                    Column(modifier = Modifier.weight(1f)) {
                                        // Filename above waveform removed; shown inside waveform
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 2.dp else 4.dp)) {
                                            // Normal mode: individual file actions (moved inline with waveform)
                                    }
                                }
                                
                                // Controls row for all items - centered in playlist row
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                        .height(animatedRowHeight), // Animated adaptive height on promotion
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                        // Left side buttons: Dislike and Play/Pause (only for active rows)
                                        if (isCurrent) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Dislike button
                                                if (!isMultiSelectionMode && (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED)) {
                                                    IconButton(
                                                        onClick = {
                                                            try {
                                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                                if (actualIndex >= 0) {
                                                                    if (isCurrent) {
                                                                        // Mirror active-row swipe-left behavior: play next, remove row, then sort in background
                                                                        scope.launch {
                                                                            // Prevent miniplayer flash while transitioning
                                                                            suppressMiniOnLeftSwipe = true
                                                                            try { viewModel.playNextAfterRemoval() } catch (e: Exception) { CrashLogger.log("MusicPlayerScreen", "playNextAfterRemoval() error: ${e.message}") }
                                                                            // Give a brief moment for transition, then remove and sort
                                                                            delay(80)
                                                                            try { viewModel.removeFromCurrentListByUri(item.uri) } catch (_: Exception) {}
                                                                            try { viewModel.sortMusicFile(item, SortAction.DISLIKE) } catch (e: Exception) { CrashLogger.log("MusicPlayerScreen", "sortMusicFile(DISLIKE) error: ${e.message}") }
                                                                        }
                                                                    } else {
                                                                        viewModel.sortAtIndex(actualIndex, SortAction.DISLIKE)
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                CrashLogger.log("Debug", "Error in dislike button: ${e.message}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ThumbDown, 
                                                            contentDescription = "Dislike", 
                                                            tint = NoButton,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                
                                                // Play/Pause button (hidden in Multi-Select mode; also hide whenever main player is visible)
                                                if (!isMultiSelectionMode && !isMainPlayerVisible) IconButton(
                                                    onClick = { 
                                                        try {
                                                            viewModel.playPause()
                                                        } catch (e: Exception) {
                                                            CrashLogger.log("Debug", "Error in play/pause button: ${e.message}")
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Dislike button for inactive rows (original behavior)
                                            if ((currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) && !isMultiSelectionMode) {
                                                IconButton(
                                                    onClick = { 
                                                        try {
                                                            val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                            if (actualIndex >= 0) {
                                                                viewModel.sortAtIndex(actualIndex, SortAction.DISLIKE)
                                                            }
                                                        } catch (e: Exception) {
                                                            CrashLogger.log("Debug", "Error in dislike button: ${e.message}")
                                                        }
                                                    },
                                                    modifier = Modifier.size(adaptiveButtonSize) // Adaptive button size
                                                ) {
                                                    Icon(
                                                        Icons.Default.ThumbDown, 
                                                        contentDescription = "Dislike", 
                                                        tint = NoButton,
                                                        modifier = Modifier.size(adaptiveIconSize) // Adaptive icon size
                                                    )
                                                }
                                                Spacer(Modifier.width(10.dp))
                                            }
                                        }
                                        
                                        // Center content: show filename when main waveform visible; otherwise show row waveform for active row
                                        if (isCurrent) {
                                            if (isWaveformVisible && isMainPlayerVisible) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(animatedRowHeight)
                                                        .combinedClickable(
                                                            onClick = {
                                                                // Tap filename to play
                                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                                if (actualIndex >= 0) viewModel.jumpTo(actualIndex)
                                                            },
                                                            onLongClick = {
                                                                // Long-press filename to rename/multi-select
                                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                                pendingIndexForMulti = if (actualIndex >= 0) actualIndex else null
                                                                manualRenameText = (currentPlayingFile?.name ?: item.name).substringBeforeLast('.', (currentPlayingFile?.name ?: item.name))
                                                                showRenameActionDialog = true
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        if (isMultiSelectionMode && actualIndex >= 0) {
                                                            Checkbox(
                                                                checked = selectedIndices.contains(actualIndex),
                                                                onCheckedChange = { viewModel.toggleSelection(actualIndex) },
                                                                modifier = Modifier.padding(end = 6.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = item.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.85f,
                                                                fontWeight = FontWeight.Medium
                                                            ),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Waveform with track info overlay at top
                                                val progressPercent = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                                val trackDurationText = if (duration > 0) { val m = duration / 60000; val s = (duration % 60000) / 1000; "${m}:${"%02d".format(s)}" } else ""
                                                val trackSizeText = if (item.size > 0) { "%.1f MB".format(item.size / (1024.0 * 1024.0)) } else ""
                                                val trackBitrateText = if (item.bitrate > 0) "${item.bitrate}kbps" else ""
                                                val trackInfoParts = listOfNotNull(trackDurationText.ifEmpty { null }, trackBitrateText.ifEmpty { null }, trackSizeText.ifEmpty { null })
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Track info above waveform
                                                    if (trackInfoParts.isNotEmpty()) {
                                                        Text(
                                                            text = trackInfoParts.joinToString(" | "),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp, lineHeight = 7.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 1,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                    WaveformWithToggle(
                                                        sharedState = sharedWaveformState,
                                                        progress = progressPercent,
                                                        onSeek = { seekProgress ->
                                                            val seekPosition = (seekProgress * duration).toLong()
                                                            viewModel.seekTo(seekPosition)
                                                        },
                                                        songUri = item.uri.toString(),
                                                        waveformHeight = visualSettings.rowWaveformHeight.toInt(),
                                                        currentPosition = currentPosition,
                                                        totalDuration = duration,
                                                        fileName = item.name,
                                                        opacity = 0.7f,
                                                        customWaveformColor = customWaveformColor,
                                                        animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = (300 / visualSettings.animationSpeed).toInt(),
                                                        modifier = Modifier.fillMaxWidth().height(visualSettings.rowWaveformHeight.dp)
                                                    )
                                                }
                                            }
                                            
                                            // 2x2 control pack to the right (Like, Move, Spectrogram, Share)
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    // Like (top-left)
                                            IconButton(
                                                onClick = {
                                                            try {
                                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                                if (actualIndex >= 0) {
                                                                    viewModel.sortAtIndex(actualIndex, SortAction.LIKE)
                                                                }
                                                            } catch (e: Exception) { CrashLogger.log("Debug", "Error in like button: ${e.message}") }
                                                        },
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Favorite,
                                                            contentDescription = "Like",
                                                            tint = YesButton,
                                                            modifier = Modifier.size(adaptiveIconSize)
                                                        )
                                                    }
                                                    // Move to (top-right)
                                                    IconButton(
                                                        onClick = {
                                                            // Open move dialog (no viewing options)
                                                            viewModeForSubfolderDialog = false
                                                            showSubfolderSelectionDialog = true
                                                        },
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = "Move to Subfolder",
                                                            tint = Color(0xFF4CAF50),
                                                            modifier = Modifier.size(adaptiveIconSize)
                                                        )
                                                    }
                                                }
                                                // Elapsed / Total time + Share (bottom row)
                                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                                    // Elapsed/Total time column
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                                    ) {
                                                        val elapsedMins = currentPosition / 60000
                                                        val elapsedSecs = (currentPosition % 60000) / 1000
                                                        val totalMins = duration / 60000
                                                        val totalSecs = (duration % 60000) / 1000
                                                        Text(
                                                            text = "${elapsedMins}:${"%02d".format(elapsedSecs)}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = adaptiveSpekFontSize, lineHeight = adaptiveSpekFontSize * 1.1f),
                                                            color = Color.Red,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Text(
                                                            text = "${totalMins}:${"%02d".format(totalSecs)}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = adaptiveSpekFontSize, lineHeight = adaptiveSpekFontSize * 1.1f),
                                                            color = Color.Red,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                    // Share
                                                    IconButton(
                                                        onClick = {
                                                            try {
                                                                viewModel.shareToWhatsApp()
                                                            } catch (e: Exception) {
                                                                CrashLogger.log("Debug", "Error in share button: ${e.message}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                                    ) {
                                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF2196F3), modifier = Modifier.size(adaptiveIconSize))
                                                    }
                                                }
                                            }
                                        } else if (!isCurrent) {
                                            // Filename for non-current items (centered vertically)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(animatedRowHeight) // Match animated row height
                                                    .combinedClickable(
                                                        onClick = {
                                                            val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                            if (actualIndex >= 0 && !isMultiSelectionMode) {
                                                                viewModel.jumpTo(actualIndex)
                                                            } else if (actualIndex >= 0 && isMultiSelectionMode) {
                                                                viewModel.toggleSelection(actualIndex)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                            pendingIndexForMulti = if (actualIndex >= 0) actualIndex else null
                                                            manualRenameText = (currentPlayingFile?.name ?: item.name).substringBeforeLast('.', (currentPlayingFile?.name ?: item.name))
                                                            showRenameActionDialog = true
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Start,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                    if (isMultiSelectionMode && actualIndex >= 0) {
                                                        Checkbox(
                                                            checked = selectedIndices.contains(actualIndex),
                                                            onCheckedChange = { viewModel.toggleSelection(actualIndex) },
                                                            modifier = Modifier.padding(end = 6.dp)
                                                        )
                                                    }
                                                    // Show red checkmark for played-but-not-actioned files (but not the currently playing one)
                                                    if (item.uri in playedButNotActioned && item.uri != currentPlayingFile?.uri) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "Played but not actioned",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = Color.Red
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                    }
                                                    
                                                    // Just show filename (subfolder is now in header)
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.85f
                                                        ),
                                                        maxLines = 4,
                                                        overflow = TextOverflow.Visible,
                                                        textAlign = TextAlign.Start,
                                                        modifier = Modifier.weight(1f, fill = true)
                                                    )
                                                }
                                            }
                                                }
                                                
                                        // Right side Like button for inactive rows
                                        if (!isCurrent && (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) && !isMultiSelectionMode) {
                                                    IconButton(
                                                        onClick = {
                                                            try {
                                                                val actualIndex = currentPlaylistFiles.indexOfFirst { it.uri == item.uri }
                                                                if (actualIndex >= 0) {
                                                                    viewModel.sortAtIndex(actualIndex, SortAction.LIKE)
                                                                }
                                                            } catch (e: Exception) {
                                                        CrashLogger.log("Debug", "Error in like button (inactive row): ${e.message}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Favorite,
                                                            contentDescription = "Like",
                                                            tint = YesButton,
                                                            modifier = Modifier.size(adaptiveIconSize)
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
                        }
                    }
                    // Bottom shadow removed
                    } // end Box wrapping LazyColumn

                    // Enhanced Sticky minimized player when scrolled - positioned correctly in BoxScope
                    // Smart visibility: show when current playing track is not in current playlist OR not visible in viewport
                    val currentTrackIndex = currentPlaylistFiles.indexOfFirst { it.uri == currentPlayingFile?.uri }
                    val isFileInCurrentPlaylist = currentTrackIndex != -1
                    // First-open behavior: hide miniplayer while the very first track is in sight
                    var isFirstOpen by remember { mutableStateOf(true) }
                    var initialAutoScrolled by remember { mutableStateOf(false) }
                    val isCurrentTrackFullyVisible = remember(listState.layoutInfo, currentTrackIndex) {
                        val info = listState.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == currentTrackIndex }
                        if (item == null) {
                            false
                        } else {
                            val viewportStart = info.viewportStartOffset
                            val viewportEnd = info.viewportEndOffset
                            val top = item.offset
                            val bottom = item.offset + item.size
                            // Fully visible if entire item bounds within viewport
                            top >= viewportStart && bottom <= viewportEnd
                        }
                    }
                    // Row is visible in ANY amount if it's present in visibleItemsInfo
                    val isCurrentTrackAnyVisible = remember(listState.layoutInfo, currentTrackIndex) {
                        val info = listState.layoutInfo
                        info.visibleItemsInfo.any { it.index == currentTrackIndex }
                    }
                    
                    // Mark first-open completed once user scrolls or advances beyond the first row
                    LaunchedEffect(isScrolled, currentTrackIndex) {
                        if (isScrolled || currentTrackIndex > 0) isFirstOpen = false
                    }
                    // First-time auto-scroll to reveal main player
                    LaunchedEffect(currentTrackIndex, isFirstOpen, currentPlayingFile?.uri) {
                        if (isFirstOpen && !initialAutoScrolled && isFileInCurrentPlaylist && currentTrackIndex == 0 && currentPlayingFile != null) {
                            try {
                                // Wait until LazyColumn has laid out at least one item to avoid no-op scrolls
                                var attempts = 0
                                while (listState.layoutInfo.visibleItemsInfo.isEmpty() && attempts < 20) {
                                    delay(25)
                                    attempts++
                                }
                                // Smoothly scroll ~500px from top of first item to reveal main player area
                                listState.animateScrollToItem(0, 500)
                            } catch (e: Exception) {
                                CrashLogger.log("MusicPlayerScreen", "Auto-scroll failed: ${e.message}")
                            } finally {
                                initialAutoScrolled = true
                            }
                        }
                    }
                    
                    // PHASE 4: Show miniplayer when file not in playlist OR when scrolled and not visible
                    var isMiniPlayerHidden by remember { mutableStateOf(false) }
                    LaunchedEffect(currentPlayingFile?.uri) { 
                        isMiniPlayerHidden = false
                        // If we just suppressed due to a dislike/swipe, keep it suppressed briefly to avoid flash
                        if (suppressMiniOnLeftSwipe) {
                            delay(500)
                            suppressMiniOnLeftSwipe = false
                        } else {
                            suppressMiniOnLeftSwipe = false
                        }
                        if (currentPlayingFile?.uri == promotingNextUri) promotingNextUri = null
                    }
                    val hideMiniBecauseFirstTrackInSight = isFirstOpen && isFileInCurrentPlaylist && currentTrackIndex == 0 && isCurrentTrackFullyVisible && isMainPlayerVisible
                    // Determine if current playing file is liked
                    val isCurrentLiked = remember(currentPlayingFile?.uri, likedFiles) {
                        currentPlayingFile?.let { f -> likedFiles.any { it.uri == f.uri } } ?: false
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = visualSettings.enableMiniPlayer &&
                                currentPlayingFile != null &&
                                (
                                    // If not part of current playlist, always allow mini visibility
                                    !isFileInCurrentPlaylist ||
                                    // Show only when the active row (waveform region) is no longer visible at all
                                    !isCurrentTrackAnyVisible
                                ) &&
                                // Do not show mini-player when main player AND its waveform are visible and the current track is liked
                                !(isMainPlayerVisible && isWaveformVisible && isCurrentLiked) &&
                        // Also do not show mini-player when main player AND its waveform are visible and the current playing row is not visible
                                !(isMainPlayerVisible && isWaveformVisible && !isCurrentTrackAnyVisible) &&
                                // Guard: don't show mini on very first open if first item is fully visible
                                !(isFileInCurrentPlaylist && currentTrackIndex == 0 && isMainPlayerVisible) &&
                                !isMiniPlayerHidden &&
                                !suppressMiniOnLeftSwipe &&
                                !hideMiniBecauseFirstTrackInSight,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .wrapContentHeight()
                            .zIndex(1f),
                        label = "Miniplayer Animated Visibility"
                    ) {
                        // PHASE 4: Use currentPlayingFile to show actual playing file, not next file
                        currentPlayingFile?.let { file ->
                            // Animation states for miniplayer swipe feedback - Animatable
                            val scope = rememberCoroutineScope()
                            val miniSwipeOffset = remember { Animatable(0f) }
                            var miniSwipeDirection by remember { mutableStateOf(0) } // -1 left, 0 none, 1 right
                            val miniAnimatedOffset = miniSwipeOffset.value
                            
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
                                    .heightIn(max = 140.dp)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
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
                                    .then(
                                        if (isFileInCurrentPlaylist) Modifier.pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                // no-op
                                            },
                                            onDragEnd = {
                                                val current = miniSwipeOffset.value
                                                val threshold = 100f
                                                val exit = if (current > 0) 420f else -420f
                                                if (abs(current) > threshold) {
                                                    val prevFile = file
                                                    scope.launch {
                                                        // 1) Animate out fully
                                                        miniSwipeOffset.animateTo(exit, tween(220))
                                                        // 2) Animate back
                                                        miniSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                                        // 3) Then sequence next/sort
                                                        if (current < 0) {
                                                            try {
                                                                viewModel.next()
                                                                prevFile.let { viewModel.sortMusicFile(it, SortAction.DISLIKE) }
                                                    } catch (e: Exception) {
                                                                CrashLogger.log("Debug", "Error in miniplayer swipe next/sort: ${e.message}")
                                                            }
                                                        } else if (current > 0) {
                                                            try { prevFile.let { viewModel.sortMusicFile(it, SortAction.LIKE) } } catch (e: Exception) {
                                                                CrashLogger.log("Debug", "Error in miniplayer swipe like: ${e.message}")
                                                    }
                                                }
                                                    }
                                                } else {
                                                    scope.launch { miniSwipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                                                }
                                                miniSwipeDirection = 0
                                            }
                                        ) { _, dragAmount ->
                                            val (x, _) = dragAmount
                                            val newValue = (miniSwipeOffset.value + x).coerceIn(-200f, 200f)
                                            scope.launch { miniSwipeOffset.snapTo(newValue) }
                                            miniSwipeDirection = when {
                                                newValue > 30 -> 1
                                                newValue < -30 -> -1
                                                else -> 0
                                            }
                                        }
                                        } else Modifier
                                    ),
                                colors = CardDefaults.cardColors(containerColor = miniCardColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                                    // Row 1: Close + Song title + Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Close button
                                        IconButton(
                                            onClick = { isMiniPlayerHidden = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Hide", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                        // Song title
                                        Text(
                                            text = file.name.ifEmpty { "Unknown Track" },
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        )
                                        // Playback controls
                                        IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        FloatingActionButton(
                                            onClick = { viewModel.playPause() },
                                            modifier = Modifier.size(28.dp),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                                        ) {
                                            Icon(
                                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    // Row 2: Metadata + Like/Dislike/SpEk
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val miniDurationText = formatTime(duration)
                                        val miniBitrateText = "${calculateBitrate(file.size, duration)} kbps"
                                        val miniSizeText = formatFileSize(file.size)
                                        Text(
                                            text = "$miniDurationText | $miniBitrateText | $miniSizeText",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (isMultiSelectionMode && selectedIndices.isNotEmpty()) viewModel.sortSelectedFiles(SortAction.DISLIKE)
                                                else viewModel.sortCurrentFile(SortAction.DISLIKE)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) { Icon(Icons.Default.ThumbDown, "Dislike", tint = NoButton, modifier = Modifier.size(12.dp)) }
                                        IconButton(
                                            onClick = {
                                                if (isMultiSelectionMode && selectedIndices.isNotEmpty()) viewModel.sortSelectedFiles(SortAction.LIKE)
                                                else viewModel.sortCurrentFile(SortAction.LIKE)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) { Icon(Icons.Default.Favorite, "Like", tint = YesButton, modifier = Modifier.size(12.dp)) }
                                        IconButton(
                                            onClick = { showSpectrogram = !showSpectrogram },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("SpEk", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 7.sp), color = Color(0xFFFFB6C1))
                                        }
                                    }
                                    // Row 3: Compact waveform
                                    WaveformWithToggle(
                                        sharedState = sharedWaveformState,
                                        progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                        onSeek = { seekProgress ->
                                            val seekPosition = (seekProgress * duration).toLong()
                                            viewModel.seekTo(seekPosition)
                                        },
                                        songUri = file.uri.toString(),
                                        waveformHeight = 36,
                                        currentPosition = currentPosition,
                                        totalDuration = duration,
                                        fileName = file.name,
                                        customWaveformColor = customWaveformColor,
                                        animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = (300 / visualSettings.animationSpeed).toInt(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        items(
                            items = subfolderHistory,
                            key = { it },
                            contentType = { "subfolder-history" }
                        ) { subfolder ->
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    if (viewModeForSubfolderDialog) {
                        "Select Liked Subfolders to View"
                    } else {
                        when (currentPlaylistTab) {
                            PlaylistTab.TODO -> "Move to Liked Subfolder"
                            PlaylistTab.REJECTED -> "Move to Liked Subfolder"
                            PlaylistTab.LIKED -> "Move to Subfolder"
                        }
                    }
                )
            },
            text = {
                var selectedViewSubfolders by remember { mutableStateOf(setOf<String>()) }
                val scroll = androidx.compose.foundation.rememberScrollState()
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
                    if (!viewModeForSubfolderDialog) {
                        Text("Select or create a subfolder to move the current playing file")
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Add new subfolder option
                    if (!viewModeForSubfolderDialog) {
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
                    }
                    
                    // Available subfolders grid
                    if (availableSubfolders.isNotEmpty()) {
                        Text(
                            if (viewModeForSubfolderDialog) "Available subfolders to view:" else "Available subfolders:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (!viewModeForSubfolderDialog) {
                            val gridItems = availableSubfolders
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 500.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                userScrollEnabled = true
                            ) {
                                items(
                                    count = gridItems.size,
                                    key = { idx -> gridItems[idx] },
                                    contentType = { "available-subfolder" }
                                ) { idx ->
                                    val subfolder = gridItems[idx]
                                    OutlinedButton(
                                        onClick = {
                                            showSubfolderSelectionDialog = false
                                            viewModel.moveCurrentFileToSubfolder(subfolder)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder
                                        ) {
                                            Text(
                                            text = subfolder,
                                            style = MaterialTheme.typography.bodySmall
                                            )
                                    }
                                }
                            }
                        }
                        if (viewModeForSubfolderDialog) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            // Multi-select to VIEW liked subfolders: chips as 2-column pills
                            // Removed helper text line per request
                            Spacer(Modifier.height(6.dp))
                            val viewList = availableSubfolders
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                userScrollEnabled = true
                            ) {
                                items(
                                    count = viewList.size,
                                    key = { idx -> viewList[idx] },
                                    contentType = { "view-subfolder" }
                                ) { idx ->
                                    val name = viewList[idx]
                                    val checked = selectedViewSubfolders.contains(name)
                                    OutlinedButton(
                                        onClick = {
                                            selectedViewSubfolders = if (checked) selectedViewSubfolders - name else selectedViewSubfolders + name
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder
                                    ) {
                                                Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall
                                            )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        showSubfolderSelectionDialog = false
                                        viewModel.loadLikedRootOnly()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder
                                ) {
                                    Text("View Root Only")
                                }
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View", color = MaterialTheme.colorScheme.onPrimary)
                                }
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
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = true
                        ) {
                            items(
                                count = recentSubfolders.size,
                                key = { idx -> recentSubfolders[idx] },
                                contentType = { "recent-subfolder" }
                            ) { idx ->
                                val subfolder = recentSubfolders[idx]
                                OutlinedButton(
                                onClick = {
                                    showSubfolderSelectionDialog = false
                                    viewModel.moveCurrentFileToSubfolder(subfolder)
                                },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder
                                ) {
                                    Text(
                                        text = subfolder,
                                        style = MaterialTheme.typography.bodySmall
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
}
}
