package com.example.mobiledigger.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import android.media.AudioAttributes
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
    
    fun playFile(musicFile: MusicFile, nextMusicFile: MusicFile? = null): Boolean {
        return try {
            currentFile = musicFile
            val uri = musicFile.uri
            
            if (uri == null) {
                CrashLogger.log("AudioManager", "No URI available for file: ${musicFile.name}")
                return false
            }
            
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
    
    private suspend fun tryPlayWithFFmpeg(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val player = ffmpegPlayer ?: return@withContext false
            
            // Stop any current playback
            if (player.isPlaying) {
                player.stop()
            }
            
            // Reset prepared state
            isFFmpegPrepared = false
            
            // Try to get file path for FFmpegMediaPlayer
            val filePath = try {
                // For file:// URIs, try to get the actual path
                if (uri.scheme == "file") {
                    uri.path
                    } else {
                    // For content:// URIs, FFmpegMediaPlayer might have issues
                    // Log this and return false to fallback to ExoPlayer
                    CrashLogger.log("AudioManager", "FFmpegMediaPlayer cannot handle content:// URI: $uri")
                    return@withContext false
                    }
                } catch (e: Exception) {
                CrashLogger.log("AudioManager", "Error getting file path for FFmpegMediaPlayer", e)
                return@withContext false
            }
            
            if (filePath == null) {
                CrashLogger.log("AudioManager", "Cannot get file path for FFmpegMediaPlayer")
                return@withContext false
            }
            
            // Set data source and prepare
            player.setDataSource(filePath)
            player.prepareAsync()
            
            // Wait for preparation to complete
            var attempts = 0
            while (attempts < 50 && !isFFmpegPrepared) { // Wait up to 5 seconds
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            if (isFFmpegPrepared) {
                player.start()
                return@withContext true
                } else {
                CrashLogger.log("AudioManager", "FFmpegMediaPlayer preparation timeout")
                return@withContext false
            }
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
    
    private suspend fun tryPlayWithExoPlayer(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val player = exoPlayerFallback ?: return@withContext false
            
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
            true
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "ExoPlayer fallback error", e)
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
    
    fun isPlaying(): Boolean {
        return try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.isPlaying ?: false
            } else {
                exoPlayerFallback?.isPlaying ?: false
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Is playing error", e)
            false
        }
    }
    
    fun getCurrentFile(): MusicFile? {
        return currentFile
    }

    fun setVolume(volume: Float) {
        try {
            if (isUsingFFmpeg) {
                ffmpegPlayer?.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            } else {
                exoPlayerFallback?.volume = volume.coerceIn(0f, 1f)
            }
            } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Set volume error", e)
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
            
            CrashLogger.log("AudioManager", "Successfully copied URI to temp file: ${tempFile.absolutePath} (${String.format("%.2f", mbCopied)}MB in ${duration}ms, ${String.format("%.2f", speed)}MB/s)")
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
    
    // Spectrogram generation with proper audio analysis
    suspend fun generateSpectrogram(musicFile: MusicFile): ImageBitmap? {
        return try {
            val cacheKey = "${musicFile.name}_${musicFile.size}"
            spectrogramCache[cacheKey]?.let { return it }
            
            val spectrogram = withContext(Dispatchers.IO) {
                generateSpectrogramInternal(musicFile)
            }
            
            spectrogram?.let { 
                spectrogramCache[cacheKey] = it
            }
            
            spectrogram
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Spectrogram generation failed", e)
            null
        }
    }
    
    private fun generateSpectrogramInternal(musicFile: MusicFile): ImageBitmap? {
        return try {
            CrashLogger.log("AudioManager", "Starting spectrogram generation for: ${musicFile.name}")
            
            val uri = musicFile.uri ?: return null
            
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
            
            CrashLogger.log("AudioManager", "Generated spectrogram bitmap")
            spectrogram
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Spectrogram generation internal error", e)
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
            
            // Create a pattern based on file characteristics
            val paint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            
            // Generate a pattern based on file size and name hash
            val fileHash = musicFile.name.hashCode()
            val sizeFactor = (musicFile.size / 1000000f).coerceIn(0.1f, 10f) // MB
            
            for (y in 0 until height step 4) {
                val frequency = (fileHash + y * 1000) % 1000
                val amplitude = (sizeFactor * 50).toInt().coerceIn(10, 100)
                
                paint.color = when (frequency % 5) {
                    0 -> Color.BLUE
                    1 -> Color.GREEN
                    2 -> Color.YELLOW
                    3 -> Color.RED
                    else -> Color.WHITE
                }
                
                for (x in 0 until width) {
                    val wave = (sin(x * 0.1 + y * 0.05) * amplitude).toInt()
                    val pixelY = (height / 2 + wave).coerceIn(0, height - 1)
                    bitmap.setPixel(x, pixelY, paint.color)
                }
            }
            
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Fallback spectrogram generation failed", e)
            null
        }
    }
    
    private fun extractAudioData(uri: Uri): ShortArray? {
        return try {
            // Try to extract audio data using MediaExtractor
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
                    
                    // Try to get audio format
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
            CrashLogger.log("AudioManager", "Selected audio track: sampleRate=$sampleRate, channels=$channels, format=$audioFormat")
            
            // Read audio data
            val audioData = mutableListOf<Short>()
            val buffer = ByteArray(8192) // Larger buffer for better performance
            var totalSamples = 0
            val maxSamples = sampleRate * 30 // Limit to 30 seconds for performance
            
            while (totalSamples < maxSamples) {
                val byteBuffer = java.nio.ByteBuffer.wrap(buffer)
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize <= 0) break
                
                // Convert bytes to shorts based on audio format
                when (audioFormat) {
                    android.media.AudioFormat.ENCODING_PCM_16BIT -> {
                        // 16-bit audio (most common)
                        for (i in 0 until sampleSize step 2) {
                            if (i + 1 < sampleSize) {
                                val sample = ((buffer[i + 1].toInt() and 0xFF) shl 8) or (buffer[i].toInt() and 0xFF)
                                audioData.add(sample.toShort())
                                totalSamples++
                            }
                        }
                    }
                    android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                        // 8-bit audio
                        for (i in 0 until sampleSize) {
                            val sample = (buffer[i].toInt() and 0xFF) - 128 // Convert to signed
                            audioData.add((sample shl 8).toShort()) // Scale to 16-bit range
                            totalSamples++
                        }
                    }
                    android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                        // 32-bit float audio
                        for (i in 0 until sampleSize step 4) {
                            if (i + 3 < sampleSize) {
                                val floatValue = java.nio.ByteBuffer.wrap(buffer, i, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                                val sample = (floatValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                audioData.add(sample.toShort())
                                totalSamples++
                            }
                        }
                    }
                    else -> {
                        // Default to 16-bit interpretation
                        for (i in 0 until sampleSize step 2) {
                            if (i + 1 < sampleSize) {
                                val sample = ((buffer[i + 1].toInt() and 0xFF) shl 8) or (buffer[i].toInt() and 0xFF)
                                audioData.add(sample.toShort())
                                totalSamples++
                            }
                        }
                    }
                }
                
                if (!extractor.advance()) break
            }
            
            extractor.release()
            CrashLogger.log("AudioManager", "Extracted ${audioData.size} audio samples")
            audioData.toShortArray()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error extracting audio data", e)
            null
        }
    }
    
    private fun generateSpectrogramFromAudioData(audioData: ShortArray): ImageBitmap? {
        return try {
            val width = 150  // Time resolution
            val height = 200 // Frequency resolution
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Parameters for spectrogram
            val sampleRate = 44100
            val windowSize = 1024
            val hopSize = audioData.size / width
            val fftSize = windowSize
            
            // Generate spectrogram data
            val spectrogramData = Array(height) { FloatArray(width) }
            
            for (timeIndex in 0 until width) {
                val startSample = timeIndex * hopSize
                val endSample = minOf(startSample + windowSize, audioData.size)
                
                if (startSample >= audioData.size) break
                
                // Extract window of audio data
                val window = ShortArray(windowSize)
                for (i in 0 until windowSize) {
                    val sampleIndex = startSample + i
                    window[i] = if (sampleIndex < audioData.size) audioData[sampleIndex] else 0
                }
                
                // Apply window function (Hanning window)
                val windowedData = applyHanningWindow(window)
                
                // Perform FFT (simplified version)
                val fftResult = performSimpleFFT(windowedData)
                
                // Map FFT results to frequency bins
                for (freqIndex in 0 until height) {
                    val fftIndex = (freqIndex * fftSize / height).coerceAtMost(fftSize / 2 - 1)
                    val magnitude = sqrt(fftResult[fftIndex * 2].toDouble().pow(2) + fftResult[fftIndex * 2 + 1].toDouble().pow(2)).toFloat()
                    spectrogramData[height - 1 - freqIndex][timeIndex] = magnitude
                }
            }
            
            // Normalize and create bitmap
            val maxMagnitude = spectrogramData.maxOfOrNull { row -> row.maxOrNull() ?: 0f } ?: 1f
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val normalizedMagnitude = (spectrogramData[y][x] / maxMagnitude).coerceIn(0f, 1f)
                    val color = magnitudeToColor(normalizedMagnitude)
                    bitmap.setPixel(x, y, color)
                }
            }
            
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Error generating spectrogram from audio data", e)
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
        // Simplified FFT implementation
        val n = data.size
        val result = FloatArray(n * 2) // Real and imaginary parts
        
        // Copy real data
        for (i in 0 until n) {
            result[i * 2] = data[i]
            result[i * 2 + 1] = 0f
        }
        
        // Simple FFT implementation (not optimized, but functional)
        fft(result, n)
        
        return result
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
    
    private fun magnitudeToColor(magnitude: Float): Int {
        // Convert magnitude to color using a colormap
        // Use a logarithmic scale for better visualization
        val logMagnitude = if (magnitude > 0) log10(magnitude + 1e-10f) else -10f
        val normalizedLog = ((logMagnitude + 10f) / 10f).coerceIn(0f, 1f)
        
        // Create a more sophisticated colormap (similar to professional audio software)
        // Black -> Blue -> Green -> Yellow -> Red -> White
        val intensity = (normalizedLog * 255).toInt().coerceIn(0, 255)
        
        val (red, green, blue) = when {
            intensity < 51 -> {
                // Black to Blue (0-50)
                val t = intensity / 51f
                Triple(0, 0, (t * 255).toInt())
            }
            intensity < 102 -> {
                // Blue to Green (51-101)
                val t = (intensity - 51) / 51f
                Triple(0, (t * 255).toInt(), ((1 - t) * 255).toInt())
            }
            intensity < 153 -> {
                // Green to Yellow (102-152)
                val t = (intensity - 102) / 51f
                Triple((t * 255).toInt(), 255, 0)
            }
            intensity < 204 -> {
                // Yellow to Red (153-203)
                val t = (intensity - 153) / 51f
                Triple(255, ((1 - t) * 255).toInt(), 0)
            }
            else -> {
                // Red to White (204-255)
                val t = (intensity - 204) / 51f
                Triple(255, (t * 255).toInt(), (t * 255).toInt())
            }
        }
        
        return Color.argb(255, red, green, blue)
    }
    
    // Duration extraction (simplified)
    suspend fun extractDuration(musicFile: MusicFile): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val uri = musicFile.uri ?: return@withContext 0L
            
            // Try MediaMetadataRetriever first
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                CrashLogger.log("AudioManager", "MediaMetadataRetriever failed for duration", e)
                0L
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            CrashLogger.log("AudioManager", "Duration extraction failed", e)
            0L
        }
    }
}
