package com.example.mobiledigger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
// Note: AdaptiveTheme is not available in current Compose version
// import androidx.compose.material3.adaptive.AdaptiveTheme
// import androidx.compose.material3.adaptive.AdaptiveThemeColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

// Material 3 Expressive - Enhanced color schemes with vibrant colors
private val DarkColorScheme = darkColorScheme(
    primary = GreenAccent,
    onPrimary = Color.White,
    primaryContainer = GreenAccent.copy(alpha = 0.2f),
    onPrimaryContainer = GreenAccent,
    secondary = GroovyBlue,
    onSecondary = Color.White,
    secondaryContainer = GroovyBlue.copy(alpha = 0.2f),
    onSecondaryContainer = GroovyBlue,
    tertiary = Pink80,
    onTertiary = Color.White,
    tertiaryContainer = Pink80.copy(alpha = 0.2f),
    onTertiaryContainer = Pink80,
    error = DislikeRed,
    onError = Color.White,
    errorContainer = DislikeRed.copy(alpha = 0.2f),
    onErrorContainer = DislikeRed,
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF3A3A3A),
    scrim = Color.Black.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenAccent,
    onPrimary = Color.White,
    primaryContainer = GreenAccent.copy(alpha = 0.1f),
    onPrimaryContainer = GreenAccent.copy(alpha = 0.8f),
    secondary = GroovyBlue,
    onSecondary = Color.White,
    secondaryContainer = GroovyBlue.copy(alpha = 0.1f),
    onSecondaryContainer = GroovyBlue.copy(alpha = 0.8f),
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Pink40.copy(alpha = 0.1f),
    onTertiaryContainer = Pink40.copy(alpha = 0.8f),
    error = DislikeRed,
    onError = Color.White,
    errorContainer = DislikeRed.copy(alpha = 0.1f),
    onErrorContainer = DislikeRed.copy(alpha = 0.8f),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black.copy(alpha = 0.5f)
)

data class AppColors(
    val greenAccent: Color = GreenAccent,
    val groovyBlue: Color = GroovyBlue,
    val likeGreen: Color = LikeGreen,
    val dislikeRed: Color = DislikeRed,
    val yesButton: Color = YesButton,
    val noButton: Color = NoButton,
    val orangeAccent: Color = OrangeAccent
)

private val LocalAppColors = staticCompositionLocalOf { AppColors() }

@Composable
fun MobileDiggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    selectedTheme: ThemeColors = MobileDiggerTheme,
    fontFamilyName: String = "Default",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> createDarkColorScheme(selectedTheme)
        else -> createLightColorScheme(selectedTheme)
    }
    
    // Check if we're in desktop mode or large screen
    val isLargeScreen = configuration.screenWidthDp >= 840 || configuration.screenHeightDp >= 840
    
    // Use standard MaterialTheme (AdaptiveTheme not available in current Compose version)
    val typography = getTypography(fontFamilyName)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        CompositionLocalProvider(LocalAppColors provides AppColors()) {
            // Material3 leaves LocalTextStyle as the system default, so bare Text()
            // ignored the selected font. Propagate the chosen typography as the default
            // text style so every Text inherits the selected font family.
            androidx.compose.material3.ProvideTextStyle(value = typography.bodyLarge) {
                content()
            }
        }
    }
}

// Every role is a SOLID colour derived from the theme (no alpha), including the
// surfaceContainer* roles that dialogs, menus, cards and sheets use — those used to fall back
// to Material's default purple-grey whatever theme was picked.
private fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

private fun createDarkColorScheme(theme: ThemeColors): androidx.compose.material3.ColorScheme {
    val bg = theme.darkBackground
    val surf = theme.darkSurface
    val on = theme.darkOnSurface
    val onBg = theme.darkOnBackground
    val p = theme.darkPrimary
    return darkColorScheme(
        primary = p,
        onPrimary = theme.darkOnPrimary,
        primaryContainer = mix(surf, p, 0.32f),
        onPrimaryContainer = mix(p, Color.White, 0.55f),
        inversePrimary = mix(p, Color.Black, 0.35f),
        secondary = theme.darkSecondary,
        onSecondary = theme.darkOnSecondary,
        secondaryContainer = mix(surf, theme.darkSecondary, 0.28f),
        onSecondaryContainer = mix(theme.darkSecondary, Color.White, 0.55f),
        tertiary = theme.darkTertiary,
        onTertiary = theme.darkOnTertiary,
        tertiaryContainer = mix(surf, theme.darkTertiary, 0.28f),
        onTertiaryContainer = mix(theme.darkTertiary, Color.White, 0.55f),
        error = DislikeRed,
        onError = Color.White,
        errorContainer = mix(surf, DislikeRed, 0.30f),
        onErrorContainer = mix(DislikeRed, Color.White, 0.55f),
        background = bg,
        onBackground = onBg,
        surface = surf,
        onSurface = on,
        surfaceVariant = mix(surf, onBg, 0.10f),
        onSurfaceVariant = mix(on, surf, 0.30f),
        surfaceTint = p,
        inverseSurface = onBg,
        inverseOnSurface = bg,
        outline = mix(on, surf, 0.55f),
        outlineVariant = mix(on, surf, 0.82f),
        scrim = Color.Black,
        surfaceBright = mix(surf, onBg, 0.14f),
        surfaceDim = bg,
        surfaceContainerLowest = mix(bg, Color.Black, 0.25f),
        surfaceContainerLow = mix(surf, bg, 0.5f),
        surfaceContainer = mix(surf, onBg, 0.04f),
        surfaceContainerHigh = mix(surf, onBg, 0.08f),
        surfaceContainerHighest = mix(surf, onBg, 0.12f)
    )
}

private fun createLightColorScheme(theme: ThemeColors): androidx.compose.material3.ColorScheme {
    val bg = theme.background
    val surf = theme.surface
    val on = theme.onSurface
    val onBg = theme.onBackground
    val p = theme.primary
    return lightColorScheme(
        primary = p,
        onPrimary = theme.onPrimary,
        primaryContainer = mix(Color.White, p, 0.16f),
        onPrimaryContainer = mix(p, Color.Black, 0.45f),
        inversePrimary = mix(p, Color.White, 0.45f),
        secondary = theme.secondary,
        onSecondary = theme.onSecondary,
        secondaryContainer = mix(Color.White, theme.secondary, 0.14f),
        onSecondaryContainer = mix(theme.secondary, Color.Black, 0.45f),
        tertiary = theme.tertiary,
        onTertiary = theme.onTertiary,
        tertiaryContainer = mix(Color.White, theme.tertiary, 0.14f),
        onTertiaryContainer = mix(theme.tertiary, Color.Black, 0.45f),
        error = DislikeRed,
        onError = Color.White,
        errorContainer = mix(Color.White, DislikeRed, 0.12f),
        onErrorContainer = mix(DislikeRed, Color.Black, 0.45f),
        background = bg,
        onBackground = onBg,
        surface = bg,
        onSurface = on,
        surfaceVariant = mix(surf, onBg, 0.06f),
        onSurfaceVariant = mix(on, bg, 0.28f),
        surfaceTint = p,
        inverseSurface = mix(onBg, Color.Black, 0.2f),
        inverseOnSurface = bg,
        outline = mix(on, bg, 0.55f),
        outlineVariant = mix(on, bg, 0.82f),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = mix(surf, onBg, 0.08f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = mix(bg, surf, 0.5f),
        surfaceContainer = surf,
        surfaceContainerHigh = mix(surf, onBg, 0.03f),
        surfaceContainerHighest = mix(surf, onBg, 0.06f)
    )
}
