package com.example.mobiledigger.ipod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import com.example.mobiledigger.util.CrashLogger
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What to send: AAC at a bit rate, or Apple Lossless; [keepCompatible] sends MP3/AAC/ALAC files untouched. */
data class SendQuality(val alac: Boolean = false, val aacKbps: Int = 256, val keepCompatible: Boolean = true) {
    val label get() = if (alac) "Apple Lossless" else "AAC $aacKbps kbps"
    fun save(ctx: Context) = ctx.getSharedPreferences("ipod", Context.MODE_PRIVATE).edit()
        .putBoolean("alac", alac).putInt("aac", aacKbps).putBoolean("keep", keepCompatible).apply()
    companion object {
        fun load(ctx: Context) = ctx.getSharedPreferences("ipod", Context.MODE_PRIVATE).let {
            SendQuality(it.getBoolean("alac", false), it.getInt("aac", 256), it.getBoolean("keep", true))
        }
    }
}

/** A file ready for the iPod: AAC/ALAC .m4a or .mp3, plus what the sync operation needs to say about it. */
data class PreparedAudio(
    val file: File,
    val ext: String,             // "m4a" or "mp3"
    val codec: String,           // "aac", "alac" or "mp3"
    val tags: Map<String, String>,
    val sampleRate: Double,
    val samples: Long,           // valid samples per channel
    val delay: Long,
    val drain: Long,
    val bitRateKbps: Int,
    val channels: Int,
    val cover: File?,            // JPEG
    val title: String,
    val converted: Boolean
)

/** Debug timing log for the iPod feature (temporary, while it's being finished). */
object IpodDebug {
    const val ENABLED = true
    fun log(msg: String) { if (ENABLED) CrashLogger.log("iPodDebug", msg) }
}

object TouchMedia {
    private const val TAG = "TouchMedia"
    private val LOSSLESS = setOf("wav", "flac", "aif", "aiff")

    /**
     * Copies [uri] into [work], converting when [q] asks for it or the iPod can't play the format,
     * and reads what the sync needs. [progress] reports 0..1 of the conversion.
     */
    fun prepare(ctx: Context, uri: Uri, displayName: String, work: File, index: Int, q: SendQuality = SendQuality(),
                progress: (Float) -> Unit = {}): PreparedAudio {
        work.mkdirs()
        val t0 = SystemClock.elapsedRealtime()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val src = File(work, "src-$index.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { i -> src.outputStream().use { i.copyTo(it, 512 * 1024) } }
            ?: throw IOException("Can't read $displayName")
        val tCopy = SystemClock.elapsedRealtime()
        val srcKb = src.length() / 1024
        val tags = readTags(src, displayName)
        val cover = prepareCover(src, File(work, "cover-$index.jpg"))
        val mime = trackMime(src)
        val title = tags["title"] ?: displayName.substringBeforeLast('.')
        val isMp3 = ext == "mp3" || mime == "audio/mpeg"
        val isAacM4a = (ext == "m4a" || ext == "mp4") && mime == "audio/mp4a-latm"
        val isAlacM4a = (ext == "m4a" || ext == "mp4") && mime == "audio/alac"
        val lossless = ext in LOSSLESS || isAlacM4a
        val result = when {
            isMp3 && q.keepCompatible -> {
                val out = File(work, "track-$index.mp3"); src.renameTo(out)
                val f = audioFormat(out)
                val sr = f?.optInt(MediaFormat.KEY_SAMPLE_RATE)?.toDouble()?.takeIf { it > 0 } ?: 44100.0
                val durUs = f?.optLong(MediaFormat.KEY_DURATION) ?: 0L
                val br = (tags["bitrate"]?.toIntOrNull() ?: 0) / 1000
                PreparedAudio(out, "mp3", "mp3", tags, sr, (durUs / 1e6 * sr).toLong(), 0, 0,
                    if (br > 0) br else 320, f?.optInt(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: 2, cover, title, false)
            }
            (isAacM4a || isAlacM4a) && q.keepCompatible -> {
                val out = File(work, "track-$index.m4a"); src.renameTo(out)
                m4a(out, if (isAlacM4a) "alac" else "aac", tags, cover, title, false)
            }
            q.alac && lossless -> {
                val out = File(work, "track-$index.m4a")
                encodeAlac(src, ext, out, progress)
                src.delete()
                m4a(out, "alac", tags, cover, title, true)
            }
            else -> {
                val out = File(work, "track-$index.m4a")
                // Lossy sources when Apple Lossless is chosen: AAC at the top rate (lossless would only grow the file).
                transcodeToAac(src, ext, out, if (q.alac) 320 else q.aacKbps, progress)
                src.delete()
                m4a(out, "aac", tags, cover, title, true)
            }
        }
        val t1 = SystemClock.elapsedRealtime()
        val audioSec = result.samples / result.sampleRate
        IpodDebug.log("prepare “$displayName”: copy ${tCopy - t0} ms, ${if (result.converted) "convert→${result.codec}" else "as is"} " +
            "${t1 - tCopy} ms for %.1f s audio (%.1fx realtime), %d KB → %d KB".format(audioSec,
                if (t1 > tCopy) audioSec * 1000 / (t1 - tCopy) else 0.0, srcKb, result.file.length() / 1024))
        return result
    }

    private fun MediaFormat.optInt(k: String) = if (containsKey(k)) getInteger(k) else 0
    private fun MediaFormat.optLong(k: String) = if (containsKey(k)) getLong(k) else 0L

    private fun m4a(f: File, codec: String, tags: Map<String, String>, cover: File?, title: String, converted: Boolean): PreparedAudio {
        val fmt = audioFormat(f)
        val rate = fmt?.optInt(MediaFormat.KEY_SAMPLE_RATE)?.toDouble()?.takeIf { it > 0 } ?: 44100.0
        val durUs = fmt?.optLong(MediaFormat.KEY_DURATION) ?: 0L
        val t = Mp4Timing.read(f, rate, durUs / 1e6)
        val alac = codec == "alac"
        return PreparedAudio(f, "m4a", codec, tags, t.sampleRate, t.samples,
            if (alac) 0 else t.delay, if (alac) 0 else t.drain,
            if (t.bitRateKbps > 0) t.bitRateKbps else (tags["bitrate"]?.toIntOrNull() ?: 0) / 1000,
            fmt?.optInt(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: 2, cover, title, converted)
    }

    private fun audioFormat(f: File): MediaFormat? = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(f.path)
        try {
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally { ex.release() }
    }.getOrNull()

    private fun trackMime(f: File) = audioFormat(f)?.getString(MediaFormat.KEY_MIME)

    fun readTags(f: File, displayName: String): Map<String, String> {
        val m = HashMap<String, String>()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            fun put(k: String, key: Int) { r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { m[k] = it } }
            put("title", MediaMetadataRetriever.METADATA_KEY_TITLE)
            put("artist", MediaMetadataRetriever.METADATA_KEY_ARTIST)
            put("album_artist", MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            put("album", MediaMetadataRetriever.METADATA_KEY_ALBUM)
            put("genre", MediaMetadataRetriever.METADATA_KEY_GENRE)
            put("composer", MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            put("track", MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            put("disc", MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            put("year", MediaMetadataRetriever.METADATA_KEY_YEAR)
            put("compilation", MediaMetadataRetriever.METADATA_KEY_COMPILATION)
            put("bitrate", MediaMetadataRetriever.METADATA_KEY_BITRATE)
        } catch (e: Exception) {
            CrashLogger.log(TAG, "No tags for $displayName: ${e.message}")
        } finally { runCatching { r.release() } }
        // "Artist - Title.ext" file names when the file has no tags
        if (m["title"] == null) {
            // "07. Artist_-_Title_(Original_Mix)" → artist "Artist", title "Title (Original Mix)"
            var base = displayName.substringBeforeLast('.')
            if (!base.contains(' ') && base.contains('_')) base = base.replace('_', ' ')
            base = base.replace(Regex("^\\s*\\d{1,3}\\s*[.)\\-]\\s*"), "").replace(Regex("\\s{2,}"), " ").trim()
            val parts = base.split(" - ", limit = 2)
            if (parts.size == 2 && m["artist"] == null) { m["artist"] = parts[0].trim(); m["title"] = parts[1].trim() }
            else m["title"] = base
        }
        return m
    }

    /** Embedded cover as a JPEG of at most 1000 px (kept as is if already a JPEG ≤ 1400 px). */
    private fun prepareCover(src: File, out: File): File? {
        val r = MediaMetadataRetriever()
        val bytes = try { r.setDataSource(src.path); r.embeddedPicture } catch (_: Exception) { null } finally { runCatching { r.release() } }
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            if (isJpeg && maxOf(opts.outWidth, opts.outHeight) in 1..1400) { out.writeBytes(bytes); return out }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scale = minOf(1f, 1000f / maxOf(bmp.width, bmp.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out
        }.getOrNull()
    }

    // ------------------------------------------------------------------ PCM sources

    /** Interleaved PCM, at most 2 channels, values at [bits] (16 or 24). */
    interface Pcm {
        val rate: Int
        val channels: Int
        val bits: Int
        fun read(): IntArray?
        fun fraction(): Float
        fun close()
    }

    private fun open(src: File, ext: String, wantFullDepth: Boolean): Pcm = when {
        ext == "flac" -> runCatching<Pcm> { FlacPcm(src) }.getOrElse {
            IpodDebug.log("own FLAC decoder refused ${src.name}: ${it.message}; using Android's"); DecodedPcm(src, false)
        }
        ext == "wav" -> runCatching { WavPcm(src) }.getOrElse { DecodedPcm(src, false) }
        isAiff(src) -> AiffPcm(src)
        // Android's decoders only in 16-bit: asked for float output its FLAC decoder handed back
        // another layout (half the length, noise), so full-depth formats use the readers above.
        else -> DecodedPcm(src, false)
    }

    /** FLAC through our own decoder: exact at any bit depth (24-bit stays 24-bit). */
    private class FlacPcm(f: File) : Pcm {
        private val d = FlacDecoder(f)
        override val rate get() = d.sampleRate
        override val channels get() = minOf(d.channels, 2)
        override val bits get() = if (d.bitsPerSample > 16) 24 else 16
        init { if (d.sampleRate <= 0 || d.channels <= 0) throw IOException("Bad FLAC header") }
        override fun fraction() = d.fraction
        override fun read(): IntArray? {
            val blk = d.nextBlock() ?: return null
            val src = d.bitsPerSample
            val shift = bits - src // 8 → 16, 12 → 16, 20 → 24 (left); 32 → 24 (right)
            if (shift != 0) for (i in blk.indices) blk[i] = if (shift > 0) blk[i] shl shift else blk[i] shr -shift
            return downmix(blk, d.channels, bits)
        }
        override fun close() = d.close()
    }

    private fun isAiff(f: File): Boolean = runCatching {
        RandomAccessFile(f, "r").use { r -> val b = ByteArray(12); r.readFully(b); String(b, 0, 4) == "FORM" && String(b, 8, 4).let { it == "AIFF" || it == "AIFC" } }
    }.getOrDefault(false)

    /** Mixes [n]-channel frames down to stereo (front L/R plus centre at -3 dB). */
    private fun downmix(v: IntArray, inCh: Int, bits: Int): IntArray {
        if (inCh <= 2) return v
        val frames = v.size / inCh
        val max = (1 shl (bits - 1)) - 1
        return IntArray(frames * 2) { i ->
            val f = i / 2; val c = i % 2
            (v[f * inCh + c] + v[f * inCh + 2] * 7 / 10).coerceIn(-max - 1, max)
        }
    }

    /** Converts a little/big-endian PCM chunk to ints at 16 or 24 bits. */
    private fun decodeInts(b: ByteArray, n: Int, srcBits: Int, float: Boolean, little: Boolean, outBits: Int): IntArray {
        val bps = srcBits / 8
        val count = n / bps
        val out = IntArray(count)
        val bb = ByteBuffer.wrap(b, 0, n).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val outMax = (1 shl (outBits - 1)) - 1
        for (i in 0 until count) {
            val o = i * bps
            out[i] = when {
                float && bps == 4 -> (bb.getFloat(o).coerceIn(-1f, 1f) * outMax).toInt()
                float && bps == 8 -> (bb.getDouble(o).coerceIn(-1.0, 1.0) * outMax).toInt()
                bps == 1 -> ((b[o].toInt() and 0xFF) - 128) shl (outBits - 8)
                bps == 2 -> bb.getShort(o).toInt() shl (outBits - 16)
                bps == 3 -> {
                    val v = if (little) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16)
                            else (b[o + 2].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o].toInt() shl 16)
                    if (outBits >= 24) v shl (outBits - 24) else v shr (24 - outBits)
                }
                else -> bb.getInt(o) shr (32 - outBits)
            }
        }
        return out
    }

    /** WAV read directly (keeps 24-bit audio intact; Android's decoder would round it to 16). */
    private class WavPcm(f: File) : Pcm {
        private val raf = RandomAccessFile(f, "r")
        override var rate = 44100; private set
        private var srcCh = 2
        override val channels get() = minOf(srcCh, 2)
        private var srcBits = 16
        private var float = false
        override val bits get() = if (srcBits > 16) 24 else 16
        private var start = 0L; private var end = 0L

        init {
            val h = ByteArray(12); raf.readFully(h)
            if (String(h, 0, 4) != "RIFF" || String(h, 8, 4) != "WAVE") throw IOException("not a WAV")
            val le = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(le)
                val id = String(le, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(le, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val pos = raf.filePointer
                if (id == "fmt ") {
                    val fb = ByteArray(minOf(size, 40).toInt()); raf.readFully(fb)
                    val bb = ByteBuffer.wrap(fb).order(ByteOrder.LITTLE_ENDIAN)
                    var fmt = bb.getShort(0).toInt() and 0xFFFF
                    srcCh = bb.getShort(2).toInt(); rate = bb.getInt(4); srcBits = bb.getShort(14).toInt()
                    if (fmt == 0xFFFE && fb.size >= 26) fmt = bb.getShort(24).toInt() and 0xFFFF
                    float = fmt == 3
                    if (fmt != 1 && fmt != 3) throw IOException("compressed WAV")
                } else if (id == "data") {
                    start = pos; end = minOf(pos + size, raf.length()); break
                }
                raf.seek(pos + size + (size and 1))
            }
            if (end <= start) throw IOException("no WAV data")
            raf.seek(start)
        }

        override fun fraction() = ((raf.filePointer - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        override fun read(): IntArray? {
            val frameBytes = srcBits / 8 * srcCh
            val left = end - raf.filePointer
            if (left < frameBytes) return null
            val n = (minOf(left, 256L * 1024) / frameBytes * frameBytes).toInt()
            val b = ByteArray(n); raf.readFully(b)
            return downmix(decodeInts(b, n, srcBits, float, true, bits), srcCh, bits)
        }
        override fun close() = raf.close()
    }

    /** AIFF read directly (Android can't decode it). */
    private class AiffPcm(f: File) : Pcm {
        private val raf = RandomAccessFile(f, "r")
        override var rate = 44100; private set
        private var srcCh = 2
        override val channels get() = minOf(srcCh, 2)
        private var srcBits = 16
        override val bits get() = if (srcBits > 16) 24 else 16
        private var start = 0L; private var end = 0L
        private var little = false
        private var float = false

        init {
            raf.seek(12)
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4).also { raf.readFully(it) }.let { String(it, Charsets.US_ASCII) }
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val pos = raf.filePointer
                when (id) {
                    "COMM" -> {
                        srcCh = raf.readShort().toInt(); raf.readInt(); srcBits = raf.readShort().toInt()
                        val ext = ByteArray(10).also { raf.readFully(it) }
                        rate = ieee80(ext).toInt()
                        if (size >= 22) {
                            val ct = String(ByteArray(4).also { raf.readFully(it) }, Charsets.US_ASCII)
                            little = ct == "sowt"; float = ct == "fl32" || ct == "FL32" || ct == "fl64"
                        }
                    }
                    "SSND" -> { val offset = raf.readInt(); raf.readInt(); start = pos + 8 + offset; end = minOf(pos + size, raf.length()) }
                }
                raf.seek(pos + size + (size and 1))
            }
            if (end <= start) throw IOException("No audio data in this AIFF")
            raf.seek(start)
        }

        private fun ieee80(b: ByteArray): Double {
            val exp = ((b[0].toInt() and 0x7F) shl 8) or (b[1].toInt() and 0xFF)
            var mant = 0L
            for (i in 2 until 10) mant = (mant shl 8) or (b[i].toLong() and 0xFF)
            return mant.toULong().toDouble() * Math.pow(2.0, (exp - 16383 - 63).toDouble())
        }

        override fun fraction() = ((raf.filePointer - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        override fun read(): IntArray? {
            val frameBytes = srcBits / 8 * srcCh
            val left = end - raf.filePointer
            if (left < frameBytes) return null
            val n = (minOf(left, 256L * 1024) / frameBytes * frameBytes).toInt()
            val b = ByteArray(n); raf.readFully(b)
            return downmix(decodeInts(b, n, srcBits, float, little, bits), srcCh, bits)
        }
        override fun close() = raf.close()
    }

    /**
     * Anything Android can decode (FLAC, OGG, Opus, MP3, AAC…). [fullDepth] asks for float output so
     * 24-bit FLAC stays 24-bit. The loop never sleeps on an idle codec: it feeds every free input
     * buffer, then takes whatever output is ready (the old 10 ms waits made conversion crawl).
     */
    private class DecodedPcm(src: File, fullDepth: Boolean) : Pcm {
        private val ex = MediaExtractor().apply { setDataSource(src.path) }
        private val dec: MediaCodec
        override var rate = 44100; private set
        private var srcCh = 2
        override val channels get() = minOf(srcCh, 2)
        override val bits: Int
        private var float = false
        private val durationUs: Long
        private var lastPts = 0L
        private var inputDone = false
        private var done = false
        private val info = MediaCodec.BufferInfo()

        init {
            val idx = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("No audio in this file")
            ex.selectTrack(idx)
            val f = ex.getTrackFormat(idx)
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            durationUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            val mime = f.getString(MediaFormat.KEY_MIME)!!
            val highRes = fullDepth && (mime == MediaFormat.MIMETYPE_AUDIO_FLAC || mime == "audio/alac" || mime == MediaFormat.MIMETYPE_AUDIO_RAW)
            bits = if (highRes) 24 else 16
            if (highRes) f.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_FLOAT)
            dec = MediaCodec.createDecoderByType(mime)
            dec.configure(f, null, null, 0)
            dec.start()
        }

        override fun fraction() = if (durationUs > 0) (lastPts.toFloat() / durationUs).coerceIn(0f, 1f) else 0f

        override fun read(): IntArray? {
            while (!done) {
                while (!inputDone) {
                    val ii = dec.dequeueInputBuffer(0)
                    if (ii < 0) break
                    val n = ex.readSampleData(dec.getInputBuffer(ii)!!, 0)
                    if (n < 0) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                }
                val oi = dec.dequeueOutputBuffer(info, 2_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcCh = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val ob = dec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val out: IntArray = if (float) {
                        val fb = ob.asFloatBuffer(); val max = (1 shl (bits - 1)) - 1
                        IntArray(fb.remaining()) { (fb.get().coerceIn(-1f, 1f) * max).toInt() }
                    } else {
                        val sb = ob.asShortBuffer(); val sh = bits - 16
                        IntArray(sb.remaining()) { sb.get().toInt() shl sh }
                    }
                    lastPts = info.presentationTimeUs
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    if (out.isNotEmpty()) return downmix(out, srcCh, bits)
                }
            }
            return null
        }

        override fun close() { runCatching { dec.stop() }; runCatching { dec.release() }; ex.release() }
    }

    /** Linear-interpolation resampler on interleaved ints (only used when a rate must change). */
    private class Resampler(private val inRate: Int, private val outRate: Int, private val ch: Int) {
        private var pos = 0.0
        private val last = IntArray(ch)
        private var primed = false
        fun process(inp: IntArray): IntArray {
            if (inRate == outRate) return inp
            val frames = inp.size / ch
            if (!primed) { for (c in 0 until ch) last[c] = inp[c]; primed = true }
            val step = inRate.toDouble() / outRate
            val outFrames = ((frames - pos) / step).toInt().coerceAtLeast(0) + 1
            val out = IntArray(outFrames * ch)
            var o = 0
            while (pos < frames && o < outFrames) {
                val i = pos.toInt(); val frac = pos - i
                for (c in 0 until ch) {
                    val a = if (i == 0) last[c] else inp[(i - 1) * ch + c]
                    val b = inp[i * ch + c]
                    out[o * ch + c] = (a + (b - a) * frac).toInt()
                }
                o++; pos += step
            }
            pos -= frames
            for (c in 0 until ch) last[c] = inp[(frames - 1) * ch + c]
            return if (o == outFrames) out else out.copyOf(o * ch)
        }
    }

    private fun targetRate(r: Int) = if (r in setOf(32000, 44100, 48000)) r else if (r % 44100 == 0) 44100 else if (r > 48000) 48000 else 44100

    // ------------------------------------------------------------------ encoders

    /** AAC-LC at [kbps] into an .m4a, via the phone's encoder, without idle waits. */
    fun transcodeToAac(src: File, ext: String, out: File, kbps: Int, progress: (Float) -> Unit) {
        val pcm = open(src, ext, false)
        val t0 = SystemClock.elapsedRealtime()
        var feedMs = 0L
        try {
            val outRate = targetRate(pcm.rate)
            val ch = pcm.channels
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, outRate, ch).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, kbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            out.delete()
            val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var framesIn = 0L
            val rs = Resampler(pcm.rate, outRate, ch)
            val shift = pcm.bits - 16
            var pending: ShortArray? = null; var pendingPos = 0
            var lastProgress = 0L
            try {
                while (true) {
                    // Feed every free input buffer.
                    while (!inputDone) {
                        val ii = enc.dequeueInputBuffer(0)
                        if (ii < 0) break
                        val sb = enc.getInputBuffer(ii)!!.order(ByteOrder.nativeOrder()).also { it.clear() }.asShortBuffer()
                        val cap = sb.remaining()
                        var written = 0
                        val tf = SystemClock.elapsedRealtime()
                        while (written < cap) {
                            if (pending == null || pendingPos >= pending!!.size) {
                                val chunk = pcm.read() ?: break
                                val r = rs.process(chunk)
                                pending = ShortArray(r.size) { (r[it] shr shift).toShort() }; pendingPos = 0
                                continue
                            }
                            val n = minOf(cap - written, pending!!.size - pendingPos)
                            sb.put(pending!!, pendingPos, n)
                            pendingPos += n; written += n
                        }
                        feedMs += SystemClock.elapsedRealtime() - tf
                        val pts = framesIn * 1_000_000L / outRate
                        framesIn += written / ch
                        if (written == 0) { enc.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else enc.queueInputBuffer(ii, 0, written * 2, pts, 0)
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgress > 250) { progress(pcm.fraction()); lastProgress = now }
                    // Take whatever output is ready; only wait (briefly) when nothing could be fed.
                    var finished = false
                    while (true) {
                        val oi = enc.dequeueOutputBuffer(info, if (inputDone) 5_000 else 1_000)
                        if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(enc.outputFormat); mux.start(); continue }
                        if (oi < 0) continue
                        val ob = enc.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info) }
                        enc.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { finished = true; break }
                    }
                    if (finished) break
                }
            } finally {
                runCatching { enc.stop() }; enc.release()
                runCatching { if (track >= 0) mux.stop() }; runCatching { mux.release() }
            }
            IpodDebug.log("AAC $kbps k: ${pcm.rate} Hz/${pcm.bits} bit → $outRate Hz, ${framesIn} frames, " +
                "${SystemClock.elapsedRealtime() - t0} ms total, $feedMs ms reading/feeding")
        } finally { pcm.close() }
        if (!out.exists() || out.length() < 1000) throw IOException("Conversion to AAC failed")
    }

    /** Apple Lossless (16 or 24 bit) into an .m4a with our own encoder. */
    fun encodeAlac(src: File, ext: String, out: File, progress: (Float) -> Unit) {
        val pcm = open(src, ext, true)
        val t0 = SystemClock.elapsedRealtime()
        try {
            val outRate = targetRate(pcm.rate)
            val ch = pcm.channels
            val enc = AlacEncoder(outRate, ch, pcm.bits)
            val w = AlacM4aWriter(out, enc)
            val rs = Resampler(pcm.rate, outRate, ch)
            val frame = IntArray(AlacEncoder.FRAME * ch)
            var fill = 0
            var lastProgress = 0L
            var total = 0L
            while (true) {
                val chunk = pcm.read()?.let { rs.process(it) } ?: break
                var p = 0
                while (p < chunk.size) {
                    val n = minOf(frame.size - fill, chunk.size - p)
                    System.arraycopy(chunk, p, frame, fill, n); fill += n; p += n
                    if (fill == frame.size) { w.write(enc.encodeFrame(frame, AlacEncoder.FRAME), AlacEncoder.FRAME); total += AlacEncoder.FRAME; fill = 0 }
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgress > 250) { progress(pcm.fraction()); lastProgress = now }
            }
            if (fill > 0) { val n = fill / ch; w.write(enc.encodeFrame(frame.copyOf(fill), n), n); total += n }
            w.finish()
            IpodDebug.log("ALAC ${pcm.bits} bit ${pcm.rate}→$outRate Hz, $total frames, ${SystemClock.elapsedRealtime() - t0} ms")
        } finally { pcm.close() }
        if (!out.exists() || out.length() < 1000) throw IOException("Conversion to Apple Lossless failed")
    }
}

/** Gapless values from an MP4 (ported from IpodDigger's MP4.timing). */
object Mp4Timing {
    data class T(val sampleRate: Double, val samples: Long, val delay: Long = 0, val drain: Long = 0, val bitRateKbps: Int = 0)

    fun read(f: File, fallbackRate: Double, fallbackSeconds: Double): T {
        val fallback = T(if (fallbackRate > 0) fallbackRate else 44100.0, (fallbackSeconds * (if (fallbackRate > 0) fallbackRate else 44100.0)).toLong())
        val d = runCatching { f.readBytes() }.getOrNull() ?: return fallback
        fun u32(i: Int): Long = if (i + 4 <= d.size) ByteBuffer.wrap(d, i, 4).int.toLong() and 0xFFFFFFFFL else 0
        fun u64(i: Int): Long = (u32(i) shl 32) or u32(i + 4)
        fun boxes(start: Int, end: Int): List<Triple<String, Int, Int>> {
            val out = ArrayList<Triple<String, Int, Int>>(); var i = start
            while (i + 8 <= end) {
                var size = u32(i); var hdr = 8
                val type = String(d, i + 4, 4, Charsets.ISO_8859_1)
                if (size == 1L) { size = u64(i + 8); hdr = 16 } else if (size == 0L) size = (end - i).toLong()
                if (size < hdr || i + size > end) break
                out += Triple(type, i + hdr, (i + size).toInt()); i += size.toInt()
            }
            return out
        }
        fun find(s: Int, e: Int, path: List<String>): Pair<Int, Int>? {
            for ((t, bs, be) in boxes(s, e)) if (t == path[0]) return if (path.size == 1) bs to be else find(bs, be, path.drop(1))
            return null
        }
        val moov = find(0, d.size, listOf("moov")) ?: return fallback
        var trak: Pair<Int, Int>? = null
        for ((t, s, e) in boxes(moov.first, moov.second)) if (t == "trak") {
            val h = find(s, e, listOf("mdia", "hdlr"))
            if (h != null && h.first + 12 <= d.size && String(d, h.first + 8, 4, Charsets.ISO_8859_1) == "soun") { trak = s to e; break }
        }
        val tk = trak ?: return fallback
        val mdhd = find(tk.first, tk.second, listOf("mdia", "mdhd")) ?: return fallback
        val ts: Long; val mdur: Long
        if (d[mdhd.first].toInt() == 1) { ts = u32(mdhd.first + 20); mdur = u64(mdhd.first + 24) } else { ts = u32(mdhd.first + 12); mdur = u32(mdhd.first + 16) }
        if (ts <= 0) return fallback
        var movieTs = ts
        find(moov.first, moov.second, listOf("mvhd"))?.let { mv ->
            movieTs = if (d[mv.first].toInt() == 1) u32(mv.first + 20) else u32(mv.first + 12)
            if (movieTs == 0L) movieTs = ts
        }
        var mediaTime = 0L; var valid = mdur
        find(tk.first, tk.second, listOf("edts", "elst"))?.let { el ->
            val v = d[el.first].toInt(); val count = u32(el.first + 4).toInt(); var p = el.first + 8
            repeat(minOf(count, 16)) {
                val sd: Long; val mt: Long
                if (v == 1) { sd = u64(p); mt = u64(p + 8); p += 20 } else { sd = u32(p); mt = u32(p + 4).toInt().toLong(); p += 12 }
                if (mt >= 0) { mediaTime = mt; valid = sd * ts / movieTs }
            }
        }
        var bytes = 0L
        find(tk.first, tk.second, listOf("mdia", "minf", "stbl", "stsz"))?.let { sz ->
            val fixed = u32(sz.first + 4); val count = u32(sz.first + 8).toInt()
            if (fixed != 0L) bytes = fixed * count else for (k in 0 until count) bytes += u32(sz.first + 12 + 4 * k)
        }
        val rate = if (fallbackRate > 0) fallbackRate else ts.toDouble()
        val scale = rate / ts
        val seconds = valid.toDouble() / ts
        return T(rate, (valid * scale).toLong(), (mediaTime * scale).toLong(),
            maxOf(((mdur - mediaTime - valid) * scale).toLong(), 0),
            if (bytes > 0 && seconds > 0) Math.round(bytes * 8 / seconds / 1000).toInt() else 0)
    }
}
