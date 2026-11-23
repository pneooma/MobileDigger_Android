package com.example.mobiledigger.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

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






// ─────────────────────────────────────────────────────────────────────────
//  Extra themes (v12.20) — 24 more light/dark variations
// ─────────────────────────────────────────────────────────────────────────








val IndigoTheme = ThemeColors(
    name = "Indigo",
    primary = Color(0xFF7986CB), secondary = Color(0xFF9FA8DA), tertiary = Color(0xFFC5CAE9), accent = Color(0xFFE8EAF6),
    background = Color(0xFFF9FAFE), surface = Color(0xFFF0F2FB), onBackground = Color(0xFF1A237E), onSurface = Color(0xFF283593),
    darkPrimary = Color(0xFF3949AB), darkSecondary = Color(0xFF3F51B5), darkTertiary = Color(0xFF5C6BC0), darkAccent = Color(0xFF7986CB),
    darkBackground = Color(0xFF0D1030), darkSurface = Color(0xFF1A1F45), darkOnBackground = Color(0xFFE8EAF6), darkOnSurface = Color(0xFFC5CAE9)
)








val SteelTheme = ThemeColors(
    name = "Steel",
    primary = Color(0xFF607D8B), secondary = Color(0xFF78909C), tertiary = Color(0xFF90A4AE), accent = Color(0xFFB0BEC5),
    background = Color(0xFFF9FAFB), surface = Color(0xFFEEF1F3), onBackground = Color(0xFF263238), onSurface = Color(0xFF455A64),
    darkPrimary = Color(0xFF455A64), darkSecondary = Color(0xFF546E7A), darkTertiary = Color(0xFF607D8B), darkAccent = Color(0xFF78909C),
    darkBackground = Color(0xFF101619), darkSurface = Color(0xFF1E272C), darkOnBackground = Color(0xFFECEFF1), darkOnSurface = Color(0xFFCFD8DC)
)










// ─────────────────────────────────────────────────────────────────────────
//  Curated palettes (v13.029). Light mode uses deeper accents (white text),
//  dark mode brighter ones (dark text); backgrounds are neutrals tinted with the
//  theme's hue. Every pair checked for contrast (text ≥ 4.5:1, accent on bg ≥ 3:1).
//  Steel, Indigo and HighContrast are kept exactly as they were.
// ─────────────────────────────────────────────────────────────────────────

private fun onFor(c: Color): Color {
    val dark = Color(0xFF111418)
    fun cr(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }
    return if (cr(dark, c) >= cr(Color.White, c)) dark else Color.White
}

private fun curated(
    name: String, lp: Long, ls: Long, lt: Long, dp: Long, ds: Long, dt: Long
): ThemeColors {
    val l = Color(lp); val lsC = Color(ls); val ltC = Color(lt)
    val d = Color(dp); val dsC = Color(ds); val dtC = Color(dt)
    val lightText = lerp(Color(0xFF15171A), l, 0.18f)
    val darkText = lerp(Color(0xFFF1F3F5), d, 0.08f)
    return ThemeColors(
        name = name,
        primary = l, secondary = lsC, tertiary = ltC, accent = lerp(Color.White, l, 0.3f),
        background = lerp(Color.White, l, 0.025f), surface = lerp(Color.White, l, 0.06f),
        onPrimary = onFor(l), onSecondary = onFor(lsC), onTertiary = onFor(ltC),
        onBackground = lightText, onSurface = lightText,
        darkPrimary = d, darkSecondary = dsC, darkTertiary = dtC, darkAccent = d,
        darkBackground = lerp(Color(0xFF0C0E11), d, 0.07f), darkSurface = lerp(Color(0xFF16191E), d, 0.09f),
        darkOnPrimary = onFor(d), darkOnSecondary = onFor(dsC), darkOnTertiary = onFor(dtC),
        darkOnBackground = darkText, darkOnSurface = darkText
    )
}

val MobileDiggerTheme = curated("MobileDigger", 0xFF047857, 0xFF2563EB, 0xFFDB2777, 0xFF34D399, 0xFF60A5FA, 0xFFF472B6)
val OceanTheme = curated("Ocean", 0xFF0277BD, 0xFF00796B, 0xFF5C6BC0, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF9FA8DA)
val MidnightTheme = curated("Midnight", 0xFF5B4BC4, 0xFF2563EB, 0xFFA21CAF, 0xFFA78BFA, 0xFF93C5FD, 0xFFE879F9)
val MonochromeTheme = curated("Monochrome", 0xFF3F3F46, 0xFF71717A, 0xFF52525B, 0xFFD4D4D8, 0xFFA1A1AA, 0xFFE4E4E7)
val LavenderTheme = curated("Lavender", 0xFF7C3AED, 0xFFDB2777, 0xFF0E7490, 0xFFC4B5FD, 0xFFF9A8D4, 0xFF67E8F9)
val PeachTheme = curated("Peach", 0xFFC2410C, 0xFFDB2777, 0xFFA16207, 0xFFFDBA8C, 0xFFF9A8D4, 0xFFFDE047)
val MintTheme = curated("Mint", 0xFF0F766E, 0xFF047857, 0xFF0369A1, 0xFF5EEAD4, 0xFF6EE7B7, 0xFF7DD3FC)
val SkyTheme = curated("Sky", 0xFF0369A1, 0xFF4F46E5, 0xFF0F766E, 0xFF7DD3FC, 0xFFA5B4FC, 0xFF5EEAD4)
val SunsetTheme = curated("Sunset", 0xFFC2410C, 0xFFBE185D, 0xFF7C3AED, 0xFFFB923C, 0xFFF472B6, 0xFFC4B5FD)
val ForestTheme = curated("Forest", 0xFF15803D, 0xFF4D7C0F, 0xFFA16207, 0xFF4ADE80, 0xFFA3E635, 0xFFFCD34D)
val RoseTheme = curated("Rose", 0xFFE11D48, 0xFFA21CAF, 0xFFC2410C, 0xFFFB7185, 0xFFF0ABFC, 0xFFFDBA74)
val CrimsonTheme = curated("Crimson", 0xFFB91C1C, 0xFF9F1239, 0xFFB45309, 0xFFF87171, 0xFFFDA4AF, 0xFFFCD34D)
val AmberTheme = curated("Amber", 0xFFB45309, 0xFFC2410C, 0xFF0F766E, 0xFFFBBF24, 0xFFFB923C, 0xFF5EEAD4)
val CoffeeTheme = curated("Coffee", 0xFF8B5E3C, 0xFFA16207, 0xFF57534E, 0xFFD6A77A, 0xFFE9C46A, 0xFFD6D3D1)
val SlateTheme = curated("Slate", 0xFF475569, 0xFF0369A1, 0xFF64748B, 0xFF94A3B8, 0xFF7DD3FC, 0xFFCBD5E1)
val TealTheme = curated("Teal", 0xFF0F766E, 0xFF0369A1, 0xFFB45309, 0xFF2DD4BF, 0xFF38BDF8, 0xFFFBBF24)
val EmeraldTheme = curated("Emerald", 0xFF047857, 0xFF0F766E, 0xFF4D7C0F, 0xFF34D399, 0xFF5EEAD4, 0xFFBEF264)
val SapphireTheme = curated("Sapphire", 0xFF1D4ED8, 0xFF0E7490, 0xFF6D28D9, 0xFF60A5FA, 0xFF22D3EE, 0xFFC4B5FD)
val RubyTheme = curated("Ruby", 0xFFBE123C, 0xFF9D174D, 0xFFB45309, 0xFFFB7185, 0xFFF9A8D4, 0xFFFCD34D)
val CoralTheme = curated("Coral", 0xFFC2410C, 0xFFBE185D, 0xFF0F766E, 0xFFFF8A73, 0xFFF9A8D4, 0xFF5EEAD4)
val LimeTheme = curated("Lime", 0xFF4D7C0F, 0xFF15803D, 0xFFA16207, 0xFFA3E635, 0xFF86EFAC, 0xFFFDE047)
val PlumTheme = curated("Plum", 0xFF86198F, 0xFF6D28D9, 0xFFBE185D, 0xFFE879F9, 0xFFC4B5FD, 0xFFF9A8D4)
val SandTheme = curated("Sand", 0xFFA16207, 0xFF92400E, 0xFF57534E, 0xFFE7C68F, 0xFFFCD34D, 0xFFD6D3D1)
val AquaTheme = curated("Aqua", 0xFF0E7490, 0xFF0F766E, 0xFF1D4ED8, 0xFF22D3EE, 0xFF5EEAD4, 0xFF93C5FD)
val BerryTheme = curated("Berry", 0xFFA21CAF, 0xFFBE185D, 0xFF6D28D9, 0xFFF0ABFC, 0xFFF9A8D4, 0xFFC4B5FD)
val CharcoalTheme = curated("Charcoal", 0xFF404040, 0xFF737373, 0xFF047857, 0xFFA3A3A3, 0xFFD4D4D4, 0xFF6EE7B7)
val SakuraTheme = curated("Sakura", 0xFFDB2777, 0xFFE11D48, 0xFF7C3AED, 0xFFF9A8D4, 0xFFFDA4AF, 0xFFDDD6FE)
val VolcanoTheme = curated("Volcano", 0xFFC2410C, 0xFFB91C1C, 0xFF374151, 0xFFFB923C, 0xFFF87171, 0xFF9CA3AF)
val ArcticTheme = curated("Arctic", 0xFF0369A1, 0xFF0E7490, 0xFF4F46E5, 0xFF93C5FD, 0xFFA5F3FC, 0xFFC7D2FE)
val MustardTheme = curated("Mustard", 0xFFA16207, 0xFF854D0E, 0xFF4D7C0F, 0xFFFACC15, 0xFFFDE68A, 0xFFBEF264)

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