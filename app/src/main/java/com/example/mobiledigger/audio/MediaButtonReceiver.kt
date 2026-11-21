package com.example.mobiledigger.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.mobiledigger.util.CrashLogger

class MediaButtonReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
        private const val PREFS_NAME = "headphone_button_times"
        private const val KEY_LAST_NEXT = "last_next_tap"
        private const val KEY_LAST_PREV = "last_prev_tap"
        private const val DOUBLE_TAP_WINDOW = 500L // 500ms
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PLAY_PAUSE")
                        val playPauseIntent = Intent(MusicService.ACTION_PLAY_PAUSE).apply { setPackage(context.packageName) }
                        context.sendBroadcast(playPauseIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PLAY")
                        val playIntent = Intent(MusicService.ACTION_PLAY).apply { setPackage(context.packageName) }
                        context.sendBroadcast(playIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PAUSE")
                        val pauseIntent = Intent(MusicService.ACTION_PAUSE).apply { setPackage(context.packageName) }
                        context.sendBroadcast(pauseIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        // Route to the real player (the ViewModel handles ACTION_NEXT),
                        // mirroring the PLAY_PAUSE path above. Previously this was a no-op,
                        // so hardware/headphone NEXT did nothing.
                        CrashLogger.log("MediaButtonReceiver", "Media button: NEXT")
                        context.sendBroadcast(Intent(MusicService.ACTION_NEXT).apply { setPackage(context.packageName) })
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PREVIOUS")
                        context.sendBroadcast(Intent(MusicService.ACTION_PREVIOUS).apply { setPackage(context.packageName) })
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        // Stop = pause (never a toggle, which could start the music)
                        CrashLogger.log("MediaButtonReceiver", "Media button: STOP")
                        context.sendBroadcast(Intent(MusicService.ACTION_PAUSE).apply { setPackage(context.packageName) })
                    }
                }
            }
        }
    }
}
