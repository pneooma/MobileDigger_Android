package com.example.mobiledigger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp

data class VisualSettings(
    // Color Settings
    val primaryColor: Color = Color(0xFF10B981),
    val secondaryColor: Color = Color(0xFF2196F3),
    val accentColor: Color = Color(0xFFEFB8C8),
    val likeColor: Color = Color(0xFF4CAF50),
    val dislikeColor: Color = Color(0xFFE91E63),
    val backgroundOpacity: Float = 1.0f,
    val surfaceOpacity: Float = 1.0f,
    
    // Typography Settings
    val titleSize: Float = 22f,
    val bodySize: Float = 16f,
    val captionSize: Float = 12f,
    val buttonSize: Float = 14f,
    val scaleFactor: Float = 1.0f, // Global text scale
    
    // Layout Settings
    val cardCornerRadius: Float = 12f,
    val buttonCornerRadius: Float = 8f,
    val paddingSmall: Float = 8f,
    val paddingMedium: Float = 16f,
    val paddingLarge: Float = 24f,
    
    // Waveform Settings
    val waveformHeight: Float = 80f,
    val miniWaveformHeight: Float = 60f,
    val waveformBarWidth: Float = 1f,
    val waveformGap: Float = 0f,
    val waveformOpacity: Float = 1.0f,
    val waveformColor: Color = Color(0xFF10B981),
    val waveformPlayedColor: Color = Color(0xFF4CAF50),
    
    // Player Settings
    val controlButtonSize: Float = 40f,
    
    // Animation Settings
    val enableAnimations: Boolean = true,
    val animationSpeed: Float = 1.0f, // 0.5x to 2.0x speed
    val enableHapticFeedback: Boolean = true
)

class VisualSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("visual_settings", Context.MODE_PRIVATE)
    
    companion object {
        // Color keys
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_SECONDARY_COLOR = "secondary_color"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_LIKE_COLOR = "like_color"
        private const val KEY_DISLIKE_COLOR = "dislike_color"
        private const val KEY_BACKGROUND_OPACITY = "background_opacity"
        private const val KEY_SURFACE_OPACITY = "surface_opacity"
        
        // Typography keys
        private const val KEY_TITLE_SIZE = "title_size"
        private const val KEY_BODY_SIZE = "body_size"
        private const val KEY_CAPTION_SIZE = "caption_size"
        private const val KEY_BUTTON_SIZE = "button_size"
        private const val KEY_SCALE_FACTOR = "scale_factor"
        
        // Layout keys
        private const val KEY_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val KEY_BUTTON_CORNER_RADIUS = "button_corner_radius"
        private const val KEY_PADDING_SMALL = "padding_small"
        private const val KEY_PADDING_MEDIUM = "padding_medium"
        private const val KEY_PADDING_LARGE = "padding_large"
        
        // Waveform keys
        private const val KEY_WAVEFORM_HEIGHT = "waveform_height"
        private const val KEY_MINI_WAVEFORM_HEIGHT = "mini_waveform_height"
        private const val KEY_WAVEFORM_BAR_WIDTH = "waveform_bar_width"
        private const val KEY_WAVEFORM_GAP = "waveform_gap"
        private const val KEY_WAVEFORM_OPACITY = "waveform_opacity"
        private const val KEY_WAVEFORM_COLOR = "waveform_color"
        private const val KEY_WAVEFORM_PLAYED_COLOR = "waveform_played_color"
        
        // Player keys
        private const val KEY_CONTROL_BUTTON_SIZE = "control_button_size"
        
        // Animation keys
        private const val KEY_ENABLE_ANIMATIONS = "enable_animations"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_ENABLE_HAPTIC_FEEDBACK = "enable_haptic_feedback"
    }
    
    private val _settings = mutableStateOf(loadSettings())
    val settings: State<VisualSettings> = _settings
    
    private fun loadSettings(): VisualSettings {
        return VisualSettings(
            // Colors
            primaryColor = Color(prefs.getLong(KEY_PRIMARY_COLOR, 0xFF10B981)),
            secondaryColor = Color(prefs.getLong(KEY_SECONDARY_COLOR, 0xFF2196F3)),
            accentColor = Color(prefs.getLong(KEY_ACCENT_COLOR, 0xFFEFB8C8)),
            likeColor = Color(prefs.getLong(KEY_LIKE_COLOR, 0xFF4CAF50)),
            dislikeColor = Color(prefs.getLong(KEY_DISLIKE_COLOR, 0xFFE91E63)),
            backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, 1.0f),
            surfaceOpacity = prefs.getFloat(KEY_SURFACE_OPACITY, 1.0f),
            
            // Typography
            titleSize = prefs.getFloat(KEY_TITLE_SIZE, 22f),
            bodySize = prefs.getFloat(KEY_BODY_SIZE, 16f),
            captionSize = prefs.getFloat(KEY_CAPTION_SIZE, 12f),
            buttonSize = prefs.getFloat(KEY_BUTTON_SIZE, 14f),
            scaleFactor = prefs.getFloat(KEY_SCALE_FACTOR, 1.0f),
            
            // Layout
            cardCornerRadius = prefs.getFloat(KEY_CARD_CORNER_RADIUS, 12f),
            buttonCornerRadius = prefs.getFloat(KEY_BUTTON_CORNER_RADIUS, 8f),
            paddingSmall = prefs.getFloat(KEY_PADDING_SMALL, 8f),
            paddingMedium = prefs.getFloat(KEY_PADDING_MEDIUM, 16f),
            paddingLarge = prefs.getFloat(KEY_PADDING_LARGE, 24f),
            
            // Waveform
            waveformHeight = prefs.getFloat(KEY_WAVEFORM_HEIGHT, 80f),
            miniWaveformHeight = prefs.getFloat(KEY_MINI_WAVEFORM_HEIGHT, 60f),
            waveformBarWidth = prefs.getFloat(KEY_WAVEFORM_BAR_WIDTH, 1f),
            waveformGap = prefs.getFloat(KEY_WAVEFORM_GAP, 0f),
            waveformOpacity = prefs.getFloat(KEY_WAVEFORM_OPACITY, 1.0f),
            waveformColor = Color(prefs.getLong(KEY_WAVEFORM_COLOR, 0xFF10B981)),
            waveformPlayedColor = Color(prefs.getLong(KEY_WAVEFORM_PLAYED_COLOR, 0xFF4CAF50)),
            
            // Player
            controlButtonSize = prefs.getFloat(KEY_CONTROL_BUTTON_SIZE, 40f),
            
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
            // Colors
            putLong(KEY_PRIMARY_COLOR, settings.primaryColor.toArgb().toLong())
            putLong(KEY_SECONDARY_COLOR, settings.secondaryColor.toArgb().toLong())
            putLong(KEY_ACCENT_COLOR, settings.accentColor.toArgb().toLong())
            putLong(KEY_LIKE_COLOR, settings.likeColor.toArgb().toLong())
            putLong(KEY_DISLIKE_COLOR, settings.dislikeColor.toArgb().toLong())
            putFloat(KEY_BACKGROUND_OPACITY, settings.backgroundOpacity)
            putFloat(KEY_SURFACE_OPACITY, settings.surfaceOpacity)
            
            // Typography
            putFloat(KEY_TITLE_SIZE, settings.titleSize)
            putFloat(KEY_BODY_SIZE, settings.bodySize)
            putFloat(KEY_CAPTION_SIZE, settings.captionSize)
            putFloat(KEY_BUTTON_SIZE, settings.buttonSize)
            putFloat(KEY_SCALE_FACTOR, settings.scaleFactor)
            
            // Layout
            putFloat(KEY_CARD_CORNER_RADIUS, settings.cardCornerRadius)
            putFloat(KEY_BUTTON_CORNER_RADIUS, settings.buttonCornerRadius)
            putFloat(KEY_PADDING_SMALL, settings.paddingSmall)
            putFloat(KEY_PADDING_MEDIUM, settings.paddingMedium)
            putFloat(KEY_PADDING_LARGE, settings.paddingLarge)
            
            // Waveform
            putFloat(KEY_WAVEFORM_HEIGHT, settings.waveformHeight)
            putFloat(KEY_MINI_WAVEFORM_HEIGHT, settings.miniWaveformHeight)
            putFloat(KEY_WAVEFORM_BAR_WIDTH, settings.waveformBarWidth)
            putFloat(KEY_WAVEFORM_GAP, settings.waveformGap)
            putFloat(KEY_WAVEFORM_OPACITY, settings.waveformOpacity)
            putLong(KEY_WAVEFORM_COLOR, settings.waveformColor.toArgb().toLong())
            putLong(KEY_WAVEFORM_PLAYED_COLOR, settings.waveformPlayedColor.toArgb().toLong())
            
            // Player
            putFloat(KEY_CONTROL_BUTTON_SIZE, settings.controlButtonSize)
            
            // Animation
            putBoolean(KEY_ENABLE_ANIMATIONS, settings.enableAnimations)
            putFloat(KEY_ANIMATION_SPEED, settings.animationSpeed)
            putBoolean(KEY_ENABLE_HAPTIC_FEEDBACK, settings.enableHapticFeedback)
        }.apply()
    }
    
    // Convenience methods for individual updates
    fun updatePrimaryColor(color: Color) {
        val newSettings = _settings.value.copy(primaryColor = color)
        updateSettings(newSettings)
    }
    
    fun updateScaleFactor(scale: Float) {
        val newSettings = _settings.value.copy(scaleFactor = scale)
        updateSettings(newSettings)
    }
    
    
    fun resetToDefaults() {
        updateSettings(VisualSettings())
    }
}
