package com.example.mobiledigger.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object UnifiedLogger {
    private var context: Context? = null
    private var resultsFolder: DocumentFile? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val isWriting = AtomicBoolean(false)
    private val lock = Any()
    
    // Configuration
    private const val MAX_FILE_BYTES = 10 * 1024 * 1024 // 10 MB cap
    private const val WRITE_INTERVAL_MS = 2000L // Write every 2 seconds
    private var lastWriteAtMs: Long = 0L
    
    // Performance timing helper
    private val timingMap = mutableMapOf<String, Long>()
    
    fun initialize(ctx: Context) {
        context = ctx
        // Start background writer thread
        Thread {
            while (true) {
                try {
                    Thread.sleep(WRITE_INTERVAL_MS)
                    flushLogs()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    android.util.Log.e("UnifiedLogger", "Error in background writer", e)
                }
            }
        }.start()
        
        log("UnifiedLogger", "Unified logging system initialized")
    }
    
    fun setResultsFolder(uri: Uri?) {
        context?.let { ctx ->
            resultsFolder = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
            log("UnifiedLogger", "Results folder set: ${uri?.toString() ?: "null"}")
        }
    }
    
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name
        val threadId = Thread.currentThread().id
        
        val logEntry = if (throwable != null) {
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            "[$timestamp] [Thread:$threadName-$threadId] [$tag] $message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\nStack trace:\n$stackTrace"
        } else {
            "[$timestamp] [Thread:$threadName-$threadId] [$tag] $message"
        }
        
        // Add to queue
        logQueue.offer(logEntry)
        
        // Also log to Android logcat for immediate debugging
        android.util.Log.d(tag, message, throwable)
        
        // Force immediate write for errors
        if (throwable != null) {
            flushLogs()
        }
    }
    
    fun startTiming(key: String) {
        synchronized(timingMap) {
            timingMap[key] = System.currentTimeMillis()
        }
    }
    
    fun endTiming(tag: String, key: String, message: String = "") {
        val elapsed = synchronized(timingMap) {
            val start = timingMap.remove(key) ?: return
            System.currentTimeMillis() - start
        }
        log(tag, "⏱️ $key took ${elapsed}ms ${if (message.isNotEmpty()) "- $message" else ""}")
    }
    
    private fun flushLogs() {
        if (isWriting.compareAndSet(false, true)) {
            try {
                val now = System.currentTimeMillis()
                if (now - lastWriteAtMs < WRITE_INTERVAL_MS && logQueue.isEmpty()) {
                    return
                }
                lastWriteAtMs = now
                
                val logsToWrite = mutableListOf<String>()
                while (logQueue.isNotEmpty()) {
                    logQueue.poll()?.let { logsToWrite.add(it) }
                }
                
                if (logsToWrite.isNotEmpty()) {
                    // Use runBlocking to call suspend function from non-suspend context
                    kotlinx.coroutines.runBlocking {
                        writeToFile(logsToWrite)
                    }
                }
            } finally {
                isWriting.set(false)
            }
        }
    }
    
    private suspend fun writeToFile(logs: List<String>) = withContext(Dispatchers.IO) {
        try {
            val folder = resultsFolder
            if (folder == null) {
                android.util.Log.w("UnifiedLogger", "No results folder set, cannot write logs")
                return@withContext
            }
            
            if (!folder.exists()) {
                android.util.Log.w("UnifiedLogger", "Results folder does not exist")
                return@withContext
            }
            
            // Find or create single DEBUG file
            var debugFile = folder.findFile("DEBUG")
            if (debugFile == null) {
                debugFile = folder.createFile("text/plain", "DEBUG")
                if (debugFile == null) {
                    android.util.Log.e("UnifiedLogger", "Failed to create DEBUG file")
                    return@withContext
                }
            }
            
            // Rotate by deleting and recreating the SAME name (no backups) to ensure a single file
            val currentLength = try { debugFile.length() } catch (_: Exception) { 0 }
            if (currentLength > MAX_FILE_BYTES) {
                try {
                    debugFile.delete()
                } catch (_: Exception) { }
                debugFile = folder.createFile("text/plain", "DEBUG")
                if (debugFile == null) {
                    android.util.Log.e("UnifiedLogger", "Failed to recreate DEBUG file after rotation")
                    return@withContext
                }
            }
            
            // Append mode using SAF
            val logContent = logs.joinToString("\n") + "\n"
            context?.contentResolver?.openOutputStream(debugFile.uri, "wa")?.use { outputStream ->
                outputStream.write(logContent.toByteArray())
                outputStream.flush()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("UnifiedLogger", "Failed to write logs to file", e)
        }
    }
    
    fun forceFlush() {
        flushLogs()
    }
    
    fun clearLogs() {
        logQueue.clear()
        try {
            resultsFolder?.findFile("DEBUG")?.delete()
            log("UnifiedLogger", "Logs cleared")
        } catch (e: Exception) {
            android.util.Log.e("UnifiedLogger", "Failed to clear logs", e)
        }
    }
    
    fun getLogCount(): Int = logQueue.size
}
