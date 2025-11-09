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
        // Swap to new BPM engine V2 (tempogram + HPS + grid refinement)
        val engine = BpmEngineV2()
        val res = engine.analyze(
            pcmMono = data,
            sampleRate = sampleRate,
            frameSize = frameSize,
            hopSize = hopSize
        )
        val seconds = maxSamples / sampleRate
        return BpmResult(
            bpm = res.bpm,
            beatsSeconds = res.beatsSeconds,
            confidence = res.confidence,
            analyzedSeconds = seconds
        )
    }
    
    // Confidence handled inside engine
}


