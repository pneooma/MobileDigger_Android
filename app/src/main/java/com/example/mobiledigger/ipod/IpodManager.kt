package com.example.mobiledigger.ipod

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-wide holder for the connected iPod touch, its library, and the send "outbox": songs queued
 * for the iPod are converted in the background right away (several at once, even with no iPod
 * plugged in) and sent in batches whenever the iPod is connected — later batches keep converting
 * while an earlier one is uploading.
 */
object IpodManager {
    enum class Status { IDLE, NO_DEVICE, CONNECTING, READY, BUSY, ERROR }
    enum class TState { WAITING, PREPARING, CONVERTING, READY, SENDING, DONE, FAILED }

    /** One song in the outbox. [playlist] = iPod playlist to put it in (created if missing), null = library only. */
    data class TransferItem(
        val id: Int, val name: String, val playlist: String?, val quality: SendQuality,
        val state: TState, val detail: String = "", val progress: Float = 0f
    )
    data class HistoryEntry(val time: Long, val text: String, val ok: Boolean)

    data class Ui(
        val status: Status = Status.IDLE,
        val message: String = "",
        val prompt: String? = null,         // e.g. "Tap Trust on the iPod"
        val library: TouchLibrary? = null,
        val deviceName: String? = null,
        val free: Pair<Long, Long>? = null,
        val progress: Float? = null,
        val log: List<String> = emptyList(),
        val queue: List<TransferItem> = emptyList(),
        val history: List<HistoryEntry> = emptyList()
    ) {
        val pending get() = queue.count { it.state != TState.DONE && it.state != TState.FAILED }
    }

    /** A song from this phone to send (name decides the format). */
    data class Source(val uri: android.net.Uri, val name: String, val size: Long = 0)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var touch: IpodTouch? = null
    private const val ACTION_PERMISSION = "com.example.mobiledigger.IPOD_USB_PERMISSION"

    // Conversions run here: a few at once (the encoders are single-threaded each).
    private val workers = Executors.newFixedThreadPool(maxOf(2, minOf(4, Runtime.getRuntime().availableProcessors() / 2)))
    private val prepared = ConcurrentHashMap<Int, PreparedAudio>()
    private val sources = ConcurrentHashMap<Int, Source>()
    private val nextId = AtomicInteger(1)
    private val stateLock = Any()

    /** The full-screen iPod page. */
    private val _pageOpen = MutableStateFlow(false)
    val pageOpen: StateFlow<Boolean> = _pageOpen.asStateFlow()
    fun openPage() { _pageOpen.value = true }
    fun closePage() { _pageOpen.value = false }

    val isConnected get() = touch != null

    @Volatile private var app: Context? = null
    private const val BATCH = 12          // wait for this many ready songs (each session costs ~13 s on its own)…
    private const val BATCH_WAIT_MS = 45_000L  // …but not longer than this
    private const val MAX_BATCH = 40

    /** True while something is converting/sending or an iPod job runs — what the background service waits for. */
    fun hasBackgroundWork(): Boolean {
        val u = _ui.value
        if (u.status == Status.BUSY || u.status == Status.CONNECTING) return true
        return u.queue.any {
            it.state == TState.WAITING || it.state == TState.PREPARING || it.state == TState.CONVERTING || it.state == TState.SENDING ||
                (it.state == TState.READY && touch != null)
        }
    }

    private fun keepAlive(ctx: Context) { if (hasBackgroundWork()) IpodService.start(ctx) }

    // ------------------------------------------------------------------ outbox on disk

    private fun outboxFile(ctx: Context) = File(ctx.filesDir, "ipod/outbox.json")
    @Volatile private var saveQueued = false

    /** Writes the songs not sent yet, so a closed / killed app picks them up again. Debounced. */
    private fun saveOutbox() {
        val ctx = app ?: return
        if (saveQueued) return
        saveQueued = true
        scope.launch {
            delay(1000)
            saveQueued = false
            val arr = JSONArray()
            for (it in _ui.value.queue) {
                if (it.state == TState.DONE || it.state == TState.FAILED) continue
                val s = sources[it.id] ?: continue
                arr.put(JSONObject().put("uri", s.uri.toString()).put("name", s.name).put("size", s.size)
                    .put("playlist", it.playlist ?: "").put("alac", it.quality.alac).put("aac", it.quality.aacKbps)
                    .put("keep", it.quality.keepCompatible))
            }
            runCatching {
                val f = outboxFile(ctx); f.parentFile?.mkdirs()
                val tmp = File(f.path + ".tmp"); tmp.writeText(arr.toString()); tmp.renameTo(f)
            }.onFailure { CrashLogger.log("iPod", "couldn't save the outbox", it) }
        }
    }

    /** Called when the app comes to the foreground: puts back songs that were queued when the app was closed, and resumes work. */
    fun onForeground(context: Context) {
        val ctx = context.applicationContext
        if (app == null) {
            app = ctx
            val saved = runCatching { JSONArray(outboxFile(ctx).takeIf { it.exists() }?.readText() ?: "[]") }.getOrNull() ?: JSONArray()
            val groups = (0 until saved.length()).map { saved.getJSONObject(it) }.groupBy {
                Triple(it.optString("playlist").ifBlank { null }, it.optBoolean("alac"), it.optInt("aac", 256) to it.optBoolean("keep", true))
            }
            for ((k, list) in groups) {
                enqueue(ctx, list.map { Source(android.net.Uri.parse(it.getString("uri")), it.getString("name"), it.optLong("size")) },
                    k.first, SendQuality(k.second, k.third.first, k.third.second), restored = true)
            }
        }
        keepAlive(ctx)
        autoConnect(ctx)
    }

    fun note(s: String) {
        CrashLogger.log("iPod", s)
        synchronized(stateLock) { _ui.value = _ui.value.copy(log = (_ui.value.log + s).takeLast(200)) }
    }

    private fun update(f: (Ui) -> Ui) = synchronized(stateLock) { _ui.value = f(_ui.value) }

    private fun set(status: Status, message: String, prompt: String? = null) {
        update { it.copy(status = status, message = message, prompt = prompt) }
        note(message)
    }

    private fun setItem(id: Int, f: (TransferItem) -> TransferItem) {
        var finished = false
        update { u -> u.copy(queue = u.queue.map { if (it.id == id) f(it).also { n -> finished = it.state != n.state && (n.state == TState.DONE || n.state == TState.FAILED) } else it }) }
        if (finished) saveOutbox()
    }

    private fun addHistory(text: String, ok: Boolean) =
        update { it.copy(history = (listOf(HistoryEntry(System.currentTimeMillis(), text, ok)) + it.history).take(20)) }

    fun clearFinished() = update { u -> u.copy(queue = u.queue.filter { it.state != TState.DONE && it.state != TState.FAILED }) }

    /** Drops songs still waiting / converting / ready (not the ones being sent right now). */
    fun cancelPending() {
        val drop = _ui.value.queue.filter { it.state == TState.WAITING || it.state == TState.PREPARING || it.state == TState.CONVERTING || it.state == TState.READY }
        if (drop.isEmpty()) return
        val ids = drop.map { it.id }.toSet()
        update { u -> u.copy(queue = u.queue.filterNot { it.id in ids }) }
        for (id in ids) { sources.remove(id); prepared.remove(id)?.file?.parentFile?.deleteRecursively() }
        note("Cancelled ${ids.size} songs waiting for the iPod")
        saveOutbox()
    }

    // ------------------------------------------------------------------ connection

    /** Finds the iPod, asks for USB permission if needed, connects and reads its library. */
    fun connect(context: Context) {
        val ctx = context.applicationContext
        app = app ?: ctx
        val usb = ctx.getSystemService(UsbManager::class.java)
        val dev = IpodTouch.findDevice(usb)
        if (dev == null) { set(Status.NO_DEVICE, "No iPod touch found. Plug it into the phone (USB-C to Lightning) and unlock it."); return }
        if (usb.hasPermission(dev)) { start(ctx, usb, dev); return }
        set(Status.CONNECTING, "Asking for permission to use the iPod…")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                runCatching { ctx.unregisterReceiver(this) }
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) start(ctx, usb, dev)
                else set(Status.ERROR, "USB permission wasn't given. Tap Connect and choose Allow.")
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(ACTION_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ACTION_PERMISSION).setPackage(ctx.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        usb.requestPermission(dev, pi)
    }

    private fun start(ctx: Context, usb: UsbManager, dev: UsbDevice) {
        scope.launch {
            lock.withLock {
                runCatching { touch?.close() }; touch = null
                set(Status.CONNECTING, "Connecting to the iPod…")
                IpodService.start(ctx)
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    val t = IpodTouch.connect(ctx, usb, dev) { msg -> update { it.copy(prompt = msg) }; note(msg) }
                    touch = t
                    update { it.copy(deviceName = t.name, prompt = null, free = t.freeSpace()) }
                    set(Status.BUSY, "Reading the music on ${t.name}…")
                    val afc = t.afc()
                    try {
                        val lib = t.readLibrary(afc, File(ctx.cacheDir, "ipod-db"))
                        update { it.copy(library = lib) }
                        note("${t.name}: ${lib.tracks.size} songs, ${lib.playlists.size} playlists")
                        IpodDebug.log("connect + read library: ${SystemClock.elapsedRealtime() - t0} ms")
                        set(Status.READY, "Connected")
                    } finally { afc.close() }
                } catch (e: Exception) {
                    CrashLogger.log("iPod", "connect failed", e)
                    runCatching { touch?.close() }; touch = null
                    set(Status.ERROR, e.message ?: e.javaClass.simpleName)
                }
            }
            trySend(ctx)
        }
    }

    /** Silently connects when the iPod is plugged in and permission was given before. */
    private fun autoConnect(ctx: Context) {
        if (touch != null || _ui.value.status == Status.CONNECTING) return
        val usb = ctx.getSystemService(UsbManager::class.java)
        val dev = IpodTouch.findDevice(usb) ?: return
        if (usb.hasPermission(dev)) start(ctx, usb, dev)
    }

    // ------------------------------------------------------------------ jobs on the iPod

    /**
     * Runs [block] with the connected iPod (reconnecting once if the link dropped), then re-reads the
     * library. Returns false when there was no iPod.
     */
    private suspend fun runJob(ctx: Context, title: String, block: (IpodTouch, File) -> String): Boolean {
        var ok = false
        lock.withLock {
            var t = touch ?: run { set(Status.ERROR, "Connect the iPod first."); return@withLock }
            if (runCatching { t.freeSpace() }.getOrNull() == null) {
                runCatching { t.close() }; touch = null
                val usb = ctx.getSystemService(UsbManager::class.java)
                val dev = IpodTouch.findDevice(usb)
                if (dev == null || !usb.hasPermission(dev)) { set(Status.ERROR, "The iPod isn't connected any more. Plug it in and tap Reconnect."); return@withLock }
                t = try { IpodTouch.connect(ctx, usb, dev) { m -> update { it.copy(prompt = m) } } }
                    catch (e: Exception) { set(Status.ERROR, e.message ?: "Couldn't reconnect to the iPod"); return@withLock }
                touch = t
            }
            val w = File(ctx.cacheDir, "ipod-sync").also { it.deleteRecursively(); it.mkdirs() }
            val t0 = SystemClock.elapsedRealtime()
            try {
                set(Status.BUSY, title)
                val summary = block(t, w)
                update { it.copy(progress = null) }
                set(Status.BUSY, "Reading the iPod library…")
                val afc = t.afc()
                try { update { it.copy(library = t.readLibrary(afc, File(ctx.cacheDir, "ipod-db")), free = t.freeSpace()) } }
                finally { afc.close() }
                set(Status.READY, summary)
                addHistory(summary, true)
                IpodDebug.log("job “$title”: ${SystemClock.elapsedRealtime() - t0} ms")
                ok = true
            } catch (e: Exception) {
                CrashLogger.log("iPod", "$title failed", e)
                update { it.copy(progress = null) }
                set(Status.ERROR, e.message ?: e.javaClass.simpleName)
                addHistory("$title — ${e.message ?: "failed"}", false)
            } finally { w.deleteRecursively() }
        }
        return ok
    }

    private fun job(ctx: Context, title: String, block: (IpodTouch, File) -> String) {
        IpodService.start(ctx.applicationContext)
        scope.launch { runJob(ctx.applicationContext, title, block); trySend(ctx.applicationContext) }
    }

    private fun sync(t: IpodTouch, ctx: Context, w: File, onSending: (Int) -> Unit = {}, onSent: (Int) -> Unit = {}) =
        TouchSync(t, ctx.filesDir, w, ::note, { msg, f -> update { it.copy(message = msg, progress = f) } }, onSending, onSent)

    /** Wire pids (item_store.sync_id) of a playlist's current members, in order. */
    private fun memberSyncIds(lib: TouchLibrary, pl: TouchPlaylist): List<Long> {
        val byId = lib.tracks.associateBy { it.id }
        return pl.trackIds.mapNotNull { byId[it]?.syncId?.takeIf { s -> s != 0L } }
    }

    /** Create the playlist [name] with [members], or append them to the editable playlist of that name. */
    private fun playlistChange(lib: TouchLibrary?, name: String, members: List<Member>): PlaylistChange {
        val existing = lib?.playlists?.firstOrNull { it.editable && it.name.equals(name, ignoreCase = true) }
        return if (existing == null) PlaylistChange.Create(name, members)
        else PlaylistChange.Replace(existing.id, existing.name, memberSyncIds(lib, existing).map { Member.Existing(it) } +
            members.filterNot { m -> m is Member.Existing && m.syncId in memberSyncIds(lib, existing) })
    }

    // ------------------------------------------------------------------ the outbox

    /**
     * Queues songs for the iPod. Conversion starts immediately in the background; they're sent as
     * soon as the iPod is connected. [playlist] = iPod playlist to add them to (null = none).
     */
    fun enqueue(context: Context, list: List<Source>, playlist: String?, quality: SendQuality, restored: Boolean = false) {
        val ctx = context.applicationContext
        app = app ?: ctx
        if (list.isEmpty()) return
        val items = list.map { s ->
            val id = nextId.getAndIncrement()
            sources[id] = s
            TransferItem(id, s.name, playlist?.trim()?.takeIf { it.isNotEmpty() }, quality, TState.WAITING)
        }
        update { it.copy(queue = it.queue + items) }
        note((if (restored) "Resumed " else "Queued ") + "${items.size} songs for the iPod" + (playlist?.let { " (playlist “$it”)" } ?: ""))
        saveOutbox()
        val work = File(ctx.cacheDir, "ipod-outbox").also { it.mkdirs() }
        for (item in items) workers.execute { prepareItem(ctx, item, work) }
        IpodService.start(ctx)
        autoConnect(ctx)
    }

    private fun prepareItem(ctx: Context, item: TransferItem, work: File) {
        val s = sources[item.id] ?: return
        if (_ui.value.queue.none { it.id == item.id }) return   // cancelled meanwhile
        val needsConvert = s.name.substringAfterLast('.', "").lowercase().let { ext ->
            !item.quality.keepCompatible || ext !in setOf("mp3", "m4a", "mp4")
        }
        setItem(item.id) { it.copy(state = if (needsConvert) TState.CONVERTING else TState.PREPARING,
            detail = if (needsConvert) "Converting to ${item.quality.label}" else "Preparing") }
        try {
            val dir = File(work, "item-${item.id}")
            val uri = if (s.uri.scheme == "http" || s.uri.scheme == "https") {
                val f = File(dir.also { it.mkdirs() }, "net.${s.name.substringAfterLast('.', "bin")}")
                java.net.URL(s.uri.toString()).openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
                android.net.Uri.fromFile(f)
            } else s.uri
            var shown = -1f
            val a = TouchMedia.prepare(ctx, uri, s.name, dir, item.id, item.quality) { f ->
                // Only redraw on visible steps: hundreds of queued songs × frequent callbacks = a lot of copying.
                if (f - shown >= 0.02f || f >= 1f) { shown = f; setItem(item.id) { it.copy(progress = f) } }
            }
            prepared[item.id] = a
            setItem(item.id) { it.copy(state = TState.READY, progress = 1f,
                detail = if (a.converted) "Converted (${a.codec.uppercase()}), ready to send" else "Ready to send") }
            trySend(ctx)
        } catch (e: Exception) {
            CrashLogger.log("iPod", "Can't prepare ${s.name}", e)
            setItem(item.id) { it.copy(state = TState.FAILED, detail = e.message ?: "Couldn't read it") }
        }
    }

    private val sending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Sends every song that's ready, in one session; repeats while more become ready. */
    private fun trySend(ctx: Context) {
        if (touch == null) { autoConnect(ctx); return }
        if (!sending.compareAndSet(false, true)) return
        scope.launch {
            try {
                var waitingSince = 0L
                while (true) {
                    val q = _ui.value.queue
                    val allReady = q.filter { it.state == TState.READY && prepared.containsKey(it.id) }
                    if (allReady.isEmpty() || touch == null) break
                    // Fewer than a batch ready and more still converting: wait a little for them, so the
                    // fixed cost of a sync session is shared by more songs.
                    val inFlight = q.count { it.state == TState.WAITING || it.state == TState.PREPARING || it.state == TState.CONVERTING }
                    if (inFlight > 0 && allReady.size < BATCH) {
                        if (waitingSince == 0L) waitingSince = SystemClock.elapsedRealtime()
                        if (SystemClock.elapsedRealtime() - waitingSince < BATCH_WAIT_MS) { delay(1500); continue }
                    }
                    waitingSince = 0L
                    val ready = allReady.take(MAX_BATCH)
                    ready.forEach { r -> setItem(r.id) { it.copy(state = TState.SENDING, detail = "Waiting for the iPod", progress = 0f) } }
                    val ok = runJob(ctx, "Sending ${ready.size} song${if (ready.size == 1) "" else "s"}…") { t, w ->
                        val adds = ready.map { TouchAdd(prepared[it.id]!!) }
                        val lib = _ui.value.library
                        val changes = ready.withIndex().filter { it.value.playlist != null }.groupBy { it.value.playlist!! }
                            .map { (name, idx) -> playlistChange(lib, name, idx.map { Member.New(it.index) }) }
                        val r = sync(t, ctx, w,
                            onSending = { i -> ready.getOrNull(i)?.let { r -> setItem(r.id) { it.copy(detail = "Sending") } } },
                            onSent = { i -> ready.getOrNull(i)?.let { r -> setItem(r.id) { it.copy(state = TState.DONE, detail = "On the iPod") } } }
                        ).run(adds, emptyList(), changes)
                        val converted = adds.count { it.audio.converted }
                        "Sent ${r.added} song${if (r.added == 1) "" else "s"}" + (if (converted > 0) " ($converted converted)" else "") +
                            (if (changes.isNotEmpty()) " · playlist ${ready.mapNotNull { it.playlist }.distinct().joinToString()}" else "")
                    }
                    // Whatever didn't make it: back to READY on a dropped link (sent next time), else failed.
                    for (r in ready) {
                        val cur = _ui.value.queue.firstOrNull { it.id == r.id } ?: continue
                        if (cur.state == TState.SENDING) setItem(r.id) {
                            if (ok) it.copy(state = TState.FAILED, detail = "The iPod didn't take it")
                            else it.copy(state = TState.READY, detail = "Will send when the iPod is connected")
                        }
                        if (_ui.value.queue.firstOrNull { it.id == r.id }?.state == TState.DONE) {
                            prepared.remove(r.id)?.file?.parentFile?.deleteRecursively()
                            sources.remove(r.id)
                        }
                    }
                    if (!ok) break
                }
            } finally { sending.set(false) }
            // Something became ready while we were sending?
            if (touch != null && _ui.value.queue.any { it.state == TState.READY }) trySend(ctx)
        }
    }

    fun retryFailed(context: Context) {
        val ctx = context.applicationContext
        val failed = _ui.value.queue.filter { it.state == TState.FAILED && sources.containsKey(it.id) }
        val work = File(ctx.cacheDir, "ipod-outbox")
        for (f in failed) {
            if (prepared.containsKey(f.id)) setItem(f.id) { it.copy(state = TState.READY, detail = "Ready to send") }
            else { setItem(f.id) { it.copy(state = TState.WAITING, detail = "") }; workers.execute { prepareItem(ctx, f, work) } }
        }
        saveOutbox()
        IpodService.start(ctx)
        trySend(ctx)
    }

    // ------------------------------------------------------------------ managing what's on the iPod

    /** Creates ([pid] null) or rewrites a playlist with [members] (wire pids, in order). */
    fun savePlaylist(context: Context, pid: Long?, name: String, members: List<Long>) {
        job(context, "Saving “$name” on the iPod…") { t, w ->
            val m = members.map { Member.Existing(it) }
            val change = if (pid == null) PlaylistChange.Create(name, m) else PlaylistChange.Replace(pid, name, m)
            sync(t, context, w).run(emptyList(), emptyList(), listOf(change))
            "Saved “$name” (${members.size} songs)"
        }
    }

    fun deletePlaylist(context: Context, pid: Long, name: String) {
        job(context, "Deleting “$name”…") { t, w ->
            sync(t, context, w).run(emptyList(), emptyList(), listOf(PlaylistChange.Delete(pid)))
            "Deleted the playlist “$name” (its songs stay on the iPod)"
        }
    }

    fun removeSongs(context: Context, syncIds: List<Long>) {
        job(context, "Removing ${syncIds.size} songs…") { t, w ->
            val r = sync(t, context, w).run(emptyList(), syncIds, emptyList())
            "Removed ${r.removed} song${if (r.removed == 1) "" else "s"} from ${t.name}"
        }
    }

    /** Adds songs already on the iPod to the playlist [name] (created if missing). */
    fun addToPlaylist(context: Context, syncIds: List<Long>, name: String) {
        job(context, "Adding ${syncIds.size} songs to “$name”…") { t, w ->
            sync(t, context, w).run(emptyList(), emptyList(), listOf(playlistChange(_ui.value.library, name, syncIds.map { Member.Existing(it) })))
            "Added ${syncIds.size} song${if (syncIds.size == 1) "" else "s"} to “$name”"
        }
    }

    /** Changes a song's title / artist / album / genre on the iPod. */
    fun editSong(context: Context, edit: TrackEdit) {
        job(context, "Saving “${edit.title}”…") { t, w ->
            sync(t, context, w).run(emptyList(), emptyList(), emptyList(), listOf(edit))
            "Saved “${edit.title}”"
        }
    }

    fun disconnect() {
        scope.launch { lock.withLock { runCatching { touch?.close() }; touch = null; update { it.copy(status = Status.IDLE, message = "Disconnected", library = null) } } }
    }
}
