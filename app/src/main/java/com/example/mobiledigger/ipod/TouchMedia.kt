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
import com.example.mobiledigger.util.CrashLogger
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

object TouchMedia {
    private const val TAG = "TouchMedia"
    private const val AAC_BITRATE = 256_000

    /** Copies [uri] into [work] (converting to AAC when the iPod can't play it) and reads what the sync needs. */
    fun prepare(ctx: Context, uri: Uri, displayName: String, work: File, index: Int,
                progress: (Float) -> Unit = {}): PreparedAudio {
        work.mkdirs()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val src = File(work, "src-$index.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { i -> src.outputStream().use { i.copyTo(it, 256 * 1024) } }
            ?: throw IOException("Can't read $displayName")
        val tags = readTags(src, displayName)
        val cover = prepareCover(src, File(work, "cover-$index.jpg"))
        val mime = trackMime(src)
        val title = tags["title"] ?: displayName.substringBeforeLast('.')
        return when {
            ext == "mp3" || mime == "audio/mpeg" -> {
                val out = File(work, "track-$index.mp3"); src.renameTo(out)
                val f = audioFormat(out)
                val sr = f?.optInt(MediaFormat.KEY_SAMPLE_RATE)?.toDouble()?.takeIf { it > 0 } ?: 44100.0
                val durUs = f?.optLong(MediaFormat.KEY_DURATION) ?: 0L
                val br = (tags["bitrate"]?.toIntOrNull() ?: 0) / 1000
                PreparedAudio(out, "mp3", "mp3", tags, sr, (durUs / 1e6 * sr).toLong(), 0, 0,
                    if (br > 0) br else 320, f?.optInt(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: 2, cover, title, false)
            }
            (ext == "m4a" || ext == "mp4") && (mime == "audio/mp4a-latm" || mime == "audio/alac") -> {
                val out = File(work, "track-$index.m4a"); src.renameTo(out)
                val codec = if (mime == "audio/alac") "alac" else "aac"
                m4a(out, codec, tags, cover, title, false)
            }
            else -> {
                val out = File(work, "track-$index.m4a")
                transcodeToAac(src, out, progress)
                src.delete()
                m4a(out, "aac", tags, cover, title, true)
            }
        }
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
            val base = displayName.substringBeforeLast('.')
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

    // ------------------------------------------------------------------ transcoding

    /** Decodes [src] (FLAC, WAV, OGG, Opus, AIFF…) and encodes AAC-LC 256 kbps into an .m4a. */
    fun transcodeToAac(src: File, out: File, progress: (Float) -> Unit) {
        val pcm = if (isAiff(src)) AiffPcm(src) else DecodedPcm(src)
        try {
            val inRate = pcm.sampleRate
            val outRate = if (inRate in setOf(32000, 44100, 48000)) inRate else if (inRate % 44100 == 0) 44100 else 48000
            val outCh = minOf(pcm.channels, 2)
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, outRate, outCh).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
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
            val resampler = Resampler(inRate, outRate, pcm.channels, outCh)
            var pending: ShortArray? = null; var pendingPos = 0
            try {
                while (true) {
                    if (!inputDone) {
                        val ii = enc.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = enc.getInputBuffer(ii)!!.order(ByteOrder.nativeOrder())
                            buf.clear()
                            val cap = buf.remaining() / 2
                            var written = 0
                            while (written < cap) {
                                if (pending == null || pendingPos >= pending!!.size) {
                                    val chunk = pcm.read() ?: break
                                    pending = resampler.process(chunk); pendingPos = 0
                                    progress(pcm.fraction())
                                    continue
                                }
                                val n = minOf(cap - written, pending!!.size - pendingPos)
                                buf.asShortBuffer().apply { position(written) }.put(pending!!, pendingPos, n)
                                pendingPos += n; written += n
                            }
                            val pts = framesIn * 1_000_000L / outRate
                            framesIn += written / outCh
                            if (written == 0) {
                                enc.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                            } else enc.queueInputBuffer(ii, 0, written * 2, pts, 0)
                        }
                    }
                    val oi = enc.dequeueOutputBuffer(info, 10_000)
                    when {
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = mux.addTrack(enc.outputFormat); mux.start() }
                        oi >= 0 -> {
                            val ob = enc.getOutputBuffer(oi)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && track >= 0) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info) }
                            enc.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            } finally {
                runCatching { enc.stop() }; enc.release()
                runCatching { if (track >= 0) mux.stop() }; runCatching { mux.release() }
            }
        } finally { pcm.close() }
        if (!out.exists() || out.length() < 1000) throw IOException("Conversion to AAC failed")
    }

    private interface PcmSource {
        val sampleRate: Int
        val channels: Int
        /** Interleaved 16-bit samples, null at the end. */
        fun read(): ShortArray?
        fun fraction(): Float
        fun close()
    }

    /** Any format Android can decode, as 16-bit PCM. */
    private class DecodedPcm(src: File) : PcmSource {
        private val ex = MediaExtractor().apply { setDataSource(src.path) }
        private val dec: MediaCodec
        override var sampleRate = 44100; private set
        override var channels = 2; private set
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
            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            durationUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            dec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(f, null, null, 0)
            dec.start()
        }

        override fun fraction() = if (durationUs > 0) (lastPts.toFloat() / durationUs).coerceIn(0f, 1f) else 0f

        override fun read(): ShortArray? {
            while (!done) {
                if (!inputDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(ii)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val ob = dec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val out = if (float) {
                        val fb = ob.asFloatBuffer(); ShortArray(fb.remaining()) { (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                    } else {
                        val sb = ob.asShortBuffer(); ShortArray(sb.remaining()).also { sb.get(it) }
                    }
                    lastPts = info.presentationTimeUs
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    if (out.isNotEmpty()) return out
                }
            }
            return null
        }

        override fun close() { runCatching { dec.stop() }; runCatching { dec.release() }; ex.release() }
    }

    private fun isAiff(f: File): Boolean = runCatching {
        RandomAccessFile(f, "r").use { r -> val b = ByteArray(12); r.readFully(b); String(b, 0, 4) == "FORM" && String(b, 8, 4).let { it == "AIFF" || it == "AIFC" } }
    }.getOrDefault(false)

    /** AIFF (big-endian PCM, Android can't decode it) read directly. */
    private class AiffPcm(f: File) : PcmSource {
        private val raf = RandomAccessFile(f, "r")
        override var sampleRate = 44100; private set
        override var channels = 2; private set
        private var bits = 16
        private var dataStart = 0L
        private var dataEnd = 0L
        private var littleEndian = false

        init {
            raf.seek(12)
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4).also { raf.readFully(it) }.let { String(it, Charsets.US_ASCII) }
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val start = raf.filePointer
                when (id) {
                    "COMM" -> {
                        channels = raf.readShort().toInt(); raf.readInt(); bits = raf.readShort().toInt()
                        val ext = ByteArray(10).also { raf.readFully(it) }
                        sampleRate = ieee80(ext).toInt()
                        if (size >= 22) { val ct = ByteArray(4).also { raf.readFully(it) }; littleEndian = String(ct) == "sowt" }
                    }
                    "SSND" -> { val offset = raf.readInt(); raf.readInt(); dataStart = start + 8 + offset; dataEnd = start + size }
                }
                raf.seek(start + size + (size and 1))
            }
            if (dataEnd <= dataStart) throw IOException("No audio data in this AIFF")
            raf.seek(dataStart)
        }

        private fun ieee80(b: ByteArray): Double {
            val exp = ((b[0].toInt() and 0x7F) shl 8) or (b[1].toInt() and 0xFF)
            var mant = 0L
            for (i in 2 until 10) mant = (mant shl 8) or (b[i].toLong() and 0xFF)
            return mant.toULong().toDouble() * Math.pow(2.0, (exp - 16383 - 63).toDouble())
        }

        override fun fraction() = ((raf.filePointer - dataStart).toFloat() / (dataEnd - dataStart)).coerceIn(0f, 1f)

        override fun read(): ShortArray? {
            val bps = bits / 8
            val left = dataEnd - raf.filePointer
            if (left < bps) return null
            val n = minOf(left, 64L * 1024 / bps * bps).toInt()
            val b = ByteArray(n); raf.readFully(b)
            return ShortArray(n / bps) { i ->
                val o = i * bps
                // Take the two most significant bytes of each sample
                val hi = if (littleEndian) b[o + bps - 1] else b[o]
                val lo = if (bps == 1) 0 else if (littleEndian) b[o + bps - 2] else b[o + 1]
                if (bps == 1) ((hi.toInt() - 128) shl 8).toShort() else (((hi.toInt()) shl 8) or (lo.toInt() and 0xFF)).toShort()
            }
        }

        override fun close() = raf.close()
    }

    /** Linear-interpolation resampler + down-mix to stereo (only used when the rate or channel count must change). */
    private class Resampler(private val inRate: Int, private val outRate: Int, private val inCh: Int, private val outCh: Int) {
        private var pos = 0.0
        private var last = ShortArray(inCh)
        fun process(inp: ShortArray): ShortArray {
            val frames = inp.size / inCh
            fun sample(f: Int, c: Int): Int = if (f < 0) last[minOf(c, inCh - 1)].toInt() else inp[f * inCh + minOf(c, inCh - 1)].toInt()
            fun mixed(f: Int, c: Int): Int = if (inCh <= 2 || outCh == 1 && inCh == 1) sample(f, c) else {
                // >2 channels: front left/right (+ centre at -3 dB)
                val centre = if (inCh > 2) sample(f, 2) * 7 / 10 else 0
                (sample(f, c) + centre).coerceIn(-32768, 32767)
            }
            if (inRate == outRate && inCh == outCh) return inp
            if (inRate == outRate) return ShortArray(frames * outCh) { i -> mixed(i / outCh, i % outCh).toShort() }
            val step = inRate.toDouble() / outRate
            val out = ArrayList<Short>(((frames / step) + 2).toInt() * outCh)
            while (pos < frames) {
                val f0 = kotlin.math.floor(pos).toInt() - 1 + 1
                val frac = pos - kotlin.math.floor(pos)
                for (c in 0 until outCh) {
                    val a = mixed(f0 - 1, c); val b = mixed(f0, c)
                    out.add((a + (b - a) * frac).toInt().toShort())
                }
                pos += step
            }
            pos -= frames
            for (c in 0 until inCh) last[c] = inp[(frames - 1) * inCh + c]
            return out.toShortArray()
        }
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
