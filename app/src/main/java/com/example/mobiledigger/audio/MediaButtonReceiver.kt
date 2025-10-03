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
                        val playPauseIntent = Intent(MusicService.ACTION_PLAY_PAUSE)
                        context.sendBroadcast(playPauseIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PLAY")
                        val playIntent = Intent(MusicService.ACTION_PLAY_PAUSE)
                        context.sendBroadcast(playIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PAUSE")
                        val pauseIntent = Intent(MusicService.ACTION_PLAY_PAUSE)
                        context.sendBroadcast(pauseIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        // Defer handling to MediaSession (MusicService) for consistency
                        CrashLogger.log("MediaButtonReceiver", "Forwarding NEXT to MediaSession via player command")
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        // Defer handling to MediaSession (MusicService) for consistency
                        CrashLogger.log("MediaButtonReceiver", "Forwarding PREVIOUS to MediaSession via player command")
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: STOP")
                        val stopIntent = Intent("STOP")
                        context.sendBroadcast(stopIntent)
                    }
                }
            }
        }
    }
}
