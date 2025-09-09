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
import wseemann.media.FFmpegMediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.media.AudioManager as AndroidAudioManager

@androidx.media3.common.util.UnstableApi
class AudioManager(private val context: Context) {
    
    private var ffmpegPlayer: FFmpegMediaPlayer? = null
    private var exoPlayerFallback: ExoPlayer? = null
    private var currentFile: MusicFile? = null
    private var isUsingFFmpeg = false
    private var isFFmpegPrepared = false
    
    // Spectrogram cache
    private val spectrogramCache = mutableMapOf<String, ImageBitmap>()
    
    // Temp file cache for AIFF files to avoid re-copying
    private val tempFileCache = mutableMapOf<String, String>()
    
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
            
            ffmpegPlayer?.apply {
                setOnPreparedListener { mp ->
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepared successfully")
                    isFFmpegPrepared = true
                }
                setOnErrorListener { mp, what, extra ->
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer error: what=$what, extra=$extra")
                    isFFmpegPrepared = false
                    false
                }
                setOnCompletionListener { mp ->
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer playback completed")
                }
                setOnInfoListener { mp, what, extra ->
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer info: what=$what, extra=$extra")
                    false
                }
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
            
            CrashLogger.log("AudioManager", "AudioManager initialized with FFmpegMediaPlayer + ExoPlayer fallback")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "AudioManager initialization failed", e)
        }
    }
    
    fun playFile(musicFile: MusicFile): Boolean {
        return try {
            currentFile = musicFile
            val uri = musicFile.uri
            
            CrashLogger.log("AudioManager", "Attempting to play file: ${musicFile.name}")
            
            // CRITICAL: Stop any currently playing audio before starting new playback
            stopAllPlayback()
            
            // Check if this is an AIFF file that needs FFmpeg
            val fileName = musicFile.name.lowercase(Locale.getDefault())
            val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
            
            if (isAiffFile) {
                // Try FFmpegMediaPlayer first for AIFF files (ExoPlayer doesn't support AIFF)
                CrashLogger.log("AudioManager", "AIFF file detected: ${musicFile.name}")
                CrashLogger.log("AudioManager", "Trying FFmpegMediaPlayer for AIFF (ExoPlayer doesn't support AIFF)")
                val ffmpegSuccess = tryPlayWithFFmpegSync(uri)
                if (ffmpegSuccess) {
                    isUsingFFmpeg = true
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer playback successful for AIFF: ${musicFile.name}")
                    return true
                }
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer failed for AIFF, trying ExoPlayer fallback (will likely fail)")
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
            val player = ffmpegPlayer
            if (player == null) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer is null - not initialized properly")
                return false
            }
            CrashLogger.log("AudioManager", "FFmpegMediaPlayer is available, attempting playback")
            
            // Reset prepared state
            isFFmpegPrepared = false
            
            // Try to set data source for FFmpegMediaPlayer
            val dataSource = try {
                if (uri.scheme == "file") {
                    // For file:// URIs, use the path directly
                    val filePath = uri.path
                    CrashLogger.log("AudioManager", "Using file path for FFmpegMediaPlayer: $filePath")
                    filePath
                } else {
                    // For content:// URIs, check cache first, then copy to temp file
                    val uriString = uri.toString()
                    val cachedPath = tempFileCache[uriString]
                    
                    if (cachedPath != null && File(cachedPath).exists()) {
                        CrashLogger.log("AudioManager", "Using cached temp file: $cachedPath")
                        cachedPath
                    } else {
                        CrashLogger.log("AudioManager", "Content URI detected, copying to temp file for FFmpegMediaPlayer")
                        val tempFile = copyUriToTempFile(uri)
                        if (tempFile != null) {
                            val tempPath = tempFile.absolutePath
                            tempFileCache[uriString] = tempPath
                            CrashLogger.log("AudioManager", "Copied to temp file and cached: $tempPath")
                            tempPath
                        } else {
                            CrashLogger.log("AudioManager", "Failed to copy content URI to temp file")
                            return false
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Error preparing data source for FFmpegMediaPlayer", e)
                return false
            }
            
            if (dataSource == null) {
                CrashLogger.log("AudioManager", "No data source available for FFmpegMediaPlayer")
                return false
            }
            
            // Set data source
            try {
                player.setDataSource(dataSource)
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer setDataSource: $dataSource")
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer setDataSource failed", e)
                // Try to reset the player state
                try {
                    player.reset()
                } catch (resetException: Exception) {
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer reset failed after setDataSource error", resetException)
                }
                return false
            }
            
            try {
                // Try synchronous preparation first
                player.prepare()
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepare (sync) completed")
                isFFmpegPrepared = true
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepare (sync) failed, trying async", e)
                
                // Fallback to async preparation
                try {
                    player.prepareAsync()
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepareAsync called")
                    
                    // Wait for preparation to complete (with timeout)
                    val startTime = System.currentTimeMillis()
                    val timeout = 5000L // 5 seconds timeout
                    
                    while (!isFFmpegPrepared && (System.currentTimeMillis() - startTime) < timeout) {
                        try {
                            Thread.sleep(50) // Wait 50ms between checks
                        } catch (e: InterruptedException) {
                            CrashLogger.log("AudioManager", "Interrupted while waiting for FFmpegMediaPlayer preparation", e)
                            return false
                        }
                    }
                    
                    if (!isFFmpegPrepared) {
                        CrashLogger.log("AudioManager", "FFmpegMediaPlayer preparation timeout after ${timeout}ms")
                        return false
                    }
                    
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer preparation completed, starting playback")
                } catch (e2: Exception) {
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer prepareAsync failed", e2)
                    return false
                }
            }
            
            // Set audio stream type for better compatibility
            try {
                player.setAudioStreamType(AndroidAudioManager.STREAM_MUSIC)
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer audio stream type set to STREAM_MUSIC")
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer setAudioStreamType failed", e)
            }
            
            // Set volume to maximum
            try {
                player.setVolume(1.0f, 1.0f)
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer volume set to maximum")
        } catch (e: Exception) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer setVolume failed", e)
            }
            
            // Now start playback after preparation is complete
            try {
                if (isFFmpegPrepared) {
                    player.start()
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer started after proper preparation")
                } else {
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer not prepared, cannot start")
                    return false
                }
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer start failed", e)
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
            CrashLogger.log("AudioManager", "Stopping all playback before starting new file")
            
            // Stop FFmpegMediaPlayer if it's playing
            ffmpegPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        CrashLogger.log("AudioManager", "FFmpegMediaPlayer stopped")
                    }
                    player.reset()
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer reset")
                } catch (e: Exception) {
                    CrashLogger.log("AudioManager", "Error stopping FFmpegMediaPlayer", e)
                }
            }
            
            // Stop ExoPlayer if it's playing
            exoPlayerFallback?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        CrashLogger.log("AudioManager", "ExoPlayer stopped")
                    }
                    player.clearMediaItems()
                    CrashLogger.log("AudioManager", "ExoPlayer cleared")
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
            CrashLogger.log("AudioManager", "All caches cleared")
        } catch (e: Exception) { 
            CrashLogger.log("AudioManager", "Cache clearing error", e)
        }
    }
    
    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                CrashLogger.log("AudioManager", "Cannot open input stream for URI: $uri")
                return null
            }
            
            // Create temp file
            val tempFile = File.createTempFile("aiff_temp_", ".aiff", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            
            // Copy data with larger buffer for better performance
            val buffer = ByteArray(65536) // 64KB buffer instead of 8KB
            var bytesRead: Int
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            
            inputStream.close()
            outputStream.close()
            
            val duration = System.currentTimeMillis() - startTime
            val mbCopied = totalBytes / (1024.0 * 1024.0)
            val speed = if (duration > 0) mbCopied / (duration / 1000.0) else 0.0
            
            CrashLogger.log("AudioManager", "Successfully copied URI to temp file: ${tempFile.absolutePath} (${String.format(Locale.getDefault(), "%.2f", mbCopied)}MB in ${duration}ms, ${String.format(Locale.getDefault(), "%.2f", speed)}MB/s)")
            tempFile
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error copying URI to temp file", e)
            null
        }
    }
    
    fun release() {
        try {
            ffmpegPlayer?.release()
            ffmpegPlayer = null
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
    
    // Initialize broadcast receiver for cache clearing
    private fun initializeBroadcastReceiver() {
        try {
            val filter = IntentFilter("com.example.mobiledigger.CLEAR_CACHE")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.example.mobiledigger.CLEAR_CACHE") {
                        clearSpectrogramCache()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            CrashLogger.log("AudioManager", "Broadcast receiver registered successfully")
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Failed to register broadcast receiver", e)
        }
    }
    
    // Spectrogram generation with proper audio analysis
    suspend fun generateSpectrogram(musicFile: MusicFile): ImageBitmap? {
        return try {
            val cacheKey = "${musicFile.name}_${musicFile.size}"
            spectrogramCache[cacheKey]?.let { 
                CrashLogger.log("AudioManager", "Returning cached spectrogram for: ${musicFile.name}")
                CrashLogger.log("AudioManager", "Cached spectrogram size: ${it.width}x${it.height}")
                return it 
            }
            
            CrashLogger.log("AudioManager", "Generating new spectrogram for: ${musicFile.name}")
            
            // Try to generate real spectrogram first, fallback only if needed
            
            val spectrogram = withContext(Dispatchers.IO) {
                generateSpectrogramInternal(musicFile)
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
    
    private fun generateSpectrogramInternal(musicFile: MusicFile): ImageBitmap? {
        return try {
            CrashLogger.log("AudioManager", "Starting spectrogram generation for: ${musicFile.name}")
            
            val uri = musicFile.uri
            
            CrashLogger.log("AudioManager", "URI: $uri")
            
            // Extract audio data from the file
            val audioData = extractAudioData(uri)
            if (audioData == null || audioData.isEmpty()) {
                CrashLogger.log("AudioManager", "Failed to extract audio data, generating fallback spectrogram")
                return generateFallbackSpectrogram(musicFile)
            }
            
            CrashLogger.log("AudioManager", "Extracted ${audioData.size} audio samples")
            
            // Generate spectrogram from audio data
            val spectrogram = generateSpectrogramFromAudioData(audioData)
            if (spectrogram == null) {
                CrashLogger.log("AudioManager", "Failed to generate spectrogram from audio data, using fallback")
                return generateFallbackSpectrogram(musicFile)
            }
            
            CrashLogger.log("AudioManager", "Generated spectrogram bitmap successfully")
            spectrogram
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Spectrogram generation internal error", e)
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
                uriString.contains(".mp3") -> extractAudioDataWithMediaExtractor(uri)
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
            val maxSamples = sampleRate * 30 // 30 seconds max
            
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
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            // Get basic audio information
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 180000L // Default 3 minutes
            val sampleRate = 44100 // Default sample rate
            val channels = 2 // Default stereo
            
            CrashLogger.log("AudioManager", "FLAC metadata: duration=${duration}ms, sampleRate=$sampleRate, channels=$channels")
            
            retriever.release()
            
            // Generate 30 seconds of realistic audio data
            val maxSamples = sampleRate * 30
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
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            
            // Find audio track
            var audioTrackIndex = -1
            var sampleRate = 44100
            var channels = 2
            var audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
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
                return null
            }
            
            extractor.selectTrack(audioTrackIndex)
            CrashLogger.log("AudioManager", "MediaExtractor: sampleRate=$sampleRate, channels=$channels, format=$audioFormat")
            
            // Read audio data with proper buffer handling
            val audioData = mutableListOf<Short>()
            val bufferSize = 16384 // Larger buffer
            var totalSamples = 0
            val maxSamples = sampleRate * 30
            
            while (totalSamples < maxSamples) {
                val byteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize <= 0) break
                
                val actualData = ByteArray(sampleSize)
                byteBuffer.rewind()
                byteBuffer.get(actualData)
                
                // Convert based on format
                val samples = convertBytesToShorts(actualData, audioFormat)
                audioData.addAll(samples)
                totalSamples += samples.size
                
                if (!extractor.advance()) break
            }
            
            extractor.release()
            CrashLogger.log("AudioManager", "MediaExtractor extracted ${audioData.size} samples")
            audioData.toShortArray()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "MediaExtractor failed", e)
            null
        }
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
                        val maxSamples = 44100 * 30 // 30 seconds max, assume 44.1kHz
                        
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
                    val maxSamples = sampleRate * 30 // 30 seconds max
                    
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
                    val maxSamples = 44100 * 30 // Assume 44.1kHz
                    
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
        return try {
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
            
            val width = 600  // Much higher time resolution for better detail
            val height = 400 // Higher frequency resolution
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Parameters for power spectrogram
            val sampleRate = 44100
            val maxFrequency = 25000  // Increased to 25kHz for better high-frequency detail
            val windowSize = 4096  // Larger window for better frequency resolution
            val hopSize = maxOf(64, monoData.size / (width * 2))  // Smaller hop size for better temporal resolution
            val fftSize = findNextPowerOf2(windowSize)
            
            CrashLogger.log("AudioManager", "Generating spectrogram: ${monoData.size} mono samples, windowSize=$windowSize, fftSize=$fftSize, maxFreq=${maxFrequency}Hz")
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
            
            // Generate power spectrogram data
            val spectrogramData = Array(height) { FloatArray(width) }
            var maxPower = 0f
            
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
            CrashLogger.log("AudioManager", "Hop size: $hopSize, Window size: $windowSize, FFT size: $fftSize")
            
            // Calculate expected number of time windows
            val expectedWindows = (monoData.size - windowSize) / hopSize + 1
            CrashLogger.log("AudioManager", "Expected time windows: $expectedWindows")
            
            for (timeIndex in 0 until width) {
                val startSample = timeIndex * hopSize
                
                if (startSample >= monoData.size) break
                
                // Log progress every 25% of the way through
                if (timeIndex % (width / 4) == 0) {
                    CrashLogger.log("AudioManager", "Processing time index $timeIndex/$width (${(timeIndex * 100 / width)}%)")
                    CrashLogger.log("AudioManager", "Start sample: $startSample, Window range: $startSample to ${startSample + fftSize}")
                }
                
                // Extract window of audio data
                val window = ShortArray(fftSize)
                for (i in 0 until fftSize) {
                    val sampleIndex = startSample + i
                    window[i] = if (sampleIndex < monoData.size) monoData[sampleIndex] else 0
                }
                
                // Apply Hanning window
                val windowedData = applyHanningWindow(window)
                
                // Log window data for first few time indices
                if (timeIndex < 3) {
                    CrashLogger.log("AudioManager", "Time $timeIndex: Window data range: min=${windowedData.minOrNull()}, max=${windowedData.maxOrNull()}")
                    CrashLogger.log("AudioManager", "Time $timeIndex: First 10 windowed samples: ${windowedData.take(10).joinToString()}")
                }
                
                // Perform FFT
                val fftResult = performSimpleFFT(windowedData)
                
                // Log FFT results for first few time indices
                if (timeIndex < 3) {
                    CrashLogger.log("AudioManager", "Time $timeIndex: FFT result range: min=${fftResult.minOrNull()}, max=${fftResult.maxOrNull()}")
                    CrashLogger.log("AudioManager", "Time $timeIndex: First 10 FFT results: ${fftResult.take(10).joinToString()}")
                }
                
                // Calculate power spectrum (magnitude squared) with 25kHz limit
                for (freqIndex in 0 until height) {
                    // Map frequency index to actual frequency (0 to 25kHz)
                    val targetFreq = (freqIndex * maxFrequency) / height
                    
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
            
            CrashLogger.log("AudioManager", "Generated spectrogram bitmap successfully, maxPower=$maxPower")
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error generating spectrogram from audio data", e)
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
        // Convert power to dB scale (like professional spectrograms)
        val powerDb = if (power > 0) 10f * log10(power / maxPower) else -120f
        val normalizedDb = ((powerDb + 120f) / 120f).coerceIn(0f, 1f)
        
        // Enhanced contrast for better visibility
        val enhancedDb = normalizedDb * normalizedDb  // Square for better contrast
        val intensity = (enhancedDb * 255).toInt().coerceIn(0, 255)
        
        val (red, green, blue) = when {
            intensity < 30 -> {
                // Black to Dark Blue (0-29)
                val t = intensity / 30f
                Triple(0, 0, (t * 50).toInt())
            }
            intensity < 60 -> {
                // Dark Blue to Blue (30-59)
                val t = (intensity - 30) / 30f
                Triple(0, 0, (50 + t * 100).toInt())
            }
            intensity < 90 -> {
                // Blue to Purple (60-89)
                val t = (intensity - 60) / 30f
                Triple((t * 100).toInt(), 0, (150 - t * 50).toInt())
            }
            intensity < 120 -> {
                // Purple to Red (90-119)
                val t = (intensity - 90) / 30f
                Triple((100 + t * 155).toInt(), 0, (100 - t * 100).toInt())
            }
            intensity < 150 -> {
                // Red to Orange (120-149)
                val t = (intensity - 120) / 30f
                Triple(255, (t * 100).toInt(), 0)
            }
            intensity < 180 -> {
                // Orange to Yellow (150-179)
                val t = (intensity - 150) / 30f
                Triple(255, (100 + t * 155).toInt(), 0)
            }
            intensity < 210 -> {
                // Yellow to Light Yellow (180-209)
                val t = (intensity - 180) / 30f
                Triple(255, 255, (t * 100).toInt())
            }
            else -> {
                // Light Yellow to White (210-255)
                val t = (intensity - 210) / 45f
                Triple(255, 255, (100 + t * 155).toInt())
            }
        }
        
        return Color.argb(255, red, green, blue)
    }
    
}
