package com.example.mobiledigger.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * Custom waveform generator using Android's MediaExtractor and MediaCodec APIs.
 * Pure Kotlin implementation - no native FFmpeg dependencies.
 * 
 * This class extracts PCM audio data from any audio file format and samples
 * amplitude values at regular intervals to generate a waveform visualization.
 */
class WaveformGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "WaveformGenerator"
        private const val TIMEOUT_US = 5000L // 5ms timeout for dequeue operations
        private const val DECODE_TIMEOUT_US = 400000L // 400ms timeout for decode operations (increased for better quality)
        private const val PARALLEL_THREADS = 6 // Number of parallel processing threads (increased from 4 for better performance)
        private const val USE_PARALLEL_PROCESSING = true // Toggle for parallel vs sequential
    }
    
    /**
     * Generate waveform data from an audio file URI.
     * 
     * @param uri The URI of the audio file
     * @param targetSampleCount Fixed number of samples to generate (default: 100 for speed)
     * @param fileName The file name (used to detect unsupported formats like AIFF)
     * @return IntArray of amplitude values (0-100), or null if extraction fails
     */
    suspend fun generateWaveform(uri: Uri, targetSampleCount: Int = 100, fileName: String = ""): IntArray? = withContext(Dispatchers.IO) {
        // PHASE 5: Profile waveform generation
        val startTime = android.os.SystemClock.elapsedRealtime()
        val result = try {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            
            try {
                // Check if this is an AIFF file
                val isAiff = fileName.lowercase().let { it.endsWith(".aif") || it.endsWith(".aiff") }
                
                if (isAiff) {
                    return@withContext generateAiffWaveform(uri, targetSampleCount)
                }
            
            // Set data source for supported formats
            extractor.setDataSource(context, uri, null)
            
            // Find audio track
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                return@withContext null
            }
            
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            
            // Calculate timeout as half of file duration
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val maxGenerationTimeMs = (durationUs / 2_000).toLong()
            val decodeTimeoutUs = (maxGenerationTimeMs * 1000) / targetSampleCount
            
            // Create and configure codec
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(format, null, null, 0)
            codec.start()
            
            // Extract PCM data with parallel processing
            val pcmData = if (USE_PARALLEL_PROCESSING) {
                try {
                    codec.stop()
                    codec.release()
                } catch (e: Exception) {
                    // Ignore
                }
                codec = null
                extractPCMDataParallel(uri, mimeType, format, targetSampleCount, decodeTimeoutUs)
            } else {
                extractPCMData(extractor, codec, targetSampleCount, decodeTimeoutUs)
            }
            
            if (pcmData.isEmpty()) {
                return@withContext null
            }
            
            // Downsample and apply smoothing
            val waveform = downsampleToWaveform(pcmData, targetSampleCount)
            applySmoothingAndCompression(waveform)
            
            } catch (e: Exception) {
                CrashLogger.log(TAG, "Error generating waveform", e)
                null
            } finally {
                try {
                    codec?.stop()
                    codec?.release()
                    extractor.release()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        } finally {
            // PHASE 5: Log generation time
            val duration = android.os.SystemClock.elapsedRealtime() - startTime
            com.example.mobiledigger.util.PerformanceProfiler.recordOperation("WaveformGen", duration)
            if (duration > 1000) {
                CrashLogger.log(TAG, "⚠️ SLOW waveform generation: ${duration}ms for $fileName")
            }
        }
        return@withContext result
    }
    
    /**
     * Find the first audio track in the media file.
     */
    private fun findAudioTrack(extractor: MediaExtractor): Int {
        val trackCount = extractor.trackCount
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Extract raw PCM audio data using MediaCodec.
     * Uses SEEK-BASED sampling for MAXIMUM SPEED - seeks to specific timestamps instead of processing all frames.
     */
    private fun extractPCMData(extractor: MediaExtractor, codec: MediaCodec, targetSamples: Int, decodeTimeoutUs: Long = DECODE_TIMEOUT_US): FloatArray {
        val pcmSamples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        
        // Get total duration
        val format = extractor.getTrackFormat(0)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        
        // Calculate time interval between samples
        val intervalUs = if (targetSamples > 0) durationUs / targetSamples else durationUs
        
        var samplesCollected = 0
        var currentTimeUs = 0L
        
        // Sample at specific time positions instead of processing all frames
        while (samplesCollected < targetSamples && currentTimeUs < durationUs) {
            try {
                extractor.seekTo(currentTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        
                        if (sampleSize > 0) {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            
                            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, decodeTimeoutUs)
                            
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                
                                if (outputBuffer != null && bufferInfo.size > 0) {
                                    val samples = extractSamplesFromBuffer(outputBuffer, bufferInfo)
                                    if (samples.isNotEmpty()) {
                                        pcmSamples.add(samples.maxOrNull() ?: 0f)
                                        samplesCollected++
                                    }
                                }
                                
                                codec.releaseOutputBuffer(outputBufferIndex, false)
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                            }
                        }
                    }
                }
                
                currentTimeUs += intervalUs
                
            } catch (e: Exception) {
                currentTimeUs += intervalUs
            }
        }
        
        return pcmSamples.toFloatArray()
    }
    
    /**
     * Extract PCM data using PARALLEL processing across multiple threads.
     * Divides the audio file into segments and processes each segment concurrently.
     */
    private suspend fun extractPCMDataParallel(
        uri: Uri,
        mimeType: String,
        format: MediaFormat,
        targetSamples: Int,
        decodeTimeoutUs: Long = DECODE_TIMEOUT_US
    ): FloatArray = withContext(Dispatchers.IO) {
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val samplesPerThread = targetSamples / PARALLEL_THREADS
        
        // Create parallel jobs for each segment
        val parallelJobs = (0 until PARALLEL_THREADS).map { threadIndex ->
            async(Dispatchers.IO) {
                // Calculate this thread's time segment
                val segmentStartUs = (durationUs / PARALLEL_THREADS) * threadIndex
                val segmentEndUs = if (threadIndex == PARALLEL_THREADS - 1) {
                    durationUs // Last thread goes to the end
                } else {
                    (durationUs / PARALLEL_THREADS) * (threadIndex + 1)
                }
                val segmentDurationUs = segmentEndUs - segmentStartUs
                
                var extractor: MediaExtractor? = null
                var codec: MediaCodec? = null
                
                try {
                    // Each thread gets its own extractor and codec
                    extractor = MediaExtractor()
                    extractor.setDataSource(context, uri, null)
                    val audioTrackIndex = findAudioTrack(extractor)
                    if (audioTrackIndex < 0) {
                        return@async emptyList<Float>()
                    }
                    extractor.selectTrack(audioTrackIndex)
                    
                    codec = MediaCodec.createDecoderByType(mimeType)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    
                    // Extract samples for this segment
                    extractSegmentPCMData(
                        extractor, 
                        codec, 
                        segmentStartUs, 
                        segmentEndUs, 
                        samplesPerThread,
                        threadIndex,
                        decodeTimeoutUs
                    )
                    
                } catch (e: Exception) {
                    emptyList<Float>()
                } finally {
                    try {
                        codec?.stop()
                        codec?.release()
                        extractor?.release()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
        }
        
        // Wait for all threads to complete and combine results
        val allSegments = parallelJobs.awaitAll()
        val combinedSamples = mutableListOf<Float>()
        allSegments.forEach { segment ->
            combinedSamples.addAll(segment)
        }
        
        combinedSamples.toFloatArray()
    }
    
    /**
     * Extract PCM data for a specific time segment (used by parallel processing).
     */
    private fun extractSegmentPCMData(
        extractor: MediaExtractor,
        codec: MediaCodec,
        segmentStartUs: Long,
        segmentEndUs: Long,
        targetSamples: Int,
        threadIndex: Int,
        decodeTimeoutUs: Long = DECODE_TIMEOUT_US
    ): List<Float> {
        val pcmSamples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        
        val segmentDurationUs = segmentEndUs - segmentStartUs
        val intervalUs = if (targetSamples > 0) segmentDurationUs / targetSamples else segmentDurationUs
        
        var samplesCollected = 0
        var currentTimeUs = segmentStartUs
        
        // Profiling for this segment
        var totalSeekTime = 0L
        var totalDecodeTime = 0L
        
        while (samplesCollected < targetSamples && currentTimeUs < segmentEndUs) {
            try {
                // Seek to position
                val seekStart = System.nanoTime()
                extractor.seekTo(currentTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                totalSeekTime += (System.nanoTime() - seekStart) / 1_000_000
                
                // Decode one frame
                val decodeStart = System.nanoTime()
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize > 0) {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            
                            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                            
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                
                                if (outputBuffer != null && bufferInfo.size > 0) {
                                    val samples = extractSamplesFromBuffer(outputBuffer, bufferInfo)
                                    if (samples.isNotEmpty()) {
                                        pcmSamples.add(samples.maxOrNull() ?: 0f)
                                        samplesCollected++
                                    }
                                }
                                
                                codec.releaseOutputBuffer(outputBufferIndex, false)
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                            }
                        }
                    }
                }
                totalDecodeTime += (System.nanoTime() - decodeStart) / 1_000_000
                
                currentTimeUs += intervalUs
                
            } catch (e: Exception) {
                currentTimeUs += intervalUs
            }
        }
        
        
        return pcmSamples
    }
    
    /**
     * Extract amplitude samples from a PCM buffer.
     * Assumes 16-bit PCM audio (most common format).
     */
    private fun extractSamplesFromBuffer(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): List<Float> {
        val samples = mutableListOf<Float>()
        
        buffer.position(bufferInfo.offset)
        buffer.limit(bufferInfo.offset + bufferInfo.size)
        
        // Read 16-bit PCM samples
        while (buffer.remaining() >= 2) {
            val sample = buffer.short.toFloat() / Short.MAX_VALUE // Normalize to -1.0 to 1.0
            samples.add(abs(sample)) // Use absolute value for amplitude
        }
        
        return samples
    }
    
    /**
     * Downsample the PCM data to a fixed number of amplitude values.
     * This groups samples into buckets and takes the maximum amplitude in each bucket.
     */
    private fun downsampleToWaveform(pcmData: FloatArray, targetSamples: Int): IntArray {
        if (pcmData.isEmpty()) return IntArray(0)
        if (targetSamples <= 0) return IntArray(0)
        
        val waveform = IntArray(targetSamples)
        val samplesPerBucket = pcmData.size / targetSamples
        
        if (samplesPerBucket == 0) {
            // Less PCM data than target samples, just copy what we have
            pcmData.forEachIndexed { index, sample ->
                if (index < targetSamples) {
                    waveform[index] = (sample * 100).toInt().coerceIn(0, 100)
                }
            }
            return waveform
        }
        
        // First pass: collect raw amplitudes
        val rawAmplitudes = FloatArray(targetSamples)
        var globalMax = 0f
        
        for (i in 0 until targetSamples) {
            val startIdx = i * samplesPerBucket
            val endIdx = minOf(startIdx + samplesPerBucket, pcmData.size)
            
            // Find max amplitude in this bucket
            var maxAmplitude = 0f
            for (j in startIdx until endIdx) {
                if (pcmData[j] > maxAmplitude) {
                    maxAmplitude = pcmData[j]
                }
            }
            
            rawAmplitudes[i] = maxAmplitude
            if (maxAmplitude > globalMax) {
                globalMax = maxAmplitude
            }
        }
        
        // Second pass: normalize to prevent overflow/clipping
        // Scale so the max value is 85 instead of 100, leaving headroom
        val targetMax = 85f
        val scaleFactor = if (globalMax > 0f) targetMax / globalMax else 1f
        
        for (i in 0 until targetSamples) {
            waveform[i] = (rawAmplitudes[i] * scaleFactor).toInt().coerceIn(0, 100)
        }
        
        return waveform
    }
    
    /**
     * Generate waveform for AIFF files using libvlc decoder.
     * AIFF is not supported by Android's MediaExtractor/MediaCodec.
     */
    private suspend fun generateAiffWaveform(uri: Uri, targetSampleCount: Int): IntArray? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        
        try {
            tempFile = copyUriToTempFile(uri)
            if (tempFile == null) {
                return@withContext null
            }
            
            return@withContext generateSimplifiedAiffWaveform(tempFile, targetSampleCount)
            
        } catch (e: Exception) {
            CrashLogger.log(TAG, "Error generating AIFF waveform", e)
            null
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Generate a simplified waveform by sampling the AIFF file at regular intervals.
     * This properly parses the AIFF file structure to find the SSND (Sound Data) chunk.
     */
    private fun generateSimplifiedAiffWaveform(file: File, targetSampleCount: Int): IntArray {
        var waveform = IntArray(targetSampleCount) { 50 } // Default mid-level
        
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(12)
                
                // Read FORM chunk header (12 bytes): "FORM" + size + "AIFF"
                if (fis.read(buffer, 0, 12) != 12) {
                    return waveform
                }
                
                val formType = String(buffer, 0, 4)
                val fileType = String(buffer, 8, 4)
                
                if (formType != "FORM" || (fileType != "AIFF" && fileType != "AIFC")) {
                    return waveform
                }
                
                // Find SSND (Sound Data) chunk
                var ssndOffset = -1L
                var ssndSize = 0L
                var numChannels = 2
                var bitsPerSample = 16
                
                while (true) {
                    val chunkHeader = ByteArray(8)
                    val bytesRead = fis.read(chunkHeader, 0, 8)
                    if (bytesRead != 8) break
                    
                    val chunkType = String(chunkHeader, 0, 4)
                    // Big-endian chunk size
                    val chunkSize = ((chunkHeader[4].toInt() and 0xFF) shl 24) or
                                   ((chunkHeader[5].toInt() and 0xFF) shl 16) or
                                   ((chunkHeader[6].toInt() and 0xFF) shl 8) or
                                   (chunkHeader[7].toInt() and 0xFF)
                    
                    when (chunkType) {
                        "COMM" -> {
                            // Read COMM chunk for format info
                            val commData = ByteArray(18)
                            fis.read(commData, 0, 18)
                            numChannels = ((commData[0].toInt() and 0xFF) shl 8) or (commData[1].toInt() and 0xFF)
                            bitsPerSample = ((commData[6].toInt() and 0xFF) shl 8) or (commData[7].toInt() and 0xFF)
                            // Skip rest of COMM chunk
                            fis.skip((chunkSize - 18).toLong())
                        }
                        "SSND" -> {
                            // SSND chunk has 8 bytes: offset + blockSize, then audio data
                            ssndOffset = fis.channel.position() + 8 // Skip offset/blockSize fields
                            ssndSize = (chunkSize - 8).toLong()
                            break
                        }
                        else -> {
                            // Skip unknown chunk
                            fis.skip(chunkSize.toLong())
                        }
                    }
                }
                
                if (ssndOffset < 0 || ssndSize <= 0) {
                    return waveform
                }
                
                // Now sample the audio data
                val bytesPerSample = bitsPerSample / 8
                val frameSize = numChannels * bytesPerSample
                val sampleBuffer = ByteArray(8192)
                
                for (i in 0 until targetSampleCount) {
                    val samplePosition = ssndOffset + (i * ssndSize / targetSampleCount)
                    fis.channel.position(samplePosition)
                    
                    val bytesRead = fis.read(sampleBuffer, 0, minOf(sampleBuffer.size, (ssndSize - (samplePosition - ssndOffset)).toInt()))
                    
                    if (bytesRead > 0) {
                        var maxPeak = 0
                        
                        var j = 0
                        while (j < bytesRead - 1) {
                            // Read 16-bit big-endian sample
                            val high = sampleBuffer[j].toInt() and 0xFF
                            val low = sampleBuffer[j + 1].toInt() and 0xFF
                            val sample = (high shl 8) or low
                            // Convert to signed
                            val signedSample = if (sample > 32767) sample - 65536 else sample
                            
                            // Track peak (max absolute value) - standard for waveform display
                            val absSample = kotlin.math.abs(signedSample)
                            if (absSample > maxPeak) {
                                maxPeak = absSample
                            }
                            
                            j += frameSize // Jump to next frame (skip other channels)
                        }
                        
                        // Normalize peak to 0-100 range
                        val normalizedPeak = maxPeak / 32768.0
                        val amplitude = (normalizedPeak * 100.0).toInt().coerceIn(5, 100)
                        waveform[i] = amplitude
                    }
                }
                
                // Apply smoothing and compression for better visual quality
                waveform = applySmoothingAndCompression(waveform)
            }
            
        } catch (e: Exception) {
            CrashLogger.log(TAG, "Error reading AIFF file for waveform", e)
        }
        
        return waveform
    }
    
    /**
     * Apply smoothing and dynamic range compression to waveform for better visual quality
     */
    private fun applySmoothingAndCompression(waveform: IntArray): IntArray {
        if (waveform.isEmpty()) return waveform
        
        val targetSampleCount = waveform.size
        
        // Step 1: Apply smoothing filter (3-point moving average) for better visual quality
        val smoothed = IntArray(targetSampleCount)
        for (i in waveform.indices) {
            val prev = if (i > 0) waveform[i - 1] else waveform[i]
            val next = if (i < waveform.size - 1) waveform[i + 1] else waveform[i]
            smoothed[i] = ((prev + waveform[i] * 2 + next) / 4.0).toInt()
        }
        
        // Step 2: Compress dynamic range to reduce extremes (30-85 range for smoother look)
        val minVal = smoothed.minOrNull() ?: 5
        val maxVal = smoothed.maxOrNull() ?: 100
        val range = maxVal - minVal
        
        if (range > 5) {
            // Compress to 30-85 range (55-point range instead of 90)
            for (i in smoothed.indices) {
                val normalized = ((smoothed[i] - minVal).toFloat() / range * 55f) + 30f
                smoothed[i] = normalized.toInt().coerceIn(30, 85)
            }
        }
        
        return smoothed
    }
    
    /**
     * Copy URI to a temporary file (needed for libvlc)
     */
    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile("aiff_temp_", ".aiff", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}

