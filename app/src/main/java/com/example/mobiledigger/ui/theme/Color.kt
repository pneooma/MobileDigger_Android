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

// ─────────────────────────────────────────────────────────────────────────
//  Extra themes (v12.20) — 24 more light/dark variations
// ─────────────────────────────────────────────────────────────────────────

val SunsetTheme = ThemeColors(
    name = "Sunset",
    primary = Color(0xFFFFB74D), secondary = Color(0xFFFF8A65), tertiary = Color(0xFFF48FB1), accent = Color(0xFFFFE0B2),
    background = Color(0xFFFFFBF7), surface = Color(0xFFFFF3E0), onBackground = Color(0xFFBF360C), onSurface = Color(0xFFE65100),
    onPrimary = Color(0xFF4A2800),
    darkPrimary = Color(0xFFFF9800), darkSecondary = Color(0xFFFF7043), darkTertiary = Color(0xFFF06292), darkAccent = Color(0xFFFFB74D),
    darkBackground = Color(0xFF2A1505), darkSurface = Color(0xFF3A2510), darkOnBackground = Color(0xFFFFE0B2), darkOnSurface = Color(0xFFFFCC80)
)

val ForestTheme = ThemeColors(
    name = "Forest",
    primary = Color(0xFF66BB6A), secondary = Color(0xFF81C784), tertiary = Color(0xFFA5D6A7), accent = Color(0xFFC8E6C9),
    background = Color(0xFFF6FBF6), surface = Color(0xFFECF5EC), onBackground = Color(0xFF1B5E20), onSurface = Color(0xFF2E7D32),
    darkPrimary = Color(0xFF388E3C), darkSecondary = Color(0xFF43A047), darkTertiary = Color(0xFF66BB6A), darkAccent = Color(0xFF81C784),
    darkBackground = Color(0xFF0B1A0B), darkSurface = Color(0xFF152415), darkOnBackground = Color(0xFFC8E6C9), darkOnSurface = Color(0xFFA5D6A7)
)

val RoseTheme = ThemeColors(
    name = "Rose",
    primary = Color(0xFFF06292), secondary = Color(0xFFF48FB1), tertiary = Color(0xFFF8BBD0), accent = Color(0xFFFCE4EC),
    background = Color(0xFFFFF8FA), surface = Color(0xFFFFF0F4), onBackground = Color(0xFF880E4F), onSurface = Color(0xFFAD1457),
    darkPrimary = Color(0xFFE91E63), darkSecondary = Color(0xFFEC407A), darkTertiary = Color(0xFFF06292), darkAccent = Color(0xFFF48FB1),
    darkBackground = Color(0xFF2A0A15), darkSurface = Color(0xFF3A1525), darkOnBackground = Color(0xFFFCE4EC), darkOnSurface = Color(0xFFF8BBD0)
)

val CrimsonTheme = ThemeColors(
    name = "Crimson",
    primary = Color(0xFFEF5350), secondary = Color(0xFFE57373), tertiary = Color(0xFFEF9A9A), accent = Color(0xFFFFCDD2),
    background = Color(0xFFFFF8F8), surface = Color(0xFFFFEFEF), onBackground = Color(0xFFB71C1C), onSurface = Color(0xFFC62828),
    darkPrimary = Color(0xFFD32F2F), darkSecondary = Color(0xFFE53935), darkTertiary = Color(0xFFEF5350), darkAccent = Color(0xFFE57373),
    darkBackground = Color(0xFF2A0B0B), darkSurface = Color(0xFF3A1616), darkOnBackground = Color(0xFFFFCDD2), darkOnSurface = Color(0xFFEF9A9A)
)

val AmberTheme = ThemeColors(
    name = "Amber",
    primary = Color(0xFFFFCA28), secondary = Color(0xFFFFD54F), tertiary = Color(0xFFFFE082), accent = Color(0xFFFFF8E1),
    background = Color(0xFFFFFEF7), surface = Color(0xFFFFF8E1), onBackground = Color(0xFFFF6F00), onSurface = Color(0xFFF57F17),
    onPrimary = Color(0xFF4A3500),
    darkPrimary = Color(0xFFFFB300), darkSecondary = Color(0xFFFFC107), darkTertiary = Color(0xFFFFCA28), darkAccent = Color(0xFFFFD54F),
    darkBackground = Color(0xFF2A2105), darkSurface = Color(0xFF3A3010), darkOnBackground = Color(0xFFFFF8E1), darkOnSurface = Color(0xFFFFE082),
    darkOnPrimary = Color(0xFF3A2A00)
)

val CoffeeTheme = ThemeColors(
    name = "Coffee",
    primary = Color(0xFFA1887F), secondary = Color(0xFFBCAAA4), tertiary = Color(0xFFD7CCC8), accent = Color(0xFFEFEBE9),
    background = Color(0xFFFBF9F8), surface = Color(0xFFF5F0EE), onBackground = Color(0xFF3E2723), onSurface = Color(0xFF4E342E),
    darkPrimary = Color(0xFF6D4C41), darkSecondary = Color(0xFF795548), darkTertiary = Color(0xFF8D6E63), darkAccent = Color(0xFFA1887F),
    darkBackground = Color(0xFF1A120F), darkSurface = Color(0xFF2A201C), darkOnBackground = Color(0xFFEFEBE9), darkOnSurface = Color(0xFFD7CCC8)
)

val SlateTheme = ThemeColors(
    name = "Slate",
    primary = Color(0xFF78909C), secondary = Color(0xFF90A4AE), tertiary = Color(0xFFB0BEC5), accent = Color(0xFFCFD8DC),
    background = Color(0xFFFAFBFC), surface = Color(0xFFF0F3F5), onBackground = Color(0xFF263238), onSurface = Color(0xFF37474F),
    darkPrimary = Color(0xFF455A64), darkSecondary = Color(0xFF546E7A), darkTertiary = Color(0xFF78909C), darkAccent = Color(0xFF90A4AE),
    darkBackground = Color(0xFF0E1518), darkSurface = Color(0xFF1C262B), darkOnBackground = Color(0xFFCFD8DC), darkOnSurface = Color(0xFFB0BEC5)
)

val IndigoTheme = ThemeColors(
    name = "Indigo",
    primary = Color(0xFF7986CB), secondary = Color(0xFF9FA8DA), tertiary = Color(0xFFC5CAE9), accent = Color(0xFFE8EAF6),
    background = Color(0xFFF9FAFE), surface = Color(0xFFF0F2FB), onBackground = Color(0xFF1A237E), onSurface = Color(0xFF283593),
    darkPrimary = Color(0xFF3949AB), darkSecondary = Color(0xFF3F51B5), darkTertiary = Color(0xFF5C6BC0), darkAccent = Color(0xFF7986CB),
    darkBackground = Color(0xFF0D1030), darkSurface = Color(0xFF1A1F45), darkOnBackground = Color(0xFFE8EAF6), darkOnSurface = Color(0xFFC5CAE9)
)

val TealTheme = ThemeColors(
    name = "Teal",
    primary = Color(0xFF26A69A), secondary = Color(0xFF4DB6AC), tertiary = Color(0xFF80CBC4), accent = Color(0xFFB2DFDB),
    background = Color(0xFFF6FBFB), surface = Color(0xFFECF6F5), onBackground = Color(0xFF004D40), onSurface = Color(0xFF00695C),
    darkPrimary = Color(0xFF00796B), darkSecondary = Color(0xFF00897B), darkTertiary = Color(0xFF26A69A), darkAccent = Color(0xFF4DB6AC),
    darkBackground = Color(0xFF081A18), darkSurface = Color(0xFF142826), darkOnBackground = Color(0xFFB2DFDB), darkOnSurface = Color(0xFF80CBC4)
)

val EmeraldTheme = ThemeColors(
    name = "Emerald",
    primary = Color(0xFF2ECC71), secondary = Color(0xFF58D68D), tertiary = Color(0xFF82E0AA), accent = Color(0xFFABEBC6),
    background = Color(0xFFF6FCF8), surface = Color(0xFFECF8F1), onBackground = Color(0xFF145A32), onSurface = Color(0xFF1E8449),
    darkPrimary = Color(0xFF27AE60), darkSecondary = Color(0xFF2ECC71), darkTertiary = Color(0xFF58D68D), darkAccent = Color(0xFF82E0AA),
    darkBackground = Color(0xFF071A0F), darkSurface = Color(0xFF12281B), darkOnBackground = Color(0xFFABEBC6), darkOnSurface = Color(0xFF82E0AA)
)

val SapphireTheme = ThemeColors(
    name = "Sapphire",
    primary = Color(0xFF2980B9), secondary = Color(0xFF3498DB), tertiary = Color(0xFF5DADE2), accent = Color(0xFFAED6F1),
    background = Color(0xFFF5FAFD), surface = Color(0xFFE9F3FA), onBackground = Color(0xFF0B3D62), onSurface = Color(0xFF154360),
    darkPrimary = Color(0xFF1F618D), darkSecondary = Color(0xFF2471A3), darkTertiary = Color(0xFF2980B9), darkAccent = Color(0xFF5DADE2),
    darkBackground = Color(0xFF06121C), darkSurface = Color(0xFF102233), darkOnBackground = Color(0xFFAED6F1), darkOnSurface = Color(0xFF85C1E9)
)

val RubyTheme = ThemeColors(
    name = "Ruby",
    primary = Color(0xFFE0245E), secondary = Color(0xFFE84C7C), tertiary = Color(0xFFF1739B), accent = Color(0xFFF9C5D5),
    background = Color(0xFFFFF7F9), surface = Color(0xFFFCECF1), onBackground = Color(0xFF7B0A2E), onSurface = Color(0xFFA01040),
    darkPrimary = Color(0xFFC01850), darkSecondary = Color(0xFFE0245E), darkTertiary = Color(0xFFE84C7C), darkAccent = Color(0xFFF1739B),
    darkBackground = Color(0xFF26060F), darkSurface = Color(0xFF3A0F1D), darkOnBackground = Color(0xFFF9C5D5), darkOnSurface = Color(0xFFF1739B)
)

val CoralTheme = ThemeColors(
    name = "Coral",
    primary = Color(0xFFFF7F6B), secondary = Color(0xFFFF9E8C), tertiary = Color(0xFFFFBCAE), accent = Color(0xFFFFD9D0),
    background = Color(0xFFFFFAF9), surface = Color(0xFFFFEFEC), onBackground = Color(0xFF9E2A1E), onSurface = Color(0xFFC0392B),
    onPrimary = Color(0xFF4A0F08),
    darkPrimary = Color(0xFFF4511E), darkSecondary = Color(0xFFFF6F50), darkTertiary = Color(0xFFFF7F6B), darkAccent = Color(0xFFFF9E8C),
    darkBackground = Color(0xFF2A0F0A), darkSurface = Color(0xFF3A1C16), darkOnBackground = Color(0xFFFFD9D0), darkOnSurface = Color(0xFFFFBCAE)
)

val LimeTheme = ThemeColors(
    name = "Lime",
    primary = Color(0xFFC0CA33), secondary = Color(0xFFD4E157), tertiary = Color(0xFFE6EE9C), accent = Color(0xFFF0F4C3),
    background = Color(0xFFFCFEF2), surface = Color(0xFFF6F9E6), onBackground = Color(0xFF827717), onSurface = Color(0xFF9E9D24),
    onPrimary = Color(0xFF33370A),
    darkPrimary = Color(0xFFAFB42B), darkSecondary = Color(0xFFC0CA33), darkTertiary = Color(0xFFD4E157), darkAccent = Color(0xFFE6EE9C),
    darkBackground = Color(0xFF1E2105), darkSurface = Color(0xFF2C3010), darkOnBackground = Color(0xFFF0F4C3), darkOnSurface = Color(0xFFE6EE9C),
    darkOnPrimary = Color(0xFF262A05)
)

val PlumTheme = ThemeColors(
    name = "Plum",
    primary = Color(0xFFAB47BC), secondary = Color(0xFFBA68C8), tertiary = Color(0xFFCE93D8), accent = Color(0xFFE1BEE7),
    background = Color(0xFFFDF8FE), surface = Color(0xFFF8EEFA), onBackground = Color(0xFF4A148C), onSurface = Color(0xFF6A1B9A),
    darkPrimary = Color(0xFF8E24AA), darkSecondary = Color(0xFF9C27B0), darkTertiary = Color(0xFFAB47BC), darkAccent = Color(0xFFCE93D8),
    darkBackground = Color(0xFF1F0A26), darkSurface = Color(0xFF2F153A), darkOnBackground = Color(0xFFE1BEE7), darkOnSurface = Color(0xFFCE93D8)
)

val SteelTheme = ThemeColors(
    name = "Steel",
    primary = Color(0xFF607D8B), secondary = Color(0xFF78909C), tertiary = Color(0xFF90A4AE), accent = Color(0xFFB0BEC5),
    background = Color(0xFFF9FAFB), surface = Color(0xFFEEF1F3), onBackground = Color(0xFF263238), onSurface = Color(0xFF455A64),
    darkPrimary = Color(0xFF455A64), darkSecondary = Color(0xFF546E7A), darkTertiary = Color(0xFF607D8B), darkAccent = Color(0xFF78909C),
    darkBackground = Color(0xFF101619), darkSurface = Color(0xFF1E272C), darkOnBackground = Color(0xFFECEFF1), darkOnSurface = Color(0xFFCFD8DC)
)

val SandTheme = ThemeColors(
    name = "Sand",
    primary = Color(0xFFD2B48C), secondary = Color(0xFFDEC4A0), tertiary = Color(0xFFE8D5B7), accent = Color(0xFFF5EAD7),
    background = Color(0xFFFFFDF9), surface = Color(0xFFFAF3E8), onBackground = Color(0xFF5D4037), onSurface = Color(0xFF6D4C41),
    onPrimary = Color(0xFF4A3520),
    darkPrimary = Color(0xFFB89968), darkSecondary = Color(0xFFC9A86F), darkTertiary = Color(0xFFD2B48C), darkAccent = Color(0xFFE8D5B7),
    darkBackground = Color(0xFF1F1A12), darkSurface = Color(0xFF2D261B), darkOnBackground = Color(0xFFF5EAD7), darkOnSurface = Color(0xFFE8D5B7),
    darkOnPrimary = Color(0xFF332615)
)

val AquaTheme = ThemeColors(
    name = "Aqua",
    primary = Color(0xFF00BCD4), secondary = Color(0xFF26C6DA), tertiary = Color(0xFF4DD0E1), accent = Color(0xFFB2EBF2),
    background = Color(0xFFF4FDFE), surface = Color(0xFFE6F9FB), onBackground = Color(0xFF006064), onSurface = Color(0xFF00838F),
    onPrimary = Color(0xFF003A40),
    darkPrimary = Color(0xFF0097A7), darkSecondary = Color(0xFF00ACC1), darkTertiary = Color(0xFF00BCD4), darkAccent = Color(0xFF4DD0E1),
    darkBackground = Color(0xFF06181A), darkSurface = Color(0xFF102A2D), darkOnBackground = Color(0xFFB2EBF2), darkOnSurface = Color(0xFF80DEEA)
)

val BerryTheme = ThemeColors(
    name = "Berry",
    primary = Color(0xFFAD1457), secondary = Color(0xFFC2185B), tertiary = Color(0xFFD81B60), accent = Color(0xFFF48FB1),
    background = Color(0xFFFFF7FA), surface = Color(0xFFFBEAF0), onBackground = Color(0xFF4A0E2A), onSurface = Color(0xFF880E4F),
    darkPrimary = Color(0xFF880E4F), darkSecondary = Color(0xFFAD1457), darkTertiary = Color(0xFFC2185B), darkAccent = Color(0xFFEC407A),
    darkBackground = Color(0xFF240612), darkSurface = Color(0xFF360F20), darkOnBackground = Color(0xFFF8BBD0), darkOnSurface = Color(0xFFF48FB1)
)

val CharcoalTheme = ThemeColors(
    name = "Charcoal",
    primary = Color(0xFF546E7A), secondary = Color(0xFF607D8B), tertiary = Color(0xFF78909C), accent = Color(0xFFB0BEC5),
    background = Color(0xFFF7F8F9), surface = Color(0xFFECEFF1), onBackground = Color(0xFF212121), onSurface = Color(0xFF37474F),
    darkPrimary = Color(0xFF37474F), darkSecondary = Color(0xFF455A64), darkTertiary = Color(0xFF546E7A), darkAccent = Color(0xFF78909C),
    darkBackground = Color(0xFF0A0A0A), darkSurface = Color(0xFF1A1A1A), darkOnBackground = Color(0xFFECEFF1), darkOnSurface = Color(0xFFB0BEC5)
)

val SakuraTheme = ThemeColors(
    name = "Sakura",
    primary = Color(0xFFF8BBD0), secondary = Color(0xFFFBC4D5), tertiary = Color(0xFFFDD9E3), accent = Color(0xFFFFEEF3),
    background = Color(0xFFFFFAFC), surface = Color(0xFFFFF0F5), onBackground = Color(0xFF880E4F), onSurface = Color(0xFFAD1457),
    onPrimary = Color(0xFF5A0A2A),
    darkPrimary = Color(0xFFEC407A), darkSecondary = Color(0xFFF06292), darkTertiary = Color(0xFFF48FB1), darkAccent = Color(0xFFF8BBD0),
    darkBackground = Color(0xFF2A0F1A), darkSurface = Color(0xFF3A1A28), darkOnBackground = Color(0xFFFFEEF3), darkOnSurface = Color(0xFFFDD9E3)
)

val VolcanoTheme = ThemeColors(
    name = "Volcano",
    primary = Color(0xFFFF5722), secondary = Color(0xFFFF7043), tertiary = Color(0xFFFF8A65), accent = Color(0xFFFFAB91),
    background = Color(0xFFFFF8F6), surface = Color(0xFFFFEDE7), onBackground = Color(0xFFBF360C), onSurface = Color(0xFFD84315),
    onPrimary = Color(0xFF4A1000),
    darkPrimary = Color(0xFFE64A19), darkSecondary = Color(0xFFF4511E), darkTertiary = Color(0xFFFF5722), darkAccent = Color(0xFFFF8A65),
    darkBackground = Color(0xFF2A0D06), darkSurface = Color(0xFF3A1810), darkOnBackground = Color(0xFFFFAB91), darkOnSurface = Color(0xFFFF8A65)
)

val ArcticTheme = ThemeColors(
    name = "Arctic",
    primary = Color(0xFF90CAF9), secondary = Color(0xFFB3E5FC), tertiary = Color(0xFFE1F5FE), accent = Color(0xFFF0F9FF),
    background = Color(0xFFFCFEFF), surface = Color(0xFFF0F8FF), onBackground = Color(0xFF0D47A1), onSurface = Color(0xFF1565C0),
    onPrimary = Color(0xFF0A2A4A),
    darkPrimary = Color(0xFF42A5F5), darkSecondary = Color(0xFF64B5F6), darkTertiary = Color(0xFF90CAF9), darkAccent = Color(0xFFB3E5FC),
    darkBackground = Color(0xFF0A1420), darkSurface = Color(0xFF162536), darkOnBackground = Color(0xFFE1F5FE), darkOnSurface = Color(0xFFB3E5FC)
)

val MustardTheme = ThemeColors(
    name = "Mustard",
    primary = Color(0xFFE6A817), secondary = Color(0xFFEDB94E), tertiary = Color(0xFFF2CC7E), accent = Color(0xFFF9E4B7),
    background = Color(0xFFFFFDF6), surface = Color(0xFFFAF2DF), onBackground = Color(0xFF7A5901), onSurface = Color(0xFF96710B),
    onPrimary = Color(0xFF3A2A00),
    darkPrimary = Color(0xFFC28F0E), darkSecondary = Color(0xFFE6A817), darkTertiary = Color(0xFFEDB94E), darkAccent = Color(0xFFF2CC7E),
    darkBackground = Color(0xFF211A06), darkSurface = Color(0xFF312712), darkOnBackground = Color(0xFFF9E4B7), darkOnSurface = Color(0xFFF2CC7E)
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
    SkyTheme,
    // v12.20 additions
    SunsetTheme,
    ForestTheme,
    RoseTheme,
    CrimsonTheme,
    AmberTheme,
    CoffeeTheme,
    SlateTheme,
    IndigoTheme,
    TealTheme,
    EmeraldTheme,
    SapphireTheme,
    RubyTheme,
    CoralTheme,
    LimeTheme,
    PlumTheme,
    SteelTheme,
    SandTheme,
    AquaTheme,
    BerryTheme,
    CharcoalTheme,
    SakuraTheme,
    VolcanoTheme,
    ArcticTheme,
    MustardTheme
)