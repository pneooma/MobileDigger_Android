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
        // Start background writer thread
        Thread {
            while (true) {
                try {
                    Thread.sleep(WRITE_INTERVAL_MS)
                    flushLogs()
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    android.util.Log.e("UnifiedLogger", "Error in background writer", e)
                }
            }
        }.start()
        
        log("UnifiedLogger", "Unified logging system initialized")
    }
    
    fun setResultsFolder(uri: Uri?) {
        val ctx = context ?: return
        resultsFolder = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
        resultsSubfolder = null
        debugFileUri = null
        
        // Ensure a dedicated 'results' subfolder to avoid clutter
        val root = resultsFolder
        if (root == null) {
            android.util.Log.w("UnifiedLogger", "Results root is null; cannot set results folder")
            return
        }
        if (!root.exists()) {
            android.util.Log.w("UnifiedLogger", "Results root doesn't exist")
            return
        }
        
        // Find or create 'results' directory (case-insensitive)
        val existingDir = root.listFiles().firstOrNull { it.isDirectory && (it.name ?: "").equals("results", ignoreCase = true) }
        resultsSubfolder = existingDir ?: root.createDirectory("results") ?: root
        
        // Initialize (or find) DEBUG file and cache its URI
        initDebugFileIfNeeded()
        
        log("UnifiedLogger", "Results folder set to ${uri?.toString() ?: "null"}; using subfolder '${resultsSubfolder?.name}'")
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
        if (isWriting.compareAndSet(false, true)) {
            try {
                val now = System.currentTimeMillis()
                if (now - lastWriteAtMs < WRITE_INTERVAL_MS && logQueue.isEmpty()) return
                lastWriteAtMs = now
                
                val logsToWrite = mutableListOf<String>()
                while (logQueue.isNotEmpty()) { logQueue.poll()?.let { logsToWrite.add(it) } }
                if (logsToWrite.isNotEmpty()) {
                    kotlinx.coroutines.runBlocking { writeToFile(logsToWrite) }
                }
            } finally {
                isWriting.set(false)
            }
        }
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
        try {
            if (resultsFolder == null) return@withContext
            if (resultsSubfolder == null) resultsSubfolder = resultsFolder
            
            // Ensure DEBUG file exists and URI is cached
            initDebugFileIfNeeded()
            val targetUri = debugFileUri ?: return@withContext
            val ctx = context ?: return@withContext
            
            // Check size for rotation
            val currentLength = try {
                DocumentFile.fromSingleUri(ctx, targetUri)?.length() ?: 0
            } catch (_: Exception) { 0 }
            if (currentLength > MAX_FILE_BYTES) {
                // Delete existing and recreate with same name, update cached URI
                try { DocumentsContract.deleteDocument(ctx.contentResolver, targetUri) } catch (_: Exception) {}
                val folder = resultsSubfolder ?: resultsFolder ?: return@withContext
                val recreated = folder.createFile("text/plain", "DEBUG")
                if (recreated == null) return@withContext
                debugFileUri = recreated.uri
            }
            
            // Append to the single DEBUG file
            val content = logs.joinToString("\n") + "\n"
            ctx.contentResolver.openOutputStream(debugFileUri!!, "wa")?.use { os ->
                os.write(content.toByteArray())
                os.flush()
            }
        } catch (e: Exception) {
            android.util.Log.e("UnifiedLogger", "Failed to write logs to file", e)
        }
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
