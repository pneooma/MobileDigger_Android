package com.example.mobiledigger.file.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.example.mobiledigger.file.drives.DriveClient
import com.example.mobiledigger.file.drives.DriveClients
import com.example.mobiledigger.file.drives.DriveStore
import com.example.mobiledigger.file.drives.NetworkDrive
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One file of a transfer. [rel] = its path under the destination folder ("2026-W32/sub/a.flac"). */
data class TransferFile(val path: String, val rel: String, val size: Long, val done: Boolean = false)

/** A copy/move from a network drive folder to a phone folder. Saved to disk so it can be resumed. */
data class TransferJob(
    val driveId: String,
    val driveName: String,
    val sourceFolder: String,
    val destTreeUri: String,
    val destLabel: String,
    val move: Boolean,
    val files: List<TransferFile>,
    /** Files copied but whose original couldn't be deleted (move only). */
    val notDeleted: Int = 0
) {
    val folderName: String get() = sourceFolder.trim('/').substringAfterLast('/').ifBlank { driveName }
    val totalBytes: Long get() = files.sumOf { it.size }
    val doneBytes: Long get() = files.filter { it.done }.sumOf { it.size }
    val doneFiles: Int get() = files.count { it.done }

    fun toJson(): JSONObject = JSONObject()
        .put("driveId", driveId).put("driveName", driveName).put("sourceFolder", sourceFolder)
        .put("destTreeUri", destTreeUri).put("destLabel", destLabel).put("move", move).put("notDeleted", notDeleted)
        .put("files", JSONArray().apply {
            files.forEach { put(JSONObject().put("p", it.path).put("r", it.rel).put("s", it.size).put("d", it.done)) }
        })

    companion object {
        fun fromJson(o: JSONObject) = TransferJob(
            o.getString("driveId"), o.optString("driveName"), o.optString("sourceFolder"),
            o.getString("destTreeUri"), o.optString("destLabel"), o.optBoolean("move"),
            o.getJSONArray("files").let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { TransferFile(it.getString("p"), it.getString("r"), it.optLong("s"), it.optBoolean("d")) } }
            },
            o.optInt("notDeleted")
        )
    }
}

enum class TransferStatus { IDLE, RUNNING, PAUSED, DONE }

data class TransferUi(
    val status: TransferStatus = TransferStatus.IDLE,
    val job: TransferJob? = null,
    /** Bytes of the file being copied right now (on top of the finished files). */
    val currentBytes: Long = 0,
    val currentName: String = "",
    /** Why it's paused (null = paused by the user), or the summary when done. */
    val message: String? = null,
    /** Smoothed download speed while running, bytes per second (0 = not known yet). */
    val speedBps: Long = 0
) {
    /** Seconds left at the current speed, or null when unknown. */
    val etaSeconds: Long? get() {
        val total = job?.totalBytes ?: return null
        if (speedBps <= 0 || status != TransferStatus.RUNNING) return null
        return ((total - doneBytes).coerceAtLeast(0) / speedBps)
    }
    val doneBytes: Long get() = (job?.doneBytes ?: 0) + currentBytes
    val fraction: Float get() = job?.totalBytes?.takeIf { it > 0 }?.let { (doneBytes.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/**
 * Copies or moves a folder from a network drive to a phone folder in the background.
 * Progress is written to disk after every file: if the app is closed, the network drops or the
 * user pauses, "Continue" picks up at the first unfinished file (that file is copied again).
 */
object TransferManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private val _state = MutableStateFlow(TransferUi())
    val state: StateFlow<TransferUi> = _state.asStateFlow()
    private lateinit var app: Context

    private const val MARGIN = 100L * 1024 * 1024 // keep 100 MB free on the phone

    private fun jobFile() = File(app.filesDir, "transfer_job.json")

    /** Call once at startup: restores an unfinished transfer as "paused". */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        runCatching {
            val f = jobFile()
            if (f.exists()) {
                val job = TransferJob.fromJson(JSONObject(f.readText()))
                if (job.doneFiles < job.files.size) {
                    _state.value = TransferUi(TransferStatus.PAUSED, job, message = "Interrupted (the app was closed)")
                } else f.delete()
            }
        }
    }

    // ---------------------------------------------------------------- planning

    data class Plan(val files: List<TransferFile>, val totalBytes: Long)

    /** Lists everything under [folder] on [drive] (all files, not just audio). */
    fun scan(drive: NetworkDrive, folder: String): Plan {
        val client = DriveClients.create(drive)
        try {
            val root = folder.trim('/')
            val top = root.substringAfterLast('/').ifBlank { drive.name.ifBlank { "Network" } }
            val out = mutableListOf<TransferFile>()
            fun walk(path: String, depth: Int) {
                if (depth > 15 || out.size > 20000) return
                for (e in client.list(path).sortedBy { it.name.lowercase() }) {
                    if (e.name.startsWith(".")) continue
                    if (e.isDir) walk(e.path, depth + 1)
                    else {
                        val inside = if (root.isEmpty()) e.path.trim('/') else e.path.trim('/').removePrefix("$root/")
                        out += TransferFile(e.path, "$top/$inside", e.size)
                    }
                }
            }
            walk(root, 0)
            return Plan(out, out.sumOf { it.size })
        } finally {
            runCatching { client.close() }
        }
    }

    fun canWrite(drive: NetworkDrive): Boolean = DriveClients.create(drive).let { c -> try { c.canWrite } finally { runCatching { c.close() } } }

    /** Free bytes on the storage holding [treeUri], or null if it can't be told. */
    fun freeBytes(context: Context, treeUri: Uri): Long? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val volumeId = docId.substringBefore(':')
        val sm = context.getSystemService(StorageManager::class.java)
        val vol = sm.storageVolumes.firstOrNull { v ->
            if (volumeId.equals("primary", true)) v.isPrimary else v.uuid.equals(volumeId, true)
        }
        val dir = vol?.directory ?: return@runCatching null
        StatFs(dir.path).availableBytes
    }.getOrNull()

    fun spaceProblem(context: Context, treeUri: Uri, neededBytes: Long): String? {
        val free = freeBytes(context, treeUri) ?: return null
        return if (neededBytes + MARGIN > free)
            "Not enough space on the phone: needs ${fmt(neededBytes)}, ${fmt(free)} free."
        else null
    }

    // ---- speed: exponential moving average of bytes/s, sampled at most every 0.5 s
    private var speedT = 0L
    private var speedBytes = 0L
    private var speedEma = 0.0

    private fun resetSpeed() { speedT = 0L; speedBytes = 0L; speedEma = 0.0 }

    /** Publishes [currentBytes] of the file in progress, updating the speed. */
    private fun report(currentBytes: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        val st = _state.value.copy(currentBytes = currentBytes)
        val total = st.doneBytes
        if (speedT == 0L) { speedT = now; speedBytes = total }
        val dt = now - speedT
        if (dt >= 500) {
            val inst = (total - speedBytes).coerceAtLeast(0) * 1000.0 / dt
            speedEma = if (speedEma <= 0.0) inst else speedEma * 0.7 + inst * 0.3
            speedT = now; speedBytes = total
        }
        _state.value = st.copy(speedBps = speedEma.toLong())
    }

    /** "4.2 MB/s" */
    fun fmtSpeed(bps: Long): String = when {
        bps >= 1024L * 1024 -> "%.1f MB/s".format(bps / (1024.0 * 1024.0))
        bps >= 1024 -> "${bps / 1024} KB/s"
        else -> "$bps B/s"
    }

    /** "1:23 left" / "1 h 05 min left" */
    fun fmtEta(sec: Long): String = when {
        sec >= 3600 -> "${sec / 3600} h ${"%02d".format((sec % 3600) / 60)} min left"
        else -> "${sec / 60}:${"%02d".format(sec % 60)} left"
    }

    fun fmt(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", b / (1024.0 * 1024))
        else -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
    }

    // ---------------------------------------------------------------- control

    fun start(context: Context, job: TransferJob) {
        init(context)
        if (_state.value.status == TransferStatus.RUNNING) return
        save(job)
        run(job)
    }

    /** Continue a paused / interrupted transfer. */
    fun resume(context: Context) {
        init(context)
        val job = _state.value.job ?: return
        if (_state.value.status == TransferStatus.RUNNING) return
        run(job)
    }

    fun pause() {
        if (_state.value.status != TransferStatus.RUNNING) return
        worker?.cancel()
        _state.value = _state.value.copy(status = TransferStatus.PAUSED, currentBytes = 0, message = null)
    }

    /** Stops for good. Files already copied stay on the phone. */
    fun abort() {
        worker?.cancel()
        val job = _state.value.job
        jobFile().delete()
        _state.value = TransferUi(
            TransferStatus.DONE, job,
            message = job?.let { "Stopped. ${it.doneFiles} of ${it.files.size} files are on the phone." }
        )
    }

    fun dismiss() { if (_state.value.status == TransferStatus.DONE) _state.value = TransferUi() }

    private fun save(job: TransferJob) = runCatching { jobFile().writeText(job.toJson().toString()) }

    private fun run(start: TransferJob) {
        val tree = Uri.parse(start.destTreeUri)
        spaceProblem(app, tree, start.files.filterNot { it.done }.sumOf { it.size })?.let {
            _state.value = TransferUi(TransferStatus.PAUSED, start, message = it)
            return
        }
        resetSpeed()
        _state.value = TransferUi(TransferStatus.RUNNING, start)
        runCatching {
            app.startForegroundService(Intent(app, TransferService::class.java))
        }.onFailure { CrashLogger.log("TransferManager", "Couldn't start the foreground service", it) }

        worker = scope.launch {
            var job = start
            val drive = DriveStore(app).get(job.driveId)
            if (drive == null) {
                _state.value = TransferUi(TransferStatus.PAUSED, job, message = "The network drive \"${job.driveName}\" is no longer saved")
                return@launch
            }
            var client: DriveClient = DriveClients.create(drive)
            val saf = SafTree(app, tree)
            try {
                for (i in job.files.indices) {
                    val f = job.files[i]
                    if (f.done) continue
                    if (!isActive) return@launch
                    _state.value = _state.value.copy(currentName = f.rel.substringAfterLast('/'), currentBytes = 0)
                    var attempt = 0
                    while (true) {
                        try {
                            copyOne(client, saf, f) { isActive }
                            break
                        } catch (e: Exception) {
                            if (!isActive) return@launch
                            attempt++
                            CrashLogger.log("TransferManager", "Copy failed (try $attempt): ${f.rel}", e)
                            runCatching { client.close() }
                            if (attempt >= 3) {
                                _state.value = _state.value.copy(
                                    status = TransferStatus.PAUSED, currentBytes = 0,
                                    message = "Interrupted: ${e.message ?: "connection lost"}"
                                )
                                return@launch
                            }
                            delay(2000L * attempt)
                            client = DriveClients.create(drive)
                        }
                    }
                    var notDeleted = job.notDeleted
                    if (job.move) {
                        try { client.delete(f.path) } catch (e: Exception) {
                            notDeleted++
                            CrashLogger.log("TransferManager", "Copied but couldn't delete the original: ${f.path}", e)
                        }
                    }
                    job = job.copy(files = job.files.toMutableList().also { it[i] = f.copy(done = true) }, notDeleted = notDeleted)
                    save(job)
                    _state.value = _state.value.copy(job = job, currentBytes = 0)
                }
                jobFile().delete()
                val verb = if (job.move) "Moved" else "Copied"
                _state.value = TransferUi(
                    TransferStatus.DONE, job,
                    message = "$verb ${job.files.size} files (${fmt(job.totalBytes)}) to ${job.destLabel}/${job.folderName}" +
                        if (job.notDeleted > 0) ". ${job.notDeleted} originals couldn't be deleted from ${job.driveName}." else ""
                )
                CrashLogger.log("TransferManager", "Transfer finished: ${job.files.size} files")
            } finally {
                runCatching { client.close() }
            }
        }
    }

    private fun copyOne(client: DriveClient, saf: SafTree, f: TransferFile, alive: () -> Boolean) {
        val dir = saf.dirFor(f.rel.substringBeforeLast('/', ""))
        val name = f.rel.substringAfterLast('/')
        val target = saf.fileFor(dir, name) // existing (overwritten) or new
        var written = 0L
        client.open(f.path, 0).use { rs ->
            (app.contentResolver.openOutputStream(target, "wt") ?: throw Exception("Can't write $name")).use { out ->
                val buf = ByteArray(256 * 1024)
                var lastReport = 0L
                var lastReportT = 0L
                while (true) {
                    if (!alive()) throw java.util.concurrent.CancellationException("paused")
                    val n = rs.input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                    // Report every 2 MB or every 0.5 s (slow links), which also drives the speed
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (written - lastReport > 2L * 1024 * 1024 || now - lastReportT >= 500) {
                        lastReport = written; lastReportT = now
                        report(written)
                    }
                }
            }
        }
        if (f.size > 0 && written != f.size) throw Exception("$name: only ${fmt(written)} of ${fmt(f.size)} arrived")
    }

    /** Folders/files inside a SAF tree, found by name (so a resumed copy reuses them). */
    private class SafTree(private val context: Context, private val tree: Uri) {
        private val cr = context.contentResolver
        private val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        private val dirs = HashMap<String, Uri>().apply { put("", rootDoc) }

        private fun child(parent: Uri, name: String): Uri? {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
            cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
            return null
        }

        fun dirFor(rel: String): Uri = dirs.getOrPut(rel) {
            val parent = dirFor(rel.substringBeforeLast('/', ""))
            val name = rel.substringAfterLast('/')
            child(parent, name) ?: DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: throw Exception("Can't create folder $name")
        }

        fun fileFor(dir: Uri, name: String): Uri =
            child(dir, name) ?: DocumentsContract.createDocument(cr, dir, "application/octet-stream", name)
                ?: throw Exception("Can't create $name")
    }
}
