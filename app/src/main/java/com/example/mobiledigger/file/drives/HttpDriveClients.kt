package com.example.mobiledigger.file.drives

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun encodeSegments(path: String): String =
        path.split('/').filter { it.isNotEmpty() }.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    /** Turns an HTTP failure into something a person can act on. */
    fun fail(resp: Response, what: String): Nothing {
        val code = resp.code
        resp.close()
        throw DriveException(
            when (code) {
                401, 403 -> "Wrong username or password ($code)"
                404 -> "$what not found (404). Check the address."
                405 -> "This address doesn't speak WebDAV (405)"
                else -> "$what failed: HTTP $code"
            }
        )
    }

    fun friendly(e: Exception, address: String): DriveException = when (e) {
        is DriveException -> e
        is java.net.UnknownHostException -> DriveException("Can't find \"$address\". Check the address.", e)
        is java.net.ConnectException -> DriveException("Nothing answered at $address. Is the drive on and on this Wi-Fi?", e)
        is java.net.SocketTimeoutException -> DriveException("$address took too long to answer", e)
        is javax.net.ssl.SSLException -> DriveException("Secure connection failed. Try http:// instead of https://", e)
        else -> DriveException(e.message ?: e.javaClass.simpleName, e)
    }

    fun openRanged(request: Request.Builder, offset: Long, what: String): RemoteStream {
        if (offset > 0) request.header("Range", "bytes=$offset-")
        val resp = client.newCall(request.build()).execute()
        if (!resp.isSuccessful) fail(resp, what)
        val body = resp.body ?: run { resp.close(); throw DriveException("$what: empty response") }
        val total = if (resp.code == 206) {
            resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
        } else body.contentLength()
        var stream = body.byteStream()
        if (offset > 0 && resp.code == 200) {
            // Server ignored Range: skip forward ourselves.
            var left = offset
            while (left > 0) {
                val s = stream.skip(left)
                if (s <= 0) break
                left -= s
            }
        }
        return RemoteStream(stream, total) { resp.close() }
    }
}

/** The MobileDigger / Pi library server: GET /library/list?path=… returning {dirs, files}. */
class MobileDiggerServerClient(private val drive: NetworkDrive) : DriveClient {
    private val base: String = drive.address.trim().removeSuffix("/").let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private fun req(url: String) = Request.Builder().url(url).apply {
        if (drive.password.isNotBlank()) header("X-Dashboard-Password", drive.password)
    }

    /**
     * With write access set up, list through the SMB share: the library server only reports
     * audio files, so archives (.zip/.rar/.7z) were invisible and archive-only folders looked
     * empty. Same library root, same relative paths. Falls back to the server if SMB fails.
     */
    override fun list(path: String): List<RemoteEntry> {
        writer?.let { smb ->
            try { return smb.list(rel(path)) } catch (e: Exception) {
                com.example.mobiledigger.util.CrashLogger.log("MobileDiggerServerClient", "SMB listing failed, using the server", e)
            }
        }
        return serverList(path)
    }

    private fun serverList(path: String): List<RemoteEntry> = try {
        val q = URLEncoder.encode(path.trim('/'), "UTF-8").replace("+", "%20")
        Http.client.newCall(req("$base/library/list?path=$q").build()).execute().use { resp ->
            if (!resp.isSuccessful) Http.fail(resp, "Folder")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val out = mutableListOf<RemoteEntry>()
            json.optJSONArray("dirs")?.let { a ->
                for (i in 0 until a.length()) {
                    val d = a.optJSONObject(i) ?: continue
                    val p = d.optString("path").takeIf { it.isNotEmpty() } ?: continue
                    out.add(RemoteEntry(d.optString("name").ifEmpty { p.substringAfterLast('/') }, p.trim('/'), true, 0))
                }
            }
            json.optJSONArray("files")?.let { a ->
                for (i in 0 until a.length()) {
                    val f = a.optJSONObject(i) ?: continue
                    val name = f.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    // Prefer the library-relative "path" (same layout as the SMB share); older
                    // servers only send their own raw URL.
                    val rel = f.optString("path").takeIf { it.isNotEmpty() }?.trim('/')
                    val url = f.optString("url").takeIf { it.isNotEmpty() }
                    val p = rel ?: url ?: continue
                    out.add(RemoteEntry(name, p, false, f.optLong("size", 0L)))
                }
            }
            out
        }
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }

    override fun open(path: String, offset: Long): RemoteStream {
        // The server only serves audio; archives and other files come from the SMB share.
        val w = writer
        if (w != null && !DriveClients.isAudio(path)) return w.open(rel(path), offset)
        return serverOpen(path, offset)
    }

    private fun serverOpen(path: String, offset: Long): RemoteStream = try {
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "$base/${Http.encodeSegments(path)}"          // legacy raw server URL
            else -> "$base/library/files/${Http.encodeSegments(path)}"           // library-relative path
        }
        Http.openRanged(req(url), offset, "File")
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }

    /** Writes go to the SMB share on the same machine, if one is configured. */
    private val writer: SmbDriveClient? by lazy {
        if (drive.writeShare.isBlank()) null else SmbDriveClient(
            NetworkDrive(
                id = drive.id, name = drive.name, type = DriveType.SMB,
                address = java.net.URI(base).host ?: drive.address.substringBefore(':'),
                share = drive.writeShare, username = drive.writeUser, password = drive.writePassword
            )
        )
    }

    override val canWrite: Boolean get() = drive.writeShare.isNotBlank()
    private fun w(): SmbDriveClient = writer ?: throw DriveException(
        "${drive.name.ifBlank { "This server" }} is read-only. Turn on \"Write access\" for it under Network drives to delete or unpack files."
    )
    private fun rel(path: String) = if (path.startsWith("/library/files/")) path.removePrefix("/library/files/") else path.trim('/')
    override fun delete(path: String) = w().delete(rel(path))
    override fun mkdirs(path: String) = w().mkdirs(rel(path))
    override fun write(path: String, input: java.io.InputStream, length: Long) = w().write(rel(path), input, length)
    override fun close() { runCatching { if (drive.writeShare.isNotBlank()) writer?.close() } }
}

/** Plain WebDAV (RFC 4918): PROPFIND Depth 1 for listing, ranged GET for reading, Basic auth. */
class WebDavClient(private val drive: NetworkDrive) : DriveClient {
    private val base: String = drive.address.trim().removeSuffix("/").let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }
    private val basePath: String = runCatching { java.net.URI(base).rawPath ?: "" }.getOrDefault("")
        .let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.trimEnd('/')

    private fun req(path: String) = Request.Builder()
        .url(if (path.isBlank()) "$base/" else "$base/${Http.encodeSegments(path)}")
        .apply { if (drive.username.isNotBlank()) header("Authorization", Credentials.basic(drive.username, drive.password)) }

    override fun list(path: String): List<RemoteEntry> = try {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
        val folderUrl = req(path).build().url.toString().let { if (it.endsWith("/")) it else "$it/" }
        val request = Request.Builder().url(folderUrl)
            .apply { if (drive.username.isNotBlank()) header("Authorization", Credentials.basic(drive.username, drive.password)) }
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()
        Http.client.newCall(request).execute().use { resp ->
            if (resp.code != 207 && !resp.isSuccessful) Http.fail(resp, "Folder")
            parse(resp.body?.string() ?: "", path.trim('/'))
        }
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }

    private fun parse(xml: String, folder: String): List<RemoteEntry> {
        val out = mutableListOf<RemoteEntry>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(xml.reader())
        var href: String? = null
        var isDir = false
        var size = 0L
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "response") { href = null; isDir = false; size = 0L }
                    if (tag == "collection") isDir = true
                }
                XmlPullParser.TEXT -> {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) when (tag) {
                        "href" -> href = t
                        "getcontentlength" -> size = t.toLongOrNull() ?: 0L
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "response" && href != null) {
                        val full = URLDecoder.decode(
                            (runCatching { java.net.URI(href).rawPath }.getOrNull() ?: href!!).replace("+", "%2B"), "UTF-8"
                        ).trimEnd('/')
                        val rel = full.removePrefix(basePath).trim('/')
                        if (rel != folder && rel.isNotEmpty()) {
                            out.add(RemoteEntry(rel.substringAfterLast('/'), rel, isDir, size))
                        }
                    }
                    tag = ""
                }
            }
        }
        return out
    }

    override fun open(path: String, offset: Long): RemoteStream = try {
        Http.openRanged(req(path), offset, "File")
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }

    override fun delete(path: String) = try {
        Http.client.newCall(req(path).delete().build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) Http.fail(resp, "Delete")
        }
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }

    override fun mkdirs(path: String) {
        var cur = ""
        for (seg in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            cur = joinPath(cur, seg)
            try {
                val url = req(cur).build().url.toString().let { if (it.endsWith("/")) it else "$it/" }
                val r = Request.Builder().url(url)
                    .apply { if (drive.username.isNotBlank()) header("Authorization", Credentials.basic(drive.username, drive.password)) }
                    .method("MKCOL", null).build()
                Http.client.newCall(r).execute().use { resp ->
                    // 405 = already exists
                    if (!resp.isSuccessful && resp.code != 405) Http.fail(resp, "Create folder")
                }
            } catch (e: Exception) { throw Http.friendly(e, drive.address) }
        }
    }

    override fun write(path: String, input: java.io.InputStream, length: Long) = try {
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: okio.BufferedSink) { input.copyTo(sink.outputStream(), 128 * 1024) }
        }
        Http.client.newCall(req(path).put(body).build()).execute().use { resp ->
            if (!resp.isSuccessful) Http.fail(resp, "Upload")
        }
    } catch (e: Exception) { throw Http.friendly(e, drive.address) }
}
