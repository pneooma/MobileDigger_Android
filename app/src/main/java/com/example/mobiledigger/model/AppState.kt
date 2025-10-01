package com.example.mobiledigger.model

import com.example.mobiledigger.model.MusicFile

/**
 * Data classes to group related UI state for better organization and maintainability.
 * These replace the scattered StateFlows in MusicViewModel with cohesive state objects.
 */

/**
 * Represents the current playback state
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentFile: MusicFile? = null,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE
)

/**
 * Represents the current playlist state
 */
data class PlaylistState(
    val currentPlaylist: String = "TODO",
    val currentIndex: Int = 0,
    val todoCount: Int = 0,
    val likedCount: Int = 0,
    val rejectedCount: Int = 0,
    val isPlaylistLoading: Boolean = false
)

/**
 * Represents the current UI state
 */
data class UIState(
    val isMainPlayerVisible: Boolean = true,
    val isMiniPlayerVisible: Boolean = false,
    val isSpectrogramVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val isSearchVisible: Boolean = false,
    val isFileOperationsVisible: Boolean = false,
    val currentTheme: String = "System",
    val isDarkMode: Boolean = false
)

/**
 * Represents the current file operations state
 */
data class FileOperationsState(
    val isCreatingZip: Boolean = false,
    val isMovingFiles: Boolean = false,
    val isDeletingFiles: Boolean = false,
    val isSharingFiles: Boolean = false,
    val isSortingFiles: Boolean = false,
    val lastOperationResult: String? = null,
    val isFileLoading: Boolean = false,
    val isLoadingProgress: Float = 0f
)

/**
 * Represents the current spectrogram state
 */
data class SpectrogramState(
    val isGenerating: Boolean = false,
    val isVisible: Boolean = false,
    val currentSpectrogramUri: String? = null,
    val spectrogramData: FloatArray? = null,
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f
)

/**
 * Represents the current search state
 */
data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MusicFile> = emptyList(),
    val isSearchVisible: Boolean = false,
    val searchHistory: List<String> = emptyList()
)

/**
 * Represents the current audio analysis state
 */
data class AudioAnalysisState(
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f,
    val currentAnalysisType: AnalysisType? = null,
    val analysisResults: Map<String, Any> = emptyMap(),
    val isBPMDetected: Boolean = false,
    val detectedBPM: Float = 0f,
    val detectedKey: String = "",
    val confidence: Float = 0f
)

/**
 * Represents the current file management state
 */
data class FileManagementState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val totalFilesFound: Int = 0,
    val filesProcessed: Int = 0,
    val isRescanning: Boolean = false,
    val currentSourcePath: String = "",
    val recentSources: List<String> = emptyList()
)

/**
 * Represents the current notification state
 */
data class NotificationState(
    val isNotificationEnabled: Boolean = true,
    val isMediaSessionActive: Boolean = false,
    val notificationTitle: String = "",
    val notificationArtist: String = "",
    val notificationArtwork: String? = null
)

/**
 * Represents the current error state
 */
data class ErrorState(
    val hasError: Boolean = false,
    val errorMessage: String = "",
    val errorType: ErrorType? = null,
    val isRetryable: Boolean = false
)

/**
 * Enum for repeat modes
 */
enum class RepeatMode {
    NONE,
    ONE,
    ALL
}

/**
 * Enum for analysis types
 */
enum class AnalysisType {
    SPECTROGRAM,
    BPM,
    KEY,
    WAVEFORM,
    FULL_ANALYSIS
}

/**
 * Enum for error types
 */
enum class ErrorType {
    FILE_ACCESS,
    NETWORK,
    AUDIO_PLAYBACK,
    FILE_OPERATION,
    PERMISSION,
    UNKNOWN
}
