package com.example.mobiledigger.audio.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class OnsetFeatures(
    val spectralFlux: FloatArray,
    val highFrequencyContent: FloatArray,
    val complexDomain: FloatArray,
    val energyDerivative: FloatArray,
    val hopSize: Int,
    val frameRateHz: Float
)

internal object OnsetDetectors {
    
    fun computeOnsetFeatures(
        pcm: FloatArray,
        sampleRate: Int,
        frameSize: Int = 2048,
        hopSize: Int = 256
    ): OnsetFeatures {
        val hannWindow = AnalysisWindows.hann(frameSize)
        val stft = Stft(frameSize)
        val numFrames = ((pcm.size - frameSize).coerceAtLeast(0) / hopSize) + 1
        
        val sf = FloatArray(numFrames)
        val hfc = FloatArray(numFrames)
        val cd = FloatArray(numFrames)
        val ed = FloatArray(numFrames)
        
        var prevMag: FloatArray? = null
        var prevPhase: FloatArray? = null
        var prevEnergy = 0.0
        
        var frameIndex = 0
        var offset = 0
        while (offset + frameSize <= pcm.size && frameIndex < numFrames) {
            val frame = FloatArray(frameSize)
            for (i in 0 until frameSize) {
                frame[i] = pcm[offset + i] * hannWindow[i]
            }
            val spectrum = stft.fft(frame) // interleaved re, im
            val bins = frameSize / 2
            val mag = FloatArray(bins)
            val phase = FloatArray(bins)
            var energy = 0.0
            var sfAcc = 0.0
            var hfcAcc = 0.0
            var cdAcc = 0.0
            
            var bin = 0
            var specIndex = 0
            while (bin < bins) {
                val re = spectrum[specIndex]
                val im = spectrum[specIndex + 1]
                val m = sqrt(re * re + im * im)
                val p = atan2(im, re)
                mag[bin] = m
                phase[bin] = p
                energy += m
                
                val prevM = prevMag?.get(bin) ?: 0f
                val diff = max(0f, m - prevM)
                sfAcc += diff
                
                val freqWeight = (bin + 1).toDouble()
                hfcAcc += m * freqWeight
                
                if (prevMag != null && prevPhase != null) {
                    val dPhase = p - prevPhase!![bin]
                    val pr = prevM * kotlin.math.cos(dPhase)
                    val pi = prevM * kotlin.math.sin(dPhase)
                    val cr = (m - pr).toDouble()
                    val ci = (0.0 - pi)
                    cdAcc += sqrt(cr * cr + ci * ci)
                }
                
                bin++
                specIndex += 2
            }
            
            val energyDeriv = (energy - prevEnergy).toFloat().coerceAtLeast(0f)
            prevEnergy = energy
            prevMag = mag
            prevPhase = phase
            
            sf[frameIndex] = sfAcc.toFloat()
            hfc[frameIndex] = hfcAcc.toFloat()
            cd[frameIndex] = cdAcc.toFloat()
            ed[frameIndex] = energyDeriv
            
            frameIndex++
            offset += hopSize
        }
        
        val frameRate = sampleRate.toFloat() / hopSize.toFloat()
        return OnsetFeatures(
            spectralFlux = normalize(sf),
            highFrequencyContent = normalize(hfc),
            complexDomain = normalize(cd),
            energyDerivative = normalize(ed),
            hopSize = hopSize,
            frameRateHz = frameRate
        )
    }
    
    fun combineOdFs(
        features: OnsetFeatures,
        weights: FloatArray = floatArrayOf(0.4f, 0.2f, 0.3f, 0.1f),
        medianFilter: Int = 5,
        localMaxRadius: Int = 2
    ): FloatArray {
        val n = features.spectralFlux.size
        val combined = FloatArray(n)
        var i = 0
        while (i < n) {
            val v = (features.spectralFlux[i] * weights[0]) +
                (features.highFrequencyContent[i] * weights[1]) +
                (features.complexDomain[i] * weights[2]) +
                (features.energyDerivative[i] * weights[3])
            combined[i] = v
            i++
        }
        val smoothed = medianSmooth(combined, medianFilter)
        return peakEnhance(smoothed, localMaxRadius)
    }
    
    private fun normalize(x: FloatArray): FloatArray {
        var maxV = 1e-9f
        for (v in x) {
            if (v > maxV) maxV = v
        }
        if (maxV <= 0f) return x.copyOf()
        val y = FloatArray(x.size)
        var i = 0
        while (i < x.size) {
            y[i] = x[i] / maxV
            i++
        }
        return y
    }
    
    private fun medianSmooth(x: FloatArray, window: Int): FloatArray {
        if (window <= 1) return x.copyOf()
        val half = window / 2
        val out = FloatArray(x.size)
        val buf = FloatArray(window)
        var i = 0
        while (i < x.size) {
            var k = 0
            var j = i - half
            while (j <= i + half) {
                val idx = when {
                    j < 0 -> 0
                    j >= x.size -> x.size - 1
                    else -> j
                }
                buf[k] = x[idx]
                k++
                j++
            }
            buf.sort()
            out[i] = buf[k / 2]
            i++
        }
        return out
    }
    
    private fun peakEnhance(x: FloatArray, radius: Int): FloatArray {
        if (radius <= 0) return x.copyOf()
        val out = FloatArray(x.size)
        var i = 0
        while (i < x.size) {
            val center = x[i]
            var isPeak = true
            var r = 1
            while (r <= radius) {
                val left = x[(i - r).coerceAtLeast(0)]
                val right = x[(i + r).coerceAtMost(x.size - 1)]
                if (left > center || right > center) {
                    isPeak = false
                    break
                }
                r++
            }
            out[i] = if (isPeak) center else center * 0.5f
            i++
        }
        return out
    }
}

internal object AnalysisWindows {
    fun hann(size: Int): FloatArray {
        val w = FloatArray(size)
        var i = 0
        val denom = (size - 1).toDouble()
        while (i < size) {
            w[i] = (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / denom)).toFloat()
            i++
        }
        return w
    }
}

internal class Stft(private val size: Int) {
    private val fftBuf = FloatArray(size * 2)
    
    fun fft(frame: FloatArray): FloatArray {
        require(frame.size == size)
        var i = 0
        var j = 0
        while (i < size) {
            fftBuf[j] = frame[i]
            fftBuf[j + 1] = 0f
            i++
            j += 2
        }
        fftInPlace(fftBuf, size)
        return fftBuf.copyOf()
    }
    
    private fun fftInPlace(data: FloatArray, n: Int) {
        if (n <= 1) return
        var j = 0
        var i = 1
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = data[i * 2]
                val ti = data[i * 2 + 1]
                data[i * 2] = data[j * 2]
                data[i * 2 + 1] = data[j * 2 + 1]
                data[j * 2] = tr
                data[j * 2 + 1] = ti
            }
            i++
        }
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * Math.PI / len).toFloat()
            val wlr = kotlin.math.cos(ang)
            val wli = kotlin.math.sin(ang)
            var start = 0
            while (start < n) {
                var wr = 1f
                var wi = 0f
                var k = 0
                while (k < len / 2) {
                    val i0 = (start + k) * 2
                    val i1 = (start + k + len / 2) * 2
                    val ur = data[i0]
                    val ui = data[i0 + 1]
                    val vr = data[i1] * wr - data[i1 + 1] * wi
                    val vi = data[i1] * wi + data[i1 + 1] * wr
                    data[i0] = ur + vr
                    data[i0 + 1] = ui + vi
                    data[i1] = ur - vr
                    data[i1 + 1] = ui - vi
                    val nwr = wr * wlr - wi * wli
                    val nwi = wr * wli + wi * wlr
                    wr = nwr
                    wi = nwi
                    k++
                }
                start += len
            }
            len = len shl 1
        }
    }
}


