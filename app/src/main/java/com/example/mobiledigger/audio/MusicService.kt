package com.example.mobiledigger.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.mobiledigger.MainActivity
import com.example.mobiledigger.R
import com.example.mobiledigger.util.CrashLogger
import java.util.concurrent.Executors

/**
 * Shows the media notification / lock-screen / Quick Settings player and forwards its buttons
 * to the ViewModel, which owns the real player. This service plays nothing itself.
 *
 * It has exactly ONE media session (MediaSessionCompat). It used to also run an empty Media3
 * session + ExoPlayer: Android saw two players from the app, and the empty one could take the
 * headphone buttons (where "next" was mapped to Reject) or pull the notification down.
 *
 * It is also a media BROWSER service: the car (e.g. Tesla over Bluetooth/AVRCP) can scroll the
 * current playlist ("Now playing" queue) and browse To Do / Liked / Rejected, and picking a track
 * plays it in the app.
 */
class MusicService : MediaBrowserServiceCompat() {

    private lateinit var notificationManager: NotificationManager
    private var session: MediaSessionCompat? = null
    private val main = Handler(Looper.getMainLooper())
    private val artLoader = Executors.newSingleThreadExecutor { r -> Thread(r, "NotificationArt").apply { isDaemon = true } }

    // What the notification shows (pushed by the ViewModel)
    private var title: String = "MobileDigger"
    private var uri: Uri? = null
    private var isPlaying = false
    private var position = 0L
    private var duration = 0L

    @Volatile private var artUri: Uri? = null
    private var art: Bitmap? = null
    private val defaultArt: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.ic_pig_headphones) }.getOrNull()
    }

    /** False after the user swiped the paused notification away; the next real update brings it back. */
    private var dismissed = false

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "com.example.mobiledigger.PLAY_PAUSE"
        /** Explicit play / pause (Bluetooth, car, lock screen) — never toggle these. */
        const val ACTION_PLAY = "com.example.mobiledigger.PLAY"
        const val ACTION_PAUSE = "com.example.mobiledigger.PAUSE"
        const val ACTION_NEXT = "com.example.mobiledigger.NEXT"
        const val ACTION_PREVIOUS = "com.example.mobiledigger.PREVIOUS"
        const val ACTION_LIKE = "com.example.mobiledigger.LIKE"
        const val ACTION_DISLIKE = "com.example.mobiledigger.DISLIKE"
        const val ACTION_SPECTROGRAM = "com.example.mobiledigger.SPECTROGRAM"
        const val ACTION_SHARE = "com.example.mobiledigger.SHARE"
        const val ACTION_STOP = "com.example.mobiledigger.STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.mobiledigger.UPDATE_NOTIFICATION"
        /** Updates only the seek position (no re-post, so no flicker). */
        const val ACTION_UPDATE_POSITION = "com.example.mobiledigger.UPDATE_POSITION"
        const val ACTION_SEEK = "com.example.mobiledigger.ACTION_SEEK"
        /** Play a specific track picked in the car: extras [EXTRA_TAB] + [EXTRA_URI]. */
        const val ACTION_PLAY_ITEM = "com.example.mobiledigger.PLAY_ITEM"
        const val EXTRA_TAB = "extra_tab"
        private const val ROOT_ID = "root"
        /** Most tracks handed to the car per list (keeps Bluetooth browsing quick). */
        private const val MAX_ITEMS = 500
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_URI = "extra_uri"

        /** True once the app started the service, so the ViewModel doesn't restart it on every update. */
        @Volatile var isRunning = false
            private set

        /** "Artist - Title.wav" → ("Title", "Artist"); otherwise (name, null). */
        fun splitTrackName(fileName: String): Pair<String, String?> {
            val base = fileName.substringBeforeLast('.', fileName).trim()
            val i = base.indexOf(" - ")
            return if (i > 0 && i + 3 < base.length) base.substring(i + 3).trim() to base.substring(0, i).trim()
            else base to null
        }
    }

    private fun send(action: String, extra: (Intent.() -> Unit)? = null) {
        try {
            sendBroadcast(Intent(action).apply { setPackage(packageName); extra?.invoke(this) })
        } catch (e: Exception) { CrashLogger.log("MusicService", "broadcast $action failed", e) }
    }

    private fun seekBy(deltaMs: Long) = seekTo(position + deltaMs)

    private fun seekTo(target: Long) {
        val pos = if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
        position = pos
        updatePlaybackState()
        send(ACTION_SEEK) { putExtra("position", pos) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_NOTIFICATION -> {
                    val newUri = intent.getStringExtra(EXTRA_URI)?.takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    update(
                        intent.getStringExtra(EXTRA_TITLE) ?: title,
                        intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying),
                        intent.getLongExtra(EXTRA_CURRENT_POSITION, position),
                        intent.getLongExtra(EXTRA_DURATION, 0L),
                        newUri
                    )
                }
                ACTION_UPDATE_POSITION -> {
                    val p = intent.getLongExtra(EXTRA_CURRENT_POSITION, -1L)
                    val d = intent.getLongExtra(EXTRA_DURATION, 0L)
                    if (p >= 0) position = p
                    if (d > 0 && d != duration) { duration = d; publishMetadata() }
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                    updatePlaybackState()
                }
                ACTION_STOP -> {
                    // Notification swiped away. Only possible while paused (it's ongoing while
                    // playing). Just let it go; it comes back with the next track or Play.
                    if (isPlaying) { postNotification(); return }  // Android 14 lets FGS notes be swiped
                    dismissed = true
                    main.removeCallbacks(detachWhenStillPaused)
                    stopForegroundCompat(remove = true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        try {
            val mbr = ComponentName(this, MediaButtonReceiver::class.java)
            val mbrIntent = PendingIntent.getBroadcast(
                this, 20, Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(mbr), PendingIntent.FLAG_IMMUTABLE
            )
            session = MediaSessionCompat(this, "MobileDigger", mbr, mbrIntent).apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() = send(ACTION_PLAY)
                    override fun onPause() = send(ACTION_PAUSE)
                    // Stop (e.g. dismissing the player in Quick Settings) must never START music —
                    // it used to be a toggle.
                    override fun onStop() = send(ACTION_PAUSE)
                    override fun onSkipToNext() = send(ACTION_NEXT)
                    override fun onSkipToPrevious() = send(ACTION_PREVIOUS)
                    override fun onFastForward() = seekBy(10_000L)
                    override fun onRewind() = seekBy(-10_000L)
                    override fun onSeekTo(pos: Long) = seekTo(pos)
                    override fun onSkipToQueueItem(id: Long) {
                        val uri = queueUris.getOrNull(id.toInt()) ?: return
                        playItem(queueTab, uri)
                    }
                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        val (tab, uri) = mediaId?.let { MediaLibrary.parse(it) } ?: return
                        playItem(tab, uri)
                    }
                    override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                        when (action) {
                            ACTION_LIKE -> send(ACTION_LIKE)
                            ACTION_DISLIKE -> send(ACTION_DISLIKE)
                        }
                    }
                })
                setSessionActivity(contentIntent())
                isActive = true
            }
            sessionToken = session?.sessionToken
        } catch (e: Exception) {
            CrashLogger.log("MusicService", "Failed to create media session", e)
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE_NOTIFICATION)
            addAction(ACTION_UPDATE_POSITION)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        publishMetadata()
        updatePlaybackState()
        MediaLibrary.listener = { main.post { onLibraryChanged() } }
        onLibraryChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true  // only once STARTED by the app (the car can bind it on its own)
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            session?.let { runCatching { androidx.media.session.MediaButtonReceiver.handleIntent(it, intent) } }
        }
        // Every startForegroundService() must be answered by startForeground(), or Android
        // kills the app. Then drop back to a plain notification if we're paused.
        val n = buildNotification()
        if (!startForegroundSafe(n)) runCatching { notificationManager.notify(NOTIFICATION_ID, n) }
        if (!isPlaying && inForeground) main.postDelayed(detachWhenStillPaused, 5_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        MediaLibrary.listener = null
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        runCatching { session?.isActive = false; session?.release() }
        session = null
        stopForegroundCompat(remove = true)
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        artLoader.shutdownNow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MobileDigger Music Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls with like/dislike sorting actions"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ---- State updates from the ViewModel -------------------------------------------------

    fun update(newTitle: String, playing: Boolean, pos: Long, dur: Long, newUri: Uri?) {
        val trackChanged = newTitle != title || (newUri != null && newUri != uri)
        title = newTitle
        if (newUri != null) uri = newUri
        isPlaying = playing
        if (pos >= 0) position = pos
        if (trackChanged) duration = 0L
        if (dur > 0) duration = dur
        if (playing || trackChanged) dismissed = false
        publishMetadata()
        updatePlaybackState()
        postNotification()
        loadArtIfNeeded()
    }

    private fun publishMetadata() {
        val s = session ?: return
        val (t, artist) = splitTrackName(title)
        val b = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, t)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist ?: "MobileDigger")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist ?: "MobileDigger")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (duration > 0) duration else -1L)
        currentArt()?.let { b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        runCatching { s.setMetadata(b.build()) }
    }

    private fun updatePlaybackState() {
        val s = session ?: return
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        val pos = if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
        val state = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setActiveQueueItemId(activeQueueId())
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                pos, if (isPlaying) 1f else 0f, SystemClock.elapsedRealtime()
            )
            // Like / Dislike in the Android 13+ system player (not standard session actions)
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_LIKE, "Like", R.drawable.ic_yes_pill).build())
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_DISLIKE, "Dislike", R.drawable.ic_no_pill).build())
            .build()
        runCatching { s.setPlaybackState(state) }
    }

    // ---- Notification ------------------------------------------------------------------------

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("from_notification", true)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(code: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(this, code, Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(): Notification {
        val (t, artist) = splitTrackName(title)
        val playing = isPlaying
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pig_headphones)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(t)
            .setContentText(artist ?: "MobileDigger")
            .setLargeIcon(currentArt())
            // Never auto-cancel: tapping the notification to open the app used to remove the
            // player while paused.
            .setAutoCancel(false)
            .setOngoing(playing)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setColor(Color.BLACK)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDeleteIntent(actionIntent(10, ACTION_STOP))
            .setContentIntent(contentIntent())

        // Previous, Like, Play/Pause, Dislike, Next. Play and Pause are explicit (not a toggle),
        // so a stale icon can never do the opposite of what it shows.
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", actionIntent(1, ACTION_PREVIOUS))
        builder.addAction(android.R.drawable.btn_star_big_on, "Like", actionIntent(6, ACTION_LIKE))
        if (playing) builder.addAction(android.R.drawable.ic_media_pause, "Pause", actionIntent(2, ACTION_PAUSE))
        else builder.addAction(android.R.drawable.ic_media_play, "Play", actionIntent(4, ACTION_PLAY))
        builder.addAction(android.R.drawable.ic_delete, "Dislike", actionIntent(7, ACTION_DISLIKE))
        builder.addAction(android.R.drawable.ic_media_next, "Next", actionIntent(3, ACTION_NEXT))

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)
        session?.sessionToken?.let { style.setMediaSession(it) }
        builder.setStyle(style)
        return builder.build()
    }

    private var inForeground = false
    // A pause during a track change lasts a few hundred ms. Dropping foreground for it meant
    // the next startForeground() came from the background, which Android 12+ refuses — the app
    // then lost its foreground status and could be killed mid-song with the screen off.
    private val detachWhenStillPaused = Runnable {
        if (!isPlaying) stopForegroundCompat(remove = false)
    }

    private fun postNotification() {
        if (dismissed) return
        val n = try { buildNotification() } catch (e: Exception) {
            CrashLogger.log("MusicService", "buildNotification failed", e); return
        }
        main.removeCallbacks(detachWhenStillPaused)
        if (isPlaying) {
            if (!startForegroundSafe(n)) runCatching { notificationManager.notify(NOTIFICATION_ID, n) }
        } else {
            runCatching { notificationManager.notify(NOTIFICATION_ID, n) }
            // Paused for real → after a moment the notification becomes swipeable
            if (inForeground) main.postDelayed(detachWhenStillPaused, 5_000L)
        }
    }

    /** startForeground that never throws (Android 12+ refuses it from the background). */
    private fun startForegroundSafe(n: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        inForeground = true
        true
    } catch (e: Exception) {
        CrashLogger.log("MusicService", "startForeground refused: ${e.message}")
        false
    }

    private fun stopForegroundCompat(remove: Boolean) {
        inForeground = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(if (remove) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION") stopForeground(remove)
            }
        }
    }

    // ---- Cover art (loaded off the main thread; network tracks used to freeze the UI) -----

    private fun currentArt(): Bitmap? = if (art != null && artUri == uri) art else defaultArt

    private fun loadArtIfNeeded() {
        val target = uri ?: return
        if (target == artUri || target == Uri.EMPTY) return
        artUri = target
        art = null
        val name = title
        artLoader.execute {
            val bmp = try {
                val r = android.media.MediaMetadataRetriever()
                try {
                    if (target.scheme == "http" || target.scheme == "https") r.setDataSource(target.toString(), HashMap())
                    else r.setDataSource(this, target)
                    r.embeddedPicture?.takeIf { it.isNotEmpty() }?.let { bytes ->
                        // Downscale big covers; the notification only needs ~256 px
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        var sample = 1
                        while (opts.outWidth / (sample * 2) >= 256 && opts.outHeight / (sample * 2) >= 256) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                } finally { runCatching { r.release() } }
            } catch (e: Exception) {
                CrashLogger.log("MusicService", "No cover art: ${e.message}")
                null
            }
            // No embedded cover → the track's waveform. Reuse the app's (it computes one for
            // the playing row); if it hasn't appeared after a few seconds, compute our own.
            val art = bmp ?: waveformArt(target, name)
            main.post {
                if (artUri == target && art != null) {
                    this.art = art
                    publishMetadata()
                    postNotification()
                }
            }
        }
    }

    // ---- Car / Bluetooth browsing ---------------------------------------------------------

    /** Queue item id = index in [queueUris]. */
    private var queueUris: List<String> = emptyList()
    private var queueTab: String = MediaLibrary.TODO

    private fun playItem(tab: String, uri: String) =
        send(ACTION_PLAY_ITEM) { putExtra(EXTRA_TAB, tab); putExtra(EXTRA_URI, uri) }

    private fun activeQueueId(): Long {
        val cur = uri?.toString() ?: MediaLibrary.currentUri ?: return MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        val i = queueUris.indexOf(cur)
        return if (i >= 0) i.toLong() else MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
    }

    private fun description(tab: String, t: MediaLibrary.Track): MediaDescriptionCompat {
        val (name, artist) = splitTrackName(t.name)
        return MediaDescriptionCompat.Builder()
            .setMediaId(MediaLibrary.mediaId(tab, t.uri))
            .setTitle(name)
            .setSubtitle(artist ?: "")
            .build()
    }

    /** Window of at most [MAX_ITEMS] around the playing track, so the car opens on it. */
    private fun window(list: List<MediaLibrary.Track>, current: String?): List<MediaLibrary.Track> {
        if (list.size <= MAX_ITEMS) return list
        val i = list.indexOfFirst { it.uri == current }.coerceAtLeast(0)
        val from = (i - 50).coerceIn(0, list.size - MAX_ITEMS)
        return list.subList(from, from + MAX_ITEMS)
    }

    private fun onLibraryChanged() {
        val s = session ?: return
        val tab = MediaLibrary.currentTab
        val items = window(MediaLibrary.lists[tab].orEmpty(), MediaLibrary.currentUri)
        queueTab = tab
        queueUris = items.map { it.uri }
        runCatching {
            s.setQueue(items.mapIndexed { i, t -> MediaSessionCompat.QueueItem(description(tab, t), i.toLong()) })
            s.setQueueTitle(MediaLibrary.TABS.firstOrNull { it.first == tab }?.second ?: "Playlist")
        }
        updatePlaybackState()
        runCatching {
            notifyChildrenChanged(ROOT_ID)
            MediaLibrary.TABS.forEach { notifyChildrenChanged(it.first) }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Not for "resume after the app was closed" in Quick Settings: the player lives in the
        // app, so a resumed session would show a Play button that can't do anything.
        if (rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) return null
        val trusted = clientUid == android.os.Process.myUid() ||
            clientUid == android.os.Process.SYSTEM_UID ||
            clientUid == 1002 /* Process.BLUETOOTH_UID */ ||
            clientPackageName.contains("bluetooth", ignoreCase = true) ||
            clientPackageName == "com.google.android.projection.gearhead" // Android Auto
        return if (trusted) BrowserRoot(ROOT_ID, null) else null
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val out = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId == ROOT_ID) {
            MediaLibrary.TABS.forEach { (id, label) ->
                val n = MediaLibrary.lists[id]?.size ?: 0
                out.add(MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder().setMediaId(id).setTitle(label).setSubtitle("$n tracks").build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                ))
            }
        } else {
            val list = MediaLibrary.lists[parentId].orEmpty()
            window(list, MediaLibrary.currentUri).forEach {
                out.add(MediaBrowserCompat.MediaItem(description(parentId, it), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
        }
        result.sendResult(out)
    }

    /** Runs on [artLoader]. Null if the track changed meanwhile or nothing could be decoded. */
    private fun waveformArt(target: Uri, name: String): Bitmap? {
        val key = target.toString()
        var samples: IntArray? = null
        val until = SystemClock.elapsedRealtime() + 5_000
        while (samples == null && SystemClock.elapsedRealtime() < until) {
            if (artUri != target) return null
            samples = WaveformShare.get(key)
            if (samples == null) try { Thread.sleep(250) } catch (_: InterruptedException) { return null }
        }
        if (samples == null && artUri == target) {
            samples = try {
                kotlinx.coroutines.runBlocking { WaveformGenerator(this@MusicService).generateWaveform(target, 512, name) }
            } catch (e: Exception) {
                CrashLogger.log("MusicService", "Waveform art failed: ${e.message}"); null
            }
            samples?.let { WaveformShare.put(key, it) }
        }
        return samples?.let { runCatching { WaveformShare.render(it) }.getOrNull() }
    }
}
