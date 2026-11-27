package com.example.mobiledigger.ipod

import java.io.File
import java.io.RandomAccessFile

/**
 * Apple Lossless (ALAC) encoder, written from the ALAC bitstream as Apple's open-source codec and
 * ffmpeg's decoder define it: per channel an adaptive LPC predictor (coefficients adapt per sample,
 * exactly as the decoder does) and adaptive Golomb-Rice coding of the residuals, mid/side
 * decorrelation for stereo, low bytes sent raw for 24-bit audio, and an uncompressed "escape"
 * frame whenever that's smaller. Frames of 4096 samples. Verified bit-exact by decoding with
 * Apple's own decoder (AudioToolbox) and ffmpeg.
 */
class AlacEncoder(val sampleRate: Int, val channels: Int, val bitDepth: Int) {
    companion object {
        const val FRAME = 4096
        private const val PB = 40          // rice history multiplier
        private const val MB = 10          // initial history
        private const val KB = 14          // rice limit
        private const val DENSHIFT = 9
        private const val ORDER = 8
        private const val MIX_BITS = 2
        private const val MIX_RES = 2
        private const val PB_FACTOR = 4
    }

    init {
        require(channels in 1..2) { "ALAC: 1 or 2 channels" }
        require(bitDepth == 16 || bitDepth == 24) { "ALAC: 16 or 24 bits" }
    }

    /** ALACSpecificConfig ("magic cookie"), 24 bytes. */
    fun cookie(maxFrameBytes: Int, avgBitRate: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(24)
        b.putInt(FRAME); b.put(0); b.put(bitDepth.toByte()); b.put(PB.toByte()); b.put(MB.toByte()); b.put(KB.toByte())
        b.put(channels.toByte()); b.putShort(255); b.putInt(maxFrameBytes); b.putInt(avgBitRate); b.putInt(sampleRate)
        return b.array()
    }

    // ------------------------------------------------------------------ bit writer

    private class Bits(cap: Int) {
        var buf = ByteArray(cap)
        var bitPos = 0L
        fun put(value: Long, n: Int) {
            for (i in n - 1 downTo 0) {
                val bit = ((value ushr i) and 1L).toInt()
                val byteIdx = (bitPos ushr 3).toInt()
                if (byteIdx >= buf.size) buf = buf.copyOf(buf.size * 2)
                if (bit != 0) buf[byteIdx] = (buf[byteIdx].toInt() or (0x80 ushr (bitPos and 7).toInt())).toByte()
                bitPos++
            }
        }
        fun put(value: Int, n: Int) = put(value.toLong(), n)
        fun ones(n: Int) { repeat(n) { put(1, 1) } }
        fun align() { while (bitPos and 7L != 0L) put(0, 1) }
        fun bytes(): ByteArray = buf.copyOf(((bitPos + 7) ushr 3).toInt())
        fun reset() { java.util.Arrays.fill(buf, 0, minOf(buf.size, ((bitPos + 7) ushr 3).toInt() + 1), 0); bitPos = 0 }
    }

    private val bits = Bits(FRAME * channels * 4 + 64)
    private val u = IntArray(FRAME); private val v = IntArray(FRAME)
    private val resU = IntArray(FRAME); private val resV = IntArray(FRAME)
    private val lowU = IntArray(FRAME); private val lowV = IntArray(FRAME)

    private fun signExt(x: Int, b: Int): Int = (x shl (32 - b)) shr (32 - b)
    private fun sign(x: Int) = if (x > 0) 1 else if (x < 0) -1 else 0

    /**
     * Adaptive LPC (pc_block): residuals of [input] into [out], adapting [coefs] (newest-first order)
     * exactly as the decoder will. [chanBits] = bits of the channel signal.
     */
    private fun predict(input: IntArray, out: IntArray, n: Int, coefs: IntArray, order: Int, chanBits: Int) {
        if (n == 0) return
        val shift = 32 - chanBits
        out[0] = input[0]
        val warm = minOf(order, n - 1)
        for (j in 1..warm) out[j] = ((input[j] - input[j - 1]) shl shift) shr shift
        val lim = order + 1
        val denHalf = 1 shl (DENSHIFT - 1)
        for (j in lim until n) {
            val top = input[j - lim]
            var sum = 0
            for (k in 0 until order) sum += coefs[k] * (input[j - 1 - k] - top)
            var del = input[j] - top - ((sum + denHalf) shr DENSHIFT)
            del = (del shl shift) shr shift
            out[j] = del
            var del0 = del
            val sg = sign(del)
            if (sg > 0) {
                var k = order - 1
                while (k >= 0) {
                    val dd = top - input[j - 1 - k]
                    val sgn = sign(dd)
                    coefs[k] -= sgn
                    del0 -= (order - k) * ((sgn * dd) shr DENSHIFT)
                    if (del0 <= 0) break
                    k--
                }
            } else if (sg < 0) {
                var k = order - 1
                while (k >= 0) {
                    val dd = top - input[j - 1 - k]
                    val sgn = sign(dd)
                    coefs[k] += sgn
                    del0 -= (order - k) * ((-sgn * dd) shr DENSHIFT)
                    if (del0 >= 0) break
                    k--
                }
            }
        }
    }

    private fun log2(x: Long): Int = if (x <= 0) 0 else 63 - java.lang.Long.numberOfLeadingZeros(x)

    /** Golomb-Rice code of [n] with parameter k (ALAC's "2^k-1" variant), escaping to [escBits] raw bits. */
    private fun putScalar(n: Long, k: Int, escBits: Int) {
        if (k == 1) {
            if (n < 9) { bits.ones(n.toInt()); bits.put(0, 1) } else { bits.ones(9); bits.put(n, escBits) }
            return
        }
        val m = (1L shl k) - 1
        val q = n / m
        val r = n % m
        if (q > 8) { bits.ones(9); bits.put(n, escBits); return }
        bits.ones(q.toInt()); bits.put(0, 1)
        if (r == 0L) bits.put(0, k - 1) else bits.put(r + 1, k)
    }

    /** Adaptive Golomb coding of residuals (ag_enc / ffmpeg rice_decompress inverse). */
    private fun riceEncode(res: IntArray, n: Int, bps: Int) {
        val mult = (PB_FACTOR * PB / 4).toLong()
        var history = MB.toLong()
        var signMod = 0L
        var i = 0
        while (i < n) {
            val k = minOf(log2((history shr 9) + 3), KB)
            val z = ((res[i].toLong() shl 1) xor (res[i].toLong() shr 63)) and 0xFFFFFFFFL
            putScalar(z - signMod, k, bps)
            signMod = 0
            history = if (z > 0xFFFF) 0xFFFF else (history + z * mult - ((history * mult) shr 9)) and 0xFFFFFFFFL
            if (history < 128 && i + 1 < n) {
                var run = 0
                while (i + 1 + run < n && res[i + 1 + run] == 0 && run < 0xFFFF) run++
                val kb = minOf(7 - log2(history) + ((history + 16) shr 6).toInt(), KB)
                putScalar(run.toLong(), kb, 16)
                i += run
                signMod = 1 // a zero-block length always fits in 16 bits
                history = 0
            }
            i++
        }
    }

    /**
     * Encodes one frame of interleaved samples ([frames] per channel, values in the source bit depth).
     * Returns the frame bytes.
     */
    fun encodeFrame(pcm: IntArray, frames: Int): ByteArray {
        val compressed = encodeCompressed(pcm, frames)
        val escaped = encodeEscape(pcm, frames)
        return if (compressed.size < escaped.size) compressed else escaped
    }

    private fun header(frames: Int, shiftBytes: Int, escape: Boolean) {
        bits.put(if (channels == 2) 1 else 0, 3)  // element: CPE (stereo) / SCE (mono)
        bits.put(0, 4)                            // instance tag
        bits.put(0, 12)                           // unused
        val partial = frames != FRAME
        bits.put(if (partial) 1 else 0, 1)
        bits.put(shiftBytes, 2)
        bits.put(if (escape) 1 else 0, 1)
        if (partial) bits.put(frames.toLong(), 32)
    }

    private fun encodeEscape(pcm: IntArray, frames: Int): ByteArray {
        bits.reset()
        header(frames, 0, true)
        for (i in 0 until frames * channels) bits.put(pcm[i].toLong() and ((1L shl bitDepth) - 1), bitDepth)
        bits.put(7, 3); bits.align()
        return bits.bytes()
    }

    private fun encodeCompressed(pcm: IntArray, frames: Int): ByteArray {
        val shiftBytes = if (bitDepth == 24) 1 else 0
        val shift = shiftBytes * 8
        val chanBits = bitDepth - shift + channels - 1
        // Split low bytes (sent raw) from the part that's predicted; mix stereo to mid/side-ish.
        for (i in 0 until frames) {
            val l = pcm[i * channels]
            val r = if (channels == 2) pcm[i * channels + 1] else 0
            val lh = l shr shift; val rh = r shr shift
            if (shift > 0) { lowU[i] = l and 0xFF; lowV[i] = r and 0xFF }
            if (channels == 2) {
                u[i] = (MIX_RES * lh + ((1 shl MIX_BITS) - MIX_RES) * rh) shr MIX_BITS
                v[i] = lh - rh
            } else u[i] = lh
        }
        // Converge the initial coefficients on this frame, then encode from those (the decoder starts there).
        val coefU = initCoefs(); val coefV = initCoefs()
        repeat(2) {
            predict(u, resU, frames, coefU, ORDER, chanBits)
            if (channels == 2) predict(v, resV, frames, coefV, ORDER, chanBits)
        }
        val startU = coefU.copyOf(); val startV = coefV.copyOf()
        predict(u, resU, frames, coefU, ORDER, chanBits)
        if (channels == 2) predict(v, resV, frames, coefV, ORDER, chanBits)

        bits.reset()
        header(frames, shiftBytes, false)
        bits.put(if (channels == 2) MIX_BITS else 0, 8)
        bits.put(if (channels == 2) MIX_RES else 0, 8)
        for (c in 0 until channels) {
            val start = if (c == 0) startU else startV
            bits.put(0, 4)            // prediction type 0 (adaptive FIR)
            bits.put(DENSHIFT, 4)
            bits.put(PB_FACTOR, 3)
            bits.put(ORDER, 5)
            for (k in 0 until ORDER) bits.put(start[k].toLong() and 0xFFFF, 16)
        }
        if (shift > 0) for (i in 0 until frames) {
            bits.put(lowU[i], shift)
            if (channels == 2) bits.put(lowV[i], shift)
        }
        riceEncode(resU, frames, chanBits)
        if (channels == 2) riceEncode(resV, frames, chanBits)
        bits.put(7, 3); bits.align()
        return bits.bytes()
    }

    /** Apple's starting coefficients (AINIT/BINIT/CINIT scaled by the denominator). */
    private fun initCoefs(): IntArray {
        val den = 1 shl DENSHIFT
        return IntArray(ORDER).also { it[0] = (38 * den) shr 4; it[1] = (-29 * den) shr 4; it[2] = (-2 * den) shr 4 }
    }
}

/** Writes ALAC frames into an .m4a (mdat first, moov at the end). */
class AlacM4aWriter(private val file: File, private val enc: AlacEncoder) {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0) }
    private val sizes = ArrayList<Int>()
    private var lastFrames = AlacEncoder.FRAME
    private var totalFrames = 0L
    private val mdatStart: Long

    init {
        raf.write(box("ftyp", "M4A ".toByteArray() + int(0) + "M4A mp42isom".toByteArray()))
        mdatStart = raf.filePointer
        raf.writeInt(0); raf.write("mdat".toByteArray())
    }

    fun write(frameBytes: ByteArray, frames: Int) {
        raf.write(frameBytes); sizes += frameBytes.size; lastFrames = frames; totalFrames += frames
    }

    fun finish() {
        val mdatEnd = raf.filePointer
        raf.seek(mdatStart); raf.writeInt((mdatEnd - mdatStart).toInt()); raf.seek(mdatEnd)
        val rate = enc.sampleRate
        val dur = totalFrames
        val maxSize = sizes.maxOrNull() ?: 0
        val seconds = dur.toDouble() / rate
        val avg = if (seconds > 0) ((mdatEnd - mdatStart) * 8 / seconds).toInt() else 0
        val cookie = enc.cookie(maxSize, avg)
        val alacBox = box("alac", int(0) + cookie)
        val sampleEntry = box("alac", ByteArray(6) + short(1) + ByteArray(8) + short(enc.channels) + short(enc.bitDepth) +
            short(0) + short(0) + int(if (rate < 65536) rate shl 16 else 0) + alacBox)
        val stsd = fullBox("stsd", int(1) + sampleEntry)
        val stts = if (sizes.size <= 1) fullBox("stts", int(1) + int(sizes.size) + int(lastFrames))
            else if (lastFrames == AlacEncoder.FRAME) fullBox("stts", int(1) + int(sizes.size) + int(AlacEncoder.FRAME))
            else fullBox("stts", int(2) + int(sizes.size - 1) + int(AlacEncoder.FRAME) + int(1) + int(lastFrames))
        val stsc = fullBox("stsc", int(1) + int(1) + int(sizes.size) + int(1))
        val stsz = fullBox("stsz", int(0) + int(sizes.size) + sizes.fold(ByteArray(0)) { a, s -> a + int(s) })
        val stco = fullBox("stco", int(1) + int((mdatStart + 8).toInt()))
        val stbl = box("stbl", stsd + stts + stsc + stsz + stco)
        val dinf = box("dinf", fullBox("dref", int(1) + fullBox("url ", ByteArray(0), flags = 1)))
        val minf = box("minf", fullBox("smhd", ByteArray(4)) + dinf + stbl)
        val hdlr = fullBox("hdlr", int(0) + "soun".toByteArray() + ByteArray(12) + "SoundHandler".toByteArray() + ByteArray(1))
        val mdhd = fullBox("mdhd", int(0) + int(0) + int(rate) + int(dur.toInt()) + short(0x55C4) + short(0))
        val mdia = box("mdia", mdhd + hdlr + minf)
        val matrix = int(0x10000) + int(0) + int(0) + int(0) + int(0x10000) + int(0) + int(0) + int(0) + int(0x40000000)
        val movieDur = (dur * 1000 / rate).toInt()
        val tkhd = fullBox("tkhd", int(0) + int(0) + int(1) + int(0) + int(movieDur) + ByteArray(8) + short(0) + short(0) +
            short(0x0100) + short(0) + matrix + int(0) + int(0), flags = 7)
        val trak = box("trak", tkhd + mdia)
        val mvhd = fullBox("mvhd", int(0) + int(0) + int(1000) + int(movieDur) + int(0x10000) + short(0x0100) + ByteArray(10) +
            matrix + ByteArray(24) + int(2))
        raf.write(box("moov", mvhd + trak))
        raf.close()
    }

    private fun int(v: Int) = java.nio.ByteBuffer.allocate(4).putInt(v).array()
    private fun short(v: Int) = java.nio.ByteBuffer.allocate(2).putShort(v.toShort()).array()
    private fun box(type: String, body: ByteArray) = int(8 + body.size) + type.toByteArray(Charsets.US_ASCII) + body
    private fun fullBox(type: String, body: ByteArray, flags: Int = 0) = box(type, int(flags) + body)
}
