package com.example.mobiledigger.file.drives

import android.content.Context
import android.net.Uri
import com.example.mobiledigger.util.CrashLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Tiny HTTP server on 127.0.0.1 that streams files from any saved network drive:
 *
 *   http://127.0.0.1:<port>/d/<driveId>/<path/to/file.flac>
 *
 * Everything downstream (VLC playback, spectrogram decoding, WhatsApp share copy) already
 * understands plain http with Range requests, so SMB, WebDAV and the MobileDigger server all
 * work through one door and credentials never appear in file URIs or logs.
 * Only loopback connections are accepted.
 */
object DriveProxy {
    private const val PREFERRED_PORT = 38411
    @Volatile private var server: ServerSocket? = null
    @Volatile var port: Int = 0
        private set
    private lateinit var store: DriveStore
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "DriveProxy").apply { isDaemon = true } }
    private val clients = ConcurrentHashMap<String, Pair<NetworkDrive, DriveClient>>()

    @Synchronized
    fun ensureStarted(context: Context) {
        if (!::store.isInitialized) store = DriveStore(context)
        if (server?.isClosed == false) return
        val loopback = InetAddress.getByName("127.0.0.1")
        val s = runCatching { ServerSocket(PREFERRED_PORT, 16, loopback) }
            .getOrElse { ServerSocket(0, 16, loopback) }
        server = s
        port = s.localPort
        pool.execute {
            while (!s.isClosed) {
                val sock = try { s.accept() } catch (_: Exception) { break }
                pool.execute { handle(sock) }
            }
        }
        CrashLogger.log("DriveProxy", "Listening on 127.0.0.1:$port")
    }

    fun uriFor(driveId: String, path: String): Uri {
        val enc = path.split('/').filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return Uri.parse("http://127.0.0.1:$port/d/$driveId/$enc")
    }

    fun isDriveUri(uri: Uri?): Boolean = uri != null && uri.host == "127.0.0.1" && uri.path?.startsWith("/d/") == true

    /** (driveId, path inside the drive) for a URI made by [uriFor], or null. */
    fun parse(uri: Uri?): Pair<String, String>? {
        if (!isDriveUri(uri)) return null
        val segs = uri!!.pathSegments // already decoded
        if (segs.size < 3 || segs[0] != "d") return null
        return segs[1] to segs.drop(2).joinToString("/")
    }

    /** Drop cached connections (after a drive is edited or deleted). */
    fun invalidate(driveId: String? = null) {
        val keys = if (driveId == null) clients.keys.toList() else listOf(driveId)
        keys.forEach { k -> clients.remove(k)?.second?.let { c -> runCatching { c.close() } } }
    }

    private fun clientFor(id: String): DriveClient? {
        val drive = store.get(id) ?: return null
        clients[id]?.let { (d, c) -> if (d == drive) return c else runCatching { c.close() } }
        val c = DriveClients.create(drive)
        clients[id] = drive to c
        return c
    }

    private fun handle(sock: Socket) {
        sock.use { s ->
            try {
                if (!s.inetAddress.isLoopbackAddress) return
                s.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0 && line.substring(0, idx).equals("Range", true)) range = line.substring(idx + 1).trim()
                }
                val out = s.getOutputStream()
                if (method != "GET" && method != "HEAD") return status(out, 405, "Method Not Allowed")

                val rawPath = parts[1].substringBefore('?')
                if (!rawPath.startsWith("/d/")) return status(out, 404, "Not Found")
                val rest = rawPath.removePrefix("/d/")
                val id = rest.substringBefore('/')
                val filePath = rest.substringAfter('/', "").split('/')
                    .joinToString("/") { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
                val client = clientFor(id) ?: return status(out, 404, "Unknown drive")

                val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                val requestedEnd = range?.removePrefix("bytes=")?.substringAfter('-', "")?.toLongOrNull()

                val stream = try { client.open(filePath, start) } catch (e: Exception) {
                    CrashLogger.log("DriveProxy", "open failed: $filePath", e)
                    clients.remove(id)
                    return status(out, 502, "Bad Gateway")
                }
                stream.use { rs ->
                    val total = rs.totalSize
                    val end = when {
                        total <= 0 -> -1L
                        requestedEnd != null -> minOf(requestedEnd, total - 1)
                        else -> total - 1
                    }
                    val length = if (end >= 0) end - start + 1 else -1L
                    val partial = range != null && total > 0
                    val sb = StringBuilder()
                    sb.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                    sb.append("Content-Type: ").append(mimeFor(filePath)).append("\r\n")
                    sb.append("Accept-Ranges: bytes\r\n")
                    if (length >= 0) sb.append("Content-Length: ").append(length).append("\r\n")
                    if (partial) sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(total).append("\r\n")
                    sb.append("Connection: close\r\n\r\n")
                    out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
                    if (method == "HEAD") { out.flush(); return }
                    val buf = ByteArray(128 * 1024)
                    var left = if (length >= 0) length else Long.MAX_VALUE
                    while (left > 0) {
                        val n = rs.input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                    out.flush()
                }
            } catch (_: java.net.SocketException) {
                // Player closed the connection (seek / skip) — normal.
            } catch (e: Exception) {
                CrashLogger.log("DriveProxy", "request failed", e)
            }
        }
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        runCatching {
            out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun mimeFor(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "aif", "aiff" -> "audio/aiff"
        "m4a", "aac" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
