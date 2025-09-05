package com.example.mobiledigger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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
                "music_playback_channel",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(musicChannel)
        }
    }
}
