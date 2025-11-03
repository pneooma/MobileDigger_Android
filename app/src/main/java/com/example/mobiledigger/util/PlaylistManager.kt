package com.example.mobiledigger.util

import android.net.Uri
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.viewmodel.PlaylistTab

/**
 * Phase 6: Extract playlist management logic from MusicViewModel
 * Handles playlist operations, filtering, and state management
 */
class PlaylistManager {
    
    companion object {
        private const val TAG = "PlaylistManager"
    }
    
    /**
     * Remove a file from a playlist and return updated list
     */
    fun removeFile(playlist: List<MusicFile>, fileUri: Uri): List<MusicFile> {
        return playlist.filter { it.uri != fileUri }
    }
    
    /**
     * Add a file to a playlist (avoiding duplicates)
     */
    fun addFile(playlist: List<MusicFile>, file: MusicFile): List<MusicFile> {
        return if (playlist.any { it.uri == file.uri }) {
            CrashLogger.log(TAG, "File already exists in playlist: ${file.name}")
            playlist
        } else {
            playlist + file
        }
    }
    
    /**
     * Move file from one playlist to another
     * Returns Pair(updatedSourcePlaylist, updatedDestPlaylist)
     */
    fun moveFileBetweenPlaylists(
        sourcePlaylist: List<MusicFile>,
        destPlaylist: List<MusicFile>,
        fileUri: Uri,
        newFile: MusicFile
    ): Pair<List<MusicFile>, List<MusicFile>> {
        val updatedSource = removeFile(sourcePlaylist, fileUri)
        val updatedDest = addFile(destPlaylist, newFile)
        return Pair(updatedSource, updatedDest)
    }
    
    /**
     * Calculate the appropriate new index after removing a file
     */
    fun calculateNewIndex(
        currentIndex: Int,
        removedIndex: Int,
        playlistSize: Int
    ): Int {
        return when {
            removedIndex < currentIndex -> currentIndex - 1
            removedIndex == currentIndex && playlistSize > 0 -> minOf(currentIndex, playlistSize - 1)
            else -> currentIndex
        }
    }
    
    /**
     * Group files by subfolder for display
     */
    fun groupBySubfolder(files: List<MusicFile>): Map<String?, List<MusicFile>> {
        return files.groupBy { it.subfolder }
    }
    
    /**
     * Filter files by search query (name or subfolder)
     */
    fun filterByQuery(files: List<MusicFile>, query: String): List<MusicFile> {
        if (query.isBlank()) return files
        
        val lowerQuery = query.lowercase()
        return files.filter { file ->
            file.name.lowercase().contains(lowerQuery) ||
            file.subfolder?.lowercase()?.contains(lowerQuery) == true
        }
    }
    
    /**
     * Get playlist statistics
     */
    data class PlaylistStats(
        val totalFiles: Int,
        val totalDuration: Long,
        val averageDuration: Long,
        val subfolderCount: Int
    )
    
    fun getStats(files: List<MusicFile>): PlaylistStats {
        val totalDuration = files.sumOf { it.duration }
        val avgDuration = if (files.isNotEmpty()) totalDuration / files.size else 0L
        val subfolderCount = files.mapNotNull { it.subfolder }.distinct().size
        
        return PlaylistStats(
            totalFiles = files.size,
            totalDuration = totalDuration,
            averageDuration = avgDuration,
            subfolderCount = subfolderCount
        )
    }
}

