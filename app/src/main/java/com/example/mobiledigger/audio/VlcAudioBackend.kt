package com.example.mobiledigger.audio

import android.content.Context
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VLC Audio Backend for robust audio playback including AIFF support
 * Provides a stable alternative to FFmpegMediaPlayer for problematic files
 */
class VlcAudioBackend(private val context: Context) {
    
    private val verboseLogs = false
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val isInitialized = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isPrepared = AtomicBoolean(false)
    
    // Callbacks
    private var onPreparedListener: (() -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    init {
        initializeVLC()
    }
    
    private fun initializeVLC() {
        try {
            CrashLogger.log("VlcAudioBackend", "🔄 Initializing libVLC with minimal options...")
            
            // Minimal VLC options for maximum compatibility
            val options = arrayListOf(
                "--no-video",           // Disable video processing
                "-vvv"                  // Verbose logging for debugging
            )
            
            CrashLogger.log("VlcAudioBackend", "🔄 Creating LibVLC instance...")
            libVLC = LibVLC(context, options)
            CrashLogger.log("VlcAudioBackend", "✅ LibVLC instance created")
            
            CrashLogger.log("VlcAudioBackend", "🔄 Creating MediaPlayer...")
            mediaPlayer = MediaPlayer(libVLC)
            CrashLogger.log("VlcAudioBackend", "✅ MediaPlayer created")
            
            setupEventListeners()
            
            isInitialized.set(true)
            CrashLogger.log("VlcAudioBackend", "✅ libVLC initialized successfully")
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 Failed to initialize libVLC", e)
            isInitialized.set(false)
            // Clean up if initialization failed
            try {
                mediaPlayer?.release()
                mediaPlayer = null
                libVLC?.release()
                libVLC = null
            } catch (cleanupException: Exception) {
                CrashLogger.log("VlcAudioBackend", "💥 Failed to cleanup after initialization failure", cleanupException)
            }
        }
    }
    
    private fun setupEventListeners() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.MediaChanged -> {
                    CrashLogger.log("VlcAudioBackend", "📁 Media changed")
                }
                // MediaPlayer.Event.NothingSpecial -> {
                //     CrashLogger.log("VlcAudioBackend", "ℹ️ Nothing special")
                // }
                MediaPlayer.Event.Opening -> {
                    CrashLogger.log("VlcAudioBackend", "🔄 Opening media...")
                }
                MediaPlayer.Event.Buffering -> {
                    if (verboseLogs) {
                        val percent = event.buffering
                        CrashLogger.log("VlcAudioBackend", "📊 Buffering: $percent%")
                    }
                }
                MediaPlayer.Event.Playing -> {
                    isPlaying.set(true)
                    CrashLogger.log("VlcAudioBackend", "▶️ Playing")
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying.set(false)
                    CrashLogger.log("VlcAudioBackend", "⏸️ Paused")
                }
                MediaPlayer.Event.Stopped -> {
                    isPlaying.set(false)
                    CrashLogger.log("VlcAudioBackend", "⏹️ Stopped")
                }
                MediaPlayer.Event.EndReached -> {
                    isPlaying.set(false)
                    CrashLogger.log("VlcAudioBackend", "🏁 End reached")
                    onCompletionListener?.invoke()
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying.set(false)
                    isPrepared.set(false)
                    val error = "VLC playback error: ${event.type}"
                    CrashLogger.log("VlcAudioBackend", "💥 $error")
                    onErrorListener?.invoke(error)
                }
                MediaPlayer.Event.TimeChanged -> {
                    // Optional: Handle time changes for progress tracking
                }
                MediaPlayer.Event.PositionChanged -> {
                    // Optional: Handle position changes for seeking
                }
                MediaPlayer.Event.SeekableChanged -> {
                    CrashLogger.log("VlcAudioBackend", "🔍 Seekable changed")
                }
                MediaPlayer.Event.PausableChanged -> {
                    CrashLogger.log("VlcAudioBackend", "⏸️ Pausable changed")
                }
                MediaPlayer.Event.LengthChanged -> {
                    val length = event.lengthChanged
                    CrashLogger.log("VlcAudioBackend", "📏 Length changed: ${length}ms")
                }
                MediaPlayer.Event.Vout -> {
                    // Video output events (not relevant for audio-only)
                }
                MediaPlayer.Event.ESAdded -> {
                    CrashLogger.log("VlcAudioBackend", "🎵 Audio track added")
                }
                MediaPlayer.Event.ESDeleted -> {
                    CrashLogger.log("VlcAudioBackend", "🗑️ Audio track deleted")
                }
                MediaPlayer.Event.ESSelected -> {
                    CrashLogger.log("VlcAudioBackend", "✅ Audio track selected")
                }
                // Note: Some VLC event constants may not be available in all versions
                else -> {
                    CrashLogger.log("VlcAudioBackend", "❓ Unknown event: ${event.type}")
                }
            }
        }
    }
    
    fun setDataSource(uri: Uri): Boolean {
        return try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized")
                return false
            }
            
            CrashLogger.log("VlcAudioBackend", "🔄 Setting VLC data source: $uri")
            
            val media = Media(libVLC, uri)
            mediaPlayer?.media = media
            media.release() // Release the Media object after setting
            
            isPrepared.set(false)
            CrashLogger.log("VlcAudioBackend", "✅ VLC data source set successfully")
            true
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 Failed to set VLC data source", e)
            false
        }
    }
    
    fun prepareAsync() {
        try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized for prepare")
                return
            }
            
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🔄 Starting VLC prepare async...")
            
            // VLC doesn't have a separate prepare step - it prepares automatically
            // We'll simulate the prepared callback after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isPrepared.set(true)
                if (verboseLogs) CrashLogger.log("VlcAudioBackend", "✅ VLC prepared")
                onPreparedListener?.invoke()
            }, 100)
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC prepare failed", e)
            onErrorListener?.invoke("VLC prepare failed: ${e.message}")
        }
    }
    
    fun start() {
        try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized for start")
                return
            }
            
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "▶️ Starting VLC playback...")
            mediaPlayer?.play()
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC start failed", e)
            onErrorListener?.invoke("VLC start failed: ${e.message}")
        }
    }
    
    fun pause() {
        try {
            if (isPlaying.get()) {
                if (verboseLogs) CrashLogger.log("VlcAudioBackend", "⏸️ Pausing VLC playback...")
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC pause failed", e)
        }
    }
    
    fun stop() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "⏹️ Stopping VLC playback...")
            mediaPlayer?.stop()
            isPlaying.set(false)
            isPrepared.set(false)
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC stop failed", e)
        }
    }
    
    fun reset() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🔄 Resetting VLC player...")
            mediaPlayer?.stop()
            mediaPlayer?.media = null
            isPlaying.set(false)
            isPrepared.set(false)
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC reset failed", e)
        }
    }
    
    fun release() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🧹 Releasing VLC resources...")
            
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            libVLC?.release()
            libVLC = null
            
            isInitialized.set(false)
            isPlaying.set(false)
            isPrepared.set(false)
            
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "✅ VLC resources released")
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC release failed", e)
        }
    }
    
    fun isPlaying(): Boolean = isPlaying.get()
    
    fun isPrepared(): Boolean = isPrepared.get()
    
    fun isInitialized(): Boolean = isInitialized.get()
    
    // Callback setters
    fun setOnPreparedListener(listener: () -> Unit) {
        onPreparedListener = listener
    }
    
    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
    
    // Audio control
    fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0f, 1f)
            mediaPlayer?.volume = (clampedVolume * 100).toInt()
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🔊 VLC volume set to: ${(clampedVolume * 100).toInt()}%")
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC volume set failed", e)
        }
    }
    
    fun seekTo(positionMs: Long) {
        try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized for seek")
                return
            }
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "↔️ Seeking to ${positionMs}ms")
            mediaPlayer?.time = positionMs
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC seek failed", e)
            onErrorListener?.invoke("VLC seek failed: ${e.message}")
        }
    }

    fun getVolume(): Float {
        return try {
            val volume = mediaPlayer?.volume ?: 0
            (volume / 100f).coerceIn(0f, 1f)
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC volume get failed", e)
            0f
        }
    }
    
    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.time ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC getCurrentPosition failed", e)
            0L
        }
    }
    
    fun getDuration(): Long {
        return try {
            mediaPlayer?.length ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC getDuration failed", e)
            0L
        }
    }
}
