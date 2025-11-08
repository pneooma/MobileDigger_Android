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
        // If stereo interleaved, average; otherwise pass-through
        val isStereo = samples.size % 2 == 0
        return if (isStereo) {
            val mono = FloatArray(samples.size / 2)
            var j = 0
            for (i in mono.indices) {
                val left = samples[j].toInt()
                val right = samples[j + 1].toInt()
                mono[i] = ((left + right) / 2f) / 32768f
                j += 2
            }
            mono
        } else {
            FloatArray(samples.size) { idx -> samples[idx] / 32768f }
        }
    }
}
