package com.example.mobiledigger.audio.analysis

data class KeyAnalysisResult(
    val key: String,
    val camelot: String,
    val confidence: Double,
    val analyzedSeconds: Int
)

class KeyAnalyzer {
    
    fun analyze(
        pcmMono: FloatArray,
        sampleRate: Int,
        maxAnalyzeSeconds: Int = 120,
        frameSize: Int = 4096,
        hopSize: Int = 2048,
        hpcpBins: Int = 36
    ): KeyAnalysisResult {
        val maxSamples = (maxAnalyzeSeconds * sampleRate).coerceAtMost(pcmMono.size)
        val data = if (maxSamples < pcmMono.size) pcmMono.copyOf(maxSamples) else pcmMono
        
        val pooled = DoubleArray(hpcpBins)
        var frames = 0
        var start = 0
        while (start + frameSize <= data.size) {
            val frame = data.copyOfRange(start, start + frameSize)
            val peaks = SpectralPeaks.computeFramePeaks(
                frame = frame,
                sampleRate = sampleRate,
                minFreqHz = 80.0,
                maxFreqHz = 5000.0,
                maxPeaks = 80
            )
            val hpcp = Hpcp.computeHpcp(
                peaks = peaks,
                config = HpcpConfig(bins = hpcpBins, referenceA4Hz = 440.0, harmonics = 8)
            )
            var i = 0
            while (i < hpcp.size) {
                pooled[i] += hpcp[i]
                i++
            }
            frames++
            start += hopSize
        }
        if (frames == 0) {
            return KeyAnalysisResult("Unknown", "--", 0.0, maxSamples / sampleRate)
        }
        var i = 0
        while (i < pooled.size) {
            pooled[i] /= frames.toDouble()
            i++
        }
        val h12 = aggregateTo12(pooled)
        val match = KeyProfileMatcher.matchKey(h12)
        return KeyAnalysisResult(
            key = match.key,
            camelot = match.camelot,
            confidence = match.confidence,
            analyzedSeconds = maxSamples / sampleRate
        )
    }
    
    private fun aggregateTo12(hpcp: DoubleArray): DoubleArray {
        if (hpcp.size == 12) return hpcp.copyOf()
        val out = DoubleArray(12)
        val factor = hpcp.size / 12
        var i = 0
        while (i < hpcp.size) {
            val idx = i % 12
            out[idx] += hpcp[i]
            i++
        }
        var j = 0
        while (j < 12) {
            out[j] /= (hpcp.size / 12.0)
            j++
        }
        return out
    }
}


