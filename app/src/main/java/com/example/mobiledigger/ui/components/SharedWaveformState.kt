package com.example.mobiledigger.ui.components

import androidx.compose.runtime.*
import com.example.mobiledigger.model.MusicFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import com.example.mobiledigger.audio.WaveformGenerator
import com.example.mobiledigger.util.CrashLogger

// Extension function for number formatting
private fun Double.format(digits: Int) = "%.${digits}f".format(this)
private fun Float.format(digits: Int) = "%.${digits}f".format(this)

// DISABLED: Waveform caching completely disabled to prevent memory issues and crashes
// Waveforms will be regenerated each time a file is played
// This uses more CPU but prevents memory-related crashes
private val waveformCache = emptyMap<String, IntArray>() // No caching

// Shared preference key for waveform generation toggle
private const val PREF_WAVEFORM_ENABLED = "waveform_generation_enabled"

// Function to check if waveform generation is enabled
fun isWaveformGenerationEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_WAVEFORM_ENABLED, true) // Default: enabled (stable now!)
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
        CrashLogger.log("WaveformGenerator", "LaunchedEffect triggered for file: ${currentFile?.name}")
        
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
                CrashLogger.log("WaveformGenerator", "Waveform generation disabled by user")
                waveformData = null
                isLoading = false
                return@LaunchedEffect
            }
            
            CrashLogger.log("WaveformGenerator", "Starting waveform generation for: ${currentFile.name}")
            
            // Note: AIFF file size check removed - libvlc can handle large AIFF files now
            val fileSizeMB = currentFile.size / (1024.0 * 1024.0)
            CrashLogger.log("WaveformGenerator", "File size: ${fileSizeMB.format(1)}MB")
            
            try {
                // Use custom WaveformGenerator (pure Kotlin, no native crashes)
                scope.launch(Dispatchers.IO) {
                    try {
                        CrashLogger.log("WaveformGenerator", "Generating waveform for: ${currentFile.name}")
                        val generator = WaveformGenerator(context)
                        
                        // OPTIMIZED: Balance between speed and visual quality
                        // 256 samples = higher detail, profiling to measure speed impact
                        val targetSampleCount = 256
                        
                        CrashLogger.log("WaveformGenerator", "Requesting $targetSampleCount samples for any duration")
                        
                        // Generate waveform with fixed sample count
                        val samples = generator.generateWaveform(
                            uri = currentFile.uri,
                            targetSampleCount = targetSampleCount,
                            fileName = currentFile.name
                        )
                        
                        if (samples != null) {
                            CrashLogger.log("WaveformGenerator", "✅ Waveform generated: ${samples.size} samples")
                            // Log sample statistics
                            val min = samples.minOrNull() ?: 0
                            val max = samples.maxOrNull() ?: 0
                            val avg = if (samples.isNotEmpty()) samples.average().toInt() else 0
                            CrashLogger.log("WaveformGenerator", "📊 UI State - Min: $min, Max: $max, Avg: $avg")
                            CrashLogger.log("WaveformGenerator", "📊 UI State - First 10: ${samples.take(10).joinToString()}")
                            
                            withContext(Dispatchers.Main) {
                                waveformData = samples
                                isLoading = false
                                CrashLogger.log("WaveformGenerator", "📊 UI State UPDATED - waveformData is now ${if (waveformData != null) "NOT NULL (${waveformData!!.size} samples)" else "NULL"}")
                            }
                        } else {
                            CrashLogger.log("WaveformGenerator", "❌ Failed to generate waveform")
                            withContext(Dispatchers.Main) {
                                errorMessage = "Failed to generate waveform"
                                isLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        CrashLogger.log("WaveformGenerator", "Exception during waveform generation", e)
                        withContext(Dispatchers.Main) {
                            errorMessage = e.message ?: "Unknown error occurred"
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("WaveformGenerator", "Exception during coroutine setup", e)
                errorMessage = e.message ?: "Unknown error occurred"
                isLoading = false
            }
        } else {
            CrashLogger.log("WaveformGenerator", "No current file, clearing waveform data")
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
    CrashLogger.log("WaveformGenerator", "clearWaveformCache() called, but caching is disabled")
    // No caching, so nothing to clear
}
