package com.example.mobiledigger.audio.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.example.mobiledigger.util.PerformanceProfiler

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BpmEngineV2Result(
    val bpm: Double,
    val beatsSeconds: DoubleArray,
    val confidence: Double
)

/**
 * New BPM engine using:
 * - Spectral flux ODF with tri-band weighting
 * - Tempogram via autocorrelation over ODF
 * - Harmonic Product Spectrum over tempo axis to reduce half/double confusions
 * - Beat-grid alignment refinement around the best tempo
 */
class BpmEngineV2 {
    
    fun analyze(
        pcmMono: FloatArray,
        sampleRate: Int,
        frameSize: Int = 2048,
        hopSize: Int = 256,
        bpmMin: Int = 60,
        bpmMax: Int = 180
    ): BpmEngineV2Result {
        // Downsample to ~22.05 kHz for faster analysis when input is high-rate
        val targetRate = 22050
        val useDecimation = sampleRate > 32000
        val data = if (useDecimation) decimateToTarget(pcmMono, sampleRate, targetRate) else pcmMono
        val sr = if (useDecimation) targetRate else sampleRate
        val stft = Stft(frameSize)
        val hann = AnalysisWindows.hann(frameSize)
        val numFrames = ((data.size - frameSize).coerceAtLeast(0) / hopSize) + 1
        
        // Build magnitude spectra per frame (parallelized across up to 6 workers)
        val mags = Array(numFrames) { FloatArray(frameSize / 2) }
        val workers = kotlin.math.min(8, Runtime.getRuntime().availableProcessors())
        val tStftStart = android.os.SystemClock.elapsedRealtime()
        runBlocking {
            for (w in 0 until workers) {
                launch(Dispatchers.Default) {
                    var frameIndex = w
                    while (frameIndex < numFrames) {
                        val offset = frameIndex * hopSize
                        if (offset + frameSize > data.size) break
                        val frame = FloatArray(frameSize)
                        var i = 0
                        while (i < frameSize) {
                            frame[i] = data[offset + i] * hann[i]
                            i++
                        }
                        val spec = stft.fft(frame)
                        val bins = frameSize / 2
                        var k = 0
                        var si = 0
                        while (k < bins) {
                            val re = spec[si]
                            val im = spec[si + 1]
                            mags[frameIndex][k] = sqrt(re * re + im * im)
                            k++; si += 2
                        }
                        frameIndex += workers
                    }
                }
            }
        }
        val tStftEnd = android.os.SystemClock.elapsedRealtime()
        PerformanceProfiler.recordOperation("BPM.StftMags", (tStftEnd - tStftStart))
        
        if (numFrames == 0) return BpmEngineV2Result(120.0, doubleArrayOf(), 0.0)
        
        // Tri-band spectral flux ODF
        val tOdfStart = android.os.SystemClock.elapsedRealtime()
        val odf = triBandSpectralFlux(mags, sr, frameSize)
        val odfSmooth = movingAverage(odf, 6)
        normalizeInPlace(odfSmooth)
        val tOdfEnd = android.os.SystemClock.elapsedRealtime()
        PerformanceProfiler.recordOperation("BPM.ODF", (tOdfEnd - tOdfStart))
        
        val frameRate = sr.toFloat() / hopSize.toFloat()
        
        // Tempogram via autocorrelation
        val tTempoStart = android.os.SystemClock.elapsedRealtime()
        val tempoSeries = tempoSeriesFromACF(odfSmooth, frameRate, bpmMin, bpmMax, step = 0.5)
        // Harmonic product spectrum across tempo axis
        val hps = harmonicProductSpectrum(tempoSeries, factors = intArrayOf(2, 3))
        val bestBpm = tempoAtMax(hps, bpmMin, 0.5)
        val tTempoEnd = android.os.SystemClock.elapsedRealtime()
        PerformanceProfiler.recordOperation("BPM.Tempogram+HPS", (tTempoEnd - tTempoStart))
        
        // Beat-grid alignment refinement around best BPM
        val tRefineStart = android.os.SystemClock.elapsedRealtime()
        var bpmRefined = refineWithBeatGrid(odfSmooth, frameRate, bestBpm)
        // Infer BPM from tracked beats and choose best among multiple hypotheses
        var beats = BeatTrackerDP.trackBeats(odfSmooth, bpmRefined, frameRate)
        val bpmFromBeats = bpmFromBeatTimes(beats).takeIf { it in 50.0..200.0 }
        val finalBpm = chooseBestTempo(odfSmooth, frameRate, listOfNotNull(
            bpmRefined,
            bpmRefined / 2.0,
            (bpmRefined * 2.0).coerceAtMost(200.0),
            bpmFromBeats,
            bpmFromBeats?.div(2.0),
            bpmFromBeats?.times(2.0)?.coerceAtMost(200.0)
        ))
        bpmRefined = finalBpm
        beats = BeatTrackerDP.trackBeats(odfSmooth, bpmRefined, frameRate)
        val tRefineEnd = android.os.SystemClock.elapsedRealtime()
        PerformanceProfiler.recordOperation("BPM.Refine+BeatTrack", (tRefineEnd - tRefineStart))
        val conf = confidenceFromSeries(hps)
        
        return BpmEngineV2Result(
            bpm = bpmRefined,
            beatsSeconds = beats,
            confidence = conf
        )
    }
    
    private fun bpmFromBeatTimes(beats: DoubleArray): Double {
        if (beats.size < 3) return 120.0
        val intervals = DoubleArray(beats.size - 1)
        var i = 1
        while (i < beats.size) {
            intervals[i - 1] = (beats[i] - beats[i - 1]).coerceAtLeast(1e-3)
            i++
        }
        intervals.sort()
        val median = intervals[intervals.size / 2]
        return (60.0 / median)
    }
    
    private fun chooseBestTempo(odf: FloatArray, frameRate: Float, candidates: List<Double>): Double {
        var bestBpm = 120.0
        var bestScore = Double.NEGATIVE_INFINITY
        candidates.forEach { bpm ->
            if (bpm in 50.0..200.0) {
                val align = beatGridAlignment(odf, frameRate, bpm)
                val score = align * bpmPrior(bpm)
                if (score > bestScore) {
                    bestScore = score
                    bestBpm = bpm
                }
            }
        }
        return bestBpm
    }
    
    private fun bpmPrior(bpm: Double): Double {
        return when {
            bpm in 80.0..160.0 -> 1.0
            bpm in 60.0..80.0 || bpm in 160.0..180.0 -> 0.9
            else -> 0.7
        }
    }
    
    private fun triBandSpectralFlux(
        mags: Array<FloatArray>,
        sampleRate: Int,
        frameSize: Int
    ): FloatArray {
        val nFrames = mags.size
        val out = FloatArray(nFrames)
        val binHz = sampleRate.toFloat() / frameSize
        val bands = arrayOf(
            0f to 200f,     // low
            200f to 2000f,  // mid
            2000f to 8000f  // high
        )
        val weights = floatArrayOf(0.8f, 1.0f, 0.6f)
        var t = 0
        while (t < nFrames) {
            val prev = if (t > 0) mags[t - 1] else null
            val cur = mags[t]
            var flux = 0.0
            var b = 0
            while (b < bands.size) {
                val (lo, hi) = bands[b]
                val w = weights[b]
                var k = 0
                var bandSum = 0.0
                while (k < cur.size) {
                    val hz = k * binHz
                    if (hz >= lo && hz < hi) {
                        val diff = if (prev != null) (cur[k] - prev[k]) else 0f
                        if (diff > 0f) bandSum += diff.toDouble()
                    }
                    k++
                }
                flux += bandSum * w
                b++
            }
            out[t] = flux.toFloat()
            t++
        }
        return out
    }
    
    private fun tempoSeriesFromACF(
        odf: FloatArray,
        frameRate: Float,
        bpmMin: Int,
        bpmMax: Int,
        step: Double
    ): DoubleArray {
        // Compute ACF once
        val acf = autocorrelation(odf)
        val numSteps = ((bpmMax - bpmMin) / step).toInt() + 1
        val series = DoubleArray(numSteps)
        var i = 0
        var bpm = bpmMin.toDouble()
        while (i < numSteps) {
            val lag = (frameRate * 60.0 / bpm).toInt().coerceAtLeast(1)
            series[i] = if (lag in acf.indices) acf[lag].toDouble() else 0.0
            i++
            bpm += step
        }
        return series
    }
    
    private fun harmonicProductSpectrum(series: DoubleArray, factors: IntArray): DoubleArray {
        val out = series.copyOf()
        for (f in factors) {
            val down = downsample(series, f)
            val n = min(out.size, down.size)
            var i = 0
            while (i < n) {
                out[i] *= down[i]
                i++
            }
        }
        return out
    }
    
    private fun downsample(series: DoubleArray, factor: Int): DoubleArray {
        val n = series.size / factor
        val out = DoubleArray(n)
        var i = 0
        var j = 0
        while (i < n) {
            out[i] = series[j]
            j += factor
            i++
        }
        return out
    }
    
    private fun tempoAtMax(series: DoubleArray, bpmMin: Int, step: Double): Double {
        var bestIdx = 0
        var bestVal = Double.NEGATIVE_INFINITY
        var i = 0
        while (i < series.size) {
            if (series[i] > bestVal) {
                bestVal = series[i]
                bestIdx = i
            }
            i++
        }
        return bpmMin + bestIdx * step
    }
    
    private fun refineWithBeatGrid(odf: FloatArray, frameRate: Float, initialBpm: Double): Double {
        var best = initialBpm
        var bestScore = Double.NEGATIVE_INFINITY
        val candidates = doubleArrayOf(
            initialBpm / 2.0,
            initialBpm,
            initialBpm * 2.0
        )
        for (base in candidates) {
            var delta = -4.0
            while (delta <= 4.0) {
                val bpm = (base + delta).coerceIn(50.0, 200.0)
                val score = beatGridAlignment(odf, frameRate, bpm)
                if (score > bestScore) {
                    bestScore = score
                    best = bpm
                }
                delta += 0.25
            }
        }
        return best
    }
    
    private fun beatGridAlignment(odf: FloatArray, frameRate: Float, bpm: Double): Double {
        val fpb = frameRate * 60.0 / bpm
        if (fpb < 1.0) return 0.0
        var best = 0.0
        val steps = 12
        var s = 0
        while (s < steps) {
            val phase = (fpb * s) / steps
            var i = phase
            var sum = 0.0
            var count = 0
            while (i < odf.size) {
                sum += odf[i.toInt().coerceAtMost(odf.size - 1)].toDouble()
                count++
                i += fpb
            }
            if (count > 0) {
                val avg = sum / count
                if (avg > best) best = avg
            }
            s++
        }
        return best
    }
    
    private fun autocorrelation(x: FloatArray): FloatArray {
        val n = x.size
        val out = FloatArray(n)
        var lag = 0
        while (lag < n) {
            var s = 0.0
            var i = 0
            while (i + lag < n) {
                s += x[i].toDouble() * x[i + lag].toDouble()
                i++
            }
            out[lag] = s.toFloat()
            lag++
        }
        val z = out[0]
        if (z > 0f) {
            var i2 = 0
            while (i2 < out.size) {
                out[i2] /= z
                i2++
            }
        }
        return out
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
    
    private fun normalizeInPlace(x: FloatArray) {
        var maxV = 1e-9f
        var i = 0
        while (i < x.size) {
            val v = abs(x[i])
            if (v > maxV) maxV = v
            i++
        }
        if (maxV > 0f) {
            var j = 0
            while (j < x.size) {
                x[j] /= maxV
                j++
            }
        }
    }
    
    private fun confidenceFromSeries(series: DoubleArray): Double {
        var maxV = Double.NEGATIVE_INFINITY
        var second = Double.NEGATIVE_INFINITY
        var i = 0
        while (i < series.size) {
            val v = series[i]
            if (v > maxV) {
                second = maxV
                maxV = v
            } else if (v > second) {
                second = v
            }
            i++
        }
        if (maxV <= 0.0 || second < 0.0) return 0.0
        val margin = (maxV - second) / (maxV + 1e-9)
        return margin.coerceIn(0.0, 1.0)
    }
    
    private fun decimateToTarget(pcm: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        val factor = (srcRate / dstRate).coerceAtLeast(1)
        if (factor <= 1) return pcm
        val outLen = pcm.size / factor
        val out = FloatArray(outLen)
        var inIdx = 0
        var outIdx = 0
        while (outIdx < outLen) {
            var sum = 0f
            var k = 0
            while (k < factor && inIdx + k < pcm.size) {
                sum += pcm[inIdx + k]
                k++
            }
            out[outIdx] = sum / k.coerceAtLeast(1)
            inIdx += factor
            outIdx++
        }
        return out
    }
}


