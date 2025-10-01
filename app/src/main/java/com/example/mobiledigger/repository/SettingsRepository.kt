package com.example.mobiledigger.repository

import android.app.Application
import com.example.mobiledigger.file.PreferencesManager
import com.example.mobiledigger.ui.theme.ThemeManager
import com.example.mobiledigger.ui.theme.VisualSettingsManager

class SettingsRepository(application: Application) {
    val preferences = PreferencesManager(application)
    val themeManager = ThemeManager(application)
    val visualSettingsManager = VisualSettingsManager(application)
}
