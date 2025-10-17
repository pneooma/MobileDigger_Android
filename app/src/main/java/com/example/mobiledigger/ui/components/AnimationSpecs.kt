package com.example.mobiledigger.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

data class OptimizedAnimationSpecs(
    val songNameTween: androidx.compose.animation.core.TweenSpec<Float> = tween(300),
    val defaultDuration: Int = 300
)

@Composable
fun rememberOptimizedAnimationSpecs(): OptimizedAnimationSpecs {
    return remember { OptimizedAnimationSpecs() }
}

