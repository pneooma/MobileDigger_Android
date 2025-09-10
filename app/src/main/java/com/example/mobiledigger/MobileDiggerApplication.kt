package com.example.mobiledigger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.util.CrashLogger

class MobileDiggerApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannels()
        // Initialize crash logging immediately with no destination yet (internal + Downloads)
        CrashLogger.setDestinationFolder(this, null)
        CrashLogger.log("App", "Application started")
        // Global uncaught exception handler to capture crashes
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                CrashLogger.log("Uncaught", "Thread ${t?.name} crashed", e)
            } finally {
                // Let default handler proceed
                val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                defaultHandler?.uncaughtException(t, e)
            }
        }
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val musicChannel = NotificationChannel(
                MusicService.CHANNEL_ID, // Use the same ID as MusicService
                "MobileDigger Music Player",
                NotificationManager.IMPORTANCE_LOW // Use LOW for media notifications
            ).apply {
                description = "Music playback controls with like/dislike sorting actions"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
                // Enable media controls
                setAllowBubbles(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(musicChannel)
            CrashLogger.log("MobileDiggerApplication", "Notification channel created successfully with ID: ${MusicService.CHANNEL_ID}")
        }
    }
}
