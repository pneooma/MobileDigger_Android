package com.example.mobiledigger.ipod

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** App-wide holder for the connected iPod touch, its library and what the UI shows. */
object IpodManager {
    data class Ui(
        val status: Status = Status.IDLE,
        val message: String = "",
        val prompt: String? = null,         // e.g. "Tap Trust on the iPod"
        val library: TouchLibrary? = null,
        val deviceName: String? = null,
        val free: Pair<Long, Long>? = null,
        val progress: Float? = null,
        val log: List<String> = emptyList()
    )
    enum class Status { IDLE, NO_DEVICE, CONNECTING, READY, BUSY, ERROR }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var touch: IpodTouch? = null
    private const val ACTION_PERMISSION = "com.example.mobiledigger.IPOD_USB_PERMISSION"

    fun note(s: String) {
        CrashLogger.log("iPod", s)
        _ui.value = _ui.value.copy(log = (_ui.value.log + s).takeLast(200))
    }

    private fun set(status: Status, message: String, prompt: String? = null) {
        _ui.value = _ui.value.copy(status = status, message = message, prompt = prompt)
        note(message)
    }

    /** Finds the iPod, asks for USB permission if needed, connects and reads its library. */
    fun connect(context: Context) {
        val ctx = context.applicationContext
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
                try {
                    val t = IpodTouch.connect(ctx, usb, dev) { msg ->
                        _ui.value = _ui.value.copy(prompt = msg); note(msg)
                    }
                    touch = t
                    _ui.value = _ui.value.copy(deviceName = t.name, prompt = null, free = t.freeSpace())
                    set(Status.BUSY, "Reading the music on ${t.name}…")
                    val afc = t.afc()
                    try {
                        val lib = t.readLibrary(afc, File(ctx.cacheDir, "ipod-db"))
                        _ui.value = _ui.value.copy(library = lib)
                        set(Status.READY, "${t.name}: ${lib.tracks.size} songs, ${lib.playlists.size} playlists")
                    } finally { afc.close() }
                } catch (e: Exception) {
                    CrashLogger.log("iPod", "connect failed", e)
                    runCatching { touch?.close() }; touch = null
                    set(Status.ERROR, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    /** Where songs being sent should also go. */
    sealed class Target {
        object None : Target()
        data class Existing(val pid: Long) : Target()
        data class New(val name: String) : Target()
    }

    /** A song from this phone to send (name decides the format). */
    data class Source(val uri: android.net.Uri, val name: String)

    private fun work(ctx: Context) = File(ctx.cacheDir, "ipod-sync").also { it.deleteRecursively(); it.mkdirs() }

    /**
     * Runs [block] with the connected iPod (connecting first if needed is the UI's job), then re-reads
     * the library. Shows progress and the outcome in [ui].
     */
    private fun job(ctx: Context, title: String, block: (IpodTouch, File) -> String) {
        scope.launch {
            lock.withLock {
                val t = touch
                if (t == null) { set(Status.ERROR, "Connect the iPod first."); return@withLock }
                val w = work(ctx)
                try {
                    set(Status.BUSY, title)
                    val summary = block(t, w)
                    _ui.value = _ui.value.copy(progress = null)
                    set(Status.BUSY, "Reading the iPod library…")
                    val afc = t.afc()
                    try { _ui.value = _ui.value.copy(library = t.readLibrary(afc, File(ctx.cacheDir, "ipod-db")), free = t.freeSpace()) }
                    finally { afc.close() }
                    set(Status.READY, summary)
                } catch (e: Exception) {
                    CrashLogger.log("iPod", "$title failed", e)
                    _ui.value = _ui.value.copy(progress = null)
                    set(Status.ERROR, e.message ?: e.javaClass.simpleName)
                } finally { w.deleteRecursively() }
            }
        }
    }

    private fun sync(t: IpodTouch, ctx: Context, w: File) = TouchSync(t, ctx.filesDir, w, ::note) { msg, f ->
        _ui.value = _ui.value.copy(message = msg, progress = f)
    }

    /** Wire pids (item_store.sync_id) of a playlist's current members, in order. */
    private fun memberSyncIds(lib: TouchLibrary, pl: TouchPlaylist): List<Long> {
        val byId = lib.tracks.associateBy { it.id }
        return pl.trackIds.mapNotNull { byId[it]?.syncId?.takeIf { s -> s != 0L } }
    }

    fun send(context: Context, sources: List<Source>, target: Target) {
        val ctx = context.applicationContext
        job(ctx, "Preparing ${sources.size} songs…") { t, w ->
            val adds = ArrayList<TouchAdd>()
            val skipped = ArrayList<String>()
            sources.forEachIndexed { i, s ->
                _ui.value = _ui.value.copy(message = "Preparing “${s.name}” (${i + 1}/${sources.size})…", progress = i.toFloat() / sources.size)
                try {
                    val uri = if (s.uri.scheme == "http" || s.uri.scheme == "https") {
                        // Network-drive tracks: fetch to a local file first
                        val f = File(w, "net-$i.${s.name.substringAfterLast('.', "bin")}")
                        java.net.URL(s.uri.toString()).openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
                        android.net.Uri.fromFile(f)
                    } else s.uri
                    adds += TouchAdd(TouchMedia.prepare(ctx, uri, s.name, w, i) { f ->
                        _ui.value = _ui.value.copy(progress = (i + f) / sources.size)
                    })
                } catch (e: Exception) {
                    CrashLogger.log("iPod", "Can't prepare ${s.name}", e); skipped += s.name
                }
            }
            if (adds.isEmpty()) throw TouchSyncException("None of the songs could be read (${skipped.joinToString()})")
            val lib = _ui.value.library
            val newMembers = adds.indices.map { Member.New(it) }
            val changes = when (target) {
                Target.None -> emptyList()
                is Target.New -> listOf(PlaylistChange.Create(target.name, newMembers))
                is Target.Existing -> {
                    val pl = lib?.playlists?.firstOrNull { it.id == target.pid } ?: throw TouchSyncException("That playlist is no longer on the iPod")
                    listOf(PlaylistChange.Replace(pl.id, pl.name, memberSyncIds(lib, pl).map { Member.Existing(it) } + newMembers))
                }
            }
            val r = sync(t, ctx, w).run(adds, emptyList(), changes)
            val converted = adds.count { it.audio.converted }
            "Sent ${r.added} song${if (r.added == 1) "" else "s"} to ${t.name}" +
                (if (converted > 0) " ($converted converted to AAC)" else "") +
                (if (skipped.isNotEmpty()) ". Couldn't read: ${skipped.joinToString()}" else "")
        }
    }

    /** Creates ([pid] null) or rewrites a playlist with [members] (wire pids, in order). */
    fun savePlaylist(context: Context, pid: Long?, name: String, members: List<Long>) {
        val ctx = context.applicationContext
        job(ctx, "Saving “$name” on the iPod…") { t, w ->
            val m = members.map { Member.Existing(it) }
            val change = if (pid == null) PlaylistChange.Create(name, m) else PlaylistChange.Replace(pid, name, m)
            sync(t, ctx, w).run(emptyList(), emptyList(), listOf(change))
            "Saved “$name” (${members.size} songs)"
        }
    }

    fun deletePlaylist(context: Context, pid: Long, name: String) {
        val ctx = context.applicationContext
        job(ctx, "Deleting “$name”…") { t, w ->
            sync(t, ctx, w).run(emptyList(), emptyList(), listOf(PlaylistChange.Delete(pid)))
            "Deleted the playlist “$name” (its songs stay on the iPod)"
        }
    }

    fun removeSongs(context: Context, syncIds: List<Long>) {
        val ctx = context.applicationContext
        job(ctx, "Removing ${syncIds.size} songs…") { t, w ->
            val r = sync(t, ctx, w).run(emptyList(), syncIds, emptyList())
            "Removed ${r.removed} song${if (r.removed == 1) "" else "s"} from ${t.name}"
        }
    }

    val isConnected get() = touch != null

    fun disconnect() {
        scope.launch { lock.withLock { runCatching { touch?.close() }; touch = null; _ui.value = Ui(status = Status.IDLE, message = "Disconnected") } }
    }
}
