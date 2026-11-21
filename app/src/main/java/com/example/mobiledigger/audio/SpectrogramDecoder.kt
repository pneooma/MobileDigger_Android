package com.example.mobiledigger.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.example.mobiledigger.util.CrashLogger
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder

/**
 * Decoded audio for the spectrogram: MONO 16-bit samples + the source's real format.
 * [bitsPerSample] is null for lossy formats (MP3/AAC/OGG have no bit depth).
 */
class DecodedAudio(
    /** All samples in collect mode; empty when they were streamed to a [MonoSink]. */
    val mono: ShortArray,
    val sampleRate: Int,
    val channels: Int = 1,
    val bitsPerSample: Int? = null,
    val isFloat: Boolean = false,
    val codec: String = "",
    val frames: Long = mono.size.toLong()
)

/** Receives decoded mono samples block by block (streaming mode). */
interface MonoSink {
    /** Called once, before the first block, with the real output sample rate. */
    fun begin(sampleRate: Int) {}
    fun accept(block: ShortArray, count: Int)
}

/** Collects everything into one array (the classic, whole-buffer mode). */
class CollectSink(initialCapacity: Int) : MonoSink {
    private var data = ShortArray(initialCapacity.coerceAtLeast(1024))
    var size = 0
        private set
    override fun accept(block: ShortArray, count: Int) {
        if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
        System.arraycopy(block, 0, data, size, count)
        size += count
    }
    fun toArray(): ShortArray = if (size == data.size) data else data.copyOf(size)
}

/** Buffered writer in front of a [MonoSink], stopping at [capFrames]. */
class MonoOut(var sampleRate: Int, var capFrames: Long, private val sink: MonoSink) {
    private val block = ShortArray(32768)
    private var n = 0
    private var begun = false
    var frames = 0L
        private set
    val isFull: Boolean get() = frames >= capFrames

    /** Adds one sample (clamped to 16 bit). Returns false once the cap is reached. */
    fun put(v: Int): Boolean {
        if (frames >= capFrames) return false
        block[n++] = (if (v > 32767) 32767 else if (v < -32768) -32768 else v).toShort()
        frames++
        if (n == block.size) flush()
        return true
    }

    fun flush() {
        if (n == 0) return
        if (!begun) { sink.begin(sampleRate); begun = true }
        sink.accept(block, n)
        n = 0
    }
}

/**
 * Decodes the first [maxSeconds] of an audio file to mono PCM for spectrogram analysis.
 *
 * - WAV / AIFF / AIFF-C are parsed directly (8/16/24/32-bit int, 32/64-bit float,
 *   big- or little-endian), with real chunk walking — AIFF is not supported by
 *   Android's MediaExtractor on most devices.
 * - Everything else (MP3, FLAC, M4A, OGG, …) and any container the parser rejects
 *   goes through MediaExtractor + MediaCodec.
 *
 * Samples are downmixed to mono while decoding into a preallocated primitive array,
 * so memory stays at sampleRate * maxSeconds * 2 bytes (~16 MB for 3 min @ 44.1 kHz).
 */
class SpectrogramDecoder(
    private val context: Context,
    private val maxSeconds: Int,
    private val piPassword: () -> String? = { null }
) {
    private val tag = "SpectrogramDecoder"

    /**
     * Decodes up to [seconds]. With a [sink] the samples are streamed to it block by block and
     * the returned [DecodedAudio] carries only the format (mono is empty); without one they are
     * collected into [DecodedAudio.mono].
     */
    fun decode(
        uri: Uri, deadlineMs: Long, seconds: Int = maxSeconds, sink: MonoSink? = null,
        onProgress: (Float) -> Unit = {}
    ): DecodedAudio? {
        val t0 = SystemClock.elapsedRealtime()
        // A sink that failed half way can't be replayed, so in streaming mode remember whether
        // the direct parser already pushed data before falling back.
        var pushed = false
        val guarded = sink?.let { s -> object : MonoSink {
            override fun begin(sampleRate: Int) = s.begin(sampleRate)
            override fun accept(block: ShortArray, count: Int) { pushed = true; s.accept(block, count) }
        } }
        val direct = try {
            decodePcmContainer(uri, deadlineMs, seconds, guarded, onProgress)
        } catch (e: Exception) {
            CrashLogger.log(tag, "Direct PCM parse failed, falling back to MediaCodec", e)
            null
        }
        val result = direct ?: if (pushed) null else decodeWithMediaCodec(uri, deadlineMs, seconds, sink, onProgress)
        CrashLogger.log(
            tag,
            "Decoded ${result?.frames ?: 0} mono samples @ ${result?.sampleRate} Hz " +
                "(${if (direct != null) "direct" else "MediaCodec"}) in ${SystemClock.elapsedRealtime() - t0}ms"
        )
        return result
    }

    // ---------------------------------------------------------------- streams

    private fun isHttp(uri: Uri) = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)

    private fun httpHeaders(): Map<String, String> {
        val pw = piPassword()
        return if (pw.isNullOrBlank()) emptyMap() else mapOf("X-Dashboard-Password" to pw)
    }

    private fun openStream(uri: Uri): InputStream? = if (isHttp(uri)) {
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        httpHeaders().forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.inputStream
    } else {
        context.contentResolver.openInputStream(uri)
    }

    // ---------------------------------------------------------------- WAV / AIFF

    private fun decodePcmContainer(uri: Uri, deadlineMs: Long, seconds: Int, sink: MonoSink?, onProgress: (Float) -> Unit): DecodedAudio? {
        val raw = openStream(uri) ?: return null
        val reader = PcmContainerReader(seconds)
        return raw.use { if (sink == null) reader.read(it, deadlineMs, onProgress) else reader.stream(it, deadlineMs, sink, onProgress) }
    }

    // ---------------------------------------------------------------- MediaCodec

    private fun decodeWithMediaCodec(uri: Uri, deadlineMs: Long, seconds: Int, sink: MonoSink?, onProgress: (Float) -> Unit): DecodedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (isHttp(uri)) extractor.setDataSource(uri.toString(), httpHeaders())
            else extractor.setDataSource(context, uri, emptyMap<String, String>())

            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    trackFormat = f
                    break
                }
            }
            if (trackFormat == null) {
                CrashLogger.log(tag, "No audio track found")
                return null
            }
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            CrashLogger.log(tag, "MediaCodec: $mime ${sampleRate}Hz ${channels}ch")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val collect = if (sink == null) CollectSink(sampleRate * minOf(seconds, 200)) else null
            val out = MonoOut(sampleRate, sampleRate.toLong() * seconds, sink ?: collect!!)
            var lastReport = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && !out.isFull) {
                if (SystemClock.elapsedRealtime() > deadlineMs) {
                    CrashLogger.log(tag, "MediaCodec decode hit deadline at ${out.frames} samples")
                    break
                }
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(ib, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        ob.order(ByteOrder.nativeOrder())
                        downmixInto(ob, encoding, channels, out)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (out.frames - lastReport > sampleRate * 5L) {
                        lastReport = out.frames
                        onProgress(out.frames.toFloat() / out.capFrames)
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        val newRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (newRate != sampleRate && out.frames == 0L) {
                            sampleRate = newRate
                            out.sampleRate = newRate
                            out.capFrames = newRate.toLong() * seconds
                        }
                    }
                    CrashLogger.log(tag, "Decoder output format: ${sampleRate}Hz ${channels}ch enc=$encoding")
                }
            }
            out.flush()
            if (out.frames == 0L) return null
            val lossless = mime.contains("flac") || mime.contains("raw") || mime.contains("alac") || mime.contains("wav")
            val bits = if (!lossless) null else when {
                trackFormat.containsKey("bits-per-sample") -> trackFormat.getInteger("bits-per-sample")
                encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                encoding == AudioFormat.ENCODING_PCM_32BIT -> 32
                encoding == AudioFormat.ENCODING_PCM_8BIT -> 8
                else -> 16
            }
            val codec = mime.substringAfter("audio/").uppercase().replace("MPEG", "MP3").replace("MP4A-LATM", "AAC")
            return DecodedAudio(collect?.toArray() ?: ShortArray(0), sampleRate,
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT), bits, false, codec, out.frames)
        } catch (e: Exception) {
            CrashLogger.log(tag, "MediaCodec decode failed", e)
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    /** Downmixes one decoder output buffer into [out]. */
    private fun downmixInto(ob: java.nio.ByteBuffer, encoding: Int, channels: Int, out: MonoOut) {
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = ob.asFloatBuffer()
                val frames = fb.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) sum += fb.get()
                    if (!out.put((sum / channels * 32767f).toInt())) return
                }
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                val ib = ob.asIntBuffer()
                val frames = ib.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0L
                    for (c in 0 until channels) sum += ib.get()
                    if (!out.put(((sum / channels) shr 16).toInt())) return
                }
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val frames = ob.remaining() / (3 * channels)
                for (f in 0 until frames) {
                    var sum = 0
                    for (c in 0 until channels) {
                        val b0 = ob.get().toInt() and 0xFF
                        val b1 = ob.get().toInt() and 0xFF
                        val b2 = ob.get().toInt()
                        sum += (b2 shl 16) or (b1 shl 8) or b0
                    }
                    if (!out.put((sum / channels) shr 8)) return
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val frames = ob.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0
                    for (c in 0 until channels) sum += (ob.get().toInt() and 0xFF) - 128
                    if (!out.put((sum / channels) shl 8)) return
                }
            }
            else -> { // 16-bit
                val sb = ob.asShortBuffer()
                val frames = sb.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0
                    for (c in 0 until channels) sum += sb.get()
                    if (!out.put(sum / channels)) return
                }
            }
        }
    }
}

/**
 * Streaming WAV / AIFF / AIFF-C reader → mono 16-bit. Pure JVM (no Android APIs except
 * logging) so it can be unit-tested. Returns null for anything it doesn't understand,
 * letting the caller fall back to MediaCodec.
 */
class PcmContainerReader(
    private val maxSeconds: Int,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val log: (String) -> Unit = { CrashLogger.log("PcmContainerReader", it) }
) {
    private val tag = "PcmContainerReader"

    /** Where samples go; null = collect into DecodedAudio.mono. */
    private var target: MonoSink? = null

    /** Streams samples to [sink]; the result carries only the format. */
    fun stream(raw: InputStream, deadlineMs: Long, sink: MonoSink, onProgress: (Float) -> Unit = {}): DecodedAudio? {
        target = sink
        try { return read(raw, deadlineMs, onProgress) } finally { target = null }
    }

    fun read(raw: InputStream, deadlineMs: Long, onProgress: (Float) -> Unit = {}): DecodedAudio? {
        DataInputStream(BufferedInputStream(raw, 1 shl 16)).use { din ->
            val head = ByteArray(12)
            try { din.readFully(head) } catch (_: EOFException) { return null }
            val id = String(head, 0, 4, Charsets.US_ASCII)
            val form = String(head, 8, 4, Charsets.US_ASCII)
            return when {
                (id == "RIFF" || id == "RF64") && form == "WAVE" -> parseWav(din, deadlineMs, onProgress)
                id == "FORM" && (form == "AIFF" || form == "AIFC") -> parseAiff(din, form == "AIFC", deadlineMs, onProgress)
                else -> null
            }
        }
    }

    private fun DataInputStream.readLE16(): Int = (read() or (read() shl 8)).also { if (it < 0) throw EOFException() }
    private fun DataInputStream.readLE32(): Long {
        val b = ByteArray(4); readFully(b)
        return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    }
    private fun DataInputStream.readBE32U(): Long = readInt().toLong() and 0xFFFFFFFFL
    private fun DataInputStream.readId(): String { val b = ByteArray(4); readFully(b); return String(b, Charsets.US_ASCII) }
    private fun DataInputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) { if (read() < 0) throw EOFException(); left-- } else left -= s
        }
    }

    private fun parseWav(din: DataInputStream, deadlineMs: Long, onProgress: (Float) -> Unit): DecodedAudio? {
        var channels = 0; var sampleRate = 0; var bits = 0; var formatCode = 0
        while (true) {
            val id = try { din.readId() } catch (_: EOFException) { return null }
            val size = din.readLE32()
            when (id) {
                "fmt " -> {
                    formatCode = din.readLE16()
                    channels = din.readLE16()
                    sampleRate = din.readLE32().toInt()
                    din.readLE32() // byte rate
                    din.readLE16() // block align
                    bits = din.readLE16()
                    var consumed = 16L
                    if (formatCode == 0xFFFE && size >= 40) {
                        din.readLE16() // cbSize
                        din.readLE16() // valid bits
                        din.readLE32() // channel mask
                        formatCode = din.readLE16() // first 2 bytes of SubFormat GUID
                        consumed = 26
                    }
                    din.skipFully(size - consumed + (size and 1))
                }
                "data" -> {
                    if (channels == 0) return null // data before fmt — let MediaCodec try
                    val isFloat = formatCode == 3
                    if (formatCode != 1 && !isFloat) {
                        log("Unsupported WAV format code $formatCode")
                        return null
                    }
                    val dataBytes = if (size == 0L || size == 0xFFFFFFFFL) -1L else size
                    log("WAV: ${sampleRate}Hz ${channels}ch ${bits}bit${if (isFloat) " float" else ""}")
                    return readInterleavedPcm(din, dataBytes, channels, bits, bigEndian = false,
                        isFloat = isFloat, unsigned8 = true, sampleRate = sampleRate,
                        deadlineMs = deadlineMs, onProgress = onProgress)
                }
                else -> din.skipFully(size + (size and 1))
            }
        }
    }

    private fun parseAiff(din: DataInputStream, isAifc: Boolean, deadlineMs: Long, onProgress: (Float) -> Unit): DecodedAudio? {
        var channels = 0; var sampleRate = 0; var bits = 0
        var bigEndian = true; var isFloat = false; var unsigned8 = false
        while (true) {
            val id = try { din.readId() } catch (_: EOFException) { return null }
            val size = din.readBE32U()
            when (id) {
                "COMM" -> {
                    channels = din.readShort().toInt()
                    din.readBE32U() // numSampleFrames
                    bits = din.readShort().toInt()
                    val ext = ByteArray(10); din.readFully(ext)
                    sampleRate = ieee80ToDouble(ext).toInt()
                    var consumed = 18L
                    if (isAifc && size >= 22) {
                        val comp = din.readId()
                        consumed += 4
                        when (comp) {
                            "NONE", "twos" -> {}
                            "sowt" -> bigEndian = false
                            "fl32", "FL32" -> { isFloat = true; bits = 32 }
                            "fl64", "FL64" -> { isFloat = true; bits = 64 }
                            "in24" -> bits = 24
                            "in32" -> bits = 32
                            "raw " -> { unsigned8 = true; bits = 8 }
                            else -> {
                                log("Unsupported AIFF-C compression '$comp'")
                                return null
                            }
                        }
                    }
                    din.skipFully(size - consumed + (size and 1))
                }
                "SSND" -> {
                    if (channels == 0) return null // SSND before COMM — can't stream it
                    val offset = din.readBE32U()
                    din.readBE32U() // block size
                    din.skipFully(offset)
                    val dataBytes = size - 8 - offset
                    log("AIFF: ${sampleRate}Hz ${channels}ch ${bits}bit ${if (bigEndian) "BE" else "LE"}${if (isFloat) " float" else ""}")
                    return readInterleavedPcm(din, if (dataBytes > 0) dataBytes else -1L, channels, bits,
                        bigEndian = bigEndian, isFloat = isFloat, unsigned8 = unsigned8,
                        sampleRate = sampleRate, deadlineMs = deadlineMs, onProgress = onProgress)
                }
                else -> din.skipFully(size + (size and 1))
            }
        }
    }

    /** 80-bit IEEE 754 extended (AIFF sample rate) → Double. */
    private fun ieee80ToDouble(b: ByteArray): Double {
        val exp = ((b[0].toInt() and 0x7F) shl 8) or (b[1].toInt() and 0xFF)
        var mant = 0L
        for (i in 2 until 10) mant = (mant shl 8) or (b[i].toLong() and 0xFF)
        if (exp == 0 && mant == 0L) return 0.0
        val m = (mant ushr 1).toDouble() * 2.0 + (mant and 1L).toDouble()
        val v = m * Math.pow(2.0, (exp - 16383 - 63).toDouble())
        return if (b[0].toInt() and 0x80 != 0) -v else v
    }

    private fun readInterleavedPcm(
        src: InputStream, dataBytes: Long, channels: Int, bits: Int,
        bigEndian: Boolean, isFloat: Boolean, unsigned8: Boolean, sampleRate: Int,
        deadlineMs: Long, onProgress: (Float) -> Unit
    ): DecodedAudio? {
        if (channels !in 1..32 || sampleRate !in 1000..768_000 || bits !in 1..64) return null
        val bps = (bits + 7) / 8
        if (isFloat && bps != 4 && bps != 8) return null
        if (!isFloat && bps > 4) return null
        val frameBytes = bps * channels
        val maxFrames = sampleRate.toLong() * maxSeconds
        val availFrames = if (dataBytes > 0) dataBytes / frameBytes else Long.MAX_VALUE
        val total = minOf(maxFrames, availFrames)
        if (total <= 0) return null

        val collect = if (target == null) CollectSink(minOf(total, sampleRate.toLong() * 200).toInt()) else null
        val out = MonoOut(sampleRate, total, target ?: collect!!)
        val buf = ByteArray(frameBytes * 4096)
        val intShift = 32 - 8 * bps
        val intScale = 1.0f / (1L shl (8 * bps - 1)).toFloat()
        var written = 0L
        var carry = 0
        var lastReport = 0L
        while (written < total) {
            if (now() > deadlineMs) {
                log("PCM read hit deadline at $written/$total frames")
                break
            }
            val n = src.read(buf, carry, buf.size - carry)
            if (n <= 0) break
            val avail = carry + n
            val frames = minOf((avail / frameBytes).toLong(), total - written).toInt()
            var p = 0
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) {
                    val s: Float = if (isFloat) {
                        if (bps == 4) {
                            var v = 0
                            for (k in 0 until 4) {
                                val byte = buf[p + if (bigEndian) k else 3 - k].toInt() and 0xFF
                                v = (v shl 8) or byte
                            }
                            java.lang.Float.intBitsToFloat(v)
                        } else {
                            var v = 0L
                            for (k in 0 until 8) {
                                val byte = buf[p + if (bigEndian) k else 7 - k].toLong() and 0xFF
                                v = (v shl 8) or byte
                            }
                            java.lang.Double.longBitsToDouble(v).toFloat()
                        }
                    } else if (bps == 1) {
                        val b = buf[p].toInt()
                        (if (unsigned8) (b and 0xFF) - 128 else b) / 128f
                    } else {
                        var v = 0
                        for (k in 0 until bps) {
                            val byte = buf[p + if (bigEndian) k else bps - 1 - k].toInt() and 0xFF
                            v = (v shl 8) or byte
                        }
                        // Sign-extend from bps bytes
                        ((v shl intShift) shr intShift) * intScale
                    }
                    sum += s
                    p += bps
                }
                val mono = (sum / channels * 32767f).toInt().coerceIn(-32768, 32767)
                out.put(mono); written++
            }
            val used = frames * frameBytes
            carry = avail - used
            if (carry > 0) System.arraycopy(buf, used, buf, 0, carry)
            if (written - lastReport > sampleRate * 5L) {
                lastReport = written
                onProgress(written.toFloat() / total)
            }
        }
        out.flush()
        if (written == 0L) return null
        return DecodedAudio(collect?.toArray() ?: ShortArray(0), sampleRate, channels, bits, isFloat, "PCM", written)
    }
}
