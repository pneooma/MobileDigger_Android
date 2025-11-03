package com.example.mobiledigger.util

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * Phase 3: Automatic cache cleanup on low memory warnings
 * Responds to system memory pressure events
 */
class LowMemoryHandler(private val application: Application) : ComponentCallbacks2 {
    
    companion object {
        private const val TAG = "LowMemoryHandler"
    }
    
    private var onLowMemoryCallback: (() -> Unit)? = null
    private var onTrimMemoryCallback: ((Int) -> Unit)? = null
    
    /**
     * Register callbacks for memory events
     */
    fun setCallbacks(
        onLowMemory: (() -> Unit)? = null,
        onTrimMemory: ((Int) -> Unit)? = null
    ) {
        this.onLowMemoryCallback = onLowMemory
        this.onTrimMemoryCallback = onTrimMemory
    }
    
    /**
     * Called when the system is running low on memory
     */
    override fun onLowMemory() {
        CrashLogger.log(TAG, "🚨 LOW MEMORY WARNING - performing emergency cleanup")
        MemoryMonitor.logMemoryStats()
        
        // Trigger garbage collection
        System.gc()
        
        // Notify callback
        onLowMemoryCallback?.invoke()
        
        CrashLogger.log(TAG, "Emergency cleanup completed")
    }
    
    /**
     * Called when the system requests trimming memory
     */
    override fun onTrimMemory(level: Int) {
        val levelName = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
        
        CrashLogger.log(TAG, "⚠️ TRIM MEMORY REQUEST: $levelName (level=$level)")
        MemoryMonitor.logMemoryStats()
        
        // Aggressive cleanup for critical levels
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            CrashLogger.log(TAG, "Performing aggressive memory trim")
            System.gc()
        }
        
        // Notify callback with level
        onTrimMemoryCallback?.invoke(level)
    }
    
    /**
     * Configuration changed (unused but required)
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Not needed for memory handling
    }
    
    /**
     * Register this handler with the application
     */
    fun register() {
        application.registerComponentCallbacks(this)
        CrashLogger.log(TAG, "✅ Low memory handler registered")
    }
    
    /**
     * Unregister this handler
     */
    fun unregister() {
        application.unregisterComponentCallbacks(this)
        CrashLogger.log(TAG, "Low memory handler unregistered")
    }
}

