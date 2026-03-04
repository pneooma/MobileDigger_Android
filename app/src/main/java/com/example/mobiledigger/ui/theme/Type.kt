package com.example.mobiledigger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.R

// Local bundled fonts (no internet / GMS required)
private val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold)
)
private val RobotoFamily = FontFamily(
    Font(R.font.roboto, FontWeight.Normal),
    Font(R.font.roboto_bold, FontWeight.Bold)
)
private val PoppinsFamily = FontFamily(
    Font(R.font.poppins, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold)
)
private val NunitoFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold)
)
private val LatoFamily = FontFamily(
    Font(R.font.lato, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold)
)
private val OpenSansFamily = FontFamily(
    Font(R.font.opensans, FontWeight.Normal),
    Font(R.font.opensans_bold, FontWeight.Bold)
)
private val MontserratFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)
private val RalewayFamily = FontFamily(
    Font(R.font.raleway, FontWeight.Normal),
    Font(R.font.raleway_bold, FontWeight.Bold)
)
private val SourceSans3Family = FontFamily(
    Font(R.font.sourcesans3, FontWeight.Normal),
    Font(R.font.sourcesans3_bold, FontWeight.Bold)
)
private val DmSansFamily = FontFamily(
    Font(R.font.dmsans, FontWeight.Normal),
    Font(R.font.dmsans_bold, FontWeight.Bold)
)

val appFontFamilies = linkedMapOf(
    "Default" to FontFamily.Default,
    "Inter" to InterFamily,
    "Roboto" to RobotoFamily,
    "Poppins" to PoppinsFamily,
    "Nunito" to NunitoFamily,
    "Lato" to LatoFamily,
    "Open Sans" to OpenSansFamily,
    "Montserrat" to MontserratFamily,
    "Raleway" to RalewayFamily,
    "Source Sans 3" to SourceSans3Family,
    "DM Sans" to DmSansFamily
)

fun getTypography(fontFamilyName: String = "Default"): Typography {
    val fontFamily = appFontFamilies[fontFamilyName] ?: FontFamily.Default
    return Typography(
        displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    )
}

// Default typography (kept for compatibility)
val Typography = getTypography()
