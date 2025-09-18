package com.example.mobiledigger.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mobiledigger.ui.theme.HapticFeedbackType

object HapticFeedback {
    
    fun triggerHaptic(context: Context, type: HapticFeedbackType, intensity: Float = 1.0f) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                HapticFeedbackType.NONE -> null
                HapticFeedbackType.NORMAL -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticFeedbackType.LIGHT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticFeedbackType.MEDIUM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticFeedbackType.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticFeedbackType.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            effect?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    // Use the amplitude parameter if available
                    val scaledIntensity = (intensity * 255).toInt().coerceIn(1, 255)
                    // We can't set amplitude for predefined effects directly, so we'll re-create for custom patterns
                    // For predefined effects, we'll just trigger them as is or use a custom pattern if intensity is not 1.0
                    if (intensity != 1.0f && type != HapticFeedbackType.NONE) {
                        val pattern = longArrayOf(0, 20) // Short vibration
                        val amplitudes = intArrayOf(0, scaledIntensity)
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                    } else if (type != HapticFeedbackType.NONE) {
                        vibrator.vibrate(it)
                    }
                } else if (type != HapticFeedbackType.NONE) {
                    vibrator.vibrate(it)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            // For older APIs, intensity is hard to control per-effect. Just vibrate if not NONE.
            if (type != HapticFeedbackType.NONE) {
                vibrator.vibrate(50L) // Generic short vibration for older APIs
            }
        }
    }
    
    @Composable
    fun rememberHapticFeedback(settings: com.example.mobiledigger.ui.theme.VisualSettings): () -> Unit {
        val context = LocalContext.current
        return remember(context, settings.enableHapticFeedback, settings.hapticFeedbackType, settings.hapticFeedbackIntensity) {
            { 
                if (settings.enableHapticFeedback) {
                    triggerHaptic(context, settings.hapticFeedbackType, settings.hapticFeedbackIntensity)
                }
            }
        }
    }
}
