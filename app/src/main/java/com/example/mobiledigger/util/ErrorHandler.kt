package com.example.mobiledigger.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Phase 3: Centralized error handling to prevent crashes
 * Provides safe coroutine exception handlers
 */
object ErrorHandler {
    
    /**
     * Create a safe coroutine exception handler
     */
    fun createHandler(tag: String, onError: ((String) -> Unit)? = null): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { context, exception ->
            handleException(tag, exception, context)
            onError?.invoke(exception.message ?: "Unknown error")
        }
    }
    
    /**
     * Handle exception with detailed logging
     */
    private fun handleException(tag: String, exception: Throwable, context: CoroutineContext) {
        when (exception) {
            is OutOfMemoryError -> {
                CrashLogger.log(tag, "🚨 OUT OF MEMORY ERROR - triggering emergency cleanup", exception)
                System.gc()
                MemoryMonitor.logMemoryStats()
            }
            is SecurityException -> {
                CrashLogger.log(tag, "🔒 Security exception", exception)
            }
            is java.io.IOException -> {
                CrashLogger.log(tag, "💾 IO exception", exception)
            }
            is kotlinx.coroutines.CancellationException -> {
                CrashLogger.log(tag, "🔄 Coroutine cancelled (normal)", exception)
            }
            else -> {
                CrashLogger.log(tag, "💥 Unhandled exception in coroutine", exception)
            }
        }
    }
    
    /**
     * Safe execution wrapper with fallback
     */
    inline fun <T> runSafely(
        tag: String,
        fallback: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            CrashLogger.log(tag, "Exception in runSafely, using fallback", e)
            fallback
        }
    }
    
    /**
     * Safe execution with nullable return
     */
    inline fun <T> runSafelyOrNull(
        tag: String,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            CrashLogger.log(tag, "Exception in runSafelyOrNull, returning null", e)
            null
        }
    }
}

