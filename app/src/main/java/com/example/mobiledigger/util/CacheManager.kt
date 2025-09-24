package com.example.mobiledigger.util

import android.content.Context
import java.io.File

object CacheManager {
    /**
     * Calculate total bytes used under app cache directories (internal + external).
     */
    fun calculateTotalCacheBytes(context: Context): Long {
        return getCacheDirs(context)
            .filterNotNull()
            .sumOf { dir -> dir.safeSizeBytes() }
    }

    /**
     * Trim disk cache down to preferredMaxBytes by deleting least-recently-used files first.
     * Will never delete files present in protectedPaths.
     */
    fun trimDiskCache(
        context: Context,
        preferredMaxBytes: Long,
        hardMaxBytes: Long,
        protectedPaths: Set<String> = emptySet()
    ) {
        try {
            val cacheDirs = getCacheDirs(context).filterNotNull()
            if (cacheDirs.isEmpty()) return

            // Collect all candidate files (non-directories) excluding protected paths
            val candidates = mutableListOf<File>()
            var totalBytes = 0L
            cacheDirs.forEach { root ->
                root.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalBytes += f.length()
                        if (!protectedPaths.contains(f.absolutePath)) {
                            candidates.add(f)
                        }
                    }
                }
            }

            // Nothing to do if already under preferred cap
            if (totalBytes <= preferredMaxBytes) return

            // Sort by lastModified ascending (oldest first) to behave like LRU
            candidates.sortBy { it.lastModified() }

            var bytesToFree = totalBytes - preferredMaxBytes
            for (file in candidates) {
                // Fail-safe: if we exceed hard cap badly, keep deleting
                if (bytesToFree <= 0 && totalBytes <= hardMaxBytes) break
                val len = file.length()
                runCatching { file.delete() }
                totalBytes -= len
                bytesToFree -= len
            }
        } catch (_: Exception) {
            // Best-effort; never let cleanup crash the app
        }
    }

    private fun getCacheDirs(context: Context): List<File?> = listOf(
        context.cacheDir,
        context.externalCacheDir
    )
}

private fun File.safeSizeBytes(): Long {
    return try {
        if (!exists()) 0L
        else if (isFile) length()
        else walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }
}


