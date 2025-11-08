package com.example.mobiledigger.audio.analysis

import kotlin.math.abs
import kotlin.math.max

internal object BeatTrackerDP {
    
    fun trackBeats(
        odf: FloatArray,
        bpm: Double,
        frameRateHz: Float,
        searchRadiusFrames: Int = 4
    ): DoubleArray {
        val framesPerBeat = ((60.0 / bpm) * frameRateHz).toFloat().coerceAtLeast(1f)
        val peaks = findLocalMaxima(odf)
        if (peaks.isEmpty()) return doubleArrayOf()
        
        val scores = DoubleArray(peaks.size)
        val prev = IntArray(peaks.size) { -1 }
        
        var i = 0
        while (i < peaks.size) {
            scores[i] = odf[peaks[i]].toDouble()
            var j = 0
            while (j < i) {
                val expected = peaks[j] + framesPerBeat
                val diff = abs(peaks[i] - expected)
                if (diff <= searchRadiusFrames) {
                    val s = scores[j] + odf[peaks[i]].toDouble() - (diff / searchRadiusFrames)
                    if (s > scores[i]) {
                        scores[i] = s
                        prev[i] = j
                    }
                }
                j++
            }
            i++
        }
        
        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY
        i = 0
        while (i < scores.size) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIndex = i
            }
            i++
        }
        
        val path = ArrayList<Int>()
        var k = bestIndex
        while (k >= 0) {
            path.add(peaks[k])
            k = prev[k]
        }
        path.reverse()
        
        val beatTimes = DoubleArray(path.size)
        i = 0
        while (i < path.size) {
            beatTimes[i] = path[i] / frameRateHz.toDouble()
            i++
        }
        return beatTimes
    }
    
    private fun findLocalMaxima(x: FloatArray, radius: Int = 2, minValue: Float = 0.05f): IntArray {
        val peaks = ArrayList<Int>()
        var i = 0
        while (i < x.size) {
            val center = x[i]
            if (center >= minValue) {
                var isPeak = true
                var r = 1
                while (r <= radius) {
                    val left = x[(i - r).coerceAtLeast(0)]
                    val right = x[(i + r).coerceAtMost(x.size - 1)]
                    if (left > center || right > center) {
                        isPeak = false
                        break
                    }
                    r++
                }
                if (isPeak) peaks.add(i)
            }
            i++
        }
        return peaks.toIntArray()
    }
}


