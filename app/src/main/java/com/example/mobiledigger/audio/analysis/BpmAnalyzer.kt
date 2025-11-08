package com.example.mobiledigger.audio.analysis

data class BpmResult(
    val bpm: Double,
    val beatsSeconds: DoubleArray,
    val confidence: Double,
    val analyzedSeconds: Int
)

class BpmAnalyzer {
    
    fun analyze(
        pcmMono: FloatArray,
        sampleRate: Int,
        maxAnalyzeSeconds: Int = 120,
        frameSize: Int = 2048,
        hopSize: Int = 256
    ): BpmResult {
        val maxSamples = (maxAnalyzeSeconds * sampleRate).coerceAtMost(pcmMono.size)
        val data = if (maxSamples < pcmMono.size) {
            pcmMono.copyOf(maxSamples)
        } else {
            pcmMono
        }
        val onset = OnsetDetectors.computeOnsetFeatures(
            pcm = data,
            sampleRate = sampleRate,
            frameSize = frameSize,
            hopSize = hopSize
        )
        val odf = OnsetDetectors.combineOdFs(onset)
        val tempoCoarse = TempoInduction.estimateBpmFromOdF(odf, onset.frameRateHz)
        val beats = BeatTrackerDP.trackBeats(odf, tempoCoarse.bpm, onset.frameRateHz)
        val conf = computeConfidence(odf, tempoCoarse)
        val seconds = maxSamples / sampleRate
        return BpmResult(
            bpm = tempoCoarse.bpm,
            beatsSeconds = beats,
            confidence = conf,
            analyzedSeconds = seconds
        )
    }
    
    private fun computeConfidence(odf: FloatArray, tempo: TempoCandidate): Double {
        val energy = odf.fold(0.0) { acc, v -> acc + v }
        if (energy <= 1e-9) return 0.0
        val normScore = tempo.score / energy
        return normScore.coerceIn(0.0, 1.0)
    }
}


