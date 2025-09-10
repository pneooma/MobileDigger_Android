package com.example.mobiledigger.util

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
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            operation(retriever)
        } catch (e: Exception) {
            CrashLogger.log("ResourceManager", "MediaMetadataRetriever operation failed", e)
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
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap<String, String>())
            operation(extractor)
        } catch (e: Exception) {
            CrashLogger.log("ResourceManager", "MediaExtractor operation failed", e)
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
            // Method 1: MediaMetadataRetriever
            val duration1 = extractDuration(context, uri)
            if (duration1 > 0) return@withContext duration1
            
            // Method 2: MediaExtractor fallback
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
