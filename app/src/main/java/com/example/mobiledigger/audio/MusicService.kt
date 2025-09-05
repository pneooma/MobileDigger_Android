package com.example.mobiledigger.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.mobiledigger.MainActivity
import com.example.mobiledigger.R
import com.example.mobiledigger.util.CrashLogger

@UnstableApi
class MusicService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var notificationManager: NotificationManager
    private var currentTitle: String = "MobileDigger"
    private var isPlaying: Boolean = false
    private var currentWaveform: FloatArray? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "com.example.mobiledigger.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mobiledigger.NEXT"
        const val ACTION_PREVIOUS = "com.example.mobiledigger.PREVIOUS"
        const val ACTION_LIKE = "com.example.mobiledigger.LIKE"
        const val ACTION_DISLIKE = "com.example.mobiledigger.DISLIKE"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.mobiledigger.UPDATE_NOTIFICATION"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    // Send broadcast to ViewModel to handle play/pause
                    sendBroadcast(Intent(ACTION_PLAY_PAUSE))
                }
                ACTION_NEXT -> {
                    sendBroadcast(Intent(ACTION_NEXT))
                }
                ACTION_PREVIOUS -> {
                    sendBroadcast(Intent(ACTION_PREVIOUS))
                }
                ACTION_LIKE -> {
                    sendBroadcast(Intent(ACTION_LIKE))
                }
                ACTION_DISLIKE -> {
                    sendBroadcast(Intent(ACTION_DISLIKE))
                }
                ACTION_UPDATE_NOTIFICATION -> {
                    currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "MobileDigger"
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    CrashLogger.log("MusicService", "Updating notification: $currentTitle, playing: $isPlaying")
                    updateNotification()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        
        // Acquire wake lock to prevent sleep during playback
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MobileDigger::MusicPlayback"
        )
        
        // Start foreground service immediately with notification
        createAndShowNotification()
        
        // Initialize player and media session
        try {
            // Configure RenderersFactory to prefer FFmpeg extension if available
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            player = ExoPlayer.Builder(this, renderersFactory)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
            
            // Add player listener for state changes
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (player?.playWhenReady == true) {
                                acquireWakeLock()
                            }
                        }
                        Player.STATE_ENDED -> {
                            releaseWakeLock()
                        }
                    }
                    updateNotification()
                }
                
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    isPlaying = playWhenReady && player?.playbackState == Player.STATE_READY
                    if (playWhenReady) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                    updateNotification()
                }
            })
            
            mediaSession = MediaSession.Builder(this, player!!).build()
            player?.prepare()
            
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to initialize player", e)
        }
        
        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_LIKE)
            addAction(ACTION_DISLIKE)
            addAction(ACTION_UPDATE_NOTIFICATION)
        }
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
    }
    
    private fun createAndShowNotification() {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            CrashLogger.log("MusicService", "Started foreground service with notification - Title: $currentTitle")
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to create notification", e)
            // Fallback simple notification
            val simpleNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MobileDigger")
                .setContentText("Music Player Active")
                .setSmallIcon(R.drawable.ic_music_note)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, simpleNotification)
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onDestroy() {
        releaseWakeLock()
        unregisterReceiver(notificationReceiver)
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
    
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10*60*1000L /*10 minutes*/)
                CrashLogger.log("MusicService", "Wake lock acquired")
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to acquire wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                CrashLogger.log("MusicService", "Wake lock released")
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to release wake lock", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MobileDigger Music Player",
                NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for better visibility
            ).apply {
                description = "Music playback controls with like/dislike sorting actions"
                setShowBadge(true)
                enableLights(false) // Disable lights to avoid issues
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            notificationManager.createNotificationChannel(channel)
            CrashLogger.log("MusicService", "Notification channel created successfully with HIGH importance")
        }
    }
    
    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).apply { setPackage(packageName) }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        // Create a modern, visually appealing notification with centered title
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText("Tap to open MobileDigger")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(createPendingIntent("STOP", 99))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(false)
            .setSilent(true)
            
        // Add waveform to notification if available
        try {
            val waveform = getCurrentWaveform()
            if (waveform != null) {
                val waveformText = createWaveformText(waveform, getCurrentPosition())
                notificationBuilder.setContentText(waveformText)
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error adding waveform to notification", e)
        }

        try {
            notificationBuilder
                .setColorized(true)
                .setColor(0xFF10B981.toInt())
        } catch (_: Exception) {}

        // Add media controls with cleaner text and better spacing
        notificationBuilder
            .addAction(
                R.drawable.ic_yes_pill,
                "LIKE",
                createPendingIntent(ACTION_LIKE, 1)
            )
            .addAction(
                playPauseIcon,
                playPauseText,
                createPendingIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                R.drawable.ic_no_pill,
                "SKIP",
                createPendingIntent(ACTION_DISLIKE, 3)
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "Prev",
                createPendingIntent(ACTION_PREVIOUS, 4)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                createPendingIntent(ACTION_NEXT, 5)
            )

        // Apply MediaStyle with better spacing and layout
        try {
            val mediaStyle = MediaNotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1, 0, 2) // Show play/pause, like, skip in compact view
                .setShowCancelButton(true)
                .setCancelButtonIntent(createPendingIntent("STOP", 99))

            mediaSession?.sessionCompatToken?.let { token ->
                mediaStyle.setMediaSession(token)
            }

            notificationBuilder.setStyle(mediaStyle)
        } catch (_: Exception) {}

        return notificationBuilder.build()
    }
    
    private fun updateNotification() {
        CrashLogger.log("MusicService", "updateNotification called - Title: $currentTitle, Playing: $isPlaying")
        
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun updateNotification(title: String, isPlaying: Boolean) {
        this.currentTitle = title
        this.isPlaying = isPlaying
        updateNotification()
    }
    
    fun updateNotificationWithWaveform(title: String, isPlaying: Boolean, waveform: FloatArray?) {
        this.currentTitle = title
        this.isPlaying = isPlaying
        this.currentWaveform = waveform
        updateNotification()
    }
    
    private fun getCurrentWaveform(): FloatArray? {
        return currentWaveform
    }
    
    private fun getCurrentPosition(): Long {
        return try {
            player?.currentPosition ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error getting current position", e)
            0L
        }
    }
    
    private fun createWaveformText(waveform: FloatArray, position: Long): String {
        return try {
            val duration = player?.duration ?: 1L
            val progress = if (duration > 0) (position.toFloat() / duration.toFloat()) else 0f
            val progressIndex = (progress * (waveform.size - 1)).toInt().coerceIn(0, waveform.size - 1)
            
            // Create a simple text representation of the waveform
            val amplitude = waveform[progressIndex]
            val barCount = (amplitude * 10).toInt().coerceIn(0, 10)
            val bars = "█".repeat(barCount) + "░".repeat(10 - barCount)
            
            "Waveform: $bars"
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error creating waveform text", e)
            "Waveform: ░░░░░░░░░░"
        }
    }
}