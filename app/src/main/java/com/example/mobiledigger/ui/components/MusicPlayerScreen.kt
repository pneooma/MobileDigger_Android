@file:OptIn(ExperimentalFoundationApi::class)
package com.example.mobiledigger.ui.components

import androidx.compose.ui.draw.clip

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.example.mobiledigger.R
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.ui.components.rememberSharedWaveformState
import com.example.mobiledigger.ui.components.WaveformWithToggle
import com.example.mobiledigger.ui.components.SettingsDialog
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with


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
import com.example.mobiledigger.ui.theme.LikeGreen
import com.example.mobiledigger.ui.theme.NoButton
import com.example.mobiledigger.ui.theme.YesButton
import com.example.mobiledigger.ui.theme.AvailableThemes
import com.example.mobiledigger.viewmodel.MusicViewModel
import com.example.mobiledigger.viewmodel.PlaylistTab
import com.example.mobiledigger.viewmodel.SortField

import kotlin.math.abs
import kotlin.math.ceil
import java.util.Locale
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.onPreviewKeyEvent
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


/**
 * Where a folder lives, worked out from one of its files: the place ("Phone", "SD card / USB",
 * "Pi · Library", "Network · NAS", …) and the full folder path inside it, e.g.
 * "Phone" + ["Music", "To triage", "Abstracted"].
 */
data class FolderLocation(val place: String, val kind: String, val parts: List<String>)

fun folderLocation(
    fileUri: android.net.Uri?,
    drives: List<com.example.mobiledigger.file.drives.NetworkDrive>
): FolderLocation? {
    val uri = fileUri ?: return null
    // Network drives (served through the local proxy)
    com.example.mobiledigger.file.drives.DriveProxy.parse(uri)?.let { (driveId, path) ->
        val d = drives.firstOrNull { it.id == driveId }
        val kind = when (d?.type) {
            com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER -> "pi"
            com.example.mobiledigger.file.drives.DriveType.USB -> "usb"
            else -> "network"
        }
        val place = when (d?.type) {
            com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER -> "Pi"
            com.example.mobiledigger.file.drives.DriveType.SMB -> "Network (SMB)"
            com.example.mobiledigger.file.drives.DriveType.WEBDAV -> "Network (WebDAV)"
            com.example.mobiledigger.file.drives.DriveType.USB -> "USB drive"
            null -> "Network drive"
        } + (d?.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        return FolderLocation(place, kind, path.trim('/').split('/').dropLast(1).filter { it.isNotBlank() })
    }
    return when (uri.scheme) {
        "http", "https" -> FolderLocation("Network · ${uri.host ?: "server"}", "network",
            (uri.pathSegments ?: emptyList()).dropLast(1))
        "file" -> FolderLocation("Phone (app storage)", "phone",
            (uri.path ?: "").trim('/').split('/').dropLast(1).filter { it.isNotBlank() })
        "content" -> {
            val docId = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull()
            when {
                uri.authority == "com.android.externalstorage.documents" && docId != null -> {
                    val volume = docId.substringBefore(':')
                    val path = docId.substringAfter(':', "")
                    val place = if (volume.equals("primary", true)) "Phone" else "SD card / USB drive ($volume)"
                    FolderLocation(place, if (volume.equals("primary", true)) "phone" else "external",
                        path.trim('/').split('/').dropLast(1).filter { it.isNotBlank() })
                }
                uri.authority?.contains("google.android.apps.docs") == true -> FolderLocation("Google Drive", "cloud", emptyList())
                uri.authority?.contains("downloads") == true -> FolderLocation("Phone · Downloads", "phone", emptyList())
                else -> FolderLocation(uri.authority ?: "Unknown", "cloud", emptyList())
            }
        }
        else -> null
    }
}

/** Small tag on a To Do row whose exact file (name + size) was already liked ('L') or rejected ('R'). */
@Composable
fun AlreadySortedChip(was: Char) {
    val liked = was == 'L'
    val c = if (liked) Color(0xFF22C55E) else Color(0xFFEF4444)
    Row(
        modifier = Modifier
            .padding(end = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.16f))
            .border(0.75.dp, c.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (liked) Icons.Default.Favorite else Icons.Default.ThumbDown,
            contentDescription = if (liked) "Already liked" else "Already rejected",
            tint = c, modifier = Modifier.size(9.dp))
        Spacer(Modifier.width(2.dp))
        Text("dup", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, lineHeight = 9.sp),
            color = c, fontWeight = FontWeight.SemiBold)
    }
}

// Sealed class for playlist items (headers and files)
sealed class PlaylistItem {
    data class HeaderItem(val subfolder: String) : PlaylistItem()
    data class FileItem(val file: com.example.mobiledigger.model.MusicFile) : PlaylistItem()
    data class ArchiveRowItem(val archive: com.example.mobiledigger.file.archives.ArchiveItem) : PlaylistItem()
    /** Collapsible "Archives" drawer bar at the top of the To Do list. */
    data class ArchivesToggleItem(val count: Int) : PlaylistItem()
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
    // Main-player swipe distances in dp → px (were raw pixels)
    val motionDensity = androidx.compose.ui.platform.LocalDensity.current
    val mainTriggerPx = with(motionDensity) { Motion.mainSwipeTrigger.toPx() }
    val mainDragMaxPx = with(motionDensity) { 114.dp.toPx() }
    val mainHintPx = with(motionDensity) { 30.dp.toPx() }
    val mainExitPx = with(motionDensity) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() } + 40f
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
    

    var showLoveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUndoPicker by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    // "Archives" drawer in the To Do list: collapsed by default, choice remembered
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE) }
    var archivesOpen by remember { mutableStateOf(uiPrefs.getBoolean("archives_open", false)) }
    val setArchivesOpen: (Boolean) -> Unit = { open -> archivesOpen = open; uiPrefs.edit().putBoolean("archives_open", open).apply() }
    // Toolbar of action buttons (Home, Settings, EQ, Search…): can be hidden for more list space; remembered
    var isActionsVisible by remember { mutableStateOf(uiPrefs.getBoolean("actions_visible", true)) }
    var showThemeSelectionDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteDialogStep by remember { mutableStateOf(0) }
    var showSearchInput by remember { mutableStateOf(false) } // New state to control search input visibility
    val searchFocusRequester = remember { FocusRequester() }
    
    // Info message dropdown state
    var showSubfolderDialog by remember { mutableStateOf(false) } // New state for subfolder selection
    
    // Job for managing notification display
    var notificationJob by remember { mutableStateOf<Job?>(null) }
    
    var topMessage by remember { mutableStateOf<String?>(null) }
    fun showInfoMessage(message: String, type: String = "info") {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            // Shown as a slim banner at the TOP (like the Rejected-moving / deleting banners),
            // long enough to read: 2.5 s + 40 ms per character, max 6 s.
            topMessage = message
            kotlinx.coroutines.delay((2500L + message.length * 40L).coerceAtMost(6000L))
            topMessage = null
        }
    }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var deleteActionType by remember { mutableStateOf<PlaylistTab?>(null) } // State for delete all confirmation
    var showShareZipDialog by remember { mutableStateOf(false) } // State for ZIP sharing confirmation
    var showReadmeDialog by remember { mutableStateOf(false) } // State for README dialog
    var showSubfolderDropdown by remember { mutableStateOf(false) } // State for subfolder dropdown
    var showSubfolderManagementDialog by remember { mutableStateOf(false) } // State for subfolder management dialog
    var showSubfolderSelectionDialog by remember { mutableStateOf(false) } // State for subfolder selection dialog from playlist
    var viewModeForSubfolderDialog by remember { mutableStateOf(false) } // When true, dialog shows VIEW selector; when false, MOVE-only
    var multiMoveMode by remember { mutableStateOf(false) } // When true, the subfolder dialog moves the multi-selected files (bulk)

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
    Motion.update(visualSettings)
    val isMonochrome = viewModel.themeManager.selectedTheme.value.name == "Monochrome"
    val monoColor = MaterialTheme.colorScheme.onSurfaceVariant
    val customWaveformColor = remember(visualSettings.waveformColor) {
        if (visualSettings.waveformColor != 0) Color(visualSettings.waveformColor) else null
    }
    

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

    // Delete the selected tracks (e.g. folders ticked while a Lossy / Lossless filter is on)
    if (showDeleteSelectedDialog) {
        val n = viewModel.selectedIndices.collectAsState().value.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete $n selected track${if (n == 1) "" else "s"}?") },
            text = { Text("They're deleted permanently from the phone or the network drive. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteSelectedDialog = false; viewModel.deleteSelectedTracks() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") } }
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
            // To Do: exactly what the list shows (filters respected)
            PlaylistTab.TODO -> if (currentPlaylistTab == PlaylistTab.TODO) currentPlaylistFiles.size else musicFiles.size
            PlaylistTab.LIKED -> likedFiles.size
        }
        val todoFiltered = actionType == PlaylistTab.TODO && (losslessOnly || lossyOnly)
        
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false; deleteActionType = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("⚠️ WARNING: Delete ALL $playlistName Files?") },
            text = { 
                Column {
                    Text(
                        if (actionType == PlaylistTab.TODO)
                            "This will PERMANENTLY delete the $fileCount ${if (todoFiltered) (if (lossyOnly) "lossy " else "lossless ") else ""}track${if (fileCount == 1) "" else "s"} shown in To Do" +
                                (if (todoFiltered) " (only what the filter shows)." else ".")
                        else "This will PERMANENTLY delete ALL files in the '$playlistName' folder.",
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
    // Undo: pick which of the last 5 likes/rejects to take back
    if (showUndoPicker) {
        val undoable by viewModel.sortResults.collectAsState()
        if (undoable.isEmpty()) showUndoPicker = false
        val now = System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { showUndoPicker = false },
            title = { Text("Undo which one?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    undoable.asReversed().forEach { r ->
                        val liked = r.action == SortAction.LIKE
                        val ago = ((now - r.timestamp) / 1000).let { sec ->
                            when { sec < 60 -> "just now"; sec < 3600 -> "${sec / 60} min ago"; else -> "${sec / 3600} h ago" }
                        }
                        Card(
                            onClick = { showUndoPicker = false; viewModel.undoAction(r) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (liked) Icons.Default.Favorite else Icons.Default.ThumbDown, contentDescription = null,
                                    tint = if (liked) Color(0xFF22C55E) else Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.musicFile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text((if (liked) "Liked" else "Rejected") + " · " + ago, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUndoPicker = false }) { Text("Close") } }
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
                top = 0.dp, // status bar already in contentPadding (was counted twice → big gap)
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
        // Main screen once a source was loaded (even an empty one) or Liked/Rejected were opened —
        // an empty To Do no longer locks you out on the welcome page.
        val workspaceOpen by viewModel.workspaceOpen.collectAsState()
        val hasSourceFolder = workspaceOpen || musicFiles.isNotEmpty() || archiveItems.isNotEmpty()
        val hasDestinationFolder = viewModel.destinationFolder.collectAsState().value != null // This can be false

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
        val alreadySorted by viewModel.alreadySorted.collectAsState()
        val activeDriveId by viewModel.activeDriveId.collectAsState()
        val activeDrive = savedDrives.firstOrNull { it.id == activeDriveId }
        LaunchedEffect(Unit) { viewModel.refreshDrives() }
        if (showDrivesDialog) {
            com.example.mobiledigger.ui.screens.NetworkDrivesDialog(viewModel) { showDrivesDialog = false }
        }

        val topMessageBanner: @Composable () -> Unit = {
        // App messages ("1 file moved to Liked", "Source set…", errors): slim banner at the top,
        // same style as the progress banners below. Tap to dismiss.
        var lastTopMessage by remember { mutableStateOf("") }
        topMessage?.let { lastTopMessage = it }
        AnimatedVisibility(
            visible = topMessage != null,
            enter = expandVertically(tween(Motion.panelMs() / 2)) + fadeIn(tween(Motion.panelMs() / 2)),
            exit = shrinkVertically(tween(Motion.panelMs() / 3)) + fadeOut(tween(Motion.panelMs() / 4))
        ) {
            val m = lastTopMessage
            val lower = m.lowercase()
            val (bg, icon) = when {
                listOf("couldn", "fail", "error", "cannot", "can't", "not found", "denied").any { it in lower } ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f) to "⚠️"
                "reject" in lower || "dislike" in lower -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f) to "👎"
                "like" in lower -> Color(0xFF2E7D32).copy(alpha = 0.28f) to "❤️"
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) to "ℹ️"
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 0.dp)
                    .clickable { notificationJob?.cancel(); topMessage = null },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(icon, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(m, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }

        }
        // On the welcome page (no tabs yet) messages stay at the top
        if (!hasSourceFolder || isLoading) topMessageBanner()

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

        // Progress banners (deleting rejected files, network copy, moving to Liked/Rejected,
        // unpacking). Shown under the playlist buttons like the app messages; on the welcome
        // page (no tabs yet) at the top.
        val progressBanners: @Composable () -> Unit = {
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
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
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
                            style = MaterialTheme.typography.bodyMedium)
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
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        // Network drive → phone copy/move progress (Pause / Continue / Abort)
        com.example.mobiledigger.ui.screens.TransferBanner(
            onRescan = if (viewModel.preferences.getSourceRootUri() != null) ({ viewModel.rescanSourceFolder() }) else null
        )

        // "Reject listened": rows are gone at once, this shows the files being moved
        val rejectListened by viewModel.rejectListenedProgress.collectAsState()
        // Keep the last real progress while the banner animates out (it used to fall back to a
        // default and flash "Moving to Rejected… 0/1" at the end of a move to Liked)
        var lastBulkProgress by remember { mutableStateOf(MusicViewModel.BulkSortProgress(0, 1, false)) }
        rejectListened?.let { lastBulkProgress = it }
        // Only for several tracks; a single track moves silently
        AnimatedVisibility(visible = rejectListened != null && lastBulkProgress.total > 1, enter = expandVertically(), exit = shrinkVertically()) {
            val prog = lastBulkProgress
            val done = prog.done; val total = prog.total
            Card(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (prog.toLiked) Color(0xFF2E7D32).copy(alpha = 0.25f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(
                        (if (prog.toLiked) "❤️ Moving to Liked… " else "👎 Moving to Rejected… ") + "$done/$total",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(10.dp))
                    val animRej by animateFloatAsState(done / total.coerceAtLeast(1).toFloat(), label = "rejListened")
                    LinearProgressIndicator(
                        progress = { animRej },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = if (prog.toLiked) Color(0xFF43A047) else MaterialTheme.colorScheme.error,
                        trackColor = (if (prog.toLiked) Color(0xFF43A047) else MaterialTheme.colorScheme.error).copy(alpha = 0.2f)
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
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
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
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${(up.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = { viewModel.stopUnpacking() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(2.dp))
                                Text("Stop", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        unpackDone ?: "", style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        }
        if (!hasSourceFolder || isLoading) progressBanners()

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
        // The action buttons toolbar is set up with the header but drawn between the main
        // player and the playlist tabs (see MainPlayerSection / PlaylistTabsHeader below)
        var actionsToolbar: (@Composable () -> Unit)? = null
        if (hasSourceFolder && !isLoading) {
            val labelStyle = MaterialTheme.typography.labelSmall
            var showBulkSendDialog by remember { mutableStateOf(false) }
            val hdrBtnHeight = 29.dp
            val hdrBtnPad = PaddingValues(horizontal = 7.dp, vertical = 1.dp)

            // Thin title bar between the status bar and the header buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background((if (isMonochrome) monoColor else readableAccent()).copy(alpha = 0.10f))
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MobileDigger",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMonochrome) monoColor else readableAccent()
                )
                Text(
                    text = "  v${com.example.mobiledigger.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        // 30% wider than its natural size (easier to hit); content stays centred
                        modifier = Modifier.height(hdrBtnHeight).layout { measurable, constraints ->
                            val natural = measurable.maxIntrinsicWidth(constraints.maxHeight)
                            val w = (natural * 1.3f).toInt().coerceAtMost(constraints.maxWidth)
                            val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
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
                // Show/Hide the action buttons toolbar
                OutlinedButton(
                    onClick = {
                        isActionsVisible = !isActionsVisible
                        uiPrefs.edit().putBoolean("actions_visible", isActionsVisible).apply()
                    },
                    contentPadding = hdrBtnPad,
                    modifier = Modifier.height(hdrBtnHeight)
                ) {
                    Text(if (isActionsVisible) "Hide Actions" else "Show Actions", style = labelStyle, maxLines = 1)
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

            actionsToolbar = { androidx.compose.animation.AnimatedVisibility(
                visible = isActionsVisible,
                enter = expandVertically(tween(Motion.panelMs() / 2)) + fadeIn(tween(Motion.panelMs() / 2)),
                exit = shrinkVertically(tween(Motion.panelMs() / 2)) + fadeOut(tween(Motion.panelMs() / 3))
            ) {
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
                                text = { Text(if (activeDrive != null) "Drive: ${activeDrive.name}" else "Drives (USB & network)…") },
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
                        onClick = { if (canUndo) showUndoPicker = true }
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
            } }

            // Multi-select inline controls — compact grid layout
            if (isMultiSelectionMode) {
                val selectedCount by viewModel.selectedIndices.collectAsState()
                if (selectedArchiveUris.isEmpty()) Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Bigger touch targets: icon above label, all buttons share the width equally
                    @Composable
                    fun RowScope.MsButton(label: String, icon: ImageVector?, tint: Color = LocalContentColor.current,
                                          border: BorderStroke? = null, textColor: Color = Color.Unspecified, onClick: () -> Unit) {
                        OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = border ?: ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
                                Text(label, style = labelStyle.copy(fontSize = 11.5.sp), maxLines = 1, color = textColor)
                            }
                        }
                    }
                    MsButton("Reject", Icons.Default.ThumbDown, tint = if (isMonochrome) monoColor else Color.Red) { viewModel.sortSelectedFiles(SortAction.DISLIKE) }
                    MsButton("Rename", Icons.Default.Edit) { showRenameSelectedDialog = true }
                    MsButton("Move", Icons.Default.Folder) { viewModeForSubfolderDialog = false; multiMoveMode = true; showSubfolderSelectionDialog = true }
                    MsButton("Like", Icons.Default.Favorite, tint = YesButton) { viewModel.sortSelectedFiles(SortAction.LIKE) }
                    MsButton("Delete", Icons.Default.DeleteForever, tint = Color.Red) { if (selectedCount.isNotEmpty()) showDeleteSelectedDialog = true }
                    MsButton("Exit", Icons.Default.Close, tint = Color.Red, border = BorderStroke(1.dp, Color.Red), textColor = Color.Red) { viewModel.toggleMultiSelectionMode() }
                }
                // Archives picked in select mode get their own actions
                androidx.compose.animation.AnimatedVisibility(selectedArchiveUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFF59E0B))
                        Text("${selectedArchiveUris.size} archive${if (selectedArchiveUris.size == 1) "" else "s"}",
                            style = labelStyle.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.unpackSelectedArchives() }, modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text("Unpack", style = labelStyle.copy(fontSize = 13.sp))
                        }
                        OutlinedButton(onClick = { confirmBulkArchiveDelete = true }, modifier = Modifier.height(38.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp))
                            Text("Delete", style = labelStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(onClick = { viewModel.toggleMultiSelectionMode() }, modifier = Modifier.height(38.dp),
                            border = BorderStroke(1.dp, Color.Red), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Exit", style = labelStyle.copy(fontSize = 13.sp), color = Color.Red)
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
                        // ✨ Clean up: underscores, website tags, track numbers, promo junk, double tags…
                        var beforeClean by remember { mutableStateOf<String?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cleaned = remember(manualRenameText) { com.example.mobiledigger.util.FilenameCleaner.clean(manualRenameText) }
                            FilledTonalButton(
                                onClick = { beforeClean = manualRenameText; manualRenameText = cleaned },
                                enabled = cleaned != manualRenameText
                            ) { Text(if (cleaned != manualRenameText) "✨ Clean up" else "✓ Clean") }
                            if (beforeClean != null && beforeClean != manualRenameText) {
                                TextButton(onClick = { manualRenameText = beforeClean!!; beforeClean = null }) { Text("Undo") }
                            }
                        }
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
                        // ✨ Clean up: underscores, website tags, track numbers, promo junk, double tags…
                        var beforeClean by remember { mutableStateOf<String?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cleaned = remember(manualRenameText) { com.example.mobiledigger.util.FilenameCleaner.clean(manualRenameText) }
                            FilledTonalButton(
                                onClick = { beforeClean = manualRenameText; manualRenameText = cleaned },
                                enabled = cleaned != manualRenameText
                            ) { Text(if (cleaned != manualRenameText) "✨ Clean up" else "✓ Clean") }
                            if (beforeClean != null && beforeClean != manualRenameText) {
                                TextButton(onClick = { manualRenameText = beforeClean!!; beforeClean = null }) { Text("Undo") }
                            }
                        }
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
        
            
            // Gap under the header buttons. (With a source loaded, each section below — player,
            // tabs, banners, search, list — adds its own 6 dp top gap; nothing is lifted with
            // negative offsets any more, which used to jam whichever section came first into
            // the header when the ones above it were hidden.)
            Spacer(modifier = Modifier.height(4.dp))
            
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
                    onSourceMode = { viewModel.setSourceMode(it) },
                    onOpenSorted = {
                        val dest = viewModel.destinationFolder.value?.uri
                        if (dest == null || !openFolderInFiles(context, dest))
                            showInfoMessage("Couldn't open the folder: no file manager on this phone can show it.")
                    }
                )
            } else {
                // When both not selected, show full-size buttons (kept below header)
            }
            
            if (!hasSourceFolder) Spacer(modifier = Modifier.height(16.dp))

            
            // Search box + inline results. Rendered BELOW the main player and the playlist tabs.
            @Composable
            fun SearchSection() {
            // Slides down + fades in under the tabs (timed by Settings → Animations → Main player show/hide)
            androidx.compose.animation.AnimatedVisibility(
                visible = showSearchInput,
                enter = androidx.compose.animation.expandVertically(tween(Motion.panelMs() / 2, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
                    androidx.compose.animation.slideInVertically(tween(Motion.panelMs() / 2, easing = FastOutSlowInEasing)) { -it / 3 } +
                    androidx.compose.animation.fadeIn(tween(Motion.panelMs() / 2)),
                exit = androidx.compose.animation.shrinkVertically(tween(Motion.panelMs() / 3, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
                    androidx.compose.animation.fadeOut(tween(Motion.panelMs() / 4))
            ) { // Conditionally display search input
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

                // Results are shown INLINE under the field (they used to open as a floating menu
                // that covered the playlist tabs and the main player).
                Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp)) {
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
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val resultsMaxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f).dp
                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchExpanded && searchResults.isNotEmpty(),
                        enter = androidx.compose.animation.expandVertically(tween(Motion.panelMs() / 2)) + androidx.compose.animation.fadeIn(tween(Motion.panelMs() / 2)),
                        exit = androidx.compose.animation.shrinkVertically(tween(Motion.panelMs() / 2)) + androidx.compose.animation.fadeOut(tween(Motion.panelMs() / 3))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${searchResults.size} result${if (searchResults.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                IconButton(onClick = { searchExpanded = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ExpandLess, contentDescription = "Hide results", modifier = Modifier.size(20.dp))
                                }
                            }
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = resultsMaxH)) {
                                items(searchResults.size) { idx ->
                                    val musicFile = searchResults[idx]
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .clickable { viewModel.playFile(musicFile); searchExpanded = false }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
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
                                    if (idx < searchResults.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                    // Collapsed results: one tap brings them back
                    if (!searchExpanded && searchResults.isNotEmpty() && currentSearchText.isNotBlank()) {
                        TextButton(onClick = { searchExpanded = true }) { Text("Show ${searchResults.size} results") }
                    }
                }
            }
            }

            if (!hasSourceFolder) Spacer(modifier = Modifier.height(16.dp))

            
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
                LaunchedEffect(listState) { viewModel.scrollToTop.collect { runCatching { listState.animateScrollToItem(0) } } }
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
                            // While the card itself is sliding (swipe or Like/Dislike button) the track
                            // change needs no extra effect; otherwise (next/previous) a gentle dip.
                            var flinging by remember { mutableStateOf(false) }
                            val transitionAlpha by animateFloatAsState(
                                targetValue = if (isTransitioning && !flinging) 0.75f else 1f,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "transition_fade"
                            )
                            val animationSpec = remember(visualSettings) { Motion.settle }
                            val animatedOffset = mainSwipeOffset.value
                            // Tint and border fade in/out instead of switching in one frame
                            val cardColor by animateColorAsState(
                                when (swipeDirection) {
                                    1 -> lerp(MaterialTheme.colorScheme.primaryContainer, LikeGreen, 0.35f)
                                    -1 -> lerp(MaterialTheme.colorScheme.primaryContainer, DislikeRed, 0.35f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }, tween(160), label = "mainCardTint"
                            )
                            val borderColor by animateColorAsState(
                                when (swipeDirection) {
                                    1 -> LikeGreen
                                    -1 -> DislikeRed
                                    else -> LikeGreen.copy(alpha = 0f)
                                }, tween(160), label = "mainCardBorder"
                            )
                            /**
                             * Like / Dislike from the buttons: the same motion as a swipe — the card
                             * glides out (right for Like, left for Dislike), the track is sorted and the
                             * next one starts, and the new card glides in from the other side.
                             */
                            fun flingAndSort(action: SortAction) {
                                if (flinging) return
                                val dir = if (action == SortAction.LIKE) 1f else -1f
                                val prevFile = file
                                scope.launch {
                                    flinging = true
                                    try {
                                        swipeDirection = dir.toInt()
                                        mainSwipeOffset.animateTo(dir * mainExitPx, Motion.exit())
                                        if (action == SortAction.DISLIKE) suppressMiniOnLeftSwipe = true
                                        try { viewModel.removeFromCurrentListByUri(prevFile.uri) } catch (_: Exception) {}
                                        viewModel.playNextAfterRemoval()
                                        viewModel.sortMusicFile(prevFile, action)
                                        swipeDirection = 0
                                        mainSwipeOffset.snapTo(-dir * mainExitPx * 0.35f)
                                        mainSwipeOffset.animateTo(0f, Motion.arrive)
                                    } finally {
                                        swipeDirection = 0
                                        flinging = false
                                    }
                                }
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
                            val panelAnimMs = Motion.panelMs()
                            AnimatedVisibility(
                                visible = isMainPlayerVisible,
                                enter = expandVertically(animationSpec = tween(panelAnimMs)) + fadeIn(animationSpec = tween(panelAnimMs)),
                                exit = shrinkVertically(animationSpec = tween(panelAnimMs)) + fadeOut(animationSpec = tween(panelAnimMs))
                            ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .graphicsLayer {
                                        translationX = animatedOffset
                                        scaleX = 1f + abs(animatedOffset) / 2000f
                                        scaleY = 1f + abs(animatedOffset) / 2000f
                                        alpha = transitionAlpha
                                    }
                                    .border(width = 3.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                                    .then(
                                        run {
                                            val isCurrentInPlaylist = currentPlaylistFiles.any { it.uri == file.uri }
                                            if (isCurrentInPlaylist) Modifier.pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = { },
                                                    onDragEnd = {
                                                        val current = mainSwipeOffset.value
                                                        val threshold = mainTriggerPx
                                                        val exit = if (current > 0) mainExitPx else -mainExitPx
                                                        if (abs(current) > threshold) {
                                                            val prevFile = file
                                                            scope.launch {
                                                                flinging = true
                                                                // Slide the card off (accelerating, bounded 80..450 ms so a slow
                                                                // Animation Speed can't stall the action), fire like/dislike, then
                                                                // let the next track slide in from the opposite side.
                                                                mainSwipeOffset.animateTo(exit, Motion.exit())
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
                                                                mainSwipeOffset.snapTo(-exit * 0.35f)
                                                                mainSwipeOffset.animateTo(0f, Motion.arrive)
                                                                flinging = false
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
                                                    val newValue = (mainSwipeOffset.value + x * 0.85f).coerceIn(-mainDragMaxPx, mainDragMaxPx)
                                                    scope.launch { mainSwipeOffset.snapTo(newValue) }
                                                    swipeDirection = when {
                                                        newValue > mainHintPx -> 1
                                                        newValue < -mainHintPx -> -1
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
                                                isPlaying = isPlaying,
                                                fileName = file.name,
                                                customWaveformColor = customWaveformColor,
                                                animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = Motion.waveformMs(),
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
                                                PlayPauseIcon(isPlaying, Modifier.size(26.dp))
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
                                                    onClick = { showUndoPicker = true },
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
                                                // Share (any app)
                                                Card(
                                                    onClick = { viewModel.shareCurrentTrack() },
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
                                                            contentDescription = "Share",
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
                                                            } else if (currentPlaylistFiles.any { it.uri == file.uri }) {
                                                                flingAndSort(SortAction.DISLIKE)
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
                                                        onClick = { showUndoPicker = true },
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
                                                            } else if (currentPlaylistFiles.any { it.uri == file.uri }) {
                                                                flingAndSort(SortAction.LIKE)
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
                                                    // Share (any app)
                                                    Card(
                                                        onClick = { viewModel.shareCurrentTrack() },
                                                        modifier = Modifier.size(38.dp),
                                                        shape = CircleShape,
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = Color(0xFF25D366)
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(19.dp))
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
                        } else if (isMainPlayerVisible && currentPlaylistFiles.isNotEmpty()) {
                            // Nothing loaded yet (fresh folder): "Show Main Player" used to do nothing.
                            // A slim card that starts the first track.
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    .clickable { viewModel.jumpTo(0) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play first track",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Nothing playing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("Tap to play ${currentPlaylistFiles.first().name}", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Local composable to render the playlist tabs header (previously inside the list)
                    @Composable
                    fun PlaylistTabsHeader() {
                        // Slides open/closed like the main player (was an instant jump)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isPlaylistsVisible,
                            enter = expandVertically(tween(Motion.panelMs())) + fadeIn(tween(Motion.panelMs())),
                            exit = shrinkVertically(tween(Motion.panelMs())) + fadeOut(tween(Motion.panelMs()))
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, end = 8.dp, top = 6.dp),
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
                    actionsToolbar?.invoke()
                    PlaylistTabsHeader()
                    // App messages and progress banners appear under the playlist buttons (were above the title bar)
                    Column { topMessageBanner(); progressBanners() }
                    // Search opens under the player, the tabs and the banners
                    SearchSection()
                    
                    // Only the playlist list scrolls
                    val renderHeadersInList = false
                    Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp),
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
                                
                                val animationSpec = remember(visualSettings) { Motion.settle }
                                
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
                                val panelAnimMs2 = Motion.panelMs()
                                val mainPlayerAlpha2 by animateFloatAsState(
                                    targetValue = if (isMainPlayerVisible) 1f else 0f,
                                    animationSpec = tween(panelAnimMs2),
                                    label = "mainPlayerAlpha2"
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = if (isMainPlayerVisible) 4.dp else 0.dp)
                                        .collapseHeight(mainPlayerAlpha2) // shrinks with the fade instead of jumping to 0 at the end
                                        .animateItem(placementSpec = Motion.placement)
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
                                                    val threshold = mainTriggerPx
                                                    val exit = if (current > 0) mainExitPx else -mainExitPx
                                                    if (abs(current) > threshold) {
                                                        val prevFile = file
                                                        scope.launch {
                                                            // 1) Slide out, 2) act, 3) next track slides in from the other side
                                                            mainSwipeOffset.animateTo(exit, Motion.exit())
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
                                                                // Like = sort AND advance (sorting alone left the removed track
                                                                // playing and desynced the index — the "track restarts" bug)
                                                                try {
                                                                    try { prevFile?.let { viewModel.removeFromCurrentListByUri(it.uri) } } catch (_: Exception) {}
                                                                    viewModel.playNextAfterRemoval()
                                                                    prevFile?.let { viewModel.sortMusicFile(it, SortAction.LIKE) }
                                                                } catch (e: Exception) {
                                                                    CrashLogger.log("Debug", "Error in swipe (main) like: ${e.message}")
                                                                }
                                                            }
                                                            mainSwipeOffset.snapTo(-exit * 0.35f)
                                                            mainSwipeOffset.animateTo(0f, Motion.arrive)
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
                                                val newValue = (mainSwipeOffset.value + x * 0.85f).coerceIn(-mainDragMaxPx, mainDragMaxPx)
                                                scope.launch { mainSwipeOffset.snapTo(newValue) }
                                                swipeDirection = when {
                                                    newValue > mainHintPx -> 1
                                                    newValue < -mainHintPx -> -1
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
                                                isPlaying = isPlaying,
                                                fileName = file.name, // Pass filename to display in waveform
                                                customWaveformColor = customWaveformColor,
                                                animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = Motion.waveformMs(),
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
            PlayPauseIcon(isPlaying, Modifier.size(22.dp))
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
                                                    onClick = { showUndoPicker = true },
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
                                            // Share button (any app)
                                                Card(
                                                    onClick = { viewModel.shareCurrentTrack() },
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
                                                            contentDescription = "Share",
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
                        // Empty list → friendly card (To Do included: an empty source no longer hides the app)
                        if (currentPlaylistFiles.isEmpty() && !(currentPlaylistTab == PlaylistTab.TODO && archivesOpen && archiveItems.isNotEmpty())) {
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
                                                PlaylistTab.TODO -> if (archiveItems.isNotEmpty()) "No tracks to sort · ${archiveItems.size} archive${if (archiveItems.size == 1) "" else "s"}" else "Nothing left to sort"
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
                                                PlaylistTab.TODO -> if (archiveItems.isNotEmpty()) "Open the Archives bar above to unpack them, or load another folder from Home." else "Liked and Rejected are in the tabs above. Load another folder from Home, or Rescan."
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
                            // The "Archives" drawer bar hides/shows all archive rows (remembered).
                            val archivesByFolder = if (currentPlaylistTab == PlaylistTab.TODO && archivesOpen)
                                archiveItems.groupBy { it.folder?.takeIf { f -> f.isNotBlank() } } else emptyMap()
                            // Create flat list with headers: [Drawer, Header, Archive, File, File, Header, ...]
                            buildList {
                                if (currentPlaylistTab == PlaylistTab.TODO && archiveItems.isNotEmpty()) add(PlaylistItem.ArchivesToggleItem(archiveItems.size))
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
                                    is PlaylistItem.ArchivesToggleItem -> "archives_toggle"
                                }
                            }
                        ) { index, playlistItem ->
                            when (playlistItem) {
                                is PlaylistItem.ArchivesToggleItem -> {
                                    val chevron by animateFloatAsState(if (archivesOpen) 180f else 0f, tween(Motion.panelMs() / 2), label = "archChevron")
                                    Row(
                                        modifier = Modifier
                                            .animateItem(fadeInSpec = Motion.rowFadeIn, placementSpec = Motion.placement, fadeOutSpec = Motion.rowFadeOut)
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                            .clickable { setArchivesOpen(!archivesOpen) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Archive, null, tint = Color(0xFFF5A524), modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Archives · ${playlistItem.count}", style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(if (archivesOpen) "Shown in their folders · tap to hide" else "Hidden · tap to show",
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ExpandMore, if (archivesOpen) "Hide archives" else "Show archives",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.graphicsLayer { rotationZ = chevron })
                                    }
                                }
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
                                        // Compact folder divider: one quiet line (last path part, ellipsized),
                                        // a small count pill and the select-all box. Tap the name to see the
                                        // full folder name and details.
                                        val archCount = archiveItems.count { it.folder == subName }
                                        var nameOpen by rememberSaveable(subName) { mutableStateOf(false) }
                                        val shortName = subName.trimEnd('/').substringAfterLast('/').ifBlank { subName }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateContentSize(tween(Motion.rowExpandMs() / 2))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 14.dp, end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { nameOpen = !nameOpen }
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Folder, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = shortName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "${subIndices.size}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(50))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                    Icon(
                                                        Icons.Default.ExpandMore,
                                                        contentDescription = if (nameOpen) "Hide folder name" else "Show full folder name",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .graphicsLayer { rotationZ = if (nameOpen) 180f else 0f }
                                                    )
                                                }
                                                // Select/deselect all files in this subfolder for bulk like/dislike
                                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                                    Checkbox(
                                                        checked = allSubSelected,
                                                        onCheckedChange = { viewModel.toggleSubfolderSelection(subName) },
                                                        modifier = Modifier.scale(0.85f)
                                                    )
                                                }
                                            }
                                            if (nameOpen) {
                                                val sampleUri = subIndices.firstOrNull()?.let { currentPlaylistFiles.getOrNull(it)?.uri }
                                                    ?: archiveItems.firstOrNull { it.folder == subName }?.uri
                                                val loc = remember(sampleUri, savedDrives) { folderLocation(sampleUri, savedDrives) }
                                                Column(Modifier.padding(start = 34.dp, end = 16.dp, bottom = 8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            when (loc?.kind) {
                                                                "phone" -> Icons.Default.PhoneAndroid
                                                                "external" -> Icons.Default.SdCard
                                                                "usb" -> Icons.Default.Usb
                                                                "pi" -> Icons.Default.Dns
                                                                "cloud" -> Icons.Default.Cloud
                                                                else -> Icons.Default.Lan
                                                            },
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(Modifier.width(5.dp))
                                                        Text(
                                                            loc?.place ?: "Folder",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Text(
                                                        text = (loc?.parts?.takeIf { it.isNotEmpty() } ?: listOf(subName)).joinToString("  ›  "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                    Text(
                                                        text = "${subIndices.size} track${if (subIndices.size == 1) "" else "s"}" +
                                                            if (archCount > 0 && currentPlaylistTab == PlaylistTab.TODO) " · $archCount archive${if (archCount == 1) "" else "s"}" else "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                is PlaylistItem.ArchiveRowItem -> {
                                    val a = playlistItem.archive
                                    val ro = a.driveId != null && savedDrives.any {
                                        it.id == a.driveId && it.type == com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER && it.writeShare.isBlank()
                                    }
                                    ArchiveRow(
                                        modifier = Modifier.animateItem(fadeInSpec = Motion.rowFadeIn, placementSpec = Motion.placement, fadeOutSpec = Motion.rowFadeOut),
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
                                animationSpec = tween(durationMillis = Motion.rowExpandMs(), easing = FastOutSlowInEasing),
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
                                animationSpec = tween(Motion.removeMs()),
                                label = "rowAlpha"
                            )
                            val removeSlide by animateFloatAsState(
                                targetValue = if (isRemoving) 1f else 0f,
                                animationSpec = tween(Motion.removeMs(), easing = FastOutLinearInEasing),
                                label = "removeSlide"
                            )
                            // Remove down-to-up/alpha enter; we'll animate only height on promotion
                            // Optimized thresholds for reliable swiping
                            val swipeDensity = androidx.compose.ui.platform.LocalDensity.current
                            val swipeIndicatorThreshold = with(swipeDensity) { Motion.swipeIndicator.toPx() }  // Show indicator
                            val swipeTriggerThreshold = with(swipeDensity) { Motion.swipeTrigger.toPx() }      // Swipes past this sort
                            val swipeMaxOffset = with(swipeDensity) { Motion.swipeMax.toPx() }                 // Max drag distance
                            // Swiped rows leave by the full screen width (520 px didn't clear wide/UHD screens)
                            val rowExitPx = with(swipeDensity) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() } + 40f
                            val swipeResistance = 0.7f        // More resistance for better control
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Apply placement animation at the ITEM ROOT so all children (overlay + card) glide together
                                    .animateItem(fadeInSpec = Motion.rowFadeIn, placementSpec = Motion.placement, fadeOutSpec = Motion.rowFadeOut)
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
                                            // Removed rows (sorted / "Reject listened") slide left as they fade
                                            translationX = rowSwipeOffset.value - removeSlide * size.width * 0.35f
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
                                                                                Motion.settle
                                                                            )
                                                                        }
                                                                    } else {
                                                                        // Proceed: swipe-out → remove → reflow → next (if active) → sort
                                                                        val exit = -rowExitPx
                                                                        scope.launch {
                                                                            isRowDismissed = true
                                                                            if (isActiveNow) suppressMiniOnLeftSwipe = true
                                                                            launch { rowSwipeOffset.animateTo(exit, Motion.exit()) }
                                                                            delay(Motion.exitMs())
                                                                            try { viewModel.removeFromCurrentListByUri(item.uri) } catch (_: Exception) {}
                                                                    // Hand off the rest to ViewModel so coroutine isn't cancelled when this row is removed
                                                                    viewModel.handleRowSwipeAfterRemoval(fileToSort, SortAction.DISLIKE, isActiveNow)
                                                                        }
                                                                    }
                                                                } else {
                                                                    // Right swipe (LIKE) - no destination guard needed
                                                                    val exit = rowExitPx
                                                                    scope.launch {
                                                                        isRowDismissed = true
                                                                        launch { rowSwipeOffset.animateTo(exit, Motion.exit()) }
                                                                        delay(Motion.exitMs())
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
                                                                        Motion.settle
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
                                                    PlayPauseIcon(isPlaying, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
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
                                                        if (currentPlaylistTab == PlaylistTab.TODO) alreadySorted[item.uri]?.let { was ->
                                                            AlreadySortedChip(was)
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
                                                val wasSorted = if (currentPlaylistTab == PlaylistTab.TODO) alreadySorted[item.uri] else null
                                                val trackInfoParts = listOfNotNull(trackDurationText.ifEmpty { null }, trackBitrateText.ifEmpty { null }, trackSizeText.ifEmpty { null })
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Track info above waveform
                                                    if (trackInfoParts.isNotEmpty() || wasSorted != null) {
                                                        Text(
                                                            text = androidx.compose.ui.text.buildAnnotatedString {
                                                                if (wasSorted != null) {
                                                                    pushStyle(androidx.compose.ui.text.SpanStyle(
                                                                        color = if (wasSorted == 'L') Color(0xFF22C55E) else Color(0xFFEF4444),
                                                                        fontWeight = FontWeight.Bold))
                                                                    append(if (wasSorted == 'L') "ALREADY LIKED" else "ALREADY REJECTED")
                                                                    pop()
                                                                    if (trackInfoParts.isNotEmpty()) append(" | ")
                                                                }
                                                                append(trackInfoParts.joinToString(" | "))
                                                            },
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
                                                        isPlaying = isPlaying,
                                                        fileName = item.name,
                                                        opacity = 0.7f,
                                                        customWaveformColor = customWaveformColor,
                                                        animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = Motion.waveformMs(),
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
                                                                viewModel.shareCurrentTrack()
                                                            } catch (e: Exception) {
                                                                CrashLogger.log("Debug", "Error in share button: ${e.message}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(adaptiveButtonSize)
                                                    ) {
                                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF2196F3), modifier = Modifier.size(adaptiveIconSize))
                                                    }
                                                }
                                                // SpEk (spectrogram) spanning both columns, under time + share
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .width(adaptiveButtonSize * 2 + 2.dp)
                                                        .height(adaptiveButtonSize * 0.62f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFFFB6C1).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .clickable { showSpectrogram = true }
                                                ) {
                                                    Text(
                                                        "SPEK",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = (adaptiveButtonSize.value * 0.32f).sp
                                                        ),
                                                        color = if (isMonochrome) monoColor else Color(0xFFFFB6C1)
                                                    )
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
                                                    
                                                    if (currentPlaylistTab == PlaylistTab.TODO) alreadySorted[item.uri]?.let { was ->
                                                        AlreadySortedChip(was)
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
                    // First track playing on first open: keep the list at the top so its row is fully visible
                    LaunchedEffect(currentTrackIndex, isFirstOpen, currentPlayingFile?.uri) {
                        if (isFirstOpen && !initialAutoScrolled && isFileInCurrentPlaylist && currentTrackIndex == 0 && currentPlayingFile != null) {
                            try {
                                // Wait until LazyColumn has laid out at least one item to avoid no-op scrolls
                                var attempts = 0
                                while (listState.layoutInfo.visibleItemsInfo.isEmpty() && attempts < 20) {
                                    delay(25)
                                    attempts++
                                }
                                // The first track's row sits right under its folder header at the top of the
                                // list. (This used to scroll 500 px down to reveal a main player that lived
                                // inside the list; that now hid the playing row under the header.)
                                if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                    listState.animateScrollToItem(0)
                                }
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
                                                        miniSwipeOffset.animateTo(exit, Motion.exit())
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
                                            PlayPauseIcon(isPlaying, Modifier.size(16.dp))
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
                                        isPlaying = isPlaying,
                                        fileName = file.name,
                                        customWaveformColor = customWaveformColor,
                                        animationType = visualSettings.waveformAnimationType,
                                                        animationDurationMs = Motion.waveformMs(),
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
