package com.example.mobiledigger.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AudioAnalysisResult(
    val bpm: Float?,
    val bpmConfidence: Float,
    val key: String?,
    val keyConfidence: Float
)

class AudioAnalyzer {
    private val bpmAnalyzer = com.example.mobiledigger.audio.analysis.BpmAnalyzer()
    private val keyAnalyzer = com.example.mobiledigger.audio.analysis.KeyAnalyzer()

    fun analyze(samples: ShortArray, sampleRate: Int = 44100): AudioAnalysisResult {
        val mono = toFloatMono(samples)
        if (mono.isEmpty()) {
            return AudioAnalysisResult(
                bpm = null,
                bpmConfidence = 0f,
                key = null,
                keyConfidence = 0f
            )
        }
        val bpmRes = bpmAnalyzer.analyze(
            pcmMono = mono,
            sampleRate = sampleRate,
            maxAnalyzeSeconds = 120,
            frameSize = 2048,
            hopSize = 256
        )
        val keyRes = keyAnalyzer.analyze(
            pcmMono = mono,
            sampleRate = sampleRate,
            maxAnalyzeSeconds = 120,
            frameSize = 4096,
            hopSize = 2048,
            hpcpBins = 36
        )
        return AudioAnalysisResult(
            bpm = bpmRes.bpm.toFloat(),
            bpmConfidence = bpmRes.confidence.toFloat().coerceIn(0f, 1f),
            key = keyRes.key,
            keyConfidence = keyRes.confidence.toFloat().coerceIn(0f, 1f)
        )
    }

    private fun toFloatMono(samples: ShortArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        // Input is already mono from extraction; convert directly to float
        val out = FloatArray(samples.size)
        var i = 0
        while (i < samples.size) {
            out[i] = samples[i] / 32768f
            i++
        }
        return out
    }
}
