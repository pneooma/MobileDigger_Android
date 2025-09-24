package com.example.mobiledigger.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.model.SortResult
import com.example.mobiledigger.file.FileManager
import com.example.mobiledigger.file.PreferencesManager
import com.example.mobiledigger.ui.theme.ThemeManager
import com.example.mobiledigger.ui.theme.VisualSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Job
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.util.CacheManager
import com.example.mobiledigger.util.ResourceManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PlaylistTab {
    TODO, LIKED, REJECTED
}

class MusicViewModel(application: Application) : AndroidViewModel(application), AudioManager.PlaybackCompletionListener {
    
    val audioManager = AudioManager(application)
    private val fileManager = FileManager(application)
    val preferences = PreferencesManager(application)
    val themeManager = ThemeManager(application)
    val visualSettingsManager = VisualSettingsManager(application)
    private val context = application.applicationContext
    
    // Mutex to prevent concurrent file loading operations that cause memory pressure
    private val fileLoadingMutex = Mutex()
    
    // Broadcast receiver for notification actions
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.ACTION_PLAY_PAUSE -> playPause()
                MusicService.ACTION_NEXT -> next()
                MusicService.ACTION_PREVIOUS -> previous()
                MusicService.ACTION_LIKE -> sortCurrentFile(SortAction.LIKE)
                MusicService.ACTION_DISLIKE -> sortCurrentFile(SortAction.DISLIKE)
            }
        }
    }
    
    private val _musicFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val musicFiles: StateFlow<List<MusicFile>> = _musicFiles.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    // Track the actually playing file separately from playlist navigation
    private val _currentPlayingFile = MutableStateFlow<MusicFile?>(null)
    val currentPlayingFile: StateFlow<MusicFile?> = _currentPlayingFile.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Song transition state for smooth animations
    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Audio preloading for smooth transitions
    private var audioPreloadJob: kotlinx.coroutines.Job? = null
    private var preloadedFile: MusicFile? = null
    
    private val _sortResults = MutableStateFlow<List<SortResult>>(emptyList())
    val sortResults: StateFlow<List<SortResult>> = _sortResults.asStateFlow()

    private val _destinationPath = MutableStateFlow("")
    val destinationPath: StateFlow<String> = _destinationPath.asStateFlow()
    
    private val _destinationFolder = MutableStateFlow<androidx.documentfile.provider.DocumentFile?>(null)
    val destinationFolder: StateFlow<androidx.documentfile.provider.DocumentFile?> = _destinationFolder.asStateFlow()
    
    // Delete rejected files prompt
    private val _showDeleteRejectedPrompt = MutableStateFlow(false)
    val showDeleteRejectedPrompt: StateFlow<Boolean> = _showDeleteRejectedPrompt.asStateFlow()
    
    // Progress tracking for file deletion
    private val _deletionProgress = MutableStateFlow(0f)
    val deletionProgress: StateFlow<Float> = _deletionProgress.asStateFlow()
    
    private val _isDeletingFiles = MutableStateFlow(false)
    val isDeletingFiles: StateFlow<Boolean> = _isDeletingFiles.asStateFlow()

    // StateFlow for ZIP creation progress tracking (Int 0-100 to match existing implementation)
    private val _zipProgress = MutableStateFlow(0)
    val zipProgress: StateFlow<Int> = _zipProgress.asStateFlow()

    private val _zipInProgress = MutableStateFlow(false)
    val zipInProgress: StateFlow<Boolean> = _zipInProgress.asStateFlow()
    
    // Tabbed playlist states
    private val _currentPlaylistTab = MutableStateFlow(PlaylistTab.TODO)
    val currentPlaylistTab: StateFlow<PlaylistTab> = _currentPlaylistTab.asStateFlow()
    
    private val _likedFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val likedFiles: StateFlow<List<MusicFile>> = _likedFiles.asStateFlow()
    
    private val _rejectedFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val rejectedFiles: StateFlow<List<MusicFile>> = _rejectedFiles.asStateFlow()
    
    // Current file for notification
    private var currentFile: MusicFile? = null
    
    // State for search results
    private val _searchResults = MutableStateFlow<List<MusicFile>>(emptyList())
    val searchResults: StateFlow<List<MusicFile>> = _searchResults.asStateFlow()
    
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()
    
    // State for subfolders
    private val _subfolders = MutableStateFlow<List<Uri>>(emptyList())
    val subfolders: StateFlow<List<Uri>> = _subfolders.asStateFlow()
    
    // Computed StateFlow for current playlist files - reactive to tab changes
    val currentPlaylistFiles: StateFlow<List<MusicFile>> = combine(
        _musicFiles,
        _likedFiles,
        _rejectedFiles,
        _currentPlaylistTab
    ) { musicFiles, likedFiles, rejectedFiles, currentTab ->
        when (currentTab) {
            PlaylistTab.TODO -> musicFiles
            PlaylistTab.LIKED -> likedFiles
            PlaylistTab.REJECTED -> rejectedFiles
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Multi-selection state
    private val _selectedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndices: StateFlow<Set<Int>> = _selectedIndices.asStateFlow()
    
    private val _isMultiSelectionMode = MutableStateFlow(false)
    val isMultiSelectionMode: StateFlow<Boolean> = _isMultiSelectionMode.asStateFlow()
    
    private val _lastSortedAction = MutableStateFlow<SortAction?>(null)
    val lastSortedAction: StateFlow<SortAction?> = _lastSortedAction.asStateFlow()
    
    // Subfolder management for liked files
    private val _subfolderHistory = MutableStateFlow<List<String>>(emptyList())
    val subfolderHistory: StateFlow<List<String>> = _subfolderHistory.asStateFlow()
    
    private val _availableSubfolders = MutableStateFlow<List<String>>(emptyList())
    val availableSubfolders: StateFlow<List<String>> = _availableSubfolders.asStateFlow()
    
    private val _subfolderFileCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val subfolderFileCounts: StateFlow<Map<String, Int>> = _subfolderFileCounts.asStateFlow()
    
    private val _showSubfolderDialog = MutableStateFlow(false)
    val showSubfolderDialog: StateFlow<Boolean> = _showSubfolderDialog.asStateFlow()
    
    private val _newSubfolderName = MutableStateFlow("")
    val newSubfolderName: StateFlow<String> = _newSubfolderName.asStateFlow()
    
    private val _showSubfolderManagementDialog = MutableStateFlow(false)
    val showSubfolderManagementDialog: StateFlow<Boolean> = _showSubfolderManagementDialog.asStateFlow()

    // Recent source folders (URIs as strings), max 10
    private val _recentSourceUris = MutableStateFlow<List<String>>(emptyList())
    val recentSourceUris: StateFlow<List<String>> = _recentSourceUris.asStateFlow()
    
    init {
        try {
            // Initialize crash logger
            CrashLogger.setDestinationFolder(application, null) // Will be set when destination is selected
            CrashLogger.log("MusicViewModel", "=== APP STARTED ===")
            
            // Reset session counters on app start
            preferences.resetSessionCounters()
            CrashLogger.log("MusicViewModel", "Session counters reset")
            
            // Initialize audio manager first
            audioManager.initialize()
            audioManager.setPlaybackCompletionListener(this) // Register listener
            startPositionUpdates()
            
            // Load playlists at startup
            loadLikedFiles()
            loadRejectedFiles()
            
            // Initialize subfolder management
            loadSubfolderHistory()
            updateSubfolderInfo()
            
            // Clear caches periodically to prevent memory buildup
            startPeriodicCacheClearing()

            // Load recent sources from preferences
            loadRecentSources()
            
            // Start service and register receiver in background to avoid blocking
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    ensureMusicServiceStarted()
                    withContext(Dispatchers.Main) {
                        registerNotificationReceiver()
                    }
                    // Force notification update after service starts
                    delay(1000) // Wait 1 second for service to initialize
                    updateNotification()
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Failed to start service or register receiver", e)
                }
            }
            // Restore previously chosen folders if available
            preferences.getDestinationRootUri()?.let { saved ->
                runCatching {
                    val uri = Uri.parse(saved)
                    fileManager.setDestinationFolder(uri)
                    
                    // Check if the destination folder is actually accessible
                    val destinationFolder = fileManager.getDestinationFolder()
                    if (destinationFolder != null && destinationFolder.exists() && destinationFolder.canWrite()) {
                        _destinationPath.value = fileManager.getDestinationPath()
                        _destinationFolder.value = destinationFolder
                        // Set crash logger destination
                        CrashLogger.setDestinationFolder(application, uri)
                        CrashLogger.log("MusicViewModel", "Restored destination folder: ${fileManager.getDestinationPath()}")
                        android.util.Log.d("MusicViewModel", "Restored destination folder: ${fileManager.getDestinationPath()}")
                    } else {
                        // Clear invalid destination folder
                        CrashLogger.log("MusicViewModel", "Destination folder is not accessible, clearing it")
                        preferences.setDestinationRootUri(null)
                        _destinationPath.value = ""
                        _destinationFolder.value = null
                    }
                }
            }
            
            // Restore source folder reference (but don't auto-scan)
            preferences.getSourceRootUri()?.let { saved ->
                runCatching {
                    val uri = Uri.parse(saved)
                    android.util.Log.d("MusicViewModel", "Source folder available: $saved")
                    CrashLogger.log("MusicViewModel", "Source folder available: $saved")
                    
                    // Just restore the folder reference without scanning
                    fileManager.setSelectedFolder(uri)
                    android.util.Log.d("MusicViewModel", "Source folder restored (use Rescan to load files)")
                }
            }
            CrashLogger.log("MusicViewModel", "Initialized successfully")
            
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to initialize", e)
            _errorMessage.value = "Failed to initialize audio: ${e.message}"
        }
    }

    private fun loadRecentSources() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("recent_sources", Context.MODE_PRIVATE)
            val csv = prefs.getString("uris", "") ?: ""
            val list = if (csv.isNotBlank()) csv.split("|").filter { it.isNotBlank() } else emptyList()
            _recentSourceUris.value = list.take(10)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun saveRecentSources() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("recent_sources", Context.MODE_PRIVATE)
            prefs.edit().putString("uris", _recentSourceUris.value.take(10).joinToString("|")) .apply()
        } catch (_: Exception) { /* ignore */ }
    }

    private fun addRecentSource(uri: Uri) {
        val s = uri.toString()
        val current = _recentSourceUris.value.toMutableList()
        current.remove(s)
        current.add(0, s)
        _recentSourceUris.value = current.take(10)
        saveRecentSources()
    }

    // Set source folder preference only (do not rescan yet)
    fun setSourceFolderPreference(uri: Uri) {
        try {
            preferences.setSourceRootUri(uri.toString())
            addRecentSource(uri)
            _errorMessage.value = "Source set. Tap Rescan to load files."
        } catch (e: Exception) {
            handleError("setSourceFolderPreference", e)
        }
    }
    
    fun selectFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                // Start fresh like a new session
                _sortResults.value = emptyList()
                stopPlayback()
                _currentIndex.value = 0
                _musicFiles.value = emptyList()
                CrashLogger.log("MusicViewModel", "Starting folder selection: $uri")
                
                fileManager.setSelectedFolder(uri)
                addRecentSource(uri)
                CrashLogger.log("MusicViewModel", "FileManager.setSelectedFolder completed")
                
                // Save the source folder URI for future app starts
                preferences.setSourceRootUri(uri.toString())
                android.util.Log.d("MusicViewModel", "Saved source folder URI: $uri")
                
                val files = try {
                    fileManager.loadMusicFiles()
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error loading music files", e)
                    _errorMessage.value = "Error loading music files: ${e.message}"
                    _isLoading.value = false
                    return@launch
                }
                
                CrashLogger.log("MusicViewModel", "FileManager.loadMusicFiles completed with ${files.size} files")
                
                _musicFiles.value = files.map { it.copy(sourcePlaylist = PlaylistTab.TODO) }
                CrashLogger.log("MusicViewModel", "Updated musicFiles state")
                
                // Skip duration extraction to improve performance
                
                
                // Reload playlists when folder is selected
                loadLikedFiles()
                loadRejectedFiles()
                
                // Safety: warn if destination is inside the source
                if (fileManager.isDestinationInsideSource()) {
                    _errorMessage.value = "Warning: destination folder is inside source. Please choose a different destination from the menu."
                } else if (files.isNotEmpty()) {
                    _currentIndex.value = 0
                    // Automatically load and start playing the first song
                    loadCurrentFile()
                    val destInfo = if (fileManager.getDestinationFolder() != null) {
                        " Destination: ${fileManager.getDestinationPath()}"
                    } else {
                        " Please select destination folder from menu."
                    }
                    _errorMessage.value = "Loaded ${files.size} tracks. Now playing first track.$destInfo"
                } else {
                    _errorMessage.value = "No music files found in selected folder."
                }
                
                // Load subfolders if a source folder is selected
                preferences.getSourceRootUri()?.let { uriString ->
                    val sourceUri = Uri.parse(uriString)
                    loadSubfolders(sourceUri)
                }
                
                CrashLogger.log("MusicViewModel", "selectFolder completed successfully")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "selectFolder failed", e)
                _errorMessage.value = "Error loading folder: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    


    fun selectDestinationFolder(uri: Uri) {
        CrashLogger.log("MusicViewModel", "selectDestinationFolder called with URI: $uri")
        viewModelScope.launch {
            try {
                fileManager.setDestinationFolder(uri)
                _destinationPath.value = fileManager.getDestinationPath()
                _destinationFolder.value = fileManager.getDestinationFolder()
                // Remember user's like/dislike root
                preferences.setDestinationRootUri(uri.toString())
                CrashLogger.log("MusicViewModel", "Saved destination folder URI: $uri")
                // Set crash logger destination
                CrashLogger.setDestinationFolder(getApplication(), uri)
                CrashLogger.log("MusicViewModel", "Set destination folder: ${fileManager.getDestinationPath()}")
                
                // Reload playlists when destination is selected
                loadLikedFiles()
                loadRejectedFiles()

                if (fileManager.isDestinationInsideSource()) {
                    _errorMessage.value = "Destination is inside the source folder; this may cause issues. Pick a parent folder as destination."
                }
                
                // Test if destination is actually accessible
                val destFolder = fileManager.getDestinationFolder()
                CrashLogger.log("MusicViewModel", "Destination folder object: $destFolder")
                CrashLogger.log("MusicViewModel", "Destination folder exists: ${destFolder?.exists()}")
                CrashLogger.log("MusicViewModel", "Destination folder canRead: ${destFolder?.canRead()}")
                CrashLogger.log("MusicViewModel", "Destination folder canWrite: ${destFolder?.canWrite()}")
                
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error setting destination", e)
                _errorMessage.value = "Error setting destination: ${e.message}"
            }
        }
    }




    



    
    fun playPause() {
        val currentFiles = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        if (currentFiles.isEmpty()) {
            _errorMessage.value = "No files in current playlist to play"
            return
        }
        
        if (_isPlaying.value) {
            audioManager.pause()
            _isPlaying.value = false
        } else {
            audioManager.resume()
            _isPlaying.value = true
        }
        updateNotification()
    }
    
    fun next() {
        viewModelScope.launch {
            val currentFiles = when (_currentPlaylistTab.value) {
                PlaylistTab.TODO -> _musicFiles.value
                PlaylistTab.LIKED -> _likedFiles.value
                PlaylistTab.REJECTED -> _rejectedFiles.value
            }
            if (currentFiles.isEmpty()) {
                _errorMessage.value = "No files in current playlist to navigate"
                return@launch
            }
            
            // Start smooth transition
            _isTransitioning.value = true
            
            // Quick fade out for smooth transition (120Hz optimized)
            if (_isPlaying.value) {
                _isPlaying.value = false
                try {
                    audioManager.pause()
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error pausing during transition", e)
                }
            }
            
            delay(50) // Brief pause for 120Hz smooth animation
            
            val nextIndex = (_currentIndex.value + 1) % currentFiles.size
            _currentIndex.value = nextIndex
            loadCurrentFile()
            updateNotification()
            
            delay(100) // Allow new file to load
            _isTransitioning.value = false
        }
    }
    
    fun previous() {
        viewModelScope.launch {
            val currentFiles = when (_currentPlaylistTab.value) {
                PlaylistTab.TODO -> _musicFiles.value
                PlaylistTab.LIKED -> _likedFiles.value
                PlaylistTab.REJECTED -> _rejectedFiles.value
            }
            if (currentFiles.isEmpty()) {
                _errorMessage.value = "No files in current playlist to navigate"
                return@launch
            }
            
            // Start smooth transition
            _isTransitioning.value = true
            
            // Quick fade out for smooth transition (120Hz optimized)
            if (_isPlaying.value) {
                _isPlaying.value = false
                try {
                    audioManager.pause()
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error pausing during transition", e)
                }
            }
            
            delay(50) // Brief pause for 120Hz smooth animation
            
            val prevIndex = if (_currentIndex.value > 0) {
                _currentIndex.value - 1
            } else {
                currentFiles.size - 1
            }
            _currentIndex.value = prevIndex
            loadCurrentFile()
            updateNotification()
            
            delay(100) // Allow new file to load
            _isTransitioning.value = false
        }
    }
    
    fun playFile(file: MusicFile) {
        try {
            CrashLogger.log("MusicViewModel", "playFile called for: ${file.name} from playlist: ${file.sourcePlaylist}")
            
            // If the file is from a different playlist, switch to that playlist first
            if (file.sourcePlaylist != _currentPlaylistTab.value) {
                CrashLogger.log("MusicViewModel", "Switching from ${_currentPlaylistTab.value} to ${file.sourcePlaylist}")
                switchPlaylistTab(file.sourcePlaylist)
                
                viewModelScope.launch {
                    try {
                        delay(100) // Increased delay to ensure playlist switch is complete
                        val updatedFiles = when (_currentPlaylistTab.value) {
                            PlaylistTab.TODO -> _musicFiles.value
                            PlaylistTab.LIKED -> _likedFiles.value
                            PlaylistTab.REJECTED -> _rejectedFiles.value
                        }
                        
                        CrashLogger.log("MusicViewModel", "Updated files count: ${updatedFiles.size}")
                        val index = updatedFiles.indexOfFirst { it.uri == file.uri }
                        
                        if (index >= 0) {
                            CrashLogger.log("MusicViewModel", "Found file at index $index, loading...")
                            _currentIndex.value = index
                            loadCurrentFile()
                        } else {
                            CrashLogger.log("MusicViewModel", "File not found in target playlist after switching")
                            _errorMessage.value = "Could not find file in the target playlist after switching."
                        }
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "Error in playFile coroutine", e)
                        _errorMessage.value = "Error playing file from search: ${e.message}"
                    }
                }
            } else {
                // If the file is in the current playlist, proceed as before
                val currentFiles = when (_currentPlaylistTab.value) {
                    PlaylistTab.TODO -> _musicFiles.value
                    PlaylistTab.LIKED -> _likedFiles.value
                    PlaylistTab.REJECTED -> _rejectedFiles.value
                }
                
                CrashLogger.log("MusicViewModel", "Current files count: ${currentFiles.size}")
                val index = currentFiles.indexOfFirst { it.uri == file.uri }
                
                if (index >= 0) {
                    CrashLogger.log("MusicViewModel", "Found file at index $index in current playlist")
                    _currentIndex.value = index
                    loadCurrentFile()
                } else {
                    CrashLogger.log("MusicViewModel", "File not found in current playlist")
                    _errorMessage.value = "File not found in current playlist."
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Exception in playFile", e)
            _errorMessage.value = "Error playing file from search: ${e.message}"
        }
    }
    
    fun togglePlayPause() {
        if (_isPlaying.value) {
            audioManager.pause()
            _isPlaying.value = false
        } else {
            audioManager.resume()
            _isPlaying.value = true
        }
        updateNotification()
    }
    
    fun likeFile(file: MusicFile) {
        val updatedLikedFiles = _likedFiles.value.toMutableList()
        if (!updatedLikedFiles.any { it.uri == file.uri }) {
            updatedLikedFiles.add(file.copy(sourcePlaylist = PlaylistTab.LIKED))
            _likedFiles.value = updatedLikedFiles
            // Save to preferences
            val prefs = context.getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putStringSet("liked_files", updatedLikedFiles.map { it.uri.toString() }.toSet()).apply()
        }
    }
    
    fun dislikeFile(file: MusicFile) {
        val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
        if (!updatedRejectedFiles.any { it.uri == file.uri }) {
            updatedRejectedFiles.add(file.copy(sourcePlaylist = PlaylistTab.REJECTED))
            _rejectedFiles.value = updatedRejectedFiles
            // Save to preferences
            val prefs = context.getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putStringSet("rejected_files", updatedRejectedFiles.map { it.uri.toString() }.toSet()).apply()
        }
    }

    fun jumpTo(index: Int) {
        val files = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        if (files.isEmpty()) {
            _errorMessage.value = "No files in current playlist to jump to"
            return
        }
        if (index !in files.indices) {
            _errorMessage.value = "Invalid index: $index (playlist has ${files.size} files)"
            return
        }
        _currentIndex.value = index
        loadCurrentFile()
    }
    
    fun seekTo(position: Long) {
        audioManager.seekTo(position)
    }
    
    
    fun sortCurrentFile(action: SortAction) {
        val fileToSort = currentFile // Use the currently playing file
        if (fileToSort == null) {
            _errorMessage.value = "No current file selected to sort"
            return
        }
        sortMusicFile(fileToSort, action)
    }

    private fun sortFileAtIndex(index: Int, action: SortAction) {
        // This function will still be used for multi-selection where a specific index in the current view is sorted.
        // It should get the MusicFile from the current view's list.
        val files = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        if (index !in files.indices) {
            CrashLogger.log("MusicViewModel", "Invalid index for sorting: $index")
            _errorMessage.value = "Invalid file selected for sorting."
            return
        }
        val fileToSort = files[index]
        sortMusicFile(fileToSort, action) // Delegate to the new function
    }

    private fun sortMusicFile(file: MusicFile, action: SortAction) {
        if (!fileManager.isDestinationSelected()) {
            _errorMessage.value = "Please select a destination folder from the 'Actions' menu before sorting." // More specific message
            return
        }
        
        CrashLogger.log("MusicViewModel", "Sorting file: ${file.name} with action $action, source: ${file.sourcePlaylist}")
        
        viewModelScope.launch {
            try {
                val success = fileManager.sortFile(file, action)
                if (success) {
                    handleSuccessfulSort(file, action)
                } else {
                    _errorMessage.value = "Failed to sort file. Please check folder permissions."
                }
            } catch (e: Exception) {
                handleError("sortMusicFile", e)
            }
        }
    }

    private fun handleSuccessfulSort(sortedFile: MusicFile, action: SortAction) {
        try {
            when (action) {
                SortAction.LIKE -> preferences.incrementLiked()
                SortAction.DISLIKE -> preferences.incrementRefused()
                else -> {}
            }
        } catch (_: Exception) {
            // Log this but don't stop the main flow
            CrashLogger.log("MusicViewModel", "Error incrementing preferences for sort action $action")
        }

        val sortResult = SortResult(sortedFile, action)
        _sortResults.value = _sortResults.value + sortResult
        
        _lastSortedAction.value = action // Set the last sorted action
        viewModelScope.launch {
            delay(300) // Short delay to allow animation to play
            _lastSortedAction.value = null // Reset after animation
        }
        
        // Determine which list the file came from and remove it
        when (sortedFile.sourcePlaylist) {
            PlaylistTab.TODO -> {
                val updatedFiles = _musicFiles.value.toMutableList()
                val indexToRemove = updatedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedFiles.removeAt(indexToRemove)
                    _musicFiles.value = updatedFiles

                    // Adjust current index and playback if the sorted file was the current one
                    if (sortedFile.uri == currentFile?.uri) {
                        stopPlayback()
                        // Keep the same index position, but adjust if we're at the end
                        if (_currentIndex.value >= updatedFiles.size) {
                            _currentIndex.value = updatedFiles.size - 1 // Go to last file if we're past the end
                        }
                        if (updatedFiles.isNotEmpty()) {
                            loadCurrentFile() // Load the file at the current index
                        } else {
                            _errorMessage.value = "Well done! Now select another folder!"
                        }
                    } else if (indexToRemove < _currentIndex.value) {
                        _currentIndex.value = _currentIndex.value - 1 // Shift index if file before it was removed
                    }
                }
            }
            PlaylistTab.LIKED -> {
                val updatedLikedFiles = _likedFiles.value.toMutableList()
                val indexToRemove = updatedLikedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedLikedFiles.removeAt(indexToRemove)
                    _likedFiles.value = updatedLikedFiles
                    
                    // Adjust current index if currently playing from this list
                    if (sortedFile.uri == currentFile?.uri && _currentPlaylistTab.value == PlaylistTab.LIKED) {
                        stopPlayback()
                        // Keep the same index position, but adjust if we're at the end
                        if (_currentIndex.value >= updatedLikedFiles.size) {
                            _currentIndex.value = updatedLikedFiles.size - 1 // Go to last file if we're past the end
                        }
                        if (updatedLikedFiles.isNotEmpty()) {
                            loadCurrentFile()
                        } else {
                            _errorMessage.value = "No liked files left."
                        }
                    } else if (indexToRemove < _currentIndex.value && _currentPlaylistTab.value == PlaylistTab.LIKED) {
                        _currentIndex.value = _currentIndex.value - 1
                    }
                }
            }
            PlaylistTab.REJECTED -> {
                val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
                val indexToRemove = updatedRejectedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedRejectedFiles.removeAt(indexToRemove)
                    _rejectedFiles.value = updatedRejectedFiles
                    
                    // Adjust current index if currently playing from this list
                    if (sortedFile.uri == currentFile?.uri && _currentPlaylistTab.value == PlaylistTab.REJECTED) {
                        stopPlayback()
                        // Keep the same index position, but adjust if we're at the end
                        if (_currentIndex.value >= updatedRejectedFiles.size) {
                            _currentIndex.value = updatedRejectedFiles.size - 1 // Go to last file if we're past the end
                        }
                        if (updatedRejectedFiles.isNotEmpty()) {
                            loadCurrentFile()
                        } else {
                            _errorMessage.value = "No rejected files left."
                        }
                    } else if (indexToRemove < _currentIndex.value && _currentPlaylistTab.value == PlaylistTab.REJECTED) {
                        _currentIndex.value = _currentIndex.value - 1
                    }
                }
            }
        }
        
        // Reload playlists after sorting immediately to ensure UI consistency
        refreshAllPlaylists()
        
        _errorMessage.value = if (action == SortAction.LIKE) "Moved to 'Liked'" else "Moved to 'Rejected'"
    }

    fun sortAtIndex(index: Int, action: SortAction) {
        // This function now delegates to sortMusicFile
        val files = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        if (index in files.indices) {
            sortMusicFile(files[index], action)
        } else {
            _errorMessage.value = "Invalid index for sorting: $index"
        }
    }

    fun undoLastAction() {
        val last = _sortResults.value.lastOrNull() ?: return
        viewModelScope.launch {
            try {
                val restored = fileManager.undoSort(last)
                if (restored != null) {
                    _musicFiles.value = _musicFiles.value.toMutableList().apply { add(_currentIndex.value, restored) }
                    _sortResults.value = _sortResults.value.dropLast(1)
                    
                    // Reload all playlists after undo
                    loadLikedFiles()
                    loadRejectedFiles()
                    
                    // Update current file if needed
                    loadCurrentFile()
                    
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Unable to undo last action"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error undoing: ${e.message}"
            }
        }
    }



    
    
    
    


    
    
    private fun loadCurrentFile() {
        val files = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        val index = _currentIndex.value
        if (files.isEmpty() || index !in files.indices) return
        val currentFile = files[index]
        
        // Store current file for notification
        this.currentFile = currentFile
        
        // Update the actually playing file (independent of playlist navigation)
        _currentPlayingFile.value = currentFile
        
        // Pre-fetch next file for buffering (only if list has more than 1 item)
        val nextFile = if (files.size > 1) files[(_currentIndex.value + 1) % files.size] else null
        if (nextFile != null) {
            // Removed audioManager.preloadFile(nextFile) // New call to preload next file
        }
        
        // Check if it's an AIFF file and provide specific feedback
        val isAiffFile = currentFile.name.lowercase().endsWith(".aif") || currentFile.name.lowercase().endsWith(".aiff")
        if (isAiffFile) {
            CrashLogger.log("MusicViewModel", "Loading AIFF file: ${currentFile.name}")
        }
        
    // Check memory before loading file and clear caches if needed
    checkMemoryPressureAndCleanup()
    
    // Additional memory check specifically for waveform and file operations
    val runtime = Runtime.getRuntime()
    val availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
    if (availableMemoryMB < 50) {
        CrashLogger.log("MusicViewModel", "Critical low memory ($availableMemoryMB MB) - stopping file processing")
        return
    }
        
        // Call playFile directly (no longer a suspend function)
        val started = audioManager.playFile(currentFile)
        _isPlaying.value = started
        if (!started) {
            if (isAiffFile) {
                _errorMessage.value = "Cannot play AIFF file. This format might not be supported by your device's audio codec."
                CrashLogger.log("MusicViewModel", "AIFF playback failed for ${currentFile.name}")
            } else {
                _errorMessage.value = "Cannot start playback. Unsupported file or permission denied."
                CrashLogger.log("MusicViewModel", "Playback failed for ${currentFile.name}")
            }
        } else {
            _errorMessage.value = null
            CrashLogger.log("MusicViewModel", "Playback started successfully for ${currentFile.name}")
            // increment listened stat
            try { preferences.incrementListened() } catch (_: Exception) {}
        }
        
        // Extract and update duration if not available
        if (currentFile.duration == 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val actualDuration = extractDuration(currentFile)
                if (actualDuration > 0) {
                    withContext(Dispatchers.Main) {
                        _duration.value = actualDuration
                        updateFileDuration(currentFile, actualDuration)
                    }
                }
            }
        } else {
            _duration.value = currentFile.duration
        }
        
        
        updateNotification()
        
        // Start preloading next song for smooth transitions
        preloadNextSong()
    }
    
    private fun preloadNextSong() {
        audioPreloadJob?.cancel()
        audioPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentFiles = when (_currentPlaylistTab.value) {
                    PlaylistTab.TODO -> _musicFiles.value
                    PlaylistTab.LIKED -> _likedFiles.value
                    PlaylistTab.REJECTED -> _rejectedFiles.value
                }
                val currentIdx = _currentIndex.value
                
                // Preload next song if available
                if (currentIdx + 1 < currentFiles.size) {
                    val nextFile = currentFiles[currentIdx + 1]
                    preloadedFile = nextFile
                    CrashLogger.log("MusicViewModel", "Preloaded next song: ${nextFile.name}")
                }
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error preloading next song", e)
            }
        }
    }
    
    private suspend fun extractDuration(file: MusicFile): Long {
        return try {
            // Validate file URI before attempting extraction
            if (file.uri.toString().isEmpty() || file.uri.toString() == "null" || file.uri.toString() == ":") {
                CrashLogger.log("MusicViewModel", "Invalid URI for duration extraction: ${file.uri}")
                return 0L
            }
            
            val duration = ResourceManager.extractDurationWithFallback(context, file.uri)
            if (duration > 0) {
                duration
            } else {
                // Method 3: Estimate from file size for AIF files
                if (file.name.lowercase().endsWith(".aif") || file.name.lowercase().endsWith(".aiff")) {
                    // Rough estimate: AIF files are typically uncompressed
                    // Assume 44.1kHz, 16-bit, stereo = 176,400 bytes per second
                    val bytesPerSecond = 44100 * 2 * 2 // sample rate * channels * bytes per sample
                    val estimatedDuration = (file.size * 1000L) / bytesPerSecond
                    
                    // For large AIFF files, this estimation is often more reliable than MediaExtractor
                    if (file.size > 100 * 1024 * 1024) { // 100MB+
                        CrashLogger.log("MusicViewModel", "Using file size estimation for large AIFF file: ${file.name} (${file.size / (1024 * 1024)}MB)")
                    }
                    
                    estimatedDuration
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to extract duration for ${file.name} (URI: ${file.uri})", e)
            0L
        }
    }
    
    private fun updateFileDuration(file: MusicFile, newDuration: Long) {
        val updatedFile = file.copy(duration = newDuration)
        
        // Update in ALL lists where the file might appear
        // Update TODO list
        val updatedTodoFiles = _musicFiles.value.toMutableList()
        val todoIndex = updatedTodoFiles.indexOfFirst { it.uri == file.uri }
        if (todoIndex >= 0) {
            updatedTodoFiles[todoIndex] = updatedFile
            _musicFiles.value = updatedTodoFiles
        }
        
        // Update LIKED list
        val updatedLikedFiles = _likedFiles.value.toMutableList()
        val likedIndex = updatedLikedFiles.indexOfFirst { it.uri == file.uri }
        if (likedIndex >= 0) {
            updatedLikedFiles[likedIndex] = updatedFile
            _likedFiles.value = updatedLikedFiles
        }
        
        // Update REJECTED list
        val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
        val rejectedIndex = updatedRejectedFiles.indexOfFirst { it.uri == file.uri }
        if (rejectedIndex >= 0) {
            updatedRejectedFiles[rejectedIndex] = updatedFile
            _rejectedFiles.value = updatedRejectedFiles
        }
    }
    
    private fun updateFileDurationsBatch(updatedFiles: List<MusicFile>) {
        // Update main music files list
        val updatedMusicFiles = _musicFiles.value.toMutableList()
        updatedFiles.forEach { updatedFile ->
            val index = updatedMusicFiles.indexOfFirst { it.uri == updatedFile.uri }
            if (index != -1) {
                updatedMusicFiles[index] = updatedFile
            }
        }
        _musicFiles.value = updatedMusicFiles
        
        // Update liked files list
        val updatedLikedFiles = _likedFiles.value.toMutableList()
        updatedFiles.forEach { updatedFile ->
            val index = updatedLikedFiles.indexOfFirst { it.uri == updatedFile.uri }
            if (index != -1) {
                updatedLikedFiles[index] = updatedFile
            }
        }
        _likedFiles.value = updatedLikedFiles
        
        // Update rejected files list
        val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
        updatedFiles.forEach { updatedFile ->
            val index = updatedRejectedFiles.indexOfFirst { it.uri == updatedFile.uri }
            if (index != -1) {
                updatedRejectedFiles[index] = updatedFile
            }
        }
        _rejectedFiles.value = updatedRejectedFiles
    }
    
    private fun extractDurationsForAllFiles(files: List<MusicFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filesToProcess = files.filter { it.duration == 0L }
                CrashLogger.log("MusicViewModel", "Starting duration extraction for ${filesToProcess.size} files")
                
                // Dynamic batch size based on file count and memory
                val runtime = Runtime.getRuntime()
                val availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
                val batchSize = when {
                    availableMemoryMB > 500 -> 10 // High memory: larger batches
                    availableMemoryMB > 200 -> 5  // Medium memory: normal batches
                    else -> 3                     // Low memory: smaller batches
                }
                
                CrashLogger.log("MusicViewModel", "Available memory: ${availableMemoryMB}MB, using batch size: $batchSize")
                
                // Use coroutine semaphore to limit concurrent operations to prevent resource conflicts
                val semaphore = Semaphore(1) // Limit to 1 concurrent operation to prevent MediaExtractor conflicts
                
                for (i in filesToProcess.indices step batchSize) {
                    val batch = filesToProcess.subList(i, minOf(i + batchSize, filesToProcess.size))
                    
                    // Process batch with parallel coroutines but controlled concurrency
                        val updatedFiles = batch.map { file ->
                            async(Dispatchers.IO) {
                                semaphore.acquire()
                                try {
                                    val actualDuration = extractDuration(file)
                                    if (actualDuration > 0) {
                                        file.copy(duration = actualDuration)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    CrashLogger.log("MusicViewModel", "Error extracting duration for ${file.name}", e)
                                    null
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll().filterNotNull()
                        
                        // Add delay between batches to prevent resource conflicts
                        delay(200)
                    
                    withContext(Dispatchers.Main) {
                        try {
                            updateFileDurationsBatch(updatedFiles)
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error updating file durations batch", e)
                        }
                    }
                    
                    // Check memory pressure between batches
                    val memoryAfterBatch = (runtime.totalMemory() - runtime.freeMemory()) * 100 / runtime.maxMemory()
                    if (memoryAfterBatch > 80) {
                        CrashLogger.log("MusicViewModel", "High memory usage detected (${memoryAfterBatch}%), triggering GC")
                        System.gc()
                        delay(200) // Give GC time to work
                    } else {
                        delay(50) // Small delay between batches
                    }
                }
                
                CrashLogger.log("MusicViewModel", "Duration extraction completed")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Critical error in extractDurationsForAllFiles", e)
            }
        }
    }


    
    private fun stopPlayback() {
        audioManager.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
    
    private fun startPositionUpdates() {
        viewModelScope.launch {
            try {
                while (isActive) { // Check if coroutine is still active
                    kotlinx.coroutines.delay(33) // Update every ~33ms for 120Hz displays (30fps UI updates)
                    
                    if (_isPlaying.value && isActive) { // Double check we're still active
                        try {
                            _currentPosition.value = audioManager.getCurrentPosition()
                            _duration.value = audioManager.getDuration()
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error updating position", e)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                CrashLogger.log("MusicViewModel", "Position updates cancelled")
                throw e // Re-throw to properly handle cancellation
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error in position updates", e)
            }
        }
    }
    
    private fun startPeriodicCacheClearing() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (isActive) { // Check if coroutine is still active
                    kotlinx.coroutines.delay(300000) // Clear caches every 5 minutes
                    
                    if (!isActive) break // Exit if cancelled
                    
                    try {
                        // Check memory usage before clearing
                        val runtime = Runtime.getRuntime()
                        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                        val maxMemory = runtime.maxMemory()
                        val memoryUsagePercent = (usedMemory * 100) / maxMemory
                        
                        if (memoryUsagePercent > 50) { // Clear if using more than 50% of memory (more aggressive)
                            audioManager.clearAllCaches()
                            System.gc() // Suggest garbage collection
                            CrashLogger.log("MusicViewModel", "Periodic cache clearing completed (memory usage: ${memoryUsagePercent}%)")
                        } else {
                            CrashLogger.log("MusicViewModel", "Memory usage acceptable (${memoryUsagePercent}%), skipping cache clear")
                        }

                        // Smart disk cache trimming (best-effort, non-intrusive)
                        try {
                            val ctx = getApplication<Application>().applicationContext
                            val totalCache = CacheManager.calculateTotalCacheBytes(ctx)
                            // Preferred and hard caps (adjustable): 512MB preferred, 1GB hard
                            val preferred = 512L * 1024L * 1024L
                            val hard = 1024L * 1024L * 1024L
                            if (totalCache > preferred) {
                                val protected = mutableSetOf<String>()
                                // Protect active temp files used by audio pipeline
                                try {
                                    val p1 = javaClass.getDeclaredField("audioManager")
                                    p1.isAccessible = true
                                    val am = p1.get(this@MusicViewModel) as com.example.mobiledigger.audio.AudioManager
                                    val f1 = am.javaClass.getDeclaredField("currentFFmpegDataSourcePath")
                                    f1.isAccessible = true
                                    (f1.get(am) as? String)?.let { protected.add(it) }
                                    val f2 = am.javaClass.getDeclaredField("currentTempWavPath")
                                    f2.isAccessible = true
                                    (f2.get(am) as? String)?.let { protected.add(it) }
                                } catch (_: Exception) { /* best-effort */ }
                                CacheManager.trimDiskCache(ctx, preferred, hard, protected)
                                CrashLogger.log("MusicViewModel", "Disk cache trimmed. Before=${totalCache} bytes")
                            }
                        } catch (_: Exception) { /* ignore */ }
                    } catch (e: Exception) {
                        if (isActive) { // Only log if not cancelled
                            CrashLogger.log("MusicViewModel", "Error during periodic cache clearing", e)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                CrashLogger.log("MusicViewModel", "Periodic cache clearing cancelled")
                throw e // Re-throw to properly handle cancellation
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error in periodic cache clearing", e)
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Check memory pressure and perform emergency cleanup if needed
     */
    private fun checkMemoryPressureAndCleanup() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory * 100) / maxMemory
            
            when {
                memoryUsagePercent > 85 -> {
                    // Emergency cleanup
                    CrashLogger.log("MusicViewModel", "EMERGENCY: Memory usage at ${memoryUsagePercent}%, performing aggressive cleanup")
                    audioManager.clearAllCaches()
                    System.gc()
                    // Clear waveform cache by direct import
                    try {
                        com.example.mobiledigger.ui.components.clearWaveformCache()
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "Could not clear waveform cache", e)
                    }
                }
                memoryUsagePercent > 70 -> {
                    // High memory usage - clear caches
                    CrashLogger.log("MusicViewModel", "High memory usage detected: ${memoryUsagePercent}%, clearing caches")
                    audioManager.clearAllCaches()
                    System.gc()
                }
                memoryUsagePercent > 60 -> {
                    // Moderate memory usage - suggestion GC only
                    CrashLogger.log("MusicViewModel", "Moderate memory usage: ${memoryUsagePercent}%, suggesting GC")
                    System.gc()
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error checking memory pressure", e)
        }
    }
    
    // Enhanced error handling
    private fun handleError(operation: String, throwable: Throwable) {
        val userFriendlyMessage = when (throwable) {
            is java.io.FileNotFoundException -> "File not found. Please check if the file still exists."
            is SecurityException -> "Permission denied. Please check app permissions."
            is java.io.IOException -> "File operation failed. Please try again."
            is OutOfMemoryError -> "Not enough memory. Try closing other apps."
            else -> "An error occurred: ${throwable.message}"
        }
        _errorMessage.value = userFriendlyMessage
        CrashLogger.log("MusicViewModel", "Error in $operation", throwable)
    }
    
    

    
    // Method to rescan the current source folder
    fun rescanSourceFolder() {
        val savedUri = preferences.getSourceRootUri()
        if (savedUri != null) {
            val uri = Uri.parse(savedUri)
            CrashLogger.log("MusicViewModel", "Rescanning source folder: $savedUri")
            // Reset state to start fresh
            _errorMessage.value = null
            _sortResults.value = emptyList()
            stopPlayback()
            _currentIndex.value = 0
            _musicFiles.value = emptyList()
            // Force full rescan
            selectFolder(uri)
        } else {
            _errorMessage.value = "No source folder to rescan. Please select a folder first."
        }
    }
    
    
    // Delete all files from "I Don't Dig" folder with progress tracking
    fun deleteRejectedFiles() {
        viewModelScope.launch {
            _isDeletingFiles.value = true
            _deletionProgress.value = 0f
            
            try {
                // Get the list of rejected files first to track progress
                val rejectedFiles = _rejectedFiles.value
                val totalFiles = rejectedFiles.size
                
                if (totalFiles == 0) {
                    _errorMessage.value = "No rejected files to delete"
                    _isDeletingFiles.value = false
                    return@launch
                }
                
                CrashLogger.log("MusicViewModel", "Starting deletion of $totalFiles rejected files")
                
                // Use the enhanced delete function with progress callback
                val success = fileManager.deleteRejectedFilesWithProgress { deletedCount ->
                    val progress = deletedCount.toFloat() / totalFiles
                    _deletionProgress.value = progress
                }
                
                if (success) {
                    _deletionProgress.value = 1f
                    _rejectedFiles.value = emptyList() // Clear the rejected files list
                    _errorMessage.value = "All rejected files deleted successfully"
                    loadRejectedFiles() // Refresh the list
                } else {
                    _errorMessage.value = "Failed to delete rejected files"
                }
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error deleting rejected files", e)
                _errorMessage.value = "Error deleting rejected files: ${e.message}"
            } finally {
                _isDeletingFiles.value = false
            }
        }
    }
    
    // Share liked files as text file via WhatsApp
    fun shareLikedFilesTxt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val txtFile = fileManager.createLikedFilesTxt()
                if (txtFile == null) {
                    withContext(Dispatchers.Main) { _errorMessage.value = "No liked files to share" }
                    return@launch
                }
                
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    txtFile
                )
                
                withContext(Dispatchers.Main) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, txtFile.name)
                        putExtra(android.content.Intent.EXTRA_TEXT, "Liked Songs List from MobileDigger")
                        setPackage("com.whatsapp")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { 
                        getApplication<Application>().startActivity(send) 
                    } catch (_: Exception) {
                        val chooser = android.content.Intent.createChooser(send, "Share Liked Songs TXT")
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        getApplication<Application>().startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Share failed: ${e.message}" }
            }
        }
    }
    
    // Check if user wants to delete rejected files after sorting is complete

    


    fun shareToWhatsApp() {
        val currentFiles = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        val currentFile = currentFiles.getOrNull(_currentIndex.value)
        if (currentFile != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val originalName = currentFile.name
                    CrashLogger.log("MusicViewModel", "Starting ZIP share for: $originalName")

                    // 1) Prepare ZIP container in cache
                    val cacheDir = File(context.cacheDir, "shared_zips")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val zipFileName = "${originalName.substringBeforeLast('.')}.zip"
                    val zipFile = File(cacheDir, zipFileName)

                    // 2) Create ZIP with the original file inside
                    ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                        val entry = ZipEntry(originalName)
                        zipOut.putNextEntry(entry)
                        context.contentResolver.openInputStream(currentFile.uri)?.use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                    CrashLogger.log("MusicViewModel", "ZIP created: ${zipFile.absolutePath}")

                    // 3) Get URI via FileProvider
                    val zipUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )

                    withContext(Dispatchers.Main) {
                        // 4) Share as a document (ZIP)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, zipUri)
                            putExtra(Intent.EXTRA_SUBJECT, zipFileName)
                            putExtra(Intent.EXTRA_TEXT, "Audio file attached: $originalName")
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(shareIntent)
                            CrashLogger.log("MusicViewModel", "WhatsApp ZIP share intent started")
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "WhatsApp not found, using chooser", e)
                            val chooser = Intent.createChooser(shareIntent, "Share audio")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Failed to share: ${e.message}"
                        CrashLogger.log("MusicViewModel", "ZIP share failed", e)
                    }
                }
            }
        } else {
            _errorMessage.value = "No song selected to share"
        }
    }


    
    private fun ensureMusicServiceStarted() {
        val serviceIntent = Intent(context, MusicService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            CrashLogger.log("MusicViewModel", "MusicService started successfully.")
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to start MusicService.", e)
            _errorMessage.value = "Playback service failed to start."
        }
    }
    
    private fun registerNotificationReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(MusicService.ACTION_PLAY_PAUSE)
                addAction(MusicService.ACTION_NEXT)
                addAction(MusicService.ACTION_PREVIOUS)
                addAction(MusicService.ACTION_LIKE)
                addAction(MusicService.ACTION_DISLIKE)
            }
            context.registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            CrashLogger.log("MusicViewModel", "Notification receiver registered successfully")
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to register notification receiver", e)
        }
    }
    
    private fun updateNotification() {
        try {
            ensureMusicServiceStarted()
            
            val currentFiles = when (_currentPlaylistTab.value) {
                PlaylistTab.TODO -> _musicFiles.value
                PlaylistTab.LIKED -> _likedFiles.value
                PlaylistTab.REJECTED -> _rejectedFiles.value
            }
            val currentFile = currentFiles.getOrNull(_currentIndex.value)
            val title = currentFile?.name ?: "MobileDigger"
            
            val intent = Intent(MusicService.ACTION_UPDATE_NOTIFICATION).apply { setPackage(context.packageName) }
            intent.putExtra(MusicService.EXTRA_TITLE, title)
            intent.putExtra(MusicService.EXTRA_IS_PLAYING, _isPlaying.value)
            intent.putExtra(MusicService.EXTRA_CURRENT_POSITION, _currentPosition.value)
            intent.putExtra(MusicService.EXTRA_DURATION, _duration.value)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to update notification", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            CrashLogger.log("MusicViewModel", "ViewModel being cleared, cleaning up resources")
            
            // Cancel all running coroutines in viewModelScope
            viewModelScope.coroutineContext.cancelChildren()
            
            // Cancel search jobs to prevent memory leaks
            searchJob?.cancel()
            debounceJob?.cancel()
            
            // Stop playback first
            stopPlayback()
            
            // Unregister broadcast receiver
            try {
                context.unregisterReceiver(notificationReceiver)
                CrashLogger.log("MusicViewModel", "Notification receiver unregistered")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Receiver was not registered or already unregistered")
            }
            
            // Release audio manager and clear all caches
            audioManager.clearAllCaches()
            audioManager.release()
            
            // Force garbage collection
            System.gc()
            
            CrashLogger.log("MusicViewModel", "ViewModel cleanup completed")
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error during ViewModel cleanup", e)
        }
    }

    private var zipJob: kotlinx.coroutines.Job? = null
    
    fun startShareLikedZip(background: Boolean = true) {
        if (_zipInProgress.value) return
        _zipInProgress.value = true
        _zipProgress.value = 0
        val scope = if (background) viewModelScope else viewModelScope
        zipJob = scope.launch(Dispatchers.IO) {
            try {
                val destination = fileManager.getDestinationFolder()
                val liked = destination?.findFile("Liked") ?: destination?.findFile("I DIG")
                val files = liked?.listFiles()?.filter { it.isFile } ?: emptyList()
                if (files.isEmpty()) {
                    withContext(Dispatchers.Main) { _errorMessage.value = "No liked files to share" }
                    _zipInProgress.value = false
                    return@launch
                }
                val total = files.size.coerceAtLeast(1)

                val cacheRoot = java.io.File(getApplication<Application>().cacheDir, "idig_zip")
                if (!cacheRoot.exists()) cacheRoot.mkdirs()
                val dateStr = java.text.SimpleDateFormat("dd-MM-yy", java.util.Locale.getDefault()).format(java.util.Date())
                val outZip = java.io.File(cacheRoot, "Liked_${dateStr}.zip")

                java.util.zip.ZipOutputStream(java.io.FileOutputStream(outZip)).use { zipOut ->
                    files.forEachIndexed { idx, doc ->
                        val entryName = doc.name ?: "track_${idx+1}"
                        zipOut.putNextEntry(java.util.zip.ZipEntry(entryName))
                        try {
                            getApplication<Application>().contentResolver.openInputStream(doc.uri)?.use { input ->
                                input.copyTo(zipOut)
                            }
                        } finally {
                            zipOut.closeEntry()
                        }
                        _zipProgress.value = ((idx + 1) * 100 / total)
                    }
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    outZip
                )
                withContext(Dispatchers.Main) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, outZip.name)
                        setPackage("com.whatsapp")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { getApplication<Application>().startActivity(send) } catch (_: Exception) {
                        val chooser = android.content.Intent.createChooser(send, "Share Liked Songs")
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        getApplication<Application>().startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "ZIP failed: ${e.message}" }
            } finally {
                _zipInProgress.value = false
            }
        }
    }
    
    fun cancelZipCreation() {
        try {
            zipJob?.cancel()
            zipJob = null
            _zipInProgress.value = false
            _zipProgress.value = 0
            _errorMessage.value = "ZIP creation cancelled"
            CrashLogger.log("MusicViewModel", "ZIP creation cancelled by user")
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error cancelling ZIP creation", e)
        }
    }

    fun deleteAllLiked(confirmCount: Int): Boolean {
        if (confirmCount < 4) return false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destination = fileManager.getDestinationFolder()
                val liked = destination?.findFile("Liked") ?: destination?.findFile("I DIG")
                val files = liked?.listFiles()?.filter { it.isFile } ?: emptyList()
                var deleted = 0
                files.forEach { f -> try { if (f.delete()) deleted++ } catch (_: Exception) {} }
                withContext(Dispatchers.Main) { 
                    _errorMessage.value = "Deleted $deleted liked files"
                    _likedFiles.value = emptyList() // Clear the liked files list
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Delete failed: ${e.message}" }
            }
        }
        return true
    }
    
    fun deleteAllTodo(confirmCount: Int): Boolean {
        if (confirmCount < 4) return false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFolder = fileManager.getSelectedFolder()
                if (sourceFolder != null) {
                    val files = sourceFolder.listFiles()?.filter { it.isFile } ?: emptyList()
                    var deleted = 0
                    files.forEach { f -> 
                        try { 
                            if (f.delete()) deleted++ 
                        } catch (_: Exception) {} 
                    }
                    withContext(Dispatchers.Main) { 
                        _errorMessage.value = "Deleted $deleted todo files"
                        _musicFiles.value = emptyList() // Clear the music files list
                        _currentIndex.value = 0
                        stopPlayback()
                    }
                } else {
                    withContext(Dispatchers.Main) { 
                        _errorMessage.value = "No source folder selected" 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Delete failed: ${e.message}" }
            }
        }
        return true
    }
    
    // Multi-selection functions
    fun toggleMultiSelectionMode() {
        _isMultiSelectionMode.value = !_isMultiSelectionMode.value
        if (!_isMultiSelectionMode.value) {
            _selectedIndices.value = emptySet()
        }
    }

    fun enableMultiSelectionMode(index: Int) {
        _isMultiSelectionMode.value = true
        toggleSelection(index)
    }
    
    fun toggleSelection(index: Int) {
        if (!_isMultiSelectionMode.value) return
        val currentSelected = _selectedIndices.value.toMutableSet()
        if (currentSelected.contains(index)) {
            currentSelected.remove(index)
        } else {
            currentSelected.add(index)
        }
        _selectedIndices.value = currentSelected
    }
    
    fun selectAll() {
        if (!_isMultiSelectionMode.value) return
        _selectedIndices.value = _musicFiles.value.indices.toSet()
    }
    
    fun clearSelection() {
        _selectedIndices.value = emptySet()
    }
    
    fun moveSelectedFilesToSubfolder(subfolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = _selectedIndices.value.toList()
            val filesToMove = selected.mapNotNull { index -> _musicFiles.value.getOrNull(index) }

            if (filesToMove.isNotEmpty()) {
                val success = fileManager.moveFilesToSubfolder(filesToMove, subfolderName)
                if (success) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Moved ${filesToMove.size} files to $subfolderName"
                        _selectedIndices.value = emptySet()
                        _isMultiSelectionMode.value = false
                        rescanSourceFolder() // Refresh the list
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Failed to move files"
                    }
                }
            }
        }
    }
    
    fun sortSelectedFiles(action: SortAction) {
        val selected = _selectedIndices.value
        if (selected.isEmpty()) {
            _errorMessage.value = "No files selected"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // First, ensure audio playback and waveform generation are prioritized
                // Wait for current file to finish loading and waveform to be generated
                
                // Check if we need to move to next song first
                val currentIndex = _currentIndex.value
                val musicFiles = _musicFiles.value
                
                if (currentIndex < musicFiles.size - 1) {
                    // Move to next song first
                    withContext(Dispatchers.Main) {
                        _currentIndex.value = currentIndex + 1
                        loadCurrentFile()
                    }
                    
                    // Wait for next song to start playing and waveform to generate
                    delay(3000) // Give more time for next song to load and waveform to generate
                    
                    // Additional check to ensure playback is stable
                    var retryCount = 0
                    while (retryCount < 5 && !isPlaying.value) {
                        delay(500)
                        retryCount++
                    }
                } else {
                    // If no next song, just wait for current playback to stabilize
                    delay(2000)
                }
                
                val sortedIndices = selected.sortedDescending() // Sort in reverse order to avoid index shifting
                val successfullySortedIndices = mutableListOf<Int>()
                
                // Sort files and track successful operations
                for (index in sortedIndices) {
                    if (index in _musicFiles.value.indices) {
                        val file = _musicFiles.value[index]
                        if (fileManager.sortFile(file, action)) {
                            successfullySortedIndices.add(index)
                        }
                        // Small delay between file operations to prevent memory issues
                        delay(200) // Increased delay for better stability
                    }
                }
                
                withContext(Dispatchers.Main) {
                    val successCount = successfullySortedIndices.size
                    _errorMessage.value = "Sorted $successCount files to ${if (action == SortAction.LIKE) "Liked" else "Rejected"}"
                    _selectedIndices.value = emptySet()
                    _isMultiSelectionMode.value = false
                    
                    // Refresh the music files list by removing successfully sorted files
                    val updatedFiles = _musicFiles.value.toMutableList()
                    
                    // Remove successfully sorted files in reverse order to maintain indices
                    successfullySortedIndices.sortedDescending().forEach { index ->
                        if (index < updatedFiles.size) {
                            updatedFiles.removeAt(index)
                        }
                    }
                    
                    _musicFiles.value = updatedFiles
                    
                    // Adjust current index if needed
                    if (_currentIndex.value >= updatedFiles.size && updatedFiles.isNotEmpty()) {
                        _currentIndex.value = updatedFiles.size - 1
                        loadCurrentFile()
                    } else if (updatedFiles.isEmpty()) {
                        _currentIndex.value = 0
                        stopPlayback()
                        _errorMessage.value = "All files sorted! Select another folder to continue."
                        // Show prompt to delete rejected files
                        showDeleteRejectedPrompt()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error sorting selected files: ${e.message}"
                }
            }
        }
    }
    
    private fun showDeleteRejectedPrompt() {
        _showDeleteRejectedPrompt.value = true
    }
    
    fun dismissDeleteRejectedPrompt() {
        _showDeleteRejectedPrompt.value = false
    }
    
    // Tabbed playlist functions
    fun switchPlaylistTab(tab: PlaylistTab) {
        _currentPlaylistTab.value = tab
        
        // Reset current index when switching tabs to avoid out-of-bounds
        val currentFiles = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        if (_currentIndex.value >= currentFiles.size) {
            _currentIndex.value = if (currentFiles.isNotEmpty()) 0 else 0
        }
        
        CrashLogger.log("MusicViewModel", "Switching to playlist tab: $tab")
        when (tab) {
            PlaylistTab.TODO -> {
                CrashLogger.log("MusicViewModel", "TODO tab - using ${_musicFiles.value.size} files")
            }
            PlaylistTab.LIKED -> {
                CrashLogger.log("MusicViewModel", "LIKED tab - loading files")
                loadLikedFiles()
                updateSubfolderInfo() // Update subfolder info for liked playlist
            }
            PlaylistTab.REJECTED -> {
                CrashLogger.log("MusicViewModel", "REJECTED tab - loading files")
                loadRejectedFiles()
            }
        }
        
        // Load current file for the new tab
        // loadCurrentFile()
        
        // Force UI refresh by triggering a small delay and reload
        viewModelScope.launch {
            delay(100) // Small delay to ensure file operations complete
            loadLikedFiles()
            loadRejectedFiles()
        }
    }
    
    fun loadLikedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            fileLoadingMutex.withLock {
                try {
                    val destinationFolder = fileManager.getDestinationFolder()
                    CrashLogger.log("MusicViewModel", "Loading liked files from destination folder: ${destinationFolder?.name}")
                
                val likedFiles = if (destinationFolder != null) {
                    // Get the Liked folder within the destination folder
                    val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG")
                    if (likedFolder != null) {
                        val allFiles = mutableListOf<MusicFile>()
                        
                        // Get files from root liked folder
                        val rootFiles = fileManager.listMusicFilesInFolder(likedFolder.uri)
                        allFiles.addAll(rootFiles)
                        
                        // Get files from all subfolders
                        likedFolder.listFiles()?.forEach { subfolder ->
                            if (subfolder.isDirectory) {
                                val subfolderFiles = fileManager.listMusicFilesInFolder(subfolder.uri)
                                // Add subfolder info to each file
                                val filesWithSubfolder = subfolderFiles.map { file ->
                                    file.copy(subfolder = subfolder.name)
                                }
                                allFiles.addAll(filesWithSubfolder)
                            }
                        }
                        
                        // Remove duplicates based on URI (in case a file exists in both root and subfolder)
                        val uniqueFiles = allFiles.distinctBy { it.uri }
                        CrashLogger.log("MusicViewModel", "Found ${allFiles.size} liked files (including subfolders), ${uniqueFiles.size} unique")
                        uniqueFiles
                    } else {
                        CrashLogger.log("MusicViewModel", "Liked folder not found in destination")
                        emptyList()
                    }
                } else {
                    CrashLogger.log("MusicViewModel", "No destination folder found")
                    emptyList()
                }
                    withContext(Dispatchers.Main) {
                        _likedFiles.value = likedFiles.map { it.copy(sourcePlaylist = PlaylistTab.LIKED) }
                        CrashLogger.log("MusicViewModel", "Updated liked files StateFlow with ${likedFiles.size} files")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error loading liked files", e)
                }
            }
        }
    }
    
    private fun loadRejectedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            fileLoadingMutex.withLock {
                try {
                    val rejectedUri = fileManager.getRejectedFolderUri()
                    CrashLogger.log("MusicViewModel", "Loading rejected files from URI: $rejectedUri")
                
                val rejectedFiles = if (rejectedUri != null) {
                    val files = fileManager.listMusicFilesInFolder(rejectedUri)
                    CrashLogger.log("MusicViewModel", "Found ${files.size} rejected files")
                    files
                } else {
                    CrashLogger.log("MusicViewModel", "No rejected folder URI found")
                    emptyList()
                }
                    withContext(Dispatchers.Main) {
                        _rejectedFiles.value = rejectedFiles.map { it.copy(sourcePlaylist = PlaylistTab.REJECTED) }
                        CrashLogger.log("MusicViewModel", "Updated rejected files StateFlow with ${rejectedFiles.size} files")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error loading rejected files", e)
                }
            }
        }
    }
    
    fun getCurrentPlaylistFiles(): List<MusicFile> {
        val files = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        CrashLogger.log("MusicViewModel", "getCurrentPlaylistFiles - Tab: ${_currentPlaylistTab.value}, Files: ${files.size}")
        return files
    }
    
    
    
    fun refreshAllPlaylists() {
        viewModelScope.launch {
            delay(200) // Small delay to ensure file operations complete
            loadLikedFiles()
            loadRejectedFiles()
            CrashLogger.log("MusicViewModel", "Refreshed all playlists")
        }
    }
    
    // Search job to prevent multiple concurrent searches
    private var searchJob: Job? = null
    private var debounceJob: Job? = null
    
    fun searchMusic(query: String) {
        try {
            // Debounced search (2s), no limits
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return
            }
            debounceJob?.cancel()
            debounceJob = viewModelScope.launch {
                delay(2000)
                performSearchNow(query)
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error starting search", e)
        }
    }

    // Immediate search with no limits, used by UI button/IME action
    fun searchMusicImmediate(query: String) {
        try {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return
            }
            debounceJob?.cancel()
            performSearchNow(query)
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error starting immediate search", e)
        }
    }

    private fun performSearchNow(query: String) {
        // Cancel any running search
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val allFiles = (_musicFiles.value + _likedFiles.value + _rejectedFiles.value)
                    .distinctBy { it.uri }
                val results = allFiles.filter { file ->
                    try {
                        file.name.contains(query, ignoreCase = true)
                    } catch (_: Exception) { false }
                }
                _searchResults.value = results
                CrashLogger.log("MusicViewModel", "Immediate search completed: ${results.size} results")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error in performSearchNow", e)
                _searchResults.value = emptyList()
            }
        }
    }
    
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
    
    
    fun updateSearchText(newText: String) {
        _searchText.value = newText
    }
    
    fun loadSubfolders(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val folders = fileManager.listSubfolders(sourceUri)
                withContext(Dispatchers.Main) {
                    _subfolders.value = folders
                    CrashLogger.log("MusicViewModel", "Loaded ${folders.size} subfolders.")
                }
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error loading subfolders", e)
                withContext(Dispatchers.Main) { _errorMessage.value = "Error loading subfolders: ${e.message}" }
            }
        }
    }
    
    fun loadFilesFromSubfolder(subfolderUri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                // Preserve current playback
                val currentlyPlaying = _currentPlayingFile.value
                val currentUri = currentlyPlaying?.uri
                // Do not stop playback or reset index here; we only update the list
                CrashLogger.log("MusicViewModel", "Loading files from subfolder: $subfolderUri")
                
                val files = fileManager.loadMusicFilesFromSubfolder(subfolderUri)
                _musicFiles.value = files.map { it.copy(sourcePlaylist = PlaylistTab.TODO) }
                
                // Skip duration extraction to improve performance
                
                if (files.isNotEmpty()) {
                    // Keep current file if present in the new list; otherwise do not auto-play
                    if (currentUri != null) {
                        val idx = files.indexOfFirst { it.uri == currentUri }
                        if (idx >= 0) {
                            _currentIndex.value = idx
                            // Do NOT call loadCurrentFile(); keep the existing playback
                        }
                    }
                    _errorMessage.value = "Loaded ${files.size} tracks from subfolder (playback preserved)."
                } else {
                    _errorMessage.value = "No music files found in selected subfolder."
                }
                CrashLogger.log("MusicViewModel", "loadFilesFromSubfolder completed successfully")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "loadFilesFromSubfolder failed", e)
                _errorMessage.value = "Error loading subfolder: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    override fun onTrackCompletion() {
        CrashLogger.log("MusicViewModel", "Track completed, playing next.")
        next()
    }

    fun getFileName(uri: Uri): String? {
        return fileManager.getFileName(uri)
    }
    
    // ==================== SUBFOLDER MANAGEMENT ====================
    
    private fun loadSubfolderHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("subfolder_history", Context.MODE_PRIVATE)
                val historyString = prefs.getString("subfolder_names", "") ?: ""
                val history = if (historyString.isNotEmpty()) {
                    historyString.split(",").filter { it.isNotBlank() }.take(50)
                } else {
                    emptyList()
                }
                _subfolderHistory.value = history
                CrashLogger.log("MusicViewModel", "Loaded subfolder history: ${history.size} items")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error loading subfolder history", e)
            }
        }
    }
    
    private fun saveSubfolderHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("subfolder_history", Context.MODE_PRIVATE)
                val historyString = _subfolderHistory.value.joinToString(",")
                prefs.edit().putString("subfolder_names", historyString).apply()
                CrashLogger.log("MusicViewModel", "Saved subfolder history: ${_subfolderHistory.value.size} items")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error saving subfolder history", e)
            }
        }
    }
    
    fun updateSubfolderInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destinationFolder = fileManager.getDestinationFolder()
                if (destinationFolder != null) {
                    // Get the Liked folder within the destination folder
                    val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG")
                    if (likedFolder != null) {
                        val subfolders = mutableListOf<String>()
                        val fileCounts = mutableMapOf<String, Int>()
                        
                        // Get all subfolders in liked folder
                        likedFolder.listFiles()?.forEach { file ->
                            if (file.isDirectory) {
                                val folderName = file.name
                                if (folderName != null) {
                                    subfolders.add(folderName)
                                    
                                    // Count music files in this subfolder
                                    val musicFiles = file.listFiles()?.filter { 
                                        it.isFile && (it.name?.endsWith(".mp3", ignoreCase = true) == true)
                                    } ?: emptyList()
                                    fileCounts[folderName] = musicFiles.size
                                }
                            }
                        }
                        
                        _availableSubfolders.value = subfolders.sorted()
                        _subfolderFileCounts.value = fileCounts
                        CrashLogger.log("MusicViewModel", "Updated subfolder info: ${subfolders.size} folders")
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error updating subfolder info", e)
            }
        }
    }
    
    fun addToSubfolderHistory(subfolderName: String) {
        if (subfolderName.isBlank()) return
        
        val currentHistory = _subfolderHistory.value.toMutableList()
        // Remove if already exists to avoid duplicates
        currentHistory.remove(subfolderName)
        // Add to beginning
        currentHistory.add(0, subfolderName)
        // Keep only last 50
        _subfolderHistory.value = currentHistory.take(50)
        saveSubfolderHistory()
    }
    
    fun showSubfolderDialog() {
        _showSubfolderDialog.value = true
    }
    
    fun hideSubfolderDialog() {
        _showSubfolderDialog.value = false
        _newSubfolderName.value = ""
    }
    
    fun setNewSubfolderName(name: String) {
        _newSubfolderName.value = name
    }
    
    fun createNewSubfolder() {
        val name = _newSubfolderName.value.trim()
        if (name.isNotBlank()) {
            addToSubfolderHistory(name)
            hideSubfolderDialog()
            // Automatically move the current file to the new subfolder
            moveCurrentFileToSubfolder(name)
        }
    }
    
    fun showSubfolderManagementDialog() {
        _showSubfolderManagementDialog.value = true
    }
    
    fun hideSubfolderManagementDialog() {
        _showSubfolderManagementDialog.value = false
    }
    
    fun removeSubfoldersFromHistory(subfolderNames: List<String>) {
        if (subfolderNames.isEmpty()) return
        
        val currentHistory = _subfolderHistory.value.toMutableList()
        currentHistory.removeAll(subfolderNames)
        _subfolderHistory.value = currentHistory
        saveSubfolderHistory()
        
        _errorMessage.value = "Removed ${subfolderNames.size} subfolder(s) from memory"
    }
    
    fun moveCurrentFileToSubfolder(subfolderName: String) {
        val currentFile = _currentPlayingFile.value
        if (currentFile == null) {
            _errorMessage.value = "No file is currently playing"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destinationFolder = fileManager.getDestinationFolder()
                if (destinationFolder == null) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "No destination folder selected"
                    }
                    return@launch
                }
                
                // Get the Liked folder within the destination folder
                val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG")
                if (likedFolder == null) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Liked folder not found"
                    }
                    return@launch
                }
                
                // Create subfolder if it doesn't exist
                var subfolder = likedFolder.findFile(subfolderName)
                if (subfolder == null) {
                    subfolder = likedFolder.createDirectory(subfolderName)
                    if (subfolder == null) {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to create subfolder: $subfolderName"
                        }
                        return@launch
                    }
                }
                
                // Move the file - preserve original filename and MIME type
                val fileName = fileManager.getFileName(currentFile.uri) ?: "unknown"
                val originalMimeType = getApplication<Application>().contentResolver.getType(currentFile.uri) ?: "audio/mpeg"
                val targetFile = subfolder.createFile(originalMimeType, fileName)
                
                if (targetFile != null) {
                    // Copy file content
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(currentFile.uri)
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(targetFile.uri)
                    
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        // Delete original file
                        val originalFile = DocumentFile.fromSingleUri(getApplication(), currentFile.uri)
                        originalFile?.delete()
                        
                        // Update playlists
                        withContext(Dispatchers.Main) {
                            addToSubfolderHistory(subfolderName)
                            updateSubfolderInfo()
                            
                            // Handle file movement based on current playlist tab
                            when (_currentPlaylistTab.value) {
                                PlaylistTab.TODO -> {
                                    // Remove from TODO playlist
                                    val updatedTodoFiles = _musicFiles.value.toMutableList()
                                    val indexToRemove = updatedTodoFiles.indexOfFirst { it.uri == currentFile.uri }
                                    if (indexToRemove != -1) {
                                        updatedTodoFiles.removeAt(indexToRemove)
                                        _musicFiles.value = updatedTodoFiles
                                        
                                        // Adjust current index if needed
                                        if (indexToRemove < _currentIndex.value) {
                                            _currentIndex.value = _currentIndex.value - 1
                                        } else if (indexToRemove == _currentIndex.value) {
                                            // If we removed the currently playing file, load the next one
                                            if (updatedTodoFiles.isNotEmpty()) {
                                                if (_currentIndex.value >= updatedTodoFiles.size) {
                                                    _currentIndex.value = updatedTodoFiles.size - 1
                                                }
                                                loadCurrentFile()
                                            } else {
                                                stopPlayback()
                                                _errorMessage.value = "Moved '$fileName' to '$subfolderName'. No more files in TODO playlist."
                                            }
                                        }
                                    }
                                    
                                    // Add to liked files with subfolder info
                                    val likedFile = currentFile.copy(
                                        sourcePlaylist = PlaylistTab.LIKED,
                                        subfolder = subfolderName,
                                        uri = targetFile.uri // Use the new URI
                                    )
                                    val updatedLikedFiles = _likedFiles.value.toMutableList()
                                    // Check for duplicates before adding
                                    if (!updatedLikedFiles.any { it.uri == likedFile.uri }) {
                                        updatedLikedFiles.add(likedFile)
                                        _likedFiles.value = updatedLikedFiles
                                    }
                                }
                                PlaylistTab.REJECTED -> {
                                    // Remove from REJECTED playlist
                                    val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
                                    val indexToRemove = updatedRejectedFiles.indexOfFirst { it.uri == currentFile.uri }
                                    if (indexToRemove != -1) {
                                        updatedRejectedFiles.removeAt(indexToRemove)
                                        _rejectedFiles.value = updatedRejectedFiles
                                        
                                        // Adjust current index if needed
                                        if (indexToRemove < _currentIndex.value) {
                                            _currentIndex.value = _currentIndex.value - 1
                                        } else if (indexToRemove == _currentIndex.value) {
                                            // If we removed the currently playing file, load the next one
                                            if (updatedRejectedFiles.isNotEmpty()) {
                                                if (_currentIndex.value >= updatedRejectedFiles.size) {
                                                    _currentIndex.value = updatedRejectedFiles.size - 1
                                                }
                                                loadCurrentFile()
                                            } else {
                                                stopPlayback()
                                                _errorMessage.value = "Moved '$fileName' to '$subfolderName'. No more files in REJECTED playlist."
                                            }
                                        }
                                    }
                                    
                                    // Add to liked files with subfolder info
                                    val likedFile = currentFile.copy(
                                        sourcePlaylist = PlaylistTab.LIKED,
                                        subfolder = subfolderName,
                                        uri = targetFile.uri // Use the new URI
                                    )
                                    val updatedLikedFiles = _likedFiles.value.toMutableList()
                                    // Check for duplicates before adding
                                    if (!updatedLikedFiles.any { it.uri == likedFile.uri }) {
                                        updatedLikedFiles.add(likedFile)
                                        _likedFiles.value = updatedLikedFiles
                                    }
                                }
                                PlaylistTab.LIKED -> {
                                    // For liked files, we're just moving between subfolders
                                    // Reload liked files to reflect the new subfolder structure
                                    loadLikedFiles()
                                }
                            }
                            
                            _errorMessage.value = "Moved '$fileName' to '$subfolderName'"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to copy file to subfolder"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Failed to create file in subfolder"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error moving file: ${e.message}"
                }
                CrashLogger.log("MusicViewModel", "Error moving file to subfolder", e)
            }
        }
    }
    
    fun moveCurrentFileFromSubfolderToRoot() {
        val currentFile = _currentPlayingFile.value
        if (currentFile == null) {
            _errorMessage.value = "No file is currently playing"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destinationFolder = fileManager.getDestinationFolder()
                if (destinationFolder == null) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "No destination folder selected"
                    }
                    return@launch
                }
                
                // Get the Liked folder within the destination folder
                val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG")
                if (likedFolder == null) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Liked folder not found"
                    }
                    return@launch
                }
                
                // Move file from subfolder to root - preserve original filename and MIME type
                val fileName = fileManager.getFileName(currentFile.uri) ?: "unknown"
                val originalMimeType = getApplication<Application>().contentResolver.getType(currentFile.uri) ?: "audio/mpeg"
                val targetFile = likedFolder.createFile(originalMimeType, fileName)
                
                if (targetFile != null) {
                    // Copy file content
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(currentFile.uri)
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(targetFile.uri)
                    
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        // Delete original file
                        val originalFile = DocumentFile.fromSingleUri(getApplication(), currentFile.uri)
                        originalFile?.delete()
                        
                        // Update playlists
                        withContext(Dispatchers.Main) {
                            updateSubfolderInfo()
                            loadLikedFiles() // Refresh liked files
                            _errorMessage.value = "Moved '$fileName' to root liked folder"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to copy file to root"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Failed to create file in root"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error moving file: ${e.message}"
                }
                CrashLogger.log("MusicViewModel", "Error moving file from subfolder to root", e)
            }
        }
    }
    
    fun getCurrentFileSubfolder(): String? {
        val currentFile = _currentPlayingFile.value ?: return null
        val destinationFolder = fileManager.getDestinationFolder() ?: return null
        
        // Get the Liked folder within the destination folder
        val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG") ?: return null
        
        // Check if file is in a subfolder
        likedFolder.listFiles()?.forEach { subfolder ->
            if (subfolder.isDirectory) {
                subfolder.listFiles()?.forEach { file ->
                    if (file.uri == currentFile.uri) {
                        return subfolder.name
                    }
                }
            }
        }
        return null
    }
    
    fun setCurrentFileWithoutPlaying(file: MusicFile) {
        // Set the current file without starting playback
        _currentPlayingFile.value = file
        // Don't call loadCurrentFile() or start playback
    }
    
    fun loadFilesFromLikedSubfolder(subfolderName: String) {
        loadFilesFromLikedSubfolders(listOf(subfolderName))
    }

    fun loadFilesFromLikedSubfolders(subfolderNames: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destinationFolder = fileManager.getDestinationFolder()
                if (destinationFolder == null) {
                    withContext(Dispatchers.Main) { _errorMessage.value = "No destination folder selected" }
                    return@launch
                }
                val likedFolder = destinationFolder.findFile("Liked") ?: destinationFolder.findFile("I DIG")
                if (likedFolder == null) {
                    withContext(Dispatchers.Main) { _errorMessage.value = "Liked folder not found" }
                    return@launch
                }
                val currentUri = _currentPlayingFile.value?.uri
                val collected = mutableListOf<MusicFile>()
                likedFolder.listFiles()?.forEach { child ->
                    if (child.isDirectory && subfolderNames.any { it.equals(child.name, ignoreCase = true) }) {
                        val files = fileManager.listMusicFilesInFolder(child.uri)
                        collected += files.map { it.copy(sourcePlaylist = PlaylistTab.LIKED, subfolder = child.name) }
                    }
                }
                val unique = collected.distinctBy { it.uri }
                withContext(Dispatchers.Main) {
                    _likedFiles.value = unique
                    // Preserve current playback; do not auto play first track
                    if (currentUri != null) {
                        val idx = unique.indexOfFirst { it.uri == currentUri }
                        if (idx >= 0) {
                            _currentIndex.value = idx
                        }
                    }
                    _errorMessage.value = "Loaded ${unique.size} files from ${subfolderNames.size} subfolder(s) (playback preserved)."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Error loading liked subfolders: ${e.message}" }
                CrashLogger.log("MusicViewModel", "Error loading files from liked subfolders", e)
            }
        }
    }
}