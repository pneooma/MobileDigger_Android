package com.example.mobiledigger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp

data class VisualSettings(
    // Waveform Settings
    val waveformHeight: Float = 80f,
    val miniWaveformHeight: Float = 60f,
    // Animation Settings
    val enableAnimations: Boolean = true,
    val animationSpeed: Float = 1.0f, // 0.5x to 2.0x speed
    val enableHapticFeedback: Boolean = true
)

class VisualSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("visual_settings", Context.MODE_PRIVATE)
    
    companion object {
        // Waveform keys
        private const val KEY_WAVEFORM_HEIGHT = "waveform_height"
        private const val KEY_MINI_WAVEFORM_HEIGHT = "mini_waveform_height"
        
        // Animation keys
        private const val KEY_ENABLE_ANIMATIONS = "enable_animations"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_ENABLE_HAPTIC_FEEDBACK = "enable_haptic_feedback"
    }
    
    private val _settings = mutableStateOf(loadSettings())
    val settings: State<VisualSettings> = _settings
    
    private fun loadSettings(): VisualSettings {
        return VisualSettings(
            // Waveform
            waveformHeight = prefs.getFloat(KEY_WAVEFORM_HEIGHT, 80f),
            miniWaveformHeight = prefs.getFloat(KEY_MINI_WAVEFORM_HEIGHT, 60f),
            
            // Animation
            enableAnimations = prefs.getBoolean(KEY_ENABLE_ANIMATIONS, true),
            animationSpeed = prefs.getFloat(KEY_ANIMATION_SPEED, 1.0f),
            enableHapticFeedback = prefs.getBoolean(KEY_ENABLE_HAPTIC_FEEDBACK, true)
        )
    }
    
    fun updateSettings(newSettings: VisualSettings) {
        _settings.value = newSettings
        saveSettings(newSettings)
    }
    
    private fun saveSettings(settings: VisualSettings) {
        prefs.edit().apply {
            // Waveform
            putFloat(KEY_WAVEFORM_HEIGHT, settings.waveformHeight)
            putFloat(KEY_MINI_WAVEFORM_HEIGHT, settings.miniWaveformHeight)
            
            // Animation
            putBoolean(KEY_ENABLE_ANIMATIONS, settings.enableAnimations)
            putFloat(KEY_ANIMATION_SPEED, settings.animationSpeed)
            putBoolean(KEY_ENABLE_HAPTIC_FEEDBACK, settings.enableHapticFeedback)
        }.apply()
    }
    
    // Convenience methods for individual updates
    
    
    fun resetToDefaults() {
        updateSettings(VisualSettings())
    }
}
