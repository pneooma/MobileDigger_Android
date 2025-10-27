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
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Extension function for number formatting
private fun Double.format(digits: Int) = "%.${digits}f".format(this)

// DISABLED: Waveform caching completely disabled to prevent memory issues and crashes
// Waveforms will be regenerated each time a file is played
// This uses more CPU but prevents memory-related crashes
private val waveformCache = emptyMap<String, IntArray>() // No caching

// Shared preference key for waveform generation toggle
private const val PREF_WAVEFORM_ENABLED = "waveform_generation_enabled"

// Function to check if waveform generation is enabled
fun isWaveformGenerationEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_WAVEFORM_ENABLED, false) // Default: disabled for safety
}

// Function to toggle waveform generation
fun setWaveformGenerationEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(PREF_WAVEFORM_ENABLED, enabled).apply()
}

// Generate a consistent pastel color for a song based on its URI
fun getColorForSong(uri: String): Color {
    val hash = uri.hashCode()
    val pastelColors = listOf(
        Color(0xFFFFDAB9), // Peach
        Color(0xFFE6E6FA), // Lavender
        Color(0xFFB0E0E6), // Powder Blue
        Color(0xFFFFFACD), // Lemon Chiffon
        Color(0xFFFFE4E1), // Misty Rose
        Color(0xFFE0FFE0), // Mint
        Color(0xFFFFF0F5), // Lavender Blush
        Color(0xFFF0E68C), // Khaki
        Color(0xFFDDA0DD), // Plum
        Color(0xFFB0C4DE), // Light Steel Blue
    )
    return pastelColors[abs(hash) % pastelColors.size]
}

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
        // CRITICAL: Cancel any ongoing processing for the previous file
        scope.coroutineContext.cancelChildren()
        
        val uriString = currentFile?.uri?.toString()
        println("🔄 SharedWaveformState: LaunchedEffect triggered for file: ${currentFile?.name}")
        println("🔄 URI string: $uriString")
        println("🔄 Previous waveform data: ${waveformData?.size ?: "null"}")
        
        // CRITICAL: Always reset state when file changes
        waveformData = null
        errorMessage = null
        isLoading = true
        
        // CRITICAL: Force garbage collection to free memory before processing
        // This helps prevent crashes when switching between large AIFF files
        System.gc()
        Thread.sleep(50) // Give GC time to complete
        
        if (currentFile != null) {
            // Check if waveform generation is enabled by user
            if (!isWaveformGenerationEnabled(context)) {
                println("🔇 Waveform generation disabled by user")
                waveformData = null
                isLoading = false
                return@LaunchedEffect
            }
            
            println("🎵 Starting shared waveform generation for: ${currentFile.name}")
            println("🔗 URI: ${currentFile.uri}")
            
            // SAFETY: Skip waveform for large AIFF files only (they cause crashes)
            val fileSizeMB = currentFile.size / (1024.0 * 1024.0)
            val fileName = currentFile.name.lowercase()
            val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
            
            if (isAiffFile && fileSizeMB > 60) {
                println("⚠️ AIFF file too large for waveform (${fileSizeMB.format(1)}MB > 60MB), skipping")
                waveformData = null
                isLoading = false
                return@LaunchedEffect
            }
            
            // No caching - always regenerate waveform to prevent memory issues
            
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
                                                
                                                // No caching - directly update UI
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
                                                            
                                                            // No caching
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
                                                                    // No caching
                                                                    
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
                                            
                                            // No caching
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
                                                    
                                                    // No caching
                                                    
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
 * NOTE: Caching is now disabled, this function does nothing
 */
fun clearWaveformCache() {
    // No-op - caching is disabled
    println("🧹 Waveform cache clearing skipped (caching disabled)")
    System.gc()
}
