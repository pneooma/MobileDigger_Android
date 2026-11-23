package com.example.mobiledigger.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.graphics.Color
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
import com.example.mobiledigger.util.PerformanceProfiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.Collections
import java.io.File
import java.io.FileOutputStream

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
    
    // Feature flag for VLC backend
    companion object {
        private const val USE_VLC_FOR_AIFF = true // Enable VLC for AIFF files
        private const val USE_VLC_FOR_ALL = true // Enable VLC for all files (enabled)
        /** Seconds of audio analysed for the spectrogram (bitmap is 10 px/s). */
        const val SPECTROGRAM_MAX_SECONDS = 180
        /** Longest span "Whole track" will analyse (60 min). */
        const val SPECTROGRAM_LIMIT_SECONDS = 3600
        private const val SPECTROGRAM_DECODE_DEADLINE_MS = 120_000L
        private const val SPECTROGRAM_TIMEOUT_MS = 180_000L
    }
    private val verbosePreloadLogs = false
    
    private var exoPlayerFallback: ExoPlayer? = null
    private var vlcBackend: VlcAudioBackend? = null
    private var currentFile: MusicFile? = null
    private var isUsingVlc = false
    private var lastGcTimestampMs: Long = 0L

    // Partial wake lock so playback survives CPU sleep during background playback.
    // The real player lives in this (:main) process; previously nothing here held a
    // wake lock, so background audio could stop when the device dozed.
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = pm.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "MobileDigger:Playback"
                ).apply { setReferenceCounted(false) }
            }
            // Long safety timeout (3h) so a missed release can never pin the CPU forever.
            wakeLock?.takeIf { !it.isHeld }?.acquire(3 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "acquireWakeLock failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "releaseWakeLock failed", e)
        }
    }

    fun isVlcActive(): Boolean = isUsingVlc
    fun isRecentGc(windowMs: Long = 2000L): Boolean = (System.currentTimeMillis() - lastGcTimestampMs) < windowMs
    
    private fun runOnMainSync(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try {
                    action()
                } finally {
                    latch.countDown()
                }
            }
            try { latch.await() } catch (_: InterruptedException) {}
        }
    }
    private fun <T> callOnMainSync(call: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return call()
        }
        var result: Any? = null
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                result = call()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        try { latch.await() } catch (_: InterruptedException) {}
        if (error != null) throw error as Throwable
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
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
    private var currentTempWavPath: String? = null

    private val spectrogramDecoder by lazy {
        SpectrogramDecoder(context, SPECTROGRAM_MAX_SECONDS) // network drives come through DriveProxy (no auth needed)
    }
    private var currentVlcTempPath: String? = null

    /** Temp cache paths currently in use — must NOT be deleted by cache trimming. */
    fun getProtectedTempPaths(): Set<String> = setOfNotNull(currentTempWavPath, currentVlcTempPath)

    // User preferences for spectrogram quality
    // HIGH (512 bins, 8192-pt FFT) + temporalResolution 10 px/s ≈ double the prior
    // BALANCED/5 quality on both the frequency and time axes.
    private var spectrogramQuality: SpectrogramQuality = SpectrogramQuality.HIGH
    private var frequencyRange: FrequencyRange = FrequencyRange.EXTENDED
    private var temporalResolution: Int = 10 // pixels per second
    
    // Spectrogram cache with size limit to prevent memory leaks (EXTREMELY REDUCED for memory safety)
    private val maxCacheSize = 1 // CRITICAL: Limit to only 1 spectrogram to prevent memory exhaustion
    // Format/cutoff facts for each cached spectrogram (tiny; keyed like spectrogramCache).
    private val spectrogramInfoCache = Collections.synchronizedMap(HashMap<String, SpectrogramInfo>())
    @Volatile private var pendingInfo: SpectrogramInfo? = null
    @Volatile private var lastFftInfo: Pair<Int, SpectrumStats.Cutoff>? = null // (maxFreqHz, cutoff)

    private fun spectrogramCacheKey(musicFile: MusicFile) = "${musicFile.uri}_${musicFile.size}_q5"

    /** Real format + analysis facts for a spectrogram generated by [generateSpectrogram]. */
    fun getSpectrogramInfo(musicFile: MusicFile): SpectrogramInfo? = spectrogramInfoCache[spectrogramCacheKey(musicFile)]

    private val spectrogramCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(maxCacheSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                val shouldRemove = size > maxCacheSize
                if (shouldRemove) {
                    CrashLogger.log("AudioManager", "📊 Cache eviction: Removing eldest spectrogram (${size}/${maxCacheSize})")
                }
                return shouldRemove
            }
        }
    )
    
    private fun getQualityParameters(): Triple<Int, Int, Int> {
        val windowSize = when (spectrogramQuality) {
            SpectrogramQuality.FAST -> 1024
            SpectrogramQuality.BALANCED -> 4096
            SpectrogramQuality.HIGH -> 8192      // Increased for higher quality
        }
        
        val height = when (spectrogramQuality) {
            SpectrogramQuality.FAST -> 128
            SpectrogramQuality.BALANCED -> 256
            SpectrogramQuality.HIGH -> 768       // More frequency bins (cheap: FFT already computes them)
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
                    // CRITICAL: Don't delete a temp file that's currently in use by any backend
                    // (the AIFF temp WAV or the VLC temp) — that would kill playback.
                    if (eldest.value == currentVlcTempPath ||
                        eldest.value == currentTempWavPath) {
                        CrashLogger.log("AudioManager", "⚠️ Skipping deletion of in-use temp file: ${eldest.value}")
                        return false // Don't remove it from cache
                    }
                    
                    // Clean up the temp file when removing from cache
                    try {
                        val file = File(eldest.value)
                        val sizeMB = file.length() / (1024 * 1024)
                        file.delete()
                        CrashLogger.log("AudioManager", "📊 Cache cleanup: Removed temp file (${sizeMB}MB): ${file.name}")
                        // CRITICAL: Give OS time to release file descriptors after deletion
                        // removed sleep to avoid main-thread stalls
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
            
            // Initialize VLC backend (for AIFF and problematic files)
            if (USE_VLC_FOR_AIFF || USE_VLC_FOR_ALL) {
                CrashLogger.log("AudioManager", "🔄 Initializing VLC backend...")
                try {
                    val vlc = VlcAudioBackend(context)
                    if (vlc.isInitialized()) {
                        vlcBackend = vlc
                        setupVlcListeners()
                        CrashLogger.log("AudioManager", "✅ VLC backend initialized successfully")
                    } else {
                        CrashLogger.log("AudioManager", "💥 VLC backend initialization failed - isInitialized() returned false")
                        vlcBackend = null
                    }
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 Failed to initialize VLC backend", e)
                    vlcBackend = null
                }
            }
            
            // Initialize ExoPlayer (fallback) with better AIFF support
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

            // Float PCM output: 24/32-bit content reaches AudioTrack as float instead of
            // being truncated to 16-bit, and the float path bypasses ExoPlayer's audio
            // processors (no silence-skipping/speed DSP) — closest to bit-perfect the
            // Android mixer allows.
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setEnableAudioFloatOutput(true)

            exoPlayerFallback = ExoPlayer.Builder(context, renderersFactory)
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
            
            CrashLogger.log("AudioManager", "AudioManager initialized with VLC + ExoPlayer fallback")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "AudioManager initialization failed", e)
        }
    }
    
    private fun setupVlcListeners() {
        vlcBackend?.let { vlc ->
            vlc.setOnPreparedListener {
                CrashLogger.log("AudioManager", "✅ VLC prepared")
                // Start playback automatically when prepared
                vlc.start()
            }
            
            vlc.setOnCompletionListener {
                CrashLogger.log("AudioManager", "🏁 VLC playback completed")
                // Use the existing completion mechanism
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    playbackCompletionListener?.onTrackCompletion()
                }
            }
            
            vlc.setOnErrorListener { error ->
                CrashLogger.log("AudioManager", "💥 VLC playback error: $error")
                // Auto-skip to next track on error
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    playbackCompletionListener?.onTrackCompletion()
                }
            }
        }
    }
    
    fun preloadFile(musicFile: MusicFile) {
        if (musicFile == preloadedFile) {
            if (verbosePreloadLogs) CrashLogger.log("AudioManager", "File ${musicFile.name} already preloaded.")
            return
        }
        // Remote (Pi) files stream via VLC on demand — nothing useful to preload here.
        if (musicFile.uri.scheme == "http" || musicFile.uri.scheme == "https") return
        
        if (isUsingVlc) {
            if (verbosePreloadLogs) CrashLogger.log("AudioManager", "Skipping preload while VLC is active")
            return
        }
        // The preload reuses the SAME ExoPlayer that handles fallback playback; if it is
        // actively playing, preloading would reset it and kill the audible track mid-play.
        if (isCurrentlyPlaying()) {
            if (verbosePreloadLogs) CrashLogger.log("AudioManager", "Skipping preload while ExoPlayer is active")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastGcTimestampMs < 2000L) {
            if (verbosePreloadLogs) CrashLogger.log("AudioManager", "Skipping preload due to recent GC (${now - lastGcTimestampMs}ms ago)")
            return
        }
        val sizeBytes = musicFile.size
        if (sizeBytes > 80L * 1024L * 1024L) {
            if (verbosePreloadLogs) CrashLogger.log("AudioManager", "Skipping preload for large file (${sizeBytes / (1024 * 1024)}MB)")
            return
        }
        
        if (verbosePreloadLogs) CrashLogger.log("AudioManager", "Preloading file: ${musicFile.name}")
        
        // Stop any current preloading
        releasePreloadedPlayer()
        
        preloadedFile = musicFile
        
        val uri = musicFile.uri
        val fileName = musicFile.name.lowercase(Locale.getDefault())
        val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
        
        // AIFF is played by VLC, in place — nothing to preload (this used to copy the
        // whole file into the cache for an FFmpeg player that is no longer bundled).
        if (!isAiffFile) {
            // Preload with ExoPlayer (main thread — ExoPlayer is not thread-safe)
            exoPlayerFallback?.let { player ->
                try {
                    CrashLogger.log("AudioManager", "Preloading with ExoPlayer: ${musicFile.name}")
                    runOnMainSync {
                        player.stop()
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.playWhenReady = false // Don't start playing automatically
                    }
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error preloading with ExoPlayer", e)
                }
            }
        }
    }
    
    private fun releasePreloadedPlayer() {
        preloadedFile = null
        // No explicit release for ExoPlayer, just clear media items and stop playback
        runOnMainSync {
            exoPlayerFallback?.stop()
            exoPlayerFallback?.clearMediaItems()
        }
        CrashLogger.log("AudioManager", "Preloaded player released.")
    }
    
    fun playFile(musicFile: MusicFile): Boolean {
        return PerformanceProfiler.profile("AudioManager.playFile[${musicFile.name}]") {
            try {
                CrashLogger.log("AudioManager", "🎵 Starting playback for: ${musicFile.name}")
            
            currentFile = musicFile
            val uri = musicFile.uri
            
                // Validate URI before attempting playback
                if (uri.toString().isEmpty() || uri.toString() == "null") {
                    CrashLogger.log("AudioManager", "❌ Invalid or empty URI for file: ${musicFile.name}, URI: $uri")
                    return@profile false
                }
            
            CrashLogger.log("AudioManager", "📁 File URI: $uri")

            // Hold a wake lock for the duration of playback (released on pause/stop).
            acquireWakeLock()

            // CRITICAL: Always stop all playback first to ensure clean state
            CrashLogger.log("AudioManager", "🧹 Ensuring clean state before playback...")
            stopAllPlayback()
            
            // Additional delay to ensure cleanup is complete
            // removed sleep to avoid UI jank
            
            // CRITICAL: Force garbage collection every 5 tracks to prevent memory buildup
            // This helps prevent crashes after playing many files
            val trackCount = tempFileCache.size
            if (trackCount >= 3) {
                CrashLogger.log("AudioManager", "Running aggressive GC after $trackCount cached files")
                System.gc()
                lastGcTimestampMs = System.currentTimeMillis()
                // removed sleep to avoid UI jank
            }
            
            // AIFF files are validated first, then played by VLC. Remote http(s) files always
            // take the VLC-for-all path — the AIFF branch's local-file validation would
            // wrongly reject them (it opens the URI via ContentResolver).
            val isRemote = uri.scheme == "http" || uri.scheme == "https"
            val fileName = musicFile.name.lowercase(Locale.getDefault())
            val isAiffFile = !isRemote && (fileName.endsWith(".aif") || fileName.endsWith(".aiff"))
            
            if (isAiffFile) {
                CrashLogger.log("AudioManager", "🎵 AIFF file detected: ${musicFile.name}")
                
                // Check file size for large AIFF files
                val fileSizeMB = musicFile.size / (1024 * 1024)
                if (fileSizeMB > 100) {
                    CrashLogger.log("AudioManager", "Large AIFF file detected (${fileSizeMB}MB), using optimized playback")
                }
                
                CrashLogger.log("AudioManager", "🔄 AIFF file detected, validating before playback: ${musicFile.name}")
                
                // Validate the AIFF before playing it
                // This prevents crashes from corrupted or problematic AIFF files
                if (!validateAIFFFile(uri, musicFile)) {
                    CrashLogger.log("AudioManager", "❌ AIFF file validation failed, skipping: ${musicFile.name}")
                    return@profile false
                }
                
                CrashLogger.log("AudioManager", "✅ AIFF file validation passed, trying VLC first")
                
                // AIFF is played by VLC
                if (USE_VLC_FOR_AIFF && vlcBackend != null) {
                    try {
                        val vlcSuccess = tryPlayWithVlcSync(uri)
                        if (vlcSuccess) {
                            isUsingVlc = true
                            CrashLogger.log("AudioManager", "✅ VLC playback successful for AIFF: ${musicFile.name}")
                            return@profile true
                        } else {
                            CrashLogger.log("AudioManager", "❌ VLC failed for AIFF: ${musicFile.name}")
                        }
                    } catch (e: Exception) {
                        CrashLogger.log("AudioManager", "💥 Exception in VLC for AIFF: ${musicFile.name}", e)
                    }
                }
                
                // No other player handles AIFF (the FFmpeg fallback is no longer bundled)
                return@profile false
            }
            
            // If VLC-for-all is enabled, prefer VLC for all filetypes
            if (USE_VLC_FOR_ALL && vlcBackend != null) {
                try {
                    val vlcSuccessAll = tryPlayWithVlcSync(uri)
                    if (vlcSuccessAll) {
                        isUsingVlc = true
                        CrashLogger.log("AudioManager", "✅ VLC playback successful for: ${musicFile.name}")
                        try {
                            val intent = android.content.Intent(MusicService.ACTION_UPDATE_NOTIFICATION)
                                .putExtra(MusicService.EXTRA_TITLE, musicFile.name)
                                .putExtra(MusicService.EXTRA_IS_PLAYING, true)
                                .putExtra(MusicService.EXTRA_CURRENT_POSITION, getCurrentPosition())
                                .putExtra(MusicService.EXTRA_DURATION, getDuration())
                                .putExtra(MusicService.EXTRA_URI, uri.toString())
                            intent.`package` = context.packageName
                            context.sendBroadcast(intent)
                        } catch (_: Exception) {}
                        return@profile true
                    }
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 VLC playback failed for non-AIFF, falling back", e)
                }
            }

            // Use ExoPlayer as fallback (kept for non-AIFF and edge cases)
            val exoSuccess = tryPlayWithExoPlayerSync(uri)
            if (exoSuccess) {
                isUsingVlc = false
                CrashLogger.log("AudioManager", "ExoPlayer playback successful for: ${musicFile.name}")
                return@profile true
            }
            
                CrashLogger.log("AudioManager", "All playback methods failed for: ${musicFile.name}")
                false
                
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Error in playFile", e)
                false
            }
        }
    }
    
    private fun tryPlayWithVlcSync(uri: Uri): Boolean {
        return try {
            val vlc = vlcBackend
            if (vlc == null || !vlc.isInitialized()) {
                CrashLogger.log("AudioManager", "❌ VLC backend not available")
                return false
            }
            
            CrashLogger.log("AudioManager", "🔄 Starting VLC playback for: $uri")

            // VLC cannot handle Android content URIs directly. Play file:// URIs in place
            // (no copy), and for content:// URIs reuse the URI-keyed temp cache — previously
            // every single play re-copied the whole file (slow start + flash wear) because
            // the cache was keyed by the temp path itself and could never hit.
            val fileUri: Uri
            if (uri.scheme == "http" || uri.scheme == "https") {
                // Remote (Pi) stream — VLC handles http(s) natively, no temp copy possible
                // (ContentResolver can't open http). Range requests give seeking.
                currentVlcTempPath = null
                fileUri = uri
            } else if (uri.scheme == "file") {
                val direct = uri.path?.let { File(it) }
                if (direct == null || !direct.exists() || !direct.canRead()) {
                    CrashLogger.log("AudioManager", "❌ VLC: file URI not readable: $uri")
                    return false
                }
                currentVlcTempPath = null
                fileUri = uri
            } else if (uri.scheme == "content" && vlc.setDataSourceContent(uri)) {
                // Read in place from the phone's storage — no temp copy, no flash writes
                currentVlcTempPath = null
                vlc.start()
                CrashLogger.log("AudioManager", "✅ VLC playback initiated (in place)")
                return true
            } else {
                val uriKey = uri.toString()
                val cachedPath = tempFileCache[uriKey]?.takeIf { File(it).exists() }
                val tempPath = cachedPath ?: copyUriToTempFile(uri)?.absolutePath
                if (tempPath == null) {
                    CrashLogger.log("AudioManager", "❌ Failed to copy file to temp for VLC")
                    return false
                }
                if (cachedPath == null) CrashLogger.log("AudioManager", "✅ Copied to temp file for VLC: $tempPath")
                else CrashLogger.log("AudioManager", "✅ Reusing cached temp file for VLC: $tempPath")

                // Mark this VLC temp as in-use (protected from eviction) AND register it in
                // the LRU cache so OLD temps get reclaimed and a VLC-failure orphan stays reclaimable.
                currentVlcTempPath = tempPath
                tempFileCache[uriKey] = tempPath
                fileUri = Uri.fromFile(File(tempPath))
            }
            if (!vlc.setDataSource(fileUri)) {
                CrashLogger.log("AudioManager", "❌ VLC setDataSource failed")
                return false
            }
            
            // Prepare and start
            // Prepare and start: use start() which prepares implicitly and plays
            vlc.start()
            // The ViewModel/playFile() posts the now-playing notification with the real track name.
            
            // Wait for preparation (VLC handles this asynchronously)
            // The listeners will handle the actual playback start
            CrashLogger.log("AudioManager", "✅ VLC playback initiated")
            true
            
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "💥 VLC playback failed", e)
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

            // ExoPlayer must be accessed from its creation thread (main). playFile() runs
            // on Dispatchers.IO, so without this wrapper every call here threw/logged a
            // wrong-thread error and the fallback never actually worked reliably.
            runOnMainSync {
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }

            CrashLogger.log("AudioManager", "ExoPlayer started successfully")
            true
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "ExoPlayer playback error", e)
            false
        }
    }
    
    
    fun pause() {
        try {
            if (isUsingVlc) {
                vlcBackend?.pause()
            } else {
                exoPlayerFallback?.pause()
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Pause error", e)
        } finally {
            releaseWakeLock()
        }
    }
    
    
    private fun stopAllPlayback() {
        try {
            CrashLogger.log("AudioManager", "🛑 Stopping all playback before starting new file")
            
            // Stop VLC backend if it's playing
            vlcBackend?.let { vlc ->
                try {
                    CrashLogger.log("AudioManager", "🧹 Cleaning up VLC backend...")
                    if (vlc.isPlaying()) {
                        CrashLogger.log("AudioManager", "🛑 Stopping VLC playback...")
                        vlc.stop()
                        CrashLogger.log("AudioManager", "✅ VLC stopped")
                    }
                    vlc.reset()
                    CrashLogger.log("AudioManager", "✅ VLC reset completed")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "💥 VLC cleanup failed", e)
                }
            }
            
            // Stop ExoPlayer if it's playing - with proper cleanup to prevent dead thread handlers
            exoPlayerFallback?.let { player ->
                try {
                    runOnMainSync {
                        if (player.isPlaying) {
                            player.stop()
                            CrashLogger.log("AudioManager", "ExoPlayer stopped")
                        }
                        // Clear media items first
                        player.clearMediaItems()
                        CrashLogger.log("AudioManager", "ExoPlayer cleared")
                    }
                    
                    // Don't prepare here - let it be prepared when needed
                    // This prevents the dead thread handler issue
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error stopping ExoPlayer", e)
                }
            }
            
            // Reset the player state tracking. isUsingVlc must be reset here too —
            // previously it stayed true after stop(), so pause/seek/position calls kept
            // routing to the (stopped) VLC backend.
            isUsingVlc = false
            currentVlcTempPath = null // safe: stopAllPlayback runs before the next temp is created

            CrashLogger.log("AudioManager", "All playback stopped successfully")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error in stopAllPlayback", e)
        }
    }
    
    fun resume() {
        try {
            acquireWakeLock()
            if (isUsingVlc) {
                vlcBackend?.start()
            } else {
                runOnMainSync { exoPlayerFallback?.play() }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Resume error", e)
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        try {
            if (isUsingVlc) {
                vlcBackend?.setVolume(v)
            } else {
                runOnMainSync { exoPlayerFallback?.volume = v }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Set volume error", e)
        }
    }

    suspend fun fadeOut(durationMs: Long = 1000) {
        try {
            // Scale step count to the requested duration — a fixed 20 steps with a 10ms
            // floor made short fades (e.g. fadeOut(50)) silently take 200ms.
            val steps = (durationMs / 15L).toInt().coerceIn(2, 20)
            val delayPer = (durationMs / steps).coerceAtLeast(10)
            for (i in steps downTo 0) {
                val v = i / steps.toFloat()
                setVolume(v)
                kotlinx.coroutines.delay(delayPer)
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "FadeOut error", e)
        }
    }

    suspend fun fadeIn(durationMs: Long = 1000) {
        try {
            val steps = (durationMs / 15L).toInt().coerceIn(2, 20)
            val delayPer = (durationMs / steps).coerceAtLeast(10)
            for (i in 0..steps) {
                val v = i / steps.toFloat()
                setVolume(v)
                kotlinx.coroutines.delay(delayPer)
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "FadeIn error", e)
        }
    }
    
    fun stop() {
        stopAllPlayback()
        releasePreloadedPlayer() // Also release the preloaded player when stopping
        releaseWakeLock()
    }
    
    fun seekTo(position: Long) {
        try {
            if (isUsingVlc) {
                vlcBackend?.seekTo(positionMs = position)
            } else {
                runOnMainSync { exoPlayerFallback?.seekTo(position) }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Seek error", e)
        }
    }

    fun isCurrentlyPlaying(): Boolean {
        return try {
            if (isUsingVlc) {
                vlcBackend?.isPlaying() ?: false
            } else {
                callOnMainSync { exoPlayerFallback?.isPlaying ?: false }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error checking playing state", e)
            false
        }
    }
    
    fun getCurrentPosition(): Long {
        return try {
            if (isUsingVlc) {
                vlcBackend?.getCurrentPosition() ?: 0L
            } else {
                callOnMainSync { exoPlayerFallback?.currentPosition ?: 0L }
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Get position error", e)
            0L
        }
    }
    
    fun getDuration(): Long {
        return try {
            if (isUsingVlc) {
                vlcBackend?.getDuration() ?: 0L
            } else {
                callOnMainSync { exoPlayerFallback?.duration ?: 0L }
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
                    // CRITICAL: Don't delete a temp file currently in use by any backend.
                    if (tempPath == currentVlcTempPath ||
                        tempPath == currentTempWavPath) {
                        CrashLogger.log("AudioManager", "⚠️ Skipping deletion of in-use temp file: $tempPath")
                        // Put it back in the cache since it's still needed
                        tempFileCache[key] = tempPath
                        return@let
                    }
                    
                    try {
                        File(tempPath).delete()
                        CrashLogger.log("AudioManager", "Cleaned up temp file during cache clear: $tempPath")
                        // CRITICAL: Give OS time to release file descriptors after deletion
                        // removed sleep to avoid UI jank
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
     * Validate an AIFF file before playing it, so a corrupted file is skipped instead of crashing
     */
    private fun validateAIFFFile(uri: Uri, musicFile: MusicFile): Boolean {
        return try {
            CrashLogger.log("AudioManager", "🔍 Validating AIFF file: ${musicFile.name}")
            
            // Check file size - skip very large files that might cause memory issues
            val fileSizeMB = musicFile.size / (1024 * 1024)
            if (fileSizeMB > 200) {
                CrashLogger.log("AudioManager", "❌ AIFF file too large (${fileSizeMB}MB), skipping to prevent crashes")
                return false
            }
            
            // Check if file is accessible
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || !file.canRead()) {
                    CrashLogger.log("AudioManager", "❌ AIFF file not accessible: ${uri.path}")
                    return false
                }
            }
            
            // Try to read the first few bytes to check if file is corrupted
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val header = ByteArray(12) // AIFF header is 12 bytes
                    val bytesRead = inputStream.read(header)
                    if (bytesRead < 12) {
                        CrashLogger.log("AudioManager", "❌ AIFF file too short or corrupted")
                        return false
                    }
                    
                    // Check for AIFF file signature (FORM....AIFF)
                    val formSignature = String(header, 0, 4)
                    val aiffSignature = String(header, 8, 4)
                    
                    if (formSignature != "FORM" || aiffSignature != "AIFF") {
                        CrashLogger.log("AudioManager", "❌ Invalid AIFF file signature: FORM='$formSignature', AIFF='$aiffSignature'")
                        return false
                    }
                    
                    CrashLogger.log("AudioManager", "✅ AIFF file validation passed: valid header")
                    return true
                } ?: run {
                    CrashLogger.log("AudioManager", "❌ Cannot open AIFF file for validation")
                    return false
                }
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "❌ Error validating AIFF file", e)
                return false
            }
            
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "❌ Exception during AIFF validation", e)
            false
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
            var lastLogBytes = 0L
            
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                        
                        // Throttled progress logging (every 10MB)
                        if (totalBytes - lastLogBytes >= 10L * 1024L * 1024L) {
                            val mbCopied = totalBytes / (1024.0 * 1024.0)
                            CrashLogger.log("AudioManager", "📊 Copied ${String.format(Locale.getDefault(), "%.1f", mbCopied)}MB...")
                            lastLogBytes = totalBytes
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
            
            // Give the OS time to fully close file descriptors before the player opens the file
            // This prevents fdsan crashes where fd 0 gets corrupted
            // Force garbage collection to ensure all file handles are released
            CrashLogger.log("AudioManager", "🔄 Running GC and waiting for file descriptor cleanup...")
            System.gc()
            // removed sleep to avoid UI jank
            
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
            releaseWakeLock()
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
            
            currentVlcTempPath = null
            
            vlcBackend?.release()
            vlcBackend = null
            
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



    // Spectrogram generation with proper audio analysis
    /**
     * Spectrogram of the first [seconds] of [musicFile] (default 3 min; up to the whole track,
     * capped at [SPECTROGRAM_LIMIT_SECONDS]). Decoding and FFT run as one stream, so memory
     * doesn't grow with the length.
     */
    suspend fun generateSpectrogram(musicFile: MusicFile, seconds: Int = SPECTROGRAM_MAX_SECONDS): ImageBitmap? {
        val span = seconds.coerceIn(10, SPECTROGRAM_LIMIT_SECONDS)
        val startTime = android.os.SystemClock.elapsedRealtime()
        return try {
            // Key by URI (unique per file) + size + a quality tag. The old name+size key
        // collided across same-named files (returned a stale image for a different file)
        // and never invalidated after the quality bump. Bump the tag on any quality change.
        val baseKey = spectrogramCacheKey(musicFile)
        val cacheKey = "${baseKey}_$span"
            spectrogramCache[cacheKey]?.let { cachedSpectrogram ->
                spectrogramInfoCache[cacheKey]?.let { spectrogramInfoCache[baseKey] = it }
                CrashLogger.log("AudioManager", "Returning cached spectrogram for: ${musicFile.name}")
                CrashLogger.log("AudioManager", "Cached spectrogram size: ${cachedSpectrogram.width}x${cachedSpectrogram.height}")
                return cachedSpectrogram
            }
            
            try {
                
                CrashLogger.log("AudioManager", "Generating new spectrogram for: ${musicFile.name}")
                
                // No more MP3 -> temp WAV conversion: every format is decoded straight to
                // mono PCM by SpectrogramDecoder (the old conversion buffered ~10M boxed
                // Shorts and stalled the app on "Converting MP3 to WAV").
                _isConverting.value = false
                _isGeneratingSpectrogram.value = true
                _spectrogramProgress.value = 0f

                val spectrogram = try {
                    withTimeout(SPECTROGRAM_TIMEOUT_MS + span * 300L) {
                        withContext(Dispatchers.IO) {
                            generateSpectrogramInternal(musicFile, span)
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    CrashLogger.log("AudioManager", "Spectrogram generation timed out after ${SPECTROGRAM_TIMEOUT_MS / 1000}s")
                    null
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error during spectrogram generation", e)
                    null
                } catch (e: OutOfMemoryError) {
                    CrashLogger.log("AudioManager", "OOM during spectrogram generation")
                    null
                } finally {
                    _isGeneratingSpectrogram.value = false
                    _spectrogramProgress.value = 1f
                }

                if (spectrogram != null) {
                    spectrogramCache[cacheKey] = spectrogram
                    pendingInfo?.let { spectrogramInfoCache[cacheKey] = it; spectrogramInfoCache[baseKey] = it }
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
        } finally {
            val duration = android.os.SystemClock.elapsedRealtime() - startTime
            PerformanceProfiler.recordOperation("AudioManager.generateSpectrogram", duration)
            if (duration > 1000) {
                CrashLogger.log("AudioManager", "⚠️ SLOW spectrogram generation: ${duration}ms for ${musicFile.name}")
            }
        }
    }
    
    private fun generateSpectrogramInternal(musicFile: MusicFile, seconds: Int): ImageBitmap? {
        val t0 = System.currentTimeMillis()
        pendingInfo = null
        var builder: StreamingSpectrogram? = null
        return try {
            CrashLogger.log("AudioManager", "Spectrogram for ${musicFile.name}: first ${seconds}s")
            val (windowSize, height, requestedMaxFreq) = getQualityParameters()
            val fftSize = findNextPowerOf2(windowSize)
            val sink = object : MonoSink {
                override fun begin(sampleRate: Int) {
                    builder = StreamingSpectrogram(
                        sampleRate = sampleRate,
                        fftSize = fftSize,
                        height = height,
                        maxFreq = minOf(requestedMaxFreq, sampleRate / 2), // never draw above Nyquist
                        pixelsPerSecond = temporalResolution,
                        maxColumns = seconds * temporalResolution
                    )
                }
                override fun accept(block: ShortArray, count: Int) { builder?.accept(block, count) }
            }
            _spectrogramProgress.value = 0.02f
            val decoded = spectrogramDecoder.decode(
                musicFile.uri,
                deadlineMs = android.os.SystemClock.elapsedRealtime() + SPECTROGRAM_DECODE_DEADLINE_MS + seconds * 250L,
                seconds = seconds,
                sink = sink
            ) { p -> _spectrogramProgress.value = 0.02f + 0.9f * p }
            val b = builder
            if (decoded == null || b == null) {
                CrashLogger.log("AudioManager", "Failed to decode audio — no spectrogram")
                return null
            }
            b.finish()
            if (b.columnCount == 0) return null
            _spectrogramProgress.value = 0.94f

            // Render; very long spans are merged down to at most 4000 px (loudest wins)
            val outWidth = minOf(b.columnCount, 4000)
            val bitmap = Bitmap.createBitmap(outWidth, b.height, Bitmap.Config.ARGB_8888)
            val row = IntArray(outWidth)
            for (y in 0 until b.height) {
                b.renderRow(y, outWidth, row)
                bitmap.setPixels(row, 0, outWidth, 0, y, outWidth, 1)
            }

            val cut = SpectrumStats.estimateCutoff(b.rowMeanPower(), b.maxFreq)
            val analyzedSamples = minOf(b.analyzedSamples, b.columnCount.toLong() * b.hop)
            pendingInfo = SpectrogramInfo(
                sampleRate = decoded.sampleRate,
                channels = decoded.channels,
                bitsPerSample = decoded.bitsPerSample,
                isFloat = decoded.isFloat,
                codec = decoded.codec,
                analyzedSeconds = analyzedSamples / decoded.sampleRate.toFloat(),
                maxFreqHz = b.maxFreq,
                cutoffHz = cut.cutoffHz,
                bandwidthHz = cut.bandwidthHz,
                requestedSeconds = seconds
            )
            CrashLogger.log("AudioManager", "Spectrogram done: ${b.columnCount} columns → ${outWidth}px, " +
                "${"%.1f".format(analyzedSamples / decoded.sampleRate.toFloat())}s analysed in ${System.currentTimeMillis() - t0}ms")
            bitmap.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            CrashLogger.log("AudioManager", "OOM building spectrogram (${seconds}s)")
            null
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Spectrogram generation error", e)
            null
        }
    }

    private fun generateSpectrogramFromAudioData(
        audioData: ShortArray,
        actualSampleRate: Int = 44100,
        channels: Int = 2
    ): ImageBitmap? {
        val startTime = System.currentTimeMillis()
        return try {
            CrashLogger.log("AudioManager", "Starting streaming spectrogram generation at ${startTime}ms")
            
            if (audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "Audio data is empty, cannot generate spectrogram")
                return null
            }
            
            // Convert interleaved stereo to mono using the REAL channel count — the old
            // "even sample count = stereo" guess halved mono files (2× compressed time
            // axis and garbled analysis).
            val monoData = if (channels >= 2 && audioData.size >= 2) {
                val mono = ShortArray(audioData.size / channels)
                for (i in mono.indices) {
                    var sum = 0
                    for (c in 0 until channels) sum += audioData[i * channels + c].toInt()
                    mono[i] = (sum / channels).toShort()
                }
                CrashLogger.log("AudioManager", "Converted ${channels}ch to mono: ${audioData.size} -> ${mono.size} samples")
                mono
            } else {
                CrashLogger.log("AudioManager", "Using mono audio data: ${audioData.size} samples")
                audioData
            }

            // Use the file's REAL sample rate — hard-coding 44100 skewed the time axis
            // (~9% at 48 kHz, 2.2x at 96 kHz) because the UI labels assume 10 px/s.
            val sampleRate = actualSampleRate
            
            // Get quality-based parameters
            val (windowSize, height, requestedMaxFreq) = getQualityParameters()
            // Never draw above Nyquist — those rows just repeated the top FFT bin.
            val maxFreq = minOf(requestedMaxFreq, sampleRate / 2)
            val fftSize = findNextPowerOf2(windowSize)
            
            // Calculate hop size based on quality settings and available data
            val pixelsPerSecond = temporalResolution
            
            // Get actual audio duration for dynamic width calculation (capped)
            val audioDurationSeconds = monoData.size / sampleRate.toFloat()
            val fileSizeMB = monoData.size * 2 / (1024 * 1024) // Rough estimate in MB
            val maxAnalysisSeconds = SPECTROGRAM_MAX_SECONDS

            CrashLogger.log("AudioManager", "File size: ~${fileSizeMB}MB, limiting analysis to ${maxAnalysisSeconds}s for memory management")
            
            // Safety check: limit target width to prevent memory issues
            val targetWidth = (pixelsPerSecond * maxAnalysisSeconds).coerceAtMost(SPECTROGRAM_MAX_SECONDS * 10) // 10 px/s max
            val hopSize = sampleRate / pixelsPerSecond  // Samples per pixel
            val hopSizeAdjusted = hopSize.coerceAtLeast(64)  // Minimum hop size for performance
            
            CrashLogger.log("AudioManager", "Spectrogram dimensions: audioDuration=${audioDurationSeconds}s, maxAnalysis=${maxAnalysisSeconds}s, targetWidth=${targetWidth}px, hopSize=${hopSizeAdjusted}")
            
            // Calculate actual width based on available data
            // Count every hop that starts inside the audio (windows are zero-padded at the end),
            // so 180 s gives exactly 1800 columns (= 3:00) instead of 1799 (2:59).
            val maxPossibleWindows = (monoData.size + hopSizeAdjusted - 1) / hopSizeAdjusted
            val width = minOf(maxPossibleWindows, targetWidth)  // Use calculated width
            
            CrashLogger.log("AudioManager", "Streaming spectrogram: ${monoData.size} samples, targetWidth=$targetWidth, actualWidth=$width, hopSize=$hopSizeAdjusted")
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            CrashLogger.log("AudioManager", "Generating spectrogram: ${monoData.size} mono samples, windowSize=$windowSize, fftSize=$fftSize, maxFreq=${maxFreq}Hz")
            // Memory management: check if spectrogram would be too large
            val estimatedMemoryMB = (width * height * 4) / (1024 * 1024) // 4 bytes per float
            if (estimatedMemoryMB > 50) {
                CrashLogger.log("AudioManager", "Spectrogram too large (${estimatedMemoryMB}MB), reducing dimensions")
                // Reduce width to prevent memory issues
                val maxWidth = (50 * 1024 * 1024) / (height * 4) // Calculate max width for 50MB
                val reducedWidth = minOf(width, maxWidth)
                CrashLogger.log("AudioManager", "Reducing width from $width to $reducedWidth to prevent OOM")
                return generateSpectrogramFromAudioData(monoData.copyOfRange(0, (reducedWidth * hopSizeAdjusted).coerceAtMost(monoData.size)), sampleRate, 1)
            }
            
            // Generate power spectrogram data with memory check
            val spectrogramData = try {
                Array(height) { FloatArray(width) }
            } catch (e: OutOfMemoryError) {
                CrashLogger.log("AudioManager", "OutOfMemoryError creating spectrogram array")
                return null
            }
            var maxPower = 0f
            
            // Memory management: force GC before processing large spectrograms
            if (width > 1000 || fileSizeMB > 30) {
                CrashLogger.log("AudioManager", "Forcing garbage collection for large spectrogram (width=$width, fileSize=${fileSizeMB}MB)")
                System.gc()
                // removed sleep to avoid UI jank
            }
            
            // Gain is irrelevant: powers are normalised by maxPower before colouring.
            val dynamicRangeAdjustment = 1f
            
            CrashLogger.log("AudioManager", "Starting spectrogram generation with ${monoData.size} samples, width=$width, height=$height")
            CrashLogger.log("AudioManager", "Target width: $targetWidth, Max possible windows: $maxPossibleWindows, Using: $width windows")
            CrashLogger.log("AudioManager", "Quality: $spectrogramQuality, Frequency: $frequencyRange, Resolution: ${temporalResolution}px/s")
            CrashLogger.log("AudioManager", "Calculated hop size: $hopSize, Adjusted hop size: $hopSizeAdjusted, Window size: $windowSize, FFT size: $fftSize")
            CrashLogger.log("AudioManager", "Analyzing ${maxAnalysisSeconds} seconds (${maxAnalysisSeconds/60.0} minutes) of audio data")
            
            // Precompute FFT bin index per frequency row to avoid recomputing inside loops
            val freqBinIndex = IntArray(height) { row ->
                val targetFreq = (row * maxFreq) / height
                ((targetFreq * fftSize) / sampleRate).toInt().coerceAtMost(fftSize / 2 - 1)
            }
            
            // Parallelize time columns across up to 8 workers (or CPU count)
            val workers = kotlin.math.min(8, Runtime.getRuntime().availableProcessors())
            val localMax = FloatArray(workers) { 0f }
            val tColumnsStart = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.runBlocking {
                for (w in 0 until workers) {
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        var timeIndex = w
                        var wMax = 0f
                        while (timeIndex < width) {
                            val startSample = timeIndex * hopSizeAdjusted
                            if (startSample >= monoData.size) break
                            
                            val window = ShortArray(fftSize)
                            var wi = 0
                            while (wi < fftSize) {
                                val sampleIndex = startSample + wi
                                window[wi] = if (sampleIndex < monoData.size) monoData[sampleIndex] else 0
                                wi++
                            }
                            val windowedData = applyHanningWindow(window)
                            val fftResult = performSimpleFFT(windowedData)
                            
                            var freqIndex = 0
                            while (freqIndex < height) {
                                val fftIndex = freqBinIndex[freqIndex]
                                val idxRe = fftIndex * 2
                                val idxIm = idxRe + 1
                                if (idxIm < fftResult.size) {
                                    val real = fftResult[idxRe]
                                    val imag = fftResult[idxIm]
                                    val magnitude = sqrt(real * real + imag * imag)
                                    val power = magnitude * magnitude * dynamicRangeAdjustment
                                    spectrogramData[height - 1 - freqIndex][timeIndex] = power
                                    if (power > wMax) wMax = power
                                }
                                freqIndex++
                            }
                            
                            timeIndex += workers
                        }
                        localMax[w] = wMax
                    }
                }
            }
            // Combine local maxima
            var iMax = 0
            while (iMax < workers) {
                if (localMax[iMax] > maxPower) maxPower = localMax[iMax]
                iMax++
            }
            val tColumnsEnd = android.os.SystemClock.elapsedRealtime()
            PerformanceProfiler.recordOperation("Spectrogram.ColumnsFFT", (tColumnsEnd - tColumnsStart))
            
            // Skip the 3x3 box-blur — it was the main source of the blurry look (and it
            // cost a full-grid 9-tap pass). Render the raw FFT power for a sharp image.
            val smoothedData = spectrogramData

            // Per-frequency average power → cutoff/bandwidth estimate (row 0 = 0 Hz).
            run {
                val rowMean = DoubleArray(height)
                for (f in 0 until height) {
                    val row = spectrogramData[height - 1 - f]
                    var sum = 0.0
                    for (x in 0 until width) sum += row[x]
                    rowMean[f] = sum / width
                }
                lastFftInfo = maxFreq to SpectrumStats.estimateCutoff(rowMean, maxFreq)
                CrashLogger.log("AudioManager", "Spectrum: maxFreq=$maxFreq cutoff=${lastFftInfo?.second}")
            }
            
            // Convert power to dB and create bitmap with professional color mapping
            val tBitmapStart = android.os.SystemClock.elapsedRealtime()
            // Fill an int buffer then write the whole bitmap in one setPixels() call —
            // far faster than width*height individual setPixel() JNI calls (important
            // now that the resolution is doubled).
            val pixels = IntArray(width * height)
            var y = 0
            while (y < height) {
                val rowOffset = y * width
                var x = 0
                while (x < width) {
                    pixels[rowOffset + x] = powerToColor(smoothedData[y][x], maxPower)
                    x++
                }
                y++
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            val tBitmapEnd = android.os.SystemClock.elapsedRealtime()
            PerformanceProfiler.recordOperation("Spectrogram.BitmapWrite", (tBitmapEnd - tBitmapStart))
            
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
        // Tighter 90 dB window (was 120) lifts contrast so harmonics stand out against a
        // darker floor — looks sharper without any extra compute.
        val powerDb = if (power > 0) 10f * log10(power / maxPower) else -90f
        val normalizedDb = ((powerDb + 90f) / 90f).coerceIn(0f, 1f)

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
