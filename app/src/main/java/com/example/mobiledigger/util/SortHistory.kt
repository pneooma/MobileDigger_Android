package com.example.mobiledigger.util

import android.content.Context
import com.example.mobiledigger.model.MusicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Remembers every track ever sorted (name + size + Liked/Rejected), so a file that turns up
 * again in To Do can be flagged as already sorted, even if it went to another results folder.
 * One short text line per track (~100 bytes): 10,000 tracks ≈ 1 MB. Audio is never read.
 */
object SortHistory {
    private const val FILE = "sort_history.tsv"
    private var file: File? = null
    private val map = HashMap<String, Char>()          // key → 'L' or 'R' (last sort wins)
    private val beforeLast = HashMap<String, Char?>()  // key → value before the last record (for Undo)
    private val times = HashMap<String, Long>()         // key → when it was last sorted (epoch ms)
    private val beforeLastTime = HashMap<String, Long?>()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever a track is recorded, so flags can refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

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

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, FILE)
        file = f
        var lines = 0
        runCatching {
            // Lines: "key<TAB>L|R|-" (older) or "key<TAB>L|R|-<TAB>epochMs" ('-' = undone)
            if (f.exists()) f.forEachLine { line ->
                lines++
                val parts = line.split('\t')
                if (parts.size < 2 || parts[1].length != 1) return@forEachLine
                val k = parts[0]
                val v = parts[1][0]
                if (v == '-') { map.remove(k); times.remove(k) }
                else {
                    map[k] = v
                    val t = parts.getOrNull(2)?.toLongOrNull()
                    if (t != null) times[k] = t else times.remove(k)
                }
            }
        }
        // A track re-sorted (liked, later rejected) appends a newer line; when such stale lines
        // pile up, rewrite the file with one line per track.
        if (lines > map.size + 200) runCatching {
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.bufferedWriter().use { w ->
                map.forEach { (k, v) -> w.write(times[k]?.let { "$k\t$v\t$it\n" } ?: "$k\t$v\n") }
            }
            tmp.renameTo(f)
        }
        _version.value++
    }

    @Synchronized
    fun record(file: MusicFile, liked: Boolean) {
        if (file.size <= 0) return
        val k = key(file.name, file.size)
        val v = if (liked) 'L' else 'R'
        beforeLast[k] = map[k]
        beforeLastTime[k] = times[k]
        // The time is kept even when the verdict is unchanged: Liked / Rejected are ordered by it
        val now = System.currentTimeMillis()
        val changed = map[k] != v
        map[k] = v
        times[k] = now
        runCatching { this.file?.appendText("$k\t$v\t$now\n") }
        if (changed) _version.value++
    }

    /** Undo of a sort: put the track back to what it was before that sort (usually "never sorted"). */
    @Synchronized
    fun forget(file: MusicFile) {
        if (file.size <= 0) return
        val k = key(file.name, file.size)
        val prev = if (beforeLast.containsKey(k)) beforeLast.remove(k) else null
        val prevTime = beforeLastTime.remove(k)
        if (prev == null) { map.remove(k); times.remove(k) } else {
            map[k] = prev
            if (prevTime != null) times[k] = prevTime else times.remove(k)
        }
        runCatching { this.file?.appendText("$k\t${prev ?: '-'}" + (if (prev != null && prevTime != null) "\t$prevTime" else "") + "\n") }
        _version.value++
    }

    /** When this exact track (same name and size) was last liked/rejected, or null if unknown. */
    @Synchronized
    fun sortedAt(name: String, size: Long): Long? = if (size > 0) times[key(name, size)] else null

    /** 'L' / 'R' if this exact track (same name and size) was sorted before, else null. */
    @Synchronized
    fun lookup(name: String, size: Long): Char? = if (size > 0) map[key(name, size)] else null

    @Synchronized
    fun count(): Int = map.size

    @Synchronized
    fun clear() {
        map.clear()
        times.clear()
        runCatching { file?.delete() }
        _version.value++
    }
}
