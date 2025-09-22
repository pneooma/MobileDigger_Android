package com.example.mobiledigger.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Memory-optimized waveform generator that prevents OutOfMemoryError crashes.
 * Uses minimal memory allocation and simple sampling patterns.
 */
object LightweightWaveformGenerator {

    /**
     * Generates waveform data with strict memory limits to prevent crashes.
     */
    suspend fun generateFromUri(
        context: Context,
        uri: Uri,
        barsCount: Int = 30
    ): IntArray = withContext(Dispatchers.IO) {
        
        // Strict memory safety limits
        val effectiveBarsCount = minOf(barsCount, 30) // Maximum 30 bars
        
        // Check available memory before proceeding
        val runtime = Runtime.getRuntime()
        val availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
        
        CrashLogger.log("LightweightWaveformGenerator", "Available memory: ${availableMemoryMB}MB, generating $effectiveBarsCount bars")
        
        if (availableMemoryMB < 30) {
            CrashLogger.log("LightweightWaveformGenerator", "Low memory detected ($availableMemoryMB MB), returning simple pattern")
            return@withContext createSimplePattern(effectiveBarsCount)
        }
        
        val waveform = IntArray(effectiveBarsCount)
        var extractor: MediaExtractor? = null
        
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                CrashLogger.log("LightweightWaveformGenerator", "No audio track found for URI: $uri")
                return@withContext createSimplePattern(effectiveBarsCount)
            }
            
            extractor.selectTrack(trackIndex)
            
            // Get basic format info
            val format = extractor.getTrackFormat(trackIndex)
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            
            // Generate lightweight waveform with memory safety
            val amplitudes = generateLightweightAmplitudes(extractor, sampleRate, channelCount, effectiveBarsCount)
            
            if (amplitudes.isEmpty()) {
                return@withContext createSimplePattern(effectiveBarsCount)
            }
            
            // Scale to reasonable display values (0-100)
            val maxAmplitude = amplitudes.maxOrNull() ?: 1f
            for (i in amplitudes.indices) {
                val normalizedValue = (amplitudes[i] / maxAmplitude * 80).toInt()
                waveform[i] = normalizedValue.coerceIn(10, 100)
            }
            
            CrashLogger.log("LightweightWaveformGenerator", "Successfully generated waveform with ${waveform.size} bars")
            return@withContext waveform

        } catch (e: OutOfMemoryError) {
            CrashLogger.log("LightweightWaveformGenerator", "OutOfMemoryError caught, returning simple pattern", e)
            return@withContext createSimplePattern(effectiveBarsCount)
        } catch (e: Exception) {
            CrashLogger.log("LightweightWaveformGenerator", "Exception during waveform generation", e)
            return@withContext createSimplePattern(effectiveBarsCount)
        } finally {
            extractor?.release()
        }
    }

    private fun generateLightweightAmplitudes(
        extractor: MediaExtractor,
        sampleRate: Int,
        channelCount: Int,
        barsCount: Int
    ): FloatArray {
        val amplitudes = FloatArray(barsCount)
        
        try {
            // Use very small buffer to minimize memory usage
            val bufferSize = 4096 // 4KB buffer only
            val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
            
            var barIndex = 0
            var samplesInCurrentBar = 0
            var currentBarSum = 0f
            val samplesPerBar = 1000 // Fixed number of samples per bar for consistency
            
            while (barIndex < barsCount) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                
                buffer.clear()
                val bytesRead = extractor.readSampleData(buffer, 0)
                if (bytesRead <= 0) break
                
                buffer.flip()
                
                // Process samples with minimal memory allocation
                val samplesInBuffer = bytesRead / (2 * channelCount) // 16-bit samples
                var bufferSum = 0f
                var sampleCount = 0
                
                for (i in 0 until minOf(samplesInBuffer, 512)) { // Process max 512 samples
                    if (buffer.remaining() >= 2) {
                        val sample = buffer.short.toFloat()
                        bufferSum += abs(sample)
                        sampleCount++
                    }
                }
                
                if (sampleCount > 0) {
                    val bufferAvg = bufferSum / sampleCount
                    currentBarSum += bufferAvg
                    samplesInCurrentBar += sampleCount
                    
                    // Move to next bar when we have enough samples
                    if (samplesInCurrentBar >= samplesPerBar) {
                        amplitudes[barIndex] = currentBarSum / samplesInCurrentBar
                        barIndex++
                        currentBarSum = 0f
                        samplesInCurrentBar = 0
                    }
                }
                
                // Skip ahead to avoid processing too much data
                for (skip in 0 until 5) {
                    if (!extractor.advance()) break
                }
            }
            
            // Fill remaining bars if needed
            if (barIndex > 0 && barIndex < barsCount) {
                val avgAmplitude = amplitudes.take(barIndex).average().toFloat()
                for (i in barIndex until barsCount) {
                    amplitudes[i] = avgAmplitude * (0.8f + (i % 3) * 0.1f) // Slight variation
                }
            }
            
        } catch (e: Exception) {
            CrashLogger.log("LightweightWaveformGenerator", "Error in generateLightweightAmplitudes", e)
            return FloatArray(0)
        }
        
        return amplitudes
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    private fun createSimplePattern(barsCount: Int): IntArray {
        return IntArray(barsCount) { index ->
            when (index % 4) {
                0 -> 70
                1 -> 45
                2 -> 80
                3 -> 35
                else -> 50
            }
        }
    }
}
