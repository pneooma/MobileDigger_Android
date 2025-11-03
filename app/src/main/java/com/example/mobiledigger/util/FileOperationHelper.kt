package com.example.mobiledigger.util

import android.content.Context
import com.example.mobiledigger.file.FileManager
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.model.SortAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3: Extracted file operation utilities to reduce MusicViewModel complexity
 * Handles safe file operations with proper error handling
 */
class FileOperationHelper(private val fileManager: FileManager) {
    
    companion object {
        private const val TAG = "FileOperationHelper"
    }
    
    /**
     * Safely move a file with comprehensive error handling
     * Returns: Pair<Boolean, String?> - (success, errorMessage)
     */
    suspend fun safelyMoveFile(file: MusicFile, action: SortAction): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            if (!fileManager.isDestinationSelected()) {
                return@withContext Pair(false, "No destination folder selected")
            }
            
            if (!isValidFile(file)) {
                return@withContext Pair(false, "Invalid file: ${file.name}")
            }
            
            CrashLogger.log(TAG, "Moving file: ${file.name} -> $action")
            
            val success = fileManager.sortFile(file, action)
            
            if (success) {
                CrashLogger.log(TAG, "✅ Successfully moved: ${file.name}")
                Pair(true, null)
            } else {
                val error = "Failed to move file: ${file.name}"
                CrashLogger.log(TAG, "❌ $error")
                Pair(false, error)
            }
            
        } catch (e: SecurityException) {
            val error = "Permission denied: ${e.message}"
            CrashLogger.log(TAG, "🔒 Security error moving file", e)
            Pair(false, error)
            
        } catch (e: java.io.IOException) {
            val error = "IO error: ${e.message}"
            CrashLogger.log(TAG, "💾 IO error moving file", e)
            Pair(false, error)
            
        } catch (e: Exception) {
            val error = "Unexpected error: ${e.message}"
            CrashLogger.log(TAG, "💥 Unexpected error moving file", e)
            Pair(false, error)
        }
    }
    
    /**
     * Validate file before operations
     */
    private fun isValidFile(file: MusicFile): Boolean {
        return try {
            file.uri != null && file.name.isNotBlank()
        } catch (e: Exception) {
            CrashLogger.log(TAG, "File validation failed", e)
            false
        }
    }
    
    /**
     * Batch move files with progress tracking
     * Returns: Triple<successCount, failCount, errors>
     */
    suspend fun batchMoveFiles(
        files: List<MusicFile>,
        action: SortAction,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Triple<Int, Int, List<String>> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var successCount = 0
        var failCount = 0
        
        files.forEachIndexed { index, file ->
            val (success, error) = safelyMoveFile(file, action)
            
            if (success) {
                successCount++
            } else {
                failCount++
                error?.let { errors.add("${file.name}: $it") }
            }
            
            onProgress?.invoke(index + 1, files.size)
        }
        
        CrashLogger.log(TAG, "📊 Batch complete: $successCount succeeded, $failCount failed")
        Triple(successCount, failCount, errors)
    }
}

