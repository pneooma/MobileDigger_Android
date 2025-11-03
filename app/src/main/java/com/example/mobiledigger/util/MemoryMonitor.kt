package com.example.mobiledigger.util
import com.example.mobiledigger.util.CrashLogger

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simple memory monitor that logs memory pressure
 * Phase 1: Safe improvements - basic monitoring only
 */
object MemoryMonitor {
    private const val TAG = "MemoryMonitor"
    private const val CHECK_INTERVAL_MS = 10_000L // Check every 10 seconds
    private const val HIGH_MEMORY_THRESHOLD = 0.85f // 85% memory usage is high
    private const val CRITICAL_MEMORY_THRESHOLD = 0.95f // 95% is critical
    
    private var isMonitoring = false
    
    /**
     * Start monitoring memory usage
     */
    fun startMonitoring(scope: CoroutineScope) {
        if (isMonitoring) {
            CrashLogger.log(TAG, "Memory monitoring already active")
            return
        }
        
        isMonitoring = true
        CrashLogger.log(TAG, "✅ Memory monitoring started")
        
        scope.launch(Dispatchers.Default) {
            while (isActive && isMonitoring) {
                try {
                    checkMemory()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    CrashLogger.log(TAG, "Error in memory monitoring", e)
                }
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        CrashLogger.log(TAG, "Memory monitoring stopped")
    }
    
    /**
     * Check current memory usage
     */
    private fun checkMemory() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val usageRatio = usedMemory.toFloat() / maxMemory.toFloat()
        val usedMB = usedMemory / (1024 * 1024)
        val maxMB = maxMemory / (1024 * 1024)
        
        when {
            usageRatio >= CRITICAL_MEMORY_THRESHOLD -> {
                CrashLogger.log(TAG, "🚨 CRITICAL MEMORY: ${usedMB}MB / ${maxMB}MB (${(usageRatio * 100).toInt()}%)")
                // Suggest GC
                System.gc()
            }
            usageRatio >= HIGH_MEMORY_THRESHOLD -> {
                CrashLogger.log(TAG, "⚠️ HIGH MEMORY: ${usedMB}MB / ${maxMB}MB (${(usageRatio * 100).toInt()}%)")
            }
            else -> {
                // Normal - don't log to reduce noise
            }
        }
    }
    
    /**
     * Force a memory check and log it
     */
    fun logMemoryStats() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val usedMB = usedMemory / (1024 * 1024)
        val maxMB = maxMemory / (1024 * 1024)
        val freeMB = (maxMemory - usedMemory) / (1024 * 1024)
        val usagePercent = ((usedMemory.toFloat() / maxMemory.toFloat()) * 100).toInt()
        
        CrashLogger.log(TAG, "📊 Memory: ${usedMB}MB used / ${maxMB}MB max (${usagePercent}%) - ${freeMB}MB free")
    }
}

