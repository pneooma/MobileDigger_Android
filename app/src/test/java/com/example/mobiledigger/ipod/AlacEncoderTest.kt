package com.example.mobiledigger.ipod

import org.junit.Test
import java.io.File
import java.util.Random

/** Writes ALAC .m4a files + the original PCM for a bit-exact check with ffmpeg / AudioToolbox. */
class AlacEncoderTest {
    private fun signal(frames: Int, ch: Int, bits: Int, seed: Long): IntArray {
        val r = Random(seed)
        val max = (1 shl (bits - 1)) - 1
        return IntArray(frames * ch) { idx ->
            val i = idx / ch; val c = idx % ch
            val t = i / 44100.0
            when {
                i in 30000..52000 -> 0                                  // silence → zero runs
                i in 60000..61000 -> if (c == 0) max else -max - 1      // full-scale extremes
                else -> {
                    val s = 0.45 * Math.sin(2 * Math.PI * (220.0 + 110 * c) * t) + 0.25 * Math.sin(2 * Math.PI * 3150 * t) +
                        0.08 * (r.nextDouble() * 2 - 1)
                    (s * max).toInt().coerceIn(-max - 1, max)
                }
            }
        }
    }

    private fun run(name: String, ch: Int, bits: Int, frames: Int) {
        val dir = File("build/alac-test").apply { mkdirs() }
        val pcm = signal(frames, ch, bits, 7)
        val enc = AlacEncoder(44100, ch, bits)
        val w = AlacM4aWriter(File(dir, "$name.m4a"), enc)
        var pos = 0
        while (pos < frames) {
            val n = minOf(AlacEncoder.FRAME, frames - pos)
            w.write(enc.encodeFrame(pcm.copyOfRange(pos * ch, (pos + n) * ch), n), n)
            pos += n
        }
        w.finish()
        // Original as little-endian PCM (s16le / s24le) for comparison with the decoded output.
        val bps = bits / 8
        val raw = ByteArray(pcm.size * bps)
        for ((i, s) in pcm.withIndex()) for (b in 0 until bps) raw[i * bps + b] = (s shr (8 * b)).toByte()
        File(dir, "$name.raw").writeBytes(raw)
    }

    @Test fun stereo16() = run("s16", 2, 16, 100_000)
    @Test fun stereo24() = run("s24", 2, 24, 100_000)
    @Test fun mono16() = run("m16", 1, 16, 50_123)
}
