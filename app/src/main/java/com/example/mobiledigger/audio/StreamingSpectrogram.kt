package com.example.mobiledigger.audio

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Builds a spectrogram while audio is still being decoded, so any length (up to the whole
 * track) fits in memory: samples are dropped as soon as their FFT columns are done, and each
 * column is stored as one byte per frequency row (loudness in 0.8 dB steps).
 *
 * Colours are normalised against the loudest point of the WHOLE analysed span at the end, so a
 * long spectrogram looks the same as the first 3 minutes did.
 *
 * Pure JVM (no Android APIs) so it can be unit-tested.
 */
class StreamingSpectrogram(
    val sampleRate: Int,
    private val fftSize: Int,
    /** Frequency rows; row 0 = 0 Hz. */
    val height: Int,
    /** Top of the (linear) frequency axis, already clamped to Nyquist by the caller. */
    val maxFreq: Int,
    pixelsPerSecond: Int,
    private val maxColumns: Int
) : MonoSink {
    val hop: Int = (sampleRate / pixelsPerSecond).coerceAtLeast(64)

    private val window = FloatArray(fftSize) { i -> (0.5 * (1 - cos(2 * PI * i / (fftSize - 1)))).toFloat() }
    private val binIndex = IntArray(height) { row ->
        val targetFreq = (row.toLong() * maxFreq) / height
        ((targetFreq * fftSize) / sampleRate).toInt().coerceAtMost(fftSize / 2 - 1)
    }
    private val levels = log2(fftSize)
    private val bitRev = IntArray(fftSize) { i -> Integer.reverse(i) ushr (32 - levels) }
    private val cosT = FloatArray(fftSize / 2) { cos(-2 * PI * it / fftSize).toFloat() }
    private val sinT = FloatArray(fftSize / 2) { sin(-2 * PI * it / fftSize).toFloat() }

    private val columns = ArrayList<ByteArray>()
    private val rowPower = DoubleArray(height)
    private var maxLevel = 0

    // Pending samples: buffer[0] is absolute sample index bufStart
    private var buffer = ShortArray(fftSize + hop * 256)
    private var bufLen = 0
    private var bufStart = 0L
    private var totalSamples = 0L

    private val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    private val pool = Executors.newFixedThreadPool(workers) { r -> Thread(r, "Spectrogram").apply { isDaemon = true } }
    private val scratch = ThreadLocal.withInitial { FloatArray(fftSize) to FloatArray(fftSize) }

    val columnCount: Int get() = columns.size
    val analyzedSamples: Long get() = totalSamples
    val isFull: Boolean get() = columns.size >= maxColumns

    override fun accept(block: ShortArray, count: Int) {
        if (isFull) { totalSamples += count; return }
        if (bufLen + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, bufLen + count))
        System.arraycopy(block, 0, buffer, bufLen, count)
        bufLen += count
        totalSamples += count
        process(final = false)
    }

    /** Finishes the last (zero-padded) columns. Call once after decoding. */
    fun finish() {
        process(final = true)
        pool.shutdown()
    }

    private fun process(final: Boolean) {
        val first = columns.size
        val available = bufStart + bufLen
        var last = first
        while (last < maxColumns) {
            val start = last.toLong() * hop
            val ready = if (final) start < available else start + fftSize <= available
            if (!ready) break
            last++
        }
        if (!final && last - first < 32) return // batch columns for the thread pool
        if (last == first) return

        val tasks = (first until last).chunked(((last - first) + workers - 1) / workers).map { range ->
            Callable { range.map { c -> column(c) } }
        }
        val results = pool.invokeAll(tasks).flatMap { it.get() }
        results.forEach { (bytes, rowP) ->
            columns.add(bytes)
            for (r in 0 until height) rowPower[r] += rowP[r]
        }

        // Drop samples no future column needs
        val keepFrom = columns.size.toLong() * hop
        val drop = (keepFrom - bufStart).coerceIn(0, bufLen.toLong()).toInt()
        if (drop > 0) {
            System.arraycopy(buffer, drop, buffer, 0, bufLen - drop)
            bufLen -= drop
            bufStart += drop
        }
    }

    /** FFT of column [c] → (dB bytes, top row first; linear power per row, 0 Hz first). */
    private fun column(c: Int): Pair<ByteArray, DoubleArray> {
        val (re, im) = scratch.get()
        val start = c.toLong() * hop - bufStart
        for (i in 0 until fftSize) {
            val idx = start + i
            val v = if (idx in 0 until bufLen) buffer[idx.toInt()].toFloat() else 0f
            val j = bitRev[i]
            re[j] = v * window[i]
            im[j] = 0f
        }
        var size = 2
        while (size <= fftSize) {
            val half = size / 2
            val step = fftSize / size
            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                    re[j] += xr; im[j] += xi
                    k += step
                }
                i += size
            }
            size *= 2
        }
        val bytes = ByteArray(height)
        val rowP = DoubleArray(height)
        var localMax = 0
        for (row in 0 until height) {
            val b = binIndex[row]
            val power = re[b].toDouble() * re[b] + im[b].toDouble() * im[b]
            rowP[row] = power
            val q = if (power <= 0.0) 0 else ((10 * log10(power) + DB_OFFSET) / DB_STEP).toInt().coerceIn(0, 255)
            bytes[height - 1 - row] = q.toByte()
            if (q > localMax) localMax = q
        }
        synchronized(this) { if (localMax > maxLevel) maxLevel = localMax }
        return bytes to rowP
    }

    /** Average linear power per frequency row (0 Hz first) — for cutoff detection. */
    fun rowMeanPower(): DoubleArray {
        val n = columns.size.coerceAtLeast(1)
        return DoubleArray(height) { rowPower[it] / n }
    }

    /**
     * ARGB pixels of output row [y] (0 = top) at [outWidth] columns. When the spectrogram has
     * more columns than [outWidth], neighbouring columns are merged keeping the loudest value,
     * so short events and the cutoff stay visible.
     */
    fun renderRow(y: Int, outWidth: Int, out: IntArray) {
        val cols = columns.size
        val top = maxLevel
        val floor = top - (RANGE_DB / DB_STEP)
        for (x in 0 until outWidth) {
            val a = (x.toLong() * cols / outWidth).toInt()
            val b = (((x + 1).toLong() * cols / outWidth).toInt()).coerceAtLeast(a + 1).coerceAtMost(cols)
            var v = 0
            for (c in a until b) {
                val q = columns[c][y].toInt() and 0xFF
                if (q > v) v = q
            }
            val norm = if (v == 0) 0f else ((v - floor) / (top - floor)).coerceIn(0f, 1f)
            out[x] = colorFor(norm)
        }
    }

    companion object {
        const val RANGE_DB = 90f
        private const val DB_STEP = 0.8f
        private const val DB_OFFSET = 30.0

        private fun log2(n: Int): Int { var l = 0; var v = n; while (v > 1) { v = v shr 1; l++ }; return l }

        private val STOPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        private val COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFF00008B.toInt(), 0xFF8A2BE2.toInt(), 0xFFFF0000.toInt(), 0xFFFFFF00.toInt()
        )

        /** Same map as before: black → dark blue → purple → red → yellow. */
        fun colorFor(norm: Float): Int {
            if (norm <= 0f) return COLORS[0]
            if (norm >= 1f) return COLORS[4]
            var i = 1
            while (STOPS[i] < norm) i++
            val f = (norm - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1])
            val c1 = COLORS[i - 1]; val c2 = COLORS[i]
            fun ch(shift: Int) = (((c1 shr shift) and 0xFF) + (((c2 shr shift) and 0xFF) - ((c1 shr shift) and 0xFF)) * f).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
