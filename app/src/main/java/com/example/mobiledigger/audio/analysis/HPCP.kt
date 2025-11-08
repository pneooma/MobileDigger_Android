package com.example.mobiledigger.audio.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class HpcpConfig(
    val bins: Int = 36,
    val referenceA4Hz: Double = 440.0,
    val harmonics: Int = 8,
    val harmonicDecay: Double = 1.0,
    val windowSizeSemitones: Double = 1.0
)

internal object Hpcp {
    
    fun computeHpcp(
        peaks: List<Peak>,
        config: HpcpConfig = HpcpConfig()
    ): DoubleArray {
        val bins = config.bins
        val vec = DoubleArray(bins)
        if (peaks.isEmpty()) return vec
        
        val semitonePerBin = 12.0 / bins.toDouble()
        val width = config.windowSizeSemitones
        val halfWidth = width / 2.0
        
        peaks.forEach { peak ->
            var h = 1
            while (h <= config.harmonics) {
                val f = peak.frequencyHz * h
                val strength = peak.magnitude / h.toDouble().pow(config.harmonicDecay)
                val pc = frequencyToPitchClass(f, config.referenceA4Hz)
                val baseBin = (pc / semitonePerBin)
                val left = (baseBin - (halfWidth / semitonePerBin)).toInt()
                val right = (baseBin + (halfWidth / semitonePerBin)).toInt()
                var b = left
                while (b <= right) {
                    val binPc = b * semitonePerBin
                    val dist = angularDistance(pc, binPc)
                    val w = cosineBell(dist, halfWidth)
                    if (w > 0.0) {
                        val idx = positiveMod(b, bins)
                        vec[idx] += strength * w
                    }
                    b++
                }
                h++
            }
        }
        
        val maxV = vec.maxOrNull() ?: 0.0
        if (maxV > 0.0) {
            var i = 0
            while (i < vec.size) {
                vec[i] = (vec[i] / maxV).coerceIn(0.0, 1.0)
                i++
            }
        }
        return vec
    }
    
    private fun frequencyToPitchClass(freqHz: Double, refA4: Double): Double {
        if (freqHz <= 0.0) return 0.0
        val semitonesFromA4 = 12.0 * ln(freqHz / refA4) / ln(2.0)
        val pitchClass = (semitonesFromA4 + 9.0).mod(12.0) // A -> 9, so C -> 0
        return if (pitchClass < 0) pitchClass + 12.0 else pitchClass
    }
    
    private fun angularDistance(a: Double, b: Double): Double {
        var d = (a - b) % 12.0
        if (d < -6.0) d += 12.0
        if (d > 6.0) d -= 12.0
        return kotlin.math.abs(d)
    }
    
    private fun cosineBell(distanceSemitones: Double, halfWidth: Double): Double {
        if (distanceSemitones >= halfWidth) return 0.0
        val x = (distanceSemitones / halfWidth) * PI
        return 0.5 * (1.0 + kotlin.math.cos(x))
    }
    
    private fun positiveMod(x: Int, m: Int): Int {
        val r = x % m
        return if (r < 0) r + m else r
    }
}


