package com.example.mobiledigger.ui.theme

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
