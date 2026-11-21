package com.example.mobiledigger.file.archives

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.mobiledigger.file.drives.DriveClient
import com.example.mobiledigger.file.drives.DriveClients
import com.example.mobiledigger.file.drives.DriveStore
import com.example.mobiledigger.util.CrashLogger
import java.io.File
import java.io.InputStream

/** A .zip / .rar / .7z found in the music folder. Either local (SAF) or on a network drive. */
data class ArchiveItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    /** Folder it sits in, relative to the loaded root (for display / grouping). */
    val folder: String?,
    /** Local: SAF document URI of the containing folder (built with the tree URI). */
    val parentDocUri: Uri? = null,
    /** Network: drive id + archive path + containing folder path inside the drive. */
    val driveId: String? = null,
    val drivePath: String? = null,
    val driveFolder: String? = null
) {
    val type: String get() = name.substringAfterLast('.', "").uppercase()
    val isRemote: Boolean get() = driveId != null
}

data class UnpackOutcome(
    val files: Int,
    val folderName: String,
    val archiveDeleted: Boolean,
    val deleteProblem: String?,
    /** Local: SAF document URI of the new folder. */
    val newFolderUri: Uri? = null,
    /** Path of the new folder: relative to the loaded root (local) or to the drive root (network). */
    val newFolderPath: String = ""
)

/**
 * Unpacks an archive next to itself, into a new folder named after the archive
 * ("Album.zip" → "Album/"), then deletes the archive. Works for phone folders (SAF) and for
 * writable network drives (SMB / WebDAV). The archive is copied to the app cache first,
 * because ZIP and 7z need random access.
 */
class ArchiveUnpacker(private val context: Context) {
    private val cr = context.contentResolver

    /** Opens the archive for random access: SAF file channel, or a [RemoteChannel] on the drive. */
    private fun <T> withChannel(item: ArchiveItem, client: DriveClient?, block: (java.nio.channels.SeekableByteChannel) -> T): T {
        if (client != null) {
            val size = item.size.takeIf { it > 0 } ?: client.open(item.drivePath!!, 0).use { it.totalSize }
            if (size <= 0) throw ArchiveException("Can't tell how big ${item.name} is")
            return com.example.mobiledigger.file.drives.RemoteChannel(client, item.drivePath!!, size).use(block)
        }
        val pfd = cr.openFileDescriptor(item.uri, "r") ?: throw ArchiveException("Can't open ${item.name}")
        return pfd.use { java.io.FileInputStream(it.fileDescriptor).channel.use(block) }
    }

    private fun clientFor(item: ArchiveItem): DriveClient? {
        if (!item.isRemote) return null
        val drive = DriveStore(context).get(item.driveId) ?: throw ArchiveException("That network drive was removed")
        return DriveClients.create(drive)
    }

    /** What's inside the archive (reads only the table of contents for ZIP / 7z). */
    fun list(item: ArchiveItem): List<ArchiveEntry> {
        val client = clientFor(item)
        try {
            val tmp = File(context.cacheDir, "unpack").apply { mkdirs() }
            return withChannel(item, client) { ch -> ArchiveExtractor.list(ch, ArchiveExtractor.typeOf(item.name), tmp) }
        } finally {
            runCatching { client?.close() }
        }
    }

    /**
     * Extracts [include] (null = everything) into a new folder next to the archive, then deletes
     * the archive if [deleteArchive]. [onProgress] gets (fraction 0..1, what's happening).
     */
    fun unpack(
        item: ArchiveItem,
        include: Set<String>? = null,
        deleteArchive: Boolean = true,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): UnpackOutcome {
        val client = clientFor(item)
        try {
            if (client != null && !client.canWrite) {
                throw ArchiveException("${item.name} is on a read-only drive, so it can't be unpacked there")
            }
            if (include != null && include.isEmpty()) throw ArchiveException("No files selected")
            val tmp = File(context.cacheDir, "unpack").apply { deleteRecursively(); mkdirs() }

            // Extract straight from the archive (no full copy for ZIP / 7z)
            val base = item.name.substringBeforeLast('.').trim().ifBlank { "Unpacked" }
            val sink = if (client != null) DriveSink(client, item.driveFolder ?: "", base)
                else SafSink(item.parentDocUri ?: throw ArchiveException("Can't find the folder this archive is in"), base)
            onProgress(0f, "Opening the archive")
            val count = try {
                withChannel(item, client) { ch ->
                    ArchiveExtractor.extract(ch, ArchiveExtractor.typeOf(item.name), { rel, input, size ->
                        if (isCancelled()) throw java.util.concurrent.CancellationException("stopped")
                        // Check while copying too, so Stop reacts mid-way through a big file
                        val guarded = object : java.io.FilterInputStream(input) {
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                if (isCancelled()) throw java.util.concurrent.CancellationException("stopped")
                                return super.read(b, off, len)
                            }
                        }
                        sink.file(rel, guarded, size)
                    }, tmp, include) { done, total, rel ->
                        if (isCancelled()) throw java.util.concurrent.CancellationException("stopped")
                        onProgress(0.95f * (done.toFloat() / total.coerceAtLeast(1)), rel.substringAfterLast('/'))
                    }
                }
            } catch (e: Throwable) {
                // Stopped or failed half way: remove the half-filled new folder. The archive is untouched.
                runCatching {
                    if (sink is SafSink) DocumentsContract.deleteDocument(cr, sink.rootUri)
                    else (sink as DriveSink).removeAll()
                }.onFailure { CrashLogger.log("ArchiveUnpacker", "Couldn't clean up after a stopped unpack", it) }
                if (e is ArchiveException && e.cause is java.util.concurrent.CancellationException) throw e.cause!!
                throw e
            }
            if (count == 0) throw ArchiveException("Nothing was extracted from ${item.name}")

            var problem: String? = null
            var deleted = false
            if (deleteArchive) {
                onProgress(0.97f, "Deleting the archive")
                deleted = try {
                    if (client != null) { client.delete(item.drivePath!!); true }
                    else DocumentsContract.deleteDocument(cr, item.uri)
                } catch (e: Exception) {
                    problem = e.message ?: "unknown error"; false
                }
            }
            CrashLogger.log("ArchiveUnpacker", "Unpacked ${item.name}: $count files into '${sink.folderName}', archive deleted=$deleted")
            val parent = (if (client != null) item.driveFolder else item.folder)?.trim('/').orEmpty()
            return UnpackOutcome(
                count, sink.folderName, deleted, problem,
                newFolderUri = (sink as? SafSink)?.rootUri,
                newFolderPath = if (parent.isEmpty()) sink.folderName else "$parent/${sink.folderName}"
            )
        } finally {
            runCatching { client?.close() }
        }
    }

    private interface NamedSink : ExtractSink { val folderName: String }

    /** Writes into a new folder inside a SAF folder. */
    private inner class SafSink(private val parent: Uri, base: String) : NamedSink {
        private val root: Uri = DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, base)
            ?: throw ArchiveException("Couldn't create a folder for the unpacked files")
        val rootUri: Uri get() = root
        override val folderName: String = queryName(root) ?: base
        private val dirs = HashMap<String, Uri>().apply { put("", root) }

        private fun dir(rel: String): Uri = dirs.getOrPut(rel) {
            val parentUri = dir(rel.substringBeforeLast('/', ""))
            DocumentsContract.createDocument(cr, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, rel.substringAfterLast('/'))
                ?: throw ArchiveException("Couldn't create folder $rel")
        }

        override fun file(relPath: String, input: InputStream, size: Long) {
            val d = dir(relPath.substringBeforeLast('/', ""))
            val name = relPath.substringAfterLast('/')
            val doc = DocumentsContract.createDocument(cr, d, mimeFor(name), name)
                ?: throw ArchiveException("Couldn't create $name")
            (cr.openOutputStream(doc, "w") ?: throw ArchiveException("Couldn't write $name")).use { input.copyTo(it, 256 * 1024) }
        }

        private fun queryName(uri: Uri): String? = runCatching {
            cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /** Writes into a new folder on a network drive. */
    private class DriveSink(private val client: DriveClient, parentFolder: String, base: String) : NamedSink {
        override val folderName: String
        private val root: String
        private val made = HashSet<String>()

        init {
            val existing = runCatching { client.list(parentFolder).map { it.name.lowercase() }.toSet() }.getOrDefault(emptySet())
            var name = base
            var i = 2
            while (name.lowercase() in existing) name = "$base ($i)".also { i++ }
            folderName = name
            root = if (parentFolder.isBlank()) name else "${parentFolder.trim('/')}/$name"
            client.mkdirs(root)
            made.add(root)
        }

        fun removeAll() = client.deleteFolder(root)

        override fun file(relPath: String, input: InputStream, size: Long) {
            val full = "$root/$relPath"
            val dir = full.substringBeforeLast('/')
            if (made.add(dir)) client.mkdirs(dir)
            client.write(full, input, size)
        }
    }

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "wav" -> "audio/wav"
        "aif", "aiff" -> "audio/aiff"; "m4a", "aac" -> "audio/mp4"; "ogg" -> "audio/ogg"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "txt", "nfo", "cue", "log" -> "text/plain"
        else -> "application/octet-stream"
    }
}
