@file:OptIn(ExperimentalFoundationApi::class)
package com.example.mobiledigger.ui.components

import androidx.compose.ui.layout.positionInWindow

import androidx.compose.ui.layout.onGloballyPositioned

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
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
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
    data class ArchiveRowItem(val archive: com.example.mobiledigger.file.archives.ArchiveItem) : PlaylistItem()
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
    val lossyOnly by viewModel.lossyOnly.collectAsState()
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
    var isMainPlayerVisible by remember { mutableStateOf(false) } // hidden by default; "Show Main Player" brings it back
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


    var showLoveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
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
    // Messages from the app ("Deleted X", "Couldn't …", hints) — shown as a snackbar.
    // (They used to be stored but never drawn, so the user saw none of them.)
    val snackbarHostState = remember { SnackbarHostState() }
    fun showInfoMessage(message: String, type: String = "info") {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            infoMessageText = message
            infoMessageType = type
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
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
    var multiMoveMode by remember { mutableStateOf(false) } // When true, the subfolder dialog moves the multi-selected files (bulk)
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
    
    
    if (showEqualizer) {
        com.example.mobiledigger.ui.screens.EqualizerDialog(onDismiss = { showEqualizer = false })
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
        modifier = Modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
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
        val archiveItems by viewModel.archives.collectAsState()
        val unpackProgress by viewModel.unpackProgress.collectAsState()
        val unpackPending by viewModel.unpackPending.collectAsState()
        val unpackDone by viewModel.unpackDone.collectAsState()
        val selectedArchiveUris by viewModel.selectedArchives.collectAsState()
        var confirmBulkArchiveDelete by remember { mutableStateOf(false) }
        // Archive contents popup (preview + pick files + keep/delete)
        var openArchive by remember { mutableStateOf<com.example.mobiledigger.file.archives.ArchiveItem?>(null) }
        // Archives count as "something loaded": a folder holding only archives must not fall back to the welcome page
        val hasSourceFolder = musicFiles.isNotEmpty() || archiveItems.isNotEmpty()
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

        // Network drives (SMB / WebDAV / MobileDigger server)
        var showDrivesDialog by remember { mutableStateOf(false) }
        val savedDrives by viewModel.drives.collectAsState()
        val activeDriveId by viewModel.activeDriveId.collectAsState()
        val activeDrive = savedDrives.firstOrNull { it.id == activeDriveId }
        LaunchedEffect(Unit) { viewModel.refreshDrives() }
        if (showDrivesDialog) {
            com.example.mobiledigger.ui.screens.NetworkDrivesDialog(viewModel) { showDrivesDialog = false }
        }

        // Permanent-deletion progress bar (rejected playlist cleanup) + subtle done flash
        val isDeletingFiles by viewModel.isDeletingFiles.collectAsState()
        val deletionProgress by viewModel.deletionProgress.collectAsState()
        var showDeleteDone by remember { mutableStateOf(false) }
        LaunchedEffect(isDeletingFiles) {
            if (!isDeletingFiles && deletionProgress >= 1f) {
                showDeleteDone = true
                kotlinx.coroutines.delay(2200)
                showDeleteDone = false
            }
        }
        AnimatedVisibility(
            visible = isDeletingFiles || showDeleteDone,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDeletingFiles)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    else Color(0xFF2E7D32).copy(alpha = 0.25f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    if (isDeletingFiles) {
                        Text("🗑 Deleting rejected files… ${(deletionProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        val animProg by animateFloatAsState(deletionProgress, label = "delProg")
                        LinearProgressIndicator(
                            progress = { animProg },
                            modifier = Modifier.weight(1f).height(5.dp),
                            color = MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        )
                    } else {
                        Text("✓ All rejected files deleted",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        openArchive?.let { a ->
            val ro = a.driveId != null && savedDrives.any {
                it.id == a.driveId && it.type == com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER && it.writeShare.isBlank()
            }
            ArchiveContentsDialog(
                archive = a,
                readOnly = ro,
                loader = { viewModel.listArchive(a) },
                onExtract = { include, deleteAfter -> viewModel.unpackArchive(a, include, deleteAfter); openArchive = null },
                onDismiss = { openArchive = null }
            )
        }

        // Network drive → phone copy/move progress (Pause / Continue / Abort)
        com.example.mobiledigger.ui.screens.TransferBanner(
            onRescan = if (viewModel.preferences.getSourceRootUri() != null) ({ viewModel.rescanSourceFolder() }) else null
        )

        // "Reject listened": rows are gone at once, this shows the files being moved
        val rejectListened by viewModel.rejectListenedProgress.collectAsState()
        AnimatedVisibility(visible = rejectListened != null, enter = expandVertically(), exit = shrinkVertically()) {
            val (done, total) = rejectListened ?: (0 to 1)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(
                        if (done < total) "👎 Moving listened tracks to Rejected… $done/$total" else "✓ $total moved to Rejected",
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    val animRej by animateFloatAsState(done / total.coerceAtLeast(1).toFloat(), label = "rejListened")
                    LinearProgressIndicator(
                        progress = { animRej },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // Archive unpacking: same slim banner as the rejected-files deletion, so the list stays usable
        AnimatedVisibility(
            visible = unpackProgress != null || unpackDone != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val up = unpackProgress
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (up != null) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color(0xFF2E7D32).copy(alpha = 0.25f)
                )
            ) {
                if (up != null) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📦 Unpacking ${up.name}",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${(up.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = { viewModel.stopUnpacking() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(2.dp))
                                Text("Stop", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val animUnpack by animateFloatAsState(up.fraction, label = "unpackProg")
                        LinearProgressIndicator(
                            progress = { animUnpack },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = Color(0xFFF59E0B),
                            trackColor = Color(0xFFF59E0B).copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            up.step + if (up.queued > 0) "  ·  ${up.queued} more queued" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        unpackDone ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
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
        // (also when only archives are loaded, so the app stays controllable)
        if (hasSourceFolder && !isLoading) {
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
                        color = if (isMonochrome) monoColor else readableAccent()
                    )
                    Text(
                        text = "v${com.example.mobiledigger.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // File count (number only), centered in the space between the logo and
                // the Reject button via flexible spacers, so it adapts to any width and
                // never steals room from the header buttons on narrow screens.
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${currentPlaylistFiles.size}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.2.sp, lineHeight = 8.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isMonochrome) monoColor else readableAccent(),
                    maxLines = 1
                )
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

            // Toolbar bar: Home, Settings, EQ, Search, Rescan, Info, Sort, Rename, MultiSelect, SpEk
            val tbIconSize = (screenWidth.value / 11.6f).coerceIn(20f, 34f).dp // 11 buttons incl. Undo + EQ
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
                ToolbarButton(icon = {
                    val eqOn by com.example.mobiledigger.audio.AudioFx.enabled.collectAsState()
                    val eqGains by com.example.mobiledigger.audio.AudioFx.gains.collectAsState()
                    val active = eqOn && eqGains.any { it != 0f }
                    Icon(Icons.Default.Equalizer, contentDescription = "Equalizer", tint = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize))
                }, label = "EQ", onClick = { showEqualizer = true })
                ToolbarButton(icon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Search", onClick = { showSearchInput = !showSearchInput; if (!showSearchInput) { viewModel.updateSearchText(""); viewModel.clearSearchResults() } })
                ToolbarButton(icon = { Icon(Icons.Default.Sync, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(tbIconSize)) }, label = "Rescan", onClick = { viewModel.rescanSourceFolder() })
                Box {
                    ToolbarButton(icon = {
                        val filterActive = sortOrder != null || losslessOnly || lossyOnly
                        Box {
                            Icon(Icons.Default.List, contentDescription = "Sort", tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(tbIconSize))
                            if (filterActive) {
                                // Marker dot when a sort/filter is active
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }, label = "Sort", onClick = { filterMenuExpanded = true })
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
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Lossy Only (MP3, AAC…)"); if (lossyOnly) { Spacer(Modifier.width(4.dp)); Text("✓", color = MaterialTheme.colorScheme.primary) } } }, onClick = { viewModel.toggleLossyOnly() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Clear Sort & Filter") }, onClick = { filterMenuExpanded = false; viewModel.clearSort(); if (losslessOnly) viewModel.toggleLosslessOnly(); if (lossyOnly) viewModel.toggleLossyOnly() })
                        DropdownMenuItem(
                            text = { Text(if (activeDrive != null) "Network drive: ${activeDrive.name}" else "Network drives…") },
                            leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                            onClick = { filterMenuExpanded = false; showDrivesDialog = true }
                        )
                    }
                }
                val canUndo = viewModel.sortResults.collectAsState().value.isNotEmpty()
                ToolbarButton(
                    icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        modifier = Modifier.size(tbIconSize)) },
                    label = "Undo",
                    onClick = { if (canUndo) viewModel.undoLastAction() }
                )
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
                if (selectedArchiveUris.isEmpty()) Row(
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
                    OutlinedButton(onClick = { viewModeForSubfolderDialog = false; multiMoveMode = true; showSubfolderSelectionDialog = true }, modifier = Modifier.height(26.dp), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)); Text("Move", style = msLabelStyle, maxLines = 1)
                    }
                    OutlinedButton(onClick = { viewModel.sortSelectedFiles(SortAction.LIKE) }, modifier = Modifier.height(26.dp), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = YesButton); Spacer(Modifier.width(3.dp)); Text("Like", style = msLabelStyle, maxLines = 1)
                    }
                    OutlinedButton(onClick = { viewModel.toggleMultiSelectionMode() }, modifier = Modifier.height(26.dp), border = BorderStroke(1.dp, Color.Red), contentPadding = msPad, shape = RoundedCornerShape(16.dp)) {
                        Text("Exit", style = msLabelStyle, maxLines = 1, color = Color.Red)
                    }
                }
                // Archives picked in select mode get their own actions
                androidx.compose.animation.AnimatedVisibility(selectedArchiveUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF59E0B))
                        Text("${selectedArchiveUris.size} archive${if (selectedArchiveUris.size == 1) "" else "s"}",
                            style = labelStyle.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.unpackSelectedArchives() }, modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
                            Text("Unpack", style = labelStyle.copy(fontSize = 11.sp))
                        }
                        OutlinedButton(onClick = { confirmBulkArchiveDelete = true }, modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp))
                            Text("Delete", style = labelStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(onClick = { viewModel.toggleMultiSelectionMode() }, modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, Color.Red), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Exit", style = labelStyle.copy(fontSize = 11.sp), color = Color.Red)
                        }
                    }
                }
                if (confirmBulkArchiveDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmBulkArchiveDelete = false },
                        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Delete ${selectedArchiveUris.size} archive${if (selectedArchiveUris.size == 1) "" else "s"}?") },
                        text = { Text("They're deleted without unpacking. This can't be undone.") },
                        confirmButton = {
                            Button(onClick = { confirmBulkArchiveDelete = false; viewModel.deleteSelectedArchives() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                        },
                        dismissButton = { TextButton(onClick = { confirmBulkArchiveDelete = false }) { Text("Cancel") } }
                    )
                }
                // Selection count indicator
                if (selectedArchiveUris.isEmpty()) Text(
                    "${selectedCount.size} track${if (selectedCount.size == 1) "" else "s"} selected",
                    style = labelStyle.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
                )
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
        }
        
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Folder selection buttons - minimize after selection
            if (!hasSourceFolder && !isLoading) { // Hide while loading so the hero/buttons don't stack behind the loading card
                val recentSources by viewModel.recentSourceUris.collectAsState()
                val recentDestinations by viewModel.recentDestinationUris.collectAsState()
                // recentSources is a StateFlow, so this recomposes once the saved folders have loaded
                val lastSource = remember(recentSources) { viewModel.preferences.getSourceRootUri() ?: recentSources.firstOrNull() }
                val destName = viewModel.destinationFolder.collectAsState().value?.let { d ->
                    d.name ?: folderLabel(d.uri.toString())
                }
                WelcomePanel(
                    lastSource = lastSource,
                    destinationName = destName,
                    activeDriveName = activeDrive?.name,
                    savedDriveCount = savedDrives.size,
                    recentSources = recentSources,
                    recentDestinations = recentDestinations,
                    onPickSource = { folderLauncher.launch(null) },
                    onPickDestination = { destLauncher.launch(null) },
                    onResume = { viewModel.resumeLastSources() },
                    onOpenDrives = { showDrivesDialog = true },
                    onLoadRecent = { viewModel.setSourceFolderPreference(android.net.Uri.parse(it)) },
                    onRemoveRecent = { viewModel.removeRecentSource(it) },
                    onSetDestination = { viewModel.selectDestinationFolder(android.net.Uri.parse(it)) },
                    onRemoveDestination = { viewModel.removeRecentDestination(it) },
                    sourceMode = viewModel.sourceMode.collectAsState().value,
                    onSourceMode = { viewModel.setSourceMode(it) }
                )
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (currentSearchText.isNotBlank()) {
                                viewModel.searchMusicImmediate(currentSearchText)
                                searchExpanded = true
                            }
                        }),
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
                // Centre the card on the SCREEN, not in the space under the header/toolbar
                // (that put it visibly low).
                val rootView = androidx.compose.ui.platform.LocalView.current
                var boxTop by remember { mutableFloatStateOf(0f) }
                var boxH by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        boxTop = it.positionInWindow().y; boxH = it.size.height.toFloat()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.graphicsLayer {
                        val shift = rootView.rootView.height / 2f - (boxTop + boxH / 2f)
                        // never push it above the top of the area it lives in
                        translationY = if (boxH > 0f) shift.coerceAtLeast(-(boxH / 2f - size.height / 2f)) else 0f
                    }) { LoadingCard() }
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
                                                                // Slide the card off — speed-scaled but BOUNDED (60..450ms) so a
                                                                // slow Animation Speed can't delay/stall the action — then fire
                                                                // like/dislike immediately, then reset for the next track.
                                                                val exitMs = (220f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toInt().coerceIn(60, 450)
                                                                mainSwipeOffset.animateTo(exit, tween(exitMs))
                                                                // Both directions sort the CURRENT file AND advance to the next
                                                                // track. LIKE used to only sort (leaving the audible file playing
                                                                // while it was removed from the list) which desynced _currentIndex
                                                                // from _currentPlayingFile — the next swipe then re-loaded the stale
                                                                // index and the track appeared to "restart". Removing+advancing on
                                                                // both directions keeps index and playing file in sync.
                                                                if (current < 0) {
                                                                    try {
                                                                        suppressMiniOnLeftSwipe = true
                                                                        try { prevFile?.let { viewModel.removeFromCurrentListByUri(it.uri) } } catch (_: Exception) {}
                                                                        viewModel.playNextAfterRemoval()
                                                                        prevFile?.let { viewModel.sortMusicFile(it, SortAction.DISLIKE) }
                                                                    } catch (_: Exception) { }
                                                                } else if (current > 0) {
                                                                    try {
                                                                        try { prevFile?.let { viewModel.removeFromCurrentListByUri(it.uri) } } catch (_: Exception) {}
                                                                        viewModel.playNextAfterRemoval()
                                                                        prevFile?.let { viewModel.sortMusicFile(it, SortAction.LIKE) }
                                                                    } catch (_: Exception) { }
                                                                }
                                                                mainSwipeOffset.snapTo(0f)
                                                            }
                                                        } else {
                                                            scope.launch {
                                                                mainSwipeOffset.animateTo(0f, animationSpec)
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
                                                            mainSwipeOffset.animateTo(0f, animationSpec)
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
                                                            mainSwipeOffset.animateTo(0f, animationSpec)
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
                        if (currentPlaylistFiles.isEmpty() && !(currentPlaylistTab == PlaylistTab.TODO && musicFiles.isEmpty())) {
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
                            
                            // (The old "Must Know" card is replaced by the welcome page.)

                        }
                        
                        // Conditionally display search results or current playlist files (reverted to original)
                        val displayFiles = currentPlaylistFiles // Search results handled within dropdown
                        
                        // Group files by subfolder for TODO and LIKED playlists
                        val groupedItems: List<PlaylistItem> = if (currentPlaylistTab == PlaylistTab.TODO || currentPlaylistTab == PlaylistTab.LIKED) {
                            // Group by subfolder, handle null subfolders
                            val grouped = currentPlaylistFiles.groupBy { it.subfolder }
                            // Archives sit inside their own folder (To Do only); folders holding only
                            // archives get a header too, so they no longer look empty.
                            val archivesByFolder = if (currentPlaylistTab == PlaylistTab.TODO)
                                archiveItems.groupBy { it.folder?.takeIf { f -> f.isNotBlank() } } else emptyMap()
                            // Create flat list with headers: [Header, Archive, File, File, Header, ...]
                            buildList {
                                // Root folder first
                                archivesByFolder[null]?.let { a -> addAll(a.map { PlaylistItem.ArchiveRowItem(it) }) }
                                grouped[null]?.let { files ->
                                    addAll(files.map { PlaylistItem.FileItem(it) })
                                }
                                // Then each subfolder: header, its archives, its tracks
                                (grouped.keys.filterNotNull() + archivesByFolder.keys.filterNotNull())
                                    .distinct()
                                    .sorted()
                                    .forEach { subfolder ->
                                        add(PlaylistItem.HeaderItem(subfolder))
                                        archivesByFolder[subfolder]?.let { a -> addAll(a.map { PlaylistItem.ArchiveRowItem(it) }) }
                                        grouped[subfolder]?.let { files -> addAll(files.map { PlaylistItem.FileItem(it) }) }
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
                                    is PlaylistItem.ArchiveRowItem -> "archive_${item.archive.uri}"
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
                                        val subName = playlistItem.subfolder
                                        val subIndices = currentPlaylistFiles.indices
                                            .filter { currentPlaylistFiles[it].subfolder == subName }.toSet()
                                        val subArch = if (currentPlaylistTab == PlaylistTab.TODO) archiveItems.filter { it.folder == subName }.map { it.uri } else emptyList()
                                        val allSubSelected = if (selectedArchiveUris.isNotEmpty() || (selectedIndices.isEmpty() && subIndices.isEmpty()))
                                            subArch.isNotEmpty() && selectedArchiveUris.containsAll(subArch)
                                        else subIndices.isNotEmpty() && selectedIndices.containsAll(subIndices)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val archCount = archiveItems.count { it.folder == subName }
                                            Text(
                                                text = "$subName  ·  ${subIndices.size} Tracks" +
                                                    if (archCount > 0 && currentPlaylistTab == PlaylistTab.TODO) "  ·  $archCount archive${if (archCount == 1) "" else "s"}" else "",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Select/deselect all files in this subfolder for bulk like/dislike
                                            Checkbox(
                                                checked = allSubSelected,
                                                onCheckedChange = { viewModel.toggleSubfolderSelection(subName) }
                                            )
                                        }
                                    }
                                }
                                is PlaylistItem.ArchiveRowItem -> {
                                    val a = playlistItem.archive
                                    val ro = a.driveId != null && savedDrives.any {
                                        it.id == a.driveId && it.type == com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER && it.writeShare.isBlank()
                                    }
                                    ArchiveRow(
                                        modifier = Modifier.animateItem(),
                                        selectionMode = isMultiSelectionMode,
                                        selected = a.uri in selectedArchiveUris,
                                        onToggleSelect = { viewModel.toggleArchiveSelection(a.uri) },
                                        archive = a,
                                        readOnly = ro,
                                        state = when {
                                            unpackProgress?.name == a.name && a.uri in unpackPending -> "Unpacking ${((unpackProgress?.fraction ?: 0f) * 100).toInt()}%"
                                            a.uri in unpackPending -> "Queued"
                                            else -> null
                                        },
                                        onOpen = { openArchive = a },
                                        onDelete = { viewModel.deleteArchive(a) }
                                    )
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
                                                                            launch { rowSwipeOffset.animateTo(exit, tween((180f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toInt().coerceIn(60, 450))) }
                                                                            delay((180f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toLong().coerceIn(60L, 450L))
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
                                                                        launch { rowSwipeOffset.animateTo(exit, tween((180f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toInt().coerceIn(60, 450))) }
                                                                        delay((180f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toLong().coerceIn(60L, 450L))
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
                    // Match the current track by its ROW KEY, not by a list index. The
                    // LazyColumn renders `groupedItems` (file rows interleaved with
                    // subfolder HeaderItems), so a currentPlaylistFiles index does NOT equal
                    // the LazyColumn index when headers are present — which made the
                    // mini-player show even though the current row was on screen. FileItem
                    // rows are keyed "${uri}_${subfolder}", so we match on the URI prefix.
                    val currentTrackKeyPrefix = currentPlayingFile?.uri?.let { "${it}_" }
                    val isCurrentTrackFullyVisible = remember(listState.layoutInfo, currentTrackKeyPrefix) {
                        val info = listState.layoutInfo
                        val item = if (currentTrackKeyPrefix == null) null
                            else info.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith(currentTrackKeyPrefix) == true }
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
                    val isCurrentTrackAnyVisible = remember(listState.layoutInfo, currentTrackKeyPrefix) {
                        val info = listState.layoutInfo
                        if (currentTrackKeyPrefix == null) false
                        else info.visibleItemsInfo.any { (it.key as? String)?.startsWith(currentTrackKeyPrefix) == true }
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
                                                        miniSwipeOffset.animateTo(exit, tween((220f / visualSettings.animationSpeed.coerceAtLeast(0.1f)).toInt().coerceIn(60, 450)))
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
                                        waveformHeight = visualSettings.miniWaveformHeight.toInt(),
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
            onDismissRequest = { viewModel.hideSubfolderDialog(); multiMoveMode = false },
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
                    onClick = {
                        if (multiMoveMode) {
                            // Bulk: create the folder (auto-created by the move) and move the selection.
                            multiMoveMode = false
                            val name = newSubfolderName.trim()
                            viewModel.hideSubfolderDialog()
                            if (name.isNotBlank()) viewModel.moveSelectedFilesToSubfolder(name)
                        } else viewModel.createNewSubfolder()
                    },
                    enabled = newSubfolderName.trim().isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSubfolderDialog(); multiMoveMode = false }) {
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
            onDismissRequest = { showSubfolderSelectionDialog = false; multiMoveMode = false },
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
                                            if (multiMoveMode) { multiMoveMode = false; viewModel.moveSelectedFilesToSubfolder(subfolder) }
                                            else viewModel.moveCurrentFileToSubfolder(subfolder)
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
                                    if (multiMoveMode) { multiMoveMode = false; viewModel.moveSelectedFilesToSubfolder(subfolder) }
                                    else viewModel.moveCurrentFileToSubfolder(subfolder)
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
            confirmButton = { TextButton(onClick = { showSubfolderSelectionDialog = false; multiMoveMode = false }) { Text("Close") } }
        )
    }


}
}
}
