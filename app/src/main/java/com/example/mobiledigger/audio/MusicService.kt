package com.example.mobiledigger.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.SessionCommands
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.SessionResult
import com.example.mobiledigger.MainActivity
import com.example.mobiledigger.R
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.model.MusicFile
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    internal var player: ExoPlayer? = null // Made internal for callback access
    private lateinit var notificationManager: NotificationManager
    private var currentFile: MusicFile? = null
    internal var isPlaying: Boolean = false // Made internal
    internal var currentPosition: Long = 0L // Made internal
    internal var duration: Long = 0L // Made internal
    private var wakeLock: PowerManager.WakeLock? = null
    internal var audioManager: AudioManager? = null // Made internal
    private var progressUpdateHandler: Handler? = null
    private var progressUpdateRunnable: Runnable? = null

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "com.example.mobiledigger.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mobiledigger.NEXT"
        const val ACTION_PREVIOUS = "com.example.mobiledigger.PREVIOUS"
        const val ACTION_LIKE = "com.example.mobiledigger.LIKE"
        const val ACTION_DISLIKE = "com.example.mobiledigger.DISLIKE"
        const val ACTION_SPECTROGRAM = "com.example.mobiledigger.SPECTROGRAM"
        const val ACTION_SHARE = "com.example.mobiledigger.SHARE"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.mobiledigger.UPDATE_NOTIFICATION"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_DURATION = "extra_duration"
        const val NOTIFICATION_DURATION_KEY = "NOTIFICATION_DURATION_KEY"
    }

    // Inner class for MediaSession callback
    private inner class MusicServiceCallback : MediaSession.Callback {

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            CrashLogger.log("MusicServiceCallback", "onPlaybackResumption called")
            // Attempt to resume playback with currentFile if available
            currentFile?.let {
                val mediaItem = MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.name)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(it.name).build())
                    .build()
                this@MusicService.player?.setMediaItem(mediaItem, currentPosition)
                this@MusicService.player?.prepare()
                this@MusicService.player?.playWhenReady = true
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(mediaItem), 0, currentPosition))
            }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()
            val sessionCommands = SessionCommands.Builder().build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    CrashLogger.log("MusicService", "Notification action: PLAY_PAUSE")
                    togglePlayPause()
                }
                ACTION_NEXT -> {
                    CrashLogger.log("MusicService", "Notification action: NEXT")
                    sendBroadcast(Intent(ACTION_NEXT))
                }
                ACTION_PREVIOUS -> {
                    CrashLogger.log("MusicService", "Notification action: PREVIOUS")
                    sendBroadcast(Intent(ACTION_PREVIOUS))
                }
                ACTION_LIKE -> {
                    CrashLogger.log("MusicService", "Notification action: LIKE")
                    sendBroadcast(Intent(ACTION_LIKE))
                }
                ACTION_DISLIKE -> {
                    CrashLogger.log("MusicService", "Notification action: DISLIKE")
                    sendBroadcast(Intent(ACTION_DISLIKE))
                }
                ACTION_SPECTROGRAM -> {
                    CrashLogger.log("MusicService", "Notification action: SPECTROGRAM")
                    sendBroadcast(Intent(ACTION_SPECTROGRAM))
                }
                ACTION_SHARE -> {
                    CrashLogger.log("MusicService", "Notification action: SHARE")
                    sendBroadcast(Intent(ACTION_SHARE))
                }
                ACTION_UPDATE_NOTIFICATION -> {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "MobileDigger"
                    val playing = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    val position = intent.getLongExtra(EXTRA_CURRENT_POSITION, 0L)
                    val dur = intent.getLongExtra(EXTRA_DURATION, 0L)
                    updateNotificationData(title, playing, position, dur)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        audioManager = AudioManager(this)
        audioManager?.initialize()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MobileDigger::MusicPlayback"
        )

        progressUpdateHandler = Handler(Looper.getMainLooper())

        try {
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            player = ExoPlayer.Builder(this, renderersFactory)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    CrashLogger.log("MusicService", "Player (MediaSession) onPlaybackStateChanged: $playbackState, Duration: ${this@MusicService.player?.duration}, Position: ${this@MusicService.player?.currentPosition}")
                    if (playbackState == Player.STATE_READY) {
                        if (this@MusicService.duration > 0 && this@MusicService.currentPosition >= 0 && this@MusicService.currentPosition <= this@MusicService.duration) {
                             // Only seek if we have a valid stored position and the player is ready
                            this@MusicService.player?.seekTo(this@MusicService.currentPosition)
                            CrashLogger.log("MusicService", "Player (MediaSession) sought to ${this@MusicService.currentPosition} on STATE_READY")
                        }
                        if (this@MusicService.player?.playWhenReady == true) {
                            acquireWakeLock()
                           // startProgressUpdates() // AudioManager's progress is leading
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        releaseWakeLock()
                       // stopProgressUpdates()
                    }
                    updateNotification()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    CrashLogger.log("MusicService", "Player (MediaSession) onPlayWhenReadyChanged: $playWhenReady")
                    // Sync ExoPlayer state with AudioManager
                    if (playWhenReady) {
                        audioManager?.resume()
                        this@MusicService.isPlaying = true
                    } else {
                        audioManager?.pause()
                        this@MusicService.isPlaying = false
                    }
                    
                    if (playWhenReady && player?.playbackState == Player.STATE_READY) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                    updateNotification()
                }

                 override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    CrashLogger.log("MusicService", "Player (MediaSession) Error: ${error.message}", error)
                }
            })

            mediaSession = MediaSession.Builder(this, player!!)
                .setCallback(MusicServiceCallback())
                .build()
            
            CrashLogger.log("MusicService", "MediaSession created successfully")
            // player?.prepare() // Preparation happens in updateNotificationData when MediaItem is set

        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to initialize player or media session", e)
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_LIKE)
            addAction(ACTION_DISLIKE)
            addAction(ACTION_SPECTROGRAM)
            addAction(ACTION_SHARE)
            addAction(ACTION_UPDATE_NOTIFICATION)
        }
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        startProgressUpdates() // Start progress updates linked to AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createAndShowNotification()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MobileDigger Music Player",
                NotificationManager.IMPORTANCE_LOW 
            ).apply {
                description = "Music playback controls with like/dislike sorting actions"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createAndShowNotification() {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            CrashLogger.log("MusicService", "Started foreground service with notification")
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to create or show notification", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopProgressUpdates()
        unregisterReceiver(notificationReceiver)
        audioManager?.release()
        audioManager = null
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        player = null // Release ExoPlayer instance
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
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

    internal fun createNotification(): Notification { // Made internal for callback
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("from_notification", true)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(ACTION_PREVIOUS).apply { setPackage(packageName) }
        val prevPendingIntent = PendingIntent.getBroadcast(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val playPauseIntent = Intent(ACTION_PLAY_PAUSE).apply { setPackage(packageName) }
        val playPausePendingIntent = PendingIntent.getBroadcast(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nextIntent = Intent(ACTION_NEXT).apply { setPackage(packageName) }
        val nextPendingIntent = PendingIntent.getBroadcast(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val likeIntent = Intent(ACTION_LIKE).apply { setPackage(packageName) }
        val likePendingIntent = PendingIntent.getBroadcast(this, 4, likeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val dislikeIntent = Intent(ACTION_DISLIKE).apply { setPackage(packageName) }
        val dislikePendingIntent = PendingIntent.getBroadcast(this, 5, dislikeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val spectrogramIntent = Intent(ACTION_SPECTROGRAM).apply { setPackage(packageName) }
        val spectrogramPendingIntent = PendingIntent.getBroadcast(this, 6, spectrogramIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val shareIntent = Intent(ACTION_SHARE).apply { setPackage(packageName) }
        val sharePendingIntent = PendingIntent.getBroadcast(this, 7, shareIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val songTitle = currentFile?.name ?: "MobileDigger"
        val songInfo = "Music Player"
        val actualIsPlaying = audioManager?.isCurrentlyPlaying() ?: this.isPlaying
        val playPauseIcon = if (actualIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(songTitle)
            .setContentText(songInfo)
            .setSmallIcon(R.drawable.ic_pig_headphones)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(false)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (actualIsPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_yes_pill, "Like", likePendingIntent)
            .addAction(R.drawable.ic_no_pill, "Dislike", dislikePendingIntent)
            .addAction(R.drawable.ic_pig_headphones, "Spectrogram", spectrogramPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2, 3, 4, 5, 6)
                .setShowCancelButton(true)
            )
            
        return notificationBuilder.build()
    }

    internal fun updateNotification() { // Made internal for callback
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateNotificationData(title: String, playing: Boolean, position: Long, dur: Long) {
        CrashLogger.log("MusicService", "updateNotificationData - Title: $title, Playing: $playing, Position: $position, Duration: $dur")
        currentFile = currentFile?.copy(name = title, duration = dur) ?: MusicFile(
            uri = Uri.EMPTY, // Placeholder, AudioManager handles actual URI
            name = title,
            duration = dur,
            size = 0L
        )
        this.isPlaying = playing // Reflect intended state from AudioManager/ViewModel
        this.currentPosition = position
        this.duration = dur

        try {
            val mediaItemUri = currentFile?.uri ?: Uri.parse("file:///android_asset/placeholder.mp3") // Use a placeholder if no URI

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("MobileDigger") // Placeholder
                .setAlbumTitle("Playback")  // Placeholder
                .setTrackNumber(1)          // Placeholder
                .setTotalTrackCount(1)      // Placeholder
            
            val extras = Bundle()
            if (dur > 0) {
                extras.putLong(NOTIFICATION_DURATION_KEY, dur) // For MediaStyle to pick up duration
            }
            metadataBuilder.setExtras(extras)

            val mediaItem = MediaItem.Builder()
                .setMediaId(title) // Use title as a unique ID for the item
                .setUri(mediaItemUri) // URI for the MediaItem
                .setMediaMetadata(metadataBuilder.build())
                .build()

            player?.setMediaItem(mediaItem, position) // Set item and start position
            player?.prepare()
            player?.playWhenReady = playing // Sync play/pause state

            CrashLogger.log("MusicService", "MediaSession player updated - URI: $mediaItemUri, Title: $title, Duration in Meta: ${metadataBuilder.build().extras?.getLong(NOTIFICATION_DURATION_KEY)}")

        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error updating MediaSession player in updateNotificationData", e)
        }
        updateNotification()
    }

    private fun togglePlayPause() {
        if (audioManager?.isCurrentlyPlaying() == true) {
            audioManager?.pause()
        } else {
            audioManager?.resume()
        }
        // Update state based on actual AudioManager state for UI consistency
        this.isPlaying = audioManager?.isCurrentlyPlaying() ?: false
        player?.playWhenReady = this.isPlaying // Sync MediaSession player
        updateNotification()
        sendBroadcast(Intent(ACTION_PLAY_PAUSE)) // Inform ViewModel
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (audioManager?.isCurrentlyPlaying() == true) { // Base progress on AudioManager
                    val amPosition = audioManager?.getCurrentPosition() ?: 0L
                    val amDuration = audioManager?.getDuration() ?: 0L

                    // Update MusicService's state which updateNotificationData might use
                    currentPosition = amPosition
                    duration = amDuration
                    isPlaying = true


                    // No need to directly seek this.player here, updateNotificationData handles its state.
                    // Let MediaSession pick up position from its player during its own updates if configured.
                    // However, we ensure its isPlaying state is correct.
                    if (player?.playWhenReady != true) {
                        player?.playWhenReady = true
                    }


                    updateNotification() // Refresh notification with current progress from AudioManager
                    progressUpdateHandler?.postDelayed(this, 1000)
                } else if (isPlaying) { // If AudioManager stopped but we thought we were playing
                    isPlaying = false
                    if (player?.playWhenReady == true) {
                         player?.playWhenReady = false
                    }
                    updateNotification()
                }
            }
        }
        progressUpdateHandler?.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { runnable ->
            progressUpdateHandler?.removeCallbacks(runnable)
        }
        progressUpdateRunnable = null
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
