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
    val onSurface: Color = Color.Black
)

// Ocean Theme - Deep blues and teals
val OceanTheme = ThemeColors(
    name = "Ocean",
    primary = Color(0xFF0066CC),
    secondary = Color(0xFF00BCD4),
    tertiary = Color(0xFF4FC3F7),
    accent = Color(0xFF00E5FF),
    background = Color(0xFF001122),
    surface = Color(0xFF002244),
    onBackground = Color(0xFFE0F2F1),
    onSurface = Color(0xFFE0F2F1)
)

// Sunset Theme - Warm oranges and reds
val SunsetTheme = ThemeColors(
    name = "Sunset",
    primary = Color(0xFFFF5722),
    secondary = Color(0xFFFF9800),
    tertiary = Color(0xFFFFC107),
    accent = Color(0xFFFF6B35),
    background = Color(0xFF2C1810),
    surface = Color(0xFF3D2317),
    onBackground = Color(0xFFFFF3E0),
    onSurface = Color(0xFFFFF3E0)
)

// Forest Theme - Natural greens
val ForestTheme = ThemeColors(
    name = "Forest",
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF4CAF50),
    tertiary = Color(0xFF8BC34A),
    accent = Color(0xFF66BB6A),
    background = Color(0xFF0D1B0D),
    surface = Color(0xFF1B2E1B),
    onBackground = Color(0xFFE8F5E8),
    onSurface = Color(0xFFE8F5E8)
)

// Purple Theme - Rich purples and violets
val PurpleTheme = ThemeColors(
    name = "Purple",
    primary = Color(0xFF7B1FA2),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFFBA68C8),
    accent = Color(0xFFCE93D8),
    background = Color(0xFF1A0D1A),
    surface = Color(0xFF2D1B2D),
    onBackground = Color(0xFFF3E5F5),
    onSurface = Color(0xFFF3E5F5)
)

// Midnight Theme - Dark blues and purples
val MidnightTheme = ThemeColors(
    name = "Midnight",
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF7986CB),
    accent = Color(0xFF9FA8DA),
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE8EAF6),
    onSurface = Color(0xFFE8EAF6)
)

// Coral Theme - Warm pinks and corals
val CoralTheme = ThemeColors(
    name = "Coral",
    primary = Color(0xFFE91E63),
    secondary = Color(0xFFF06292),
    tertiary = Color(0xFFF48FB1),
    accent = Color(0xFFF8BBD9),
    background = Color(0xFF1A0D0F),
    surface = Color(0xFF2D1B1F),
    onBackground = Color(0xFFFCE4EC),
    onSurface = Color(0xFFFCE4EC)
)

// Default MobileDigger Theme
val MobileDiggerTheme = ThemeColors(
    name = "MobileDigger",
    primary = GreenAccent,
    secondary = GroovyBlue,
    tertiary = Pink80,
    accent = OrangeAccent,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6)
)

// New Themes (5 more)
val EarthyTheme = ThemeColors(
    name = "Earthy",
    primary = Color(0xFF6D4C41), // Brown
    secondary = Color(0xFF8D6E63), // Light Brown
    tertiary = Color(0xFFA1887F), // Lighter Brown
    accent = Color(0xFF795548), // Medium Brown
    background = Color(0xFF2E241F),
    surface = Color(0xFF3E322E),
    onBackground = Color(0xFFFBE9E7),
    onSurface = Color(0xFFFBE9E7)
)

val VibrantTheme = ThemeColors(
    name = "Vibrant",
    primary = Color(0xFFE040FB), // Fuchsia
    secondary = Color(0xFF7C4DFF), // Deep Purple
    tertiary = Color(0xFF448AFF), // Blue Accent
    accent = Color(0xFF00E5FF), // Cyan
    background = Color(0xFF100010),
    surface = Color(0xFF200020),
    onBackground = Color(0xFFF3E5F5),
    onSurface = Color(0xFFF3E5F5)
)

val MonochromeTheme = ThemeColors(
    name = "Monochrome",
    primary = Color(0xFF424242), // Dark Grey
    secondary = Color(0xFF616161), // Medium Grey
    tertiary = Color(0xFF9E9E9E), // Light Grey
    accent = Color(0xFFBDBDBD), // Lighter Grey
    background = Color(0xFF000000),
    surface = Color(0xFF1F1F1F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val RetroTheme = ThemeColors(
    name = "Retro",
    primary = Color(0xFFF44336), // Red
    secondary = Color(0xFFE91E63), // Pink
    tertiary = Color(0xFF9C27B0), // Purple
    accent = Color(0xFF673AB7), // Deep Purple
    background = Color(0xFF1F0000),
    surface = Color(0xFF3F0000),
    onBackground = Color(0xFFFFFDE7),
    onSurface = Color(0xFFFFFDE7)
)

val HighContrastTheme = ThemeColors(
    name = "HighContrast",
    primary = Color(0xFF000000), // Black
    secondary = Color(0xFFFFFFFF), // White
    tertiary = Color(0xFF00FF00), // Bright Green
    accent = Color(0xFFFFFF00), // Yellow
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFE0E0E0),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

// All available themes
val AvailableThemes = listOf(
    MobileDiggerTheme,
    OceanTheme,
    SunsetTheme,
    ForestTheme,
    PurpleTheme,
    MidnightTheme,
    CoralTheme,
    EarthyTheme,
    VibrantTheme,
    MonochromeTheme,
    RetroTheme,
    HighContrastTheme
)