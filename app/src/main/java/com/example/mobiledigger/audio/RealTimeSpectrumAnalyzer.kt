package com.example.mobiledigger.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint

/**
 * Real-time spectrum analyzer for live audio input
 * Similar to Spek's real-time mode
 */
class RealTimeSpectrumAnalyzer(
    private val context: Context
) {
    private val fftProcessor = EnhancedFFTProcessor()
    private val colorMapper = SpectrogramColorMapper()
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val isAnalyzing = AtomicBoolean(false)
    
    // Configuration
    private var config = SpectrogramPresets.REAL_TIME
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // Data flow
    private val _spectrumData = MutableStateFlow<FloatArray?>(null)
    val spectrumData: StateFlow<FloatArray?> = _spectrumData.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    // Coroutine scope for background processing
    private val analyzerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start real-time spectrum analysis
     */
    @SuppressLint("MissingPermission")
    fun startAnalysis(): Boolean {
        return try {
            if (isRecording.get()) {
                Log.w("RealTimeSpectrumAnalyzer", "Already recording")
                return false
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e("RealTimeSpectrumAnalyzer", "RECORD_AUDIO permission not granted")
                return false
            }
            
            // Initialize AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            ) * 4 // Use 4x minimum buffer size for better performance

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: SecurityException) {
                Log.e("RealTimeSpectrumAnalyzer", "Failed to create AudioRecord", e)
                return false
            }
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("RealTimeSpectrumAnalyzer", "Failed to initialize AudioRecord")
                return false
            }
            
            isRecording.set(true)
            _isActive.value = true
            
            // Start recording and analysis
            analyzerScope.launch {
                startRecordingAndAnalysis()
            }
            
            Log.i("RealTimeSpectrumAnalyzer", "Started real-time spectrum analysis")
            true
        } catch (e: Exception) {
            Log.e("RealTimeSpectrumAnalyzer", "Failed to start analysis", e)
            false
        }
    }
    
    /**
     * Stop real-time spectrum analysis
     */
    fun stopAnalysis() {
        try {
            isRecording.set(false)
            isAnalyzing.set(false)
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            _isActive.value = false
            _spectrumData.value = null
            
            Log.i("RealTimeSpectrumAnalyzer", "Stopped real-time spectrum analysis")
        } catch (e: Exception) {
            Log.e("RealTimeSpectrumAnalyzer", "Error stopping analysis", e)
        }
    }
    
    /**
     * Update analysis configuration
     */
    fun updateConfig(newConfig: SpectrogramConfig) {
        config = newConfig
        Log.i("RealTimeSpectrumAnalyzer", "Updated config: ${newConfig.resolution.description}")
    }
    
    /**
     * Get current configuration
     */
    fun getCurrentConfig(): SpectrogramConfig = config
    
    private suspend fun startRecordingAndAnalysis() {
        val audioRecord = this.audioRecord ?: return
        
        try {
            audioRecord.startRecording()
            
            val bufferSize = config.windowSize
            val audioBuffer = ShortArray(bufferSize)
            val floatBuffer = FloatArray(bufferSize)
            
            while (isRecording.get()) {
                val samplesRead = audioRecord.read(audioBuffer, 0, bufferSize)
                
                if (samplesRead > 0) {
                    // Convert to float
                    for (i in 0 until samplesRead) {
                        floatBuffer[i] = audioBuffer[i].toFloat()
                    }
                    
                    // Pad with zeros if needed
                    for (i in samplesRead until bufferSize) {
                        floatBuffer[i] = 0f
                    }
                    
                    // Perform real-time analysis
                    if (!isAnalyzing.get()) {
                        isAnalyzing.set(true)
                        analyzerScope.launch {
                            performRealTimeAnalysis(floatBuffer)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RealTimeSpectrumAnalyzer", "Error in recording loop", e)
        }
    }
    
    private suspend fun performRealTimeAnalysis(audioData: FloatArray) {
        try {
            // Apply window function
            val windowedData = fftProcessor.applyWindow(audioData, config.windowFunction)
            
            // Perform FFT
            val fftResult = fftProcessor.performFFT(windowedData)
            
            // Calculate power spectrum
            val powerSpectrum = fftProcessor.calculatePowerSpectrum(fftResult)
            
            // Convert to dB scale
            val maxPower = powerSpectrum.maxOrNull() ?: 1f
            val dbSpectrum = fftProcessor.powerToDb(powerSpectrum, maxPower)
            
            // Update spectrum data
            _spectrumData.value = dbSpectrum
            
        } catch (e: Exception) {
            Log.e("RealTimeSpectrumAnalyzer", "Error in real-time analysis", e)
        } finally {
            isAnalyzing.set(false)
        }
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        stopAnalysis()
        analyzerScope.cancel()
    }
}
