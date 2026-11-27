package com.example.mobiledigger.ipod

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AfcException(val status: Long, what: String) : IOException("$what [${AfcClient.statusName(status)}]")

/**
 * Apple File Conduit (com.apple.afc): the iPod's media partition as files. Packets are a 40-byte
 * little-endian header ("CFA6LPAA", total length, header length, packet number, operation),
 * header data, then payload.
 */
class AfcClient(private val s: DeviceStream) {
    companion object {
        private val MAGIC = "CFA6LPAA".toByteArray(Charsets.US_ASCII)
        private const val OP_STATUS = 1L; private const val OP_DATA = 2L; private const val OP_READ_DIR = 3L
        private const val OP_REMOVE = 8L; private const val OP_MKDIR = 9L; private const val OP_INFO = 10L
        private const val OP_OPEN = 13L; private const val OP_OPEN_RES = 14L; private const val OP_READ = 15L
        private const val OP_WRITE = 16L; private const val OP_CLOSE = 20L; private const val OP_RENAME = 24L
        private const val MODE_RDONLY = 1L; private const val MODE_WRONLY = 3L
        private const val CHUNK = 64 * 1024
        const val NOT_FOUND = 8L
        const val EXISTS = 16L

        fun statusName(c: Long) = when (c.toInt()) {
            0 -> "ok"; 7 -> "invalid argument"; 8 -> "not found"; 9 -> "is a directory"; 10 -> "permission denied"
            16 -> "already exists"; 17 -> "busy"; 18 -> "no space left"; 20 -> "I/O error"; else -> "AFC error $c"
        }
    }

    private var packetNum = 0L

    private class Reply(val op: Long, val header: ByteArray, val payload: ByteArray)

    private fun le(vararg v: Long): ByteArray {
        val b = ByteBuffer.allocate(8 * v.size).order(ByteOrder.LITTLE_ENDIAN); v.forEach { b.putLong(it) }; return b.array()
    }

    @Synchronized
    private fun call(op: Long, header: ByteArray, payload: ByteArray = ByteArray(0), payloadLen: Int = payload.size): Reply {
        val thisLen = 40L + header.size
        val h = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        h.put(MAGIC); h.putLong(thisLen + payloadLen); h.putLong(thisLen); h.putLong(packetNum++); h.putLong(op)
        s.write(if (payloadLen == 0) h.array() + header else h.array() + header + payload.copyOf(payloadLen))
        val rh = ByteBuffer.wrap(s.readFully(40, 120_000)).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8); rh.get(magic)
        if (!magic.contentEquals(MAGIC)) throw IOException("Bad AFC reply")
        val entire = rh.long; val hl = rh.long; rh.long; val rop = rh.long
        val hdr = if (hl > 40) s.readFully((hl - 40).toInt(), 120_000) else ByteArray(0)
        val pl = if (entire > hl) s.readFully((entire - hl).toInt(), 120_000) else ByteArray(0)
        return Reply(rop, hdr, pl)
    }

    private fun check(r: Reply, what: String): Reply {
        if (r.op == OP_STATUS) {
            val code = ByteBuffer.wrap(r.header).order(ByteOrder.LITTLE_ENDIAN).long
            if (code != 0L) throw AfcException(code, what)
        }
        return r
    }

    private fun path(p: String) = (p + "\u0000").toByteArray(Charsets.UTF_8)

    fun list(dir: String): List<String> {
        val r = check(call(OP_READ_DIR, path(dir)), "List $dir")
        return String(r.payload, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() && it != "." && it != ".." }
    }

    /** File info (st_size, st_ifmt…), or null if it doesn't exist. */
    fun info(p: String): Map<String, String>? {
        val r = call(OP_INFO, path(p))
        if (r.op == OP_STATUS) {
            val code = ByteBuffer.wrap(r.header).order(ByteOrder.LITTLE_ENDIAN).long
            if (code == NOT_FOUND) return null
            if (code != 0L) throw AfcException(code, "Info $p")
        }
        val parts = String(r.payload, Charsets.UTF_8).split('\u0000')
        val m = HashMap<String, String>()
        var i = 0
        while (i + 1 < parts.size) { m[parts[i]] = parts[i + 1]; i += 2 }
        return m
    }

    fun isDirectory(p: String) = info(p)?.get("st_ifmt") == "S_IFDIR"

    fun makeDirectory(p: String) {
        val r = call(OP_MKDIR, path(p))
        if (r.op == OP_STATUS) {
            val code = ByteBuffer.wrap(r.header).order(ByteOrder.LITTLE_ENDIAN).long
            if (code != 0L && code != EXISTS) throw AfcException(code, "Make folder $p")
        }
    }

    fun remove(p: String) { check(call(OP_REMOVE, path(p)), "Delete $p") }

    fun rename(from: String, to: String) { check(call(OP_RENAME, path(from) + path(to)), "Rename $from") }

    private fun open(p: String, mode: Long): Long {
        val r = check(call(OP_OPEN, le(mode) + path(p)), "Open $p")
        if (r.op != OP_OPEN_RES) throw IOException("Open $p: unexpected reply ${r.op}")
        return ByteBuffer.wrap(r.header).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun close(h: Long) { runCatching { check(call(OP_CLOSE, le(h)), "Close") } }

    fun read(p: String): ByteArray {
        val h = open(p, MODE_RDONLY)
        try {
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val r = check(call(OP_READ, le(h, CHUNK.toLong())), "Read $p")
                if (r.payload.isEmpty()) break
                out.write(r.payload)
                if (r.payload.size < CHUNK) break
            }
            return out.toByteArray()
        } finally { close(h) }
    }

    fun download(p: String, to: File) {
        val h = open(p, MODE_RDONLY)
        try {
            to.outputStream().use { out ->
                while (true) {
                    val r = check(call(OP_READ, le(h, CHUNK.toLong())), "Read $p")
                    if (r.payload.isEmpty()) break
                    out.write(r.payload)
                    if (r.payload.size < CHUNK) break
                }
            }
        } finally { close(h) }
    }

    fun write(data: ByteArray, p: String) {
        val h = open(p, MODE_WRONLY)
        try {
            var off = 0
            while (off < data.size) {
                val n = minOf(CHUNK, data.size - off)
                check(call(OP_WRITE, le(h), data.copyOfRange(off, off + n)), "Write $p")
                off += n
            }
        } finally { close(h) }
    }

    /** Streams a local file to [p], reporting bytes sent. */
    fun upload(src: File, p: String, progress: (Long) -> Unit = {}) {
        val h = open(p, MODE_WRONLY)
        try {
            src.inputStream().use { ins ->
                val buf = ByteArray(CHUNK); var sent = 0L
                while (true) {
                    val n = ins.read(buf); if (n <= 0) break
                    check(call(OP_WRITE, le(h), buf, n), "Write $p")
                    sent += n; progress(sent)
                }
            }
        } finally { close(h) }
    }

    fun close() = s.close()
}
