package com.example.mobiledigger.model

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

data class MusicFile(
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long = 0L
)

enum class SortAction {
    LIKE,
    DISLIKE,
    SKIP
}

data class SortResult(
    val musicFile: MusicFile,
    val action: SortAction,
    val timestamp: Long = System.currentTimeMillis()
)
