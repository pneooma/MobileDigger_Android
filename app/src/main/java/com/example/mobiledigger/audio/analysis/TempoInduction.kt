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
        val acf = autocorrelation(odf)
        val candidates = mutableListOf<TempoCandidate>()
        var lag = framesPerBeat(bpmMax.toDouble(), frameRateHz).coerceAtLeast(1)
        val lagMax = framesPerBeat(bpmMin.toDouble(), frameRateHz)
        while (lag <= lagMax && lag < acf.size) {
            val bpm = lagToBpm(lag, frameRateHz)
            val score = combScore(odf, lag)
            candidates.add(TempoCandidate(bpm, score))
            lag++
        }
        if (candidates.isEmpty()) return TempoCandidate(120.0, 0.0)
        val best = candidates.maxBy { it.score }
        val corrected = correctHalfDouble(best, candidates)
        return corrected
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
    
    private fun correctHalfDouble(
        best: TempoCandidate,
        candidates: List<TempoCandidate>
    ): TempoCandidate {
        val half = best.bpm / 2.0
        val double = best.bpm * 2.0
        var bestScore = best.score
        var bestBpm = best.bpm
        candidates.forEach { c ->
            if (kotlin.math.abs(c.bpm - half) < 1.5 && c.score * 1.05 > bestScore) {
                bestScore = c.score
                bestBpm = c.bpm
            }
            if (kotlin.math.abs(c.bpm - double) < 1.5 && c.score * 0.95 > bestScore) {
                bestScore = c.score
                bestBpm = c.bpm
            }
        }
        return TempoCandidate(bestBpm, bestScore)
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
}


