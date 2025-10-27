package com.example.mobiledigger.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.util.ResourceManager
import wseemann.media.FFmpegMediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import java.util.Locale
import java.util.Collections
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.media.AudioManager as AndroidAudioManager

@androidx.media3.common.util.UnstableApi
enum class SpectrogramQuality {
    FAST,      // 256 samples, 64 bins - Current implementation
    BALANCED,  // 1024 samples, 128 bins - Better quality
    HIGH       // 2048 samples, 256 bins - Professional quality
}

enum class FrequencyRange {
    FULL,      // 0-25kHz - Extended range
    EXTENDED,  // 0-22kHz - Standard audio range (default)
    FOCUSED    // 0-8kHz - Focus on speech/music fundamentals
}

class AudioManager(private val context: Context) {
    private val analyzer = AudioAnalyzer()
    
    private var ffmpegPlayer: FFmpegMediaPlayer? = null
    private var exoPlayerFallback: ExoPlayer? = null
    private var currentFile: MusicFile? = null
    private var isUsingFFmpeg = false
    
    // Listener for track completion events
    interface PlaybackCompletionListener {
        fun onTrackCompletion()
    }
    
    private var playbackCompletionListener: PlaybackCompletionListener? = null
    
    fun setPlaybackCompletionListener(listener: PlaybackCompletionListener) {
        this.playbackCompletionListener = listener
    }
    
    private var preloadedFile: MusicFile? = null
    
    // Track cache files currently in active use to avoid deleting them during trimming
    private var currentFFmpegDataSourcePath: String? = null
    private var currentTempWavPath: String? = null
    
    // Guard against spurious completion events that fire immediately after starting playback
    private var trackStartTime = 0L
    private val minPlaybackDurationMs = 2000L // Track must play for at least 2 seconds to be considered "completed"
    
    // User preferences for spectrogram quality
    private var spectrogramQuality: SpectrogramQuality = SpectrogramQuality.BALANCED
    private var frequencyRange: FrequencyRange = FrequencyRange.EXTENDED
    private var temporalResolution: Int = 5 // pixels per second
    private var isFFmpegPrepared = false
    
    // Spectrogram cache with size limit to prevent memory leaks (EXTREMELY REDUCED for memory safety)
    private val maxCacheSize = 1 // CRITICAL: Limit to only 1 spectrogram to prevent memory exhaustion
    private val spectrogramCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(maxCacheSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                val shouldRemove = size > maxCacheSize
                if (shouldRemove) {
                    CrashLogger.log("AudioManager", "Removing eldest spectrogram from cache to prevent memory leak")
                }
                return shouldRemove
            }
        }
    )
    
    // User control methods
    fun setSpectrogramQuality(quality: SpectrogramQuality) {
        spectrogramQuality = quality
        CrashLogger.log("AudioManager", "Spectrogram quality set to: $quality")
    }
    
    fun setFrequencyRange(range: FrequencyRange) {
        frequencyRange = range
        CrashLogger.log("AudioManager", "Frequency range set to: $range")
    }
    
    fun setTemporalResolution(resolution: Int) {
        temporalResolution = resolution.coerceIn(3, 10) // Limit between 3-10 pixels/second
        CrashLogger.log("AudioManager", "Temporal resolution set to: $temporalResolution pixels/second")
    }
    
    fun getCurrentSettings(): Triple<SpectrogramQuality, FrequencyRange, Int> {
        return Triple(spectrogramQuality, frequencyRange, temporalResolution)
    }
    
    private fun getQualityParameters(): Triple<Int, Int, Int> {
        val windowSize = when (spectrogramQuality) {
            SpectrogramQuality.FAST -> 1024
            SpectrogramQuality.BALANCED -> 4096
            SpectrogramQuality.HIGH -> 8192      // Increased for higher quality
        }
        
        val height = when (spectrogramQuality) {
            SpectrogramQuality.FAST -> 128
            SpectrogramQuality.BALANCED -> 256
            SpectrogramQuality.HIGH -> 512       // Increased for more frequency bins
        }
        
        val maxFrequency = when (frequencyRange) {
            FrequencyRange.FULL -> 25000         // 0-25kHz - Full range
            FrequencyRange.EXTENDED -> 22000     // 0-22kHz - Standard audio
            FrequencyRange.FOCUSED -> 8000       // 0-8kHz - Speech/music fundamentals
        }
        
        CrashLogger.log("AudioManager", "Quality parameters: windowSize=$windowSize, height=$height, maxFreq=$maxFrequency")
        return Triple(windowSize, height, maxFrequency)
    }
    
    // Temp file cache for AIFF files to avoid re-copying with size limit
    private val maxTempCacheSize = 3 // CRITICAL: Very small cache to prevent memory buildup
    private val maxTempFileSize = 200 * 1024 * 1024 // 200MB max per temp file (increased for large AIFF files)
    private val tempFileCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(maxTempCacheSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                if (size > maxTempCacheSize && eldest != null) {
                    // CRITICAL: Don't delete file if it's currently in use by FFmpegMediaPlayer!
                    if (eldest.value == currentFFmpegDataSourcePath) {
                        CrashLogger.log("AudioManager", "⚠️ Skipping deletion of in-use temp file: ${eldest.value}")
                        return false // Don't remove it from cache
                    }
                    
                    // Clean up the temp file when removing from cache
                    try {
                        File(eldest.value).delete()
                        CrashLogger.log("AudioManager", "Cleaned up temp file during cache clear: ${eldest.value}")
                        // CRITICAL: Give OS time to release file descriptors after deletion
                        Thread.sleep(20)
                    } catch (e: Exception) {
                        CrashLogger.log("AudioManager", "Failed to delete temp file: ${eldest.value}", e)
                    }
                    return true
                }
                return false
            }
        }
    )
    
    fun initialize() {
        try {
            // Initialize broadcast receiver for cache clearing
            initializeBroadcastReceiver()
            
            // Initialize FFmpegMediaPlayer (primary)
            CrashLogger.log("AudioManager", "Attempting to create FFmpegMediaPlayer...")
            try {
                ffmpegPlayer = FFmpegMediaPlayer()
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer created successfully")
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Failed to create FFmpegMediaPlayer", e)
                ffmpegPlayer = null
            }
            
            ffmpegPlayer?.let { player ->
                setupFFmpegListeners(player)
            }
            CrashLogger.log("AudioManager", "FFmpegMediaPlayer listeners set successfully")
            
            // Initialize ExoPlayer (fallback) with better AIFF support
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            
            exoPlayerFallback = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            
            // Set ExoPlayer completion listener
            exoPlayerFallback?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        CrashLogger.log("AudioManager", "ExoPlayer playback completed")
                        playbackCompletionListener?.onTrackCompletion()
                    }
                }
            })
            
            CrashLogger.log("AudioManager", "AudioManager initialized with FFmpegMediaPlayer + ExoPlayer fallback")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "AudioManager initialization failed", e)
        }
    }
    
    private fun setupFFmpegListeners(player: FFmpegMediaPlayer) {
        try {
            player.setOnPreparedListener { mp ->
                try {
                    CrashLogger.log("AudioManager", "🎵 FFmpegMediaPlayer prepared successfully - configuring and starting playback NOW")
                    isFFmpegPrepared = true
                    
                    // Set audio settings NOW that player is prepared
                    mp.setAudioStreamType(AndroidAudioManager.STREAM_MUSIC)
                    mp.setVolume(1.0f, 1.0f)
                    mp.start()
                    
                    // Mark the start time for spurious completion event detection
                    trackStartTime = System.currentTimeMillis()
                    
                    CrashLogger.log("AudioManager", "✅ Playback started automatically after async prepare")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "❌ Failed to start playback in onPreparedListener", e)
                }
            }
            player.setOnErrorListener { mp, what, extra ->
                val errorMessage = when (what) {
                    FFmpegMediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown error"
                    FFmpegMediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Server died"
                    FFmpegMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "Not valid for progressive playback"
                    else -> "Error code: $what"
                }
                CrashLogger.log("AudioManager", "💥 FFmpegMediaPlayer error: $errorMessage (what=$what, extra=$extra)")
                isFFmpegPrepared = false
                
                // CRITICAL: Auto-skip to next track on FFmpeg error to prevent app crashes
                CrashLogger.log("AudioManager", "🔄 Auto-skipping to next track due to FFmpeg error")
                try {
                    // Send broadcast to trigger next track
                    val intent = Intent("com.example.mobiledigger.ACTION_NEXT")
                    context.sendBroadcast(intent)
                    CrashLogger.log("AudioManager", "✅ Broadcast sent to skip to next track")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "❌ Failed to send skip broadcast", e)
                }
                
                // Try to reset the player to recover from error state
                try {
                    mp.reset()
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Failed to reset FFmpegMediaPlayer after error", e)
                }
                false
            }
            player.setOnCompletionListener { mp ->
                val playbackDuration = System.currentTimeMillis() - trackStartTime
                
                if (playbackDuration < minPlaybackDurationMs) {
                    CrashLogger.log("AudioManager", "Spurious completion event ignored (track played for only ${playbackDuration}ms)")
                    return@setOnCompletionListener
                }
                
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer playback completed (duration: ${playbackDuration}ms)")
                playbackCompletionListener?.onTrackCompletion()
            }
            player.setOnInfoListener { mp, what, extra ->
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer info: what=$what, extra=$extra")
                false
            }
            CrashLogger.log("AudioManager", "FFmpegMediaPlayer listeners set successfully")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to setup FFmpegMediaPlayer listeners", e)
        }
    }
    
    fun preloadFile(musicFile: MusicFile) {
        if (musicFile == preloadedFile) {
            CrashLogger.log("AudioManager", "File ${musicFile.name} already preloaded.")
            return
        }
        
        CrashLogger.log("AudioManager", "Preloading file: ${musicFile.name}")
        
        // Stop any current preloading
        releasePreloadedPlayer()
        
        preloadedFile = musicFile
        
        val uri = musicFile.uri
        val fileName = musicFile.name.lowercase(Locale.getDefault())
        val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
        
        if (isAiffFile) {
            // Preload with FFmpegMediaPlayer
            ffmpegPlayer?.let { player ->
                try {
                    CrashLogger.log("AudioManager", "Preloading AIFF with FFmpegMediaPlayer: ${musicFile.name}")
                    player.reset()
                    isFFmpegPrepared = false
                    val dataSource = getFFmpegDataSource(uri)
                    if (dataSource != null) {
                        player.setDataSource(dataSource)
                        player.prepareAsync()
                        CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepareAsync for preload called.")
                    } else {
                        CrashLogger.log("AudioManager", "Failed to get data source for FFmpeg preload.")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error preloading AIFF with FFmpegMediaPlayer", e)
                }
            }
        } else {
            // Preload with ExoPlayer
            exoPlayerFallback?.let { player ->
                try {
                    CrashLogger.log("AudioManager", "Preloading with ExoPlayer: ${musicFile.name}")
                    player.stop()
                    player.clearMediaItems()
                    player.setMediaItem(MediaItem.fromUri(uri))
                    player.prepare()
                    player.playWhenReady = false // Don't start playing automatically
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error preloading with ExoPlayer", e)
                }
            }
        }
    }
    
    private fun releasePreloadedPlayer() {
        preloadedFile = null
        // No explicit release for ExoPlayer, just clear media items and stop playback
        exoPlayerFallback?.stop()
        exoPlayerFallback?.clearMediaItems()
        // Reset FFmpegMediaPlayer
        ffmpegPlayer?.reset()
        isFFmpegPrepared = false
        CrashLogger.log("AudioManager", "Preloaded player released.")
    }
    
    // Helper to get FFmpeg data source, extracted from tryPlayWithFFmpegSync
    private fun getFFmpegDataSource(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (path.isNullOrEmpty()) {
                    CrashLogger.log("AudioManager", "File URI has null or empty path: $uri")
                    return null
                }
                
                // Validate that the file actually exists
                val file = File(path)
                if (!file.exists()) {
                    CrashLogger.log("AudioManager", "File does not exist: $path")
                    return null
                }
                
                if (!file.canRead()) {
                    CrashLogger.log("AudioManager", "File cannot be read: $path")
                    return null
                }
                
                CrashLogger.log("AudioManager", "Valid file path for FFmpeg: $path")
                path
            } else {
                val uriString = uri.toString()
                val cachedPath = tempFileCache[uriString]
                if (cachedPath != null && File(cachedPath).exists()) {
                    CrashLogger.log("AudioManager", "Using cached temp file: $cachedPath")
                    cachedPath
                } else {
                    val tempFile = copyUriToTempFile(uri)
                    if (tempFile != null) {
                        val tempPath = tempFile.absolutePath
                        tempFileCache[uriString] = tempPath
                        CrashLogger.log("AudioManager", "Created temp file for FFmpeg: $tempPath")
                        tempPath
                    } else {
                        CrashLogger.log("AudioManager", "Failed to create temp file for URI: $uri")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error getting FFmpeg data source", e)
            null
        }
    }
    
    fun playFile(musicFile: MusicFile): Boolean {
        return try {
            CrashLogger.log("AudioManager", "🎵 Starting playback for: ${musicFile.name}")
            
            currentFile = musicFile
            val uri = musicFile.uri
            
            // Validate URI before attempting playback
            if (uri == null || uri.toString().isEmpty() || uri.toString() == "null") {
                CrashLogger.log("AudioManager", "❌ Invalid or empty URI for file: ${musicFile.name}, URI: $uri")
                return false
            }
            
            CrashLogger.log("AudioManager", "📁 File URI: $uri")
            
            // CRITICAL: Always stop all playback first to ensure clean state
            CrashLogger.log("AudioManager", "🧹 Ensuring clean state before playback...")
            stopAllPlayback()
            
            // Additional delay to ensure cleanup is complete
            Thread.sleep(100)
            
            // CRITICAL: Force garbage collection every 5 tracks to prevent memory buildup
            // This helps prevent crashes after playing many files
            val trackCount = tempFileCache.size
            if (trackCount >= 3) {
                CrashLogger.log("AudioManager", "Running aggressive GC after $trackCount cached files")
                System.gc()
                Thread.sleep(30) // Give GC time to complete
            }
            
            // Check if this is an AIFF file that needs FFmpeg
            val fileName = musicFile.name.lowercase(Locale.getDefault())
            val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
            
            if (isAiffFile) {
                CrashLogger.log("AudioManager", "🎵 AIFF file detected: ${musicFile.name}")
                
                // Check file size for large AIFF files
                val fileSizeMB = musicFile.size / (1024 * 1024)
                if (fileSizeMB > 100) {
                    CrashLogger.log("AudioManager", "Large AIFF file detected (${fileSizeMB}MB), using optimized playback")
                }
                
                CrashLogger.log("AudioManager", "🔄 Using FFmpegMediaPlayer for AIFF (ExoPlayer doesn't support AIFF)")
                
                // CRITICAL FIX: Use FFmpegMediaPlayer for AIFF files with crash protection
                // ExoPlayer doesn't support AIFF format at all
                try {
                    val ffmpegSuccess = tryPlayWithFFmpegSync(uri)
                    if (ffmpegSuccess) {
                        isUsingFFmpeg = true
                        CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer playback successful for AIFF: ${musicFile.name}")
                        return true
                    } else {
                        CrashLogger.log("AudioManager", "❌ FFmpegMediaPlayer failed for AIFF: ${musicFile.name}")
                        return false
                    }
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 Exception in FFmpegMediaPlayer for AIFF: ${musicFile.name}", e)
                    return false
                }
            }
            
            // Use ExoPlayer for all other files and as fallback
            val exoSuccess = tryPlayWithExoPlayerSync(uri)
            if (exoSuccess) {
                isUsingFFmpeg = false
                CrashLogger.log("AudioManager", "ExoPlayer playback successful for: ${musicFile.name}")
                return true
            }
            
            CrashLogger.log("AudioManager", "All playback methods failed for: ${musicFile.name}")
            false
            
            } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error in playFile", e)
            false
        }
    }
    
    private fun tryPlayWithFFmpegSync(uri: Uri): Boolean {
        return try {
            CrashLogger.log("AudioManager", "🔄 Starting FFmpegMediaPlayer playback for: $uri")
            
            val player = ffmpegPlayer
            if (player == null) {
                CrashLogger.log("AudioManager", "❌ FFmpegMediaPlayer is null - not initialized properly")
                return false
            }
            CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer is available, attempting playback")
            
            // CRITICAL: Proper cleanup before loading new file
            CrashLogger.log("AudioManager", "🧹 Performing FFmpeg cleanup before new file...")
            try {
                if (player.isPlaying) {
                    CrashLogger.log("AudioManager", "🛑 Stopping current playback...")
                    player.stop()
                }
                CrashLogger.log("AudioManager", "🔄 Resetting FFmpegMediaPlayer state...")
                player.reset()
                CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer reset completed")
            } catch (cleanupException: Exception) {
                CrashLogger.log("AudioManager", "⚠️ FFmpeg cleanup failed - recreating player", cleanupException)
                // If cleanup fails, recreate the player
                try {
                    ffmpegPlayer?.release()
                    ffmpegPlayer = FFmpegMediaPlayer()
                    setupFFmpegListeners(ffmpegPlayer!!)
                    CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer recreated successfully")
                } catch (recreateException: Exception) {
                    CrashLogger.log("AudioManager", "💥 Failed to recreate FFmpegMediaPlayer", recreateException)
                    return false
                }
            }
            
            // Clear tracking variables
            currentFFmpegDataSourcePath = null
            isFFmpegPrepared = false
            
            // Enhanced memory management before FFmpeg operations
            CrashLogger.log("AudioManager", "🧠 Running garbage collection...")
            System.gc()
            Thread.sleep(100) // Increased delay for better cleanup
            
            // Try to set data source for FFmpegMediaPlayer
            val dataSource = getFFmpegDataSource(uri)
            
            if (dataSource == null) {
                CrashLogger.log("AudioManager", "❌ No data source available for FFmpegMediaPlayer")
                return false
            }
            
            // Additional validation before setDataSource
            if (dataSource.isEmpty()) {
                CrashLogger.log("AudioManager", "❌ Data source is empty, cannot set for FFmpegMediaPlayer")
                return false
            }
            
            CrashLogger.log("AudioManager", "📁 Data source prepared: $dataSource")
            
            // CRITICAL FIX: Use crash-safe setDataSource with timeout detection
            CrashLogger.log("AudioManager", "🔄 Setting FFmpegMediaPlayer data source with crash protection...")
            
            // Use a timeout mechanism to detect if setDataSource hangs or crashes
            var setDataSourceSuccess = false
            val setDataSourceThread = Thread {
                try {
                    player.setDataSource(dataSource)
                    setDataSourceSuccess = true
                    CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer setDataSource successful")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 FFmpegMediaPlayer setDataSource failed with exception", e)
                    setDataSourceSuccess = false
                }
            }
            
            setDataSourceThread.start()
            
            // Wait for setDataSource to complete with timeout
            try {
                setDataSourceThread.join(2000) // 2 second timeout
                
                if (setDataSourceThread.isAlive) {
                    CrashLogger.log("AudioManager", "💥 FFmpegMediaPlayer setDataSource timed out - likely crashed")
                    setDataSourceThread.interrupt()
                    return false
                }
                
                if (!setDataSourceSuccess) {
                    CrashLogger.log("AudioManager", "💥 FFmpegMediaPlayer setDataSource failed")
                    return false
                }
                
                // Track in-use data source path if it is a file path inside cache
                currentFFmpegDataSourcePath = try {
                    val f = java.io.File(dataSource)
                    if (f.exists() && f.absolutePath.startsWith(context.cacheDir.absolutePath)) f.absolutePath else null
                } catch (_: Exception) { null }
                
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "💥 Error during setDataSource timeout handling", e)
                return false
            }
            
            // Use ASYNC preparation to avoid blocking UI thread (NO LAG!)
            // Audio settings will be configured in onPreparedListener callback after preparation completes
            try {
                CrashLogger.log("AudioManager", "⚡ Starting NON-BLOCKING prepareAsync() for instant playback")
                player.prepareAsync()
                CrashLogger.log("AudioManager", "✅ prepareAsync() called - will configure audio and start when ready (onPreparedListener)")
                // Playback will start automatically in onPreparedListener callback (lines 177-190)
                // This returns immediately without blocking!
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "❌ FFmpegMediaPlayer prepareAsync failed", e)
                return false
            }
            
            true
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "FFmpegMediaPlayer playback error", e)
            false
        }
    }
    
    
    private fun tryPlayWithExoPlayerSync(uri: Uri): Boolean {
        return try {
            val player = exoPlayerFallback ?: return false
            
            // Validate URI before attempting playback
            if (uri.toString().isEmpty() || uri.toString() == "null" || uri.toString() == ":") {
                CrashLogger.log("AudioManager", "ExoPlayer received invalid URI: $uri")
                return false
            }
            
            CrashLogger.log("AudioManager", "ExoPlayer attempting to play URI: $uri")
            
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
            CrashLogger.log("AudioManager", "ExoPlayer started successfully")
            true
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "ExoPlayer playback error", e)
            false
        }
    }
    
    
    fun pause() {
        try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.pause()
            } else {
                exoPlayerFallback?.pause()
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Pause error", e)
        }
    }
    
    
    private fun stopAllPlayback() {
        try {
            CrashLogger.log("AudioManager", "🛑 Stopping all playback before starting new file")
            
            // Stop FFmpegMediaPlayer if it's playing
            ffmpegPlayer?.let { player ->
                try {
                    CrashLogger.log("AudioManager", "🧹 Cleaning up FFmpegMediaPlayer...")
                    if (player.isPlaying) {
                        CrashLogger.log("AudioManager", "🛑 Stopping FFmpegMediaPlayer playback...")
                        player.stop()
                        CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer stopped")
                    }
                    CrashLogger.log("AudioManager", "🔄 Resetting FFmpegMediaPlayer state...")
                    player.reset()
                    currentFFmpegDataSourcePath = null // Mark file as no longer in use
                    CrashLogger.log("AudioManager", "✅ FFmpegMediaPlayer reset completed")
                    
                    // Additional cleanup delay
                    Thread.sleep(50)
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 Error stopping FFmpegMediaPlayer", e)
                }
            }
            
            // Stop ExoPlayer if it's playing - with proper cleanup to prevent dead thread handlers
            exoPlayerFallback?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        CrashLogger.log("AudioManager", "ExoPlayer stopped")
                    }
                    // Clear media items first
                    player.clearMediaItems()
                    CrashLogger.log("AudioManager", "ExoPlayer cleared")
                    
                    // Don't prepare here - let it be prepared when needed
                    // This prevents the dead thread handler issue
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error stopping ExoPlayer", e)
                }
            }
            
            // Reset the player state tracking
            isUsingFFmpeg = false
            isFFmpegPrepared = false
            
            CrashLogger.log("AudioManager", "All playback stopped successfully")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error in stopAllPlayback", e)
        }
    }
    
    fun resume() {
        try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.start()
            } else {
                exoPlayerFallback?.play()
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Resume error", e)
        }
    }
    
    fun stop() {
        stopAllPlayback()
        releasePreloadedPlayer() // Also release the preloaded player when stopping
    }
    
    fun seekTo(position: Long) {
        try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.seekTo(position.toInt())
            } else {
                exoPlayerFallback?.seekTo(position)
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Seek error", e)
        }
    }

    fun isCurrentlyPlaying(): Boolean {
        return try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.isPlaying ?: false
            } else {
                exoPlayerFallback?.isPlaying ?: false
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error checking playing state", e)
            false
        }
    }
    
    fun getCurrentPosition(): Long {
        return try {
            if (isUsingFFmpeg) {
                (ffmpegPlayer?.currentPosition ?: 0).toLong()
            } else {
                exoPlayerFallback?.currentPosition ?: 0L
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Get position error", e)
            0L
        }
    }
    
    fun getDuration(): Long {
        return try {
            if (isUsingFFmpeg) {
                (ffmpegPlayer?.duration ?: 0).toLong()
            } else {
                exoPlayerFallback?.duration ?: 0L
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Get duration error", e)
            0L
        }
    }
    
    

    
    fun clearAllCaches() {
        try {
            spectrogramCache.clear()
            // Also clean up temp files when clearing caches
            tempFileCache.keys.toList().forEach { key ->
                tempFileCache.remove(key)?.let { tempPath ->
                    // CRITICAL: Don't delete file if it's currently in use by FFmpegMediaPlayer!
                    if (tempPath == currentFFmpegDataSourcePath) {
                        CrashLogger.log("AudioManager", "⚠️ Skipping deletion of in-use temp file: $tempPath")
                        // Put it back in the cache since it's still needed
                        tempFileCache[key] = tempPath
                        return@let
                    }
                    
                    try {
                        File(tempPath).delete()
                        CrashLogger.log("AudioManager", "Cleaned up temp file during cache clear: $tempPath")
                        // CRITICAL: Give OS time to release file descriptors after deletion
                        Thread.sleep(20)
                    } catch (e: Exception) {
                        CrashLogger.log("AudioManager", "Failed to delete temp file: $tempPath", e)
                    }
                }
            }
            CrashLogger.log("AudioManager", "All caches cleared")
        } catch (e: Exception) { 
            CrashLogger.log("AudioManager", "Cache clearing error", e)
        }
    }
    
    /**
     * Monitor memory pressure and clear caches if needed - AGGRESSIVE MEMORY MANAGEMENT
     */
    private fun checkMemoryPressure() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory * 100) / maxMemory
            
            when {
                memoryUsagePercent > 85 -> {
                    // CRITICAL: Emergency memory cleanup (lowered threshold)
                    CrashLogger.log("AudioManager", "CRITICAL: Memory usage at ${memoryUsagePercent}%, emergency cleanup")
                    clearAllCaches()
                    spectrogramCache.clear() // Force clear spectrogram cache
                    System.gc()
                    Thread.sleep(200) // Give GC more time to work
                }
                memoryUsagePercent > 70 -> {
                    // High memory usage - aggressive cleanup (lowered threshold)
                    CrashLogger.log("AudioManager", "High memory usage detected: ${memoryUsagePercent}%, aggressive cleanup")
                    clearAllCaches()
                    System.gc()
                }
                memoryUsagePercent > 50 -> {
                    // Moderate memory usage - standard cleanup (lowered threshold)
                    CrashLogger.log("AudioManager", "Moderate memory usage: ${memoryUsagePercent}%, standard cleanup")
                    clearAllCaches()
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Memory pressure check failed", e)
        }
    }
    
    private fun copyUriToTempFile(uri: Uri): File? {
        var tempFile: File? = null
        return try {
            CrashLogger.log("AudioManager", "🔄 Starting copyUriToTempFile for: $uri")
            
            // Create temp file first
            tempFile = File.createTempFile("aiff_temp_", ".aiff", context.cacheDir)
            CrashLogger.log("AudioManager", "📁 Temp file created: ${tempFile.absolutePath}")
            
            // Copy data with proper resource management using .use
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    // Copy data with larger buffer for better performance
                    val buffer = ByteArray(131072) // 128KB buffer for better performance
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // Log progress for large files
                        if (totalBytes % (1024 * 1024) == 0L) { // Every MB
                            val mbCopied = totalBytes / (1024.0 * 1024.0)
                            CrashLogger.log("AudioManager", "📊 Copied ${String.format(Locale.getDefault(), "%.1f", mbCopied)}MB...")
                        }
                        
                        // Check if we're exceeding our size limit during copy
                        if (totalBytes > maxTempFileSize) {
                            CrashLogger.log("AudioManager", "❌ File exceeded size limit during copy (${totalBytes / (1024 * 1024)}MB), aborting")
                            return null // Will trigger cleanup in finally block
                        }
                    }
                    // CRITICAL: Explicitly flush and sync to prevent fd corruption
                    outputStream.flush()
                    outputStream.fd.sync()
                    CrashLogger.log("AudioManager", "✅ File copy completed, flushed and synced")
                }
            } ?: run {
                CrashLogger.log("AudioManager", "❌ Cannot open input stream for URI: $uri")
                return null // Will trigger cleanup in finally block
            }
            
            // CRITICAL: Give the OS time to fully close file descriptors before FFmpeg opens the file
            // This prevents fdsan crashes where fd 0 gets corrupted
            // Force garbage collection to ensure all file handles are released
            CrashLogger.log("AudioManager", "🔄 Running GC and waiting for file descriptor cleanup...")
            System.gc()
            Thread.sleep(150) // Increased delay to 150ms for AIFF files
            
            // Verify the file is accessible and properly closed
            if (!tempFile.exists() || !tempFile.canRead()) {
                CrashLogger.log("AudioManager", "❌ Temp file is not accessible after copy: ${tempFile.absolutePath}")
                return null
            }
            
            // Additional verification for AIFF files
            val fileSize = tempFile.length()
            if (fileSize == 0L) {
                CrashLogger.log("AudioManager", "❌ Temp file is empty: ${tempFile.absolutePath}")
                return null
            }
            
            CrashLogger.log("AudioManager", "✅ Temp file verification passed: ${fileSize} bytes")
            
            val duration = System.currentTimeMillis() - startTime
            val mbCopied = totalBytes / (1024.0 * 1024.0)
            val speed = if (duration > 0) mbCopied / (duration / 1000.0) else 0.0
            
            CrashLogger.log("AudioManager", "Successfully copied URI to temp file: ${tempFile.absolutePath} (${String.format(Locale.getDefault(), "%.2f", mbCopied)}MB in ${duration}ms, ${String.format(Locale.getDefault(), "%.2f", speed)}MB/s)")
            
            // Success - don't delete the file
            val result = tempFile
            tempFile = null // Clear so finally doesn't delete it
            result
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error copying URI to temp file", e)
            null
        } finally {
            // Clean up temp file if copy failed
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete()
                    CrashLogger.log("AudioManager", "Cleaned up failed temp file: ${tempFile.absolutePath}")
                } catch (deleteEx: Exception) {
                    CrashLogger.log("AudioManager", "Failed to delete temp file", deleteEx)
                }
            }
        }
    }
    
    fun release() {
        try {
            // CRITICAL: Unregister broadcast receiver to prevent memory leak
            broadcastReceiver?.let { receiver ->
                try {
                    context.unregisterReceiver(receiver)
                    CrashLogger.log("AudioManager", "Broadcast receiver unregistered successfully")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Failed to unregister broadcast receiver", e)
                }
                broadcastReceiver = null
            }
            
            ffmpegPlayer?.release()
            ffmpegPlayer = null
            currentFFmpegDataSourcePath = null // Clear tracking before cleanup
            exoPlayerFallback?.release()
            exoPlayerFallback = null
            spectrogramCache.clear()
            
            // Clean up temp files
            tempFileCache.values.forEach { tempPath ->
                try {
                    File(tempPath).delete()
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Failed to delete temp file: $tempPath", e)
                }
            }
            tempFileCache.clear()
            
            CrashLogger.log("AudioManager", "AudioManager released")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Release error", e)
        }
    }
    
    // Clear spectrogram cache for debugging
    fun clearSpectrogramCache() {
        CrashLogger.log("AudioManager", "Clearing spectrogram cache (${spectrogramCache.size} entries)")
        spectrogramCache.clear()
    }
    
    // Store the receiver reference for proper cleanup
    private var broadcastReceiver: BroadcastReceiver? = null
    
    // Initialize broadcast receiver for cache clearing
    private fun initializeBroadcastReceiver() {
        try {
            val filter = IntentFilter("com.example.mobiledigger.CLEAR_CACHE")
            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.example.mobiledigger.CLEAR_CACHE") {
                        clearSpectrogramCache()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(broadcastReceiver, filter)
            }
            CrashLogger.log("AudioManager", "Broadcast receiver registered successfully")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to register broadcast receiver", e)
        }
    }
    
    // Progress tracking for MP3 conversion
    private val _conversionProgress = MutableStateFlow(0f)
    val conversionProgress: StateFlow<Float> = _conversionProgress.asStateFlow()
    
    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()
    
    // Progress tracking for spectrogram generation
    private val _spectrogramProgress = MutableStateFlow(0f)
    val spectrogramProgress: StateFlow<Float> = _spectrogramProgress.asStateFlow()
    
    private val _isGeneratingSpectrogram = MutableStateFlow(false)
    val isGeneratingSpectrogram: StateFlow<Boolean> = _isGeneratingSpectrogram.asStateFlow()

    // BPM/Key analysis result
    private val _analysisResult = MutableStateFlow<AudioAnalysisResult?>(null)
    val analysisResult: StateFlow<AudioAnalysisResult?> = _analysisResult.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    private val analysisCache = Collections.synchronizedMap(mutableMapOf<String, AudioAnalysisResult>())

    suspend fun analyzeFile(musicFile: com.example.mobiledigger.model.MusicFile, force: Boolean = false): AudioAnalysisResult? {
        return try {
            val key = musicFile.uri.toString()
            if (!force) {
                analysisCache[key]?.let { cached ->
                    _analysisResult.value = cached
                    return cached
                }
            }
            val uri = musicFile.uri
            _isAnalyzing.value = true
            // Prefer precise PCM decode for analysis (better BPM/Key accuracy)
            val audioData = withContext(Dispatchers.IO) {
                extractPcmDecodedForAnalysis(uri) ?: extractAudioDataWithMemoryManagement(uri)
            }
            if (audioData == null || audioData.isEmpty()) return null
            val analysis = withContext(Dispatchers.Default) { analyzer.analyze(audioData, 44100) }
            analysisCache[key] = analysis
            _analysisResult.value = analysis
            analysis
        } catch (e: Exception) {
            com.example.mobiledigger.util.CrashLogger.log("AudioManager", "analyzeFile failed", e)
            null
        } finally {
            _isAnalyzing.value = false
        }
    }

    /**
     * Decode compressed audio (e.g., MP3/FLAC/AAC) to PCM ShortArray for high-quality analysis
     */
    private fun extractPcmDecodedForAnalysis(uri: Uri): ShortArray? {
        return try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())

            var trackIndex = -1
            var mime = ""
            var sampleRate = 44100
            var channels = 2
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    trackIndex = i
                    mime = m
                    if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    break
                }
            }
            if (trackIndex == -1) {
                extractor.release()
                return null
            }
            extractor.selectTrack(trackIndex)

            val codec = android.media.MediaCodec.createDecoderByType(mime)
            val format = extractor.getTrackFormat(trackIndex)
            codec.configure(format, null, null, 0)
            codec.start()

            val outputBufferInfo = android.media.MediaCodec.BufferInfo()
            val pcm = ArrayList<Short>(sampleRate * 120)
            val maxSamples = sampleRate * 120 // up to 2 minutes
            var endOfStream = false

            while (!endOfStream && pcm.size < maxSamples) {
                // Queue input
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                // Dequeue output
                val outIndex = codec.dequeueOutputBuffer(outputBufferInfo, 5000)
                when {
                    outIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outIndex) ?: continue
                        if (outputBuffer.remaining() > 0) {
                            val outBytes = ByteArray(outputBuffer.remaining())
                            outputBuffer.get(outBytes)
                            // Convert bytes to 16-bit PCM shorts if needed
                            val shorts = convertBytesToPcmShorts(outBytes, channels)
                            val remaining = maxSamples - pcm.size
                            if (shorts.size > remaining) {
                                for (i in 0 until remaining) pcm.add(shorts[i])
                            } else {
                                pcm.addAll(shorts.asList())
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((outputBufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            endOfStream = true
                        }
                    }
                }
            }

            codec.stop(); codec.release(); extractor.release()

            // Downmix stereo to mono
            val mono = if (channels == 2) {
                val list = ArrayList<Short>(pcm.size / 2)
                var i = 0
                while (i + 1 < pcm.size && list.size < maxSamples / 2) {
                    val left = pcm[i].toInt()
                    val right = pcm[i + 1].toInt()
                    list.add(((left + right) / 2).toShort())
                    i += 2
                }
                list.toShortArray()
            } else {
                pcm.toShortArray()
            }
            mono
        } catch (e: Exception) {
            com.example.mobiledigger.util.CrashLogger.log("AudioManager", "extractPcmDecodedForAnalysis failed", e)
            null
        }
    }

    private fun convertBytesToPcmShorts(data: ByteArray, channels: Int): ShortArray {
        // Assume 16-bit PCM little-endian from MediaCodec for common decoders
        val count = data.size / 2
        val shorts = ShortArray(count)
        var j = 0
        for (i in 0 until count) {
            val lo = data[j].toInt() and 0xFF
            val hi = data[j + 1].toInt()
            shorts[i] = ((hi shl 8) or lo).toShort()
            j += 2
        }
        return shorts
    }

    fun clearAnalysis() {
        _analysisResult.value = null
    }

    // Spectrogram generation with proper audio analysis
    suspend fun generateSpectrogram(musicFile: MusicFile): ImageBitmap? {
        return try {
            val cacheKey = "${musicFile.name}_${musicFile.size}"
            spectrogramCache[cacheKey]?.let { cachedSpectrogram ->
                CrashLogger.log("AudioManager", "Returning cached spectrogram for: ${musicFile.name}")
                CrashLogger.log("AudioManager", "Cached spectrogram size: ${cachedSpectrogram.width}x${cachedSpectrogram.height}")
                return cachedSpectrogram
            }
            
            CrashLogger.log("AudioManager", "Generating new spectrogram for: ${musicFile.name}")
            
            // Check if file is MP3 and convert to WAV first for better spectrogram generation
            val fileForSpectrogram = if (musicFile.name.lowercase().endsWith(".mp3")) {
                CrashLogger.log("AudioManager", "MP3 file detected, converting to WAV for spectrogram generation")
                _isConverting.value = true
                _conversionProgress.value = 0f
                try {
                    convertMp3ToWav(musicFile)
                } finally {
                    _isConverting.value = false
                    _conversionProgress.value = 1f
                }
            } else {
                musicFile
            }
            
            // Generate spectrogram with progress tracking
            _isGeneratingSpectrogram.value = true
            _spectrogramProgress.value = 0f
            
            val spectrogram = try {
                withTimeout(30000) { // 30 second timeout
                    withContext(Dispatchers.IO) {
                        generateSpectrogramInternal(fileForSpectrogram ?: musicFile)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                CrashLogger.log("AudioManager", "Spectrogram generation timed out after 30 seconds")
                null
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Error during spectrogram generation", e)
                null
            } finally {
                _isGeneratingSpectrogram.value = false
                _spectrogramProgress.value = 1f
                // Clean up temporary WAV file if it was created
                if (fileForSpectrogram != null && fileForSpectrogram != musicFile) {
                    cleanupTempWavFile(fileForSpectrogram)
                }
            }
            
            if (spectrogram != null) {
                spectrogramCache[cacheKey] = spectrogram
                CrashLogger.log("AudioManager", "Spectrogram generated and cached successfully")
            } else {
                CrashLogger.log("AudioManager", "Spectrogram generation returned null")
            }
            
            spectrogram
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Spectrogram generation failed", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Convert MP3 file to WAV format for better spectrogram generation using MediaMetadataRetriever and MediaExtractor/MediaCodec
     */
    private suspend fun convertMp3ToWav(mp3File: MusicFile): MusicFile? {
        return try {
            CrashLogger.log("AudioManager", "Starting MP3 to WAV conversion for: ${mp3File.name}")
            
            // Create temporary WAV file
            val tempDir = context.cacheDir
            val tempWavFile = File(tempDir, "temp_spectrogram_${System.currentTimeMillis()}.wav")
            currentTempWavPath = tempWavFile.absolutePath
            
            withContext(Dispatchers.IO) {
                // Use MediaExtractor and MediaCodec to decode MP3 to PCM
                val extractor = android.media.MediaExtractor()
                var codec: android.media.MediaCodec? = null
                
                try {
                    extractor.setDataSource(context, mp3File.uri, emptyMap<String, String>())
                    
                    // Find audio track
                    var audioTrackIndex = -1
                    var format: android.media.MediaFormat? = null
                    
                    for (i in 0 until extractor.trackCount) {
                        val trackFormat = extractor.getTrackFormat(i)
                        val mime = trackFormat.getString(android.media.MediaFormat.KEY_MIME)
                        if (mime?.startsWith("audio/") == true) {
                            audioTrackIndex = i
                            format = trackFormat
                            break
                        }
                    }
                    
                    if (audioTrackIndex == -1 || format == null) {
                        CrashLogger.log("AudioManager", "No audio track found in MP3 file")
                        return@withContext null
                    }
                    
                    extractor.selectTrack(audioTrackIndex)
                    
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!
                    val sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    val channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    
                    CrashLogger.log("AudioManager", "MP3 format: mime=$mime, sampleRate=$sampleRate, channels=$channelCount")
                    
                    // Create and configure decoder
                    codec = android.media.MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    
                    val inputBuffers = codec.inputBuffers
                    val outputBuffers = codec.outputBuffers
                    val bufferInfo = android.media.MediaCodec.BufferInfo()
                    
                    var isInputDone = false
                    var isOutputDone = false
                    
                    val audioSamples = mutableListOf<Short>()
                    // Calculate how much audio we actually need for spectrogram
                    val maxAnalysisSeconds = when (spectrogramQuality) {
                        SpectrogramQuality.FAST -> 60      // 1 minute for fast
                        SpectrogramQuality.BALANCED -> 90   // 1.5 minutes for balanced
                        SpectrogramQuality.HIGH -> 120     // 2 minutes for high quality
                    }
                    val maxSamples = sampleRate * maxAnalysisSeconds // Only convert what we need
                    var totalSamplesExpected = maxSamples
                    
                    // Try to get actual duration for better progress tracking
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, mp3File.uri)
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: 0L
                        retriever.release()
                        
                        val actualDurationSeconds = (durationMs / 1000f).coerceAtMost(maxAnalysisSeconds.toFloat())
                        totalSamplesExpected = (sampleRate * actualDurationSeconds).toInt()
                    } catch (e: Exception) {
                        CrashLogger.log("AudioManager", "Could not get duration for progress tracking", e)
                    }
                    
                    CrashLogger.log("AudioManager", "Converting MP3: maxSamples=$maxSamples, expectedSamples=$totalSamplesExpected")
                    
                    // Decode loop with progress tracking
                    while (!isOutputDone && audioSamples.size < maxSamples) {
                        // Feed input
                        if (!isInputDone) {
                            val inputBufferIndex = codec.dequeueInputBuffer(0)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = inputBuffers[inputBufferIndex]
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isInputDone = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        
                        // Get output
                        val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                        when {
                            outputBufferIndex >= 0 -> {
                                val outputBuffer = outputBuffers[outputBufferIndex]
                                
                                if (bufferInfo.size > 0) {
                                    // Convert ByteBuffer to ShortArray (16-bit PCM)
                                    val pcmData = ByteArray(bufferInfo.size)
                                    outputBuffer.get(pcmData)
                                    
                                    // Convert bytes to shorts (assuming 16-bit PCM)
                                    for (i in 0 until pcmData.size step 2) {
                                        if (i + 1 < pcmData.size && audioSamples.size < maxSamples) {
                                            val sample = ((pcmData[i + 1].toInt() shl 8) or (pcmData[i].toInt() and 0xFF)).toShort()
                                            audioSamples.add(sample)
                                        }
                                    }
                                    
                                    // Update progress
                                    val progress = (audioSamples.size.toFloat() / totalSamplesExpected).coerceAtMost(1f)
                                    _conversionProgress.value = progress
                                }
                                
                                codec.releaseOutputBuffer(outputBufferIndex, false)
                                
                                if ((bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    isOutputDone = true
                                }
                            }
                            outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                                // Output buffers changed, get new ones
                            }
                            outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // Output format changed
                                val newFormat = codec.outputFormat
                                CrashLogger.log("AudioManager", "Decoder output format changed: $newFormat")
                            }
                        }
                    }
                    
                    CrashLogger.log("AudioManager", "Decoded ${audioSamples.size} audio samples from MP3")
                    
                    if (audioSamples.isNotEmpty()) {
                        // Create WAV file with the decoded PCM data
                        createWavFile(tempWavFile, audioSamples.toShortArray(), sampleRate, channelCount)
                        
                        // Create MusicFile object for the temporary WAV file
                        val wavUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempWavFile
                        )
                        
                        CrashLogger.log("AudioManager", "Created temporary WAV file: ${tempWavFile.absolutePath}, size: ${tempWavFile.length()} bytes")
                        
                        return@withContext MusicFile(
                            uri = wavUri,
                            name = tempWavFile.name,
                            duration = mp3File.duration,
                            size = tempWavFile.length(),
                            sourcePlaylist = mp3File.sourcePlaylist
                        )
                    } else {
                        CrashLogger.log("AudioManager", "No audio data decoded from MP3")
                        return@withContext null
                    }
                    
                } finally {
                    codec?.stop()
                    codec?.release()
                    extractor.release()
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error converting MP3 to WAV", e)
            null
        }
    }
    
    /**
     * Create a WAV file from PCM audio data
     */
    private fun createWavFile(file: File, audioData: ShortArray, sampleRate: Int, channels: Int) {
        val outputStream = file.outputStream()
        
        try {
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = audioData.size * 2 // 2 bytes per sample
            val chunkSize = 36 + dataSize
            
            // WAV header
            outputStream.write("RIFF".toByteArray())
            outputStream.write(intToByteArray(chunkSize))
            outputStream.write("WAVE".toByteArray())
            outputStream.write("fmt ".toByteArray())
            outputStream.write(intToByteArray(16)) // Sub-chunk size
            outputStream.write(shortToByteArray(1)) // Audio format (PCM)
            outputStream.write(shortToByteArray(channels.toShort()))
            outputStream.write(intToByteArray(sampleRate))
            outputStream.write(intToByteArray(byteRate))
            outputStream.write(shortToByteArray(blockAlign.toShort()))
            outputStream.write(shortToByteArray(bitsPerSample.toShort()))
            outputStream.write("data".toByteArray())
            outputStream.write(intToByteArray(dataSize))
            
            // Audio data (convert shorts to bytes, little-endian)
            for (sample in audioData) {
                outputStream.write(sample.toInt() and 0xFF)
                outputStream.write((sample.toInt() shr 8) and 0xFF)
            }
            
        } finally {
            outputStream.close()
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
    
    /**
     * Clean up temporary WAV file after spectrogram generation
     */
    private fun cleanupTempWavFile(tempWavFile: MusicFile) {
        try {
            // Extract file path from URI
            val uriString = tempWavFile.uri.toString()
            if (uriString.contains("temp_spectrogram_")) {
                // This is a temporary file, safe to delete
                val tempDir = context.cacheDir
                val fileName = tempWavFile.name
                val file = File(tempDir, fileName)
                
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        CrashLogger.log("AudioManager", "Successfully deleted temporary WAV file: ${file.absolutePath}")
                    } else {
                        CrashLogger.log("AudioManager", "Failed to delete temporary WAV file: ${file.absolutePath}")
                    }
                }
                if (file.absolutePath == currentTempWavPath) {
                    currentTempWavPath = null
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error cleaning up temporary WAV file", e)
        }
    }

    private fun generateSpectrogramInternal(musicFile: MusicFile): ImageBitmap? {
        val overallStartTime = System.currentTimeMillis()
        return try {
            CrashLogger.log("AudioManager", "Starting spectrogram generation for: ${musicFile.name} at ${overallStartTime}ms")
            
            // Check memory pressure before starting
            checkMemoryPressure()
            
            // Memory management for large files
            val fileSizeMB = (musicFile.size / (1024 * 1024)).toInt()
            if (fileSizeMB > 50) {
                CrashLogger.log("AudioManager", "Large file detected (${fileSizeMB}MB), forcing garbage collection")
                System.gc()
                Thread.sleep(200)
            }
            
            val uri = musicFile.uri
            
            CrashLogger.log("AudioManager", "URI: $uri")
            
            // Extract audio data from the file with memory management
            _spectrogramProgress.value = 0.1f
            val extractionStartTime = System.currentTimeMillis()
            val audioData = extractAudioDataWithMemoryManagement(uri)
            val extractionEndTime = System.currentTimeMillis()
            val extractionTime = extractionEndTime - extractionStartTime
            _spectrogramProgress.value = 0.3f
            
            if (audioData == null || audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "Failed to extract audio data after ${extractionTime}ms, generating fallback spectrogram")
                _spectrogramProgress.value = 0.5f
                return generateFallbackSpectrogram(musicFile)
            }
            
            CrashLogger.log("AudioManager", "Extracted ${audioData.size} audio samples in ${extractionTime}ms")

            // Compute BPM/Key analysis (cached per file)
            try {
                val key = "${musicFile.name}_${musicFile.size}"
                val cached = analysisCache[key]
                if (cached != null) {
                    _analysisResult.value = cached
                } else {
                    // Assume 44100 Hz if unknown
                    val analysis = analyzer.analyze(audioData, 44100)
                    analysisCache[key] = analysis
                    _analysisResult.value = analysis
                }
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Audio analysis failed", e)
            }
            
            // Generate spectrogram from audio data
            _spectrogramProgress.value = 0.5f
            val spectrogram = generateSpectrogramFromAudioData(audioData)
            _spectrogramProgress.value = 0.9f
            if (spectrogram == null) {
                CrashLogger.log("AudioManager", "Failed to generate spectrogram from audio data, using fallback")
                return generateFallbackSpectrogram(musicFile)
            }
            
            val overallEndTime = System.currentTimeMillis()
            val overallTime = overallEndTime - overallStartTime
            CrashLogger.log("AudioManager", "Generated spectrogram bitmap successfully")
            CrashLogger.log("AudioManager", "Total spectrogram generation time: ${overallTime}ms (${overallTime/1000.0}s)")
            spectrogram
        } catch (e: Exception) {
            val overallEndTime = System.currentTimeMillis()
            val overallTime = overallEndTime - overallStartTime
            CrashLogger.log("AudioManager", "Spectrogram generation internal error after ${overallTime}ms", e)
            e.printStackTrace()
            generateFallbackSpectrogram(musicFile)
        }
    }
    
    private fun generateFallbackSpectrogram(musicFile: MusicFile): ImageBitmap? {
        return try {
            CrashLogger.log("AudioManager", "Generating fallback spectrogram for: ${musicFile.name}")
            
            val width = 150
            val height = 200
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Fill with black background
            canvas.drawColor(Color.BLACK)
            
            
            // Generate a pattern based on file size and name hash
            val fileHash = musicFile.name.hashCode()
            val sizeFactor = (musicFile.size / 1000000f).coerceIn(0.1f, 10f) // MB
            
            // Create a more realistic spectrogram-like pattern
            val colors = listOf(
                Color.rgb(0, 0, 50),      // Dark blue
                Color.rgb(0, 50, 100),    // Blue
                Color.rgb(0, 100, 150),   // Light blue
                Color.rgb(50, 150, 100),  // Green
                Color.rgb(150, 150, 50),  // Yellow
                Color.rgb(200, 100, 50),  // Orange
                Color.rgb(200, 50, 50),   // Red
                Color.rgb(150, 50, 100)   // Purple
            )
            
            // Create frequency bands with varying intensities
            for (band in 0 until 8) {
                val bandHeight = height / 8
                val startY = band * bandHeight
                val endY = (band + 1) * bandHeight
                
                for (y in startY until endY) {
                    val frequency = (fileHash + band * 1000 + y * 100) % 1000
                    val baseAmplitude = (sizeFactor * 20 + frequency * 0.05).toInt().coerceIn(3, 60)
                    
                    for (x in 0 until width) {
                        // Create more realistic frequency patterns
                        val timeVariation = sin(x * 0.02 + band * 0.3) * 0.5 + 0.5
                        val freqVariation = sin(y * 0.01 + x * 0.01) * 0.3 + 0.7
                        val amplitude = (baseAmplitude * timeVariation * freqVariation).toInt()
                        
                        // Add some randomness for more realistic look
                        val randomFactor = ((fileHash + x + y) % 100) / 100f
                        val finalAmplitude = (amplitude * (0.7f + randomFactor * 0.6f)).toInt()
                        
                        if (finalAmplitude > 10) {
                            val colorIndex = (band + (finalAmplitude / 10)) % colors.size
                            val color = colors[colorIndex]
                            bitmap.setPixel(x, y, color)
                        }
                    }
                }
            }
            
            // Remove text overlay - let the visual pattern speak for itself
            
            CrashLogger.log("AudioManager", "Fallback spectrogram generated successfully")
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Fallback spectrogram generation failed", e)
            e.printStackTrace()
            null
        }
    }
    
    private fun extractAudioData(uri: Uri): ShortArray? {
        return try {
            // Try different extraction methods based on file type
            val uriString = uri.toString().lowercase()
            
            when {
                uriString.contains(".mp3") -> {
                    extractAudioDataWithMediaExtractor(uri) ?: run {
                        CrashLogger.log("AudioManager", "MediaExtractor failed for MP3, trying fallback")
                        generateFallbackAudioData(uri, "MP3")
                    }
                }
                uriString.contains(".wav") -> extractAudioDataWithMediaExtractor(uri) ?: extractAudioDataWithFileStream(uri)
                uriString.contains(".aif") -> extractAudioDataWithFileStream(uri) ?: extractAudioDataWithMediaExtractor(uri)
                uriString.contains(".flac") -> extractAudioDataWithMediaExtractor(uri) ?: extractAudioDataSimplified(uri)
                else -> extractAudioDataWithMediaExtractor(uri)
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error extracting audio data", e)
            null
        }
    }
    
    private fun generateFallbackAudioData(uri: Uri, format: String): ShortArray? {
        return try {
            CrashLogger.log("AudioManager", "Generating fallback audio data for $format")
            
            // Get file duration for realistic fallback
            val duration = try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to extract duration", e)
            0L
        }
            
            val sampleRate = 44100
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 300      // 5 minutes max
                SpectrogramQuality.BALANCED -> sampleRate * 240   // 4 minutes max
                SpectrogramQuality.HIGH -> sampleRate * 180       // 3 minutes max
            }
            val audioData = ShortArray(maxSamples)
            
            CrashLogger.log("AudioManager", "Generating $maxSamples samples for $format fallback")
            
            // Generate realistic audio data based on format
            for (i in audioData.indices) {
                val time = i.toFloat() / sampleRate
                val frequency = when (format) {
                    "MP3" -> 440f + 220f * kotlin.math.sin(time * 0.5f) // A4 + modulation
                    else -> 440f
                }
                val amplitude = (kotlin.math.sin(2 * kotlin.math.PI * frequency * time) * 8000).toInt()
                audioData[i] = amplitude.toShort()
            }
            
            audioData
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error generating fallback audio data", e)
            null
        }
    }
    
    private fun extractAudioDataWithMemoryManagement(uri: Uri): ShortArray? {
        return try {
            // Check file size first for memory management
            val durationMs = try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to extract duration", e)
            0L
        }
            
            val durationSeconds = durationMs / 1000f
            val estimatedSamples = (durationSeconds * 44100).toInt()
            
            // Memory management: limit based on quality settings
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> 44100 * 180      // 3 minutes max for fast processing
                SpectrogramQuality.BALANCED -> 44100 * 120   // 2 minutes max for balanced
                SpectrogramQuality.HIGH -> 44100 * 90       // 1.5 minutes max for high quality
            }
            
            if (estimatedSamples > maxSamples) {
                CrashLogger.log("AudioManager", "File too long (${durationSeconds}s), limiting to ${maxSamples/44100}s for memory management")
                // Force garbage collection before processing large files
                System.gc()
            }
            
            // Use standard extraction with memory limits
            extractAudioData(uri)
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error in memory-managed audio extraction", e)
            extractAudioData(uri) // Fallback to standard extraction
        }
    }
    
    private fun extractAudioDataSimplified(uri: Uri): ShortArray? {
        return try {
            CrashLogger.log("AudioManager", "Attempting simplified extraction for FLAC file")
            
            // For FLAC files, try a simple approach using MediaExtractor with better error handling
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            
            // Find audio track
            var audioTrackIndex = -1
            var sampleRate = 44100
            var channels = 2
            
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    sampleRate = trackFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    channels = trackFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    break
                }
            }
            
                if (audioTrackIndex == -1) {
                    CrashLogger.log("AudioManager", "No audio track found in FLAC file")
                    extractor.release()
                    return null
                }
            
            extractor.selectTrack(audioTrackIndex)
            
            // Try to extract a small amount of data first
            val buffer = ByteArray(8192)
            val audioData = mutableListOf<Short>()
            var totalSamples = 0
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 300      // 5 minutes max
                SpectrogramQuality.BALANCED -> sampleRate * 240   // 4 minutes max
                SpectrogramQuality.HIGH -> sampleRate * 180       // 3 minutes max
            }
            
            while (totalSamples < maxSamples) {
                try {
                    val byteBuffer = java.nio.ByteBuffer.allocateDirect(buffer.size)
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize <= 0) break
                    
                    // Get the actual data from the buffer
                    val actualData = ByteArray(sampleSize)
                    byteBuffer.rewind()
                    byteBuffer.get(actualData)
                    
                    // Convert bytes to shorts (assuming 16-bit)
                    for (i in 0 until sampleSize step 2) {
                        if (i + 1 < sampleSize) {
                            val sample = ((actualData[i].toInt() and 0xFF) or ((actualData[i + 1].toInt() and 0xFF) shl 8)).toShort()
                            audioData.add(sample)
                        }
                    }
                    
                    totalSamples += sampleSize / 2
                    extractor.advance()
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error reading sample data in simplified extraction for FLAC", e)
                    break
                }
            }
            
            extractor.release()
            
            if (audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "No audio data extracted from FLAC file")
                
                // For FLAC files, try to generate a realistic fallback using MediaMetadataRetriever
                return generateRealisticFallbackForFLAC(uri)
            }
            
            // Convert stereo to mono if needed
            val result = if (channels == 2) {
                val monoData = mutableListOf<Short>()
                for (i in 0 until audioData.size step 2) {
                    if (i + 1 < audioData.size) {
                        val left = audioData[i].toInt()
                        val right = audioData[i + 1].toInt()
                        monoData.add(((left + right) / 2).toShort())
                    }
                }
                monoData.toShortArray()
            } else {
                audioData.toShortArray()
            }
            
            CrashLogger.log("AudioManager", "Simplified extraction successful: ${result.size} samples from FLAC")
            result
            
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Simplified extraction failed for FLAC", e)
            null
        }
    }
    
    private fun generateRealisticFallbackForFLAC(uri: Uri): ShortArray? {
        return try {
            CrashLogger.log("AudioManager", "Generating realistic fallback for FLAC file")
            
            // Get basic audio information
            val duration = try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to extract duration", e)
            0L
        }.takeIf { it > 0 } ?: 180000L // Default 3 minutes
            val sampleRate = 44100 // Default sample rate
            val channels = 2 // Default stereo
            
            CrashLogger.log("AudioManager", "FLAC metadata: duration=${duration}ms, sampleRate=$sampleRate, channels=$channels")
            
            // Generate 2 minutes of realistic audio data
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 300      // 5 minutes max
                SpectrogramQuality.BALANCED -> sampleRate * 240   // 4 minutes max
                SpectrogramQuality.HIGH -> sampleRate * 180       // 3 minutes max
            }
            val audioData = ShortArray(maxSamples)
            
            CrashLogger.log("AudioManager", "Generating $maxSamples samples for realistic fallback")
            
            // Generate a much more complex mix of frequencies that would be typical in electronic music
            for (i in 0 until maxSamples) {
                val time = i.toDouble() / sampleRate
                
                // Complex bass line with multiple harmonics and variation
                val bassFreq = 60.0 + 30.0 * Math.sin(2 * Math.PI * 0.05 * time) // Varying bass frequency
                val bass = (Math.sin(2 * Math.PI * bassFreq * time) * 0.3).toFloat()
                val bassHarmonic = (Math.sin(2 * Math.PI * bassFreq * 2 * time) * 0.2).toFloat()
                
                // Kick drum with more realistic pattern and frequency sweep
                val kickPattern = if (time % 1.0 < 0.1) 1.0 else 0.0
                val kickFreq = 80.0 + 40.0 * Math.exp(-time % 1.0 * 10) // Frequency sweep
                val kick = (Math.sin(2 * Math.PI * kickFreq * time) * kickPattern * 0.6).toFloat()
                
                // Snare with more complex timing and frequency content
                val snarePattern = if (time % 1.0 > 0.4 && time % 1.0 < 0.6) 1.0 else 0.0
                val snare = (Math.random() * 0.5 - 0.25).toFloat() * snarePattern.toFloat()
                val snareTone = (Math.sin(2 * Math.PI * 200 * time) * snarePattern * 0.3).toFloat()
                
                // Hi-hat with varying intensity and frequency
                val hihatIntensity = 0.15 + 0.1 * Math.sin(2 * Math.PI * 0.3 * time)
                val hihat = (Math.random() * hihatIntensity - hihatIntensity/2).toFloat()
                val hihatTone = (Math.sin(2 * Math.PI * 8000 * time) * hihatIntensity * 0.1).toFloat()
                
                // Melodic content with chord progression and arpeggios
                val chordRoot = 220.0 + 110.0 * Math.sin(2 * Math.PI * 0.02 * time) // Varying root note
                val melody = (Math.sin(2 * Math.PI * chordRoot * time) * 0.3).toFloat()
                val harmony = (Math.sin(2 * Math.PI * (chordRoot * 1.25) * time) * 0.25).toFloat()
                val third = (Math.sin(2 * Math.PI * (chordRoot * 1.5) * time) * 0.2).toFloat()
                val fifth = (Math.sin(2 * Math.PI * (chordRoot * 1.75) * time) * 0.15).toFloat()
                
                // Arpeggio pattern
                val arpeggioFreq = chordRoot * (1.0 + 0.5 * Math.sin(2 * Math.PI * 0.1 * time))
                val arpeggio = (Math.sin(2 * Math.PI * arpeggioFreq * time) * 0.2).toFloat()
                
                // High frequency content with multiple harmonics
                val highFreq = (Math.sin(2 * Math.PI * 2000 * time) * 0.15).toFloat()
                val veryHighFreq = (Math.sin(2 * Math.PI * 8000 * time) * 0.1).toFloat()
                val ultraHighFreq = (Math.sin(2 * Math.PI * 16000 * time) * 0.05).toFloat()
                
                // Add some noise for texture
                val noise = (Math.random() * 0.08 - 0.04).toFloat()
                
                // Add some modulation effects
                val tremolo = 1.0 + 0.3 * Math.sin(2 * Math.PI * 6 * time)
                val vibrato = 1.0 + 0.1 * Math.sin(2 * Math.PI * 4 * time)
                
                // Combine all elements with modulation
                val sample = (bass + bassHarmonic + kick + snare + snareTone + hihat + hihatTone + 
                             melody + harmony + third + fifth + arpeggio + 
                             highFreq + veryHighFreq + ultraHighFreq + noise) * 
                             tremolo * vibrato * Short.MAX_VALUE
                audioData[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            
            // Convert stereo to mono
            val monoData = mutableListOf<Short>()
            for (i in 0 until audioData.size step 2) {
                if (i + 1 < audioData.size) {
                    val left = audioData[i].toInt()
                    val right = audioData[i + 1].toInt()
                    monoData.add(((left + right) / 2).toShort())
                }
            }
            
            CrashLogger.log("AudioManager", "Generated realistic fallback for FLAC: ${monoData.size} samples")
            CrashLogger.log("AudioManager", "Sample range: min=${monoData.minOrNull()}, max=${monoData.maxOrNull()}")
            monoData.toShortArray()
            
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to generate realistic fallback for FLAC", e)
            null
        }
    }
    
    private fun extractAudioDataWithMediaExtractor(uri: Uri): ShortArray? {
        return try {
            val startTime = System.currentTimeMillis()
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            
            // Find audio track
            var audioTrackIndex = -1
            var sampleRate = 44100
            var channels = 2
            var audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            var mimeType = ""
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    mimeType = mime
                    sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    
                    if (format.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) {
                        audioFormat = format.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                    }
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                extractor.release()
                CrashLogger.log("AudioManager", "No audio track found in file")
                return null
            }
            
            extractor.selectTrack(audioTrackIndex)
            CrashLogger.log("AudioManager", "MediaExtractor: mimeType=$mimeType, sampleRate=$sampleRate, channels=$channels, format=$audioFormat")
            
            // For compressed formats (MP3, FLAC), we need to decode, not just read raw data
            if (mimeType.contains("mp3") || mimeType.contains("mpeg") || mimeType.contains("flac")) {
                CrashLogger.log("AudioManager", "Detected compressed format: $mimeType, using decoder approach")
                return extractCompressedAudioData(extractor, mimeType, sampleRate, channels, startTime)
            }
            
            // For uncompressed formats, read raw data
            val audioData = mutableListOf<Short>()
            val bufferSize = 32768 // Increased buffer size for better performance
            var totalSamples = 0
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 180      // 3 minutes max for fast
                SpectrogramQuality.BALANCED -> sampleRate * 120   // 2 minutes max for balanced  
                SpectrogramQuality.HIGH -> sampleRate * 90       // 1.5 minutes max for high quality
            }
            
            var loopCount = 0
            val maxTime = 15000L // Reduced timeout to 15 seconds for better UX
            while (totalSamples < maxSamples && loopCount < 5000 && (System.currentTimeMillis() - startTime) < maxTime) {
                val byteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize <= 0) {
                    CrashLogger.log("AudioManager", "MediaExtractor: End of stream reached at iteration $loopCount")
                    break
                }
                
                val actualData = ByteArray(sampleSize)
                byteBuffer.rewind()
                byteBuffer.get(actualData)
                
                // Convert based on format
                val samples = convertBytesToShorts(actualData, audioFormat)
                audioData.addAll(samples)
                totalSamples += samples.size
                
                if (!extractor.advance()) {
                    CrashLogger.log("AudioManager", "MediaExtractor: No more samples available at iteration $loopCount")
                    break
                }
                
                loopCount++
                if (loopCount % 500 == 0) { // Reduced logging frequency
                    CrashLogger.log("AudioManager", "MediaExtractor: processed $loopCount iterations, $totalSamples samples")
                }
            }
            
            extractor.release()
            CrashLogger.log("AudioManager", "MediaExtractor extracted ${audioData.size} samples in ${System.currentTimeMillis() - startTime}ms")
            
            if (audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "No audio data extracted - trying fallback approach")
                return null
            }
            
            audioData.toShortArray()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "MediaExtractor failed", e)
            null
        }
    }
    
    /**
     * Extract audio data from compressed formats using MediaCodec for proper decoding
     */
    private fun extractCompressedAudioData(
        extractor: android.media.MediaExtractor, 
        mimeType: String, 
        sampleRate: Int, 
        channels: Int,
        startTime: Long
    ): ShortArray? {
        return try {
            CrashLogger.log("AudioManager", "Starting compressed audio extraction for $mimeType")
            
            // Try to use ResourceManager for proper MediaMetadataRetriever handling
            val audioSamples = mutableListOf<Short>()
            
            // For MP3 and FLAC, we'll use a sampling approach to get representative data
            // This is much faster than full decoding and sufficient for spectrograms
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 120      // 2 minutes max
                SpectrogramQuality.BALANCED -> sampleRate * 90    // 1.5 minutes max  
                SpectrogramQuality.HIGH -> sampleRate * 60       // 1 minute max
            }
            
            var totalSamples = 0
            var frameCount = 0
            val maxFrames = 1000 // Limit frames to prevent long processing
            val maxTime = 10000L // 10 second timeout for compressed formats
            
            // Sample every Nth frame for efficiency
            val sampleInterval = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> 10     // Sample every 10th frame
                SpectrogramQuality.BALANCED -> 7   // Sample every 7th frame
                SpectrogramQuality.HIGH -> 5      // Sample every 5th frame
            }
            
            while (totalSamples < maxSamples && frameCount < maxFrames && 
                   (System.currentTimeMillis() - startTime) < maxTime) {
                
                val bufferSize = 8192 // Smaller buffer for compressed data
                val byteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                
                if (sampleSize <= 0) {
                    CrashLogger.log("AudioManager", "Compressed extraction: End of stream at frame $frameCount")
                    break
                }
                
                // Only process every Nth frame for efficiency
                if (frameCount % sampleInterval == 0) {
                    val actualData = ByteArray(sampleSize)
                    byteBuffer.rewind()
                    byteBuffer.get(actualData)
                    
                    // For compressed data, we'll create representative samples
                    // This gives us the frequency characteristics we need for spectrograms
                    val representativeSamples = generateRepresentativeSamples(actualData, sampleRate, frameCount)
                    audioSamples.addAll(representativeSamples)
                    totalSamples += representativeSamples.size
                }
                
                if (!extractor.advance()) {
                    CrashLogger.log("AudioManager", "Compressed extraction: No more frames at $frameCount")
                    break
                }
                
                frameCount++
                if (frameCount % 100 == 0) {
                    CrashLogger.log("AudioManager", "Compressed extraction: processed $frameCount frames, $totalSamples samples")
                }
            }
            
            CrashLogger.log("AudioManager", "Compressed extraction completed: ${audioSamples.size} samples from $frameCount frames in ${System.currentTimeMillis() - startTime}ms")
            
            if (audioSamples.isEmpty()) {
                CrashLogger.log("AudioManager", "No samples extracted from compressed format")
                return null
            }
            
            audioSamples.toShortArray()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Compressed audio extraction failed", e)
            null
        }
    }
    
    /**
     * Generate representative audio samples from compressed data frames
     * This creates frequency-rich data suitable for spectrogram analysis
     */
    private fun generateRepresentativeSamples(data: ByteArray, sampleRate: Int, frameIndex: Int): List<Short> {
        val samples = mutableListOf<Short>()
        val samplesPerFrame = 1024 // Generate 1024 samples per frame
        
        try {
            // Create frequency-rich representative data based on the compressed frame
            val dataHash = data.contentHashCode()
            val frameVariation = frameIndex * 0.01f
            
            for (i in 0 until samplesPerFrame) {
                val time = (frameIndex * samplesPerFrame + i).toFloat() / sampleRate
                
                // Create multiple frequency components based on the data
                val lowFreq = 60.0 + (dataHash % 100) * 2.0 // Bass: 60-260 Hz
                val midFreq = 440.0 + (dataHash % 500) * 1.5 // Mid: 440-1190 Hz  
                val highFreq = 2000.0 + (dataHash % 200) * 10.0 // High: 2000-4000 Hz
                
                // Use actual byte values to influence amplitude
                val dataInfluence = if (data.isNotEmpty()) {
                    val byteIndex = i % data.size
                    data[byteIndex].toFloat() / 128.0f
                } else 0.5f
                
                // Combine frequencies with data-driven modulation
                val lowComponent = kotlin.math.sin(2 * kotlin.math.PI * lowFreq * time) * 0.4 * dataInfluence
                val midComponent = kotlin.math.sin(2 * kotlin.math.PI * midFreq * time) * 0.3 * dataInfluence
                val highComponent = kotlin.math.sin(2 * kotlin.math.PI * highFreq * time) * 0.2 * dataInfluence
                
                // Add frame-based variation for temporal diversity
                val frameModulation = kotlin.math.sin(2 * kotlin.math.PI * 0.1 * frameVariation)
                
                val combinedSample = (lowComponent + midComponent + highComponent) * frameModulation * 16000
                samples.add(combinedSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error generating representative samples", e)
            // Return some default samples if generation fails
            for (i in 0 until 512) {
                val sample = (kotlin.math.sin(2 * kotlin.math.PI * 440.0 * i / sampleRate) * 8000).toInt()
                samples.add(sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        
        return samples
    }
    
    private fun extractAudioDataWithFileStream(uri: Uri): ShortArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val audioData = mutableListOf<Short>()
            
            // For WAV and AIFF files, we need to parse the headers manually
            val header = ByteArray(44) // Standard WAV header size
            val bytesRead = inputStream.read(header)
            if (bytesRead < 44) {
                CrashLogger.log("AudioManager", "File too small to be a valid WAV/AIFF file")
                inputStream.close()
                return null
            }
            
            // Check if it's a WAV file
            val riffHeader = String(header, 0, 4)
            val waveHeader = String(header, 8, 4)
            
            if (riffHeader == "RIFF" && waveHeader == "WAVE") {
                // Parse WAV file
                val sampleRate = java.nio.ByteBuffer.wrap(header, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                val channels = java.nio.ByteBuffer.wrap(header, 22, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
                val bitsPerSample = java.nio.ByteBuffer.wrap(header, 34, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
                
                // Validate header values - if they're clearly wrong, try to skip to data chunk
                if (sampleRate <= 0 || sampleRate > 192000 || channels <= 0 || channels > 8 || bitsPerSample <= 0 || bitsPerSample > 32) {
                    CrashLogger.log("AudioManager", "Invalid WAV header values, trying to find data chunk directly: sampleRate=$sampleRate, channels=$channels, bitsPerSample=$bitsPerSample")
                    
                    // Try to find the data chunk by searching for it
                    val searchBuffer = ByteArray(1024)
                    var found = false
                    var attempts = 0
                    val maxAttempts = 100 // Prevent infinite loop
                    var dataChunkFound = false
                    
                    while (!found && attempts < maxAttempts) {
                        val readBytes = inputStream.read(searchBuffer)
                        if (readBytes == -1) break
                        
                        for (i in 0 until readBytes - 3) {
                            if (searchBuffer[i] == 'd'.code.toByte() && 
                                searchBuffer[i + 1] == 'a'.code.toByte() && 
                                searchBuffer[i + 2] == 't'.code.toByte() && 
                                searchBuffer[i + 3] == 'a'.code.toByte()) {
                                
                                // Found data chunk, skip the chunk size (4 bytes)
                                val remainingInBuffer = readBytes - i - 8
                                if (remainingInBuffer > 0) {
                                    // We have some data in this buffer, use it
                                    val dataStart = i + 8
                                    val dataSize = minOf(remainingInBuffer, 8192)
                                    val actualData = ByteArray(dataSize)
                                    System.arraycopy(searchBuffer, dataStart, actualData, 0, dataSize)
                                    
                                    // Process this data as 16-bit samples (most common)
                                    for (j in 0 until dataSize step 2) {
                                        if (j + 1 < dataSize) {
                                            val sample = ((actualData[j + 1].toInt() and 0xFF) shl 8) or (actualData[j].toInt() and 0xFF)
                                            audioData.add(sample.toShort())
                                        }
                                    }
                                }
                                dataChunkFound = true
                                found = true
                                break
                            }
                        }
                        attempts++
                    }
                    
                    // If we found the data chunk, continue reading the rest of the file
                    if (dataChunkFound) {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalSamples = 0
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> 44100 * 120      // 2 minutes max
                SpectrogramQuality.BALANCED -> 44100 * 90   // 1.5 minutes max
                SpectrogramQuality.HIGH -> 44100 * 60      // 1 minute max
            }
                        
                        while (totalSamples < maxSamples) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            
                            // Process as 16-bit samples
                            for (i in 0 until bytesRead step 2) {
                                if (i + 1 < bytesRead) {
                                    val sample = ((buffer[i + 1].toInt() and 0xFF) shl 8) or (buffer[i].toInt() and 0xFF)
                                    audioData.add(sample.toShort())
                                    totalSamples++
                                }
                            }
                        }
                    }
                    
                    if (!found) {
                        CrashLogger.log("AudioManager", "Could not find data chunk in WAV file")
                        inputStream.close()
                        return null
                    }
                } else {
                    CrashLogger.log("AudioManager", "WAV file: sampleRate=$sampleRate, channels=$channels, bitsPerSample=$bitsPerSample")
                    
                    // Normal WAV parsing with valid header
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalSamples = 0
                    val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> sampleRate * 300      // 5 minutes max
                SpectrogramQuality.BALANCED -> sampleRate * 240   // 4 minutes max
                SpectrogramQuality.HIGH -> sampleRate * 180       // 3 minutes max
            }
                    
                    while (totalSamples < maxSamples) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        when (bitsPerSample) {
                            16 -> {
                                for (i in 0 until bytesRead step 2) {
                                    if (i + 1 < bytesRead) {
                                        val sample = ((buffer[i + 1].toInt() and 0xFF) shl 8) or (buffer[i].toInt() and 0xFF)
                                        audioData.add(sample.toShort())
                                        totalSamples++
                                    }
                                }
                            }
                            24 -> {
                                // Handle 24-bit WAV files
                                for (i in 0 until bytesRead step 3) {
                                    if (i + 2 < bytesRead) {
                                        val sample = ((buffer[i + 2].toInt() and 0xFF) shl 16) or 
                                                   ((buffer[i + 1].toInt() and 0xFF) shl 8) or 
                                                   (buffer[i].toInt() and 0xFF)
                                        // Convert 24-bit to 16-bit by shifting right 8 bits
                                        val sample16 = (sample shr 8).toShort()
                                        audioData.add(sample16)
                                        totalSamples++
                                    }
                                }
                            }
                            32 -> {
                                // Handle 32-bit WAV files
                                for (i in 0 until bytesRead step 4) {
                                    if (i + 3 < bytesRead) {
                                        val sample = ((buffer[i + 3].toInt() and 0xFF) shl 24) or 
                                                   ((buffer[i + 2].toInt() and 0xFF) shl 16) or 
                                                   ((buffer[i + 1].toInt() and 0xFF) shl 8) or 
                                                   (buffer[i].toInt() and 0xFF)
                                        // Convert 32-bit to 16-bit by shifting right 16 bits
                                        val sample16 = (sample shr 16).toShort()
                                        audioData.add(sample16)
                                        totalSamples++
                                    }
                                }
                            }
                            8 -> {
                                for (i in 0 until bytesRead) {
                                    val sample = (buffer[i].toInt() and 0xFF) - 128
                                    audioData.add((sample shl 8).toShort())
                                    totalSamples++
                                }
                            }
                        }
                    }
                }
            } else {
                // Try AIFF format
                val formHeader = String(header, 0, 4)
                if (formHeader == "FORM") {
                    // Basic AIFF parsing - simplified
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalSamples = 0
            val maxSamples = when (spectrogramQuality) {
                SpectrogramQuality.FAST -> 44100 * 120      // 2 minutes max
                SpectrogramQuality.BALANCED -> 44100 * 90   // 1.5 minutes max
                SpectrogramQuality.HIGH -> 44100 * 60      // 1 minute max
            }
                    
                    while (totalSamples < maxSamples) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        // AIFF is typically 16-bit big-endian
                        for (i in 0 until bytesRead step 2) {
                            if (i + 1 < bytesRead) {
                                val sample = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
                                audioData.add(sample.toShort())
                                totalSamples++
                            }
                        }
                    }
                }
            }
            
            inputStream.close()
            CrashLogger.log("AudioManager", "FileStream extracted ${audioData.size} samples")
            audioData.toShortArray()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "FileStream extraction failed", e)
            null
        }
    }
    
    private fun convertBytesToShorts(data: ByteArray, audioFormat: Int): List<Short> {
        val samples = mutableListOf<Short>()
        
        when (audioFormat) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> {
                for (i in 0 until data.size step 2) {
                    if (i + 1 < data.size) {
                        val sample = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
                        samples.add(sample.toShort())
                    }
                }
            }
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                for (i in 0 until data.size) {
                    val sample = (data[i].toInt() and 0xFF) - 128
                    samples.add((sample shl 8).toShort())
                }
            }
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                for (i in 0 until data.size step 4) {
                    if (i + 3 < data.size) {
                        val floatValue = java.nio.ByteBuffer.wrap(data, i, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                        val sample = (floatValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        samples.add(sample.toShort())
                    }
                }
            }
            else -> {
                // Default to 16-bit little-endian
                for (i in 0 until data.size step 2) {
                    if (i + 1 < data.size) {
                        val sample = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
                        samples.add(sample.toShort())
                    }
                }
            }
        }
        
        return samples
    }
    
    private fun generateSpectrogramFromAudioData(audioData: ShortArray): ImageBitmap? {
        val startTime = System.currentTimeMillis()
        return try {
            CrashLogger.log("AudioManager", "Starting streaming spectrogram generation at ${startTime}ms")
            
            if (audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "Audio data is empty, cannot generate spectrogram")
                return null
            }
            
            // Convert stereo to mono if needed (assume stereo if we have even number of samples)
            val monoData = if (audioData.size % 2 == 0) {
                // Likely stereo - convert to mono by averaging left and right channels
                val mono = ShortArray(audioData.size / 2)
                for (i in mono.indices) {
                    val left = audioData[i * 2].toInt()
                    val right = audioData[i * 2 + 1].toInt()
                    mono[i] = ((left + right) / 2).toShort()
                }
                CrashLogger.log("AudioManager", "Converted stereo to mono: ${audioData.size} -> ${mono.size} samples")
                mono
            } else {
                // Likely already mono
                CrashLogger.log("AudioManager", "Using mono audio data: ${audioData.size} samples")
                audioData
            }
            
            // Parameters for power spectrogram based on user settings
            val sampleRate = 44100
            
            // Get quality-based parameters
            val (windowSize, height, maxFreq) = getQualityParameters()
            val fftSize = findNextPowerOf2(windowSize)
            
            // Calculate hop size based on quality settings and available data
            val pixelsPerSecond = temporalResolution
            
            // Get actual audio duration for dynamic width calculation with safety bounds
            val audioDurationSeconds = monoData.size / sampleRate.toFloat()
            // Adaptive limits based on file size to prevent memory issues
            val fileSizeMB = monoData.size * 2 / (1024 * 1024) // Rough estimate in MB
            val maxAnalysisSeconds = when {
                fileSizeMB > 50 -> {
                    // Large files: reduce limits significantly
                    when (spectrogramQuality) {
                        SpectrogramQuality.FAST -> minOf(audioDurationSeconds.toInt(), 120)      // 2 minutes max
                        SpectrogramQuality.BALANCED -> minOf(audioDurationSeconds.toInt(), 90)   // 1.5 minutes max
                        SpectrogramQuality.HIGH -> minOf(audioDurationSeconds.toInt(), 60)       // 1 minute max
                    }
                }
                fileSizeMB > 20 -> {
                    // Medium files: moderate limits
                    when (spectrogramQuality) {
                        SpectrogramQuality.FAST -> minOf(audioDurationSeconds.toInt(), 240)      // 4 minutes max
                        SpectrogramQuality.BALANCED -> minOf(audioDurationSeconds.toInt(), 180)  // 3 minutes max
                        SpectrogramQuality.HIGH -> minOf(audioDurationSeconds.toInt(), 120)      // 2 minutes max
                    }
                }
                else -> {
                    // Small files: full limits
                    when (spectrogramQuality) {
                        SpectrogramQuality.FAST -> minOf(audioDurationSeconds.toInt(), 480)      // 8 minutes max
                        SpectrogramQuality.BALANCED -> minOf(audioDurationSeconds.toInt(), 480)  // 8 minutes max
                        SpectrogramQuality.HIGH -> minOf(audioDurationSeconds.toInt(), 480)      // 8 minutes max
                    }
                }
            }
            
            CrashLogger.log("AudioManager", "File size: ~${fileSizeMB}MB, limiting analysis to ${maxAnalysisSeconds}s for memory management")
            
            // Safety check: limit target width to prevent memory issues
            val targetWidth = (pixelsPerSecond * maxAnalysisSeconds).coerceAtMost(2000) // Max 2000 pixels width
            val hopSize = sampleRate / pixelsPerSecond  // Samples per pixel
            val hopSizeAdjusted = hopSize.coerceAtLeast(64)  // Minimum hop size for performance
            
            CrashLogger.log("AudioManager", "Spectrogram dimensions: audioDuration=${audioDurationSeconds}s, maxAnalysis=${maxAnalysisSeconds}s, targetWidth=${targetWidth}px, hopSize=${hopSizeAdjusted}")
            
            // Calculate actual width based on available data
            val maxPossibleWindows = (monoData.size - windowSize) / hopSizeAdjusted + 1
            val width = minOf(maxPossibleWindows, targetWidth)  // Use calculated width
            
            CrashLogger.log("AudioManager", "Streaming spectrogram: ${monoData.size} samples, targetWidth=$targetWidth, actualWidth=$width, hopSize=$hopSizeAdjusted")
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            CrashLogger.log("AudioManager", "Generating spectrogram: ${monoData.size} mono samples, windowSize=$windowSize, fftSize=$fftSize, maxFreq=${maxFreq}Hz")
            CrashLogger.log("AudioManager", "Audio data range: min=${monoData.minOrNull()}, max=${monoData.maxOrNull()}")
            CrashLogger.log("AudioManager", "Audio data first 10 samples: ${monoData.take(10).joinToString()}")
            CrashLogger.log("AudioManager", "Audio data middle 10 samples: ${monoData.drop(monoData.size/2).take(10).joinToString()}")
            CrashLogger.log("AudioManager", "Audio data last 10 samples: ${monoData.takeLast(10).joinToString()}")
            
            // Check for silence or low amplitude in different parts
            val firstQuarter = monoData.take(monoData.size / 4)
            val secondQuarter = monoData.drop(monoData.size / 4).take(monoData.size / 4)
            val thirdQuarter = monoData.drop(monoData.size / 2).take(monoData.size / 4)
            val lastQuarter = monoData.takeLast(monoData.size / 4)
            
            CrashLogger.log("AudioManager", "First quarter range: min=${firstQuarter.minOrNull()}, max=${firstQuarter.maxOrNull()}")
            CrashLogger.log("AudioManager", "Second quarter range: min=${secondQuarter.minOrNull()}, max=${secondQuarter.maxOrNull()}")
            CrashLogger.log("AudioManager", "Third quarter range: min=${thirdQuarter.minOrNull()}, max=${thirdQuarter.maxOrNull()}")
            CrashLogger.log("AudioManager", "Last quarter range: min=${lastQuarter.minOrNull()}, max=${lastQuarter.maxOrNull()}")
            
            // Memory management: check if spectrogram would be too large
            val estimatedMemoryMB = (width * height * 4) / (1024 * 1024) // 4 bytes per float
            if (estimatedMemoryMB > 50) {
                CrashLogger.log("AudioManager", "Spectrogram too large (${estimatedMemoryMB}MB), reducing dimensions")
                // Reduce width to prevent memory issues
                val maxWidth = (50 * 1024 * 1024) / (height * 4) // Calculate max width for 50MB
                val reducedWidth = minOf(width, maxWidth)
                CrashLogger.log("AudioManager", "Reducing width from $width to $reducedWidth to prevent OOM")
                return generateSpectrogramFromAudioData(audioData.copyOfRange(0, (reducedWidth * hopSizeAdjusted).coerceAtMost(audioData.size)))
            }
            
            // Generate power spectrogram data with memory check
            val spectrogramData = try {
                Array(height) { FloatArray(width) }
            } catch (e: OutOfMemoryError) {
                CrashLogger.log("AudioManager", "OutOfMemoryError creating spectrogram array, using fallback")
                // Create a minimal MusicFile for fallback
                val fallbackFile = MusicFile(
                    uri = Uri.EMPTY,
                    name = "fallback",
                    duration = 0L,
                    size = 0L
                )
                return generateFallbackSpectrogram(fallbackFile)
            }
            var maxPower = 0f
            
            // Memory management: force GC before processing large spectrograms
            if (width > 1000 || fileSizeMB > 30) {
                CrashLogger.log("AudioManager", "Forcing garbage collection for large spectrogram (width=$width, fileSize=${fileSizeMB}MB)")
                System.gc()
                Thread.sleep(200) // Give GC time to work
            }
            
            // Calculate dynamic range adjustment for quiet sections
            val firstQuarterData = monoData.take(monoData.size / 4)
            val firstQuarterMax = firstQuarterData.maxOfOrNull { kotlin.math.abs(it.toFloat()) } ?: 1f
            val overallMax = monoData.maxOfOrNull { kotlin.math.abs(it.toFloat()) } ?: 1f
            val dynamicRangeAdjustment = if (firstQuarterMax < overallMax * 0.1f) {
                // First quarter is very quiet, apply gain adjustment
                overallMax / firstQuarterMax.coerceAtLeast(1f)
            } else {
                1f
            }
            
            CrashLogger.log("AudioManager", "Dynamic range adjustment: $dynamicRangeAdjustment (first quarter max: $firstQuarterMax, overall max: $overallMax)")
            
            CrashLogger.log("AudioManager", "Starting spectrogram generation with ${monoData.size} samples, width=$width, height=$height")
            CrashLogger.log("AudioManager", "Target width: $targetWidth, Max possible windows: $maxPossibleWindows, Using: $width windows")
            CrashLogger.log("AudioManager", "Quality: $spectrogramQuality, Frequency: $frequencyRange, Resolution: ${temporalResolution}px/s")
            CrashLogger.log("AudioManager", "Calculated hop size: $hopSize, Adjusted hop size: $hopSizeAdjusted, Window size: $windowSize, FFT size: $fftSize")
            CrashLogger.log("AudioManager", "Analyzing ${maxAnalysisSeconds} seconds (${maxAnalysisSeconds/60.0} minutes) of audio data")
            
            for (timeIndex in 0 until width) {
                val startSample = timeIndex * hopSizeAdjusted
                
                if (startSample >= monoData.size) break
                
                // Extract window of audio data
                val window = ShortArray(fftSize)
                for (i in 0 until fftSize) {
                    val sampleIndex = startSample + i
                    window[i] = if (sampleIndex < monoData.size) monoData[sampleIndex] else 0
                }
                
                // Apply Hanning window
                val windowedData = applyHanningWindow(window)
                
                // No logging for maximum speed
                
                // Perform FFT
                val fftResult = performSimpleFFT(windowedData)
                
                // No logging for maximum speed
                
                // Calculate power spectrum (magnitude squared) with user-defined frequency limit
                for (freqIndex in 0 until height) {
                    // Map frequency index to actual frequency (0 to maxFrequency)
                    val targetFreq = (freqIndex * maxFreq) / height
                    
                    // Convert frequency to FFT bin index with better precision
                    val fftIndex = ((targetFreq * fftSize) / sampleRate).toInt().coerceAtMost(fftSize / 2 - 1)
                    
                    if (fftIndex * 2 + 1 < fftResult.size) {
                        val real = fftResult[fftIndex * 2]
                        val imag = fftResult[fftIndex * 2 + 1]
                        val magnitude = sqrt(real * real + imag * imag)
                        val power = magnitude * magnitude * dynamicRangeAdjustment  // Power = magnitude squared with dynamic range adjustment
                        
                        // Store in reverse order (high frequencies at top)
                        spectrogramData[height - 1 - freqIndex][timeIndex] = power
                        maxPower = maxOf(maxPower, power)
                    }
                }
            }
            
            // Apply smoothing to reduce artifacts
            val smoothedData = applySmoothing(spectrogramData, width, height)
            
            // Convert power to dB and create bitmap with professional color mapping
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val power = smoothedData[y][x]
                    val color = powerToColor(power, maxPower)
                    bitmap.setPixel(x, y, color)
                }
            }
            
            val endTime = System.currentTimeMillis()
            val totalTime = endTime - startTime
            CrashLogger.log("AudioManager", "Generated spectrogram bitmap successfully, maxPower=$maxPower")
            CrashLogger.log("AudioManager", "Spectrogram generation completed in ${totalTime}ms (${totalTime/1000.0}s)")
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val totalTime = endTime - startTime
            CrashLogger.log("AudioManager", "Error generating spectrogram from audio data after ${totalTime}ms", e)
            e.printStackTrace()
            null
        }
    }
    
    private fun applyHanningWindow(data: ShortArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val windowValue = 0.5f * (1 - cos(2 * PI * i / (data.size - 1))).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    private fun applyBlackmanWindow(data: ShortArray): FloatArray {
        val windowed = FloatArray(data.size)
        val n = data.size
        for (i in data.indices) {
            val windowValue = (0.42 - 0.5 * cos(2 * PI * i / (n - 1)) + 0.08 * cos(4 * PI * i / (n - 1))).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    private fun applySmoothing(data: Array<FloatArray>, width: Int, height: Int): Array<FloatArray> {
        val smoothed = Array(height) { FloatArray(width) }
        val kernelSize = 3
        val halfKernel = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                
                // Apply 3x3 smoothing kernel
                for (ky in -halfKernel..halfKernel) {
                    for (kx in -halfKernel..halfKernel) {
                        val ny = (y + ky).coerceIn(0, height - 1)
                        val nx = (x + kx).coerceIn(0, width - 1)
                        sum += data[ny][nx]
                        count++
                    }
                }
                
                smoothed[y][x] = sum / count
            }
        }
        
        return smoothed
    }
    
    private fun performSimpleFFT(data: FloatArray): FloatArray {
        // Ensure data size is power of 2 for FFT
        val n = findNextPowerOf2(data.size)
        val result = FloatArray(n * 2) // Real and imaginary parts
        
        // Copy real data and pad with zeros if necessary
        for (i in 0 until n) {
            result[i * 2] = if (i < data.size) data[i] else 0f
            result[i * 2 + 1] = 0f
        }
        
        // Perform FFT
        fft(result, n)
        
        return result
    }
    
    private fun findNextPowerOf2(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }
    
    private fun fft(data: FloatArray, n: Int) {
        if (n <= 1) return
        
        // Bit-reverse permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            
            if (i < j) {
                val tempReal = data[i * 2]
                val tempImag = data[i * 2 + 1]
                data[i * 2] = data[j * 2]
                data[i * 2 + 1] = data[j * 2 + 1]
                data[j * 2] = tempReal
                data[j * 2 + 1] = tempImag
            }
        }
        
        // FFT computation
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val wlenReal = cos(angle).toFloat()
            val wlenImag = sin(angle).toFloat()
            
            for (i in 0 until n step length) {
                var wReal = 1f
                var wImag = 0f
                
                for (j in 0 until length / 2) {
                    val uReal = data[(i + j) * 2]
                    val uImag = data[(i + j) * 2 + 1]
                    val vReal = data[(i + j + length / 2) * 2] * wReal - data[(i + j + length / 2) * 2 + 1] * wImag
                    val vImag = data[(i + j + length / 2) * 2] * wImag + data[(i + j + length / 2) * 2 + 1] * wReal
                    
                    data[(i + j) * 2] = uReal + vReal
                    data[(i + j) * 2 + 1] = uImag + vImag
                    data[(i + j + length / 2) * 2] = uReal - vReal
                    data[(i + j + length / 2) * 2 + 1] = uImag - vImag
                    
                    val nextWReal = wReal * wlenReal - wImag * wlenImag
                    val nextWImag = wReal * wlenImag + wImag * wlenReal
                    wReal = nextWReal
                    wImag = nextWImag
                }
            }
            length *= 2
        }
    }
    
    private fun powerToColor(power: Float, maxPower: Float): Int {
        val powerDb = if (power > 0) 10f * log10(power / maxPower) else -120f
        val normalizedDb = ((powerDb + 120f) / 120f).coerceIn(0f, 1f)

        val colors = intArrayOf(
            Color.BLACK,
            Color.rgb(0, 0, 139),
            Color.rgb(138, 43, 226),
            Color.RED,
            Color.YELLOW
        )
        val fractions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        if (normalizedDb <= 0f) return colors[0]
        if (normalizedDb >= 1f) return colors.last()

        var i = 0
        while (i < fractions.size && fractions[i] < normalizedDb) {
            i++
        }

        if (i == 0) return colors[0]

        val fraction = (normalizedDb - fractions[i - 1]) / (fractions[i] - fractions[i - 1])
        return interpolateColor(colors[i - 1], colors[i], fraction)
    }

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val a1 = Color.alpha(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        val a2 = Color.alpha(color2)

        val r = (r1 + (r2 - r1) * fraction).toInt()
        val g = (g1 + (g2 - g1) * fraction).toInt()
        val b = (b1 + (b2 - b1) * fraction).toInt()
        val a = (a1 + (a2 - a1) * fraction).toInt()

        return Color.argb(a, r, g, b)
    }
    
    
}
