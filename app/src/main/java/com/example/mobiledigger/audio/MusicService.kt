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
import androidx.core.app.NotificationManagerCompat
import android.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.SessionCommands
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.media.session.MediaButtonReceiver
import android.content.ComponentName
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
    private var mediaSessionCompat: MediaSessionCompat? = null
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
    
    // Headphone double-tap detection state
    private var lastNextTapTime: Long = 0L
    private var lastPreviousTapTime: Long = 0L
    private val doubleTapWindowMs: Long = 500L
    
    companion object {
        // Align with VLC notification id usage
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "com.example.mobiledigger.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mobiledigger.NEXT"
        const val ACTION_PREVIOUS = "com.example.mobiledigger.PREVIOUS"
        const val ACTION_REWIND = "com.example.mobiledigger.REWIND"
        const val ACTION_FORWARD = "com.example.mobiledigger.FORWARD"
        const val ACTION_LIKE = "com.example.mobiledigger.LIKE"
        const val ACTION_DISLIKE = "com.example.mobiledigger.DISLIKE"
        const val ACTION_SPECTROGRAM = "com.example.mobiledigger.SPECTROGRAM"
        const val ACTION_SHARE = "com.example.mobiledigger.SHARE"
        const val ACTION_STOP = "com.example.mobiledigger.STOP"
        const val ACTION_CONFIRM_CLOSE = "com.example.mobiledigger.CONFIRM_CLOSE"
        const val ACTION_CANCEL_CLOSE = "com.example.mobiledigger.CANCEL_CLOSE"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.mobiledigger.UPDATE_NOTIFICATION"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_URI = "extra_uri"
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
                .remove(Player.COMMAND_STOP) // Remove stop command to hide system stop button
                .build()
            val sessionCommands = SessionCommands.Builder().build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        // Map headphone skip commands to like/dislike actions
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT -> {
                    CrashLogger.log("MusicServiceCallback", "HEADPHONE NEXT → map to DISLIKE")
                    try { sendBroadcast(Intent(ACTION_DISLIKE).apply { setPackage(packageName) }) } catch (_: Exception) {}
                    return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS -> {
                    CrashLogger.log("MusicServiceCallback", "HEADPHONE PREVIOUS → map to LIKE")
                    try { sendBroadcast(Intent(ACTION_LIKE).apply { setPackage(packageName) }) } catch (_: Exception) {}
                    return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
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
                    CrashLogger.log("MusicService", "Notification action: NEXT → move to next track")
                    sendBroadcast(Intent(ACTION_NEXT))
                }
                ACTION_PREVIOUS -> {
                    CrashLogger.log("MusicService", "Notification action: PREVIOUS → move to previous track")
                    sendBroadcast(Intent(ACTION_PREVIOUS))
                }
                ACTION_REWIND -> {
                    CrashLogger.log("MusicService", "Notification action: REWIND")
                    try {
                        val decrement = 10_000L
                        val newPos = (currentPosition - decrement).coerceAtLeast(0L)
                        mediaSessionCompat?.controller?.transportControls?.rewind()
                        audioManager?.seekTo(newPos)
                        currentPosition = newPos
                        updateNotification()
                    } catch (e: Exception) { CrashLogger.log("MusicService", "REWIND failed", e) }
                }
                ACTION_FORWARD -> {
                    CrashLogger.log("MusicService", "Notification action: FORWARD")
                    try {
                        val increment = 10_000L
                        val dur = if (duration > 0) duration else (audioManager?.getDuration() ?: 0L)
                        val newPos = (currentPosition + increment).coerceAtMost(dur)
                        mediaSessionCompat?.controller?.transportControls?.fastForward()
                        audioManager?.seekTo(newPos)
                        currentPosition = newPos
                        updateNotification()
                    } catch (e: Exception) { CrashLogger.log("MusicService", "FORWARD failed", e) }
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
                ACTION_STOP -> {
                    CrashLogger.log("MusicService", "Notification action: STOP")
                    try {
                        // If currently playing, ignore swipe-to-dismiss and keep foreground
                        val playingNow = (audioManager?.isCurrentlyPlaying() == true) || isPlaying || (player?.playWhenReady == true)
                        if (playingNow) {
                            CrashLogger.log("MusicService", "STOP ignored while playing - keeping notification")
                            updateNotification()
                            return
                        }
                        // Not playing: ask for confirmation to close
                        showCloseConfirmation()
                    } catch (e: Exception) { CrashLogger.log("MusicService", "STOP failed", e) }
                }
                ACTION_CONFIRM_CLOSE -> {
                    try {
                        audioManager?.pause()
                        isPlaying = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notificationManager.cancel(NOTIFICATION_ID)
                    } catch (e: Exception) { CrashLogger.log("MusicService", "CONFIRM_CLOSE failed", e) }
                }
                ACTION_CANCEL_CLOSE -> {
                    updateNotification()
                }
                ACTION_UPDATE_NOTIFICATION -> {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "MobileDigger"
                    val playing = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    val position = intent.getLongExtra(EXTRA_CURRENT_POSITION, 0L)
                    val dur = intent.getLongExtra(EXTRA_DURATION, 0L)
                    val uriStr = intent.getStringExtra(EXTRA_URI)
                    val incomingUri = try { if (uriStr.isNullOrEmpty()) null else Uri.parse(uriStr) } catch (_: Exception) { null }
                    updateNotificationData(title, playing, position, dur, incomingUri)
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

            // Initialize player without dummy items - let updateNotificationData set the actual media
            CrashLogger.log("MusicService", "Player initialized, waiting for actual media item")

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
                    
                    // Don't auto-skip for dummy playlist errors (initialization)
                    val currentMediaItem = player?.currentMediaItem
                    val isDummyItem = currentMediaItem?.mediaId?.startsWith("dummy") == true
                    
                    if (isDummyItem) {
                        CrashLogger.log("MusicService", "Error on dummy item, ignoring auto-skip")
                        return
                    }
                    
                    // Auto-skip to next track when ExoPlayer fails (e.g., file not found, unsupported format)
                    CrashLogger.log("MusicService", "ExoPlayer error detected, attempting to skip to next track")
                    // Post a delayed skip to allow error state to settle
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            // Send broadcast to trigger next track in ViewModel
                            sendBroadcast(Intent(ACTION_NEXT))
                            CrashLogger.log("MusicService", "Auto-skipped to next track after ExoPlayer error")
                        } catch (e: Exception) {
                            CrashLogger.log("MusicService", "Failed to auto-skip after error", e)
                        }
                    }, 500) // 500ms delay to avoid rapid cycling
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    CrashLogger.log("MusicService", "Player (MediaSession) isPlaying changed: $isPlaying")
                }
                
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    CrashLogger.log("MusicService", "Player (MediaSession) onMediaItemTransition reason: $reason")
                    // Detect if this was triggered by skip next/previous from headphones
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        // This might be from headphone buttons
                        CrashLogger.log("MusicService", "Media item transition from SEEK - possible headphone button")
                    }
                }
            })

            mediaSession = MediaSession.Builder(this, player!!)
                .setCallback(MusicServiceCallback())
                .build()
            CrashLogger.log("MusicService", "MediaSession created successfully")
            // player?.prepare() // Preparation happens in updateNotificationData when MediaItem is set

            // Initialize MediaSessionCompat like VLC for modern notification behavior
            try {
                val mbrName = ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java)
                val mbrIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
                mediaSessionCompat = MediaSessionCompat(this, "MobileDigger", mbrName, mbrIntent).apply {
                    setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                    setCallback(object : MediaSessionCompat.Callback() {
                        override fun onPlay() { audioManager?.resume(); player?.playWhenReady = true; isPlaying = true; updatePlaybackState(); sendNowPlayingUpdate(); updateNotification() }
                        override fun onPause() { audioManager?.pause(); player?.playWhenReady = false; isPlaying = false; updatePlaybackState(); sendNowPlayingUpdate(); updateNotification() }
                        override fun onStop() { audioManager?.pause(); isPlaying = false; updatePlaybackState() }
                        override fun onSkipToNext() { sendBroadcast(Intent(ACTION_NEXT).apply { setPackage(packageName) }) }
                        override fun onSkipToPrevious() { sendBroadcast(Intent(ACTION_PREVIOUS).apply { setPackage(packageName) }) }
                        override fun onFastForward() {
                            val inc = 10_000L
                            val newPos = (currentPosition + inc).coerceAtMost(audioManager?.getDuration() ?: duration)
                            audioManager?.seekTo(newPos); currentPosition = newPos; updatePlaybackState(); sendNowPlayingUpdate(); updateNotification()
                        }
                        override fun onRewind() {
                            val dec = 10_000L
                            val newPos = (currentPosition - dec).coerceAtLeast(0L)
                            audioManager?.seekTo(newPos); currentPosition = newPos; updatePlaybackState(); sendNowPlayingUpdate(); updateNotification()
                        }
                        override fun onSeekTo(pos: Long) {
                            audioManager?.seekTo(pos)
                            player?.seekTo(pos)
                            currentPosition = pos
                            updatePlaybackState(); sendNowPlayingUpdate(); updateNotification()
                        }
                    })
                    isActive = true
                }
                // Keep Media3 session active to satisfy MediaSessionService binding on newer Android
            } catch (e: Exception) {
                CrashLogger.log("MusicService", "Failed to init MediaSessionCompat", e)
            }

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
        super.onStartCommand(intent, flags, startId)
        // Forward media button intents to MediaSessionCompat like VLC
        try {
            if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                mediaSessionCompat?.let { MediaButtonReceiver.handleIntent(it, intent) }
            }
        } catch (_: Exception) {}
        // SAFETY: immediately enter foreground with a lightweight notification to avoid FGS timeout
        try {
            val bootstrapNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pig_headphones)
                .setContentTitle("MobileDigger")
                .setContentText("Preparing playback…")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, bootstrapNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, bootstrapNotification)
            }
        } catch (_: Exception) {}
        // Then post the full media notification
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
            val playingNow = (audioManager?.isCurrentlyPlaying() == true) || isPlaying || (player?.playWhenReady == true)
            if (playingNow) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    // Fallback
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                // Not playing: ensure service is not in full foreground but show notification
                try {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } catch (_: Exception) {}
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            CrashLogger.log("MusicService", "Notification shown. Foreground=$playingNow")
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to create or show notification", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        try {
            releaseWakeLock()
            stopProgressUpdates()
            unregisterReceiver(notificationReceiver)
            audioManager?.release()
            audioManager = null
            
            // Safely release media session to prevent DeadObjectException
            mediaSession?.run {
                try {
                    player?.release()
                } catch (e: Exception) {
                    CrashLogger.log("MusicService", "Error releasing player", e)
                }
                try {
                    release()
                } catch (e: Exception) {
                    CrashLogger.log("MusicService", "Error releasing media session", e)
                }
                mediaSession = null
            }
            player = null // Release ExoPlayer instance
            
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                CrashLogger.log("MusicService", "Error stopping foreground service", e)
            }
            
            try {
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                CrashLogger.log("MusicService", "Error canceling notification", e)
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error in onDestroy", e)
        }
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

        val prevPendingIntent = PendingIntent.getBroadcast(this, 1, Intent(ACTION_PREVIOUS).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val rewindPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)
        // Use MediaButtonReceiver for play/pause to ensure system transport toggles work
        val playPausePendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        val forwardPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)
        val nextPendingIntent = PendingIntent.getBroadcast(this, 3, Intent(ACTION_NEXT).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPendingIntent = PendingIntent.getBroadcast(this, 10, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val songTitle = currentFile?.name ?: "MobileDigger"
        val actualIsPlaying = (audioManager?.isCurrentlyPlaying() == true) || this.isPlaying || (player?.playWhenReady == true)
        val seekable = (this.duration > 0L) || ((audioManager?.getDuration() ?: 0L) > 0L)
        
        // Extract cover art from audio file
        val coverArt = extractCoverArt(currentFile)
        
        // VLC-style notification similar to their NotificationHelper
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pig_headphones)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(songTitle)
            .setContentText("MobileDigger Music Player")
            .setLargeIcon(coverArt)
            .setAutoCancel(!actualIsPlaying)
            .setOngoing(actualIsPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setColor(Color.BLACK)
            // Always show confirmation on swipe; keep ongoing while playing prevents removal
            .setDeleteIntent(stopPendingIntent)
            .setContentIntent(pendingIntent)

        // VLC action order: Previous, Rewind, Play/Pause, Forward, Next
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent) // 0
        builder.addAction(android.R.drawable.ic_media_rew, "Rewind", rewindPendingIntent) // 1
        val playIcon = if (actualIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        builder.addAction(playIcon, if (actualIsPlaying) "Pause" else "Play", playPausePendingIntent) // 2
        builder.addAction(android.R.drawable.ic_media_ff, "Forward", forwardPendingIntent) // 3
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent) // 4

        // Force compact to show 10s skip buttons like VLC: [rewind, play/pause, forward]
        val showActions = intArrayOf(1, 2, 3)
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(*showActions)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)
        mediaSessionCompat?.apply {
            setSessionActivity(pendingIntent)
            sessionToken?.let { mediaStyle.setMediaSession(it) }
        }
        builder.setStyle(mediaStyle)

        CrashLogger.log("MusicService", "Notification created (VLC-exact) - Title: $songTitle, Playing: $actualIsPlaying, Seekable: $seekable, Compact: ${showActions.contentToString()}")

        return builder.build()
    }

    internal fun updateNotification() { // Made internal for callback
        val notification = createNotification()
        val playingNow = audioManager?.isCurrentlyPlaying() == true || isPlaying
        if (playingNow) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            try { stopForeground(STOP_FOREGROUND_DETACH) } catch (_: Exception) {}
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
        CrashLogger.log("MusicService", "updateNotification - playing=$playingNow")
    }

    fun updateNotificationData(title: String, playing: Boolean, position: Long, dur: Long, uri: Uri? = null) {
        CrashLogger.log("MusicService", "updateNotificationData - Title: $title, Playing: $playing, Position: $position, Duration: $dur")
        val chosenUri = uri ?: currentFile?.uri ?: Uri.EMPTY
        currentFile = currentFile?.copy(name = title, duration = dur, uri = chosenUri) ?: MusicFile(
            uri = chosenUri,
            name = title,
            duration = dur,
            size = 0L
        )
        val effectiveDuration = if (dur > 0) dur else (audioManager?.getDuration() ?: dur)
        this.isPlaying = playing // Reflect intended state from AudioManager/ViewModel
        this.currentPosition = if (position > 0) position else (audioManager?.getCurrentPosition() ?: position)
        this.duration = effectiveDuration

        try {
            // Always create a media item for the notification, even without a valid URI
            // This ensures the notification shows the correct title and metadata
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("MobileDigger")
                .setAlbumTitle("Music Player")
                .setTrackNumber(1)
                .setTotalTrackCount(1)
            
            val extras = Bundle()
            if (effectiveDuration > 0) {
                extras.putLong(NOTIFICATION_DURATION_KEY, effectiveDuration)
            }
            metadataBuilder.setExtras(extras)

        // Only set a valid media item when we have a real URI; avoid placeholder URIs that break seeking
        val hasRealUri = currentFile?.uri != null && currentFile?.uri != Uri.EMPTY
        // Do not drive ExoPlayer when VLC backend is active; only publish metadata/state
        player?.playWhenReady = false

            CrashLogger.log("MusicService", "MediaSession player updated - Title: $title, Playing: $playing, Position: $position")

            // Update MediaSessionCompat metadata/state like VLC for system UI
            mediaSessionCompat?.let { msc ->
                val compatMeta = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "MobileDigger")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Music Player")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (effectiveDuration > 0) effectiveDuration else -1L)
                    .build()
                msc.setMetadata(compatMeta)
                updatePlaybackState()
            }

        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error updating MediaSession player in updateNotificationData", e)
        }
        updateNotification()
    }

    private fun updatePlaybackState() {
        mediaSessionCompat?.let { msc ->
            val actions = (PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_SEEK_TO)
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val position = currentPosition
            val buffered = currentPosition
            val now = android.os.SystemClock.elapsedRealtime()
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, if (isPlaying) 1.0f else 0.0f, now)
                .setBufferedPosition(buffered)
                .build()
            msc.setPlaybackState(playbackState)
        }
    }

    private fun showCloseConfirmation() {
        val confirmIntent = PendingIntent.getBroadcast(this, 11, Intent(ACTION_CONFIRM_CLOSE).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancelIntent = PendingIntent.getBroadcast(this, 12, Intent(ACTION_CANCEL_CLOSE).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pig_headphones)
            .setContentTitle("Close MobileDigger?")
            .setContentText("Playback is paused. Do you want to close the app?")
            .setAutoCancel(true)
            .addAction(R.drawable.ic_no_pill, "Close", confirmIntent)
            .addAction(R.drawable.ic_yes_pill, "Keep", cancelIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun sendNowPlayingUpdate() {
        try {
            val intent = Intent(ACTION_UPDATE_NOTIFICATION)
                .putExtra(EXTRA_TITLE, currentFile?.name ?: "MobileDigger")
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
                .putExtra(EXTRA_CURRENT_POSITION, audioManager?.getCurrentPosition() ?: currentPosition)
                .putExtra(EXTRA_DURATION, audioManager?.getDuration() ?: duration)
                .putExtra(EXTRA_URI, currentFile?.uri?.toString() ?: "")
            intent.`package` = packageName
            sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun togglePlayPause() {
        try {
            if (audioManager?.isCurrentlyPlaying() == true) {
                audioManager?.pause()
                this.isPlaying = false
            } else {
                audioManager?.resume()
                this.isPlaying = true
            }
            
            // Sync MediaSession player state
            player?.playWhenReady = this.isPlaying
            
            // Update notification immediately
            updateNotification()
            
            // Inform ViewModel
            sendBroadcast(Intent(ACTION_PLAY_PAUSE))
            
            CrashLogger.log("MusicService", "Toggle play/pause - isPlaying: $isPlaying")
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Error in togglePlayPause", e)
        }
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


                    // Publish playback state so system seekbar updates in real-time
                    updatePlaybackState()
                    updateNotification() // Refresh notification
                    progressUpdateHandler?.postDelayed(this, 1000)
                } else if (isPlaying) { // If AudioManager stopped but we thought we were playing
                    isPlaying = false
                    if (player?.playWhenReady == true) {
                         player?.playWhenReady = false
                    }
                    updatePlaybackState()
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
    
    private fun extractCoverArt(musicFile: MusicFile?): android.graphics.Bitmap? {
        if (musicFile?.uri == null || musicFile.uri == Uri.EMPTY) {
            CrashLogger.log("MusicService", "No URI for cover art extraction")
            return android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_pig_headphones)
        }
        
        return try {
            CrashLogger.log("MusicService", "Extracting cover art from: ${musicFile.uri}")
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(this, musicFile.uri)
            
            val embeddedPicture = retriever.getEmbeddedPicture()
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                retriever.release()
                CrashLogger.log("MusicService", "Successfully extracted cover art: ${bitmap?.width}x${bitmap?.height}")
                bitmap
            } else {
                retriever.release()
                CrashLogger.log("MusicService", "No embedded picture found, using fallback")
                // Fallback to app icon if no cover art found
                android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_pig_headphones)
            }
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to extract cover art: ${e.message}", e)
            // Fallback to app icon on error
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_pig_headphones)
        }
    }
}
