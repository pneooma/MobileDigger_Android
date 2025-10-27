package com.example.mobiledigger.repository

import android.app.Application
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.model.SortResult
import com.example.mobiledigger.file.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Repository responsible for managing all playlist operations including
 * TODO, Liked, and Rejected playlists, as well as file sorting and organization.
 */
class PlaylistRepository(application: Application) {
    
    private val fileManager = FileManager(application)
    private val context = application.applicationContext
    
    // Playlist state
    private val _todoFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val todoFiles: StateFlow<List<MusicFile>> = _todoFiles.asStateFlow()
    
    private val _likedFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val likedFiles: StateFlow<List<MusicFile>> = _likedFiles.asStateFlow()
    
    private val _rejectedFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val rejectedFiles: StateFlow<List<MusicFile>> = _rejectedFiles.asStateFlow()
    
    // Playlist metadata
    private val _todoCount = MutableStateFlow(0)
    val todoCount: StateFlow<Int> = _todoCount.asStateFlow()
    
    private val _likedCount = MutableStateFlow(0)
    val likedCount: StateFlow<Int> = _likedCount.asStateFlow()
    
    private val _rejectedCount = MutableStateFlow(0)
    val rejectedCount: StateFlow<Int> = _rejectedCount.asStateFlow()
    
    // Current playlist state
    private val _currentPlaylist = MutableStateFlow("TODO")
    val currentPlaylist: StateFlow<String> = _currentPlaylist.asStateFlow()
    
    private val _currentPlaylistFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val currentPlaylistFiles: StateFlow<List<MusicFile>> = _currentPlaylistFiles.asStateFlow()
    
    // Playlist operations state
    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()
    
    private val _isSortingFiles = MutableStateFlow(false)
    val isSortingFiles: StateFlow<Boolean> = _isSortingFiles.asStateFlow()
    
    /**
     * Load all playlists from storage
     */
    suspend fun loadAllPlaylists() = withContext(Dispatchers.IO) {
        _isLoadingPlaylists.value = true
        try {
            // Load music files from the main source
            val allFiles = fileManager.loadMusicFiles()
            
            // For now, we'll use the main music files as TODO
            // In a real implementation, you'd load from specific playlists
            _todoFiles.value = allFiles
            _likedFiles.value = emptyList() // TODO: Load from liked folder
            _rejectedFiles.value = emptyList() // TODO: Load from rejected folder
            
            _todoCount.value = allFiles.size
            _likedCount.value = 0
            _rejectedCount.value = 0
            
            // Update current playlist if it's TODO
            if (_currentPlaylist.value == "TODO") {
                _currentPlaylistFiles.value = allFiles
            }
        } finally {
            _isLoadingPlaylists.value = false
        }
    }
    
    /**
     * Switch to a specific playlist
     */
    fun switchToPlaylist(playlistName: String) {
        _currentPlaylist.value = playlistName
        _currentPlaylistFiles.value = when (playlistName) {
            "TODO" -> _todoFiles.value
            "Liked" -> _likedFiles.value
            "Rejected" -> _rejectedFiles.value
            else -> emptyList()
        }
    }
    
    /**
     * Add a file to TODO playlist
     */
    suspend fun addToTodo(file: MusicFile) = withContext(Dispatchers.IO) {
        val currentTodo = _todoFiles.value.toMutableList()
        if (!currentTodo.any { it.uri == file.uri }) {
            currentTodo.add(0, file) // Add to beginning
            _todoFiles.value = currentTodo
            _todoCount.value = currentTodo.size
            
            if (_currentPlaylist.value == "TODO") {
                _currentPlaylistFiles.value = currentTodo
            }
        }
    }
    
    /**
     * Like a file (move from TODO to Liked)
     */
    suspend fun likeFile(file: MusicFile) = withContext(Dispatchers.IO) {
        val currentTodo = _todoFiles.value.toMutableList()
        val currentLiked = _likedFiles.value.toMutableList()
        
        // Remove from TODO
        currentTodo.removeAll { it.uri == file.uri }
        
        // Add to Liked if not already there
        if (!currentLiked.any { it.uri == file.uri }) {
            currentLiked.add(file.copy(dateAdded = System.currentTimeMillis()))
            // Sort by dateAdded descending (newest first)
            currentLiked.sortByDescending { it.dateAdded }
        }
        
        _todoFiles.value = currentTodo
        _likedFiles.value = currentLiked
        
        _todoCount.value = currentTodo.size
        _likedCount.value = currentLiked.size
        
        // Update current playlist if needed
        if (_currentPlaylist.value == "TODO") {
            _currentPlaylistFiles.value = currentTodo
        } else if (_currentPlaylist.value == "Liked") {
            _currentPlaylistFiles.value = currentLiked
        }
    }
    
    /**
     * Reject a file (move from TODO to Rejected)
     */
    suspend fun rejectFile(file: MusicFile) = withContext(Dispatchers.IO) {
        val currentTodo = _todoFiles.value.toMutableList()
        val currentRejected = _rejectedFiles.value.toMutableList()
        
        // Remove from TODO
        currentTodo.removeAll { it.uri == file.uri }
        
        // Add to Rejected if not already there
        if (!currentRejected.any { it.uri == file.uri }) {
            currentRejected.add(file.copy(dateAdded = System.currentTimeMillis()))
            // Sort by dateAdded descending (newest first)
            currentRejected.sortByDescending { it.dateAdded }
        }
        
        _todoFiles.value = currentTodo
        _rejectedFiles.value = currentRejected
        
        _todoCount.value = currentTodo.size
        _rejectedCount.value = currentRejected.size
        
        // Update current playlist if needed
        if (_currentPlaylist.value == "TODO") {
            _currentPlaylistFiles.value = currentTodo
        } else if (_currentPlaylist.value == "Rejected") {
            _currentPlaylistFiles.value = currentRejected
        }
    }
    
    /**
     * Remove a file from a specific playlist
     */
    suspend fun removeFromPlaylist(file: MusicFile, playlistName: String) = withContext(Dispatchers.IO) {
        when (playlistName) {
            "TODO" -> {
                val currentTodo = _todoFiles.value.toMutableList()
                currentTodo.removeAll { it.uri == file.uri }
                _todoFiles.value = currentTodo
                _todoCount.value = currentTodo.size
                if (_currentPlaylist.value == "TODO") {
                    _currentPlaylistFiles.value = currentTodo
                }
            }
            "Liked" -> {
                val currentLiked = _likedFiles.value.toMutableList()
                currentLiked.removeAll { it.uri == file.uri }
                _likedFiles.value = currentLiked
                _likedCount.value = currentLiked.size
                if (_currentPlaylist.value == "Liked") {
                    _currentPlaylistFiles.value = currentLiked
                }
            }
            "Rejected" -> {
                val currentRejected = _rejectedFiles.value.toMutableList()
                currentRejected.removeAll { it.uri == file.uri }
                _rejectedFiles.value = currentRejected
                _rejectedCount.value = currentRejected.size
                if (_currentPlaylist.value == "Rejected") {
                    _currentPlaylistFiles.value = currentRejected
                }
            }
        }
    }
    
    /**
     * Sort files in the current playlist
     */
    suspend fun sortCurrentPlaylist(sortAction: SortAction): List<SortResult> = withContext(Dispatchers.IO) {
        _isSortingFiles.value = true
        try {
            val currentFiles = _currentPlaylistFiles.value.toMutableList()
            
            // Create sort results for each file
            val results = currentFiles.map { file ->
                SortResult(
                    musicFile = file,
                    action = sortAction,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Update the appropriate playlist based on sort action
            when (sortAction) {
                SortAction.LIKE -> {
                    // Move files to liked playlist
                    val likedFiles = currentFiles.toMutableList()
                    _likedFiles.value = likedFiles
                    _todoFiles.value = _todoFiles.value.filter { file -> 
                        !currentFiles.any { it.uri == file.uri } 
                    }
                    if (_currentPlaylist.value == "Liked") {
                        _currentPlaylistFiles.value = likedFiles
                    }
                }
                SortAction.DISLIKE -> {
                    // Move files to rejected playlist
                    val rejectedFiles = currentFiles.toMutableList()
                    _rejectedFiles.value = rejectedFiles
                    _todoFiles.value = _todoFiles.value.filter { file -> 
                        !currentFiles.any { it.uri == file.uri } 
                    }
                    if (_currentPlaylist.value == "Rejected") {
                        _currentPlaylistFiles.value = rejectedFiles
                    }
                }
                SortAction.SKIP -> {
                    // Just skip, no playlist changes
                }
            }
            
            results
        } finally {
            _isSortingFiles.value = false
        }
    }
    
    /**
     * Get files from a specific playlist
     */
    fun getPlaylistFiles(playlistName: String): List<MusicFile> {
        return when (playlistName) {
            "TODO" -> _todoFiles.value
            "Liked" -> _likedFiles.value
            "Rejected" -> _rejectedFiles.value
            else -> emptyList()
        }
    }
    
    /**
     * Clear all files from a specific playlist
     */
    suspend fun clearPlaylist(playlistName: String) = withContext(Dispatchers.IO) {
        when (playlistName) {
            "TODO" -> {
                _todoFiles.value = emptyList()
                _todoCount.value = 0
                if (_currentPlaylist.value == "TODO") {
                    _currentPlaylistFiles.value = emptyList()
                }
            }
            "Liked" -> {
                _likedFiles.value = emptyList()
                _likedCount.value = 0
                if (_currentPlaylist.value == "Liked") {
                    _currentPlaylistFiles.value = emptyList()
                }
            }
            "Rejected" -> {
                _rejectedFiles.value = emptyList()
                _rejectedCount.value = 0
                if (_currentPlaylist.value == "Rejected") {
                    _currentPlaylistFiles.value = emptyList()
                }
            }
        }
    }
}
