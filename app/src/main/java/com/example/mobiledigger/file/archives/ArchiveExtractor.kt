package com.example.mobiledigger.file.archives

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** Where extracted files go. [relPath] is '/'-separated, already sanitised, never empty. */
fun interface ExtractSink {
    fun file(relPath: String, input: InputStream, size: Long)
}

/** One file inside an archive. [path] is the sanitised relative path used when extracting. */
data class ArchiveEntry(val path: String, val size: Long) {
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/', "")
    val isAudio: Boolean get() = path.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aif", "aiff")
}

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pure-JVM listing and extraction of ZIP / 7z / RAR(4) archives.
 *
 * ZIP and 7z work on a [SeekableByteChannel], so a network archive can be read in place:
 * listing only touches the table of contents, and extracting a few files only reads those.
 * RAR needs a real file, so it is copied to [tempDir] first.
 */
object ArchiveExtractor {

    fun typeOf(name: String) = name.substringAfterLast('.', "").lowercase()

    /**
     * Safe relative path for an entry, or null to skip it.
     * Drops macOS junk, absolute paths and anything trying to climb out with "..".
     */
    fun safeRel(raw: String): String? {
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        if (parts.first() == "__MACOSX") return null
        val last = parts.last()
        if (last == ".DS_Store" || last.startsWith("._") || last.equals("Thumbs.db", true)) return null
        return parts.joinToString("/") { it.replace(Regex("[:*?\"<>|]"), "_") }
    }

    // ---------------------------------------------------------------- listing

    fun list(ch: SeekableByteChannel, type: String, tempDir: File): List<ArchiveEntry> = when (type) {
        "zip" -> wrap("ZIP") {
            ZipFile.builder().setSeekableByteChannel(ch).get().use { zf ->
                if (zf.entries.toList().any { it.generalPurposeBit.usesEncryption() }) throw ArchiveException("This ZIP is password protected")
                zf.entries.toList().filter { !it.isDirectory }.mapNotNull { e -> safeRel(e.name)?.let { ArchiveEntry(it, e.size) } }
            }
        }
        "7z" -> wrap("7z") {
            SevenZFile.builder().setSeekableByteChannel(ch).get().use { sz ->
                sz.entries.filter { !it.isDirectory && it.hasStream() }.mapNotNull { e -> safeRel(e.name)?.let { ArchiveEntry(it, e.size) } }
            }
        }
        "rar" -> withRarFile(ch, tempDir) { f -> listRar(f) }
        else -> throw ArchiveException("Unsupported archive type: .$type")
    }

    fun list(archive: File): List<ArchiveEntry> = when (typeOf(archive.name)) {
        "rar" -> listRar(archive)
        else -> Files.newByteChannel(archive.toPath(), StandardOpenOption.READ).use { list(it, typeOf(archive.name), archive.parentFile ?: File(".")) }
    }

    private fun listRar(f: File): List<ArchiveEntry> = wrap("RAR") {
        Archive(f).use { ar ->
            if (ar.isEncrypted) throw ArchiveException("This RAR is password protected")
            ar.fileHeaders.filter { !it.isDirectory }.mapNotNull { h -> safeRel(h.fileName)?.let { ArchiveEntry(it, h.fullUnpackSize) } }
        }
    }

    // ---------------------------------------------------------------- extracting

    /**
     * Extracts the entries whose path is in [include] (all when null) into [sink].
     * Returns how many files were written. [onProgress] gets (done, total, current path).
     */
    fun extract(
        ch: SeekableByteChannel, type: String, sink: ExtractSink, tempDir: File,
        include: Set<String>? = null,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Int = when (type) {
        "zip" -> wrap("ZIP") {
            var n = 0
            ZipFile.builder().setSeekableByteChannel(ch).get().use { zf ->
                val all = zf.entries.toList().filter { !it.isDirectory }
                    .mapNotNull { e -> safeRel(e.name)?.let { it to e } }
                    .filter { (rel, _) -> include == null || rel in include }
                for ((rel, e) in all) {
                    if (e.generalPurposeBit.usesEncryption()) throw ArchiveException("This ZIP is password protected")
                    onProgress(n, all.size, rel)
                    zf.getInputStream(e).use { sink.file(rel, it, e.size) }
                    n++
                }
            }
            n
        }
        "7z" -> wrap("7z") {
            var n = 0
            SevenZFile.builder().setSeekableByteChannel(ch).get().use { sz ->
                val all = sz.entries.filter { !it.isDirectory && it.hasStream() }
                    .mapNotNull { e -> safeRel(e.name)?.let { it to e } }
                    .filter { (rel, _) -> include == null || rel in include }
                for ((rel, e) in all) {
                    onProgress(n, all.size, rel)
                    sz.getInputStream(e).use { sink.file(rel, it, e.size) }
                    n++
                }
            }
            n
        }
        "rar" -> withRarFile(ch, tempDir) { f -> extractRar(f, sink, include, onProgress) }
        else -> throw ArchiveException("Unsupported archive type: .$type")
    }

    /** File-based convenience (tests, local copies). */
    fun extract(archive: File, sink: ExtractSink, include: Set<String>? = null, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): Int =
        when (typeOf(archive.name)) {
            "rar" -> extractRar(archive, sink, include, onProgress)
            else -> Files.newByteChannel(archive.toPath(), StandardOpenOption.READ).use {
                extract(it, typeOf(archive.name), sink, archive.parentFile ?: File("."), include, onProgress)
            }
        }

    private fun extractRar(f: File, sink: ExtractSink, include: Set<String>?, onProgress: (Int, Int, String) -> Unit): Int = wrap("RAR") {
        var n = 0
        Archive(f).use { ar ->
            if (ar.isEncrypted) throw ArchiveException("This RAR is password protected")
            val all = ar.fileHeaders.filter { !it.isDirectory }
                .mapNotNull { h -> safeRel(h.fileName)?.let { it to h } }
                .filter { (rel, _) -> include == null || rel in include }
            for ((rel, h) in all) {
                if (h.isEncrypted) throw ArchiveException("This RAR is password protected")
                onProgress(n, all.size, rel)
                ar.getInputStream(h).use { sink.file(rel, it, h.fullUnpackSize) }
                n++
            }
        }
        n
    }

    // ---------------------------------------------------------------- helpers

    private fun <T> withRarFile(ch: SeekableByteChannel, tempDir: File, block: (File) -> T): T {
        tempDir.mkdirs()
        val tmp = File.createTempFile("archive", ".rar", tempDir)
        try {
            ch.position(0)
            Channels.newInputStream(ch).use { input -> tmp.outputStream().use { input.copyTo(it, 256 * 1024) } }
            return block(tmp)
        } finally {
            tmp.delete()
        }
    }

    private inline fun <T> wrap(kind: String, block: () -> T): T = try {
        block()
    } catch (e: ArchiveException) {
        throw e
    } catch (e: UnsupportedRarV5Exception) {
        throw ArchiveException("This is a RAR5 archive. Only older RAR (v4) files can be unpacked in the app.", e)
    } catch (e: Exception) {
        val msg = e.message ?: e.javaClass.simpleName
        throw ArchiveException(if ("password" in msg.lowercase()) "This $kind is password protected" else "Couldn't read the $kind: $msg", e)
    }
}
