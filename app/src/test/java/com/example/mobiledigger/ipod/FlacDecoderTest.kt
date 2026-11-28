package com.example.mobiledigger.ipod

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Decodes a real FLAC (path in -Dflac.test=…) to raw PCM and re-encodes it as ALAC, for external checks. */
class FlacDecoderTest {
    @Test fun decodeAndAlac() {
        val path = System.getProperty("flac.test") ?: System.getenv("FLAC_TEST") ?: ""
        val src = File(path)
        assumeTrue("no test FLAC given", path.isNotEmpty() && src.exists())
        val dir = File("build/flac-test").apply { mkdirs() }
        val t0 = System.currentTimeMillis()
        FlacDecoder(src).use { d ->
            val bits = if (d.bitsPerSample > 16) 24 else 16
            val bps = bits / 8
            val enc = AlacEncoder(d.sampleRate, minOf(d.channels, 2), bits)
            val w = AlacM4aWriter(File(dir, "out.m4a"), enc)
            val raw = File(dir, "out.raw").outputStream().buffered(1 shl 20)
            val ch = minOf(d.channels, 2)
            val frame = IntArray(AlacEncoder.FRAME * ch); var fill = 0
            var frames = 0L
            while (true) {
                val blk = d.nextBlock() ?: break
                for (s in blk) for (b in 0 until bps) raw.write(s shr (8 * b))
                var p = 0
                while (p < blk.size) {
                    val n = minOf(frame.size - fill, blk.size - p)
                    System.arraycopy(blk, p, frame, fill, n); fill += n; p += n
                    if (fill == frame.size) { w.write(enc.encodeFrame(frame, AlacEncoder.FRAME), AlacEncoder.FRAME); fill = 0 }
                }
                frames += blk.size / ch
            }
            if (fill > 0) w.write(enc.encodeFrame(frame.copyOf(fill), fill / ch), fill / ch)
            w.finish(); raw.close()
            println("FLAC ${d.sampleRate} Hz ${d.channels} ch ${d.bitsPerSample} bit: $frames frames (header says ${d.totalSamples}), ${System.currentTimeMillis() - t0} ms")
        }
    }
}
