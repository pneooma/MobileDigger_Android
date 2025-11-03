package com.example.mobiledigger.util
import com.example.mobiledigger.util.CrashLogger

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Utility class for proper resource management of MediaMetadataRetriever and MediaExtractor
 * to prevent memory leaks
 */
object ResourceManager {
    
    /**
     * Safely execute operations with MediaMetadataRetriever
     */
    suspend fun <T> withMediaMetadataRetriever(
        context: Context,
        uri: Uri,
        operation: suspend (MediaMetadataRetriever) -> T
    ): T? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            // Validate URI before attempting to use it
            if (uri.toString().isEmpty() || uri.toString() == "null" || uri.toString() == ":") {
                CrashLogger.log("ResourceManager", "Invalid URI for MediaMetadataRetriever: $uri")
                return@withContext null
            }
            
            retriever = MediaMetadataRetriever()
            
            // Add timeout and better error handling for setDataSource
            try {
                retriever.setDataSource(context, uri)
            } catch (e: Exception) {
                CrashLogger.log("ResourceManager", "setDataSource failed for URI: $uri", e)
                return@withContext null
            }
            
            operation(retriever)
        } catch (e: Exception) {
            CrashLogger.log("ResourceManager", "MediaMetadataRetriever operation failed for URI: $uri", e)
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                CrashLogger.log("ResourceManager", "Failed to release MediaMetadataRetriever", e)
            }
        }
    }
    
    /**
     * Safely execute operations with MediaExtractor
     */
    suspend fun <T> withMediaExtractor(
        context: Context,
        uri: Uri,
        operation: suspend (MediaExtractor) -> T
    ): T? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        try {
            // Validate URI before attempting to use it
            if (uri.toString().isEmpty() || uri.toString() == "null" || uri.toString() == ":") {
                CrashLogger.log("ResourceManager", "Invalid URI for MediaExtractor: $uri")
                return@withContext null
            }
            
            extractor = MediaExtractor()
            
            // Add better error handling for setDataSource
            try {
                extractor.setDataSource(context, uri, emptyMap<String, String>())
            } catch (e: Exception) {
                CrashLogger.log("ResourceManager", "MediaExtractor setDataSource failed for URI: $uri", e)
                return@withContext null
            }
            
            operation(extractor)
        } catch (e: Exception) {
            CrashLogger.log("ResourceManager", "MediaExtractor operation failed for URI: $uri", e)
            null
        } finally {
            try {
                extractor?.release()
            } catch (e: Exception) {
                CrashLogger.log("ResourceManager", "Failed to release MediaExtractor", e)
            }
        }
    }
    
    /**
     * Extract duration safely with timeout
     */
    suspend fun extractDuration(context: Context, uri: Uri): Long = withTimeout(10000L) {
        withMediaMetadataRetriever(context, uri) { retriever ->
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull() ?: 0L
        } ?: 0L
    }
    
    /**
     * Extract duration with fallback methods
     */
    suspend fun extractDurationWithFallback(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            // Check file size first for large files
            val fileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    fd.statSize
                } ?: -1L
            } catch (e: Exception) {
                -1L
            }
            
            val fileSizeMB = fileSize / (1024 * 1024)
            val isLargeFile = fileSizeMB > 100
            
            if (isLargeFile) {
                CrashLogger.log("ResourceManager", "Large file detected (${fileSizeMB}MB), using optimized duration extraction")
            }
            
            // Method 1: MediaMetadataRetriever
            val duration1 = extractDuration(context, uri)
            if (duration1 > 0) return@withContext duration1
            
            // Method 2: MediaExtractor fallback (skip for very large files to prevent crashes)
            if (!isLargeFile || fileSizeMB < 150) {
                withMediaExtractor(context, uri) { extractor ->
                    var totalDuration = 0L
                    val trackCount = extractor.trackCount
                    
                    for (i in 0 until trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                        
                        if (mime?.startsWith("audio/") == true) {
                            val duration = format.getLong(android.media.MediaFormat.KEY_DURATION)
                            if (duration > 0) {
                                totalDuration = duration / 1000 // Convert to milliseconds
                                break
                            }
                        }
                    }
                    totalDuration
                } ?: 0L
            } else {
                CrashLogger.log("ResourceManager", "Skipping MediaExtractor for very large file (${fileSizeMB}MB)")
                0L
            }
        } catch (e: Exception) {
            CrashLogger.log("ResourceManager", "Failed to extract duration with fallback", e)
            0L
        }
    }
    
    /**
     * Coroutine-based delay replacement for Thread.sleep()
     */
    suspend fun delayWithProgress(
        totalTimeMs: Long,
        checkIntervalMs: Long = 50L,
        onProgress: (Long) -> Boolean // Return true to continue, false to abort
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < totalTimeMs) {
            if (!onProgress(System.currentTimeMillis() - startTime)) {
                return@withContext false
            }
            delay(checkIntervalMs)
        }
        true
    }
}
