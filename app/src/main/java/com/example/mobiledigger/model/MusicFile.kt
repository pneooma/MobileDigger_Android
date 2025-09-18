package com.example.mobiledigger.model

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.example.mobiledigger.viewmodel.PlaylistTab

data class MusicFile(
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long = 0L,
    val sourcePlaylist: PlaylistTab = PlaylistTab.TODO,
    val rating: Int = 0, // 0-5 stars
    val bpm: Int = 0 // Beats per minute
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