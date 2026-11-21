package com.example.mobiledigger.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * The theme's accent, or its lighter/darker container variant when the accent itself is too
 * faint against the surface (e.g. Steel's dark slate on a dark background).
 */
@Composable
@ReadOnlyComposable
fun readableAccent(): androidx.compose.ui.graphics.Color {
    val cs = MaterialTheme.colorScheme
    val a = cs.primary.luminance(); val b = cs.surface.luminance()
    val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    return if (ratio >= 3f) cs.primary else cs.onPrimaryContainer
}

/** "Loading your music…" card: themed, with an equaliser animation and changing hints. */
@Composable
fun LoadingCard() {
    val cs = MaterialTheme.colorScheme
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }

    // Entrance: fade + slight scale up
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }

    val t = rememberInfiniteTransition(label = "loading")
    val phase by t.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "eq")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(0.10f, 0.22f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")

    val hints = listOf("Scanning folders", "Reading track names", "Looking for archives", "Almost there")
    val hint = hints[(elapsed / 3).coerceAtMost(hints.size - 1)]

    Box(
        Modifier
            .graphicsLayer {
                alpha = appear.value
                val s = 0.92f + 0.08f * appear.value
                scaleX = s; scaleY = s
            }
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = glow), cs.surfaceContainerHigh)))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(28.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val ring = readableAccent()
                val ringTrack = ring.copy(alpha = 0.15f)
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 4.dp.toPx()
                    drawArc(ringTrack, 0f, 360f, false, style = Stroke(sw))
                    drawArc(
                        Brush.sweepGradient(listOf(ring.copy(alpha = 0f), ring)),
                        spin, 260f, false, style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                // Equaliser: five bars, staggered sine waves
                val barA = readableAccent()
                val barB = cs.tertiary
                Canvas(Modifier.size(44.dp, 36.dp)) {
                    val n = 5
                    val gap = size.width / (n * 2 - 1)
                    for (i in 0 until n) {
                        val h = (0.35f + 0.65f * (0.5f + 0.5f * sin(phase + i * 1.1f))) * size.height
                        val x = i * gap * 2
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(barB, barA), startY = size.height - h, endY = size.height),
                            topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                            size = androidx.compose.ui.geometry.Size(gap, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap / 2, gap / 2)
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Loading your music…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Spacer(Modifier.height(4.dp))
            AnimatedContent(targetState = hint, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "hint") { h ->
                Text(
                    if (elapsed < 1) h else "$h · ${elapsed}s",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = readableAccent(), trackColor = readableAccent().copy(alpha = 0.18f), strokeCap = StrokeCap.Round
            )
        }
    }
}
