package com.example.mobiledigger.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Fixtures: 2 s of a 1 kHz sine at ffmpeg's default 1/8 amplitude × volume 0.5
 * (peak 2048 mono, ≈1448 after ffmpeg's -3 dB stereo upmix).
 * A mis-parsed file (wrong bit depth / endianness / header read as audio) shows up as
 * noise: wrong zero-crossing rate and/or wrong peak — the "solid red spectrogram" bug.
 */
class PcmContainerReaderTest {

    private fun decode(name: String, maxSeconds: Int = 180): DecodedAudio {
        val stream = javaClass.classLoader!!.getResourceAsStream("spek/$name")
        assertNotNull("missing fixture $name", stream)
        val reader = PcmContainerReader(maxSeconds, now = { 0L }, log = { println("[$name] $it") })
        val d = stream!!.use { reader.read(it, Long.MAX_VALUE) }
        assertNotNull("$name was not decoded", d)
        return d!!
    }

    private fun assertSine(name: String, rate: Int, seconds: Double = 2.0, maxSeconds: Int = 180, peakExpected: Int = 1448) {
        val d = decode(name, maxSeconds)
        assertEquals("$name sample rate", rate, d.sampleRate)
        assertEquals("$name length", (rate * seconds).toInt().toDouble(), d.mono.size.toDouble(), rate * 0.01)

        // Skip the first 10 ms (ffmpeg sine fade-in), measure peak and frequency.
        val s = d.mono.copyOfRange(rate / 100, d.mono.size)
        val peak = s.maxOf { kotlin.math.abs(it.toInt()) }
        assertEquals("$name peak", peakExpected.toDouble(), peak.toDouble(), peakExpected * 0.1)
        var crossings = 0
        for (i in 1 until s.size) if ((s[i - 1] < 0) != (s[i] < 0)) crossings++
        val freq = crossings / 2.0 / (s.size.toDouble() / rate)
        assertEquals("$name frequency", 1000.0, freq, 15.0)
    }

    @Test fun aiff16() = assertSine("s16.aiff", 48000)
    @Test fun aiff24() = assertSine("s24.aiff", 48000)
    @Test fun aiff32() = assertSine("s32.aiff", 48000)
    @Test fun aiffMono44() = assertSine("mono44.aiff", 44100, peakExpected = 2048)
    @Test fun aifcSowtLittleEndian() = assertSine("sowt.aifc", 48000)
    @Test fun aifcFloat32() = assertSine("fl32.aiff", 48000)
    @Test fun wav16() = assertSine("s16.wav", 48000)
    @Test fun wav24Extensible() = assertSine("s24.wav", 48000)
    @Test fun wavFloat() = assertSine("f32.wav", 48000)
    @Test fun wavUnsigned8() = assertSine("u8.wav", 48000)

    @Test fun capsToMaxSeconds() = assertSine("s24.aiff", 48000, seconds = 1.0, maxSeconds = 1)
}
