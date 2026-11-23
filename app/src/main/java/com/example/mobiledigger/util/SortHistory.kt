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
            if (f.exists()) f.forEachLine { line ->
                lines++
                val i = line.lastIndexOf('\t')
                if (i > 0 && i == line.length - 2) map[line.substring(0, i)] = line[i + 1]
            }
        }
        // A track re-sorted (liked, later rejected) appends a newer line; when such stale lines
        // pile up, rewrite the file with one line per track.
        if (lines > map.size + 200) runCatching {
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.bufferedWriter().use { w -> map.forEach { (k, v) -> w.write("$k\t$v\n") } }
            tmp.renameTo(f)
        }
        _version.value++
    }

    @Synchronized
    fun record(file: MusicFile, liked: Boolean) {
        if (file.size <= 0) return
        val k = key(file.name, file.size)
        val v = if (liked) 'L' else 'R'
        if (map[k] == v) return
        map[k] = v
        runCatching { this.file?.appendText("$k\t$v\n") }
        _version.value++
    }

    /** 'L' / 'R' if this exact track (same name and size) was sorted before, else null. */
    @Synchronized
    fun lookup(name: String, size: Long): Char? = if (size > 0) map[key(name, size)] else null

    @Synchronized
    fun count(): Int = map.size

    @Synchronized
    fun clear() {
        map.clear()
        runCatching { file?.delete() }
        _version.value++
    }
}
