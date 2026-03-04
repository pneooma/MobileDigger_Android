package com.example.mobiledigger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

enum class WaveformAnimationType {
    EXPAND, SLIDE_UP, SLIDE_DOWN, FADE, SCALE, RANDOM, NONE;

    companion object {
        private val animatable = listOf(EXPAND, SLIDE_UP, SLIDE_DOWN, FADE, SCALE)
        fun randomAnimatable(): WaveformAnimationType = animatable.random()
    }
}

data class VisualSettings(
    // Waveform Settings
    val waveformHeight: Float = 144f,
    val miniWaveformHeight: Float = 48f,
    val rowWaveformHeight: Float = 120f,
    // Animation Settings
    val enableAnimations: Boolean = true,
    val animationSpeed: Float = 1.0f,
    val waveformAnimationType: WaveformAnimationType = WaveformAnimationType.EXPAND,
    // Behavior Settings
    val autoPlayFirstAfterSelect: Boolean = false,
    val autoScanLastSourceOnStart: Boolean = false,
    // Show text labels under toolbar icons
    val showToolbarLabels: Boolean = false,
    // Font family setting
    val fontFamily: String = "Default",
    // Waveform color (ARGB int, 0 = use theme default)
    val waveformColor: Int = 0,
    // Mini-player visibility
    val enableMiniPlayer: Boolean = false
)

class VisualSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("visual_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WAVEFORM_HEIGHT = "waveform_height"
        private const val KEY_MINI_WAVEFORM_HEIGHT = "mini_waveform_height"
        private const val KEY_ROW_WAVEFORM_HEIGHT = "row_waveform_height"
        private const val KEY_ENABLE_ANIMATIONS = "enable_animations"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_WAVEFORM_ANIMATION_TYPE = "waveform_animation_type"
        private const val KEY_AUTO_PLAY_FIRST = "auto_play_first_after_select"
        private const val KEY_AUTO_SCAN_LAST = "auto_scan_last_source_on_start"
        private const val KEY_SHOW_TOOLBAR_LABELS = "show_toolbar_labels"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_WAVEFORM_COLOR = "waveform_color"
        private const val KEY_ENABLE_MINI_PLAYER = "enable_mini_player"
    }

    private val _settings = mutableStateOf(loadSettings())
    val settings: State<VisualSettings> = _settings

    private fun loadSettings(): VisualSettings {
        return VisualSettings(
            waveformHeight = prefs.getFloat(KEY_WAVEFORM_HEIGHT, 96f),
            miniWaveformHeight = prefs.getFloat(KEY_MINI_WAVEFORM_HEIGHT, 72f),
            rowWaveformHeight = prefs.getFloat(KEY_ROW_WAVEFORM_HEIGHT, 80f),
            enableAnimations = prefs.getBoolean(KEY_ENABLE_ANIMATIONS, true),
            animationSpeed = prefs.getFloat(KEY_ANIMATION_SPEED, 1.0f),
            waveformAnimationType = WaveformAnimationType.valueOf(
                prefs.getString(KEY_WAVEFORM_ANIMATION_TYPE, WaveformAnimationType.EXPAND.name) ?: WaveformAnimationType.EXPAND.name
            ),
            autoPlayFirstAfterSelect = prefs.getBoolean(KEY_AUTO_PLAY_FIRST, false),
            autoScanLastSourceOnStart = prefs.getBoolean(KEY_AUTO_SCAN_LAST, false),
            showToolbarLabels = prefs.getBoolean(KEY_SHOW_TOOLBAR_LABELS, false),
            fontFamily = prefs.getString(KEY_FONT_FAMILY, "Default") ?: "Default",
            waveformColor = prefs.getInt(KEY_WAVEFORM_COLOR, 0),
            enableMiniPlayer = prefs.getBoolean(KEY_ENABLE_MINI_PLAYER, false)
        )
    }

    fun updateSettings(newSettings: VisualSettings) {
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    private fun saveSettings(settings: VisualSettings) {
        prefs.edit().apply {
            putFloat(KEY_WAVEFORM_HEIGHT, settings.waveformHeight)
            putFloat(KEY_MINI_WAVEFORM_HEIGHT, settings.miniWaveformHeight)
            putFloat(KEY_ROW_WAVEFORM_HEIGHT, settings.rowWaveformHeight)
            putBoolean(KEY_ENABLE_ANIMATIONS, settings.enableAnimations)
            putFloat(KEY_ANIMATION_SPEED, settings.animationSpeed)
            putString(KEY_WAVEFORM_ANIMATION_TYPE, settings.waveformAnimationType.name)
            putBoolean(KEY_AUTO_PLAY_FIRST, settings.autoPlayFirstAfterSelect)
            putBoolean(KEY_AUTO_SCAN_LAST, settings.autoScanLastSourceOnStart)
            putBoolean(KEY_SHOW_TOOLBAR_LABELS, settings.showToolbarLabels)
            putString(KEY_FONT_FAMILY, settings.fontFamily)
            putInt(KEY_WAVEFORM_COLOR, settings.waveformColor)
            putBoolean(KEY_ENABLE_MINI_PLAYER, settings.enableMiniPlayer)
        }.apply()
    }

    fun resetToDefaults() {
        updateSettings(VisualSettings())
    }
}
