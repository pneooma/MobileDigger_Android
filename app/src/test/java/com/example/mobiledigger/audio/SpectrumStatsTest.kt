package com.example.mobiledigger.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class SpectrumStatsTest {
    private val rows = 768
    private val maxHz = 22050

    private fun hz(row: Int) = row * maxHz.toDouble() / (rows - 1)
    private fun power(db: Double) = 10.0.pow(db / 10)

    /** Pink-ish music spectrum: −3 dB/octave-ish slope from 0 dB at 100 Hz. */
    private fun music(f: Double) = -10 * kotlin.math.log10(kotlin.math.max(f, 100.0) / 100.0)

    @Test fun mp3StyleBrickWallAt16k() {
        val p = DoubleArray(rows) { val f = hz(it); power(if (f < 16000) music(f) else -120.0) }
        val c = SpectrumStats.estimateCutoff(p, maxHz)
        assertNotNull(c.cutoffHz)
        assertEquals(16000.0, c.cutoffHz!!.toDouble(), 150.0)
    }

    @Test fun fullBandLosslessHasNoCutoff() {
        // Gradual extra roll-off above 18 kHz (−6 dB per kHz), no cliff.
        val p = DoubleArray(rows) { val f = hz(it); power(music(f) - kotlin.math.max(0.0, (f - 18000) / 1000 * 6)) }
        val c = SpectrumStats.estimateCutoff(p, maxHz)
        assertNull(c.cutoffHz)
        assertEquals(maxHz.toDouble(), c.bandwidthHz.toDouble(), 300.0)
    }

    @Test fun silenceGivesNoCutoff() {
        val c = SpectrumStats.estimateCutoff(DoubleArray(rows), maxHz)
        assertNull(c.cutoffHz)
    }
}
