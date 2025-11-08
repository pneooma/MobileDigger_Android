package com.example.mobiledigger.audio.analysis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class Peak(val frequencyHz: Double, val magnitude: Double)

internal object SpectralPeaks {
    
    fun computeFramePeaks(
        frame: FloatArray,
        sampleRate: Int,
        minFreqHz: Double = 50.0,
        maxFreqHz: Double = 5000.0,
        maxPeaks: Int = 100
    ): List<Peak> {
        val size = frame.size
        val window = AnalysisWindows.hann(size)
        val stft = Stft(size)
        val win = FloatArray(size)
        var i = 0
        while (i < size) {
            win[i] = frame[i] * window[i]
            i++
        }
        val spec = stft.fft(win)
        val bins = size / 2
        val mags = DoubleArray(bins)
        var b = 0
        var si = 0
        while (b < bins) {
            val re = spec[si].toDouble()
            val im = spec[si + 1].toDouble()
            val m = sqrt(re * re + im * im)
            mags[b] = m
            b++
            si += 2
        }
        
        val peaks = ArrayList<Peak>()
        var bin = 1
        while (bin < bins - 1) {
            val m = mags[bin]
            val left = mags[bin - 1]
            val right = mags[bin + 1]
            if (m > left && m > right) {
                val alpha = left
                val beta = m
                val gamma = right
                val denom = (alpha - 2 * beta + gamma)
                val p = if (abs(denom) > 1e-12) 0.5 * (alpha - gamma) / denom else 0.0
                val peakBin = bin + p
                val freq = (peakBin * sampleRate) / size.toDouble()
                if (freq in minFreqHz..maxFreqHz) {
                    val peakMag = beta - 0.25 * (alpha - gamma) * p
                    peaks.add(Peak(freq, peakMag))
                }
            }
            bin++
        }
        peaks.sortByDescending { it.magnitude }
        return if (peaks.size > maxPeaks) peaks.subList(0, maxPeaks) else peaks
    }
}


