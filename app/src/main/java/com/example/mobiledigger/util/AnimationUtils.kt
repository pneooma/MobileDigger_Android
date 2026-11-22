package com.example.mobiledigger.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.view.Display
import android.view.WindowManager

/**
 * Utility class for optimizing animations based on display refresh rate
 */
object AnimationUtils {
    
    /**
     * Get the display refresh rate in Hz
     */
    fun getDisplayRefreshRate(context: android.content.Context): Float {
        return try {
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            display.refreshRate
        } catch (e: Exception) {
            60f // Default to 60Hz if detection fails
        }
    }
    
    /**
     * Animation duration — independent of refresh rate (kept for existing callers)
     */
    fun getOptimizedDuration(baseDurationMs: Int, refreshRate: Float): Int {
        // Animations take the same TIME on every screen; a faster screen just draws more frames.
        // (Shortening them on 90/120 Hz made the app feel different per phone, not smoother.)
        return baseDurationMs
    }

    /**
     * Get optimized spring animation spec for high refresh rate displays
     */
    fun getOptimizedSpring(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessLow,
        refreshRate: Float = 60f
    ): AnimationSpec<Float> {
        return spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }
    
    /**
     * Get optimized tween animation spec for high refresh rate displays
     */
    fun getOptimizedTween(
        durationMs: Int,
        refreshRate: Float = 60f
    ): FiniteAnimationSpec<Float> {
        val optimizedDuration = getOptimizedDuration(durationMs, refreshRate)
        return tween(durationMillis = optimizedDuration)
    }
}

/**
 * Composable function to get optimized animation specs based on current display
 */
@Composable
fun rememberOptimizedAnimationSpecs(): AnimationSpecs {
    val context = LocalContext.current
    val refreshRate = remember { AnimationUtils.getDisplayRefreshRate(context) }
    
    return remember(refreshRate) {
        AnimationSpecs(
            refreshRate = refreshRate,
            // Standard animations
            fastTween = AnimationUtils.getOptimizedTween(150, refreshRate),
            mediumTween = AnimationUtils.getOptimizedTween(250, refreshRate),
            slowTween = AnimationUtils.getOptimizedTween(600, refreshRate),
            // Spring animations
            bouncySpring = AnimationUtils.getOptimizedSpring(
                Spring.DampingRatioMediumBouncy,
                Spring.StiffnessLow,
                refreshRate
            ),
            smoothSpring = AnimationUtils.getOptimizedSpring(
                Spring.DampingRatioNoBouncy,
                Spring.StiffnessMedium,
                refreshRate
            ),
            // Song name animation (longer for readability)
            songNameTween = AnimationUtils.getOptimizedTween(800, refreshRate),
            // Button press animations
            buttonPressTween = AnimationUtils.getOptimizedTween(150, refreshRate)
        )
    }
}

data class AnimationSpecs(
    val refreshRate: Float,
    val fastTween: FiniteAnimationSpec<Float>,
    val mediumTween: FiniteAnimationSpec<Float>,
    val slowTween: FiniteAnimationSpec<Float>,
    val bouncySpring: AnimationSpec<Float>,
    val smoothSpring: AnimationSpec<Float>,
    val songNameTween: FiniteAnimationSpec<Float>,
    val buttonPressTween: FiniteAnimationSpec<Float>
)
