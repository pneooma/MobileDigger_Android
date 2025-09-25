package com.example.mobiledigger.ui.components

import androidx.compose.runtime.*
import com.example.mobiledigger.model.MusicFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import linc.com.amplituda.Amplituda
import linc.com.amplituda.AmplitudaResult
import linc.com.amplituda.callback.AmplitudaErrorListener
import linc.com.amplituda.callback.AmplitudaSuccessListener
import linc.com.amplituda.Compress
import java.io.InputStream
import android.content.Context

// Extension function for number formatting
private fun Double.format(digits: Int) = "%.${digits}f".format(this)

// Optimized LRU cache for waveform data with size limit (reduced for memory safety)
private const val MAX_WAVEFORM_CACHE_SIZE = 3 // User preference: keep cache very small
private val waveformCache = java.util.Collections.synchronizedMap(
    object : LinkedHashMap<String, IntArray>(MAX_WAVEFORM_CACHE_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?): Boolean {
            return size > MAX_WAVEFORM_CACHE_SIZE
        }
    }
)

// Shared waveform state that can be used by multiple components
@Composable
fun rememberSharedWaveformState(
    currentFile: MusicFile?,
    context: Context
): SharedWaveformState {
    var waveformData by remember { mutableStateOf<IntArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Generate waveform when file changes
    LaunchedEffect(currentFile?.uri?.toString(), currentFile?.name) {
        // Cancel any ongoing processing for the previous file
        scope.coroutineContext.cancelChildren()
        
        val uriString = currentFile?.uri?.toString()
        println("🔄 SharedWaveformState: LaunchedEffect triggered for file: ${currentFile?.name}")
        println("🔄 URI string: $uriString")
        println("🔄 Previous waveform data: ${waveformData?.size ?: "null"}")
        
        // Always reset state when file changes
        waveformData = null
        errorMessage = null
        isLoading = true
        
        if (currentFile != null) {
            println("🎵 Starting shared waveform generation for: ${currentFile.name}")
            println("🔗 URI: ${currentFile.uri}")
            
            // Create cache key based on URI and file size
            val cacheKey = "${currentFile.uri}_${currentFile.size}"
            
            // Check cache first
            val cachedWaveform = waveformCache[cacheKey]
            if (cachedWaveform != null) {
                println("⚡ Using cached waveform data: ${cachedWaveform.size} samples")
                waveformData = cachedWaveform
                isLoading = false
                return@LaunchedEffect
            }

            try {
                // Process Amplituda on background thread for higher fidelity on small files
                scope.launch(Dispatchers.IO) {
                    try {
                        // Use Amplituda to process the audio with optimized settings
                        val amplituda = Amplituda(context)
                        
                        // Create dynamic compression settings based on file type for optimal performance
                        val fileSizeMB = currentFile.size / (1024.0 * 1024.0)
                        val fileName = currentFile.name.lowercase()
                        val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
                        
                        // AIFF files are uncompressed, so use ultra-minimal samples for maximum speed
                        val samplesPerSecond = if (isAiffFile) 1 else 3
                        val compressSettings = Compress.withParams(Compress.AVERAGE, samplesPerSecond)
                        
                        println("🎵 File: ${currentFile.name}, Size: ${fileSizeMB.format(1)}MB, Type: ${if (isAiffFile) "AIFF" else "Other"}, Samples/sec: $samplesPerSecond")
                        
                        // Try using InputStream first (more compatible with content:// URIs)
                        val inputStream = try {
                            context.contentResolver.openInputStream(currentFile.uri)
                        } catch (e: Exception) {
                            println("❌ Failed to open InputStream from URI: ${e.message}")
                            null
                        }
                        
                        if (inputStream != null) { // High-fidelity for all files
                            println("🔗 Using InputStream for Amplituda processing")
                            
                            // Process audio using InputStream with optimized settings
                            amplituda.processAudio(inputStream, compressSettings)
                                .get(
                                    object : AmplitudaSuccessListener<InputStream> {
                                        override fun onSuccess(result: AmplitudaResult<InputStream>) {
                                            try {
                                                // Convert result to IntArray for WaveformSeekBar
                                                val samples = result.amplitudesAsList()
                                                    .map { amplitude ->
                                                        // Amplituda returns values in 0-100 range, use directly
                                                        amplitude.coerceIn(0, 100)
                                                    }
                                                    .toIntArray()
                                                
                                                // Cache and update only if we improve upon lightweight
                                                waveformCache[cacheKey] = samples
                                                scope.launch(Dispatchers.Main) {
                                                    if (waveformData == null || samples.size > (waveformData?.size ?: 0)) {
                                                        waveformData = samples
                                                    }
                                                    isLoading = false
                                                }
                                                println("✅ Shared waveform generated from InputStream: ${samples.size} samples")
                                                println("🎵 First 10 amplitudes: ${samples.take(10).joinToString()}")
                                                println("🎵 Amplitude range: min=${samples.minOrNull()}, max=${samples.maxOrNull()}")
                                            } finally {
                                                // Close the InputStream
                                                try {
                                                    inputStream.close()
                                                } catch (e: Exception) {
                                                    println("⚠️ Failed to close InputStream: ${e.message}")
                                                }
                                            }
                                        }
                                    },
                                    object : AmplitudaErrorListener {
                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                            println("❌ Amplituda failed with InputStream, trying URI string: ${exception.message}")
                                            
                                            // Close the InputStream
                                            try {
                                                inputStream.close()
                                            } catch (e: Exception) {
                                                println("⚠️ Failed to close InputStream: ${e.message}")
                                            }
                                            
                                            // Try URI string as fallback
                                            val uriString = currentFile.uri.toString()
                                            println("🔗 Trying URI string: $uriString")
                                            
                                            amplituda.processAudio(uriString, compressSettings)
                                                .get(
                                                    object : AmplitudaSuccessListener<String> {
                                                        override fun onSuccess(result: AmplitudaResult<String>) {
                                                            val samples = result.amplitudesAsList()
                                                                .map { amplitude ->
                                                                    amplitude.coerceIn(0, 100)
                                                                }
                                                                .toIntArray()
                                                            
                                                            waveformCache[cacheKey] = samples
                                                            scope.launch(Dispatchers.Main) {
                                                                if (waveformData == null || samples.size > (waveformData?.size ?: 0)) {
                                                                    waveformData = samples
                                                                }
                                                                isLoading = false
                                                            }
                                                            println("✅ Shared waveform generated from URI string: ${samples.size} samples")
                                                        }
                                                    },
                                                    object : AmplitudaErrorListener {
                                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                                            println("❌ Amplituda failed with URI string, trying final fallback: ${exception.message}")
                                                            
                                                            // Final fallback to using the existing WaveformGenerator approach
                                                            scope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val fallbackSamples = com.example.mobiledigger.utils.WaveformGenerator.generateFromUri(
                                                                        context = context,
                                                                        uri = currentFile.uri
                                                                    )
                                                                    
                                                                    // Cache the fallback waveform data
                                                                    waveformCache[cacheKey] = fallbackSamples
                                                                    
                                                                    // Update UI on main thread
                                                                    scope.launch(Dispatchers.Main) {
                                                                        waveformData = fallbackSamples
                                                                        isLoading = false
                                                                    }
                                                                    println("✅ Shared fallback waveform generated: ${fallbackSamples.size} samples")
                                                                    
                                                                } catch (fallbackException: Exception) {
                                                                    println("❌ Shared fallback also failed: ${fallbackException.message}")
                                                                    scope.launch(Dispatchers.Main) {
                                                                        errorMessage = "All waveform generation methods failed: ${exception.message}"
                                                                        isLoading = false
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                        }
                                    }
                                )
                        } else {
                            println("🔗 InputStream not available, trying URI string directly")
                            val uriString = currentFile.uri.toString()
                            println("🔗 Trying URI string: $uriString")
                            
                            amplituda.processAudio(uriString, compressSettings)
                                .get(
                                    object : AmplitudaSuccessListener<String> {
                                        override fun onSuccess(result: AmplitudaResult<String>) {
                                            val samples = result.amplitudesAsList()
                                                .map { amplitude ->
                                                    amplitude.coerceIn(0, 100)
                                                }
                                                .toIntArray()
                                            
                                            waveformCache[cacheKey] = samples
                                            scope.launch(Dispatchers.Main) {
                                                if (waveformData == null || samples.size > (waveformData?.size ?: 0)) {
                                                    waveformData = samples
                                                }
                                                isLoading = false
                                            }
                                            println("✅ Shared waveform generated from URI string: ${samples.size} samples")
                                        }
                                    },
                                    object : AmplitudaErrorListener {
                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                            println("❌ Amplituda failed with URI string, trying final fallback: ${exception.message}")
                                            
                                            // Final fallback to using the existing WaveformGenerator approach
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val fallbackSamples = com.example.mobiledigger.utils.WaveformGenerator.generateFromUri(
                                                        context = context,
                                                        uri = currentFile.uri
                                                    )
                                                    
                                                    // Cache the fallback waveform data
                                                    waveformCache[cacheKey] = fallbackSamples
                                                    
                                                    // Update UI on main thread
                                                    scope.launch(Dispatchers.Main) {
                                                        waveformData = fallbackSamples
                                                        isLoading = false
                                                    }
                                                    println("✅ Shared fallback waveform generated: ${fallbackSamples.size} samples")
                                                    
                                                } catch (fallbackException: OutOfMemoryError) {
                                                    println("⚠️ OutOfMemoryError in fallback - file too large: ${fallbackException.message}")
                                                    scope.launch(Dispatchers.Main) {
                                                        errorMessage = "File too large for waveform generation. Skipping..."
                                                        isLoading = false
                                                    }
                                                    System.gc()
                                                } catch (fallbackException: Exception) {
                                                    println("❌ Shared fallback also failed: ${fallbackException.message}")
                                                    scope.launch(Dispatchers.Main) {
                                                        errorMessage = "All waveform generation methods failed: ${exception.message}"
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                        }
                        
                    } catch (e: OutOfMemoryError) {
                        println("⚠️ OutOfMemoryError during waveform generation - file too large: ${e.message}")
                        scope.launch(Dispatchers.Main) {
                            errorMessage = "File too large for waveform generation. Skipping..."
                            isLoading = false
                        }
                        System.gc()
                    } catch (e: Exception) {
                        println("❌ Exception during shared Amplituda setup: ${e.message}")
                        e.printStackTrace()
                        scope.launch(Dispatchers.Main) {
                            errorMessage = e.message ?: "Unknown error occurred"
                            isLoading = false
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                println("⚠️ OutOfMemoryError during shared coroutine setup - file too large: ${e.message}")
                errorMessage = "File too large for waveform generation. Skipping..."
                isLoading = false
                System.gc()
            } catch (e: Exception) {
                println("❌ Exception during shared coroutine setup: ${e.message}")
                e.printStackTrace()
                errorMessage = e.message ?: "Unknown error occurred"
                isLoading = false
            }
        } else {
            println("📭 No current file, clearing shared waveform data")
            waveformData = null
            errorMessage = null
            isLoading = false
        }
    }
    
    return SharedWaveformState(
        waveformData = waveformData,
        isLoading = isLoading,
        errorMessage = errorMessage
    )
}

data class SharedWaveformState(
    val waveformData: IntArray?,
    val isLoading: Boolean,
    val errorMessage: String?
)

/**
 * Function to clear the waveform cache for memory management
 */
fun clearWaveformCache() {
    try {
        val oldSize = waveformCache.size
        waveformCache.clear()
        println("🧹 Waveform cache cleared: freed $oldSize entries")
        System.gc()
    } catch (e: Exception) {
        println("❌ Error clearing waveform cache: ${e.message}")
    }
}
