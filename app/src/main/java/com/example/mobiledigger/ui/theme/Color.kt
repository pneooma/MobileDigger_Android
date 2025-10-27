package com.example.mobiledigger.ui.theme

import androidx.compose.ui.graphics.Color

// Default MobileDigger Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val GreenAccent = Color(0xFF10B981)
val GroovyBlue = Color(0xFF2196F3)
val LikeGreen = Color(0xFF4CAF50)
val DislikeRed = Color(0xFFE91E63)
val YesButton = Color(0xFF10B981)
val NoButton = Color(0xFFEF4444)
val OrangeAccent = Color(0xFFFF8C00)

// Theme Color Schemes
data class ThemeColors(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color = Color.White,
    val onSecondary: Color = Color.White,
    val onTertiary: Color = Color.White,
    val onBackground: Color = Color.Black,
    val onSurface: Color = Color.Black,
    // Dark mode variants
    val darkPrimary: Color = primary,
    val darkSecondary: Color = secondary,
    val darkTertiary: Color = tertiary,
    val darkAccent: Color = accent,
    val darkBackground: Color = Color(0xFF121212),
    val darkSurface: Color = Color(0xFF1E1E1E),
    val darkOnPrimary: Color = Color.White,
    val darkOnSecondary: Color = Color.White,
    val darkOnTertiary: Color = Color.White,
    val darkOnBackground: Color = Color.White,
    val darkOnSurface: Color = Color.White
)

// Ocean Theme - Pastel blues and teals (Android 16 style)
val OceanTheme = ThemeColors(
    name = "Ocean",
    primary = Color(0xFF81C7F4), // Soft blue
    secondary = Color(0xFF9ED5F0), // Light blue
    tertiary = Color(0xFFB8E0F2), // Very light blue
    accent = Color(0xFFD4F1F4), // Pastel cyan
    background = Color(0xFFF8FDFF), // Very light background
    surface = Color(0xFFF0F9FF), // Light surface
    onBackground = Color(0xFF1A365D), // Dark blue text
    onSurface = Color(0xFF2D4A6B), // Medium blue text
    // Dark mode variants
    darkPrimary = Color(0xFF4A90E2), // Deeper blue
    darkSecondary = Color(0xFF5BA0F2), // Medium blue
    darkTertiary = Color(0xFF6BB0FF), // Light blue
    darkAccent = Color(0xFF7BC0FF), // Bright blue
    darkBackground = Color(0xFF0A1A2E), // Dark blue background
    darkSurface = Color(0xFF1A2A3E), // Dark blue surface
    darkOnBackground = Color(0xFFE0F2FF), // Light blue text
    darkOnSurface = Color(0xFFB0D2FF) // Medium light blue text
)

// Midnight Theme - Pastel purples and deep blues (Android 16 style)
val MidnightTheme = ThemeColors(
    name = "Midnight",
    primary = Color(0xFFB19CD9), // Soft purple
    secondary = Color(0xFFC4A8E0), // Light purple
    tertiary = Color(0xFFD7C4E7), // Very light purple
    accent = Color(0xFFE8D5F0), // Pastel purple
    background = Color(0xFFFDFBFF), // Very light background
    surface = Color(0xFFF5F0FF), // Light surface
    onBackground = Color(0xFF2D1B69), // Dark purple text
    onSurface = Color(0xFF4A3A7A), // Medium purple text
    // Dark mode variants
    darkPrimary = Color(0xFF8A4FCB), // Deeper purple
    darkSecondary = Color(0xFF9B5FDB), // Medium purple
    darkTertiary = Color(0xFFAC6FEB), // Light purple
    darkAccent = Color(0xFFBD7FFB), // Bright purple
    darkBackground = Color(0xFF1A0D2E), // Dark purple background
    darkSurface = Color(0xFF2A1D3E), // Dark purple surface
    darkOnBackground = Color(0xFFE8D5FF), // Light purple text
    darkOnSurface = Color(0xFFB8A5FF) // Medium light purple text
)

// Monochrome Theme - Pastel grays (Android 16 style)
val MonochromeTheme = ThemeColors(
    name = "Monochrome",
    primary = Color(0xFFA8A8A8), // Soft gray
    secondary = Color(0xFFB8B8B8), // Light gray
    tertiary = Color(0xFFC8C8C8), // Very light gray
    accent = Color(0xFFD8D8D8), // Pastel gray
    background = Color(0xFFFAFAFA), // Very light background
    surface = Color(0xFFF5F5F5), // Light surface
    onBackground = Color(0xFF2D2D2D), // Dark gray text
    onSurface = Color(0xFF4A4A4A), // Medium gray text
    // Dark mode variants
    darkPrimary = Color(0xFF757575), // Medium gray
    darkSecondary = Color(0xFF858585), // Light gray
    darkTertiary = Color(0xFF959595), // Lighter gray
    darkAccent = Color(0xFFA5A5A5), // Light gray
    darkBackground = Color(0xFF121212), // Dark background
    darkSurface = Color(0xFF1E1E1E), // Dark surface
    darkOnBackground = Color(0xFFE0E0E0), // Light gray text
    darkOnSurface = Color(0xFFB0B0B0) // Medium light gray text
)

// High Contrast Theme - Enhanced contrast (Android 16 style)
val HighContrastTheme = ThemeColors(
    name = "HighContrast",
    primary = Color(0xFF000000), // Pure black
    secondary = Color(0xFF333333), // Dark gray
    tertiary = Color(0xFF666666), // Medium gray
    accent = Color(0xFF00FF00), // Bright green
    background = Color(0xFFFFFFFF), // Pure white
    surface = Color(0xFFF0F0F0), // Light gray surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    // Dark mode variants
    darkPrimary = Color(0xFFFFFFFF), // Pure white
    darkSecondary = Color(0xFFCCCCCC), // Light gray
    darkTertiary = Color(0xFF999999), // Medium gray
    darkAccent = Color(0xFF00FF00), // Bright green
    darkBackground = Color(0xFF000000), // Pure black
    darkSurface = Color(0xFF1A1A1A), // Dark surface
    darkOnPrimary = Color.Black,
    darkOnSecondary = Color.Black,
    darkOnTertiary = Color.Black,
    darkOnBackground = Color.White,
    darkOnSurface = Color.White
)

// Default MobileDigger Theme - Pastel green (Android 16 style)
val MobileDiggerTheme = ThemeColors(
    name = "MobileDigger",
    primary = Color(0xFF81C784), // Soft green
    secondary = Color(0xFFA5D6A7), // Light green
    tertiary = Color(0xFFC8E6C9), // Very light green
    accent = Color(0xFFE8F5E8), // Pastel green
    background = Color(0xFFF8FFF8), // Very light background
    surface = Color(0xFFF0F8F0), // Light surface
    onBackground = Color(0xFF1B5E20), // Dark green text
    onSurface = Color(0xFF2E7D32), // Medium green text
    // Dark mode variants
    darkPrimary = Color(0xFF4CAF50), // Deeper green
    darkSecondary = Color(0xFF66BB6A), // Medium green
    darkTertiary = Color(0xFF81C784), // Light green
    darkAccent = Color(0xFF9CCC65), // Bright green
    darkBackground = Color(0xFF0A1A0A), // Dark green background
    darkSurface = Color(0xFF1A2A1A), // Dark green surface
    darkOnBackground = Color(0xFFE8F5E8), // Light green text
    darkOnSurface = Color(0xFFB8D6B8) // Medium light green text
)

// Lavender Theme - Pastel purples (Android 16 style)
val LavenderTheme = ThemeColors(
    name = "Lavender",
    primary = Color(0xFFC5A3DC), // Soft lavender
    secondary = Color(0xFFD4B5E8), // Light lavender
    tertiary = Color(0xFFE3C7F4), // Very light lavender
    accent = Color(0xFFF2E5FF), // Pastel lavender
    background = Color(0xFFFDFAFF), // Very light background
    surface = Color(0xFFF8F0FF), // Light surface
    onBackground = Color(0xFF4A148C), // Dark purple text
    onSurface = Color(0xFF6A1B9A), // Medium purple text
    // Dark mode variants
    darkPrimary = Color(0xFF9C27B0), // Deeper purple
    darkSecondary = Color(0xFFAB47BC), // Medium purple
    darkTertiary = Color(0xFFBA68C8), // Light purple
    darkAccent = Color(0xFFCE93D8), // Bright purple
    darkBackground = Color(0xFF1A0D2A), // Dark purple background
    darkSurface = Color(0xFF2A1D3A), // Dark purple surface
    darkOnBackground = Color(0xFFF2E5FF), // Light purple text
    darkOnSurface = Color(0xFFD2B5FF) // Medium light purple text
)

// Peach Theme - Pastel oranges and pinks (Android 16 style)
val PeachTheme = ThemeColors(
    name = "Peach",
    primary = Color(0xFFFFB3BA), // Soft peach
    secondary = Color(0xFFFFC1C8), // Light peach
    tertiary = Color(0xFFFFCFD6), // Very light peach
    accent = Color(0xFFFFDDE4), // Pastel peach
    background = Color(0xFFFFFAFA), // Very light background
    surface = Color(0xFFFFF0F0), // Light surface
    onBackground = Color(0xFF8D1B3D), // Dark pink text
    onSurface = Color(0xFFAD1457), // Medium pink text
    // Dark mode variants
    darkPrimary = Color(0xFFE91E63), // Deeper pink
    darkSecondary = Color(0xFFF06292), // Medium pink
    darkTertiary = Color(0xFFF48FB1), // Light pink
    darkAccent = Color(0xFFF8BBD9), // Bright pink
    darkBackground = Color(0xFF2A0D1A), // Dark pink background
    darkSurface = Color(0xFF3A1D2A), // Dark pink surface
    darkOnBackground = Color(0xFFFFDDE4), // Light pink text
    darkOnSurface = Color(0xFFFFB3BA) // Medium light pink text
)

// Mint Theme - Pastel greens and teals (Android 16 style)
val MintTheme = ThemeColors(
    name = "Mint",
    primary = Color(0xFFA7F3D0), // Soft mint
    secondary = Color(0xFFB8F5E0), // Light mint
    tertiary = Color(0xFFC9F7F0), // Very light mint
    accent = Color(0xFFDAFDF0), // Pastel mint
    background = Color(0xFFFAFFFE), // Very light background
    surface = Color(0xFFF0FFF8), // Light surface
    onBackground = Color(0xFF064E3B), // Dark teal text
    onSurface = Color(0xFF065F46), // Medium teal text
    // Dark mode variants
    darkPrimary = Color(0xFF26A69A), // Deeper teal
    darkSecondary = Color(0xFF4DB6AC), // Medium teal
    darkTertiary = Color(0xFF80CBC4), // Light teal
    darkAccent = Color(0xFFB2DFDB), // Bright teal
    darkBackground = Color(0xFF0A1A15), // Dark teal background
    darkSurface = Color(0xFF1A2A25), // Dark teal surface
    darkOnBackground = Color(0xFFDAFDF0), // Light teal text
    darkOnSurface = Color(0xFFBAF5E0) // Medium light teal text
)

// Sky Theme - Light cyan and sky blues (Android 16 style)
val SkyTheme = ThemeColors(
    name = "Sky",
    primary = Color(0xFF87CEEB), // Sky blue
    secondary = Color(0xFFB0E0E6), // Powder blue
    tertiary = Color(0xFFE0F6FF), // Light cyan
    accent = Color(0xFFF0F8FF), // Alice blue
    background = Color(0xFFF8FEFF), // Very light cyan background
    surface = Color(0xFFF0FCFF), // Light cyan surface
    onBackground = Color(0xFF006064), // Dark cyan text
    onSurface = Color(0xFF00838F), // Medium cyan text
    // Dark mode variants
    darkPrimary = Color(0xFF00BCD4), // Cyan
    darkSecondary = Color(0xFF26C6DA), // Light cyan
    darkTertiary = Color(0xFF4DD0E1), // Bright cyan
    darkAccent = Color(0xFF80DEEA), // Very light cyan
    darkBackground = Color(0xFF0A1A1C), // Dark cyan background
    darkSurface = Color(0xFF1A2A2C), // Dark cyan surface
    darkOnBackground = Color(0xFFE0F6FF), // Light cyan text
    darkOnSurface = Color(0xFFB0E6F0) // Medium light cyan text
)

// All available themes
val AvailableThemes = listOf(
    MobileDiggerTheme,
    OceanTheme,
    MidnightTheme,
    MonochromeTheme,
    HighContrastTheme,
    LavenderTheme,
    PeachTheme,
    MintTheme,
    SkyTheme
)