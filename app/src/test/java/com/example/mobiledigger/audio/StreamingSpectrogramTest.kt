package com.example.mobiledigger.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StreamingSpectrogramTest {
    private val sr = 44100
    private fun tone(seconds: Int, hz: Double) = ShortArray(sr * seconds) { (sin(2 * PI * hz * it / sr) * 12000).toInt().toShort() }

    private fun build(samples: ShortArray, blocks: List<Int>, maxColumns: Int = 100_000): StreamingSpectrogram {
        val s = StreamingSpectrogram(sr, 8192, 768, 22050, 10, maxColumns)
        var pos = 0; var k = 0
        while (pos < samples.size) {
            val n = minOf(blocks[k++ % blocks.size], samples.size - pos)
            s.accept(samples.copyOfRange(pos, pos + n), n); pos += n
        }
        s.finish()
        return s
    }

    private fun image(s: StreamingSpectrogram, w: Int) = Array(s.height) { y -> IntArray(w).also { s.renderRow(y, w, it) } }

    @Test fun toneLandsOnItsRowAndColumnCountMatches() {
        val s = build(tone(5, 1000.0), listOf(32768))
        assertEquals(50, s.columnCount) // ceil(5 s * 44100 / 4410)
        val rows = s.rowMeanPower()
        val loudest = rows.indices.maxByOrNull { rows[it] }!!
        val hz = loudest * 22050.0 / 768
        assertEquals(1000.0, hz, 40.0)
        // The tone row renders as the hottest colour (yellow), silence above it stays dark
        val img = image(s, s.columnCount)
        assertEquals(0xFFFFFF00.toInt(), img[s.height - 1 - loudest][25])
        assertEquals(0xFF000000.toInt(), img[0][25])
    }

    @Test fun blockSizeDoesNotChangeTheResult() {
        val audio = tone(4, 2500.0)
        val a = image(build(audio, listOf(32768)), 40)
        val b = image(build(audio, listOf(1000, 7, 50000, 333)), 40)
        for (y in a.indices) assertArrayEquals("row $y", a[y], b[y])
    }

    @Test fun stopsAtMaxColumnsAndPoolsWideImages() {
        val s = build(tone(10, 500.0), listOf(32768), maxColumns = 30)
        assertEquals(30, s.columnCount)
        val row = IntArray(10); s.renderRow(0, 10, row) // 3 columns per pixel
        assertTrue(row.all { it == 0xFF000000.toInt() })
    }
}
