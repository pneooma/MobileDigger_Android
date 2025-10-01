package com.example.mobiledigger.audio

// import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Optimized FFT service with improved performance for
 * Fast Fourier Transform operations used in spectrogram generation.
 * Uses optimized algorithms and proper windowing functions.
 */
class OptimizedFFTService {
    
    /**
     * Perform optimized FFT on audio data using improved algorithm
     * @param audioData Input audio samples
     * @param windowSize Size of the FFT window
     * @return Array of magnitude values for frequency bins
     */
    suspend fun performFFT(audioData: FloatArray, windowSize: Int = 1024): FloatArray = withContext(Dispatchers.Default) {
        // Apply window function to reduce spectral leakage
        val windowedData = applyBlackmanWindow(audioData, windowSize)
        
        // Perform FFT using optimized implementation
        val fftResult = performOptimizedFFT(windowedData, windowSize)
        
        // Calculate magnitude spectrum
        val magnitude = FloatArray(windowSize / 2)
        for (i in 0 until windowSize / 2) {
            val real = fftResult[i * 2]
            val imag = fftResult[i * 2 + 1]
            magnitude[i] = sqrt((real * real + imag * imag).toFloat())
        }
        
        magnitude
    }
    
    /**
     * Optimized FFT implementation using Cooley-Tukey algorithm
     */
    private fun performOptimizedFFT(data: FloatArray, size: Int): FloatArray {
        val result = FloatArray(size * 2) // Complex output
        
        // Copy real data to result array
        for (i in 0 until minOf(size, data.size)) {
            result[i * 2] = data[i] // Real part
            result[i * 2 + 1] = 0f  // Imaginary part
        }
        
        // Perform in-place FFT
        fft(result, size)
        
        return result
    }
    
    /**
     * In-place FFT using Cooley-Tukey algorithm
     */
    private fun fft(data: FloatArray, n: Int) {
        if (n <= 1) return
        
        // Bit-reverse permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            
            if (i < j) {
                // Swap real parts
                val tempReal = data[i * 2]
                data[i * 2] = data[j * 2]
                data[j * 2] = tempReal
                
                // Swap imaginary parts
                val tempImag = data[i * 2 + 1]
                data[i * 2 + 1] = data[j * 2 + 1]
                data[j * 2 + 1] = tempImag
            }
        }
        
        // FFT computation
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wlenReal = cos(angle).toFloat()
            val wlenImag = sin(angle).toFloat()
            
            for (i in 0 until n step length) {
                var wReal = 1f
                var wImag = 0f
                
                for (j in 0 until length / 2) {
                    val uReal = data[(i + j) * 2]
                    val uImag = data[(i + j) * 2 + 1]
                    val vReal = data[(i + j + length / 2) * 2] * wReal - data[(i + j + length / 2) * 2 + 1] * wImag
                    val vImag = data[(i + j + length / 2) * 2] * wImag + data[(i + j + length / 2) * 2 + 1] * wReal
                    
                    data[(i + j) * 2] = uReal + vReal
                    data[(i + j) * 2 + 1] = uImag + vImag
                    data[(i + j + length / 2) * 2] = uReal - vReal
                    data[(i + j + length / 2) * 2 + 1] = uImag - vImag
                    
                    val nextWReal = wReal * wlenReal - wImag * wlenImag
                    val nextWImag = wReal * wlenImag + wImag * wlenReal
                    wReal = nextWReal
                    wImag = nextWImag
                }
            }
            length *= 2
        }
    }
    
    /**
     * Apply Blackman window function to reduce spectral leakage
     */
    private fun applyBlackmanWindow(data: FloatArray, windowSize: Int): FloatArray {
        val windowed = FloatArray(windowSize)
        val actualSize = minOf(windowSize, data.size)
        
        for (i in 0 until actualSize) {
            val windowValue = blackmanWindow(i, actualSize)
            windowed[i] = data[i] * windowValue
        }
        
        return windowed
    }
    
    /**
     * Calculate Blackman window coefficient
     */
    private fun blackmanWindow(index: Int, size: Int): Float {
        val n = size - 1
        val alpha = 0.16f
        val a0 = (1 - alpha) / 2
        val a1 = 0.5f
        val a2 = alpha / 2
        
        val term1 = a0
        val term2 = a1 * cos(2 * PI * index / n)
        val term3 = a2 * cos(4 * PI * index / n)
        
        return (term1 - term2 + term3).toFloat()
    }
    
    /**
     * Perform multiple FFT operations for spectrogram generation
     * @param audioData Complete audio data
     * @param windowSize FFT window size
     * @param hopSize Step size between windows
     * @return 2D array of magnitude values [time][frequency]
     */
    suspend fun generateSpectrogram(
        audioData: FloatArray, 
        windowSize: Int = 1024, 
        hopSize: Int = 256
    ): Array<FloatArray> = withContext(Dispatchers.Default) {
        
        val numFrames = (audioData.size - windowSize) / hopSize + 1
        val numBins = windowSize / 2
        val spectrogram = Array(numFrames) { FloatArray(numBins) }
        
        for (frame in 0 until numFrames) {
            val startIndex = frame * hopSize
            val endIndex = minOf(startIndex + windowSize, audioData.size)
            
            if (endIndex - startIndex >= windowSize) {
                val frameData = audioData.sliceArray(startIndex until endIndex)
                val magnitude = performFFT(frameData, windowSize)
                
                for (bin in 0 until numBins) {
                    spectrogram[frame][bin] = magnitude[bin]
                }
            }
        }
        
        spectrogram
    }
    
    /**
     * Calculate frequency bins for a given sample rate
     * @param sampleRate Audio sample rate
     * @param windowSize FFT window size
     * @return Array of frequency values in Hz
     */
    fun calculateFrequencyBins(sampleRate: Int, windowSize: Int): FloatArray {
        val numBins = windowSize / 2
        val frequencyBins = FloatArray(numBins)
        
        for (i in 0 until numBins) {
            frequencyBins[i] = (i * sampleRate / windowSize).toFloat()
        }
        
        return frequencyBins
    }
    
    /**
     * Convert magnitude spectrum to decibels
     * @param magnitude Magnitude values
     * @param minDb Minimum dB value for clipping
     * @return Array of dB values
     */
    fun magnitudeToDecibels(magnitude: FloatArray, minDb: Float = -80f): FloatArray {
        val dbValues = FloatArray(magnitude.size)
        
        for (i in magnitude.indices) {
            val mag = maxOf(magnitude[i], 1e-10f) // Avoid log(0)
            val db = 20f * log10(mag)
            dbValues[i] = maxOf(db, minDb)
        }
        
        return dbValues
    }
    
    /**
     * Apply smoothing to reduce noise in spectrogram
     * @param spectrogram Input spectrogram data
     * @param smoothingFactor Smoothing strength (0.0 = no smoothing, 1.0 = maximum smoothing)
     * @return Smoothed spectrogram
     */
    fun smoothSpectrogram(spectrogram: Array<FloatArray>, smoothingFactor: Float = 0.3f): Array<FloatArray> {
        if (smoothingFactor <= 0f) return spectrogram
        
        val smoothed = Array(spectrogram.size) { FloatArray(spectrogram[0].size) }
        
        for (frame in spectrogram.indices) {
            for (bin in spectrogram[frame].indices) {
                var sum = 0f
                var count = 0
                
                // Apply 3x3 smoothing kernel
                for (df in -1..1) {
                    for (db in -1..1) {
                        val f = frame + df
                        val b = bin + db
                        
                        if (f in spectrogram.indices && b in spectrogram[f].indices) {
                            sum += spectrogram[f][b]
                            count++
                        }
                    }
                }
                
                val average = sum / count
                smoothed[frame][bin] = spectrogram[frame][bin] * (1f - smoothingFactor) + average * smoothingFactor
            }
        }
        
        return smoothed
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        // Clean up any resources if needed
        // No external resources to clean up in this implementation
    }
}
