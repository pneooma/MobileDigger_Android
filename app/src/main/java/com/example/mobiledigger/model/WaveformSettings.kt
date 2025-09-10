package com.example.mobiledigger.model

import androidx.compose.runtime.Immutable

enum class UpdateFrequency(val milliseconds: Float) {
    HIGH(250f),
    MEDIUM(500f),
    LOW(1000f)
}

@Immutable
data class WaveformSettings(
    val barCount: Int = 400, // UI bars increased to 400
    val oversampleFactor: Int = 1, // iOS doesn't use oversampling, uses direct samplesPerPoint calculation
    val frameDurationMs: Int = 0, // iOS doesn't use fixed frame duration, uses samplesPerPoint
    val bufferSize: Int = 512, // Buffer size = 512
    val minAmplitude: Float = 0.00001f, // Ultra-sensitive minimum amplitude
    val maxAmplitude: Float = 3.0f, // Extended maximum amplitude range
    
    // React Native Audio Waveform inspired parameters
    val candleWidth: Float = 0.1f, // Ultra-thin candle width
    val candleHeightScale: Float = 3f, // Height scaling factor for amplitude
    val maxBarsToRender: Int = 400, // Maximum bars to render for performance (matches barCount)
    val showHorizontalScrollIndicator: Boolean = false, // Show scroll indicator
    val updateFrequency: UpdateFrequency = UpdateFrequency.MEDIUM // Update frequency
) {
    companion object {
        val DEFAULT = WaveformSettings()
        
        // Preset configurations based on iOS implementation + React Native Audio Waveform
        val HIGH_QUALITY = WaveformSettings(
            barCount = 1000,
            oversampleFactor = 1,
            frameDurationMs = 0,
            bufferSize = 8192,
            candleWidth = 2f,
            candleHeightScale = 4f,
            maxBarsToRender = 500,
            updateFrequency = UpdateFrequency.HIGH
        )
        
        val BALANCED = WaveformSettings(
            barCount = 500, // Match iOS exactly
            oversampleFactor = 1,
            frameDurationMs = 0,
            bufferSize = 4096, // Match iOS exactly
            candleWidth = 3f,
            candleHeightScale = 3f,
            maxBarsToRender = 300,
            updateFrequency = UpdateFrequency.MEDIUM
        )
        
        val PERFORMANCE = WaveformSettings(
            barCount = 250,
            oversampleFactor = 1,
            frameDurationMs = 0,
            bufferSize = 2048,
            candleWidth = 4f,
            candleHeightScale = 2f,
            maxBarsToRender = 200,
            updateFrequency = UpdateFrequency.LOW
        )
    }
}
