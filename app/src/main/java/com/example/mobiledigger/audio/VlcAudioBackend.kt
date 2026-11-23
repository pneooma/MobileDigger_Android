package com.example.mobiledigger.audio

import android.content.Context
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VLC Audio Backend for robust audio playback including AIFF support
 * Provides a stable alternative to FFmpegMediaPlayer for problematic files
 */
class VlcAudioBackend(private val context: Context) {
    
    private val verboseLogs = false
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val isInitialized = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isPrepared = AtomicBoolean(false)
    // A seek requested while the player isn't seekable yet (opening/buffering) is stored
    // here and re-applied once the player becomes seekable, so it isn't silently dropped.
    @Volatile private var pendingSeekMs: Long = -1L

    // Fast-skip guard. VLC reports "seekable" before a freshly opened file is really playing;
    // a seek in that window (e.g. tapping the waveform right after skipping) makes VLC report
    // EndReached at once, which used to skip to the NEXT track. Seeks wait for the first time
    // tick (or 500 ms), and an EndReached far from the real end is treated as a glitch.
    @Volatile private var lengthMs = -1L
    @Volatile private var lastTimeMs = 0L
    @Volatile private var lastSeekTarget = -1L
    @Volatile private var mediaSetAt = 0L
    @Volatile private var settled = false
    @Volatile private var recoveries = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val settleRunnable = Runnable { settled = true; applyPendingSeek() }
    
    // Callbacks
    private var onPreparedListener: (() -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    init {
        initializeVLC()
    }
    
    private fun initializeVLC() {
        try {
            CrashLogger.log("VlcAudioBackend", "🔄 Initializing libVLC with minimal options...")
            
            // Minimal VLC options tuned for audio fidelity:
            // --no-audio-time-stretch: never apply time-stretch DSP to samples
            // --audio-replay-gain-mode=none: no replay-gain scaling (unity gain)
            // Software volume stays at 100 (unity) outside explicit fades, so decoded
            // samples pass to AudioTrack unmodified. (-vvv verbose logging removed —
            // it burned CPU on every audio buffer.)
            val options = arrayListOf(
                "--no-video",                     // Disable video processing
                "--no-audio-time-stretch",        // No tempo/pitch DSP
                "--audio-replay-gain-mode=none",  // No replay-gain volume scaling
                // Read ahead 1 s of local files (default 0.3 s) so a busy disk (file moves,
                // waveform decoding) can't starve playback and cause short dropouts.
                "--file-caching=1000",
                // One fixed audio session (AudioTrack output) so the 24-band equalizer and the
                // live spectrum (AudioFx) can attach to MobileDigger's sound only.
                "--aout=android_audiotrack",
                "--audiotrack-session-id=${AudioFx.sessionId(context)}"
            )
            
            CrashLogger.log("VlcAudioBackend", "🔄 Creating LibVLC instance...")
            libVLC = LibVLC(context, options)
            CrashLogger.log("VlcAudioBackend", "✅ LibVLC instance created")
            
            CrashLogger.log("VlcAudioBackend", "🔄 Creating MediaPlayer...")
            mediaPlayer = MediaPlayer(libVLC)
            CrashLogger.log("VlcAudioBackend", "✅ MediaPlayer created")
            
            setupEventListeners()
            
            isInitialized.set(true)
            CrashLogger.log("VlcAudioBackend", "✅ libVLC initialized successfully")
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 Failed to initialize libVLC", e)
            isInitialized.set(false)
            // Clean up if initialization failed
            try {
                mediaPlayer?.release()
                mediaPlayer = null
                libVLC?.release()
                libVLC = null
            } catch (cleanupException: Exception) {
                CrashLogger.log("VlcAudioBackend", "💥 Failed to cleanup after initialization failure", cleanupException)
            }
        }
    }
    
    private fun setupEventListeners() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.MediaChanged -> {
                    CrashLogger.log("VlcAudioBackend", "📁 Media changed")
                }
                // MediaPlayer.Event.NothingSpecial -> {
                //     CrashLogger.log("VlcAudioBackend", "ℹ️ Nothing special")
                // }
                MediaPlayer.Event.Opening -> {
                    CrashLogger.log("VlcAudioBackend", "🔄 Opening media...")
                }
                MediaPlayer.Event.Buffering -> {
                    if (verboseLogs) {
                        val percent = event.buffering
                        CrashLogger.log("VlcAudioBackend", "📊 Buffering: $percent%")
                    }
                }
                MediaPlayer.Event.Playing -> {
                    isPlaying.set(true)
                    // Don't seek yet: wait for the first time tick, at most 500 ms
                    mainHandler.removeCallbacks(settleRunnable)
                    if (!settled) mainHandler.postDelayed(settleRunnable, 500) else applyPendingSeek()
                    CrashLogger.log("VlcAudioBackend", "▶️ Playing")
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying.set(false)
                    CrashLogger.log("VlcAudioBackend", "⏸️ Paused")
                }
                MediaPlayer.Event.Stopped -> {
                    isPlaying.set(false)
                    CrashLogger.log("VlcAudioBackend", "⏹️ Stopped")
                }
                MediaPlayer.Event.EndReached -> {
                    isPlaying.set(false)
                    val pos = maxOf(lastTimeMs, lastSeekTarget)
                    val sinceOpen = android.os.SystemClock.elapsedRealtime() - mediaSetAt
                    val spurious = (lengthMs > 10_000 && pos < lengthMs - 5_000) || (lengthMs <= 0 && sinceOpen < 2_000)
                    if (spurious && recoveries < 2) {
                        // Not a real end: carry on with the same track where the user wanted to be
                        recoveries++
                        val resumeAt = if (lastSeekTarget >= 0) lastSeekTarget else lastTimeMs
                        CrashLogger.log("VlcAudioBackend", "🏁 End reached at ${pos}ms of ${lengthMs}ms — glitch, resuming at ${resumeAt}ms (not skipping)")
                        settled = false
                        pendingSeekMs = if (resumeAt > 1000) resumeAt else -1L
                        mainHandler.post { runCatching { mediaPlayer?.play() } }
                    } else {
                        CrashLogger.log("VlcAudioBackend", "🏁 End reached")
                        onCompletionListener?.invoke()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying.set(false)
                    isPrepared.set(false)
                    val error = "VLC playback error: ${event.type}"
                    CrashLogger.log("VlcAudioBackend", "💥 $error")
                    onErrorListener?.invoke(error)
                }
                MediaPlayer.Event.TimeChanged -> {
                    val t = event.timeChanged
                    if (t > 0) {
                        lastTimeMs = t
                        if (!settled) {
                            settled = true
                            mainHandler.removeCallbacks(settleRunnable)
                            applyPendingSeek()
                        }
                    }
                }
                MediaPlayer.Event.PositionChanged -> {
                    // Optional: Handle position changes for seeking
                }
                MediaPlayer.Event.SeekableChanged -> {
                    if (settled) applyPendingSeek()
                    CrashLogger.log("VlcAudioBackend", "🔍 Seekable changed")
                }
                MediaPlayer.Event.PausableChanged -> {
                    CrashLogger.log("VlcAudioBackend", "⏸️ Pausable changed")
                }
                MediaPlayer.Event.LengthChanged -> {
                    val length = event.lengthChanged
                    if (length > 0) lengthMs = length
                    CrashLogger.log("VlcAudioBackend", "📏 Length changed: ${length}ms")
                }
                MediaPlayer.Event.Vout -> {
                    // Video output events (not relevant for audio-only)
                }
                MediaPlayer.Event.ESAdded -> {
                    CrashLogger.log("VlcAudioBackend", "🎵 Audio track added")
                }
                MediaPlayer.Event.ESDeleted -> {
                    CrashLogger.log("VlcAudioBackend", "🗑️ Audio track deleted")
                }
                MediaPlayer.Event.ESSelected -> {
                    CrashLogger.log("VlcAudioBackend", "✅ Audio track selected")
                }
                // Note: Some VLC event constants may not be available in all versions
                else -> {
                    CrashLogger.log("VlcAudioBackend", "❓ Unknown event: ${event.type}")
                }
            }
        }
    }
    
    /** Descriptor of the phone file VLC is reading in place (closed when the next track is set). */
    private var openFd: android.os.ParcelFileDescriptor? = null

    /**
     * Plays a content:// (SAF) file straight from its file descriptor, like the VLC app does,
     * instead of copying the whole file into the cache first (that wrote ~80 MB of flash per
     * track and slowed every start). Returns false if the provider can't give a seekable file;
     * the caller then falls back to the temp copy.
     */
    fun setDataSourceContent(uri: Uri): Boolean {
        if (!isInitialized.get()) return false
        val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "Can't open descriptor: ${e.message}"); null
        } ?: return false
        val seekable = try {
            android.system.Os.lseek(pfd.fileDescriptor, 0L, android.system.OsConstants.SEEK_SET) == 0L && pfd.statSize > 0
        } catch (_: Exception) { false }
        if (!seekable) { runCatching { pfd.close() }; return false }
        return try {
            mainHandler.removeCallbacks(settleRunnable)
            settled = false
            lengthMs = -1L
            lastTimeMs = 0L
            lastSeekTarget = -1L
            pendingSeekMs = -1L
            recoveries = 0
            mediaSetAt = android.os.SystemClock.elapsedRealtime()
            val media = Media(libVLC, pfd.fileDescriptor)
            mediaPlayer?.media = media
            media.release()
            val old = openFd
            openFd = pfd
            runCatching { old?.close() }
            isPrepared.set(false)
            CrashLogger.log("VlcAudioBackend", "✅ Playing in place (no copy): $uri")
            true
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 In-place source failed", e)
            runCatching { pfd.close() }
            false
        }
    }

    private fun closeOpenFd() { val f = openFd; openFd = null; runCatching { f?.close() } }

    fun setDataSource(uri: Uri): Boolean {
        return try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized")
                return false
            }
            
            CrashLogger.log("VlcAudioBackend", "🔄 Setting VLC data source: $uri")
            
            // New track: reset the fast-skip guard
            mainHandler.removeCallbacks(settleRunnable)
            settled = false
            lengthMs = -1L
            lastTimeMs = 0L
            lastSeekTarget = -1L
            pendingSeekMs = -1L
            recoveries = 0
            mediaSetAt = android.os.SystemClock.elapsedRealtime()

            val media = Media(libVLC, uri)
            mediaPlayer?.media = media
            media.release() // Release the Media object after setting
            closeOpenFd()
            
            isPrepared.set(false)
            CrashLogger.log("VlcAudioBackend", "✅ VLC data source set successfully")
            true
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 Failed to set VLC data source", e)
            false
        }
    }
    
    fun start() {
        try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized for start")
                return
            }
            
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "▶️ Starting VLC playback...")
            mediaPlayer?.play()
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC start failed", e)
            onErrorListener?.invoke("VLC start failed: ${e.message}")
        }
    }
    
    fun pause() {
        try {
            if (isPlaying.get()) {
                if (verboseLogs) CrashLogger.log("VlcAudioBackend", "⏸️ Pausing VLC playback...")
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC pause failed", e)
        }
    }
    
    fun stop() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "⏹️ Stopping VLC playback...")
            cancelSeekFade()
            mediaPlayer?.stop()
            isPlaying.set(false)
            isPrepared.set(false)
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC stop failed", e)
        }
    }
    
    fun reset() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🔄 Resetting VLC player...")
            cancelSeekFade()
            mediaPlayer?.stop()
            mediaPlayer?.media = null
            closeOpenFd()
            isPlaying.set(false)
            isPrepared.set(false)
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC reset failed", e)
        }
    }
    
    fun release() {
        try {
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🧹 Releasing VLC resources...")
            
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            closeOpenFd()
            
            libVLC?.release()
            libVLC = null
            
            isInitialized.set(false)
            isPlaying.set(false)
            isPrepared.set(false)
            
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "✅ VLC resources released")
            
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC release failed", e)
        }
    }
    
    fun isPlaying(): Boolean = isPlaying.get()
    
    fun isPrepared(): Boolean = isPrepared.get()
    
    fun isInitialized(): Boolean = isInitialized.get()
    
    // Callback setters
    fun setOnPreparedListener(listener: () -> Unit) {
        onPreparedListener = listener
    }
    
    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
    
    // Audio control
    // Volume the caller asked for (0..100); seek fades return to it.
    @Volatile private var baseVolume = 100
    // Bumped on every seek, so a newer seek cancels the steps of an older fade.
    private var seekFadeGen = 0

    fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0f, 1f)
            baseVolume = (clampedVolume * 100).toInt()
            mediaPlayer?.volume = baseVolume
            if (verboseLogs) CrashLogger.log("VlcAudioBackend", "🔊 VLC volume set to: ${(clampedVolume * 100).toInt()}%")
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC volume set failed", e)
        }
    }
    
    fun seekTo(positionMs: Long) {
        try {
            if (!isInitialized.get()) {
                CrashLogger.log("VlcAudioBackend", "❌ VLC not initialized for seek")
                return
            }
            val mp = mediaPlayer ?: return
            lastSeekTarget = positionMs
            if (mp.isSeekable && settled) {
                pendingSeekMs = -1L
                if (mp.isPlaying && baseVolume > 0) fadedSeek(positionMs)
                else mp.time = positionMs
                if (verboseLogs) CrashLogger.log("VlcAudioBackend", "↔️ Seeking to ${positionMs}ms")
            } else {
                // Track still opening — defer; applied on the first time tick (or after 500 ms).
                pendingSeekMs = positionMs
                CrashLogger.log("VlcAudioBackend", "↔️ Seek deferred until the track is playing: ${positionMs}ms")
            }
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC seek failed", e)
            onErrorListener?.invoke("VLC seek failed: ${e.message}")
        }
    }

    /**
     * Seek without a click. A plain seek drops the buffered audio and jumps mid-waveform, which
     * is heard as a short tick. Fade out (~30 ms), wait for the faded audio to reach the speaker,
     * seek, then fade back in (~80 ms) once the new position is playing. Runs on the main thread;
     * a newer seek cancels an older one's remaining steps (only the last position is used).
     */
    private fun fadedSeek(positionMs: Long) {
        val gen = ++seekFadeGen
        val mp = mediaPlayer ?: return
        val target = baseVolume
        val outSteps = 3; val inSteps = 6; val stepMs = 12L
        val startVol = mp.volume.coerceIn(0, target)
        for (i in 1..outSteps) mainHandler.postDelayed({
            if (gen == seekFadeGen) runCatching { mediaPlayer?.volume = startVol * (outSteps - i) / outSteps }
        }, i * stepMs)
        // AudioTrack latency: let the silent samples get played before the seek flushes them
        val seekAt = outSteps * stepMs + 60L
        mainHandler.postDelayed({
            if (gen == seekFadeGen) runCatching { mediaPlayer?.time = lastSeekTarget.takeIf { it >= 0 } ?: positionMs }
        }, seekAt)
        val inStart = seekAt + 60L
        for (i in 1..inSteps) mainHandler.postDelayed({
            if (gen == seekFadeGen) runCatching { mediaPlayer?.volume = baseVolume * i / inSteps }
        }, inStart + i * stepMs)
    }

    /** Drop a seek fade still in progress (track stopped/changed) and restore the volume. */
    private fun cancelSeekFade() {
        seekFadeGen++
        // Synchronous, so a later setVolume() (e.g. the next track's fade-in) always wins
        runCatching { mediaPlayer?.let { if (it.volume != baseVolume) it.volume = baseVolume } }
    }

    private fun applyPendingSeek() {
        val target = pendingSeekMs
        if (target < 0L) return
        try {
            val mp = mediaPlayer ?: return
            if (mp.isSeekable && settled) {
                mp.time = target
                pendingSeekMs = -1L
                CrashLogger.log("VlcAudioBackend", "↔️ Applied deferred seek: ${target}ms")
            }
        } catch (_: Exception) {}
    }

    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.time ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC getCurrentPosition failed", e)
            0L
        }
    }
    
    fun getDuration(): Long {
        return try {
            mediaPlayer?.length ?: 0L
        } catch (e: Exception) {
            CrashLogger.log("VlcAudioBackend", "💥 VLC getDuration failed", e)
            0L
        }
    }
}
