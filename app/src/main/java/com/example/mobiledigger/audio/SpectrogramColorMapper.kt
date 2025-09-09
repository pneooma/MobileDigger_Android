package com.example.mobiledigger.audio

import android.graphics.Color

/**
 * Enhanced color mapping system for spectrogram visualization
 * Supports multiple color schemes like professional audio analysis tools
 */
class SpectrogramColorMapper {
    
    /**
     * Convert power value to color based on color scheme
     */
    fun powerToColor(power: Float, maxPower: Float, colorScheme: ColorScheme): Int {
        val powerDb = if (power > 0) 10f * kotlin.math.log10(power / maxPower) else -120f
        val normalizedDb = ((powerDb + 120f) / 120f).coerceIn(0f, 1f)
        
        return when (colorScheme) {
            ColorScheme.SPEK_PROFESSIONAL -> spekProfessionalColor(normalizedDb)
            ColorScheme.SPEK_CLASSIC -> spekClassicColor(normalizedDb)
            ColorScheme.SPEK_MONOCHROME -> monochromeColor(normalizedDb)
            ColorScheme.RAINBOW -> rainbowColor(normalizedDb)
            ColorScheme.HEAT_MAP -> heatMapColor(normalizedDb)
        }
    }
    
    /**
     * Modern Green color scheme: Black → Dark Green → Green → Light Green → Yellow → White
     */
    private fun spekProfessionalColor(intensity: Float): Int {
        val intensityValue = (intensity * 255).toInt().coerceIn(0, 255)
        
        val (red, green, blue) = when {
            intensityValue < 30 -> {
                // Black to Dark Green (0-29)
                val t = intensityValue / 30f
                Triple(0, (t * 30).toInt(), 0)
            }
            intensityValue < 60 -> {
                // Dark Green to Green (30-59)
                val t = (intensityValue - 30) / 30f
                Triple(0, (30 + t * 100).toInt(), 0)
            }
            intensityValue < 90 -> {
                // Green to Light Green (60-89)
                val t = (intensityValue - 60) / 30f
                Triple((t * 50).toInt(), (130 + t * 50).toInt(), 0)
            }
            intensityValue < 120 -> {
                // Light Green to Yellow-Green (90-119)
                val t = (intensityValue - 90) / 30f
                Triple((50 + t * 100).toInt(), (180 + t * 50).toInt(), 0)
            }
            intensityValue < 150 -> {
                // Yellow-Green to Yellow (120-149)
                val t = (intensityValue - 120) / 30f
                Triple((150 + t * 105).toInt(), (230 + t * 25).toInt(), 0)
            }
            intensityValue < 180 -> {
                // Yellow to Light Yellow (150-179)
                val t = (intensityValue - 150) / 30f
                Triple(255, 255, (t * 100).toInt())
            }
            intensityValue < 210 -> {
                // Light Yellow to White (180-209)
                val t = (intensityValue - 180) / 30f
                Triple(255, 255, (100 + t * 155).toInt())
            }
            else -> {
                // White (210-255)
                Triple(255, 255, 255)
            }
        }
        
        return Color.argb(255, red, green, blue)
    }
    
    /**
     * Modern Green Classic: Black → Dark Green → Green → Light Green → Yellow → White
     */
    private fun spekClassicColor(intensity: Float): Int {
        val intensityValue = (intensity * 255).toInt().coerceIn(0, 255)
        
        val (red, green, blue) = when {
            intensityValue < 50 -> {
                // Black to Dark Green (0-49)
                val t = intensityValue / 50f
                Triple(0, (t * 50).toInt(), 0)
            }
            intensityValue < 100 -> {
                // Dark Green to Green (50-99)
                val t = (intensityValue - 50) / 50f
                Triple(0, (50 + t * 100).toInt(), 0)
            }
            intensityValue < 150 -> {
                // Green to Light Green (100-149)
                val t = (intensityValue - 100) / 50f
                Triple((t * 50).toInt(), (150 + t * 50).toInt(), 0)
            }
            intensityValue < 200 -> {
                // Light Green to Yellow (150-199)
                val t = (intensityValue - 150) / 50f
                Triple((50 + t * 100).toInt(), (200 + t * 55).toInt(), 0)
            }
            else -> {
                // Yellow to White (200-255)
                val t = (intensityValue - 200) / 55f
                Triple((150 + t * 105).toInt(), (255), (t * 255).toInt())
            }
        }
        
        return Color.argb(255, red, green, blue)
    }
    
    /**
     * Monochrome color scheme: Black to White
     */
    private fun monochromeColor(intensity: Float): Int {
        val intensityValue = (intensity * 255).toInt().coerceIn(0, 255)
        return Color.argb(255, intensityValue, intensityValue, intensityValue)
    }
    
    /**
     * Rainbow color scheme: Full spectrum
     */
    private fun rainbowColor(intensity: Float): Int {
        val hue = intensity * 240f // 0-240 degrees (blue to red)
        val saturation = 1.0f
        val value = 1.0f
        
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    
    /**
     * Modern Green Heat Map: Dark Green → Green → Light Green → Yellow → White
     */
    private fun heatMapColor(intensity: Float): Int {
        val intensityValue = (intensity * 255).toInt().coerceIn(0, 255)
        
        val (red, green, blue) = when {
            intensityValue < 85 -> {
                // Dark Green to Green (0-84)
                val t = intensityValue / 85f
                Triple(0, (t * 150).toInt(), 0)
            }
            intensityValue < 170 -> {
                // Green to Light Green (85-169)
                val t = (intensityValue - 85) / 85f
                Triple((t * 100).toInt(), (150 + t * 105).toInt(), 0)
            }
            else -> {
                // Light Green to Yellow to White (170-255)
                val t = (intensityValue - 170) / 85f
                Triple((100 + t * 155).toInt(), (255), (t * 255).toInt())
            }
        }
        
        return Color.argb(255, red, green, blue)
    }
    
    /**
     * Get color scheme description
     */
    fun getColorSchemeDescription(colorScheme: ColorScheme): String {
        return when (colorScheme) {
            ColorScheme.SPEK_PROFESSIONAL -> "Professional (Black→Blue→Purple→Red→Yellow→White)"
            ColorScheme.SPEK_CLASSIC -> "Classic (Black→Blue→Green→Yellow→Red)"
            ColorScheme.SPEK_MONOCHROME -> "Monochrome (Black→White)"
            ColorScheme.RAINBOW -> "Rainbow (Full Spectrum)"
            ColorScheme.HEAT_MAP -> "Heat Map (Blue→Green→Yellow→Red)"
        }
    }
}
