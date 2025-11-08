package com.example.mobiledigger.audio.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class TempoCandidate(
    val bpm: Double,
    val score: Double
)

internal object TempoInduction {
    
    fun estimateBpmFromOdF(
        odf: FloatArray,
        frameRateHz: Float,
        bpmMin: Int = 40,
        bpmMax: Int = 200
    ): TempoCandidate {
        val odfSmoothed = movingAverage(odf, 4)
        val acf = autocorrelation(odfSmoothed)
        val candidates = mutableListOf<TempoCandidate>()
        var lag = framesPerBeat(bpmMax.toDouble(), frameRateHz).coerceAtLeast(1)
        val lagMax = framesPerBeat(bpmMin.toDouble(), frameRateHz)
        while (lag <= lagMax && lag < acf.size) {
            val bpm = lagToBpm(lag, frameRateHz)
            val baseScore = combScore(odfSmoothed, lag)
            val prior = bpmPrior(bpm)
            val score = baseScore * prior
            candidates.add(TempoCandidate(bpm, score))
            lag++
        }
        if (candidates.isEmpty()) return TempoCandidate(120.0, 0.0)
        val coarseBest = candidates.maxBy { it.score }
        // Refine using beat-grid alignment around best and its half/double
        val refined = refineWithBeatGrid(odfSmoothed, frameRateHz, coarseBest.bpm)
        return refined
    }
    
    private fun bpmPrior(bpm: Double): Double {
        // Prefer typical dance/modern music tempos 80–160; soft-penalize extremes
        return when {
            bpm in 80.0..160.0 -> 1.0
            bpm in 60.0..80.0 || bpm in 160.0..180.0 -> 0.9
            else -> 0.7
        }
    }
    
    private fun autocorrelation(x: FloatArray): FloatArray {
        val n = x.size
        val acf = FloatArray(n)
        var lag = 0
        while (lag < n) {
            var s = 0.0
            var i = 0
            while (i + lag < n) {
                s += x[i].toDouble() * x[i + lag].toDouble()
                i++
            }
            acf[lag] = s.toFloat()
            lag++
        }
        return acf
    }
    
    private fun combScore(odf: FloatArray, lag: Int, harmonics: Int = 8): Double {
        val n = odf.size
        var score = 0.0
        var h = 1
        while (h <= harmonics) {
            val step = lag * h
            if (step <= 0) break
            var i = 0
            var s = 0.0
            while (i < n) {
                s += odf[i].toDouble()
                i += step
            }
            score += s / h
            h++
        }
        return score
    }
    
    private fun framesPerBeat(bpm: Double, frameRateHz: Float): Int {
        val secondsPerBeat = 60.0 / bpm
        val frames = (secondsPerBeat * frameRateHz).toInt()
        return max(1, frames)
    }
    
    private fun lagToBpm(lag: Int, frameRateHz: Float): Double {
        val secondsPerBeat = lag.toDouble() / frameRateHz
        val bpm = 60.0 / secondsPerBeat
        return min(300.0, max(20.0, bpm))
    }
    
    private fun movingAverage(x: FloatArray, win: Int): FloatArray {
        if (win <= 1 || x.isEmpty()) return x.copyOf()
        val w = min(win, x.size)
        val out = FloatArray(x.size)
        var sum = 0.0
        var i = 0
        while (i < x.size) {
            sum += x[i]
            if (i >= w) sum -= x[i - w]
            out[i] = (sum / kotlin.math.min(i + 1, w)).toFloat()
            i++
        }
        return out
    }
    
    private fun refineWithBeatGrid(odf: FloatArray, frameRateHz: Float, initialBpm: Double): TempoCandidate {
        // Candidate set: initial, half, double, and +/- 6 BPM around each at 0.5 BPM steps
        val baseCandidates = doubleArrayOf(
            (initialBpm / 2.0).coerceAtLeast(30.0),
            initialBpm,
            (initialBpm * 2.0).coerceAtMost(240.0)
        ).distinct().toDoubleArray()
        
        var bestBpm = initialBpm
        var bestScore = Double.NEGATIVE_INFINITY
        
        baseCandidates.forEach { base ->
            var delta = -6.0
            while (delta <= 6.0) {
                val bpm = (base + delta).coerceIn(40.0, 200.0)
                val score = beatGridAlignmentScore(odf, frameRateHz, bpm)
                if (score > bestScore) {
                    bestScore = score
                    bestBpm = bpm
                }
                delta += 0.5
            }
        }
        return TempoCandidate(bestBpm, bestScore)
    }
    
    private fun beatGridAlignmentScore(odf: FloatArray, frameRateHz: Float, bpm: Double): Double {
        val framesPerBeat = (frameRateHz * 60.0 / bpm)
        if (framesPerBeat < 1.0) return 0.0
        // Search the best phase offset within one beat interval
        val phaseSteps = 8
        var best = 0.0
        var s = 0
        while (s < phaseSteps) {
            val phase = (framesPerBeat * s) / phaseSteps
            var i = phase
            var sum = 0.0
            var count = 0
            while (i < odf.size) {
                val idx = i.toInt().coerceIn(0, odf.size - 1)
                sum += odf[idx].toDouble()
                count++
                i += framesPerBeat
            }
            val avg = if (count > 0) sum / count else 0.0
            if (avg > best) best = avg
            s++
        }
        // Mild prior to keep tempos in common range
        val prior = bpmPrior(bpm)
        return best * prior
    }
}


