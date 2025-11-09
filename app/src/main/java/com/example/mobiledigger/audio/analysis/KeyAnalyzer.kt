package com.example.mobiledigger.audio.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        
        // 1) Estimate tuning from a subset of frames
        val tuningRef = estimateTuningA4Hz(
            data = data,
            sampleRate = sampleRate,
            frameSize = frameSize,
            hopSize = hopSize
        )
        
        val pooled = DoubleArray(hpcpBins)
        var frames = 0
        // Parallelize frame processing across up to 6 workers
        val workers = 6
        val partials = Array(workers) { DoubleArray(hpcpBins) }
        runBlocking {
            for (w in 0 until workers) {
                launch(Dispatchers.Default) {
                    var start = w
                    var localFrames = 0
                    while (true) {
                        val offset = start * hopSize
                        if (offset + frameSize > data.size) break
                        val frame = data.copyOfRange(offset, offset + frameSize)
                        val peaks = SpectralPeaks.computeFramePeaks(
                            frame = frame,
                            sampleRate = sampleRate,
                            minFreqHz = 80.0,
                            maxFreqHz = 5000.0,
                            maxPeaks = 80
                        )
                        val hpcp = Hpcp.computeHpcp(
                            peaks = peaks,
                            config = HpcpConfig(bins = hpcpBins, referenceA4Hz = tuningRef, harmonics = 8)
                        )
                        var i = 0
                        while (i < hpcp.size) {
                            partials[w][i] += hpcp[i]
                            i++
                        }
                        localFrames++
                        start += workers
                    }
                    synchronized(this@KeyAnalyzer) {
                        frames += localFrames
                    }
                }
            }
        }
        var wi = 0
        while (wi < workers) {
            var j = 0
            while (j < hpcpBins) {
                pooled[j] += partials[wi][j]
                j++
            }
            wi++
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
        val match = KeyProfileMatcher.matchKey(emphasizeTonalPeaks(h12))
        return KeyAnalysisResult(
            key = match.key,
            camelot = match.camelot,
            confidence = match.confidence,
            analyzedSeconds = maxSamples / sampleRate
        )
    }
    
    private fun estimateTuningA4Hz(
        data: FloatArray,
        sampleRate: Int,
        frameSize: Int,
        hopSize: Int
    ): Double {
        // Analyze up to first ~20 frames for speed
        val stft = Stft(frameSize)
        val hann = AnalysisWindows.hann(frameSize)
        val cents = ArrayList<Double>()
        var frames = 0
        var start = 0
        while (start + frameSize <= data.size && frames < 20) {
            val frame = FloatArray(frameSize)
            var i = 0
            while (i < frameSize) {
                frame[i] = data[start + i] * hann[i]
                i++
            }
            val spec = stft.fft(frame)
            val bins = frameSize / 2
            var k = 1
            var si = 2
            while (k < bins - 1) {
                val re = spec[si].toDouble()
                val im = spec[si + 1].toDouble()
                val mag = kotlin.math.hypot(re, im)
                val reL = spec[si - 2].toDouble()
                val imL = spec[si - 1].toDouble()
                val reR = spec[si + 2].toDouble()
                val imR = spec[si + 3].toDouble()
                val mL = kotlin.math.hypot(reL, imL)
                val mR = kotlin.math.hypot(reR, imR)
                if (mag > mL && mag > mR) {
                    // Quadratic interpolation
                    val alpha = mL
                    val beta = mag
                    val gamma = mR
                    val denom = (alpha - 2 * beta + gamma)
                    val p = if (kotlin.math.abs(denom) > 1e-12) 0.5 * (alpha - gamma) / denom else 0.0
                    val peakBin = k + p
                    val freq = (peakBin * sampleRate) / frameSize.toDouble()
                    if (freq > 80.0 && freq < 5000.0) {
                        val centsOff = centsFromA440(freq)
                        cents.add(centsOff)
                    }
                }
                k++
                si += 2
            }
            frames++
            start += hopSize
        }
        if (cents.isEmpty()) return 440.0
        cents.sort()
        val median = cents[cents.size / 2]
        return 440.0 * Math.pow(2.0, median / 1200.0)
    }
    
    private fun centsFromA440(freq: Double): Double {
        return 1200.0 * kotlin.math.log(freq / 440.0, 2.0)
    }
    
    private fun aggregateTo12(hpcp: DoubleArray): DoubleArray {
        if (hpcp.size == 12) return hpcp.copyOf()
        val out = DoubleArray(12)
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
    
    private fun emphasizeTonalPeaks(h12: DoubleArray): DoubleArray {
        // Lightly emphasize strongest bins to help key profile matching
        val out = h12.copyOf()
        var maxV = 0.0
        var i = 0
        while (i < out.size) {
            if (out[i] > maxV) maxV = out[i]
            i++
        }
        if (maxV <= 0.0) return out
        i = 0
        while (i < out.size) {
            val v = out[i] / maxV
            out[i] *= (0.8 + 0.2 * v) // 0.8..1.0 boost
            i++
        }
        return out
    }
}


