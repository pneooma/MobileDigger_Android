package com.example.mobiledigger.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import com.example.mobiledigger.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.example.mobiledigger.audio.MediaLibrary
import kotlinx.coroutines.flow.debounce
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
import kotlinx.coroutines.channels.Channel
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.util.CacheManager
import com.example.mobiledigger.util.ResourceManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PlaylistTab {
    TODO, LIKED, REJECTED
}

enum class SortField {
    NAME, FILE_SIZE, DATE_ADDED, DURATION, EXTENSION, SUBFOLDER, RANDOM
}

data class SortOrder(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true
)

class MusicViewModel(application: Application) : AndroidViewModel(application), AudioManager.PlaybackCompletionListener {
    
    val audioManager = AudioManager(application)
    private val fileManager = FileManager(application)
    private val settingsRepository = SettingsRepository(application)
    val preferences = settingsRepository.preferences
    val themeManager = settingsRepository.themeManager
    val visualSettingsManager = settingsRepository.visualSettingsManager
    private val context = application.applicationContext

    // Audio focus handling — pause when another app plays, resume when it stops
    private val systemAudioManager = application.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var pausedByAudioFocusLoss = false
    private val audioFocusListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isPlaying.value) {
                    audioManager.pause()
                    _isPlaying.value = false
                    pausedByAudioFocusLoss = true
                    updateNotification()
                    CrashLogger.log("MusicViewModel", "Paused due to audio focus loss (transient=${ focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT })")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Could duck volume, but for a music triage app just pause
                if (_isPlaying.value) {
                    audioManager.pause()
                    _isPlaying.value = false
                    pausedByAudioFocusLoss = true
                    updateNotification()
                    CrashLogger.log("MusicViewModel", "Paused due to audio focus duck request")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByAudioFocusLoss) {
                    audioManager.resume()
                    _isPlaying.value = true
                    pausedByAudioFocusLoss = false
                    updateNotification()
                    CrashLogger.log("MusicViewModel", "Resumed after audio focus gain")
                }
            }
        }
    }
    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(audioFocusListener)
        .setWillPauseWhenDucked(true)
        .build()

    // PHASE 5: Performance profiling - moved to separate init block to avoid nested init
    private fun startPerformanceStatLogging() {
        viewModelScope.launch {
            // Log performance stats every 5 minutes
            while (isActive) {
                delay(300_000) // 5 minutes
                com.example.mobiledigger.util.PerformanceProfiler.logStats()
            }
        }
    }
    
    // Mutex to prevent concurrent file loading operations that cause memory pressure
    private val fileLoadingMutex = Mutex()
    
    // Debounce guard for duplicate playback completion callbacks
    private var lastCompletionTime = 0L
    private val completionDebounceMs = 300L // Ignore duplicate completions within 300ms
    
    // Background file operation queue system - prevents blocking playback
    private sealed class FileOperation {
        data class Move(val file: MusicFile, val action: SortAction) : FileOperation()
    }
    
    // PHASE 2: Bounded channel to prevent memory leaks (was UNLIMITED)
    private val fileOperationChannel = Channel<FileOperation>(capacity = 100) // Max 100 pending operations
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val fileOperationDispatcher = Dispatchers.IO.limitedParallelism(1) // Single-threaded queue
    
    // Broadcast receiver for notification actions
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - received action: ${intent?.action}")
            when (intent?.action) {
                MusicService.ACTION_PLAY_PAUSE -> {
                    CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - ACTION_PLAY_PAUSE")
                    playPause()
                }
                MusicService.ACTION_PLAY_ITEM -> {
                    val tab = intent.getStringExtra(MusicService.EXTRA_TAB)
                    val uri = intent.getStringExtra(MusicService.EXTRA_URI)
                    if (tab != null && uri != null) playFromCar(tab, uri)
                }
                MusicService.ACTION_PLAY -> { if (!_isPlaying.value) playPause() else updateNotification() }
                MusicService.ACTION_PAUSE -> { if (_isPlaying.value) playPause() else updateNotification() }
                MusicService.ACTION_NEXT -> {
                    CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - ACTION_NEXT")
                    next()
                }
                MusicService.ACTION_PREVIOUS -> {
                    CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - ACTION_PREVIOUS")
                    previous()
                }
                MusicService.ACTION_LIKE -> {
                    CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - ACTION_LIKE")
                    sortCurrentFile(SortAction.LIKE)
                }
                MusicService.ACTION_DISLIKE -> {
                    CrashLogger.log("MusicViewModel", "📡 BROADCAST_RECEIVER - ACTION_DISLIKE")
                    sortCurrentFile(SortAction.DISLIKE)
                }
                "com.example.mobiledigger.ACTION_SEEK" -> {
                    val pos = intent.getLongExtra("position", 0L)
                    seekTo(pos)
                }
            }
        }
    }
    
    /**
     * True once a source has been loaded (even if it had 0 tracks) or the sorted playlists were
     * opened directly. Keeps the main screen (Liked / Rejected tabs) reachable when To Do is empty;
     * it used to fall back to the welcome page whenever To Do had no files.
     */
    private val _workspaceOpen = MutableStateFlow(false)
    val workspaceOpen: StateFlow<Boolean> = _workspaceOpen.asStateFlow()

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

    // After a seek, hold the target position until VLC's async seek catches up, so the
    // position loop doesn't overwrite it with the stale (pre-seek) value and snap the
    // seekbar back — which made seeks look like they didn't take effect.
    @Volatile private var suppressPositionPushUntil = 0L
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()


    // UI removal animation support: URIs fading out before actual removal
    /** Row fade-out time before a sorted row is removed (Settings → Animations → Remove). */
    private fun removeAnimMs(): Long = com.example.mobiledigger.ui.components.Motion.removeMs().toLong()
    private val _removingUris = MutableStateFlow<Set<Uri>>(emptySet())
    val removingUris: StateFlow<Set<Uri>> = _removingUris.asStateFlow()

    // Immediate UI-driven removal (used after swipe fade-out) without touching playback
    fun removeFromCurrentListByUri(uri: Uri) {
        when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> {
                val list = _musicFiles.value.toMutableList()
                val idx = list.indexOfFirst { it.uri == uri }
                if (idx != -1) {
                    list.removeAt(idx)
                    _musicFiles.value = list
                    if (idx < _currentIndex.value) _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
                }
            }
            PlaylistTab.LIKED -> {
                val list = _likedFiles.value.toMutableList()
                val idx = list.indexOfFirst { it.uri == uri }
                if (idx != -1) {
                    list.removeAt(idx)
                    _likedFiles.value = list
                    if (_currentPlaylistTab.value == PlaylistTab.LIKED && idx < _currentIndex.value) _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
                }
            }
            PlaylistTab.REJECTED -> {
                val list = _rejectedFiles.value.toMutableList()
                val idx = list.indexOfFirst { it.uri == uri }
                if (idx != -1) {
                    list.removeAt(idx)
                    _rejectedFiles.value = list
                    if (_currentPlaylistTab.value == PlaylistTab.REJECTED && idx < _currentIndex.value) _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
                }
            }
        }
    }

    // Play the next item that shifts into currentIndex after the current one was removed
    fun playNextAfterRemoval() {
        viewModelScope.launch {
            // Faster transition: minimal delay and quicker fade
            // Ensure UI reflow happens first
            // Removed extra delay to speed up next playback
            try { audioManager.fadeOut(50) } catch (_: Exception) {}
            val files = when (_currentPlaylistTab.value) {
                PlaylistTab.TODO -> _musicFiles.value
                PlaylistTab.LIKED -> _likedFiles.value
                PlaylistTab.REJECTED -> _rejectedFiles.value
            }
            if (files.isEmpty()) {
                _isPlaying.value = false
                updateNotification()
                return@launch
            }
            // Robust advance. Normally removeFromCurrentListByUri() has already pulled the
            // just-actioned file out, so the next file shifted into _currentIndex and we
            // simply clamp. But if the audible file is somehow still in the list (a stale
            // index / desync), advance PAST it by URI so we never re-load the same track
            // (which manifested as the player "restarting" the current track).
            val playingUri = _currentPlayingFile.value?.uri
            val stillPresent = if (playingUri != null) files.indexOfFirst { it.uri == playingUri } else -1
            _currentIndex.value = if (stillPresent != -1) {
                (stillPresent + 1) % files.size
            } else {
                _currentIndex.value.coerceIn(0, files.size - 1)
            }
            loadCurrentFile()
            // Start new file at 0 volume then fade in
            audioManager.setVolume(0f)
            audioManager.resume()
            try { audioManager.fadeIn(300) } catch (_: Exception) {}
            updateNotification()
        }
    }

    // Run post-removal actions from a stable scope so row Composable removal doesn't cancel the flow
    fun handleRowSwipeAfterRemoval(file: MusicFile, action: SortAction, wasActiveRow: Boolean) {
        viewModelScope.launch {
            try {
                // Small reflow delay before actions to let list settle visually
                delay(120)
                if (wasActiveRow && action == SortAction.DISLIKE) {
                    // Advance playback first, then move the file in background
                    playNextAfterRemoval()
                    // Give next a brief head start to avoid jank
                    delay(80)
                }
                // Perform the sort (LIKE/DISLIKE) - already robust and guarded internally
                sortMusicFile(file, action)
            } catch (e: Exception) {
                handleError("handleRowSwipeAfterRemoval", e)
            }
        }
    }
    
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

    /**
     * To Do tracks that were already sorted: same name (ignoring case and "(1)" copy suffixes)
     * and same size as a file in Liked / Rejected now, or one sorted earlier (SortHistory).
     * Value: 'L' = liked before, 'R' = rejected before.
     */
    val alreadySorted: StateFlow<Map<Uri, Char>> = kotlinx.coroutines.flow.combine(
        _musicFiles, _likedFiles, _rejectedFiles, com.example.mobiledigger.util.SortHistory.version
    ) { todo, liked, rejected, _ ->
        val H = com.example.mobiledigger.util.SortHistory
        fun k(f: MusicFile) = H.normName(f.name) + "|" + f.size
        val likedKeys = liked.filter { it.size > 0 }.mapTo(HashSet()) { k(it) }
        val rejectedKeys = rejected.filter { it.size > 0 }.mapTo(HashSet()) { k(it) }
        val sortedUris = HashSet<Uri>().apply { liked.forEach { add(it.uri) }; rejected.forEach { add(it.uri) } }
        val out = HashMap<Uri, Char>()
        for (f in todo) {
            if (f.size <= 0 || f.uri in sortedUris) continue
            val key = k(f)
            val v = when {
                key in rejectedKeys -> 'R'
                key in likedKeys -> 'L'
                else -> H.lookup(f.name, f.size)
            }
            if (v != null) out[f.uri] = v
        }
        out as Map<Uri, Char>
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyMap())
    
    // Liked playlist subfolder filter (null or empty = no filter, show all liked)
    private val _likedFilter = MutableStateFlow<List<String>?>(null)
    val likedFilter: StateFlow<List<String>?> = _likedFilter.asStateFlow()
    
    // Sort & filter
    private val _sortOrder = MutableStateFlow<SortOrder?>(null)
    val sortOrder: StateFlow<SortOrder?> = _sortOrder.asStateFlow()

    private val _losslessOnly = MutableStateFlow(false)
    val losslessOnly: StateFlow<Boolean> = _losslessOnly.asStateFlow()

    private val _lossyOnly = MutableStateFlow(false)
    val lossyOnly: StateFlow<Boolean> = _lossyOnly.asStateFlow()

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
    
    // Lossless extensions for filter
    private val losslessExtensions = setOf("flac", "wav", "aiff", "aif", "alac", "ape", "dsd", "dsf", "dff", "wv")
    // Lossy extensions for filter (treated by extension; m4a is grouped as lossy here)
    private val lossyExtensions = setOf("mp3", "aac", "m4a", "ogg", "oga", "opus", "wma")

    // Computed StateFlow for current playlist files - reactive to tab changes, sort, and filter
    val currentPlaylistFiles: StateFlow<List<MusicFile>> = combine(
        combine(_musicFiles, _likedFiles, _rejectedFiles, _currentPlaylistTab) { musicFiles, likedFiles, rejectedFiles, currentTab ->
            when (currentTab) {
                PlaylistTab.TODO -> musicFiles
                PlaylistTab.LIKED -> likedFiles
                PlaylistTab.REJECTED -> rejectedFiles
            }
        },
        _sortOrder,
        _losslessOnly,
        _lossyOnly
    ) { files, sort, lossless, lossy ->
        var result = files
        if (lossless) {
            result = result.filter { f ->
                val ext = f.name.substringAfterLast('.', "").lowercase()
                ext in losslessExtensions
            }
        }
        if (lossy) {
            result = result.filter { f ->
                val ext = f.name.substringAfterLast('.', "").lowercase()
                ext in lossyExtensions
            }
        }
        if (sort != null) {
            result = when (sort.field) {
                SortField.NAME -> if (sort.ascending) result.sortedBy { it.name.lowercase() } else result.sortedByDescending { it.name.lowercase() }
                SortField.FILE_SIZE -> if (sort.ascending) result.sortedBy { it.size } else result.sortedByDescending { it.size }
                SortField.DATE_ADDED -> if (sort.ascending) result.sortedBy { it.dateAdded } else result.sortedByDescending { it.dateAdded }
                SortField.DURATION -> if (sort.ascending) result.sortedBy { it.duration } else result.sortedByDescending { it.duration }
                SortField.EXTENSION -> if (sort.ascending) result.sortedBy { it.name.substringAfterLast('.', "").lowercase() } else result.sortedByDescending { it.name.substringAfterLast('.', "").lowercase() }
                SortField.SUBFOLDER -> if (sort.ascending) result.sortedBy { (it.subfolder ?: "").lowercase() } else result.sortedByDescending { (it.subfolder ?: "").lowercase() }
                SortField.RANDOM -> result.shuffled()
            }
        }
        result
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

    // Recent destination folders (URIs as strings), max 10
    private val _recentDestinationUris = MutableStateFlow<List<String>>(emptyList())
    val recentDestinationUris: StateFlow<List<String>> = _recentDestinationUris.asStateFlow()
    
    // Tracking played files with no action in current session
    private val _playedButNotActioned = MutableStateFlow<Set<Uri>>(emptySet())
    val playedButNotActioned: StateFlow<Set<Uri>> = _playedButNotActioned.asStateFlow()
    
    init {
        // Plain-language notes from network-drive actions (e.g. a delete the drive refused)
        fileManager.onDriveNotice = { msg -> _errorMessage.value = msg }
        try {
            CrashLogger.log("MusicViewModel", "=== APP STARTED ===")
            
            // Reset session counters on app start
            preferences.resetSessionCounters()
            CrashLogger.log("MusicViewModel", "Session counters reset")
            
            // Initialize audio manager first
            audioManager.initialize()
            audioManager.setPlaybackCompletionListener(this) // Register listener
            startPositionUpdates()
            
            // Start background file operation worker
            startFileOperationWorker()
            CrashLogger.log("MusicViewModel", "File operation worker started")
            
            // Load playlists at startup
            loadLikedFiles()
            loadRejectedFiles()
            
            // Initialize subfolder management
            loadSubfolderHistory()
            updateSubfolderInfo()
            
            // Clear caches periodically to prevent memory buildup
            startPeriodicCacheClearing()
            
            // PHASE 5: Start performance stat logging
            startPerformanceStatLogging()

            // Load recent sources and destinations from preferences
            loadRecentSources()
            loadRecentDestinations()
            
            // Start service and register receiver in background to avoid blocking
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    ensureMusicServiceStarted()
                    withContext(Dispatchers.Main) {
                        registerNotificationReceiver()
                        publishLibraryForCar()
                    }
                    // Force notification update after service starts
                    delay(1000) // Wait 1 second for service to initialize
                    updateNotification()
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Failed to start service or register receiver", e)
                }
            }
            
            // Check for pending external audio files
            checkForPendingExternalAudio()
            
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
                        CrashLogger.log("MusicViewModel", "Restored destination folder: ${fileManager.getDestinationPath()}")
                        android.util.Log.d("MusicViewModel", "Restored destination folder: ${fileManager.getDestinationPath()}")
                        
                        // Update subfolder information when destination is restored
                        updateSubfolderInfo()
                    } else {
                        // Clear invalid destination folder
                        CrashLogger.log("MusicViewModel", "Destination folder is not accessible, clearing it")
                        preferences.setDestinationRootUri(null)
                        _destinationPath.value = ""
                        _destinationFolder.value = null
                    }
                }
            }
            
            // Restore source folder reference (optionally auto-scan based on setting)
            preferences.getSourceRootUri()?.let { saved ->
                runCatching {
                    val uri = Uri.parse(saved)
                    android.util.Log.d("MusicViewModel", "Source folder available: $saved")
                    CrashLogger.log("MusicViewModel", "Source folder available: $saved")
                    
                    // Just restore the folder reference without scanning
                    fileManager.setSelectedFolder(uri)
                    android.util.Log.d("MusicViewModel", "Source folder restored (use Rescan to load files)")

                    // Auto-scan last source on start if enabled
                    if (isAutoScanLastSourceEnabled()) {
                        rescanSourceFolder()
                    }
                }
            }
            CrashLogger.log("MusicViewModel", "Initialized successfully")
            
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to initialize", e)
            _errorMessage.value = "Failed to initialize audio: ${e.message}"
        }
    }
    
    /**
     * Background file operation worker - processes file moves in queue
     * This prevents blocking the UI and audio playback during file operations
     */
    private fun startFileOperationWorker() {
        viewModelScope.launch(fileOperationDispatcher) {
            CrashLogger.log("FileOperationWorker", "Worker started, waiting for operations...")
            for (operation in fileOperationChannel) {
                try {
                    when (operation) {
                        is FileOperation.Move -> {
                            CrashLogger.log("FileOperationWorker", "Processing move: ${operation.file.name} -> ${operation.action}")
                            
                            if (!fileManager.isDestinationSelected() && !com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(operation.file.uri)) {
                                CrashLogger.log("FileOperationWorker", "No destination folder selected, skipping move")
                                continue
                            }
                            
                            // Serialize against the direct sort paths (sortMusicFile /
                            // sortAtIndex / sortSelectedFiles), which all hold this mutex.
                            // Without it a queued move and a direct move can run at the
                            // same time and collide on the destination folder (duplicate
                            // unique-name resolution, concurrent createFile/delete).
                            val success = fileOperationMutex.withLock {
                                fileManager.sortFile(operation.file, operation.action)
                            }

                            if (success) {
                                CrashLogger.log("FileOperationWorker", "Successfully moved: ${operation.file.name}")
                            } else {
                                CrashLogger.log("FileOperationWorker", "Failed to move: ${operation.file.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    CrashLogger.log("FileOperationWorker", "Error processing operation", e)
                    // Continue processing next operation even if one fails
                }
            }
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


    private fun loadRecentDestinations() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("recent_destinations", Context.MODE_PRIVATE)
            val csv = prefs.getString("uris", "") ?: ""
            val list = if (csv.isNotBlank()) csv.split("|").filter { it.isNotBlank() } else emptyList()
            _recentDestinationUris.value = list.take(10)
        } catch (_: Exception) {}
    }

    private fun saveRecentDestinations() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("recent_destinations", Context.MODE_PRIVATE)
            prefs.edit().putString("uris", _recentDestinationUris.value.take(10).joinToString("|")).apply()
        } catch (_: Exception) {}
    }

    private fun addRecentDestination(uri: Uri) {
        val s = uri.toString()
        val current = _recentDestinationUris.value.toMutableList()
        current.remove(s)
        current.add(0, s)
        _recentDestinationUris.value = current.take(10)
        saveRecentDestinations()
    }

    fun removeRecentDestination(uriString: String) {
        val current = _recentDestinationUris.value.toMutableList()
        current.remove(uriString)
        _recentDestinationUris.value = current
        saveRecentDestinations()
    }

    fun removeRecentSource(uriString: String) {
        val current = _recentSourceUris.value.toMutableList()
        current.remove(uriString)
        _recentSourceUris.value = current
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
        // Picking a phone folder while set to "network only" means: show me this folder.
        if (preferences.getSourceMode() == "NETWORK") setSourceMode("PHONE")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                // Start fresh like a new session
                _sortResults.value = emptyList()
                stopPlayback()
                _currentIndex.value = 0
                _musicFiles.value = emptyList()
                _playedButNotActioned.value = emptySet() // Clear tracked files when loading new folder
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
                _archives.value = fileManager.lastArchives
                _workspaceOpen.value = true
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
                    if (isAutoPlayAfterSelectEnabled()) {
                        // Automatically load and start playing the first song
                        loadCurrentFile()
                        val destInfo = if (fileManager.getDestinationFolder() != null) {
                            " Destination: ${fileManager.getDestinationPath()}"
                        } else {
                            " Please select destination folder from menu."
                        }
                        _errorMessage.value = "Loaded ${files.size} tracks. Now playing first track.$destInfo"
                    } else {
                        _errorMessage.value = "Loaded ${files.size} tracks from source."
                    }
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

    private fun isAutoPlayAfterSelectEnabled(): Boolean {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("visual_settings", Context.MODE_PRIVATE)
            prefs.getBoolean("auto_play_first_after_select", false)
        } catch (_: Exception) { false }
    }

    private fun isAutoScanLastSourceEnabled(): Boolean {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("visual_settings", Context.MODE_PRIVATE)
            prefs.getBoolean("auto_scan_last_source_on_start", false)
        } catch (_: Exception) { false }
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
                addRecentDestination(uri)
                CrashLogger.log("MusicViewModel", "Saved destination folder URI: $uri")
                CrashLogger.log("MusicViewModel", "Set destination folder: ${fileManager.getDestinationPath()}")
                
                // Reload playlists when destination is selected
                loadLikedFiles()
                loadRejectedFiles()
                
                // Update subfolder information when destination is selected
                updateSubfolderInfo()

                if (fileManager.isDestinationInsideSource()) {
                    _errorMessage.value = "Destination is inside the source folder; this may cause issues. Pick a parent folder as destination."
                }
                
                // Test if destination is actually accessible
                val destFolder = fileManager.getDestinationFolder()
                CrashLogger.log("MusicViewModel", "Destination folder object: $destFolder")
                CrashLogger.log("MusicViewModel", "Destination folder exists: ${destFolder?.exists()}")
                CrashLogger.log("MusicViewModel", "Destination folder canRead: ${destFolder?.canRead()}")
                CrashLogger.log("MusicViewModel", "Destination folder canWrite: ${destFolder?.canWrite()}")
                
                // Ensure subfolders are created immediately
                fileManager.ensureSubfolders()
                CrashLogger.log("MusicViewModel", "Subfolders ensured for destination")
                
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error setting destination", e)
                _errorMessage.value = "Error setting destination: ${e.message}"
            }
        }
    }
    
    // Reset app to initial state (like when first opened)
    fun resetToInitialState() {
        viewModelScope.launch {
            try {
                CrashLogger.log("MusicViewModel", "Resetting to initial state")
                
                // Stop any current playback
                stopPlayback()
                
                // Clear all music files and playlists
                _workspaceOpen.value = false
                _musicFiles.value = emptyList()
                _likedFiles.value = emptyList()
                _rejectedFiles.value = emptyList()
                // Archives count as "something loaded" on the main screen: if they stay, the
                // welcome page never comes back and the empty list fills the screen.
                _archives.value = emptyList()
                _unpackDone.value = null
                _currentIndex.value = 0
                _currentPlaylistTab.value = PlaylistTab.TODO
                
                // Clear source folder selection
                preferences.setSourceRootUri(null)
                fileManager.clearSelectedFolder()
                
                // Clear search results
                _searchResults.value = emptyList()
                _searchText.value = ""
                
                // Clear error messages
                _errorMessage.value = null
                _isLoading.value = false
                
                // Clear sort results
                _sortResults.value = emptyList()
                
                // Clear multi-selection state
                _selectedIndices.value = emptySet()
                _isMultiSelectionMode.value = false
                
                // Clear subfolder states
                _subfolders.value = emptyList()
                _subfolderHistory.value = emptyList()
                _availableSubfolders.value = emptyList()
                _subfolderFileCounts.value = emptyMap()
                
                // Clear caches
                audioManager.clearAllCaches()
                
                CrashLogger.log("MusicViewModel", "Successfully reset to initial state")
                _errorMessage.value = "Welcome! Select a source folder to get started."
                
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error resetting to initial state", e)
                _errorMessage.value = "Error resetting app: ${e.message}"
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
            pausedByAudioFocusLoss = false
            systemAudioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            // Request audio focus, but resume REGARDLESS of the result (like VLC does) —
            // gating resume on a GRANTED result made the play button do nothing whenever
            // the focus request was denied.
            systemAudioManager.requestAudioFocus(audioFocusRequest)
            audioManager.resume()
            _isPlaying.value = true
            pausedByAudioFocusLoss = false
        }
        updateNotification()
    }

    fun next() {
        CrashLogger.log("MusicViewModel", "🔄 NEXT() called - starting next track")
        viewModelScope.launch {
            try {
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - getting current files")
                val currentFiles = when (_currentPlaylistTab.value) {
                    PlaylistTab.TODO -> _musicFiles.value
                    PlaylistTab.LIKED -> _likedFiles.value
                    PlaylistTab.REJECTED -> _rejectedFiles.value
                }
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - current files count: ${currentFiles.size}")
                
                if (currentFiles.isEmpty()) {
                    CrashLogger.log("MusicViewModel", "🔄 NEXT() - no files in playlist")
                    _errorMessage.value = "No files in current playlist to navigate"
                    return@launch
                }
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - starting transition")
                // Start smooth transition
                _isTransitioning.value = true
                
                // Quick fade out for smooth transition (120Hz optimized)
                if (_isPlaying.value) {
                    CrashLogger.log("MusicViewModel", "🔄 NEXT() - pausing current playback")
                    _isPlaying.value = false
                    try {
                        audioManager.pause()
                        CrashLogger.log("MusicViewModel", "🔄 NEXT() - pause successful")
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "🔄 NEXT() - error pausing during transition", e)
                    }
                }
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - waiting 50ms for transition")
                delay(50) // Brief pause for 120Hz smooth animation
                
                val currentIndex = _currentIndex.value
                val nextIndex = (currentIndex + 1) % currentFiles.size
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - moving from index $currentIndex to $nextIndex")
                _currentIndex.value = nextIndex
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - calling loadCurrentFile()")
                loadCurrentFile()
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - updating notification")
                updateNotification()
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - waiting 100ms for file load")
                delay(100) // Allow new file to load
                
                CrashLogger.log("MusicViewModel", "🔄 NEXT() - completing transition")
                _isTransitioning.value = false
                CrashLogger.log("MusicViewModel", "✅ NEXT() completed successfully")
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "💥 NEXT() - exception occurred", e)
                _isTransitioning.value = false
                _errorMessage.value = "Error moving to next track: ${e.message}"
            }
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
    
    fun jumpTo(index: Int) {
        // `index` is a position in the displayed (filtered + sorted) list. Resolve the
        // target file there, then translate to the raw-list index that _currentIndex and
        // next()/previous()/loadCurrentFile() operate on.
        val displayFiles = currentPlaylistFiles.value
        val target = displayFiles.getOrNull(index)
        if (target == null) {
            _errorMessage.value = "Invalid index: $index (playlist has ${displayFiles.size} files)"
            return
        }
        val rawFiles = when (_currentPlaylistTab.value) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        val rawIndex = rawFiles.indexOfFirst { it.uri == target.uri }
        if (rawIndex < 0) {
            _errorMessage.value = "Could not locate selected file"
            return
        }
        _currentIndex.value = rawIndex
        loadCurrentFile()
    }
    
    fun seekTo(position: Long) {
        // Optimistically reflect the target immediately and suppress the position loop's
        // overwrite for a short window so the seekbar doesn't revert before VLC catches up.
        _currentPosition.value = position
        suppressPositionPushUntil = android.os.SystemClock.elapsedRealtime() + 700
        audioManager.seekTo(position)
        try { updateNotificationPosition() } catch (_: Exception) {}
    }
    
    
    fun sortCurrentFile(action: SortAction) {
        val fileToSort = currentFile // Use the currently playing file
        if (fileToSort == null) {
            _errorMessage.value = "No current file selected to sort"
            return
        }
        
        CrashLogger.log("MusicViewModel", "sortCurrentFile: ${fileToSort.name} with action $action")
        
        // Remove from played-but-not-actioned when action is taken
        _playedButNotActioned.value = _playedButNotActioned.value - fileToSort.uri
        CrashLogger.log("MusicViewModel", "🔢 Removed ${fileToSort.name} from playedButNotActioned counter (sortCurrentFile)")
        
        // ⚡ INSTANT PLAYBACK: Process UI and playback immediately, file move happens in background
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // 1. Queue the file operation in background (non-blocking)
                if (fileManager.isDestinationSelected() || com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(fileToSort.uri)) {
                    fileOperationChannel.send(FileOperation.Move(fileToSort, action))
                    CrashLogger.log("MusicViewModel", "File operation queued: ${fileToSort.name}")
                } else {
                    _errorMessage.value = "Please select a destination folder from the 'Actions' menu before sorting."
                }
                
                // 2. Immediately update UI and counters
                handleSuccessfulSort(fileToSort, action)
                
                // 3. Immediately move to next track when disliking from main player
                // The file will be moved in background while next track plays
                if (action == SortAction.DISLIKE) {
                    // Small delay to let UI animate the removal marker without jank
                    viewModelScope.launch {
                        delay(80)
                        try {
                            next()
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error advancing to next after dislike", e)
                        }
                    }
                }
                
            } catch (e: Exception) {
                handleError("sortCurrentFile", e)
            }
        }
    }

    fun sortMusicFile(file: MusicFile, action: SortAction) {
        CrashLogger.log("MusicViewModel", "🔍 sortMusicFile called: ${file.name} with action=$action")
        
        // Remove from played-but-not-actioned when action is taken
        _playedButNotActioned.value = _playedButNotActioned.value - file.uri
        
        if (!fileManager.isDestinationSelected() && !com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(file.uri)) {
            _errorMessage.value = "Please select a destination folder from the 'Actions' menu before sorting." // More specific message
            CrashLogger.log("MusicViewModel", "❌ No destination folder selected")
            return
        }
        
        CrashLogger.log("MusicViewModel", "📁 Sorting file: ${file.name} with action $action, source: ${file.sourcePlaylist}")
        
        viewModelScope.launch {
            // Use mutex to prevent concurrent file operations
            fileOperationMutex.withLock {
                CrashLogger.log("MusicViewModel", "🔒 Acquired file operation mutex for single file")
                try {
                    CrashLogger.log("MusicViewModel", "🔄 Calling fileManager.sortFile for ${file.name}")
                    val success = fileManager.sortFile(file, action)
                    CrashLogger.log("MusicViewModel", "📁 FileManager.sortFile result: success=$success for ${file.name}")
                    
                    if (success) {
                        CrashLogger.log("MusicViewModel", "✅ File sorted successfully, calling handleSuccessfulSort")
                        handleSuccessfulSort(file, action)
                    } else {
                        _errorMessage.value = "Failed to sort file. Please check folder permissions."
                        CrashLogger.log("MusicViewModel", "❌ File sorting failed: ${file.name}")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "💥 Exception in sortMusicFile for ${file.name}", e)
                    handleError("sortMusicFile", e)
                } finally {
                    CrashLogger.log("MusicViewModel", "🔓 Releasing file operation mutex for single file")
                }
            }
        }
    }
    
    // Safe version that preserves playback priority

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
        
        // Determine which list the file came from and remove it with fade-out delay
        _removingUris.value = _removingUris.value + sortedFile.uri
        viewModelScope.launch {
            delay(removeAnimMs())
            _removingUris.value = _removingUris.value - sortedFile.uri
        }
        when (sortedFile.sourcePlaylist) {
            PlaylistTab.TODO -> {
                viewModelScope.launch {
                    delay(removeAnimMs())
                val updatedFiles = _musicFiles.value.toMutableList()
                val indexToRemove = updatedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedFiles.removeAt(indexToRemove)
                    _musicFiles.value = updatedFiles

                        // Do NOT auto-advance; keep playback as-is. Only shift index if item before current was removed.
                        if (sortedFile.uri != currentFile?.uri && indexToRemove < _currentIndex.value) {
                        _currentIndex.value = _currentIndex.value - 1 // Shift index if file before it was removed
                        }
                    }
                }
            }
            PlaylistTab.LIKED -> {
                viewModelScope.launch {
                    delay(removeAnimMs())
                val updatedLikedFiles = _likedFiles.value.toMutableList()
                val indexToRemove = updatedLikedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedLikedFiles.removeAt(indexToRemove)
                    _likedFiles.value = updatedLikedFiles
                    
                        // Do NOT auto-advance; keep playback as-is. Only shift index if item before current was removed.
                        if (!(sortedFile.uri == currentFile?.uri && _currentPlaylistTab.value == PlaylistTab.LIKED) && indexToRemove < _currentIndex.value && _currentPlaylistTab.value == PlaylistTab.LIKED) {
                        _currentIndex.value = _currentIndex.value - 1
                        }
                    }
                }
            }
            PlaylistTab.REJECTED -> {
                viewModelScope.launch {
                    delay(removeAnimMs())
                val updatedRejectedFiles = _rejectedFiles.value.toMutableList()
                val indexToRemove = updatedRejectedFiles.indexOfFirst { it.uri == sortedFile.uri }
                if (indexToRemove != -1) {
                    updatedRejectedFiles.removeAt(indexToRemove)
                    _rejectedFiles.value = updatedRejectedFiles
                    
                        // Do NOT auto-advance; keep playback as-is. Only shift index if item before current was removed.
                        if (!(sortedFile.uri == currentFile?.uri && _currentPlaylistTab.value == PlaylistTab.REJECTED) && indexToRemove < _currentIndex.value && _currentPlaylistTab.value == PlaylistTab.REJECTED) {
                        _currentIndex.value = _currentIndex.value - 1
                        }
                    }
                }
            }
        }
        
        // Reload playlists after sorting immediately to ensure UI consistency
        refreshAllPlaylists()
        
        _errorMessage.value = if (action == SortAction.LIKE) "Moved to 'Liked'" else "Moved to 'Rejected'"
    }

    fun sortAtIndex(index: Int, action: SortAction) {
        // `index` is a row in the displayed (filtered + sorted) list, which is what the
        // UI builds it from. Resolve the file there so the correct row is actioned.
        val files = currentPlaylistFiles.value

        if (index !in files.indices) {
            _errorMessage.value = "Invalid index for sorting: $index"
            return
        }

        val fileToSort = files[index]
        
        // Debug logging
        CrashLogger.log("MusicViewModel", "🔍 sortAtIndex: index=$index, file='${fileToSort.name}', action=$action, playlistSize=${files.size}")
        
        // Remove from played-but-not-actioned when action is taken
        _playedButNotActioned.value = _playedButNotActioned.value - fileToSort.uri
        CrashLogger.log("MusicViewModel", "🔢 Removed ${fileToSort.name} from playedButNotActioned counter (sortAtIndex)")
        
        // Use queue-based sorting for instant response
        viewModelScope.launch(Dispatchers.Main) {
            // Use mutex to prevent concurrent file operations
            fileOperationMutex.withLock {
                try {
                    // Queue the file operation in background (non-blocking)
                    if (fileManager.isDestinationSelected() || com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(fileToSort.uri)) {
                        fileOperationChannel.send(FileOperation.Move(fileToSort, action))
                        CrashLogger.log("MusicViewModel", "File operation queued: ${fileToSort.name}")
                    } else {
                        _errorMessage.value = "Please select a destination folder from the 'Actions' menu before sorting."
                        return@withLock
                    }
                    
                    // Immediately update UI
                    handleSuccessfulSort(fileToSort, action)
                    
                } catch (e: Exception) {
                    handleError("sortAtIndex", e)
                }
            }
        }
    }
    
    fun movePlayedButNotActionedToRejected() {
        CrashLogger.log("MusicViewModel", "🔍 movePlayedButNotActionedToRejected called with ${_playedButNotActioned.value.size} files")
        
        val playedUris = _playedButNotActioned.value
        if (playedUris.isEmpty()) {
            CrashLogger.log("MusicViewModel", "No played-but-not-actioned files to move")
            return
        }
        
        // Exclude the currently playing file
        val currentPlayingUri = _currentPlayingFile.value?.uri
        val urisToReject = if (currentPlayingUri != null) {
            playedUris - currentPlayingUri
        } else {
            playedUris
        }
        
        CrashLogger.log("MusicViewModel", "After excluding current file: ${urisToReject.size} files to reject")
        
        if (urisToReject.isEmpty()) {
            CrashLogger.log("MusicViewModel", "No files to reject after excluding current file")
            return
        }
        
        // Get the files from TODO playlist that match the played URIs
        val filesToReject = _musicFiles.value.filter { it.uri in urisToReject }
        
        CrashLogger.log("MusicViewModel", "Found ${filesToReject.size} files to reject from TODO playlist")
        
        if (filesToReject.isEmpty()) {
            CrashLogger.log("MusicViewModel", "No matching files found in TODO playlist")
            return
        }
        
        if (!fileManager.isDestinationSelected() && filesToReject.any { !com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(it.uri) }) {
            _errorMessage.value = "Please select a destination folder before rejecting."
            return
        }

        // All the rows fade + slide out together, then are removed in one go (the rest glide up),
        // the list scrolls back to the top, and the files move in the background with a progress bar.
        val rejectUris = filesToReject.map { it.uri }.toSet()
        _playedButNotActioned.value = _playedButNotActioned.value - urisToReject
        _rejectListenedProgress.value = BulkSortProgress(0, filesToReject.size, toLiked = false)
        _removingUris.value = _removingUris.value + rejectUris

        viewModelScope.launch {
            delay(removeAnimMs())
            val playingUri = _currentPlayingFile.value?.uri
            val remaining = _musicFiles.value.filterNot { it.uri in rejectUris }
            _musicFiles.value = remaining
            _removingUris.value = _removingUris.value - rejectUris
            val newIdx = if (playingUri != null) remaining.indexOfFirst { it.uri == playingUri } else -1
            _currentIndex.value = when {
                newIdx != -1 -> newIdx
                remaining.isEmpty() -> 0
                else -> _currentIndex.value.coerceIn(0, remaining.size - 1)
            }
            _scrollToTop.tryEmit(Unit)
        }

        viewModelScope.launch(Dispatchers.IO) {
            delay(removeAnimMs())
            val failed = mutableListOf<MusicFile>()
            fileOperationMutex.withLock {
                filesToReject.forEachIndexed { i, file ->
                    val ok = try { fileManager.sortFile(file, SortAction.DISLIKE) } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "Reject listened failed: ${file.name}", e); false
                    }
                    if (!ok) failed += file
                    _rejectListenedProgress.value = BulkSortProgress(i + 1, filesToReject.size, toLiked = false)
                }
            }
            withContext(Dispatchers.Main) {
                if (failed.isNotEmpty()) {
                    val cur = _musicFiles.value
                    _musicFiles.value = cur + failed.filter { f -> cur.none { it.uri == f.uri } }
                    _errorMessage.value = "Rejected ${filesToReject.size - failed.size}, ${failed.size} couldn't be moved."
                }
                // Success needs no extra message: the progress banner already ends with "✓ N moved to Rejected"
                loadRejectedFiles()
                CrashLogger.log("MusicViewModel", "Reject listened: ${filesToReject.size - failed.size} moved, ${failed.size} failed")
            }
            finishBulkProgress()
        }
    }

    /** Ask the playlist to scroll back to the top (e.g. after "Reject listened"). */
    private val _scrollToTop = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTop: kotlinx.coroutines.flow.SharedFlow<Unit> = _scrollToTop

    /** Files being moved to Liked / Rejected in the background (Reject listened, multi-select). */
    data class BulkSortProgress(val done: Int, val total: Int, val toLiked: Boolean)

    /** Progress of a background bulk move; null when idle. The banner is the only message for it. */
    private val _rejectListenedProgress = MutableStateFlow<BulkSortProgress?>(null)
    val rejectListenedProgress: StateFlow<BulkSortProgress?> = _rejectListenedProgress.asStateFlow()

    /** Lets the bar reach 100 %, then hides the banner (no separate "N moved" message). */
    private suspend fun finishBulkProgress() {
        kotlinx.coroutines.delay(450)
        _rejectListenedProgress.value = null
    }

    fun undoLastAction() {
        val last = _sortResults.value.lastOrNull() ?: return
        viewModelScope.launch {
            try {
                val restored = fileManager.undoSort(last)
                if (restored != null) {
                    com.example.mobiledigger.util.SortHistory.forget(last.musicFile)
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
        CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() called")
        try {
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - getting files for playlist: ${_currentPlaylistTab.value}")
            val files = when (_currentPlaylistTab.value) {
                PlaylistTab.TODO -> _musicFiles.value
                PlaylistTab.LIKED -> _likedFiles.value
                PlaylistTab.REJECTED -> _rejectedFiles.value
            }
            val index = _currentIndex.value
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - files count: ${files.size}, current index: $index")
            
            if (files.isEmpty() || index !in files.indices) {
                CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - invalid state: empty files or invalid index")
                return
            }
            
            val currentFile = files[index]
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - loading file: ${currentFile.name}")
        
            // Store current file for notification
            this.currentFile = currentFile
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - stored current file")
            
            // Update the actually playing file (independent of playlist navigation)
            _currentPlayingFile.value = currentFile
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - updated current playing file to: ${currentFile.name} (URI: ${currentFile.uri})")
            
            // Pre-fetch next file for buffering (only if list has more than 1 item)
            val nextFile = if (files.size > 1) files[(_currentIndex.value + 1) % files.size] else null
            if (nextFile != null) {
                CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - preloaded next file: ${nextFile.name}")
            }
            
            // Check if it's an AIFF file and provide specific feedback
            val isAiffFile = currentFile.name.lowercase().endsWith(".aif") || currentFile.name.lowercase().endsWith(".aiff")
            if (isAiffFile) {
                CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - AIFF file detected: ${currentFile.name}")
            }
        
            // Check memory before loading file and clear caches if needed
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - checking memory pressure")
            checkMemoryPressureAndCleanup()
            
            // Additional memory check specifically for waveform and file operations
            val runtime = Runtime.getRuntime()
            val availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - available memory: ${availableMemoryMB}MB")
            if (availableMemoryMB < 50) {
                CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - critical low memory ($availableMemoryMB MB) - stopping file processing")
                return
            }
            
            // Request audio focus before playing
            systemAudioManager.requestAudioFocus(audioFocusRequest)
            pausedByAudioFocusLoss = false

            // Play off main thread; coalesce UI updates
            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - calling audioManager.playFile() [IO]")
            viewModelScope.launch(Dispatchers.IO) {
                val started = audioManager.playFile(currentFile)
                withContext(Dispatchers.Main) {
                    _isPlaying.value = started
                    if (!started) {
                        if (isAiffFile) {
                            _errorMessage.value = "Cannot play AIFF file. This format might not be supported by your device's audio codec."
                            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - AIFF playback failed for ${currentFile.name}")
                        } else {
                            _errorMessage.value = "Cannot start playback. Unsupported file or permission denied."
                            CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - playback failed for ${currentFile.name}")
                        }
                    } else {
                        _errorMessage.value = null
                        CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - playback started successfully for ${currentFile.name}")
                        if (_currentPlaylistTab.value == PlaylistTab.TODO) {
                            _playedButNotActioned.value = _playedButNotActioned.value + currentFile.uri
                            CrashLogger.log("MusicViewModel", "✅ Added ${currentFile.name} to played-but-not-actioned list. Total tracked: ${_playedButNotActioned.value.size}")
                        } else {
                            CrashLogger.log("MusicViewModel", "⏭️ Not tracking ${currentFile.name} - not in TODO playlist (current: ${_currentPlaylistTab.value})")
                        }
                        try { preferences.incrementListened() } catch (_: Exception) {}
                        updateNotification()
                    }
                }
                // Extract and update duration if not available
                if (currentFile.duration == 0L) {
                    try {
                        val actualDuration = extractDuration(currentFile)
                        CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - extracted duration (IO): ${actualDuration}ms")
                        if (actualDuration > 0) {
                            withContext(Dispatchers.Main) {
                                _duration.value = actualDuration
                                updateFileDuration(currentFile, actualDuration)
                                CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - duration updated")
                            }
                        }
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - error extracting duration", e)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _duration.value = currentFile.duration
                        CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - using existing duration: ${currentFile.duration}ms")
                    }
                }
                // Extract bitrate if not yet known
                if (currentFile.bitrate == 0) {
                    try {
                        val bitrate = extractBitrate(currentFile)
                        if (bitrate > 0) {
                            withContext(Dispatchers.Main) { updateFileBitrate(currentFile, bitrate) }
                        }
                    } catch (_: Exception) {}
                }
                // Preload next song (guarded)
                withContext(Dispatchers.Main) {
                    CrashLogger.log("MusicViewModel", "📁 LOAD_CURRENT_FILE() - starting preload next song (guarded)")
                }
                preloadNextSong()
                withContext(Dispatchers.Main) {
                    CrashLogger.log("MusicViewModel", "✅ LOAD_CURRENT_FILE() async flow completed")
                }
            }
            CrashLogger.log("MusicViewModel", "✅ LOAD_CURRENT_FILE() scheduled async work")
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "💥 LOAD_CURRENT_FILE() - exception occurred", e)
            _errorMessage.value = "Error loading current file: ${e.message}"
        }
    }
    
    private var isPreloadEnabled: Boolean = true
    
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
                
                // Preload next song if available (guard by size, VLC usage, recent GC)
                if (isPreloadEnabled && currentIdx + 1 < currentFiles.size) {
                    val nextFile = currentFiles[currentIdx + 1]
                    val sizeMb = nextFile.size / (1024 * 1024)
                    val skipBySize = sizeMb > 80
                    val skipByVlc = audioManager.isVlcActive()
                    val skipByGc = audioManager.isRecentGc(2000)
                    if (skipBySize || skipByVlc || skipByGc) {
                        CrashLogger.log("MusicViewModel", "Skipping preload (sizeMb=$sizeMb, vlc=$skipByVlc, recentGc=$skipByGc) for ${nextFile.name}")
                    } else {
                        preloadedFile = nextFile
                        CrashLogger.log("MusicViewModel", "Preloading next song: ${nextFile.name}")
                        try {
                            audioManager.preloadFile(nextFile)
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error calling audioManager.preloadFile", e)
                        }
                    }
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
    

    private fun extractBitrate(file: MusicFile): Int {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, file.uri)
                val bitrateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val bps = bitrateStr?.toIntOrNull() ?: 0
                bps / 1000 // Convert to kbps
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun updateFileBitrate(file: MusicFile, bitrate: Int) {
        val update = { list: List<MusicFile> ->
            list.map { if (it.uri == file.uri) it.copy(bitrate = bitrate) else it }
        }
        _musicFiles.value = update(_musicFiles.value)
        _likedFiles.value = update(_likedFiles.value)
        _rejectedFiles.value = update(_rejectedFiles.value)
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


    
    // Stopping the player fires a "track completed" event; it must not start the next track.
    private var ignoreCompletionUntil = 0L

    private fun stopPlayback() {
        ignoreCompletionUntil = System.currentTimeMillis() + 1000
        audioManager.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        pausedByAudioFocusLoss = false
        systemAudioManager.abandonAudioFocusRequest(audioFocusRequest)
    }
    
    private fun startPositionUpdates() {
        // Poll on IO: VLC's native position/duration getters aren't Main-bound, StateFlow
        // assignment is thread-safe, and the suppress guard is @Volatile — keeps ~30Hz
        // polling off the Main thread.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var notifTick = 0
                var lastNotifiedDuration = 0L
                while (isActive) { // Check if coroutine is still active
                    kotlinx.coroutines.delay(33) // Update every ~33ms for 120Hz displays (30fps UI updates)

                    if (_isPlaying.value && isActive) { // Double check we're still active
                        try {
                            val suppressed = android.os.SystemClock.elapsedRealtime() < suppressPositionPushUntil
                            if (!suppressed) {
                                // Skip while a recent seek is settling so we don't snap back.
                                _currentPosition.value = audioManager.getCurrentPosition()
                            }
                            _duration.value = audioManager.getDuration()
                            val dur = _duration.value
                            if (dur > 0 && kotlin.math.abs(dur - lastNotifiedDuration) > 1000) {
                                // Duration just became known (or changed for a new track) — push a
                                // FULL notification update so the seekbar appears immediately
                                // (it needs METADATA_KEY_DURATION). Previously the seekbar stayed
                                // hidden on the first track until the user pressed play/pause.
                                lastNotifiedDuration = dur
                                notifTick = 0
                                try { updateNotification() } catch (_: Exception) {}
                            } else if (!suppressed && ++notifTick >= 30) {
                                // Otherwise stream just the position ~once a second (no re-post).
                                notifTick = 0
                                try { updateNotificationPosition() } catch (_: Exception) {}
                            }
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
                                protected.addAll(audioManager.getProtectedTempPaths())
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
    // ----- Network drives (SMB / WebDAV / MobileDigger server) -----
    private val driveStore by lazy { com.example.mobiledigger.file.drives.DriveStore(context) }
    private val _drives = MutableStateFlow<List<com.example.mobiledigger.file.drives.NetworkDrive>>(emptyList())
    val drives: StateFlow<List<com.example.mobiledigger.file.drives.NetworkDrive>> = _drives.asStateFlow()
    private val _activeDriveId = MutableStateFlow<String?>(null)
    val activeDriveId: StateFlow<String?> = _activeDriveId.asStateFlow()

    data class DriveBrowseState(
        val driveId: String? = null,
        val path: String = "",
        val dirs: List<com.example.mobiledigger.file.drives.RemoteEntry> = emptyList(),
        val audioCount: Int = 0,
        val loading: Boolean = false,
        val error: String? = null
    )
    private val _driveBrowse = MutableStateFlow(DriveBrowseState())
    val driveBrowse: StateFlow<DriveBrowseState> = _driveBrowse.asStateFlow()

    fun refreshDrives() {
        _drives.value = driveStore.all()
        _activeDriveId.value = driveStore.activeId()
    }

    fun saveDrive(drive: com.example.mobiledigger.file.drives.NetworkDrive) {
        driveStore.save(drive)
        com.example.mobiledigger.file.drives.DriveProxy.invalidate(drive.id)
        refreshDrives()
    }

    fun deleteDrive(id: String) {
        val wasActive = driveStore.activeId() == id
        driveStore.delete(id)
        com.example.mobiledigger.file.drives.DriveProxy.invalidate(id)
        refreshDrives()
        if (wasActive) dropNetworkFiles()
    }

    /** Tries to list the drive's top folder. Returns null on success, or a readable error. */
    suspend fun testDrive(drive: com.example.mobiledigger.file.drives.NetworkDrive): String? =
        withContext(Dispatchers.IO) {
            val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
            try {
                client.list("")
                if (drive.type == com.example.mobiledigger.file.drives.DriveType.MOBILEDIGGER && drive.writeShare.isNotBlank()) {
                    // Check the SMB share used for writing, too
                    val host = runCatching { java.net.URI(if ("://" in drive.address) drive.address else "http://${drive.address}").host }.getOrNull()
                        ?: drive.address.substringBefore(':')
                    val smb = com.example.mobiledigger.file.drives.SmbDriveClient(
                        drive.copy(type = com.example.mobiledigger.file.drives.DriveType.SMB, address = host,
                            share = drive.writeShare, username = drive.writeUser, password = drive.writePassword)
                    )
                    try { smb.list("") } catch (e: Exception) {
                        return@withContext "The server works, but write access failed: ${e.message}"
                    } finally { runCatching { smb.close() } }
                }
                null
            } catch (e: Exception) { e.message ?: "Couldn't connect" }
            finally { runCatching { client.close() } }
        }

    private var browseClient: Pair<com.example.mobiledigger.file.drives.NetworkDrive, com.example.mobiledigger.file.drives.DriveClient>? = null

    /** One folder level of [drive] for the folder picker. */
    fun browseDrive(drive: com.example.mobiledigger.file.drives.NetworkDrive, path: String) {
        _driveBrowse.value = _driveBrowse.value.copy(driveId = drive.id, loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val client = browseClient?.takeIf { it.first == drive }?.second ?: run {
                browseClient?.second?.let { runCatching { it.close() } }
                com.example.mobiledigger.file.drives.DriveClients.create(drive).also { browseClient = drive to it }
            }
            _driveBrowse.value = try {
                val entries = client.list(path)
                DriveBrowseState(
                    driveId = drive.id,
                    path = path.trim('/'),
                    dirs = entries.filter { it.isDir }.sortedBy { it.name.lowercase() },
                    audioCount = entries.count { !it.isDir && com.example.mobiledigger.file.drives.DriveClients.isAudio(it.name) }
                )
            } catch (e: Exception) {
                runCatching { client.close() }; browseClient = null
                _driveBrowse.value.copy(loading = false, error = e.message ?: "Couldn't open this folder")
            }
        }
    }

    /** Makes [drive] the active source and loads [folder] (recursively). */
    fun loadDrive(drive: com.example.mobiledigger.file.drives.NetworkDrive, folder: String) {
        // Loading a drive while set to "phone only" means: show me this drive.
        if (preferences.getSourceMode() == "PHONE") setSourceMode("NETWORK")
        val updated = drive.copy(folder = folder.trim('/'))
        driveStore.save(updated)
        driveStore.setActive(updated.id)
        com.example.mobiledigger.file.drives.DriveProxy.invalidate(updated.id)
        refreshDrives()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fresh = fileManager.loadMusicFiles()
                _musicFiles.value = fresh
                _archives.value = fileManager.lastArchives
                _workspaceOpen.value = true
                loadLikedFiles()
                loadRejectedFiles()
                val err = fileManager.lastDriveError
                _errorMessage.value = when {
                    err != null -> "${updated.name}: $err"
                    fresh.none { com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(it.uri) } ->
                        "No music found in ${updated.name}${if (updated.folder.isNotEmpty()) "/" + updated.folder else ""}"
                    else -> null
                }
                CrashLogger.log("MusicViewModel", "Drive '${updated.name}' loaded, ${fresh.size} files total")
            } catch (e: Exception) {
                handleError("loadDrive", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ----- What a load includes: phone folder, network drive, or both -----
    // "Load from" starts on Phone every time the app starts (loading a network drive still
    // switches it to Network for that session).
    private val _sourceMode = MutableStateFlow(run { preferences.setSourceMode("PHONE"); "PHONE" })
    val sourceMode: StateFlow<String> = _sourceMode.asStateFlow()
    fun setSourceMode(mode: String) {
        preferences.setSourceMode(mode)
        _sourceMode.value = mode
    }

    /** "Pick up where you left off": reloads whatever the Phone/Network/Both choice says. */
    fun resumeLastSources() {
        val mode = preferences.getSourceMode()
        // Same phone folder the welcome page shows: the saved one, or (after Home cleared it)
        // the most recent one. Previously only the saved one counted, so after Home the card
        // said "Phone" but loaded the Pi.
        val phoneUri = preferences.getSourceRootUri() ?: _recentSourceUris.value.firstOrNull()
        val drive = driveStore.active()
        CrashLogger.log("MusicViewModel", "Resume: load from=$mode, phone=${phoneUri != null}, drive=${drive?.name}")
        when {
            mode != "NETWORK" && phoneUri != null -> selectFolder(Uri.parse(phoneUri)) // also adds the drive when mode is BOTH
            mode == "PHONE" -> _errorMessage.value = "No phone folder yet: pick one under \"On this phone\"."
            drive != null -> loadDrive(drive, drive.folder)
            phoneUri != null -> selectFolder(Uri.parse(phoneUri))
        }
    }

    // ----- Archives (.zip / .rar / .7z) found in the loaded folders -----
    private val _archives = MutableStateFlow<List<com.example.mobiledigger.file.archives.ArchiveItem>>(emptyList())
    val archives: StateFlow<List<com.example.mobiledigger.file.archives.ArchiveItem>> = _archives.asStateFlow()
    /** Live progress of the archive being unpacked (null when idle). */
    data class UnpackProgress(val name: String, val fraction: Float, val step: String, val queued: Int)
    private val _unpackProgress = MutableStateFlow<UnpackProgress?>(null)
    val unpackProgress: StateFlow<UnpackProgress?> = _unpackProgress.asStateFlow()
    /** Archives waiting or being unpacked (their rows show "Queued" / a percentage). */
    private val _unpackPending = MutableStateFlow<Set<Uri>>(emptySet())
    val unpackPending: StateFlow<Set<Uri>> = _unpackPending.asStateFlow()
    /** Short "done" line shown in the progress banner for a moment after each archive. */
    private val _unpackDone = MutableStateFlow<String?>(null)
    val unpackDone: StateFlow<String?> = _unpackDone.asStateFlow()

    data class UnpackRequest(
        val item: com.example.mobiledigger.file.archives.ArchiveItem,
        /** Entry paths to extract; null = everything. */
        val include: Set<String>? = null,
        val deleteArchive: Boolean = true,
        /** Stop "generation" when queued; a Stop bumps it so older requests are skipped. */
        val gen: Int = 0
    )
    @Volatile private var unpackGen = 0

    /** Stops the archive being unpacked and drops everything queued. Archives stay as they were. */
    fun stopUnpacking() {
        if (_unpackPending.value.isEmpty()) return
        unpackGen++
        while (unpackQueue.tryReceive().isSuccess) { /* drain */ }
        _unpackPending.value = emptySet()
        _unpackProgress.value = _unpackProgress.value?.copy(step = "Stopping…")
    }
    private val unpackQueue = kotlinx.coroutines.channels.Channel<UnpackRequest>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    /** Contents of an archive for the preview popup (only reads its table of contents). */
    suspend fun listArchive(item: com.example.mobiledigger.file.archives.ArchiveItem): Result<List<com.example.mobiledigger.file.archives.ArchiveEntry>> =
        withContext(Dispatchers.IO) {
            runCatching { com.example.mobiledigger.file.archives.ArchiveUnpacker(context).list(item) }
        }

    /** Queue one archive with the user's choices from the preview popup. */
    fun unpackArchive(item: com.example.mobiledigger.file.archives.ArchiveItem, include: Set<String>?, deleteArchive: Boolean) {
        if (item.uri in _unpackPending.value) return
        _unpackPending.value = _unpackPending.value + item.uri
        unpackQueue.trySend(UnpackRequest(item, include, deleteArchive, unpackGen))
        if (unpackWorker == null) unpackWorker = viewModelScope.launch(Dispatchers.IO) { runUnpackQueue() }
    }
    private var unpackWorker: kotlinx.coroutines.Job? = null

    /**
     * Queues archives for unpacking. They're done one at a time in the background while the
     * user keeps working; after each one only that folder is added to To Do (no full reload,
     * playback keeps going).
     */
    fun unpackArchives(items: List<com.example.mobiledigger.file.archives.ArchiveItem>) {
        val fresh = items.filter { it.uri !in _unpackPending.value }
        if (fresh.isEmpty()) return
        _unpackPending.value = _unpackPending.value + fresh.map { it.uri }
        fresh.forEach { unpackQueue.trySend(UnpackRequest(it, gen = unpackGen)) }
        if (unpackWorker == null) unpackWorker = viewModelScope.launch(Dispatchers.IO) { runUnpackQueue() }
    }

    /** Deletes an archive without unpacking it (phone folder or writable network drive). */
    fun deleteArchive(item: com.example.mobiledigger.file.archives.ArchiveItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val problem: String? = try {
                if (item.isRemote) {
                    val drive = driveStore.get(item.driveId) ?: throw Exception("its network drive is no longer saved")
                    val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
                    try { client.delete(item.drivePath!!) } finally { runCatching { client.close() } }
                } else {
                    if (!android.provider.DocumentsContract.deleteDocument(context.contentResolver, item.uri)) throw Exception("the phone refused")
                }
                null
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Delete archive failed: ${item.name}", e)
                e.message ?: "unknown error"
            }
            if (problem == null) {
                _archives.value = _archives.value.filterNot { it.uri == item.uri }
                _errorMessage.value = "Deleted ${item.name}"
            } else {
                _errorMessage.value = "Couldn't delete ${item.name}: $problem"
            }
        }
    }

    /** Lives as long as the ViewModel; handles queued archives one by one. */
    private suspend fun runUnpackQueue() {
        val unpacker = com.example.mobiledigger.file.archives.ArchiveUnpacker(context)
        for (req in unpackQueue) {
            val item = req.item
            if (req.gen != unpackGen) continue // stopped before it started
            val queuedAfter = (_unpackPending.value.size - 1).coerceAtLeast(0)
            _unpackProgress.value = UnpackProgress(item.name, 0f, "Starting", queuedAfter)
            try {
                val out = unpacker.unpack(item, req.include, req.deleteArchive, isCancelled = { req.gen != unpackGen }) { f, step ->
                    _unpackProgress.value = UnpackProgress(item.name, f, step, (_unpackPending.value.size - 1).coerceAtLeast(0))
                }
                // Partial update: just the new folder
                val (tracks, nested) = if (item.isRemote) fileManager.listDriveFolder(item.driveId!!, out.newFolderPath)
                    else out.newFolderUri?.let { fileManager.scanSingleFolder(it, out.newFolderPath) } ?: (emptyList<com.example.mobiledigger.model.MusicFile>() to emptyList())
                val known = _musicFiles.value.map { it.uri }.toHashSet()
                val newTracks = tracks.filter { it.uri !in known }.map { it.copy(sourcePlaylist = PlaylistTab.TODO) }
                withContext(Dispatchers.Main) {
                    _musicFiles.value = _musicFiles.value + newTracks // appended: indices of what's playing stay valid
                    _archives.value = _archives.value.filterNot { out.archiveDeleted && it.uri == item.uri } +
                        nested.filterNot { n -> _archives.value.any { it.uri == n.uri } }
                }
                _unpackDone.value = "✓ ${item.name}: ${newTracks.size} track${if (newTracks.size == 1) "" else "s"} added to “${out.folderName}”"
                if (req.deleteArchive && !out.archiveDeleted) _errorMessage.value = "${item.name} was unpacked, but the archive couldn't be deleted (${out.deleteProblem})."
                CrashLogger.log("MusicViewModel", "Unpacked ${item.name}: ${out.files} files, ${newTracks.size} new tracks in '${out.newFolderPath}'")
            } catch (e: java.util.concurrent.CancellationException) {
                CrashLogger.log("MusicViewModel", "Unpack stopped: ${item.name}")
                _unpackDone.value = null
                _errorMessage.value = "Unpacking stopped. ${item.name} is unchanged."
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Unpack failed: ${item.name}", e)
                _errorMessage.value = "${item.name}: ${e.message ?: "couldn't be unpacked"}"
            } finally {
                _unpackPending.value = _unpackPending.value - item.uri
            }
            if (_unpackPending.value.isEmpty()) {
                _unpackProgress.value = null
                val shown = _unpackDone.value
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2500)
                    if (_unpackProgress.value == null && _unpackDone.value == shown) _unpackDone.value = null
                }
            }
        }
    }

    /** Stops using any network drive as a source (the saved drives stay). */
    fun disconnectDrive() {
        driveStore.setActive(null)
        refreshDrives()
        dropNetworkFiles()
    }

    private fun dropNetworkFiles() {
        _musicFiles.value = _musicFiles.value.filterNot { com.example.mobiledigger.file.drives.DriveProxy.isDriveUri(it.uri) }
        _archives.value = _archives.value.filterNot { it.isRemote }
        CrashLogger.log("MusicViewModel", "Network drive source cleared")
    }

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
    
    // Check if user wants to delete rejected files after sorting is complete

    


    /**
     * Shares the current track to WhatsApp as a plain document — the ORIGINAL file, no zip.
     * The file is copied (keeping its exact name) into cache/shared_music and exposed via
     * GenericShareProvider, which reports application/octet-stream so WhatsApp sends it as
     * a document instead of treating it as audio media.
     */
    fun shareToWhatsApp() {
        val currentFile = _currentPlayingFile.value
        if (currentFile == null) {
            _errorMessage.value = "No song selected to share"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalName = currentFile.name
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "track" }
                CrashLogger.log("MusicViewModel", "Starting generic document share for: $originalName")

                // 1) Fresh per-share folder (keeps the exact filename, avoids clobbering a
                //    copy WhatsApp may still be reading); drop older shares.
                val shareRoot = File(context.cacheDir, "shared_music")
                shareRoot.listFiles()?.forEach { it.deleteRecursively() }
                val shareDir = File(shareRoot, System.currentTimeMillis().toString()).apply { mkdirs() }
                val outFile = File(shareDir, originalName)

                // 2) Copy the original bytes (content://, file:// or a network drive via the local DriveProxy)
                val scheme = currentFile.uri.scheme?.lowercase()
                val input = if (scheme == "http" || scheme == "https") {
                    val conn = java.net.URL(currentFile.uri.toString()).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 30_000
                    conn.inputStream
                } else {
                    context.contentResolver.openInputStream(currentFile.uri)
                } ?: throw java.io.IOException("Cannot open $originalName")
                input.use { inp -> FileOutputStream(outFile).use { out -> inp.copyTo(out, 1 shl 16) } }
                CrashLogger.log("MusicViewModel", "Share copy ready: ${outFile.absolutePath} (${outFile.length()} bytes)")

                // 3) URI whose ContentResolver type is application/octet-stream
                val uri = FileProvider.getUriForFile(
                    context,
                    com.example.mobiledigger.file.GenericShareProvider.authority(context.packageName),
                    outFile
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = com.example.mobiledigger.file.GenericShareProvider.GENERIC_MIME
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TITLE, originalName)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(shareIntent)
                        CrashLogger.log("MusicViewModel", "WhatsApp document share intent started")
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "WhatsApp not found, using chooser", e)
                        shareIntent.setPackage(null)
                        val chooser = Intent.createChooser(shareIntent, "Share audio")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to share: ${e.message}"
                    CrashLogger.log("MusicViewModel", "Generic share failed", e)
                }
            }
        }
    }

    private fun ensureMusicServiceStarted() {
        // Starting it again on every update was pointless and, from the background, Android 12+
        // refuses it ("Playback service failed to start" popped up while the phone was locked).
        if (MusicService.isRunning) return
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
        }
    }
    
    /** A track picked in the car's list (Bluetooth browsing / "Now playing" queue). */
    private fun playFromCar(tabName: String, uri: String) {
        val tab = runCatching { PlaylistTab.valueOf(tabName) }.getOrNull() ?: return
        val list = when (tab) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        val file = list.firstOrNull { it.uri.toString() == uri } ?: return
        CrashLogger.log("MusicViewModel", "Car picked ${file.name} from $tab")
        playFile(file.copy(sourcePlaylist = tab))
    }

    /** Keeps the car's track list (MediaLibrary) in step with the playlists. */
    private fun publishLibraryForCar() {
        viewModelScope.launch {
            combine(_musicFiles, _likedFiles, _rejectedFiles, _currentPlaylistTab, _currentPlayingFile) { todo, liked, rejected, tab, playing ->
                fun conv(l: List<MusicFile>) = l.map { MediaLibrary.Track(it.uri.toString(), it.name) }
                Triple(
                    mapOf(MediaLibrary.TODO to conv(todo), MediaLibrary.LIKED to conv(liked), MediaLibrary.REJECTED to conv(rejected)),
                    tab.name,
                    playing?.uri?.toString()
                )
            }
                .debounce(400)
                .collect { (lists, tab, cur) -> MediaLibrary.update(lists, tab, cur) }
        }
    }

    private fun registerNotificationReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(MusicService.ACTION_PLAY_PAUSE)
                addAction(MusicService.ACTION_PLAY)
                addAction(MusicService.ACTION_PLAY_ITEM)
                addAction(MusicService.ACTION_PAUSE)
                addAction(MusicService.ACTION_NEXT)
                addAction(MusicService.ACTION_PREVIOUS)
                addAction(MusicService.ACTION_LIKE)
                addAction(MusicService.ACTION_DISLIKE)
                addAction("com.example.mobiledigger.ACTION_SEEK")
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
            
            // The track that is actually PLAYING — not whatever sits at the current index of the
            // tab on screen (switching tabs or sorting used to put the wrong title on the notification).
            val currentFile = _currentPlayingFile.value ?: run {
                val currentFiles = when (_currentPlaylistTab.value) {
                    PlaylistTab.TODO -> _musicFiles.value
                    PlaylistTab.LIKED -> _likedFiles.value
                    PlaylistTab.REJECTED -> _rejectedFiles.value
                }
                currentFiles.getOrNull(_currentIndex.value)
            }
            val title = currentFile?.name ?: "MobileDigger"
            
            val intent = Intent(MusicService.ACTION_UPDATE_NOTIFICATION).apply { setPackage(context.packageName) }
            intent.putExtra(MusicService.EXTRA_TITLE, title)
            currentFile?.uri?.let { intent.putExtra(MusicService.EXTRA_URI, it.toString()) }
            intent.putExtra(MusicService.EXTRA_IS_PLAYING, _isPlaying.value)
            intent.putExtra(MusicService.EXTRA_CURRENT_POSITION, _currentPosition.value)
            intent.putExtra(MusicService.EXTRA_DURATION, _duration.value)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to update notification", e)
        }
    }

    // Lightweight: only refresh the notification seekbar position, without rebuilding /
    // re-posting the whole notification (which made it flicker every second).
    private fun updateNotificationPosition() {
        try {
            val intent = Intent(MusicService.ACTION_UPDATE_POSITION).apply { setPackage(context.packageName) }
            intent.putExtra(MusicService.EXTRA_IS_PLAYING, _isPlaying.value)
            intent.putExtra(MusicService.EXTRA_CURRENT_POSITION, _currentPosition.value)
            intent.putExtra(MusicService.EXTRA_DURATION, _duration.value)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Failed to update notification position", e)
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
            
            // PHASE 2: Clear StateFlow references to prevent memory leaks
            _musicFiles.value = emptyList()
            _likedFiles.value = emptyList()
            _rejectedFiles.value = emptyList()
            _currentPlayingFile.value = null
            _selectedIndices.value = emptySet()
            _playedButNotActioned.value = emptySet()
            CrashLogger.log("MusicViewModel", "📊 StateFlows cleared to prevent memory leaks")
            
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
    
    /**
     * "Delete all To Do": permanently deletes exactly the tracks the To Do list SHOWS — the active
     * Lossy / Lossless filter is respected, subfolders and network tracks included. (It used to
     * delete every file in the top of the source folder, ignoring filters and subfolders.)
     */
    fun deleteAllTodo(confirmCount: Int): Boolean {
        if (confirmCount < 4) return false
        val shown = if (_currentPlaylistTab.value == PlaylistTab.TODO) currentPlaylistFiles.value else _musicFiles.value
        deleteTracksPermanently(shown)
        return true
    }

    /** Selection bar → Delete: permanently deletes the selected (displayed) tracks. */
    fun deleteSelectedTracks() {
        val display = currentPlaylistFiles.value
        val files = _selectedIndices.value.sorted().mapNotNull { display.getOrNull(it) }
        _selectedIndices.value = emptySet()
        _isMultiSelectionMode.value = false
        deleteTracksPermanently(files)
    }

    /** Rows fade out, then the files are deleted in the background with the deletion banner. */
    private fun deleteTracksPermanently(files: List<MusicFile>) {
        if (files.isEmpty()) { _errorMessage.value = "Nothing to delete"; return }
        val uris = files.map { it.uri }.toSet()
        if (_currentPlayingFile.value?.uri in uris) stopPlayback()
        _removingUris.value = _removingUris.value + uris
        viewModelScope.launch {
            delay(removeAnimMs())
            _musicFiles.value = _musicFiles.value.filterNot { it.uri in uris }
            _likedFiles.value = _likedFiles.value.filterNot { it.uri in uris }
            _rejectedFiles.value = _rejectedFiles.value.filterNot { it.uri in uris }
            _removingUris.value = _removingUris.value - uris
            _currentIndex.value = _currentIndex.value.coerceIn(0, (currentPlaylistFiles.value.size - 1).coerceAtLeast(0))
            _isDeletingFiles.value = true
            _deletionProgress.value = 0f
            var deleted = 0
            val failed = mutableListOf<MusicFile>()
            withContext(Dispatchers.IO) {
                files.forEachIndexed { i, f ->
                    if (fileManager.deletePermanently(f)) deleted++ else failed += f
                    _deletionProgress.value = (i + 1f) / files.size
                }
            }
            _isDeletingFiles.value = false
            if (failed.isNotEmpty()) {
                // Put back what couldn't be deleted, in its own list
                _musicFiles.value = _musicFiles.value + failed.filter { it.sourcePlaylist == PlaylistTab.TODO }
                _errorMessage.value = "Deleted $deleted, ${failed.size} couldn't be deleted."
            } else {
                _errorMessage.value = "Deleted $deleted track${if (deleted == 1) "" else "s"}"
            }
        }
    }

    // Multi-selection functions
    // Sort & filter controls
    fun setSortOrder(field: SortField, ascending: Boolean) {
        _sortOrder.value = SortOrder(field, ascending)
    }

    fun clearSort() {
        _sortOrder.value = null
    }

    fun toggleLosslessOnly() {
        val newValue = !_losslessOnly.value
        _losslessOnly.value = newValue
        if (newValue) _lossyOnly.value = false // lossless and lossy are mutually exclusive
    }

    fun toggleLossyOnly() {
        val newValue = !_lossyOnly.value
        _lossyOnly.value = newValue
        if (newValue) _losslessOnly.value = false
    }

    fun toggleMultiSelectionMode() {
        _isMultiSelectionMode.value = !_isMultiSelectionMode.value
        if (!_isMultiSelectionMode.value) {
            _selectedIndices.value = emptySet()
            _selectedArchives.value = emptySet()
        }
    }

    // ----- Multi-select for archives (unpack / delete in bulk) -----
    private val _selectedArchives = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedArchives: StateFlow<Set<Uri>> = _selectedArchives.asStateFlow()

    fun toggleArchiveSelection(uri: Uri) {
        if (_selectedIndices.value.isNotEmpty()) { tracksFirstHint(); return }
        _isMultiSelectionMode.value = true
        val cur = _selectedArchives.value
        _selectedArchives.value = if (uri in cur) cur - uri else cur + uri
    }

    private fun takeSelectedArchives(): List<com.example.mobiledigger.file.archives.ArchiveItem> {
        val sel = _selectedArchives.value
        val list = _archives.value.filter { it.uri in sel }
        _selectedArchives.value = emptySet()
        if (_selectedIndices.value.isEmpty()) _isMultiSelectionMode.value = false
        return list
    }

    /** Unpacks every selected archive (all files, archive deleted afterwards). */
    fun unpackSelectedArchives() = unpackArchives(takeSelectedArchives())

    /** Deletes every selected archive without unpacking. */
    fun deleteSelectedArchives() {
        val list = takeSelectedArchives()
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            val failed = mutableListOf<String>()
            for (item in list) {
                try {
                    if (item.isRemote) {
                        val drive = driveStore.get(item.driveId) ?: throw Exception("drive no longer saved")
                        val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
                        try { client.delete(item.drivePath!!) } finally { runCatching { client.close() } }
                    } else if (!android.provider.DocumentsContract.deleteDocument(context.contentResolver, item.uri)) {
                        throw Exception("the phone refused")
                    }
                    ok++
                    _archives.value = _archives.value.filterNot { it.uri == item.uri }
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Bulk delete archive failed: ${item.name}", e)
                    failed += item.name
                }
            }
            _errorMessage.value = "Deleted $ok archive${if (ok == 1) "" else "s"}" +
                if (failed.isNotEmpty()) ". ${failed.size} couldn't be deleted: ${failed.take(3).joinToString()}" else ""
        }
    }

    fun enableMultiSelectionMode(index: Int) {
        if (_selectedArchives.value.isNotEmpty()) { archivesFirstHint(); return }
        _isMultiSelectionMode.value = true
        toggleSelection(index)
    }

    // Tracks and archives can't be selected together (different actions)
    private fun archivesFirstHint() { _errorMessage.value = "You're selecting archives. Unpack, delete or clear them first." }
    private fun tracksFirstHint() { _errorMessage.value = "You're selecting tracks. Finish or clear them first." }
    
    fun toggleSelection(index: Int) {
        if (!_isMultiSelectionMode.value) return
        if (_selectedArchives.value.isNotEmpty()) { archivesFirstHint(); return }
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
        if (_selectedArchives.value.isNotEmpty()) { archivesFirstHint(); return }
        // Select every row in the CURRENTLY DISPLAYED list (respects the active tab,
        // sort and lossless filter) — not just the raw TODO list.
        _selectedIndices.value = currentPlaylistFiles.value.indices.toSet()
    }
    
    fun clearSelection() {
        _selectedIndices.value = emptySet()
    }

    /**
     * Select (or, if already all-selected, deselect) every file under the given subfolder
     * so the user can bulk like/dislike them from the subfolder header. Operates on the
     * displayed list indices, consistent with toggleSelection / sortSelectedFiles.
     */
    fun toggleSubfolderSelection(subfolderName: String) {
        val files = currentPlaylistFiles.value
        val indices = files.indices.filter { files[it].subfolder == subfolderName }.toSet()
        // Archives in that folder are part of the folder too (To Do tab only)
        val archUris = if (_currentPlaylistTab.value == PlaylistTab.TODO)
            _archives.value.filter { it.folder == subfolderName }.map { it.uri }.toSet() else emptySet()
        if (indices.isEmpty() && archUris.isEmpty()) return
        // One kind at a time: keep selecting what's already being selected; otherwise tracks,
        // or archives when the folder holds only archives.
        val archiveMode = _selectedArchives.value.isNotEmpty() ||
            (_selectedIndices.value.isEmpty() && indices.isEmpty())
        if (archiveMode) {
            if (archUris.isEmpty()) { archivesFirstHint(); return }
            _isMultiSelectionMode.value = true
            val cur = _selectedArchives.value
            _selectedArchives.value = if (cur.containsAll(archUris)) cur - archUris else cur + archUris
        } else {
            if (indices.isEmpty()) { tracksFirstHint(); return }
            _isMultiSelectionMode.value = true
            val current = _selectedIndices.value
            _selectedIndices.value = if (current.containsAll(indices)) current - indices else current + indices
        }
    }
    
    fun moveSelectedFilesToSubfolder(subfolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = _selectedIndices.value.toList()
            val filesToMove = selected.mapNotNull { index -> currentPlaylistFiles.value.getOrNull(index) }

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
    
    // Mutex for file operations to prevent race conditions
    private val fileOperationMutex = Mutex()
    
    fun sortSelectedFiles(action: SortAction) {
        val selected = _selectedIndices.value
        CrashLogger.log("MusicViewModel", "🔍 sortSelectedFiles called with action=$action, selectedCount=${selected.size}")

        if (selected.isEmpty()) {
            _errorMessage.value = "No files selected"
            return
        }

        // Resolve the selected files from the CURRENTLY DISPLAYED list (filtered + sorted)
        // — exactly the list the UI built the selection indices against. Snapshot it NOW on
        // the main thread, before we clear the selection below. Indexing the raw tab list
        // would action the WRONG files whenever a sort or the lossless filter is active.
        val displayFiles = currentPlaylistFiles.value
        val selectedFiles = selected.sorted().mapNotNull { displayFiles.getOrNull(it) }
        if (selectedFiles.isEmpty()) {
            _errorMessage.value = "No files selected"
            return
        }

        val selectedUris = selectedFiles.map { it.uri }.toSet()
        val tab = _currentPlaylistTab.value

        // Exit multi-select and OPTIMISTICALLY remove the rows from the list RIGHT NOW. That
        // makes the LazyColumn fade + collapse them out (via animateItem) so the user actually
        // watches the files leave — instead of an invisible-but-still-spaced empty row. The
        // file I/O runs in the background under the mutex; any file that fails to move is put
        // back afterwards. No blocking full-screen spinner.
        _selectedIndices.value = emptySet()
        _isMultiSelectionMode.value = false

        val playingUri = _currentPlayingFile.value?.uri
        val rawBefore = when (tab) {
            PlaylistTab.TODO -> _musicFiles.value
            PlaylistTab.LIKED -> _likedFiles.value
            PlaylistTab.REJECTED -> _rejectedFiles.value
        }
        val remaining = rawBefore.filterNot { it.uri in selectedUris }
        when (tab) {
            PlaylistTab.TODO -> _musicFiles.value = remaining
            PlaylistTab.LIKED -> _likedFiles.value = remaining
            PlaylistTab.REJECTED -> _rejectedFiles.value = remaining
        }
        // Keep the index pointing at the still-playing file when it survives; else clamp.
        val newIdx = if (playingUri != null) remaining.indexOfFirst { it.uri == playingUri } else -1
        _currentIndex.value = when {
            newIdx != -1 -> newIdx
            remaining.isEmpty() -> 0
            else -> _currentIndex.value.coerceIn(0, remaining.size - 1)
        }
        CrashLogger.log("MusicViewModel", "🗂️ Optimistically removed ${selectedUris.size} rows; ${remaining.size} remain")

        viewModelScope.launch(Dispatchers.IO) {
            fileOperationMutex.withLock {
                _rejectListenedProgress.value = BulkSortProgress(0, selectedFiles.size, toLiked = action == SortAction.LIKE)
                val failedFiles = mutableListOf<MusicFile>()
                var successCount = 0
                for ((n, file) in selectedFiles.withIndex()) {
                    _rejectListenedProgress.value = BulkSortProgress(n, selectedFiles.size, toLiked = action == SortAction.LIKE)
                    try {
                        if (fileManager.sortFile(file, action)) {
                            successCount++
                        } else {
                            failedFiles.add(file)
                            CrashLogger.log("MusicViewModel", "❌ Failed to sort: ${file.name}")
                        }
                    } catch (e: Exception) {
                        failedFiles.add(file)
                        CrashLogger.log("MusicViewModel", "💥 Exception sorting ${file.name}", e)
                    }
                }
                _rejectListenedProgress.value = BulkSortProgress(selectedFiles.size, selectedFiles.size, toLiked = action == SortAction.LIKE)
                CrashLogger.log("MusicViewModel", "📊 Bulk sort done: success=$successCount, failed=${failedFiles.size}")

                withContext(Dispatchers.Main) {
                    if (failedFiles.isNotEmpty()) {
                        // Put back the files that did NOT actually move (rare — usually a
                        // permission issue). Appended; exact original order isn't worth
                        // preserving for a failure case.
                        val failedUris = failedFiles.map { it.uri }.toSet()
                        val cur = when (tab) {
                            PlaylistTab.TODO -> _musicFiles.value
                            PlaylistTab.LIKED -> _likedFiles.value
                            PlaylistTab.REJECTED -> _rejectedFiles.value
                        }
                        val toReadd = selectedFiles.filter { it.uri in failedUris && cur.none { c -> c.uri == it.uri } }
                        if (toReadd.isNotEmpty()) {
                            val restored = cur + toReadd
                            when (tab) {
                                PlaylistTab.TODO -> _musicFiles.value = restored
                                PlaylistTab.LIKED -> _likedFiles.value = restored
                                PlaylistTab.REJECTED -> _rejectedFiles.value = restored
                            }
                        }
                        _errorMessage.value = "Sorted $successCount, failed ${failedFiles.size}. Check folder permissions."
                    }
                    // Success: the progress banner was the message; it hides itself.
                    viewModelScope.launch { finishBulkProgress() }

                    // If the TODO list is now empty and nothing failed, offer to clear rejected.
                    if (tab == PlaylistTab.TODO && _musicFiles.value.isEmpty()) {
                        stopPlayback()
                        showDeleteRejectedPrompt()
                    }
                    refreshAllPlaylists()
                }
            }
        }
    }

    enum class RenameCase { MANUAL, TITLE, UPPER, LOWER }

    private fun applyCaseTransform(base: String, mode: RenameCase): String {
        return when (mode) {
            RenameCase.MANUAL -> base
            RenameCase.UPPER -> base.uppercase()
            RenameCase.LOWER -> base.lowercase()
            RenameCase.TITLE -> base
                .let { input ->
                    val sb = StringBuilder(input.length)
                    var newWord = true
                    input.forEach { ch ->
                        when {
                            ch.isLetter() -> {
                                if (newWord) {
                                    sb.append(ch.titlecase())
                                } else {
                                    sb.append(ch.lowercaseChar())
                                }
                                newWord = false
                            }
                            else -> {
                                sb.append(ch)
                                newWord = ch.isWhitespace() || ch == '_' || ch == '-' || ch == '(' || ch == '[' || ch == '{' || ch == '.' || ch == ':' || ch == '!' || ch == ')' || ch == ']' || ch == '}'
                            }
                        }
                    }
                    sb.toString()
                }
        }
    }

    fun renameSelectedFiles(mode: RenameCase) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val indices = _selectedIndices.value.toList()
                if (indices.isEmpty()) {
                    withContext(Dispatchers.Main) { _errorMessage.value = "No files selected" }
                    return@launch
                }
                // Resolve selected files from the displayed (filtered + sorted) list,
                // then write results back to the raw tab list by identity (URI) so the
                // right files are renamed regardless of active sort/filter.
                val displayFiles = currentPlaylistFiles.value
                val filesToRename = indices.sorted().mapNotNull { displayFiles.getOrNull(it) }
                var success = 0
                val renames = mutableMapOf<android.net.Uri, MusicFile>()
                filesToRename.forEach { mf ->
                    val base = mf.name.substringBeforeLast('.', mf.name)
                    val newBase = applyCaseTransform(base, mode)
                    val renamed = fileManager.renameFile(mf, newBase)
                    if (renamed != null) {
                        success++
                        renames[mf.uri] = renamed
                        if (_currentPlayingFile.value?.uri == mf.uri) {
                            _currentPlayingFile.value = renamed
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    when (_currentPlaylistTab.value) {
                        PlaylistTab.TODO -> _musicFiles.value = _musicFiles.value.map { renames[it.uri] ?: it }
                        PlaylistTab.LIKED -> _likedFiles.value = _likedFiles.value.map { renames[it.uri] ?: it }
                        PlaylistTab.REJECTED -> _rejectedFiles.value = _rejectedFiles.value.map { renames[it.uri] ?: it }
                    }
                    _selectedIndices.value = emptySet()
                    _isMultiSelectionMode.value = false
                    _errorMessage.value = "Renamed $success files"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Rename failed: ${e.message}" }
            }
        }
    }

    fun renameCurrentPlayingFile(manualBase: String, mode: RenameCase) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = _currentPlayingFile.value ?: return@launch
            try {
                val resumePos = _currentPosition.value
                val base = if (manualBase.isNotBlank()) manualBase else cur.name.substringBeforeLast('.', cur.name)
                val finalBase = applyCaseTransform(base, mode)
                val renamed = fileManager.renameFile(cur, finalBase)
                if (renamed != null) {
                    withContext(Dispatchers.Main) {
                        // Update in current playlist
                        when (_currentPlaylistTab.value) {
                            PlaylistTab.TODO -> _musicFiles.value = _musicFiles.value.map { if (it.uri == cur.uri) renamed else it }
                            PlaylistTab.LIKED -> _likedFiles.value = _likedFiles.value.map { if (it.uri == cur.uri) renamed else it }
                            PlaylistTab.REJECTED -> _rejectedFiles.value = _rejectedFiles.value.map { if (it.uri == cur.uri) renamed else it }
                        }
                        _currentPlayingFile.value = renamed
                        // Ensure playback continues from same time
                        try { seekTo(resumePos) } catch (_: Exception) {}
                        _errorMessage.value = "Renamed current file"
                    }
                } else {
                    withContext(Dispatchers.Main) { _errorMessage.value = "Rename failed" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Rename failed: ${e.message}" }
            }
        }
    }

    fun renameFileAtIndex(index: Int, manualBase: String, mode: RenameCase) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = when (_currentPlaylistTab.value) {
                    PlaylistTab.TODO -> _musicFiles.value
                    PlaylistTab.LIKED -> _likedFiles.value
                    PlaylistTab.REJECTED -> _rejectedFiles.value
                }
                val mf = files.getOrNull(index) ?: return@launch
                val base = if (manualBase.isNotBlank()) manualBase else mf.name.substringBeforeLast('.', mf.name)
                val finalBase = applyCaseTransform(base, mode)
                val renamed = fileManager.renameFile(mf, finalBase)
                if (renamed != null) {
                    withContext(Dispatchers.Main) {
                        when (_currentPlaylistTab.value) {
                            PlaylistTab.TODO -> _musicFiles.value = _musicFiles.value.mapIndexed { i, f -> if (i == index) renamed else f }
                            PlaylistTab.LIKED -> _likedFiles.value = _likedFiles.value.mapIndexed { i, f -> if (i == index) renamed else f }
                            PlaylistTab.REJECTED -> _rejectedFiles.value = _rejectedFiles.value.mapIndexed { i, f -> if (i == index) renamed else f }
                        }
                        if (_currentPlayingFile.value?.uri == mf.uri) {
                            _currentPlayingFile.value = renamed
                        }
                        _errorMessage.value = "Renamed file"
                    }
                } else {
                    withContext(Dispatchers.Main) { _errorMessage.value = "Rename failed" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Rename failed: ${e.message}" }
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
                val filter = _likedFilter.value
                if (!filter.isNullOrEmpty()) {
                    if (filter.contains("<ROOT_ONLY>")) {
                        loadLikedRootOnly()
                    } else {
                        loadFilesFromLikedSubfolders(filter)
                    }
                } else {
                    loadLikedFiles()
                }
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
            if (_currentPlaylistTab.value == PlaylistTab.LIKED) {
                val filter = _likedFilter.value
                if (!filter.isNullOrEmpty()) {
                    if (filter.contains("<ROOT_ONLY>")) {
                        loadLikedRootOnly()
                    } else {
                        loadFilesFromLikedSubfolders(filter)
                    }
                } else {
                    loadLikedFiles()
                }
            }
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
                                CrashLogger.log("MusicViewModel", "📁 Subfolder: ${subfolder.name} - ${filesWithSubfolder.size} files")
                                filesWithSubfolder.forEachIndexed { idx, file ->
                                    if (idx < 2) CrashLogger.log("MusicViewModel", "  ✅ File with subfolder: ${file.name} -> ${file.subfolder}")
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
                        val sortedLikedFiles = likedFiles.map { it.copy(sourcePlaylist = PlaylistTab.LIKED) }
                            .sortedByDescending { it.dateAdded }
                        _likedFiles.value = sortedLikedFiles
                        CrashLogger.log("MusicViewModel", "Updated liked files StateFlow with ${sortedLikedFiles.size} files (sorted by dateAdded)")
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
                        val sortedRejectedFiles = rejectedFiles.map { it.copy(sourcePlaylist = PlaylistTab.REJECTED) }
                            .sortedByDescending { it.dateAdded }
                        _rejectedFiles.value = sortedRejectedFiles
                        CrashLogger.log("MusicViewModel", "Updated rejected files StateFlow with ${sortedRejectedFiles.size} files (sorted by dateAdded)")
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
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCompletion = currentTime - lastCompletionTime
        
        if (timeSinceLastCompletion < completionDebounceMs) {
            CrashLogger.log("MusicViewModel", "Duplicate completion callback ignored (${timeSinceLastCompletion}ms since last)")
            return
        }
        
        if (currentTime < ignoreCompletionUntil) {
            CrashLogger.log("MusicViewModel", "Completion after stopPlayback() ignored")
            return
        }
        lastCompletionTime = currentTime
        CrashLogger.log("MusicViewModel", "Track completed, playing next.")
        
        // No need to track here anymore - tracking happens when file starts playing
        
        next()
    }

    fun getFileName(uri: Uri): String? {
        return fileManager.getFileName(uri)
    }
    
    // ==================== SUBFOLDER MANAGEMENT ====================
    
    private fun isMusicFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "aif", "aiff", "wma")
    }
    
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
                                        it.isFile && isMusicFile(it.name)
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
        // Use currentFile property (set immediately in loadCurrentFile) instead of _currentPlayingFile StateFlow
        val fileToMove = this.currentFile
        CrashLogger.log("MusicViewModel", "📂 moveCurrentFileToSubfolder called - Current file: ${fileToMove?.name}, Subfolder: $subfolderName")
        if (fileToMove == null) {
            _errorMessage.value = "No file is currently playing"
            CrashLogger.log("MusicViewModel", "📂 moveCurrentFileToSubfolder - No current file, aborting")
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
                val fileName = fileManager.getFileName(fileToMove.uri) ?: "unknown"
                val originalMimeType = getApplication<Application>().contentResolver.getType(fileToMove.uri) ?: "audio/mpeg"
                val targetFile = subfolder.createFile(originalMimeType, fileName)
                
                if (targetFile != null) {
                    // Copy file content
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(fileToMove.uri)
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(targetFile.uri)
                    
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        // Delete original file
                        val originalFile = DocumentFile.fromSingleUri(getApplication(), fileToMove.uri)
                        originalFile?.delete()
                        
                        // Update playlists
                        withContext(Dispatchers.Main) {
                            addToSubfolderHistory(subfolderName)
                            updateSubfolderInfo()
                            
                            // Remove from played-but-not-actioned counter (this is like liking the file)
                            _playedButNotActioned.value = _playedButNotActioned.value - fileToMove.uri
                            
                            // Handle file movement based on current playlist tab
                            when (_currentPlaylistTab.value) {
                                PlaylistTab.TODO -> {
                                    // Remove from TODO playlist
                                    val updatedTodoFiles = _musicFiles.value.toMutableList()
                                    val indexToRemove = updatedTodoFiles.indexOfFirst { it.uri == fileToMove.uri }
                                    val wasPlayingRemovedFile = (indexToRemove == _currentIndex.value)
                                    
                                    if (indexToRemove != -1) {
                                        // Trigger fade-out animation before removal
                                        _removingUris.value = _removingUris.value + fileToMove.uri
                                        viewModelScope.launch {
                                            delay(300)
                                            _removingUris.value = _removingUris.value - fileToMove.uri
                                        updatedTodoFiles.removeAt(indexToRemove)
                                        _musicFiles.value = updatedTodoFiles
                                        
                                        // PHASE 4: Keep file playing when moved to subfolder
                                        // Don't interrupt playback - just update the index
                                        if (indexToRemove < _currentIndex.value) {
                                            _currentIndex.value = _currentIndex.value - 1
                                        } else if (wasPlayingRemovedFile) {
                                            // File was playing - DON'T load next file, keep current one playing
                                            // Just adjust index to prevent out of bounds
                                            if (_currentIndex.value >= updatedTodoFiles.size && updatedTodoFiles.isNotEmpty()) {
                                                _currentIndex.value = updatedTodoFiles.size - 1
                                            }
                                            CrashLogger.log("MusicViewModel", "📂 File moved to subfolder while playing - keeping playback active")
                                        }
                                            // Update current playing file to point to new URI in liked folder
                                            _currentPlayingFile.value = fileToMove.copy(uri = targetFile.uri, sourcePlaylist = PlaylistTab.LIKED, subfolder = subfolderName)
                                        }
                                    }
                                    
                                    // Add to liked files with subfolder info
                                    val likedFile = fileToMove.copy(
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
                                    val indexToRemove = updatedRejectedFiles.indexOfFirst { it.uri == fileToMove.uri }
                                    val wasPlayingRemovedFile = (indexToRemove == _currentIndex.value)
                                    
                                    if (indexToRemove != -1) {
                                        _removingUris.value = _removingUris.value + fileToMove.uri
                                        viewModelScope.launch {
                                            delay(300)
                                            _removingUris.value = _removingUris.value - fileToMove.uri
                                        updatedRejectedFiles.removeAt(indexToRemove)
                                        _rejectedFiles.value = updatedRejectedFiles
                                        
                                        // PHASE 4: Keep file playing when moved to subfolder
                                        if (indexToRemove < _currentIndex.value) {
                                            _currentIndex.value = _currentIndex.value - 1
                                        } else if (wasPlayingRemovedFile) {
                                            // File was playing - DON'T load next file, keep current one playing
                                            if (_currentIndex.value >= updatedRejectedFiles.size && updatedRejectedFiles.isNotEmpty()) {
                                                _currentIndex.value = updatedRejectedFiles.size - 1
                                            }
                                            CrashLogger.log("MusicViewModel", "📂 File moved to subfolder while playing - keeping playback active")
                                        }
                                            _currentPlayingFile.value = fileToMove.copy(uri = targetFile.uri, sourcePlaylist = PlaylistTab.LIKED, subfolder = subfolderName)
                                        }
                                    }
                                    
                                    // Add to liked files with subfolder info
                                    val likedFile = fileToMove.copy(
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
    
    fun loadFilesFromLikedSubfolders(subfolderNames: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _likedFilter.value = subfolderNames
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
                    val sortedUnique = unique.sortedByDescending { it.dateAdded }
                    _likedFiles.value = sortedUnique
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
    
    fun clearLikedFilterAndReload() {
        _likedFilter.value = null
        loadLikedFiles()
    }
    
    fun loadLikedRootOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            fileLoadingMutex.withLock {
                try {
                    val destinationFolder = fileManager.getDestinationFolder()
                    val likedFolder = destinationFolder?.findFile("Liked") ?: destinationFolder?.findFile("I DIG")
                    val rootFiles = if (likedFolder != null) {
                        fileManager.listMusicFilesInFolder(likedFolder.uri)
                    } else {
                        emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        val sorted = rootFiles.map { it.copy(sourcePlaylist = PlaylistTab.LIKED, subfolder = null) }
                            .sortedByDescending { it.dateAdded }
                        _likedFiles.value = sorted
                        // Mark a special filter state to show reload icon
                        _likedFilter.value = listOf("<ROOT_ONLY>")
                        CrashLogger.log("MusicViewModel", "Loaded ${sorted.size} files from Liked root only")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error loading liked root only", e)
                }
            }
        }
    }
    
    // External audio file handling methods
    private fun checkForPendingExternalAudio() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val hasPendingAudio = prefs.getBoolean("has_pending_audio", false)
                if (hasPendingAudio) {
                    val audioUriString = prefs.getString("pending_audio_uri", null)
                    if (audioUriString != null) {
                        val audioUri = Uri.parse(audioUriString)
                        CrashLogger.log("MusicViewModel", "Processing pending external audio file: $audioUri")
                        handleExternalAudioFile(audioUri)
                        
                        // Clear the pending audio flag
                        prefs.edit().apply {
                            remove("pending_audio_uri")
                            putBoolean("has_pending_audio", false)
                            apply()
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("MusicViewModel", "Error checking for pending external audio", e)
            }
        }
    }
    
    // Public method to force check for pending external audio (called from MainActivity)
    fun forceCheckPendingExternalAudio() {
        CrashLogger.log("MusicViewModel", "Force checking for pending external audio")
        checkForPendingExternalAudio()
    }
    
    fun handleExternalAudioFile(audioUri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                CrashLogger.log("MusicViewModel", "Handling external audio file: $audioUri")

                // The sending app's permission can expire (e.g. WhatsApp) — don't add a dead "Unknown" row
                val readable = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openFileDescriptor(audioUri, "r")?.use { true } ?: false }.getOrDefault(false)
                }
                if (!readable) {
                    _isLoading.value = false
                    _errorMessage.value = "Couldn't open that file any more. Open it again from the app that sent it."
                    return@launch
                }
                
                // Manual-only policy: do not auto-trigger spectrogram or analysis here
                
                // Create a MusicFile object from the URI with better error handling
                val musicFile = try {
                    createMusicFileFromUri(audioUri)
                } catch (e: OutOfMemoryError) {
                    CrashLogger.log("MusicViewModel", "Out of memory while creating MusicFile", e)
                    System.gc() // Force garbage collection
                    _errorMessage.value = "File too large to process. Please try a smaller file."
                    return@launch
                } catch (e: Exception) {
                    CrashLogger.log("MusicViewModel", "Error creating MusicFile from URI", e)
                    _errorMessage.value = "Error loading file: ${e.message}"
                    return@launch
                }
                
                if (musicFile != null) {
                    try {
                        // Add to the beginning of the TODO playlist and play
                        val currentFiles = _musicFiles.value.toMutableList()
                        currentFiles.add(0, musicFile) // Add to beginning
                        _musicFiles.value = currentFiles
                        _currentIndex.value = 0
                        
                        // Switch to TODO tab if not already there
                        if (_currentPlaylistTab.value != PlaylistTab.TODO) {
                            switchPlaylistTab(PlaylistTab.TODO)
                        }
                        
                        // Play the file with error handling
                        try {
                            playFile(musicFile)
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error playing external file", e)
                            _errorMessage.value = "File loaded but playback failed: ${e.message}"
                            return@launch
                        }
                        
                        _errorMessage.value = "Opened external file: ${musicFile.name}"
                        
                        CrashLogger.log("MusicViewModel", "Successfully opened external audio file: ${musicFile.name}")
                    } catch (e: Exception) {
                        CrashLogger.log("MusicViewModel", "Error processing external file", e)
                        _errorMessage.value = "Error processing file: ${e.message}"
                    }
                } else {
                    _errorMessage.value = "Could not load the selected audio file"
                    CrashLogger.log("MusicViewModel", "Failed to create MusicFile from URI: $audioUri")
                }
            } catch (e: OutOfMemoryError) {
                CrashLogger.log("MusicViewModel", "Out of memory in handleExternalAudioFile", e)
                System.gc() // Force garbage collection
                _errorMessage.value = "Out of memory. Please try a smaller file."
            } catch (e: Exception) {
                handleError("handleExternalAudioFile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun createMusicFileFromUri(uri: Uri): MusicFile? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(context, uri)
            val fileSize = getFileSizeFromUri(context, uri)
            
            // For external files opened via "Open with", copy to temporary location for better access
            val finalUri = if (uri.scheme == "content") {
                copyContentUriToTempFile(uri, fileName ?: "temp_audio")
            } else {
                uri
            }
            
            MusicFile(
                name = fileName ?: "Unknown",
                uri = finalUri,
                size = fileSize ?: 0L,
                duration = 0L, // Will be extracted later
                sourcePlaylist = PlaylistTab.TODO
            )
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error creating MusicFile from URI", e)
            null
        }
    }
    
    private suspend fun copyContentUriToTempFile(contentUri: Uri, fileName: String): Uri = withContext(Dispatchers.IO) {
        try {
            // Check file size first to avoid memory issues
            val fileSize = getFileSizeFromUri(context, contentUri) ?: 0L
            val fileSizeMB = fileSize / (1024 * 1024)
            
            // Skip copying for very large files (>100MB) to avoid crashes
            if (fileSizeMB > 100) {
                CrashLogger.log("MusicViewModel", "File too large (${fileSizeMB}MB), using original URI")
                return@withContext contentUri
            }
            
            val tempDir = File(context.cacheDir, "temp_audio")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            // Clean up old temp files to prevent storage issues
            cleanupOldTempFiles(tempDir)
            
            val tempFile = File(tempDir, fileName)
            val inputStream = context.contentResolver.openInputStream(contentUri)
            
            if (inputStream == null) {
                CrashLogger.log("MusicViewModel", "Could not open input stream for URI: $contentUri")
                return@withContext contentUri
            }
            
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    // Copy with buffer to handle large files safely
                    val buffer = ByteArray(8192) // 8KB buffer
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // Check memory pressure every 10MB
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            System.gc() // Force garbage collection
                        }
                    }
                }
            }
            
            // Verify the file was copied successfully
            if (tempFile.exists() && tempFile.length() > 0) {
                CrashLogger.log("MusicViewModel", "Successfully copied file to temp location: ${tempFile.absolutePath}")
                Uri.fromFile(tempFile)
            } else {
                CrashLogger.log("MusicViewModel", "Temp file copy failed or empty, using original URI")
                contentUri
            }
        } catch (e: OutOfMemoryError) {
            CrashLogger.log("MusicViewModel", "Out of memory while copying file", e)
            System.gc() // Force garbage collection
            contentUri // Fallback to original URI
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error copying content URI to temp file", e)
            contentUri // Fallback to original URI
        }
    }
    
    private fun cleanupOldTempFiles(tempDir: File) {
        try {
            val files = tempDir.listFiles()
            if (files != null && files.size > 10) { // Keep only 10 most recent temp files
                files.sortedByDescending { it.lastModified() }
                    .drop(10)
                    .forEach { file ->
                        try {
                            if (file.delete()) {
                                CrashLogger.log("MusicViewModel", "Cleaned up old temp file: ${file.name}")
                            }
                        } catch (e: Exception) {
                            CrashLogger.log("MusicViewModel", "Error deleting temp file: ${file.name}", e)
                        }
                    }
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error cleaning up temp files", e)
        }
    }
    
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error getting file name from URI", e)
            null
        }
    }
    
    private fun getFileSizeFromUri(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                } else null
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicViewModel", "Error getting file size from URI", e)
            null
        }
    }
    
    // Manual-only policy: spectrogram display is controlled exclusively by explicit UI actions (buttons/prompts).
    
}

