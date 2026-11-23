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
    // Per-animation durations in ms (Animation Speed multiplies all of them)
    val animSwipeOutMs: Float = 200f,
    val animRemoveMs: Float = 300f,
    val animReflowMs: Float = 350f,
    val animSettleMs: Float = 300f,
    val animArriveMs: Float = 350f,
    val animPanelMs: Float = 500f,
    val animRowExpandMs: Float = 400f,
    val animWaveformMs: Float = 300f,
    val animPlayPauseMs: Float = 180f,

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
        private const val KEY_ANIMSWIPEOUTMS = "animSwipeOutMs"
        private const val KEY_ANIMREMOVEMS = "animRemoveMs"
        private const val KEY_ANIMREFLOWMS = "animReflowMs"
        private const val KEY_ANIMSETTLEMS = "animSettleMs"
        private const val KEY_ANIMARRIVEMS = "animArriveMs"
        private const val KEY_ANIMPANELMS = "animPanelMs"
        private const val KEY_ANIMROWEXPANDMS = "animRowExpandMs"
        private const val KEY_ANIMWAVEFORMMS = "animWaveformMs"
        private const val KEY_ANIMPLAYPAUSEMS = "animPlayPauseMs"

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
            enableMiniPlayer = prefs.getBoolean(KEY_ENABLE_MINI_PLAYER, false),
            animSwipeOutMs = prefs.getFloat(KEY_ANIMSWIPEOUTMS, 200f),
            animRemoveMs = prefs.getFloat(KEY_ANIMREMOVEMS, 300f),
            animReflowMs = prefs.getFloat(KEY_ANIMREFLOWMS, 350f),
            animSettleMs = prefs.getFloat(KEY_ANIMSETTLEMS, 300f),
            animArriveMs = prefs.getFloat(KEY_ANIMARRIVEMS, 350f),
            animPanelMs = prefs.getFloat(KEY_ANIMPANELMS, 500f),
            animRowExpandMs = prefs.getFloat(KEY_ANIMROWEXPANDMS, 400f),
            animWaveformMs = prefs.getFloat(KEY_ANIMWAVEFORMMS, 300f),
            animPlayPauseMs = prefs.getFloat(KEY_ANIMPLAYPAUSEMS, 180f)
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
            putFloat(KEY_ANIMSWIPEOUTMS, settings.animSwipeOutMs)
            putFloat(KEY_ANIMREMOVEMS, settings.animRemoveMs)
            putFloat(KEY_ANIMREFLOWMS, settings.animReflowMs)
            putFloat(KEY_ANIMSETTLEMS, settings.animSettleMs)
            putFloat(KEY_ANIMARRIVEMS, settings.animArriveMs)
            putFloat(KEY_ANIMPANELMS, settings.animPanelMs)
            putFloat(KEY_ANIMROWEXPANDMS, settings.animRowExpandMs)
            putFloat(KEY_ANIMWAVEFORMMS, settings.animWaveformMs)
            putFloat(KEY_ANIMPLAYPAUSEMS, settings.animPlayPauseMs)

        }.apply()
    }

}
