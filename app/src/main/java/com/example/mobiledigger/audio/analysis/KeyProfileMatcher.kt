package com.example.mobiledigger.audio.analysis

internal data class KeyResult(
    val key: String,
    val camelot: String,
    val isMinor: Boolean,
    val confidence: Double
)

internal object KeyProfiles {
    val major = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    val minor = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )
    val noteNames = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val camelotMajor = arrayOf("8B","3B","10B","5B","12B","7B","2B","9B","4B","11B","6B","1B")
    val camelotMinor = arrayOf("5A","12A","7A","2A","9A","4A","11A","6A","1A","8A","3A","10A")
}

internal object KeyProfileMatcher {
    
    fun matchKey(hpcp12: DoubleArray): KeyResult {
        val norm = normalize(hpcp12)
        val (majKey, majScore) = bestRotation(norm, KeyProfiles.major)
        val (minKey, minScore) = bestRotation(norm, KeyProfiles.minor)
        val isMinor = minScore > majScore
        val idx = if (isMinor) minKey else majKey
        val name = KeyProfiles.noteNames[idx] + if (isMinor) " minor" else " major"
        val camelot = if (isMinor) KeyProfiles.camelotMinor[idx] else KeyProfiles.camelotMajor[idx]
        val conf = kotlin.math.abs(majScore - minScore) / (majScore + minScore + 1e-9)
        return KeyResult(name, camelot, isMinor, conf.coerceIn(0.0, 1.0))
    }
    
    private fun bestRotation(x: DoubleArray, profile: DoubleArray): Pair<Int, Double> {
        var bestIdx = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var r = 0
        while (r < 12) {
            var s = 0.0
            var i = 0
            while (i < 12) {
                val xi = x[(i + r) % 12]
                s += xi * profile[i]
                i++
            }
            if (s > bestScore) {
                bestScore = s
                bestIdx = (12 - r) % 12
            }
            r++
        }
        return bestIdx to bestScore
    }
    
    private fun normalize(v: DoubleArray): DoubleArray {
        val sum = v.sum()
        if (sum <= 1e-9) return v.copyOf()
        val out = DoubleArray(v.size)
        var i = 0
        while (i < v.size) {
            out[i] = v[i] / sum
            i++
        }
        return out
    }
}


