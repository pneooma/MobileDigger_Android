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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalAppColors provides AppColors()) {
            content()
        }
    }
}

private fun createDarkColorScheme(theme: ThemeColors) = darkColorScheme(
    primary = theme.darkPrimary,
    onPrimary = theme.darkOnPrimary,
    primaryContainer = theme.darkPrimary.copy(alpha = 0.2f),
    onPrimaryContainer = theme.darkPrimary,
    secondary = theme.darkSecondary,
    onSecondary = theme.darkOnSecondary,
    secondaryContainer = theme.darkSecondary.copy(alpha = 0.2f),
    onSecondaryContainer = theme.darkSecondary,
    tertiary = theme.darkTertiary,
    onTertiary = theme.darkOnTertiary,
    tertiaryContainer = theme.darkTertiary.copy(alpha = 0.2f),
    onTertiaryContainer = theme.darkTertiary,
    error = DislikeRed,
    onError = Color.White,
    errorContainer = DislikeRed.copy(alpha = 0.2f),
    onErrorContainer = DislikeRed,
    background = theme.darkBackground,
    onBackground = theme.darkOnBackground,
    surface = theme.darkSurface,
    onSurface = theme.darkOnSurface,
    surfaceVariant = theme.darkSurface.copy(alpha = 0.8f),
    onSurfaceVariant = theme.darkOnSurface.copy(alpha = 0.7f),
    outline = theme.darkPrimary.copy(alpha = 0.5f),
    outlineVariant = theme.darkPrimary.copy(alpha = 0.3f),
    scrim = Color.Black.copy(alpha = 0.5f)
)

private fun createLightColorScheme(theme: ThemeColors) = lightColorScheme(
    primary = theme.primary,
    onPrimary = theme.onPrimary,
    primaryContainer = theme.primary.copy(alpha = 0.1f),
    onPrimaryContainer = theme.primary.copy(alpha = 0.8f),
    secondary = theme.secondary,
    onSecondary = theme.onSecondary,
    secondaryContainer = theme.secondary.copy(alpha = 0.1f),
    onSecondaryContainer = theme.secondary.copy(alpha = 0.8f),
    tertiary = theme.tertiary,
    onTertiary = theme.onTertiary,
    tertiaryContainer = theme.tertiary.copy(alpha = 0.1f),
    onTertiaryContainer = theme.tertiary.copy(alpha = 0.8f),
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