package com.example.mobiledigger.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object UnifiedLogger {
    private var context: Context? = null
    private var resultsFolder: DocumentFile? = null
    private var resultsSubfolder: DocumentFile? = null
    private var debugFileUri: Uri? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<String>()

    fun initialize(ctx: Context) {
        context = ctx
        log("UnifiedLogger", "Unified logging system initialized (file logging disabled)")
    }

    fun setResultsFolder(uri: Uri?) {
        val ctx = context ?: return
        resultsFolder = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
        resultsSubfolder = null
        debugFileUri = null
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

        logQueue.offer(logEntry)
        // Verbose logs only in debug builds; exceptions always log.
        if (throwable != null || com.example.mobiledigger.BuildConfig.DEBUG) {
            android.util.Log.d(tag, message, throwable)
        }
    }
}
