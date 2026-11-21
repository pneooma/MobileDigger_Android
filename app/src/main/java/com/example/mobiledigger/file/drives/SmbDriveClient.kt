package com.example.mobiledigger.file.drives

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.hierynomus.security.bc.BCSecurityProvider
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB 2/3 via smbj. One connection + session per client, reopened if the server drops it.
 * Uses BouncyCastle's lightweight API (BCSecurityProvider): Android's built-in crypto has no MD4,
 * which NTLM needs.
 */
class SmbDriveClient(private val drive: NetworkDrive) : DriveClient {
    private val host = drive.address.trim().removePrefix("smb://").substringBefore('/').substringBefore(':')
    private val port = drive.address.trim().removePrefix("smb://").substringBefore('/')
        .substringAfter(':', "").toIntOrNull() ?: SMBClient.DEFAULT_PORT
    private val shareName = drive.share.trim().trim('/', '\\')

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    @Synchronized
    private fun share(): DiskShare {
        share?.let { if (it.isConnected) return it }
        closeQuietly()
        if (host.isBlank()) throw DriveException("Enter the drive's address (name or IP)")
        if (shareName.isBlank()) throw DriveException("Enter the share name, e.g. Music")
        try {
            val config = SmbConfig.builder()
                .withSecurityProvider(BCSecurityProvider())
                .withTimeout(20, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build()
            val c = SMBClient(config).also { client = it }
            val conn = c.connect(host, port).also { connection = it }
            val auth = when {
                drive.username.isBlank() -> AuthenticationContext.guest()
                else -> AuthenticationContext(drive.username.trim(), drive.password.toCharArray(), drive.domain.trim().ifBlank { null })
            }
            val s = conn.authenticate(auth).also { session = it }
            val sh = s.connectShare(shareName) as? DiskShare
                ?: throw DriveException("\"$shareName\" isn't a folder share")
            share = sh
            return sh
        } catch (e: DriveException) {
            closeQuietly(); throw e
        } catch (e: Exception) {
            closeQuietly()
            val msg = (e.message ?: "").lowercase()
            throw DriveException(
                when {
                    e is java.net.UnknownHostException -> "Can't find \"$host\". Try its IP address."
                    e is java.net.ConnectException || "connect" in msg && "refused" in msg ->
                        "Nothing answered at $host. Is file sharing (SMB) turned on?"
                    "logon_failure" in msg || "status_logon" in msg -> "Wrong username or password"
                    "bad_network_name" in msg -> "No share called \"$shareName\" on $host"
                    "access_denied" in msg -> "Access denied. Check the username and share permissions."
                    "timeout" in msg || e is java.net.SocketTimeoutException -> "$host took too long to answer"
                    else -> e.message ?: e.javaClass.simpleName
                }, e
            )
        }
    }

    private fun smbPath(path: String) = path.trim('/').replace('/', '\\')

    private fun <T> retrying(block: (DiskShare) -> T): T = try {
        block(share())
    } catch (e: DriveException) {
        throw e
    } catch (e: Exception) {
        // Stale connection (NAS went to sleep, Wi-Fi changed): reconnect once.
        synchronized(this) { closeQuietly() }
        try { block(share()) } catch (e2: DriveException) { throw e2 } catch (e2: Exception) {
            throw DriveException(e2.message ?: "SMB error", e2)
        }
    }

    override fun list(path: String): List<RemoteEntry> = retrying { sh ->
        sh.list(smbPath(path)).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == ".." || name.startsWith(".")) return@mapNotNull null
            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            RemoteEntry(name, joinPath(path, name), isDir, info.endOfFile)
        }
    }

    override fun open(path: String, offset: Long): RemoteStream = retrying { sh ->
        val f: File = sh.openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        val size = f.fileInformation.standardInformation.endOfFile
        // Never ask for more than the server negotiated, or it rejects the read.
        val maxRead = runCatching { sh.treeConnect.session.connection.negotiatedProtocol.maxReadSize }.getOrDefault(65536)
        RemoteStream(SmbFileInputStream(f, offset, size, minOf(256 * 1024, maxRead).coerceAtLeast(4096)), size) { runCatching { f.close() } }
    }

    override fun delete(path: String) = retrying { sh -> sh.rm(smbPath(path)) }

    override fun deleteFolder(path: String) = retrying { sh -> sh.rmdir(smbPath(path), true) }

    override fun mkdirs(path: String) = retrying { sh ->
        var cur = ""
        for (seg in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            cur = joinPath(cur, seg)
            // Servers differ in how they say "not there" (NAME_NOT_FOUND, PATH_NOT_FOUND, NO_SUCH_FILE),
            // so treat any failure as "missing" and ignore "already exists" when creating.
            val exists = runCatching { sh.folderExists(smbPath(cur)) }.getOrDefault(false)
            if (!exists) {
                try { sh.mkdir(smbPath(cur)) } catch (e: com.hierynomus.mssmb2.SMBApiException) {
                    if (e.status != com.hierynomus.mserref.NtStatus.STATUS_OBJECT_NAME_COLLISION) throw e
                }
            }
        }
    }

    override fun write(path: String, input: InputStream, length: Long) = retrying { sh ->
        val f = sh.openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        f.use { file -> file.outputStream.use { out -> input.copyTo(out, 128 * 1024) } }
        Unit
    }

    @Synchronized
    override fun close() = closeQuietly()

    private fun closeQuietly() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null
    }
}

/**
 * Sequential reader over an SMB file from [start] to [size], in [chunk]-byte reads.
 * Never reads past [size]: some servers answer an end-of-file read in a way smbj can't parse.
 */
private class SmbFileInputStream(private val file: File, start: Long, private val size: Long, chunk: Int) : InputStream() {
    private var pos = start
    private val buf = ByteArray(chunk)
    private var bufLen = 0
    private var bufPos = 0
    private var eof = false

    private fun fill(): Boolean {
        if (eof || (size >= 0 && pos >= size)) { eof = true; return false }
        val want = if (size >= 0) minOf(buf.size.toLong(), size - pos).toInt() else buf.size
        val n = file.read(buf, pos, 0, want)
        if (n <= 0) { eof = true; return false }
        pos += n; bufLen = n; bufPos = 0
        return true
    }

    override fun read(): Int {
        if (bufPos >= bufLen && !fill()) return -1
        return buf[bufPos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (bufPos >= bufLen && !fill()) return -1
        val n = minOf(len, bufLen - bufPos)
        System.arraycopy(buf, bufPos, b, off, n)
        bufPos += n
        return n
    }
}
