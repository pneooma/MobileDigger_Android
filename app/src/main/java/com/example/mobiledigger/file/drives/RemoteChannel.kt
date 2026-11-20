package com.example.mobiledigger.file.drives

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

/**
 * Read-only random access over a file on a network drive, built on [DriveClient.open] with an
 * offset. Lets ZIP/7z readers look at the table of contents (end of the file) or a single entry
 * without downloading the whole archive. Small forward jumps are skipped in-stream; anything
 * else reopens the stream at the new offset.
 */
class RemoteChannel(
    private val client: DriveClient,
    private val path: String,
    private val size: Long
) : SeekableByteChannel {
    private var pos = 0L
    private var stream: RemoteStream? = null
    private var streamPos = -1L
    private var open = true
    private val chunk = ByteArray(128 * 1024)

    /** Bytes actually pulled over the network (for tests / diagnostics). */
    var bytesFetched = 0L
        private set

    override fun read(dst: ByteBuffer): Int {
        if (!open) throw IOException("closed")
        if (pos >= size) return -1
        positionStream()
        val want = minOf(dst.remaining().toLong(), size - pos, chunk.size.toLong()).toInt()
        if (want == 0) return 0
        val n = stream!!.input.read(chunk, 0, want)
        if (n <= 0) return -1
        dst.put(chunk, 0, n)
        pos += n; streamPos += n; bytesFetched += n
        return n
    }

    private fun positionStream() {
        val s = stream
        if (s != null && streamPos == pos) return
        if (s != null && pos > streamPos && pos - streamPos <= 256 * 1024) {
            // short hop forward: read and drop instead of reconnecting
            var left = pos - streamPos
            while (left > 0) {
                val n = s.input.read(chunk, 0, minOf(left, chunk.size.toLong()).toInt())
                if (n <= 0) break
                left -= n; streamPos += n; bytesFetched += n
            }
            if (streamPos == pos) return
        }
        runCatching { s?.close() }
        stream = client.open(path, pos)
        streamPos = pos
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun position(): Long = pos
    override fun position(newPosition: Long): SeekableByteChannel { pos = newPosition.coerceAtLeast(0); return this }
    override fun size(): Long = size
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false; runCatching { stream?.close() }; stream = null }
}
