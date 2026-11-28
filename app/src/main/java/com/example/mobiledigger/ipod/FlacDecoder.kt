package com.example.mobiledigger.ipod

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * FLAC decoder (pure Kotlin, lossless at any bit depth). Android's own FLAC decoder can't be relied
 * on for 24-bit output: asked for float it handed back samples in another layout, which halved the
 * length and turned the converted song into noise. This one reads the stream itself.
 * Supports the whole format: constant / verbatim / fixed / LPC subframes, wasted bits, Rice and
 * escaped partitions, independent and left/side, side/right, mid/side stereo.
 */
class FlacDecoder(file: File) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    private val buf: MappedByteBuffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
    private val size = raf.length()
    private var pos = 0L        // bit position

    var sampleRate = 0; private set
    var channels = 0; private set
    var bitsPerSample = 0; private set
    var totalSamples = 0L; private set
    private var maxBlock = 4096
    var decodedSamples = 0L; private set

    init {
        if (readBits(32).toInt() != 0x664C6143) throw IOException("Not a FLAC file")
        var last = false
        while (!last) {
            last = readBits(1) == 1L
            val type = readBits(7).toInt()
            val len = readBits(24)
            if (type == 0) {
                readBits(16); maxBlock = readBits(16).toInt(); readBits(24); readBits(24)
                sampleRate = readBits(20).toInt(); channels = readBits(3).toInt() + 1
                bitsPerSample = readBits(5).toInt() + 1; totalSamples = readBits(36)
                pos += 128 // MD5
            } else pos += len * 8
        }
    }

    val fraction get() = if (totalSamples > 0) (decodedSamples.toFloat() / totalSamples).coerceIn(0f, 1f) else (pos / 8f / size).coerceIn(0f, 1f)

    // ------------------------------------------------------------------ bits

    private fun byteAt(i: Long): Int = buf.get(i.toInt()).toInt() and 0xFF

    private fun readBits(n: Int): Long {
        var v = 0L
        var left = n
        while (left > 0) {
            val byteIdx = pos ushr 3
            if (byteIdx >= size) throw IOException("Unexpected end of FLAC data")
            val bitOff = (pos and 7).toInt()
            val take = minOf(left, 8 - bitOff)
            val b = (byteAt(byteIdx) shr (8 - bitOff - take)) and ((1 shl take) - 1)
            v = (v shl take) or b.toLong()
            pos += take; left -= take
        }
        return v
    }

    private fun readSigned(n: Int): Long {
        if (n == 0) return 0
        val v = readBits(n)
        return (v shl (64 - n)) shr (64 - n)
    }

    private fun readUnary(): Int {
        var n = 0
        while (true) {
            val byteIdx = pos ushr 3
            if (byteIdx >= size) throw IOException("Unexpected end of FLAC data")
            val bitOff = (pos and 7).toInt()
            // fast path: rest of this byte is zero
            val rest = byteAt(byteIdx) and (0xFF ushr bitOff)
            if (rest == 0) { n += 8 - bitOff; pos += 8 - bitOff; continue }
            val lead = Integer.numberOfLeadingZeros(rest) - 24 - bitOff
            n += lead; pos += lead + 1
            return n
        }
    }

    private fun align() { pos = (pos + 7) and 7L.inv() }

    // ------------------------------------------------------------------ frames

    /** Next block as interleaved samples at the stream's bit depth, or null at the end. */
    fun nextBlock(): IntArray? {
        align()
        // Find the frame sync (0xFFF8 / 0xFFF9)
        while (true) {
            val i = pos ushr 3
            if (i + 1 >= size) return null
            if (byteAt(i) == 0xFF && (byteAt(i + 1) and 0xFE) == 0xF8) break
            pos += 8
        }
        readBits(15); readBits(1)
        val bsCode = readBits(4).toInt()
        val srCode = readBits(4).toInt()
        val chAssign = readBits(4).toInt()
        val ssCode = readBits(3).toInt()
        readBits(1)
        // frame / sample number, UTF-8 style
        val first = readBits(8).toInt()
        var extra = 0
        var m = 0x80
        while (first and m != 0) { extra++; m = m shr 1 }
        repeat(maxOf(0, extra - 1)) { readBits(8) }
        val blockSize = when (bsCode) {
            1 -> 192
            in 2..5 -> 576 shl (bsCode - 2)
            6 -> readBits(8).toInt() + 1
            7 -> readBits(16).toInt() + 1
            in 8..15 -> 256 shl (bsCode - 8)
            else -> throw IOException("Bad FLAC block size")
        }
        when (srCode) { 12 -> readBits(8); 13, 14 -> readBits(16) }
        readBits(8) // CRC-8
        val bps = when (ssCode) { 0 -> bitsPerSample; 1 -> 8; 2 -> 12; 4 -> 16; 5 -> 20; 6 -> 24; 7 -> 32; else -> bitsPerSample }
        val nCh = if (chAssign < 8) chAssign + 1 else 2
        val data = Array(nCh) { ch ->
            val extraBit = when (chAssign) { 8 -> if (ch == 1) 1 else 0; 9 -> if (ch == 0) 1 else 0; 10 -> if (ch == 1) 1 else 0; else -> 0 }
            subframe(blockSize, bps + extraBit)
        }
        align(); readBits(16) // CRC-16
        // Stereo decorrelation
        when (chAssign) {
            8 -> for (i in 0 until blockSize) data[1][i] = data[0][i] - data[1][i]
            9 -> for (i in 0 until blockSize) data[0][i] = data[0][i] + data[1][i]
            10 -> for (i in 0 until blockSize) {
                var mid = data[0][i]; val side = data[1][i]
                mid = (mid shl 1) or (side and 1)
                data[0][i] = (mid + side) shr 1; data[1][i] = (mid - side) shr 1
            }
        }
        decodedSamples += blockSize
        val out = IntArray(blockSize * nCh)
        for (i in 0 until blockSize) for (c in 0 until nCh) out[i * nCh + c] = data[c][i].toInt()
        return out
    }

    private fun subframe(n: Int, bps: Int): LongArray {
        readBits(1)
        val type = readBits(6).toInt()
        var wasted = 0
        if (readBits(1) == 1L) wasted = readUnary() + 1
        val b = bps - wasted
        val s = LongArray(n)
        when {
            type == 0 -> { val v = readSigned(b); s.fill(v) }
            type == 1 -> for (i in 0 until n) s[i] = readSigned(b)
            type in 8..12 -> {
                val order = type - 8
                for (i in 0 until order) s[i] = readSigned(b)
                residual(s, n, order)
                for (i in order until n) s[i] += when (order) {
                    0 -> 0L
                    1 -> s[i - 1]
                    2 -> 2 * s[i - 1] - s[i - 2]
                    3 -> 3 * s[i - 1] - 3 * s[i - 2] + s[i - 3]
                    else -> 4 * s[i - 1] - 6 * s[i - 2] + 4 * s[i - 3] - s[i - 4]
                }
            }
            type >= 32 -> {
                val order = (type and 31) + 1
                for (i in 0 until order) s[i] = readSigned(b)
                val precision = readBits(4).toInt() + 1
                val shift = readSigned(5).toInt()
                val coefs = LongArray(order) { readSigned(precision) }
                residual(s, n, order)
                for (i in order until n) {
                    var sum = 0L
                    for (j in 0 until order) sum += coefs[j] * s[i - 1 - j]
                    s[i] += if (shift >= 0) sum shr shift else sum shl -shift
                }
            }
            else -> throw IOException("Reserved FLAC subframe type $type")
        }
        if (wasted > 0) for (i in 0 until n) s[i] = s[i] shl wasted
        return s
    }

    /** Rice-coded residual into s[order..n). */
    private fun residual(s: LongArray, n: Int, order: Int) {
        val method = readBits(2).toInt()
        val paramBits = if (method == 0) 4 else 5
        val escape = if (method == 0) 15 else 31
        val partOrder = readBits(4).toInt()
        val parts = 1 shl partOrder
        var i = order
        for (p in 0 until parts) {
            val count = (n shr partOrder) - (if (p == 0) order else 0)
            val k = readBits(paramBits).toInt()
            if (k == escape) {
                val raw = readBits(5).toInt()
                repeat(count) { s[i++] = readSigned(raw) }
            } else {
                repeat(count) {
                    val q = readUnary().toLong()
                    val u = (q shl k) or (if (k > 0) readBits(k) else 0L)
                    s[i++] = (u ushr 1) xor -(u and 1)
                }
            }
        }
    }

    override fun close() = raf.close()
}
