package com.example.mobiledigger.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 32-band equalizer + live spectrum for MobileDigger's own playback.
 *
 * VLC is told to play through ONE fixed audio session (see [sessionId]); Android's
 * DynamicsProcessing (pre-EQ with 32 bands) and Visualizer attach to that session. Effects stick
 * to the session, so they keep working across tracks; other apps are not affected.
 */
object AudioFx {
    const val BANDS = 32
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    private const val F_LO = 20f
    private const val F_HI = 20000f
    private const val PREFS = "audio_fx"

    /** Band centres, log-spaced 20 Hz … 20 kHz. */
    val centers = FloatArray(BANDS) { F_LO * (F_HI / F_LO).pow(it / (BANDS - 1f)) }
    /** Upper edge of each band (geometric midpoint to the next centre). */
    private val cutoffs = FloatArray(BANDS) { i -> if (i == BANDS - 1) F_HI else sqrt(centers[i] * centers[i + 1]) }
    private fun lowEdge(i: Int) = if (i == 0) F_LO / 1.2f else cutoffs[i - 1]

    private var appContext: Context? = null
    @Volatile private var session = 0

    private val _gains = MutableStateFlow(FloatArray(BANDS))
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _available = MutableStateFlow(true)
    /** False if this phone refused to create the equalizer. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var dp: DynamicsProcessing? = null

    /** Audio session VLC must use. Created once per process, before LibVLC starts. */
    fun sessionId(context: Context): Int {
        if (session == 0) synchronized(this) {
            if (session == 0) {
                appContext = context.applicationContext
                val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                session = am.generateAudioSessionId()
                load()
                apply()
            }
        }
        return session
    }

    // ---- Equalizer ----------------------------------------------------------------------

    fun setGain(band: Int, db: Float) {
        if (band !in 0 until BANDS) return
        val g = _gains.value.copyOf()
        g[band] = db.coerceIn(MIN_DB, MAX_DB)
        _gains.value = g
        apply()
    }

    fun setAll(gains: FloatArray) {
        _gains.value = FloatArray(BANDS) { gains.getOrElse(it) { 0f }.coerceIn(MIN_DB, MAX_DB) }
        apply(); save()
    }

    fun setEnabled(on: Boolean) { _enabled.value = on; apply(); save() }

    fun save() {
        val c = appContext ?: return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("gains", _gains.value.joinToString(","))
            .putBoolean("enabled", _enabled.value)
            .apply()
    }

    private fun load() {
        val p = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        p.getString("gains", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }?.let { l ->
            _gains.value = when {
                l.size == BANDS -> l.toFloatArray()
                // Saved with another band count (e.g. 24): stretch the same curve onto the new bands
                l.size >= 2 -> FloatArray(BANDS) { i ->
                    val x = i * (l.size - 1f) / (BANDS - 1f)
                    val k = x.toInt().coerceAtMost(l.size - 2)
                    l[k] + (l[k + 1] - l[k]) * (x - k)
                }
                else -> FloatArray(BANDS)
            }
        }
        _enabled.value = p.getBoolean("enabled", true)
    }

    @Synchronized
    private fun apply() {
        if (session == 0) return
        val gains = _gains.value
        val flat = gains.all { it == 0f }
        try {
            val fx = dp ?: run {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    /* preEq */ true, BANDS, /* mbc */ false, 0, /* postEq */ false, 0, /* limiter */ true
                ).setPreferredFrameDuration(10f).build()
                DynamicsProcessing(0, session, cfg).also { dp = it }
            }
            val eq = DynamicsProcessing.Eq(true, true, BANDS)
            for (i in 0 until BANDS) eq.setBand(i, DynamicsProcessing.EqBand(true, cutoffs[i], gains[i]))
            fx.setPreEqAllChannelsTo(eq)
            // Headroom: pull the input down by the biggest boost so boosted bands don't clip;
            // the limiter catches whatever is left.
            val boost = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            fx.setInputGainAllChannelsTo(-boost)
            fx.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            fx.enabled = _enabled.value && !flat
            _available.value = true
        } catch (e: Throwable) {
            CrashLogger.log("AudioFx", "Equalizer unavailable: ${e.message}")
            _available.value = false
            runCatching { dp?.release() }; dp = null
        }
    }

    // ---- Live spectrum ----------------------------------------------------------------------

    /** Spectrum resolution (independent of the EQ bands) — enough points for a smooth line. */
    const val SPECTRUM_POINTS = 96
    private val specCenters = FloatArray(SPECTRUM_POINTS) { F_LO * (F_HI / F_LO).pow(it / (SPECTRUM_POINTS - 1f)) }
    private val specHi = FloatArray(SPECTRUM_POINTS) { i -> if (i == SPECTRUM_POINTS - 1) F_HI else sqrt(specCenters[i] * specCenters[i + 1]) }
    private fun specLo(i: Int) = if (i == 0) F_LO / 1.05f else specHi[i - 1]

    private val _spectrum = MutableStateFlow(FloatArray(SPECTRUM_POINTS))
    /** 0..1 per spectrum point (log-spaced 20 Hz–20 kHz), ~30 updates/s while [startSpectrum] is active. */
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()
    private var visualizer: Visualizer? = null

    /** Needs RECORD_AUDIO (Android's rule for any Visualizer; nothing is recorded). */
    @Synchronized
    fun startSpectrum(): Boolean {
        if (session == 0) return false
        if (visualizer != null) return true
        return try {
            val v = Visualizer(session)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) _spectrum.value = bandsFromFft(fft, samplingRate / 1000f)
                }
            }, Visualizer.getMaxCaptureRate().coerceAtMost(30_000), false, true)
            v.enabled = true
            visualizer = v
            true
        } catch (e: Throwable) {
            CrashLogger.log("AudioFx", "Spectrum unavailable: ${e.message}")
            false
        }
    }

    @Synchronized
    fun stopSpectrum() {
        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null
        _spectrum.value = FloatArray(SPECTRUM_POINTS)
    }

    /** Visualizer FFT bytes → loudest magnitude per band, 0..1 (about 50 dB of range). */
    private fun bandsFromFft(fft: ByteArray, sampleRateHz: Float): FloatArray {
        val n = fft.size              // capture size
        val bins = n / 2
        val hzPerBin = sampleRateHz / n
        fun mag(k: Int): Float = when {
            k <= 0 -> kotlin.math.abs(fft[0].toFloat())
            k >= bins -> kotlin.math.abs(fft[1].toFloat())
            else -> hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
        }
        val out = FloatArray(SPECTRUM_POINTS)
        for (i in 0 until SPECTRUM_POINTS) {
            val lo = (specLo(i) / hzPerBin)
            val hi = (specHi[i] / hzPerBin)
            var m = 0f
            if (hi - lo < 1f) {
                // Band narrower than one FFT bin (bass): interpolate at the band centre
                val x = (specCenters[i] / hzPerBin).coerceIn(1f, bins - 1f)
                val k = x.toInt()
                val f = x - k
                m = mag(k) * (1 - f) + mag(k + 1) * f
            } else {
                for (k in lo.toInt().coerceAtLeast(1)..hi.toInt().coerceAtMost(bins - 1)) m = maxOf(m, mag(k))
            }
            val db = if (m <= 0f) 0f else 20f * log10(m)   // byte FFT: 0 … ~45 dB
            out[i] = ((db - 2f) / 40f).coerceIn(0f, 1f)
        }
        return out
    }

    // ---- Presets -------------------------------------------------------------------------

    /** Smooth curves defined at a few frequencies, interpolated on a log scale. */
    private fun curve(vararg pts: Pair<Float, Float>) = FloatArray(BANDS) { i ->
        val f = log10(centers[i])
        val p = pts.map { log10(it.first) to it.second }
        when {
            f <= p.first().first -> p.first().second
            f >= p.last().first -> p.last().second
            else -> {
                val j = p.indexOfFirst { it.first >= f }
                val (x0, y0) = p[j - 1]; val (x1, y1) = p[j]
                y0 + (y1 - y0) * (f - x0) / (x1 - x0)
            }
        }
    }

    val presets: List<Pair<String, FloatArray>> by lazy {
        listOf(
            "Flat" to FloatArray(BANDS),
            "Bass boost" to curve(30f to 7f, 80f to 6f, 200f to 2f, 500f to 0f),
            "Club" to curve(40f to 5f, 100f to 4f, 400f to -1f, 2000f to 1f, 8000f to 3f, 16000f to 2f),
            "Deep house" to curve(35f to 6f, 90f to 4f, 300f to -2f, 1000f to 0f, 6000f to 1f, 12000f to 2f),
            "Vocal" to curve(80f to -2f, 300f to 0f, 1500f to 3f, 3500f to 4f, 8000f to 1f, 16000f to 0f),
            "Treble" to curve(1000f to 0f, 4000f to 3f, 10000f to 6f, 18000f to 7f),
            "Loudness" to curve(30f to 6f, 120f to 3f, 1000f to 0f, 6000f to 2f, 14000f to 5f),
            "Cut lows" to curve(20f to -12f, 60f to -8f, 120f to -2f, 200f to 0f),
        )
    }
}
