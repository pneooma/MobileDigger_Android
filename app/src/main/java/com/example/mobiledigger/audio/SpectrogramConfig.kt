package com.example.mobiledigger.audio

/**
 * Configuration class for spectrogram generation parameters
 * Based on professional audio analysis tools like Spek
 */
data class SpectrogramConfig(
    val windowSize: Int = 4096,        // FFT window size (larger = better frequency resolution)
    val hopSize: Int = 1024,           // Overlap between windows (smaller = better time resolution)
    val fftSize: Int = 4096,           // FFT size (must be power of 2)
    val dynamicRange: Float = 60f,     // Dynamic range in dB (adjustable)
    val frequencyRange: Pair<Float, Float> = 20f to 22000f, // Frequency range in Hz
    val colorScheme: ColorScheme = ColorScheme.SPEK_PROFESSIONAL,
    val windowFunction: WindowFunction = WindowFunction.HANNING,
    val resolution: SpectrogramResolution = SpectrogramResolution.HIGH,
    val analysisType: AnalysisType = AnalysisType.POWER_SPECTRUM
)

/**
 * Color schemes for spectrogram visualization
 */
enum class ColorScheme {
    SPEK_PROFESSIONAL,    // Black→Blue→Purple→Red→Yellow→White (like Spek)
    SPEK_CLASSIC,         // Black→Blue→Green→Yellow→Red
    SPEK_MONOCHROME,      // Grayscale
    RAINBOW,              // Rainbow spectrum
    HEAT_MAP              // Heat map colors
}

/**
 * Window functions for FFT preprocessing
 */
enum class WindowFunction {
    HANNING,             // Hanning window (good general purpose)
    HAMMING,             // Hamming window (slightly better frequency resolution)
    BLACKMAN,            // Blackman window (better side lobe suppression)
    RECTANGULAR,         // No windowing (rectangular window)
    KAISER               // Kaiser window (adjustable parameter)
}

/**
 * Spectrogram resolution presets
 */
enum class SpectrogramResolution(val width: Int, val height: Int, val description: String) {
    ULTRA_FAST(150, 200, "Ultra-fast format"),
    FAST(300, 200, "Fast format"),
    STANDARD(600, 300, "Standard format"),
    HIGH(1200, 400, "High resolution"),
    ULTRA_HIGH(2400, 800, "Ultra-high resolution")
}

/**
 * Analysis types for spectrogram generation
 */
enum class AnalysisType {
    POWER_SPECTRUM,      // Power spectrum (magnitude squared)
    MAGNITUDE_SPECTRUM,  // Magnitude spectrum
    PHASE_SPECTRUM,      // Phase spectrum
    COMPLEX_SPECTRUM     // Complex spectrum
}

/**
 * Default configurations for different use cases
 */
object SpectrogramPresets {
    val SPEK_DEFAULT = SpectrogramConfig(
        windowSize = 4096,
        hopSize = 1024,
        fftSize = 4096,
        dynamicRange = 60f,
        frequencyRange = 20f to 22000f,
        colorScheme = ColorScheme.SPEK_PROFESSIONAL,
        windowFunction = WindowFunction.HANNING,
        resolution = SpectrogramResolution.HIGH,
        analysisType = AnalysisType.POWER_SPECTRUM
    )
    
    val REAL_TIME = SpectrogramConfig(
        windowSize = 2048,
        hopSize = 512,
        fftSize = 2048,
        dynamicRange = 40f,
        frequencyRange = 20f to 20000f,
        colorScheme = ColorScheme.SPEK_PROFESSIONAL,
        windowFunction = WindowFunction.HANNING,
        resolution = SpectrogramResolution.FAST,
        analysisType = AnalysisType.POWER_SPECTRUM
    )
    
    val ULTRA_DETAILED = SpectrogramConfig(
        windowSize = 8192,
        hopSize = 512,
        fftSize = 8192,
        dynamicRange = 80f,
        frequencyRange = 20f to 22000f,
        colorScheme = ColorScheme.SPEK_PROFESSIONAL,
        windowFunction = WindowFunction.BLACKMAN,
        resolution = SpectrogramResolution.ULTRA_HIGH,
        analysisType = AnalysisType.POWER_SPECTRUM
    )
}
