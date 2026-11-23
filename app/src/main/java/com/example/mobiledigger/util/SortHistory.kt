package com.example.mobiledigger.util

import android.content.Context
import com.example.mobiledigger.model.MusicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Remembers every track ever sorted: name, size, Liked/Rejected, when, where it came from and
 * where it went. Used to flag a file that turns up again in To Do as already sorted (even if
 * it went to another results folder), and shown in Settings → History so you can look up a
 * file and see where you got it from. One text line per sort (~200 bytes): 10,000 tracks ≈ 2 MB.
 * Audio is never read.
 *
 * Line format (tab separated; older lines have only the first 2 or 3 fields):
 *   key  verdict(L|R|-)  epochMs  origin  destination  fileName
 * '-' marks an Undo (the track counts as not sorted again).
 */
object SortHistory {
    private const val FILE = "sort_history.tsv"

    data class Entry(
        val name: String,          // file name as it was (exact case)
        val size: Long,
        val verdict: Char,         // 'L' or 'R'
        val time: Long?,           // when it was liked/rejected (null for very old entries)
        val origin: String?,       // where it came from, e.g. "Phone › To triage › Album › track.flac"
        val destination: String?   // where it went, e.g. "Results october › Liked"
    )

    private var file: File? = null
    private val entries = HashMap<String, Entry>()        // key → latest sort
    private val beforeLast = HashMap<String, Entry?>()    // key → entry before the last record (for Undo)
    private val _version = MutableStateFlow(0)
    /** Bumps whenever the history changes, so flags and the History list can refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Turns a track's URI into a readable place + path ("Phone › To triage › …"). Set by the
     * ViewModel, which knows the saved network drives; null keeps the raw URI path.
     */
    @Volatile var describeLocation: ((android.net.Uri) -> String?)? = null

    /** "Artist - Title (1).FLAC" → "artist - title.flac": case, copy suffixes and spaces don't matter. */
    fun normName(name: String): String {
        val n = name.trim().lowercase()
        val dot = n.lastIndexOf('.')
        val (base, ext) = if (dot > 0) n.substring(0, dot) to n.substring(dot) else n to ""
        val ext2 = ext.replace(Regex("""\s*\(\d{1,3}\)$"""), "")          // "x.flac (1)"
        val base2 = base.replace(Regex("""\s*\(\d{1,3}\)$"""), "")         // "x (1).flac"
            .replace(Regex("""\s+"""), " ").trim()
        return base2 + ext2
    }

    private fun key(name: String, size: Long) = normName(name) + "|" + size
    private fun clean(s: String?) = s?.replace('\t', ' ')?.replace('\n', ' ')?.takeIf { it.isNotBlank() }

    private fun line(k: String, e: Entry?) =
        if (e == null) "$k\t-\n"
        else listOf(k, e.verdict.toString(), e.time?.toString() ?: "", clean(e.origin) ?: "", clean(e.destination) ?: "", clean(e.name) ?: "")
            .joinToString("\t").trimEnd('\t') + "\n"

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, FILE)
        file = f
        var lines = 0
        runCatching {
            if (f.exists()) f.forEachLine { l ->
                lines++
                val p = l.split('\t')
                if (p.size < 2 || p[1].length != 1) return@forEachLine
                val k = p[0]
                val v = p[1][0]
                if (v == '-') { entries.remove(k); return@forEachLine }
                val prev = entries[k]
                val size = k.substringAfterLast('|').toLongOrNull() ?: 0L
                entries[k] = Entry(
                    name = p.getOrNull(5)?.takeIf { it.isNotBlank() } ?: prev?.name ?: k.substringBeforeLast('|'),
                    size = size,
                    verdict = v,
                    time = p.getOrNull(2)?.toLongOrNull() ?: prev?.time,
                    // Older lines (times only) keep the place a previous line recorded
                    origin = p.getOrNull(3)?.takeIf { it.isNotBlank() } ?: prev?.origin,
                    destination = p.getOrNull(4)?.takeIf { it.isNotBlank() } ?: prev?.destination
                )
            }
        }
        // A track re-sorted appends a newer line; when stale lines pile up, rewrite one per track.
        if (lines > entries.size + 200) runCatching {
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.bufferedWriter().use { w -> entries.forEach { (k, e) -> w.write(line(k, e)) } }
            tmp.renameTo(f)
        }
        _version.value++
    }

    /**
     * A track was liked/rejected. [destination] is where it went (e.g. "Results october › Liked").
     * When a track is moved between Liked and Rejected, the place it ORIGINALLY came from is kept.
     */
    @Synchronized
    fun record(file: MusicFile, liked: Boolean, destination: String? = null) {
        if (file.size <= 0) return
        val k = key(file.name, file.size)
        val prev = entries[k]
        beforeLast[k] = prev
        val fromResults = file.sourcePlaylist != com.example.mobiledigger.viewmodel.PlaylistTab.TODO
        val here = runCatching { describeLocation?.invoke(file.uri) }.getOrNull() ?: file.uri.toString()
        val e = Entry(
            name = file.name,
            size = file.size,
            verdict = if (liked) 'L' else 'R',
            time = System.currentTimeMillis(),
            origin = if (fromResults && prev?.origin != null) prev.origin else here,
            destination = destination ?: prev?.destination
        )
        entries[k] = e
        runCatching { this.file?.appendText(line(k, e)) }
        // Bump even when the verdict is unchanged: the time/order and the History list changed
        _version.value++
    }

    /** Undo of a sort: put the track back to what it was before that sort (usually "never sorted"). */
    @Synchronized
    fun forget(file: MusicFile) {
        if (file.size <= 0) return
        val k = key(file.name, file.size)
        val prev = if (beforeLast.containsKey(k)) beforeLast.remove(k) else null
        if (prev == null) entries.remove(k) else entries[k] = prev
        runCatching { this.file?.appendText(line(k, prev)) }
        _version.value++
    }

    /** When this exact track (same name and size) was last liked/rejected, or null if unknown. */
    @Synchronized
    fun sortedAt(name: String, size: Long): Long? = if (size > 0) entries[key(name, size)]?.time else null

    /** 'L' / 'R' if this exact track (same name and size) was sorted before, else null. */
    @Synchronized
    fun lookup(name: String, size: Long): Char? = if (size > 0) entries[key(name, size)]?.verdict else null

    /** Every sorted track, newest first (tracks without a time last). */
    @Synchronized
    fun all(): List<Entry> = entries.values.sortedWith(compareByDescending<Entry> { it.time ?: 0L }.thenBy { it.name.lowercase() })

    @Synchronized
    fun count(): Int = entries.size

    @Synchronized
    fun clear() {
        entries.clear()
        beforeLast.clear()
        runCatching { file?.delete() }
        _version.value++
    }
}
