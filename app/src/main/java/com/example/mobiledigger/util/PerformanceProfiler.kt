package com.example.mobiledigger.util

import android.os.SystemClock

/**
 * Phase 5: Performance profiling utility
 * Tracks operation timing to identify bottlenecks
 */
object PerformanceProfiler {
    
    private const val TAG = "PerformanceProfiler"
    
    // Threshold for logging slow operations (milliseconds)
    const val SLOW_OPERATION_THRESHOLD_MS = 1000L // Made public for inline function
    
    /**
     * Profile a block of code and log if it's slow
     * Note: Not inline to avoid visibility issues with private properties
     */
    fun <T> profile(
        operationName: String,
        logSlowOps: Boolean = true,
        block: () -> T
    ): T {
        val startTime = SystemClock.elapsedRealtime()
        val result = block()
        val duration = SystemClock.elapsedRealtime() - startTime
        
        if (logSlowOps && duration > SLOW_OPERATION_THRESHOLD_MS) {
            CrashLogger.log(TAG, "⚠️ SLOW OPERATION: $operationName took ${duration}ms")
            MemoryMonitor.logMemoryStats()
        } else if (duration > 100) {
            // Log operations > 100ms at debug level
            CrashLogger.log(TAG, "📊 $operationName took ${duration}ms")
        }
        
        return result
    }
    
    /**
     * Create a timed scope that logs duration on completion
     */
    class TimedScope(private val operationName: String) : AutoCloseable {
        private val startTime = SystemClock.elapsedRealtime()
        
        override fun close() {
            val duration = SystemClock.elapsedRealtime() - startTime
            if (duration > SLOW_OPERATION_THRESHOLD_MS) {
                CrashLogger.log(TAG, "⚠️ SLOW: $operationName took ${duration}ms")
            } else if (duration > 100) {
                CrashLogger.log(TAG, "⏱️ $operationName: ${duration}ms")
            }
        }
    }
    
    /**
     * Measure multiple iterations and log statistics
     */
    fun measureIterations(
        operationName: String,
        iterations: Int,
        operation: () -> Unit
    ) {
        val times = mutableListOf<Long>()
        
        repeat(iterations) {
            val start = SystemClock.elapsedRealtime()
            operation()
            times.add(SystemClock.elapsedRealtime() - start)
        }
        
        val avg = times.average()
        val min = times.minOrNull() ?: 0
        val max = times.maxOrNull() ?: 0
        
        CrashLogger.log(TAG, "📊 $operationName ($iterations iterations): avg=${avg.toInt()}ms, min=${min}ms, max=${max}ms")
    }
    
    /**
     * Track operation count and aggregate timing
     */
    private val operationStats = mutableMapOf<String, OperationStats>()
    
    data class OperationStats(
        var count: Int = 0,
        var totalDuration: Long = 0L,
        var minDuration: Long = Long.MAX_VALUE,
        var maxDuration: Long = 0L
    )
    
    /**
     * Record an operation execution
     */
    fun recordOperation(operationName: String, durationMs: Long) {
        val stats = operationStats.getOrPut(operationName) { OperationStats() }
        stats.count++
        stats.totalDuration += durationMs
        stats.minDuration = minOf(stats.minDuration, durationMs)
        stats.maxDuration = maxOf(stats.maxDuration, durationMs)
    }
    
    /**
     * Log all accumulated statistics
     */
    fun logStats() {
        if (operationStats.isEmpty()) {
            CrashLogger.log(TAG, "📊 No performance stats collected yet")
            return
        }
        
        CrashLogger.log(TAG, "📊 === PERFORMANCE STATISTICS ===")
        operationStats.entries.sortedByDescending { it.value.totalDuration }.forEach { (name, stats) ->
            val avg = stats.totalDuration / stats.count
            CrashLogger.log(TAG, "  $name: ${stats.count}x, avg=${avg}ms, min=${stats.minDuration}ms, max=${stats.maxDuration}ms, total=${stats.totalDuration}ms")
        }
    }
    
    /**
     * Clear all statistics
     */
    fun clearStats() {
        operationStats.clear()
        CrashLogger.log(TAG, "📊 Performance stats cleared")
    }
}

