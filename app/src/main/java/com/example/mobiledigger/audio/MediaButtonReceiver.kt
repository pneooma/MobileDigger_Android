package com.example.mobiledigger.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.mobiledigger.util.CrashLogger

class MediaButtonReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PLAY_PAUSE")
                        // Send broadcast to MusicService
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
                        CrashLogger.log("MediaButtonReceiver", "Media button: NEXT")
                        val nextIntent = Intent(MusicService.ACTION_NEXT)
                        context.sendBroadcast(nextIntent)
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        CrashLogger.log("MediaButtonReceiver", "Media button: PREVIOUS")
                        val prevIntent = Intent(MusicService.ACTION_PREVIOUS)
                        context.sendBroadcast(prevIntent)
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
