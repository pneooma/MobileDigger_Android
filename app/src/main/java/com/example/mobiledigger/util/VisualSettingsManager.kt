package com.example.mobiledigger.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VisualSettingsManager {
    private val _enableAnimations = MutableStateFlow(true)
    val enableAnimations: StateFlow<Boolean> = _enableAnimations.asStateFlow()
    
    private val _animationSpeed = MutableStateFlow(1.0f)
    val animationSpeed: StateFlow<Float> = _animationSpeed.asStateFlow()
    
    private val _waveformHeight = MutableStateFlow(96) // Increased by 20% (80 * 1.2)
    val waveformHeight: StateFlow<Int> = _waveformHeight.asStateFlow()
    
    private val _miniWaveformHeight = MutableStateFlow(72) // Increased by 20% (60 * 1.2)
    val miniWaveformHeight: StateFlow<Int> = _miniWaveformHeight.asStateFlow()
    
    fun setEnableAnimations(enabled: Boolean) {
        _enableAnimations.value = enabled
    }
    
    fun setAnimationSpeed(speed: Float) {
        _animationSpeed.value = speed
    }
    
    fun setWaveformHeight(height: Int) {
        _waveformHeight.value = height
    }
    
    fun setMiniWaveformHeight(height: Int) {
        _miniWaveformHeight.value = height
    }
}

