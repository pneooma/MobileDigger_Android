package com.example.mobiledigger.file.drives

import java.io.InputStream

/** One file or folder on a network drive. [path] is relative to the drive root, '/'-separated, no leading '/'. */
data class RemoteEntry(val name: String, val path: String, val isDir: Boolean, val size: Long)

/** An open byte stream starting at the requested offset. [totalSize] = full file size (-1 if unknown). */
class RemoteStream(val input: InputStream, val totalSize: Long, private val onClose: () -> Unit = {}) : AutoCloseable {
    override fun close() {
        runCatching { input.close() }
        runCatching { onClose() }
    }
}

/** Blocking protocol client. Call from a background thread. */
interface DriveClient {
    /** One folder level. Throws with a human-readable message when the drive can't be reached. */
    fun list(path: String): List<RemoteEntry>

    /** Opens [path] for reading from byte [offset]. */
    fun open(path: String, offset: Long): RemoteStream

    /** False for servers that only allow reading (the MobileDigger/Pi library server). */
    val canWrite: Boolean get() = true

    /** Deletes a file. Throws [DriveException] with a readable reason when it can't. */
    fun delete(path: String)

    /** Deletes a folder and everything in it (used to clean up a stopped unpack). */
    fun deleteFolder(path: String): Unit = throw DriveException("Deleting folders isn't supported on this drive")

    /** Creates [path] and any missing parent folders. */
    fun mkdirs(path: String)

    /** Writes [input] to [path], replacing an existing file. [length] = -1 if unknown. */
    fun write(path: String, input: InputStream, length: Long)

    fun close() {}
}

class DriveException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DriveClients {
    val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aif", "aiff")
    val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z")

    fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
    fun isArchive(name: String) = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

    fun create(drive: NetworkDrive): DriveClient = when (drive.type) {
        DriveType.MOBILEDIGGER -> MobileDiggerServerClient(drive)
        DriveType.WEBDAV -> WebDavClient(drive)
        DriveType.SMB -> SmbDriveClient(drive)
    }

    /** Recursively collects audio files (and archives) under [root] as (folder, file) pairs, up to [limit]. */
    fun listAudioRecursive(
        client: DriveClient, root: String, limit: Int = 2000,
        archives: MutableList<Pair<String, RemoteEntry>>? = null
    ): List<Pair<String, RemoteEntry>> {
        val out = mutableListOf<Pair<String, RemoteEntry>>()
        fun walk(path: String, depth: Int) {
            if (out.size >= limit || depth > 12) return
            val entries = try { client.list(path) } catch (e: Exception) {
                if (depth == 0) throw e else return
            }
            entries.filter { !it.isDir && isAudio(it.name) }.sortedBy { it.name.lowercase() }
                .forEach { if (out.size < limit) out.add(path to it) }
            archives?.let { a -> entries.filter { !it.isDir && isArchive(it.name) }.forEach { a.add(path to it) } }
            entries.filter { it.isDir }.sortedBy { it.name.lowercase() }.forEach { walk(it.path, depth + 1) }
        }
        walk(root.trim('/'), 0)
        return out
    }
}

internal fun joinPath(parent: String, name: String): String =
    if (parent.isBlank()) name.trim('/') else parent.trim('/') + "/" + name.trim('/')
