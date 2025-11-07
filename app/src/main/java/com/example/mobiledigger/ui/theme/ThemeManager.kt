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
        private const val KEY_SELECTED_THEME = "selected_theme"
    }
    
    private val _isDarkMode = mutableStateOf(prefs.getBoolean(KEY_DARK_MODE, true))
    val isDarkMode: State<Boolean> = _isDarkMode
    
    private val _useDynamicColor = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val useDynamicColor: State<Boolean> = _useDynamicColor
    
    private val _selectedTheme = mutableStateOf(
        AvailableThemes.find { it.name == prefs.getString(KEY_SELECTED_THEME, "Mint") } 
            ?: MintTheme
    )
    val selectedTheme: State<ThemeColors> = _selectedTheme
    
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
    
    fun setSelectedTheme(theme: ThemeColors) {
        _selectedTheme.value = theme
        prefs.edit().putString(KEY_SELECTED_THEME, theme.name).apply()
    }
    
    fun getSelectedTheme(): ThemeColors {
        return _selectedTheme.value
    }
}
