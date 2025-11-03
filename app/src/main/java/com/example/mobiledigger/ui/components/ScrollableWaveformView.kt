package com.example.mobiledigger.ui.components
import com.example.mobiledigger.util.CrashLogger

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mobiledigger.model.MusicFile
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import linc.com.amplituda.Amplituda
import linc.com.amplituda.AmplitudaResult
import linc.com.amplituda.callback.AmplitudaErrorListener
import linc.com.amplituda.callback.AmplitudaSuccessListener
import linc.com.amplituda.Compress
import java.io.InputStream

// Simple in-memory cache for waveform data
private val waveformCache = mutableMapOf<String, IntArray>()

@Composable
fun ScrollableWaveformView(
    currentFile: MusicFile?,
    progress: Float, // 0.0 to 1.0
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var waveformData by remember { mutableStateOf<IntArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Local progress state for immediate feedback
    var localProgress by remember { mutableStateOf(progress) }
    
    // Update local progress when external progress changes
    LaunchedEffect(progress) {
        localProgress = progress
    }

    // Generate waveform when file changes
    LaunchedEffect(currentFile?.uri?.toString(), currentFile?.name) {
        // Cancel any ongoing processing for the previous file
        scope.coroutineContext.cancelChildren()
        
        val uriString = currentFile?.uri?.toString()
        CrashLogger.log("Debug", "🔄 ScrollableWaveformView: LaunchedEffect triggered for file: ${currentFile?.name}")
        CrashLogger.log("Debug", "🔄 URI string: $uriString")
        CrashLogger.log("Debug", "🔄 Previous waveform data: ${waveformData?.size ?: "null"}")
        
        // Always reset state when file changes
        waveformData = null
        errorMessage = null
        isLoading = true
        
        if (currentFile != null) {
            CrashLogger.log("Debug", "🎵 Starting Amplituda waveform generation for: ${currentFile.name}")
            CrashLogger.log("Debug", "🔗 URI: ${currentFile.uri}")
            
            // Create cache key based on URI and file size
            val cacheKey = "${currentFile.uri}_${currentFile.size}"
            
            // Check cache first
            val cachedWaveform = waveformCache[cacheKey]
            if (cachedWaveform != null) {
                CrashLogger.log("Debug", "⚡ Using cached waveform data: ${cachedWaveform.size} samples")
                waveformData = cachedWaveform
                isLoading = false
                return@LaunchedEffect
            }

            try {
                // Process Amplituda on background thread to prevent ANR
                scope.launch(Dispatchers.IO) {
                    try {
                        // Use Amplituda to process the audio with optimized settings
                        val amplituda = Amplituda(context)
                        
                        // Create optimized compression settings based on file type
                        val fileName = currentFile.name.lowercase()
                        val isAiffFile = fileName.endsWith(".aif") || fileName.endsWith(".aiff")
                        
                        // AIFF files are uncompressed, so use ultra-minimal samples for maximum speed
                        val samplesPerSecond = if (isAiffFile) 1 else 3
                        val compressSettings = Compress.withParams(
                            Compress.AVERAGE,
                            samplesPerSecond // AIFF: 1 sample/sec, Others: 3 samples/sec
                        )
                        
                        // Try using InputStream first (more compatible with content:// URIs)
                        val inputStream = try {
                            context.contentResolver.openInputStream(currentFile.uri)
                        } catch (e: Exception) {
                            CrashLogger.log("Debug", "❌ Failed to open InputStream from URI: ${e.message}")
                            null
                        }
                        
                        if (inputStream != null) {
                            CrashLogger.log("Debug", "🔗 Using InputStream for Amplituda processing")
                            
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
                                                
                                                // Cache the waveform data
                                                waveformCache[cacheKey] = samples
                                                
                                                // Update UI on main thread
                                                scope.launch(Dispatchers.Main) {
                                                    waveformData = samples
                                                    isLoading = false
                                                }
                                                CrashLogger.log("Debug", "✅ Amplituda waveform generated from InputStream: ${samples.size} samples")
                                                CrashLogger.log("Debug", "🎵 First 10 amplitudes: ${samples.take(10).joinToString()}")
                                                CrashLogger.log("Debug", "🎵 Amplitude range: min=${samples.minOrNull()}, max=${samples.maxOrNull()}")
                                            } finally {
                                                // Close the InputStream
                                                try {
                                                    inputStream.close()
                                                } catch (e: Exception) {
                                                    CrashLogger.log("Debug", "⚠️ Failed to close InputStream: ${e.message}")
                                                }
                                            }
                                        }
                                    },
                                    object : AmplitudaErrorListener {
                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                            CrashLogger.log("Debug", "❌ Amplituda failed with InputStream, trying URI string: ${exception.message}")
                                            
                                            // Close the InputStream
                                            try {
                                                inputStream.close()
                                            } catch (e: Exception) {
                                                CrashLogger.log("Debug", "⚠️ Failed to close InputStream: ${e.message}")
                                            }
                                            
                                            // Try URI string as fallback
                                            val uriString = currentFile.uri.toString()
                                            CrashLogger.log("Debug", "🔗 Trying URI string: $uriString")
                                            
                                            amplituda.processAudio(uriString, compressSettings)
                                                .get(
                                                    object : AmplitudaSuccessListener<String> {
                                                        override fun onSuccess(result: AmplitudaResult<String>) {
                                                            val samples = result.amplitudesAsList()
                                                                .map { amplitude ->
                                                                    amplitude.coerceIn(0, 100)
                                                                }
                                                                .toIntArray()
                                                            
                                                            // Cache the waveform data
                                                            waveformCache[cacheKey] = samples
                                                            
                                                            // Update UI on main thread
                                                            scope.launch(Dispatchers.Main) {
                                                                waveformData = samples
                                                                isLoading = false
                                                            }
                                                            CrashLogger.log("Debug", "✅ Amplituda waveform generated from URI string: ${samples.size} samples")
                                                        }
                                                    },
                                                    object : AmplitudaErrorListener {
                                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                                            CrashLogger.log("Debug", "❌ Amplituda failed with URI string, trying final fallback: ${exception.message}")
                                                            
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
                                                                    CrashLogger.log("Debug", "✅ Final fallback waveform generated: ${fallbackSamples.size} samples")
                                                                    
                                                                } catch (fallbackException: Exception) {
                                                                    CrashLogger.log("Debug", "❌ Final fallback also failed: ${fallbackException.message}")
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
                            CrashLogger.log("Debug", "🔗 InputStream not available, trying URI string directly")
                            val uriString = currentFile.uri.toString()
                            CrashLogger.log("Debug", "🔗 Trying URI string: $uriString")
                            
                            amplituda.processAudio(uriString, compressSettings)
                                .get(
                                    object : AmplitudaSuccessListener<String> {
                                        override fun onSuccess(result: AmplitudaResult<String>) {
                                            val samples = result.amplitudesAsList()
                                                .map { amplitude ->
                                                    amplitude.coerceIn(0, 100)
                                                }
                                                .toIntArray()
                                            
                                            // Cache the waveform data
                                            waveformCache[cacheKey] = samples
                                            
                                            // Update UI on main thread
                                            scope.launch(Dispatchers.Main) {
                                                waveformData = samples
                                                isLoading = false
                                            }
                                            CrashLogger.log("Debug", "✅ Amplituda waveform generated from URI string: ${samples.size} samples")
                                        }
                                    },
                                    object : AmplitudaErrorListener {
                                        override fun onError(exception: linc.com.amplituda.exceptions.AmplitudaException) {
                                            CrashLogger.log("Debug", "❌ Amplituda failed with URI string, trying final fallback: ${exception.message}")
                                            
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
                                                    CrashLogger.log("Debug", "✅ Final fallback waveform generated: ${fallbackSamples.size} samples")
                                                    
                                                } catch (fallbackException: Exception) {
                                                    CrashLogger.log("Debug", "❌ Final fallback also failed: ${fallbackException.message}")
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
                        
                    } catch (e: Exception) {
                        CrashLogger.log("Debug", "❌ Exception during Amplituda setup: ${e.message}")
                        e.printStackTrace()
                        scope.launch(Dispatchers.Main) {
                            errorMessage = e.message ?: "Unknown error occurred"
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("Debug", "❌ Exception during coroutine setup: ${e.message}")
                e.printStackTrace()
                errorMessage = e.message ?: "Unknown error occurred"
                isLoading = false
            }
        } else {
            CrashLogger.log("Debug", "📭 No current file, clearing waveform data")
            waveformData = null
            errorMessage = null
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Color.Gray.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Calculate progress based on tap position
                    val tapProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    localProgress = tapProgress
                    onSeek(tapProgress)
                    CrashLogger.log("Debug", "🎯 Tap seek to: ${(tapProgress * 100).toInt()}%")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Always-visible green progress line overlay
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val progressX = localProgress * size.width
            drawLine(
                color = Color.Green,
                start = Offset(progressX, 0f),
                end = Offset(progressX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
        when {
            isLoading -> {
                // Loading indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "loadingDots")
                    (0..2).forEach { index ->
                        val animatedAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600, easing = LinearEasing),
                                initialStartOffset = StartOffset(offsetMillis = index * 200),
                                repeatMode = RepeatMode.Reverse
                            ), label = "dotAlpha$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = animatedAlpha),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            
            errorMessage != null -> {
                // Error state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Waveform Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            waveformData != null -> {
                // Waveform display using WaveformSeekBar
                val playedColor = MaterialTheme.colorScheme.primary.toArgb()
                val unplayedColor = Color.Gray.toArgb()
                
                AndroidView(
                    factory = { ctx ->
                        WaveformSeekBar(ctx).apply {
                            // Configure WaveformSeekBar appearance
                            waveBackgroundColor = unplayedColor
                            waveProgressColor = playedColor
                            waveWidth = 1f
                            waveGap = 0f
                            waveCornerRadius = 2f
                            wavePaddingTop = 1
                            wavePaddingBottom = 1
                            
                            // Set the waveform data
                            sample = waveformData!!
                            
                            // Set initial progress
                            this.progress = localProgress * 100f
                            
                            // Disable built-in gesture handling - make it purely visual
                            onProgressChanged = null
                            
                            // Disable touch events on the WaveformSeekBar
                            isEnabled = false
                        }
                    },
                    update = { waveformSeekBar ->
                        // Update progress when it changes externally
                        waveformSeekBar.progress = localProgress * 100f
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            else -> {
                // No waveform available - show tap area with progress indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Show progress indicator
                    Text(
                        text = "Tap to seek • ${(localProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
