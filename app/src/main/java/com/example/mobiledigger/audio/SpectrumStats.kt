package com.example.mobiledigger.audio

import kotlin.math.log10
import kotlin.math.max

/** What the spectrogram actually shows — used for honest axes and the share card. */
data class SpectrogramInfo(
    val sampleRate: Int,
    val channels: Int,
    /** null for lossy sources (MP3/AAC/OGG have no bit depth). */
    val bitsPerSample: Int?,
    val isFloat: Boolean,
    val codec: String,
    /** Seconds of audio decoded and drawn (≤ AudioManager.SPECTROGRAM_MAX_SECONDS). */
    val analyzedSeconds: Float,
    /** Top of the (linear) frequency axis, already clamped to Nyquist. */
    val maxFreqHz: Int,
    /** Frequency of a brick-wall drop (typical of lossy encoding/upsampling), or null if none. */
    val cutoffHz: Int?,
    /** Highest frequency that still has meaningful energy (≈ "bandwidth"). */
    val bandwidthHz: Int,
    /** How many seconds were asked for (the track may be shorter). */
    val requestedSeconds: Int = 180
)

object SpectrumStats {

    data class Cutoff(val cutoffHz: Int?, val bandwidthHz: Int)

    /**
     * Estimates the high-frequency cutoff from per-row average power (row 0 = 0 Hz,
     * last row = [maxFreqHz], linear spacing).
     *
     * A lossy encoder's low-pass is a CLIFF: energy collapses by tens of dB within a few
     * hundred Hz and stays down. Natural roll-off in a lossless master is gradual. So we
     * look for the steepest drop between the band just below and the band just above each
     * row, and only call it a cutoff if it's ≥ [minDropDb] and the band below is real
     * signal (within [signalFloorDb] of the loudest row).
     */
    fun estimateCutoff(
        rowMeanPower: DoubleArray,
        maxFreqHz: Int,
        minDropDb: Double = 25.0,
        signalFloorDb: Double = -80.0
    ): Cutoff {
        val n = rowMeanPower.size
        if (n < 8) return Cutoff(null, maxFreqHz)
        val peak = rowMeanPower.maxOrNull()?.takeIf { it > 0 } ?: return Cutoff(null, 0)
        val db = DoubleArray(n) { 10 * log10(max(rowMeanPower[it], 1e-30) / peak) }
        val hzPerRow = maxFreqHz.toDouble() / (n - 1)
        val k = max(2, (300.0 / hzPerRow).toInt()) // ~300 Hz bands either side

        // Bandwidth: highest row (smoothed over k rows) still above the signal floor.
        var bandwidthRow = 0
        for (i in n - 1 downTo 0) {
            val lo = max(0, i - k + 1)
            var sum = 0.0
            for (j in lo..i) sum += db[j]
            if (sum / (i - lo + 1) > signalFloorDb) { bandwidthRow = i; break }
        }

        var bestDrop = 0.0
        var bestRow = -1
        for (i in k until n - k) {
            var below = 0.0
            for (j in i - k until i) below += db[j]
            below /= k
            var above = 0.0
            for (j in i + 1..i + k) above += db[j]
            above /= k
            val drop = below - above
            if (below > signalFloorDb && drop > bestDrop) { bestDrop = drop; bestRow = i }
        }
        val cutoff = if (bestRow >= 0 && bestDrop >= minDropDb) (bestRow * hzPerRow).toInt() else null
        return Cutoff(cutoff, (bandwidthRow * hzPerRow).toInt())
    }
}
