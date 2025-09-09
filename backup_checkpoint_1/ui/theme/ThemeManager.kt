package com.example.mobiledigger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
    
    private val _isDarkMode = mutableStateOf(prefs.getBoolean(KEY_DARK_MODE, false))
    val isDarkMode: State<Boolean> = _isDarkMode
    
    private val _useDynamicColor = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val useDynamicColor: State<Boolean> = _useDynamicColor
    
    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean(KEY_DARK_MODE, newValue).apply()
    }
    
    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
    
    fun toggleDynamicColor() {
        val newValue = !_useDynamicColor.value
        _useDynamicColor.value = newValue
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, newValue).apply()
    }
    
    fun setDynamicColor(enabled: Boolean) {
        _useDynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
