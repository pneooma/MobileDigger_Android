package com.example.mobiledigger.util

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private var destinationFolder: DocumentFile? = null
    private var context: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val memoryLogs = ArrayDeque<String>() // ring buffer for UI/debug
    private val pendingLines = ArrayDeque<String>() // to-be-written lines
    private val lock = Any()
    private const val MAX_IN_MEMORY_LINES = 500
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 2 MB cap
    private const val WRITE_INTERVAL_MS = 1000L
    private var lastWriteAtMs: Long = 0L
    private var internalLogFile: java.io.File? = null
    private var publicDownloadsLogUri: Uri? = null
    
    fun setDestinationFolder(ctx: Context, uri: Uri?) {
        context = ctx
        destinationFolder = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
        
        // Create internal crash logs directory
        try {
            val crashLogsDir = java.io.File(ctx.filesDir, "crashlogs")
            if (!crashLogsDir.exists()) {
                crashLogsDir.mkdirs()
            }
            internalLogFile = java.io.File(crashLogsDir, "mobiledigger_crash_log.txt")
        } catch (e: Exception) {
            android.util.Log.e("CrashLogger", "Failed to create internal log file", e)
        }
        
        // Move public downloads file creation to background to avoid blocking startup
        Thread {
            ensurePublicDownloadsFile(ctx)
        }.start()
    }
    
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logEntry = if (throwable != null) {
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            "[$timestamp] [$tag] $message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\nStack trace:\n$stackTrace"
        } else {
            "[$timestamp] [$tag] $message"
        }
        
        synchronized(lock) {
            memoryLogs.addLast(logEntry)
            if (memoryLogs.size > MAX_IN_MEMORY_LINES) memoryLogs.removeFirst()
            pendingLines.addLast(logEntry)
        }
        
        // Also log to Android logcat
        android.util.Log.d(tag, message, throwable)
        
        // Write out periodically, immediately if throwable present
        try {
            val force = throwable != null
            writeOutIfDue(force)
        } catch (e: Exception) {
            android.util.Log.e("CrashLogger", "Failed to schedule log write", e)
        }
    }
    
    private fun writeOutIfDue(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWriteAtMs < WRITE_INTERVAL_MS) return
        lastWriteAtMs = now
        Thread {
            val batch = drainPending()
            if (batch.isEmpty()) return@Thread
            try { writeToInternalFile(batch) } catch (e: Exception) { android.util.Log.e("CrashLogger", "Internal write failed", e) }
            // Disabled external writes to avoid creating files in user storage
            // try { writeToPublicDownloads(batch) } catch (e: Exception) { android.util.Log.e("CrashLogger", "Public write failed", e) }
            // try { writeToDestinationFile(batch) } catch (e: Exception) { android.util.Log.e("CrashLogger", "Destination write failed", e) }
        }.start()
    }

    private fun drainPending(): List<String> {
        synchronized(lock) {
            if (pendingLines.isEmpty()) return emptyList()
            val out = pendingLines.toList()
            pendingLines.clear()
            return out
        }
    }

    private fun writeToDestinationFile(batch: List<String>) { /* disabled */ }
    
    fun getAllLogs(): String {
        synchronized(lock) { return memoryLogs.joinToString("\n") }
    }
    
    private fun writeToInternalFileAsync() {
        Thread {
            try {
                val batch = drainPending()
                if (batch.isEmpty()) return@Thread
                writeToInternalFile(batch)
            } catch (e: Exception) {
                android.util.Log.e("CrashLogger", "Internal write failed", e)
            }
        }.start()
    }
    
    private fun writeToInternalFile(batch: List<String>) {
        val logFile = internalLogFile ?: return
        try {
            logFile.appendText(batch.joinToString("\n") + "\n")
            if (logFile.length() > MAX_FILE_BYTES) {
                // Overwrite with in-memory tail
                val tail = synchronized(lock) { memoryLogs.joinToString("\n") + "\n" }
                logFile.writeText(tail)
            }
        } catch (e: Exception) {
            android.util.Log.e("CrashLogger", "Failed to write to internal log file", e)
        }
    }
    
    fun getInternalLogPath(): String? {
        return internalLogFile?.absolutePath
    }

    private fun ensurePublicDownloadsFile(ctx: Context) { /* disabled */ }

    private fun writeToPublicDownloads(batch: List<String>) { /* disabled */ }
    
    fun clearLogs() {
        synchronized(lock) {
            memoryLogs.clear()
            pendingLines.clear()
        }
        try {
            internalLogFile?.delete()
        } catch (e: Exception) {
            android.util.Log.e("CrashLogger", "Failed to clear internal log", e)
        }
    }
}
