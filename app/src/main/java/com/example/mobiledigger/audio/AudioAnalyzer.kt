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
    private val fft = EnhancedFFTProcessor()

    fun analyze(samples: ShortArray, sampleRate: Int = 44100): AudioAnalysisResult {
        val mono = toFloatMono(samples)

        val windowSize = 4096
        val hopSize = 1024

        val stft = computeMagnitudeStft(mono, windowSize, hopSize)

        val bpmPair = estimateBpmFromStft(stft, sampleRate, hopSize)
        val chroma = computeChromaFromStft(stft, sampleRate, windowSize)
        val keyPair = estimateKeyFromChroma(chroma)

        return AudioAnalysisResult(
            bpm = bpmPair.first,
            bpmConfidence = bpmPair.second,
            key = keyPair.first,
            keyConfidence = keyPair.second
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

    private fun computeMagnitudeStft(
        mono: FloatArray,
        windowSize: Int,
        hopSize: Int
    ): Array<FloatArray> {
        if (mono.isEmpty()) return emptyArray()
        val numFrames = max(0, (mono.size - windowSize) / hopSize + 1)
        val frames = Array(numFrames) { FloatArray(windowSize / 2) }
        val windowed = FloatArray(windowSize)
        var prevMag: FloatArray? = null
        var frameIndex = 0
        var start = 0
        while (start + windowSize <= mono.size && frameIndex < numFrames) {
            for (i in 0 until windowSize) {
                windowed[i] = mono[start + i]
            }
            val w = fft.applyWindow(windowed, WindowFunction.HANNING)
            val fftBins = fft.performFFT(w)
            val mags = FloatArray(windowSize / 2)
            for (k in mags.indices) {
                val real = fftBins[k * 2]
                val imag = fftBins[k * 2 + 1]
                mags[k] = sqrt(real * real + imag * imag)
            }
            frames[frameIndex] = mags
            prevMag = mags
            frameIndex++
            start += hopSize
        }
        return frames
    }

    private fun estimateBpmFromStft(
        stft: Array<FloatArray>,
        sampleRate: Int,
        hopSize: Int
    ): Pair<Float?, Float> {
        if (stft.isEmpty()) return Pair(null, 0f)
        // Spectral flux (positive differences)
        val flux = FloatArray(stft.size)
        var prev: FloatArray? = null
        for (t in stft.indices) {
            val cur = stft[t]
            if (prev != null) {
                var sum = 0f
                val n = min(prev!!.size, cur.size)
                for (i in 0 until n) {
                    val diff = cur[i] - prev!![i]
                    if (diff > 0f) sum += diff
                }
                flux[t] = sum
            } else {
                flux[t] = 0f
            }
            prev = cur
        }
        // Normalize and smooth
        normalizeInPlace(flux)
        val smoothed = movingAverage(flux, 8)

        // Autocorrelation of envelope
        val ac = autocorrelate(smoothed)
        // Convert lag to BPM; envelope rate = sampleRate / hopSize
        val envFs = sampleRate.toFloat() / hopSize
        val minBpm = 60f
        val maxBpm = 145f
        var bestBpm: Float? = null
        var bestScore = 0f
        for (lag in 1 until ac.size) {
            val bpm = 60f * envFs / lag
            if (bpm in minBpm..maxBpm) {
                val score = ac[lag]
                if (score > bestScore) {
                    bestScore = score
                    bestBpm = bpm
                }
            }
        }
        // Fold extreme BPMs into target range by halving/doubling as needed
        if (bestBpm != null) {
            var folded = bestBpm
            while (folded!! > maxBpm) folded /= 2f
            while (folded < minBpm) folded *= 2f
            bestBpm = folded
        }
        val confidence = if (ac.isNotEmpty()) bestScore / (ac.maxOrNull() ?: 1f) else 0f
        return Pair(bestBpm, confidence.coerceIn(0f, 1f))
    }

    private fun computeChromaFromStft(
        stft: Array<FloatArray>,
        sampleRate: Int,
        windowSize: Int
    ): FloatArray {
        if (stft.isEmpty()) return FloatArray(12) { 0f }
        val chroma = FloatArray(12)
        val binHz = sampleRate.toFloat() / windowSize
        for (t in stft.indices) {
            val mags = stft[t]
            var frameSum = 0f
            for (k in mags.indices) {
                val freq = k * binHz
                if (freq < 27.5f || freq > 5000f) continue
                val pc = hzToPitchClass(freq)
                val weight = mags[k]
                chroma[pc] += weight
                frameSum += weight
            }
        }
        // Log compression and normalization
        for (i in chroma.indices) {
            chroma[i] = ln(1f + chroma[i])
        }
        normalizeInPlace(chroma)
        return chroma
    }

    private fun estimateKeyFromChroma(chroma: FloatArray): Pair<String?, Float> {
        if (chroma.sum() == 0f) return Pair(null, 0f)

        val majorProfile = floatArrayOf(
            6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f,
            2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f
        )
        val minorProfile = floatArrayOf(
            6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f,
            2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f
        )
        val keys = arrayOf(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
        )

        var bestScore = Float.NEGATIVE_INFINITY
        var bestKey: String? = null
        // Normalize chroma and test all rotations; prefer stronger separation between top-2
        val chromaNorm = chroma.copyOf().also { normalizeInPlace(it) }
        for (shift in 0 until 12) {
            val majScore = cosineSimilarity(chromaNorm, rotate(majorProfile, shift))
            val minScore = cosineSimilarity(chromaNorm, rotate(minorProfile, shift))
            if (majScore > bestScore) {
                bestScore = majScore
                bestKey = keys[shift] + " major"
            }
            if (minScore > bestScore) {
                bestScore = minScore
                bestKey = keys[shift] + " minor"
            }
        }
        val confidence = ((bestScore + 1f) / 2f).coerceIn(0f, 1f)
        return Pair(bestKey, confidence)
    }

    private fun rotate(arr: FloatArray, shift: Int): FloatArray {
        val n = arr.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            out[i] = arr[(i - shift + n) % n]
        }
        return out
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0f || nb == 0f) return -1f
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1f, 1f)
    }

    private fun hzToPitchClass(freq: Float): Int {
        // MIDI note for frequency: 69 + 12*log2(f/440)
        val midi = 69.0 + 12.0 * (kotlin.math.log(freq / 440.0, 2.0))
        val pc = (midi.roundToInt() % 12 + 12) % 12
        return pc
    }

    private fun movingAverage(x: FloatArray, win: Int): FloatArray {
        if (x.isEmpty() || win <= 1) return x.copyOf()
        val w = win.coerceAtMost(x.size)
        val out = FloatArray(x.size)
        var sum = 0f
        for (i in 0 until x.size) {
            sum += x[i]
            if (i >= w) sum -= x[i - w]
            out[i] = sum / min(i + 1, w)
        }
        return out
    }

    private fun autocorrelate(x: FloatArray): FloatArray {
        val n = x.size
        val out = FloatArray(n)
        for (lag in 0 until n) {
            var s = 0f
            val maxI = n - lag
            for (i in 0 until maxI) {
                s += x[i] * x[i + lag]
            }
            out[lag] = s
        }
        // Normalize by zero-lag
        val z = out[0]
        if (z > 0f) {
            for (i in out.indices) out[i] /= z
        }
        return out
    }

    private fun normalizeInPlace(x: FloatArray) {
        var maxVal = 0f
        for (v in x) maxVal = max(maxVal, abs(v))
        if (maxVal <= 0f) return
        for (i in x.indices) x[i] /= maxVal
    }
}


