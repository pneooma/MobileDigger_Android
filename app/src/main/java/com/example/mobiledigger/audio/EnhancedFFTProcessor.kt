package com.example.mobiledigger.audio

import kotlin.math.*

/**
 * Enhanced FFT processor with multiple window functions and optimized algorithms
 * Based on professional audio analysis techniques
 */
class EnhancedFFTProcessor {
    
    /**
     * Apply window function to audio data
     */
    fun applyWindow(data: FloatArray, windowFunction: WindowFunction): FloatArray {
        return when (windowFunction) {
            WindowFunction.HANNING -> applyHanningWindow(data)
            WindowFunction.HAMMING -> applyHammingWindow(data)
            WindowFunction.BLACKMAN -> applyBlackmanWindow(data)
            WindowFunction.RECTANGULAR -> data.copyOf()
            WindowFunction.KAISER -> applyKaiserWindow(data, 5.0) // Default alpha = 5.0
        }
    }
    
    /**
     * Perform enhanced FFT with optimized algorithm
     */
    fun performFFT(data: FloatArray): FloatArray {
        val n = findNextPowerOf2(data.size)
        val result = FloatArray(n * 2) // Real and imaginary parts
        
        // Copy real data and pad with zeros if necessary
        for (i in 0 until n) {
            result[i * 2] = if (i < data.size) data[i] else 0f
            result[i * 2 + 1] = 0f
        }
        
        // Perform optimized FFT
        fftOptimized(result, n)
        
        return result
    }
    
    /**
     * Calculate power spectrum from FFT result
     */
    fun calculatePowerSpectrum(fftResult: FloatArray): FloatArray {
        val n = fftResult.size / 2
        val powerSpectrum = FloatArray(n / 2) // Only positive frequencies
        
        for (i in 0 until n / 2) {
            val real = fftResult[i * 2]
            val imag = fftResult[i * 2 + 1]
            val magnitude = sqrt(real * real + imag * imag)
            powerSpectrum[i] = magnitude * magnitude // Power = magnitude squared
        }
        
        return powerSpectrum
    }
    
    /**
     * Calculate magnitude spectrum from FFT result
     */
    fun calculateMagnitudeSpectrum(fftResult: FloatArray): FloatArray {
        val n = fftResult.size / 2
        val magnitudeSpectrum = FloatArray(n / 2) // Only positive frequencies
        
        for (i in 0 until n / 2) {
            val real = fftResult[i * 2]
            val imag = fftResult[i * 2 + 1]
            magnitudeSpectrum[i] = sqrt(real * real + imag * imag)
        }
        
        return magnitudeSpectrum
    }
    
    /**
     * Convert power spectrum to dB scale
     */
    fun powerToDb(powerSpectrum: FloatArray, maxPower: Float): FloatArray {
        return powerSpectrum.map { power ->
            if (power > 0) 10f * log10(power / maxPower) else -120f
        }.toFloatArray()
    }
    
    // Window function implementations
    
    private fun applyHanningWindow(data: FloatArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val windowValue = 0.5f * (1 - cos(2 * PI * i / (data.size - 1))).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    private fun applyHammingWindow(data: FloatArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val windowValue = (0.54f - 0.46f * cos(2 * PI * i / (data.size - 1))).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    private fun applyBlackmanWindow(data: FloatArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val windowValue = (0.42f - 0.5f * cos(2 * PI * i / (data.size - 1)) + 
                              0.08f * cos(4 * PI * i / (data.size - 1))).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    private fun applyKaiserWindow(data: FloatArray, alpha: Double): FloatArray {
        val windowed = FloatArray(data.size)
        val i0Alpha = besselI0(alpha)
        
        for (i in data.indices) {
            val x = 2.0 * i / (data.size - 1) - 1.0
            val windowValue = (besselI0(alpha * sqrt(1.0 - x * x)) / i0Alpha).toFloat()
            windowed[i] = data[i] * windowValue
        }
        return windowed
    }
    
    // Optimized FFT implementation
    private fun fftOptimized(data: FloatArray, n: Int) {
        if (n <= 1) return
        
        // Bit-reverse permutation (optimized)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            
            if (i < j) {
                val tempReal = data[i * 2]
                val tempImag = data[i * 2 + 1]
                data[i * 2] = data[j * 2]
                data[i * 2 + 1] = data[j * 2 + 1]
                data[j * 2] = tempReal
                data[j * 2 + 1] = tempImag
            }
        }
        
        // FFT computation with optimized twiddle factors
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
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
    
    private fun findNextPowerOf2(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }
    
    // Bessel function I0 for Kaiser window
    private fun besselI0(x: Double): Double {
        var result = 1.0
        var term = 1.0
        val x2 = x * x / 4.0
        
        for (i in 1..20) { // 20 iterations should be sufficient
            term *= x2 / (i * i)
            result += term
        }
        
        return result
    }
}
