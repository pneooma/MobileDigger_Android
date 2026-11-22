package com.example.mobiledigger.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.mobiledigger.audio.AudioFx
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

private fun fmtHz(f: Float) = when {
    f >= 10000f -> "${(f / 1000f).roundToInt()} kHz"
    f >= 1000f -> "${"%.1f".format(f / 1000f)} kHz"
    else -> "${f.roundToInt()} Hz"
}
private fun fmtDb(db: Float) = (if (db > 0) "+" else if (db < 0) "−" else "") + "%.1f dB".format(abs(db))

private val GRID_FREQS = listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)
private val AXIS_LABELS = listOf(20f to "20", 50f to "50", 100f to "100", 200f to "200", 500f to "500",
    1000f to "1k", 2000f to "2k", 5000f to "5k", 10000f to "10k", 20000f to "20k")

/** x position (0..1) of a frequency on the 20 Hz – 20 kHz log axis. */
private fun xOfFreq(f: Float) = (ln(f / 20f) / ln(1000f)).coerceIn(0f, 1f)

/** Smooth line through the points (Catmull-Rom → cubic Bézier). */
private fun smoothPath(xs: FloatArray, ys: FloatArray, path: Path = Path()): Path {
    path.reset()
    if (xs.isEmpty()) return path
    path.moveTo(xs[0], ys[0])
    for (i in 0 until xs.size - 1) {
        val x0 = xs[maxOf(i - 1, 0)]; val y0 = ys[maxOf(i - 1, 0)]
        val x1 = xs[i]; val y1 = ys[i]
        val x2 = xs[i + 1]; val y2 = ys[i + 1]
        val x3 = xs[minOf(i + 2, xs.size - 1)]; val y3 = ys[minOf(i + 2, xs.size - 1)]
        path.cubicTo(
            x1 + (x2 - x0) / 6f, y1 + (y2 - y0) / 6f,
            x2 - (x3 - x1) / 6f, y2 - (y3 - y1) / 6f,
            x2, y2
        )
    }
    return path
}

/** 24-band equalizer with an animated spectrum analyzer underneath. */
@Composable
fun EqualizerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val gains by AudioFx.gains.collectAsState()
    val enabled by AudioFx.enabled.collectAsState()
    val available by AudioFx.available.collectAsState()
    var selected by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }

    val accent = cs.primary
    val accent2 = cs.tertiary
    val active = enabled && available
    val curveColor = if (active) accent else cs.outline
    val activeAlpha by animateFloatAsState(if (active) 1f else 0.45f, tween(250), label = "eqAlpha")

    val presetName = AudioFx.presets.firstOrNull { (_, c) -> c.indices.all { abs(c[it] - gains[it]) < 0.05f } }?.first ?: "Custom"

    // Spectrum needs RECORD_AUDIO (Android's rule for reading audio output; nothing is recorded)
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var spectrumOk by remember { mutableStateOf(true) }
    DisposableEffect(micGranted) {
        if (micGranted) spectrumOk = AudioFx.startSpectrum()
        onDispose { AudioFx.stopSpectrum() }
    }

    // Animated spectrum: eased toward each new reading every frame, with a slowly falling peak line
    val n = AudioFx.SPECTRUM_POINTS
    val level = remember { FloatArray(n) }
    val peak = remember { FloatArray(n) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(micGranted) {
        if (!micGranted) return@LaunchedEffect
        while (true) {
            withFrameNanos { tick = it }
            val s = AudioFx.spectrum.value
            for (i in 0 until n) {
                // light spatial smoothing so the line flows instead of jittering
                val target = (s[maxOf(i - 1, 0)] + 2 * s[i] + s[minOf(i + 1, n - 1)]) / 4f
                level[i] += (target - level[i]) * (if (target > level[i]) 0.45f else 0.12f)
                peak[i] = if (level[i] > peak[i]) level[i] else (peak[i] - 0.004f).coerceAtLeast(level[i])
            }
        }
    }

    Dialog(onDismissRequest = { AudioFx.save(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {

                // ---- Header ---------------------------------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Text(
                            if (!available) "Not available on this phone" else if (active) "$presetName · ${AudioFx.BANDS} bands" else "Bypassed",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) accent else cs.onSurfaceVariant
                        )
                    }
                    // Power button: lit when the EQ is on
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (active) accent.copy(alpha = 0.18f) else cs.surfaceContainerHighest)
                            .border(1.5.dp, if (active) accent else cs.outlineVariant, CircleShape)
                            .clickable(enabled = available) { AudioFx.setEnabled(!enabled) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.PowerSettingsNew, "On / off", tint = if (active) accent else cs.onSurfaceVariant) }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { AudioFx.save(); onDismiss() }) { Icon(Icons.Default.Close, "Close") }
                }
                Spacer(Modifier.height(14.dp))

                // ---- Presets --------------------------------------------------------------
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.presets.forEach { (name, curve) ->
                        val on = name == presetName
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) accent else cs.surfaceContainerHigh)
                                .border(1.dp, if (on) accent else cs.outlineVariant, RoundedCornerShape(50))
                                .clickable { AudioFx.setAll(curve); selected = -1; if (!enabled) AudioFx.setEnabled(true) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(name, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) cs.onPrimary else cs.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ---- EQ graph -------------------------------------------------------------
                val graphBg = Brush.verticalGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerLow))
                val gridColor = cs.onSurface.copy(alpha = 0.07f)
                val zeroColor = cs.onSurface.copy(alpha = 0.22f)
                val labelColor = cs.onSurfaceVariant
                val handleBg = cs.surfaceContainerLowest
                Box(
                    Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp))
                        .background(graphBg).border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                ) {
                    var w by remember { mutableFloatStateOf(1f) }
                    var h by remember { mutableFloatStateOf(1f) }
                    var last by remember { mutableIntStateOf(-1) }
                    val padX = with(androidx.compose.ui.platform.LocalDensity.current) { 7.dp.toPx() }
                    // Bands sit at their real frequency on the same log axis as the grid and labels
                    fun bandX(i: Int) = padX + (w - 2 * padX) * xOfFreq(AudioFx.centers[i])
                    fun bandAt(x: Float) = (0 until AudioFx.BANDS).minBy { abs(bandX(it) - x) }
                    fun yOf(db: Float, height: Float): Float {
                        val pad = height * 0.10f
                        return pad + (AudioFx.MAX_DB - db) / (AudioFx.MAX_DB - AudioFx.MIN_DB) * (height - 2 * pad)
                    }
                    fun dbAt(y: Float): Float {
                        val pad = h * 0.10f
                        val t = ((y - pad) / (h - 2 * pad)).coerceIn(0f, 1f)
                        val db = AudioFx.MAX_DB - t * (AudioFx.MAX_DB - AudioFx.MIN_DB)
                        return if (abs(db) < 0.6f) 0f else (db * 2).roundToInt() / 2f   // 0.5 dB steps, sticky 0
                    }
                    fun paint(x: Float, y: Float) {
                        if (!available) return
                        val b = bandAt(x); val db = dbAt(y)
                        if (last >= 0 && abs(b - last) > 1) {         // fill bands skipped by a fast swipe
                            val from = gains[last]; val step = if (b > last) 1 else -1
                            var i = last + step
                            while (i != b) { AudioFx.setGain(i, from + (db - from) * (i - last).toFloat() / (b - last)); i += step }
                        }
                        AudioFx.setGain(b, db); last = b; selected = b
                        if (!enabled) AudioFx.setEnabled(true)
                    }
                    val curvePath = remember { Path() }
                    val fillPath = remember { Path() }

                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { o -> val b = bandAt(o.x); AudioFx.setGain(b, 0f); selected = b; AudioFx.save() },
                                    onTap = { o -> last = -1; paint(o.x, o.y); AudioFx.save() }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { o -> dragging = true; last = -1; paint(o.x, o.y) },
                                    onDragEnd = { dragging = false; last = -1; AudioFx.save() },
                                    onDragCancel = { dragging = false; last = -1; AudioFx.save() },
                                    onDrag = { change, _ -> paint(change.position.x, change.position.y) }
                                )
                            }
                    ) {
                        w = size.width; h = size.height
                        val zero = yOf(0f, size.height)
                        // Grid: frequency verticals + dB horizontals
                        GRID_FREQS.forEach { f ->
                            val x = padX + (size.width - 2 * padX) * xOfFreq(f)
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
                        }
                        listOf(12f, 6f, -6f, -12f).forEach { db ->
                            drawLine(gridColor, Offset(0f, yOf(db, size.height)), Offset(size.width, yOf(db, size.height)), 0.5.dp.toPx())
                        }
                        drawLine(zeroColor, Offset(0f, zero), Offset(size.width, zero), 0.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

                        // Curve through the band points, extended flat to the edges
                        val xs = FloatArray(AudioFx.BANDS + 2)
                        val ys = FloatArray(AudioFx.BANDS + 2)
                        xs[0] = 0f; ys[0] = yOf(gains[0], size.height)
                        for (i in 0 until AudioFx.BANDS) { xs[i + 1] = bandX(i); ys[i + 1] = yOf(gains[i], size.height) }
                        xs[xs.size - 1] = size.width; ys[ys.size - 1] = yOf(gains.last(), size.height)
                        smoothPath(xs, ys, curvePath)

                        // Fill between the curve and 0 dB: boosts warm, cuts cool
                        fillPath.reset(); fillPath.addPath(curvePath)
                        fillPath.lineTo(size.width, zero); fillPath.lineTo(0f, zero); fillPath.close()
                        drawPath(fillPath, Brush.verticalGradient(
                            0f to curveColor.copy(alpha = 0.38f * activeAlpha),
                            zero / size.height to curveColor.copy(alpha = 0.04f),
                            1f to accent2.copy(alpha = 0.30f * activeAlpha)
                        ))
                        // Glow + line
                        drawPath(curvePath, curveColor.copy(alpha = 0.25f * activeAlpha), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(curvePath, curveColor.copy(alpha = activeAlpha), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))

                        // Band handles: big, ringed dots; hollow when the band is at 0 dB
                        val r = 4.5.dp.toPx(); val ring = 1.5.dp.toPx()  // 32 bands: dots just touch on a phone
                        for (i in 0 until AudioFx.BANDS) {
                            val c = Offset(bandX(i), yOf(gains[i], size.height))
                            if (i == selected) {
                                drawCircle(curveColor.copy(alpha = 0.28f), r * 2.6f, c)
                                drawCircle(Color.White, r * 1.45f, c)
                                drawCircle(curveColor, r * 1.05f, c)
                            } else if (gains[i] != 0f) {
                                drawCircle(handleBg, r + ring, c)
                                drawCircle(curveColor.copy(alpha = activeAlpha), r, c)
                            } else {
                                drawCircle(handleBg, r + ring * 0.5f, c)
                                drawCircle(curveColor.copy(alpha = 0.9f * activeAlpha), r, c, style = Stroke(width = ring))
                            }
                        }
                    }
                    // dB scale (right edge)
                    Column(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp, top = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End
                    ) {
                        listOf("+12", "+6", "0", "−6", "−12").forEach { Text(it, fontSize = 9.sp, color = labelColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace) }
                    }
                    // Floating readout while touching
                    if (selected >= 0) {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp))
                                .background(cs.inverseSurface.copy(alpha = if (dragging) 0.92f else 0.75f)).padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("${fmtHz(AudioFx.centers[selected])}  ${fmtDb(gains[selected])}", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = cs.inverseOnSurface, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                FreqAxis(padStart = 7.dp)
                Spacer(Modifier.height(6.dp))

                Spacer(Modifier.height(12.dp))

                // ---- Spectrum analyzer (animated line) --------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (micGranted && spectrumOk) accent2 else cs.outline))
                    Spacer(Modifier.width(8.dp))
                    Text("Spectrum", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp))
                        .background(graphBg).border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !micGranted -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text("To draw the spectrum, Android asks for the microphone permission. Nothing is recorded.",
                                fontSize = 12.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Show spectrum") }
                        }
                        !spectrumOk -> Text("The spectrum isn't available on this phone.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        else -> {
                            val linePath = remember { Path() }
                            val areaPath = remember { Path() }
                            val peakPath = remember { Path() }
                            Canvas(Modifier.fillMaxSize()) {
                                tick // redraw every frame
                                GRID_FREQS.forEach { f ->
                                    val x = 7.dp.toPx() + (size.width - 14.dp.toPx()) * xOfFreq(f)
                                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
                                }
                                val top = size.height * 0.08f
                                val bottom = size.height
                                val xs = FloatArray(n) { 7.dp.toPx() + (size.width - 14.dp.toPx()) * it / (n - 1f) }
                                val ys = FloatArray(n) { bottom - level[it] * (bottom - top) }
                                smoothPath(xs, ys, linePath)
                                areaPath.reset(); areaPath.addPath(linePath)
                                areaPath.lineTo(xs.last(), bottom); areaPath.lineTo(xs.first(), bottom); areaPath.close()
                                drawSpectrum(areaPath, linePath, accent, accent2)
                                val ps = FloatArray(n) { bottom - peak[it] * (bottom - top) }
                                smoothPath(xs, ps, peakPath)
                                drawPath(peakPath, accent2.copy(alpha = 0.45f), style = Stroke(width = 0.6.dp.toPx()))
                            }
                        }
                    }
                }
                FreqAxis(padStart = 7.dp)
                Spacer(Modifier.height(18.dp))

                // ---- Every band, one row each ------------------------------------------------
                // Collapsed every time the equalizer opens; tap the header to show the rows
                var bandsOpen by remember { mutableStateOf(false) }
                val changed = gains.count { it != 0f }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { bandsOpen = !bandsOpen }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (bandsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (bandsOpen) "Collapse" else "Expand", tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bands", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        Text(if (changed == 0) "All at 0 dB" else "$changed of ${AudioFx.BANDS} changed", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = { AudioFx.setAll(FloatArray(AudioFx.BANDS)); selected = -1 }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset all")
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = bandsOpen,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
                        .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(vertical = 4.dp)
                ) {
                    for (i in 0 until AudioFx.BANDS) {
                        BandRow(
                            index = i, gain = gains[i], selected = i == selected, active = active,
                            onSelect = { selected = i },
                            onChange = { db -> selected = i; AudioFx.setGain(i, db); if (!enabled) AudioFx.setEnabled(true) },
                            onDone = { AudioFx.save() }
                        )
                        if (i < AudioFx.BANDS - 1) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = cs.outlineVariant.copy(alpha = 0.35f))
                    }
                }
                }
                Spacer(Modifier.height(10.dp))
                Text("Tap or drag on the curve to set bands · double-tap a band for 0 dB · fine-tune any band in the list. Boosts lower the overall level a little so nothing clips. Affects MobileDigger only.",
                    fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
            }
        }
    }
}

private fun DrawScope.drawSpectrum(area: Path, line: Path, a: Color, b: Color) {
    drawPath(area, Brush.verticalGradient(listOf(a.copy(alpha = 0.55f), b.copy(alpha = 0.18f), Color.Transparent)))
    drawPath(line, a.copy(alpha = 0.30f), style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(line, Brush.horizontalGradient(listOf(b, a, b)), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
}

/** Frequency labels on the same log axis as the graphs. */
@Composable
private fun FreqAxis(padStart: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp).padding(top = 4.dp)) {
        val pad = padStart
        val inner = maxWidth - pad * 2
        AXIS_LABELS.forEach { (f, label) ->
            val x = pad + inner * xOfFreq(f)
            Text(label, fontSize = 9.sp, color = cs.onSurfaceVariant.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace,
                modifier = Modifier.offset(x = (x - 8.dp).coerceIn(0.dp, maxWidth - 22.dp)))
        }
    }
}

/** One band: frequency · − · slider · + · value. Highlighted when it's the band picked on the curve. */
@Composable
private fun BandRow(
    index: Int, gain: Float, selected: Boolean, active: Boolean,
    onSelect: () -> Unit, onChange: (Float) -> Unit, onDone: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (active) cs.primary else cs.outline
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fmtHz(AudioFx.centers[index]), Modifier.width(58.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) cs.primary else cs.onSurface)
        IconButton(onClick = { onChange(gain - 0.5f); onDone() }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, "Cut ${fmtHz(AudioFx.centers[index])}", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        }
        Slider(
            value = gain,
            onValueChange = { v -> onChange(if (abs(v) < 0.4f) 0f else (v * 2).roundToInt() / 2f) },
            onValueChangeFinished = onDone,
            valueRange = AudioFx.MIN_DB..AudioFx.MAX_DB,
            modifier = Modifier.weight(1f).height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = tint, activeTrackColor = tint.copy(alpha = 0.85f),
                inactiveTrackColor = cs.onSurface.copy(alpha = 0.12f)
            )
        )
        IconButton(onClick = { onChange(gain + 0.5f); onDone() }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, "Boost ${fmtHz(AudioFx.centers[index])}", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        }
        Text(fmtDb(gain), Modifier.width(56.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = when { gain > 0f -> cs.primary; gain < 0f -> cs.tertiary; else -> cs.onSurfaceVariant })
        // Per-band reset to 0 dB (dimmed when already flat)
        IconButton(onClick = { onChange(0f); onDone() }, enabled = gain != 0f, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, "Reset ${fmtHz(AudioFx.centers[index])} to 0 dB", Modifier.size(17.dp),
                tint = if (gain != 0f) cs.primary else cs.onSurface.copy(alpha = 0.2f))
        }
    }
}
