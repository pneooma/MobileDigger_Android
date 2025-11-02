package com.example.mobiledigger.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
    private var resultsSubfolder: DocumentFile? = null
    private var debugFileUri: Uri? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val isWriting = AtomicBoolean(false)
    
    // Configuration
    private const val MAX_FILE_BYTES = 10 * 1024 * 1024 // 10 MB cap
    private const val WRITE_INTERVAL_MS = 2000L // Write every 2 seconds
    private var lastWriteAtMs: Long = 0L
    
    // Performance timing helper
    private val timingMap = mutableMapOf<String, Long>()
    
    fun initialize(ctx: Context) {
        context = ctx
        // DISABLED: Background writer thread disabled for better performance
        // (File logging is disabled)
        
        log("UnifiedLogger", "Unified logging system initialized (file logging disabled)")
    }
    
    fun setResultsFolder(uri: Uri?) {
        val ctx = context ?: return
        resultsFolder = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
        resultsSubfolder = null
        debugFileUri = null
        
        // DISABLED: File creation disabled for better performance
        // (File logging is disabled)
        
        log("UnifiedLogger", "Results folder set to ${uri?.toString() ?: "null"} (file logging disabled)")
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
        if (throwable != null) flushLogs()
    }
    
    fun startTiming(key: String) { synchronized(timingMap) { timingMap[key] = System.currentTimeMillis() } }
    fun endTiming(tag: String, key: String, message: String = "") {
        val elapsed = synchronized(timingMap) {
            val start = timingMap.remove(key) ?: return
            System.currentTimeMillis() - start
        }
        log(tag, "⏱️ $key took ${elapsed}ms ${if (message.isNotEmpty()) "- $message" else ""}")
    }
    
    private fun flushLogs() {
        // DISABLED: File flushing disabled for better performance
        // (File logging is disabled)
        return
    }
    
    private fun initDebugFileIfNeeded() {
        val ctx = context ?: return
        if (debugFileUri != null) return
        val folder = resultsSubfolder ?: resultsFolder ?: return
        
        // Find existing DEBUG file (case-insensitive by name)
        val existing = folder.listFiles().firstOrNull { it.isFile && (it.name ?: "").equals("DEBUG", ignoreCase = true) }
        val file = existing ?: folder.createFile("text/plain", "DEBUG")
        debugFileUri = file?.uri
    }
    
    private suspend fun writeToFile(logs: List<String>) = withContext(Dispatchers.IO) {
        // DISABLED: File export disabled for better performance
        return@withContext
    }
    
    fun forceFlush() { flushLogs() }
    fun clearLogs() {
        logQueue.clear()
        val ctx = context ?: return
        val uri = debugFileUri ?: return
        try { DocumentsContract.deleteDocument(ctx.contentResolver, uri) } catch (_: Exception) {}
        debugFileUri = null
    }
    
    fun getLogCount(): Int = logQueue.size
}
