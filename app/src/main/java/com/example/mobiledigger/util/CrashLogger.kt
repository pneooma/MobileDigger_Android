package com.example.mobiledigger.util

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * App log: every line goes to logcat (debug builds, or always with an exception) and, in
 * batches, to files/crashlogs/mobiledigger_crash_log.txt (capped at 2 MB).
 */
object CrashLogger {
    // DateTimeFormatter is thread-safe (the old shared SimpleDateFormat was not)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val memoryLogs = ArrayDeque<String>() // recent lines, used to trim the file
    private val pendingLines = ArrayDeque<String>() // to-be-written lines
    private val lock = Any()
    private const val MAX_IN_MEMORY_LINES = 500
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 2 MB cap
    private const val WRITE_INTERVAL_MS = 1000L
    private var lastWriteAtMs: Long = 0L
    private var internalLogFile: java.io.File? = null

    fun init(ctx: Context) {
        try {
            val crashLogsDir = java.io.File(ctx.filesDir, "crashlogs")
            if (!crashLogsDir.exists()) crashLogsDir.mkdirs()
            internalLogFile = java.io.File(crashLogsDir, "mobiledigger_crash_log.txt")
        } catch (e: Exception) {
            android.util.Log.e("CrashLogger", "Failed to create internal log file", e)
        }
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        // Verbose logs only in debug builds; exceptions always log.
        if (throwable != null || com.example.mobiledigger.BuildConfig.DEBUG) {
            android.util.Log.d(tag, message, throwable)
        }

        val timestamp = LocalDateTime.now().format(dateFormat)
        val thread = Thread.currentThread()
        val logEntry = if (throwable != null) {
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            "[$timestamp] [Thread:${thread.name}-${thread.id}] [$tag] $message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\nStack trace:\n$stackTrace"
        } else {
            "[$timestamp] [Thread:${thread.name}-${thread.id}] [$tag] $message"
        }

        synchronized(lock) {
            memoryLogs.addLast(logEntry)
            if (memoryLogs.size > MAX_IN_MEMORY_LINES) memoryLogs.removeFirst()
            pendingLines.addLast(logEntry)
        }

        // Write out periodically, immediately if throwable present
        try {
            writeOutIfDue(force = throwable != null)
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
            // Throwable, not Exception: an OutOfMemoryError here previously killed the
            // whole app from a background logging thread.
            try { writeToInternalFile(batch) } catch (e: Throwable) { android.util.Log.e("CrashLogger", "Internal write failed", e) }
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

    private fun writeToInternalFile(batch: List<String>) {
        val logFile = internalLogFile ?: return
        try {
            logFile.appendText(batch.joinToString("\n") + "\n")
            if (logFile.length() > MAX_FILE_BYTES) {
                // Overwrite with a BOUNDED in-memory tail (joining everything once
                // attempted a 93MB string and OOMed).
                val tail = synchronized(lock) { memoryLogs.toList().takeLast(2000).joinToString("\n") + "\n" }
                logFile.writeText(tail)
            }
        } catch (e: Throwable) {
            android.util.Log.e("CrashLogger", "Failed to write to internal log file", e)
        }
    }
}
