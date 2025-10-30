package com.example.mobiledigger.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs

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
        private const val DECODE_TIMEOUT_US = 50000L // 50ms timeout for decode operations
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
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        
        try {
            CrashLogger.log(TAG, "Starting waveform generation for URI: $uri")
            
            // Check if this is an AIFF file - MediaExtractor doesn't support AIFF at all
            val isAiff = fileName.lowercase().let { it.endsWith(".aif") || it.endsWith(".aiff") }
            
            if (isAiff) {
                CrashLogger.log(TAG, "⚠️ AIFF format - Android MediaExtractor doesn't support AIFF")
                CrashLogger.log(TAG, "Skipping waveform generation for AIFF (use pastel background instead)")
                return@withContext null
            }
            
            // Set data source for supported formats
            extractor.setDataSource(context, uri, null)
            
            // Find audio track
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                CrashLogger.log(TAG, "No audio track found in file")
                return@withContext null
            }
            
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: run {
                CrashLogger.log(TAG, "Could not determine MIME type")
                return@withContext null
            }
            
            // Get duration for logging
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val durationSeconds = (durationUs / 1_000_000).toInt()
            
            CrashLogger.log(TAG, "Audio format: $mimeType, Duration: ${durationSeconds}s, Target samples: $targetSampleCount")
            
            // Create and configure codec
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(format, null, null, 0)
            codec.start()
            
            // Extract PCM data with target sample count
            val pcmData = extractPCMData(extractor, codec, targetSampleCount)
            
            if (pcmData.isEmpty()) {
                CrashLogger.log(TAG, "No PCM data extracted")
                return@withContext null
            }
            
            CrashLogger.log(TAG, "Extracted ${pcmData.size} PCM samples")
            
            // Downsample to target sample count (should already be close)
            val waveform = downsampleToWaveform(pcmData, targetSampleCount)
            
            CrashLogger.log(TAG, "Generated waveform with ${waveform.size} samples")
            
            waveform
            
        } catch (e: Exception) {
            CrashLogger.log(TAG, "Error generating waveform", e)
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                CrashLogger.log(TAG, "Error releasing codec", e)
            }
            
            try {
                extractor.release()
            } catch (e: Exception) {
                CrashLogger.log(TAG, "Error releasing extractor", e)
            }
        }
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
    private fun extractPCMData(extractor: MediaExtractor, codec: MediaCodec, targetSamples: Int): FloatArray {
        val pcmSamples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        
        // Get total duration
        val format = extractor.getTrackFormat(0)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        
        // Calculate time interval between samples
        val intervalUs = if (targetSamples > 0) durationUs / targetSamples else durationUs
        
        CrashLogger.log(TAG, "⚡ FAST MODE: Seeking to $targetSamples positions (interval: ${intervalUs / 1000}ms)")
        
        var samplesCollected = 0
        var currentTimeUs = 0L
        
        // Sample at specific time positions instead of processing all frames
        while (samplesCollected < targetSamples && currentTimeUs < durationUs) {
            try {
                // Seek to specific timestamp
                extractor.seekTo(currentTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                
                // Feed one frame to decoder
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize > 0) {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            
                            // Get decoded output with appropriate timeout
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
                                // Format changed, retry
                                codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                            }
                        }
                    }
                }
                
                // Move to next sample position
                currentTimeUs += intervalUs
                
            } catch (e: Exception) {
                CrashLogger.log(TAG, "Error sampling at ${currentTimeUs}us", e)
                currentTimeUs += intervalUs
            }
        }
        
        CrashLogger.log(TAG, "⚡ FAST extraction complete: collected $samplesCollected samples by seeking")
        return pcmSamples.toFloatArray()
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
}

