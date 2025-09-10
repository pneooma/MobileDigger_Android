package com.example.mobiledigger.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.mobiledigger.model.WaveformSettings
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object WaveformGenerator {

    // Current settings - can be updated in real-time
    private var currentSettings = WaveformSettings.DEFAULT

    /**
     * Update waveform generation settings in real-time
     */
    fun updateSettings(settings: WaveformSettings) {
        currentSettings = settings
        println("🎛️ WaveformGenerator: Settings updated - Bars: ${settings.barCount}, Oversample: ${settings.oversampleFactor}, Frame: ${settings.frameDurationMs}ms")
    }

    /**
     * Get current waveform settings
     */
    fun getCurrentSettings(): WaveformSettings = currentSettings

    /**
     * Generates waveform data from a URI using seewav-inspired approach.
     * Combines frame-based processing with oversampling for more reactive waveforms.
     */
    suspend fun generateFromUri(
        context: Context,
        uri: Uri,
        barsCount: Int = currentSettings.barCount
    ): IntArray = withContext(Dispatchers.IO) {
        
        // Apply render limit for performance (React Native Audio Waveform approach)
        val effectiveBarsCount = minOf(barsCount, currentSettings.maxBarsToRender)
        
        println("🎵 WaveformGenerator: Starting iOS-inspired generation from URI: $uri")
        println("📊 Render settings: barsCount=$barsCount, effectiveBarsCount=$effectiveBarsCount, maxBarsToRender=${currentSettings.maxBarsToRender}")
        val waveform = IntArray(effectiveBarsCount)
        val extractor = MediaExtractor()
        
        try {
            // Use the same approach as spectrogram generation
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                println("❌ No audio track found for URI: $uri")
                CrashLogger.log("WaveformGenerator", "No audio track found for URI: $uri")
                return@withContext waveform
            }
            extractor.selectTrack(trackIndex)
            
            // Log audio format info
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0
            
            println("🎵 Audio format - MIME: $mime, Sample Rate: $sampleRate, Channels: $channelCount, Duration: $duration")
            println("🎵 File type detection - URI: $uri")
            val uriString = uri.toString().lowercase()
            when {
                uriString.contains(".mp3") -> println("🎵 Detected MP3 file")
                uriString.contains(".wav") -> println("🎵 Detected WAV file")
                uriString.contains(".aif") -> println("🎵 Detected AIFF file")
                uriString.contains(".flac") -> println("🎵 Detected FLAC file")
                else -> println("🎵 Unknown file type")
            }

            // Try iOS-inspired frame-based processing first
            var frameAmplitudes = generateFrameBasedWaveform(extractor, sampleRate, channelCount, effectiveBarsCount)
            
            // If no data generated, try fallback chunk-based approach
            if (frameAmplitudes.isEmpty()) {
                println("⚠️ iOS approach failed, trying fallback chunk-based approach")
                frameAmplitudes = generateChunkBasedWaveform(extractor, sampleRate, channelCount, effectiveBarsCount)
            }
            
            if (frameAmplitudes.isEmpty()) {
                CrashLogger.log("WaveformGenerator", "No amplitude data generated for URI: $uri with any approach")
                return@withContext waveform
            }
            
            // Find the actual range of RMS values for proper scaling
            val minRms = frameAmplitudes.minOrNull() ?: 0f
            val maxRms = frameAmplitudes.maxOrNull() ?: 1f
            val rmsRange = maxRms - minRms
            
            println("📊 RMS range: min=$minRms, max=$maxRms, range=$rmsRange")
            
            // Scale RMS values to 0-100 range with proper normalization
            val waveformIntArray = if (rmsRange > 0.001f) {
                frameAmplitudes.map { amplitude ->
                    // Normalize to 0-1 range, then scale to 0-100
                    val normalized = (amplitude - minRms) / rmsRange
                    (normalized * 100).toInt().coerceIn(0, 100)
                }.toIntArray()
            } else {
                // If all values are similar, create a subtle variation
                frameAmplitudes.map { amplitude ->
                    // Use a small variation based on the actual value
                    val baseValue = (amplitude * 50).toInt().coerceIn(10, 90)
                    baseValue
                }.toIntArray()
            }
            
            println("✅ iOS-style waveform generated: ${waveformIntArray.size} bars from ${frameAmplitudes.size} points")
            println("🎵 Raw RMS values (first 10): ${frameAmplitudes.take(10).joinToString()}")
            println("🎵 Scaled values (first 10): ${waveformIntArray.take(10).joinToString()}")
            println("📊 Using iOS approach - samplesPerPoint calculation, no oversampling")
            println("🎛️ Using settings - Bars: ${currentSettings.barCount}, Buffer: ${currentSettings.bufferSize}, MinAmplitude: ${currentSettings.minAmplitude}")
            
            return@withContext waveformIntArray

        } catch (e: Exception) {
            CrashLogger.log("WaveformGenerator", "Exception during waveform generation from URI: $uri", e)
            println("❌ Waveform generation failed for URI: $uri: ${e.message}")
            return@withContext waveform
        } finally {
            extractor.release()
        }
    }
    
    /**
     * iOS-inspired waveform generation.
     * Uses the exact same approach as iOS: samplesPerPoint calculation and direct RMS processing.
     */
    private fun generateFrameBasedWaveform(
        extractor: MediaExtractor,
        sampleRate: Int,
        channelCount: Int,
        targetBars: Int
    ): List<Float> {
        val waveformPoints = mutableListOf<Float>()
        
        // iOS approach: Calculate samplesPerPoint = max(1, frameCount / targetPoints)
        // We need to estimate total frame count first
        val estimatedFrameCount = estimateTotalFrames(extractor, sampleRate, channelCount)
        val samplesPerPoint = maxOf(1, (estimatedFrameCount / targetBars).toInt())
        
        println("📊 iOS-style calculation: frameCount=$estimatedFrameCount, samplesPerPoint=$samplesPerPoint, targetBars=$targetBars")
        
        // Reset extractor to beginning
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        println("📊 Extractor reset to beginning")
        
        val buffer = ByteBuffer.allocateDirect(currentSettings.bufferSize).order(ByteOrder.nativeOrder())
        
        for (i in 0 until targetBars) {
            val startFrame = i * samplesPerPoint
            
            // Seek to the start frame position
            val seekTimeUs = (startFrame * 1_000_000L) / (sampleRate * channelCount)
            extractor.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            // Read samples for this point
            val pointAmplitude = readSamplesForPoint(extractor, buffer, samplesPerPoint)
            waveformPoints.add(pointAmplitude)
        }
        
        println("📊 Generated ${waveformPoints.size} waveform points using iOS approach")
        return waveformPoints
    }
    
    /**
     * Fallback chunk-based waveform generation for formats that don't work with seek-based approach
     */
    private fun generateChunkBasedWaveform(
        extractor: MediaExtractor,
        sampleRate: Int,
        channelCount: Int,
        targetBars: Int
    ): List<Float> {
        println("📊 Using fallback chunk-based approach")
        
        val waveformPoints = mutableListOf<Float>()
        val buffer = ByteBuffer.allocateDirect(currentSettings.bufferSize).order(ByteOrder.nativeOrder())
        
        // Reset extractor to beginning
        try {
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        } catch (e: Exception) {
            println("⚠️ Cannot seek to beginning: ${e.message}")
            return emptyList()
        }
        
        var totalSamplesRead = 0
        var currentBarSamples = 0
        var sumOfSquares = 0.0
        val samplesPerBar = (sampleRate * channelCount) / targetBars // More accurate calculation
        
        println("📊 Fallback: samplesPerBar=$samplesPerBar, sampleRate=$sampleRate, channels=$channelCount")
        
        var consecutiveFailures = 0
        val maxFailures = 10
        
        while (waveformPoints.size < targetBars && consecutiveFailures < maxFailures) {
            buffer.clear()
            val sampleSize = try {
                extractor.readSampleData(buffer, 0)
            } catch (e: IllegalArgumentException) {
                println("⚠️ IllegalArgumentException in fallback approach: ${e.message}")
                consecutiveFailures++
                if (!extractor.advance()) break
                continue
            } catch (e: Exception) {
                println("⚠️ Other exception in fallback approach: ${e.message}")
                consecutiveFailures++
                break
            }
            
            if (sampleSize <= 0) {
                consecutiveFailures++
                if (!extractor.advance()) break
                continue
            }
            
            consecutiveFailures = 0 // Reset on successful read
            buffer.position(0)
            val shortBuffer = buffer.asShortBuffer()
            val samplesInBuffer = sampleSize / 2
            
            println("📊 Read $samplesInBuffer samples from buffer of size $sampleSize")
            
            // Process samples in this buffer
            for (i in 0 until samplesInBuffer) {
                val sample = shortBuffer.get(i).toDouble()
                sumOfSquares += sample * sample
                currentBarSamples++
                totalSamplesRead++
                
                // When we have enough samples for a bar, calculate RMS and add to waveform
                if (currentBarSamples >= samplesPerBar && waveformPoints.size < targetBars) {
                    val rms = kotlin.math.sqrt(sumOfSquares / currentBarSamples).toFloat()
                    val finalRms = if (rms < 0.001f) currentSettings.minAmplitude else rms
                    waveformPoints.add(finalRms)
                    
                    println("📊 Bar ${waveformPoints.size}: samples=$currentBarSamples, rms=$rms, final=$finalRms")
                    
                    // Reset for next bar
                    sumOfSquares = 0.0
                    currentBarSamples = 0
                }
            }
            
            if (!extractor.advance()) {
                println("📊 Cannot advance extractor, stopping")
                break
            }
        }
        
        // Add final bar if we have remaining samples
        if (currentBarSamples > 0 && waveformPoints.size < targetBars) {
            val rms = kotlin.math.sqrt(sumOfSquares / currentBarSamples).toFloat()
            val finalRms = if (rms < 0.001f) currentSettings.minAmplitude else rms
            waveformPoints.add(finalRms)
            println("📊 Final bar: samples=$currentBarSamples, rms=$rms, final=$finalRms")
        }
        
        println("📊 Fallback approach generated ${waveformPoints.size} points from $totalSamplesRead samples")
        return waveformPoints
    }
    
    /**
     * Estimate total frames in the audio file (iOS approach)
     */
    private fun estimateTotalFrames(extractor: MediaExtractor, sampleRate: Int, channelCount: Int): Long {
        // Try to get duration from format
        val format = extractor.getTrackFormat(0)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            // Estimate based on file size if duration not available
            180_000_000L // Default 3 minutes
        }
        
        return (durationUs * sampleRate * channelCount) / 1_000_000L
    }
    
    /**
     * Read samples for a single waveform point (iOS approach)
     */
    private fun readSamplesForPoint(extractor: MediaExtractor, buffer: ByteBuffer, samplesPerPoint: Int): Float {
        var samplesRead = 0
        var sumOfSquares = 0.0
        var bufferReads = 0
        var consecutiveFailures = 0
        val maxFailures = 3
        
        while (samplesRead < samplesPerPoint && consecutiveFailures < maxFailures) {
            buffer.clear()
            val sampleSize = try {
                extractor.readSampleData(buffer, 0)
            } catch (e: IllegalArgumentException) {
                println("⚠️ IllegalArgumentException in readSampleData: ${e.message}")
                consecutiveFailures++
                if (!extractor.advance()) break
                continue
            } catch (e: Exception) {
                println("⚠️ Other exception in readSampleData: ${e.message}")
                consecutiveFailures++
                break
            }
            
            if (sampleSize <= 0) {
                consecutiveFailures++
                if (bufferReads == 0) {
                    println("⚠️ No sample data read on first attempt")
                }
                if (!extractor.advance()) break
                continue
            }
            
            consecutiveFailures = 0 // Reset on successful read
            bufferReads++
            buffer.position(0)
            val shortBuffer = buffer.asShortBuffer()
            val samplesInBuffer = sampleSize / 2
            
            // Calculate RMS for samples in this buffer
            val samplesToProcess = minOf(samplesInBuffer, samplesPerPoint - samplesRead)
            for (i in 0 until samplesToProcess) {
                val sample = shortBuffer.get(i).toDouble()
                sumOfSquares += sample * sample
            }
            
            samplesRead += samplesToProcess
            
            if (!extractor.advance()) {
                println("⚠️ Cannot advance extractor, stopping at buffer read $bufferReads")
                break
            }
        }
        
        // Calculate RMS: sqrt(sum / number of samples) - exactly like iOS
        val rms = if (samplesRead > 0) {
            kotlin.math.sqrt(sumOfSquares / samplesRead).toFloat()
        } else {
            0f
        }
        
        // Debug logging for first few points
        if (samplesRead > 0) {
            println("📊 Point: samplesRead=$samplesRead, bufferReads=$bufferReads, rms=$rms")
        }
        
        // Apply minimum amplitude only if RMS is very small (to prevent completely flat waveforms)
        return if (rms < 0.001f) {
            currentSettings.minAmplitude
        } else {
            rms
        }
    }
    
    /**
     * Calculate amplitude for a frame using RMS (Root Mean Square).
     * This matches seewav's approach for smooth, reactive waveforms.
     */
    private fun calculateFrameAmplitude(shortBuffer: java.nio.ShortBuffer, sampleCount: Int): Float {
        var sumOfSquares = 0.0
        val actualSampleCount = minOf(sampleCount, shortBuffer.remaining())
        
        for (i in 0 until actualSampleCount) {
            val sample = shortBuffer.get(i).toDouble()
            sumOfSquares += sample * sample
        }
        
        return if (actualSampleCount > 0) {
            kotlin.math.sqrt(sumOfSquares / actualSampleCount).toFloat()
        } else {
            0f
        }
    }
    

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun normalizeWaveform(waveform: IntArray): IntArray {
        val maxAmplitude = waveform.maxOrNull() ?: return waveform
        if (maxAmplitude == 0) return waveform

        val scaledWaveform = IntArray(waveform.size)
        for (i in waveform.indices) {
            // Scale to configured range using current settings (iOS approach)
            val normalizedValue = waveform[i].toFloat() / maxAmplitude
            val scaledValue = (normalizedValue * currentSettings.maxAmplitude * 100).toInt()
            scaledWaveform[i] = maxOf(scaledValue, (currentSettings.minAmplitude * 100).toInt())
        }
        return scaledWaveform
    }
}