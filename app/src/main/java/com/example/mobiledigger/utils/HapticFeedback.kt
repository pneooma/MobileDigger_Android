package com.example.mobiledigger.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object HapticFeedback {
    
    fun triggerHaptic(context: Context, type: HapticType = HapticType.Light) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                HapticType.Light -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.Medium -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticType.Heavy -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticType.Success -> VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50), -1)
                HapticType.Error -> VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
                HapticType.Selection -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(type.duration)
        }
    }
    
    @Composable
    fun rememberHapticFeedback(): (HapticType) -> Unit {
        val context = LocalContext.current
        return remember(context) { { type -> triggerHaptic(context, type) } }
    }
}

enum class HapticType(val duration: Long) {
    Light(10),
    Medium(25),
    Heavy(50),
    Success(100),
    Error(200),
    Selection(5)
}
