package com.example.mobiledigger.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.ui.theme.VisualSettings

/**
 * One place for the app's motion, so lists, swipes and players feel the same everywhere.
 * Every duration comes from Settings → Animations (one slider each, in ms) and is divided by
 * the overall Animation Speed. Real time, independent of the screen's refresh rate.
 */
object Motion {
    @Volatile private var s: VisualSettings = VisualSettings()

    /** Called by the main screen whenever the settings change. */
    fun update(settings: VisualSettings) { s = settings }

    /** Effective duration: slider value ÷ overall speed; 0 when animations are off. */
    fun ms(base: Float): Int =
        if (!s.enableAnimations) 0 else (base / s.animationSpeed.coerceIn(0.05f, 3f)).toInt().coerceIn(0, 20_000)

    /** Spring stiffness that settles in about [durMs] for damping [zeta] (settle ≈ 4 / (ζ·ω)). */
    private fun stiffnessFor(durMs: Int, zeta: Float): Float {
        if (durMs <= 0) return 10_000f
        val omega = 4f / (zeta * durMs / 1000f)
        return (omega * omega).coerceIn(1f, 10_000f)
    }

    /** Rows gliding into their new place after a sort / removal. */
    val placement: FiniteAnimationSpec<IntOffset>
        get() = ms(s.animReflowMs).let { if (it == 0) snap() else tween(it, easing = FastOutSlowInEasing) }
    val rowFadeIn: FiniteAnimationSpec<Float>
        get() = ms(s.animReflowMs * 0.6f).let { if (it == 0) snap() else tween(it, easing = LinearOutSlowInEasing) }
    val rowFadeOut: FiniteAnimationSpec<Float>
        get() = ms(s.animRemoveMs * 0.6f).let { if (it == 0) snap() else tween(it, easing = FastOutLinearInEasing) }

    /** Fade-out of a row sorted with the Like / Reject buttons, before it's removed. */
    fun removeMs(): Int = ms(s.animRemoveMs)

    /** An unfinished swipe settling back: one small overshoot. */
    val settle: AnimationSpec<Float>
        get() = ms(s.animSettleMs).let { if (it == 0) snap() else spring(dampingRatio = 0.72f, stiffness = stiffnessFor(it, 0.72f)) }

    /** The next track's card sliding in after a swipe on the main player. */
    val arrive: AnimationSpec<Float>
        get() = ms(s.animArriveMs).let { if (it == 0) snap() else spring(dampingRatio = 0.85f, stiffness = stiffnessFor(it, 0.85f)) }

    /** A swiped (liked / rejected) card or row leaving the screen: accelerates out. */
    fun exit(): AnimationSpec<Float> = ms(s.animSwipeOutMs).let { if (it == 0) snap() else tween(it, easing = FastOutLinearInEasing) }
    fun exitMs(): Long = ms(s.animSwipeOutMs).toLong()

    fun panelMs(): Int = ms(s.animPanelMs)            // main player show / hide
    fun rowExpandMs(): Int = ms(s.animRowExpandMs)    // playing row growing to show its waveform
    fun waveformMs(): Int = ms(s.animWaveformMs)      // waveform show / hide
    fun playPauseMs(): Int = ms(s.animPlayPauseMs)    // play ⇄ pause icon

    // Swipe distances in dp (they were raw pixels, so they shrank on sharper screens)
    val swipeIndicator = 19.dp
    val swipeTrigger = 38.dp
    val swipeMax = 76.dp
    val mainSwipeTrigger = 57.dp
}

/** Play ⇄ pause with a quick scale-and-fade instead of an instant swap. */
@Composable
fun PlayPauseIcon(isPlaying: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            val d = Motion.playPauseMs()
            (scaleIn(tween(d, easing = LinearOutSlowInEasing), initialScale = 0.6f) + fadeIn(tween(d * 3 / 4))) togetherWith
                (scaleOut(tween(d * 2 / 3, easing = FastOutLinearInEasing), targetScale = 0.6f) + fadeOut(tween(d / 2)))
        },
        label = "playPause"
    ) { playing ->
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tint,
            modifier = modifier
        )
    }
}

/** Shows [fraction] (0..1) of the content's height, clipped — lets a panel shrink away smoothly. */
fun Modifier.collapseHeight(fraction: Float): Modifier =
    if (fraction >= 1f) this else this.clipToBounds().layout { m, c ->
        val p = m.measure(c)
        val h = (p.height * fraction.coerceIn(0f, 1f)).toInt()
        layout(p.width, h) { p.place(0, 0) }
    }
