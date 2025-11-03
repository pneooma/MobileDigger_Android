package com.example.mobiledigger.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.mobiledigger.util.CrashLogger
import java.io.File

object FilePathExtractor {
    
    /**
     * Attempts to extract a file path from a URI.
     * Returns null if the URI cannot be converted to a file path.
     * This mimics iOS direct file access approach.
     */
    fun getFilePath(context: Context, uri: Uri): String? {
        CrashLogger.log("Debug", "🔍 FilePathExtractor: Attempting to extract path from URI: $uri")
        CrashLogger.log("Debug", "🔍 URI scheme: ${uri.scheme}")
        
        return when (uri.scheme) {
            "file" -> {
                // Direct file URI - like iOS AVAudioFile(forReading: url)
                val path = uri.path
                CrashLogger.log("Debug", "📁 Direct file URI path: $path")
                if (isFilePathAccessible(path)) {
                    CrashLogger.log("Debug", "✅ Direct file path is accessible - using like iOS")
                    path
                } else {
                    CrashLogger.log("Debug", "❌ Direct file path is not accessible")
                    null
                }
            }
            "content" -> {
                // Try to get file path from content URI
                CrashLogger.log("Debug", "📄 Content URI detected, attempting extraction...")
                val path = getFilePathFromContentUri(context, uri)
                CrashLogger.log("Debug", "📄 Content URI extraction result: $path")
                path
            }
            else -> {
                CrashLogger.log("Debug", "❌ Unsupported URI scheme: ${uri.scheme}")
                CrashLogger.log("FilePathExtractor", "Unsupported URI scheme: ${uri.scheme}")
                null
            }
        }
    }
    
    private fun getFilePathFromContentUri(context: Context, uri: Uri): String? {
        return try {
            CrashLogger.log("Debug", "🔍 Attempting to extract file path from content URI: $uri")
            
            // Try MediaStore first
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val filePath = cursor.getString(columnIndex)
                    CrashLogger.log("Debug", "📁 MediaStore file path: $filePath")
                    if (filePath != null && File(filePath).exists()) {
                        CrashLogger.log("Debug", "✅ MediaStore path is valid and file exists")
                        return filePath
                    } else {
                        CrashLogger.log("Debug", "❌ MediaStore path is invalid or file doesn't exist")
                    }
                }
            }
            
            // Try OpenableColumns as fallback
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val fileName = cursor.getString(nameIndex)
                        CrashLogger.log("Debug", "📄 File name from OpenableColumns: $fileName")
                        if (fileName != null) {
                            // Try to construct a path in external storage
                            val externalDir = context.getExternalFilesDir(null)
                            if (externalDir != null) {
                                val tempPath = File(externalDir, fileName).absolutePath
                                CrashLogger.log("Debug", "📁 Constructed temp path: $tempPath")
                                if (File(tempPath).exists()) {
                                    CrashLogger.log("Debug", "✅ Temp path file exists")
                                    return tempPath
                                } else {
                                    CrashLogger.log("Debug", "❌ Temp path file doesn't exist")
                                }
                            }
                        }
                    }
                }
            }
            
            CrashLogger.log("Debug", "❌ Could not extract file path from content URI")
            null
        } catch (e: Exception) {
            CrashLogger.log("Debug", "❌ Exception extracting file path: ${e.message}")
            CrashLogger.log("FilePathExtractor", "Error extracting file path from content URI", e)
            null
        }
    }
    
    /**
     * Checks if a file path is accessible and the file exists
     */
    fun isFilePathAccessible(filePath: String?): Boolean {
        return filePath != null && File(filePath).exists() && File(filePath).canRead()
    }
}
