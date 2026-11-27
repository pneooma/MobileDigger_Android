package com.example.mobiledigger.ipod

import android.util.Base64
import com.example.mobiledigger.util.CrashLogger
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Date
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/** A byte stream to a device service: plain mux connection or TLS on top of it. */
interface DeviceStream {
    fun write(b: ByteArray)
    fun readFully(n: Int, timeoutMs: Long = 30_000): ByteArray
    fun close()
}

class PlainStream(val c: UsbMux.MuxConnection) : DeviceStream {
    override fun write(b: ByteArray) = c.write(b)
    override fun readFully(n: Int, timeoutMs: Long) = c.readFully(n, timeoutMs)
    override fun close() = c.close()
}

class LockdownException(val code: String, message: String) : IOException(message)

/**
 * The host's half of a pairing ("Trust this computer"): certificates and keys, saved per device.
 * Same structure as libimobiledevice / macOS pair records.
 */
class PairRecord(
    val hostId: String,
    val systemBuid: String,
    val hostKey: PrivateKey,
    val hostCert: X509Certificate,
    val rootKey: PrivateKey,
    val rootCert: X509Certificate,
    val deviceCert: X509Certificate,
    var escrowBag: ByteArray? = null
) {
    fun toPlistRequestRecord(): Map<String, Any?> = linkedMapOf(
        "DeviceCertificate" to pem("CERTIFICATE", deviceCert.encoded),
        "HostCertificate" to pem("CERTIFICATE", hostCert.encoded),
        "RootCertificate" to pem("CERTIFICATE", rootCert.encoded),
        "HostID" to hostId,
        "SystemBUID" to systemBuid
    )

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val m = linkedMapOf<String, Any?>(
            "HostID" to hostId, "SystemBUID" to systemBuid,
            "HostPrivateKey" to hostKey.encoded, "HostCertificate" to hostCert.encoded,
            "RootPrivateKey" to rootKey.encoded, "RootCertificate" to rootCert.encoded,
            "DeviceCertificate" to deviceCert.encoded, "EscrowBag" to escrowBag
        )
        file.writeBytes(Plist.toXml(m))
    }

    companion object {
        private fun pem(type: String, der: ByteArray): ByteArray {
            val b64 = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
            return "-----BEGIN $type-----\n$b64\n-----END $type-----\n".toByteArray(Charsets.US_ASCII)
        }

        private fun cert(der: ByteArray) =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate

        private fun key(der: ByteArray) = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))

        fun load(file: File): PairRecord? = runCatching {
            if (!file.exists()) return null
            val m = Plist.dict(Plist.parse(file.readBytes()))
            PairRecord(
                m["HostID"] as String, m["SystemBUID"] as String,
                key(m["HostPrivateKey"] as ByteArray), cert(m["HostCertificate"] as ByteArray),
                key(m["RootPrivateKey"] as ByteArray), cert(m["RootCertificate"] as ByteArray),
                cert(m["DeviceCertificate"] as ByteArray), m["EscrowBag"] as? ByteArray
            )
        }.onFailure { CrashLogger.log("PairRecord", "Couldn't read ${file.name}", it) }.getOrNull()

        /** New root + host keys/certs and a device certificate for [devicePublicKeyPem]. */
        fun generate(devicePublicKeyPem: ByteArray, systemBuid: String): PairRecord {
            val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val root = gen.generateKeyPair()
            val host = gen.generateKeyPair()
            val devPub = parseRsaPublicKey(devicePublicKeyPem)
            val empty = X500Name(arrayOf<RDN>())
            val now = System.currentTimeMillis()
            val from = Date(now - 24L * 3600_000)
            val to = Date(now + 10L * 365 * 24 * 3600_000)
            val ext = JcaX509ExtensionUtils()
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(root.private)
            val conv = JcaX509CertificateConverter()

            val rootCert = conv.getCertificate(
                JcaX509v3CertificateBuilder(empty, BigInteger.ONE, from, to, empty, root.public)
                    .addExtension(Extension.basicConstraints, true, BasicConstraints(true))
                    .build(signer)
            )
            fun leaf(pub: PublicKey) = conv.getCertificate(
                JcaX509v3CertificateBuilder(empty, BigInteger.ONE, from, to, empty, pub)
                    .addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                    .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
                    .addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pub))
                    .build(JcaContentSignerBuilder("SHA256withRSA").build(root.private))
            )
            return PairRecord(
                UUID.randomUUID().toString().uppercase(), systemBuid,
                host.private, leaf(host.public), root.private, rootCert, leaf(devPub)
            )
        }

        /** "-----BEGIN RSA PUBLIC KEY-----" (PKCS#1) as the device reports it. */
        private fun parseRsaPublicKey(pemBytes: ByteArray): PublicKey {
            val text = String(pemBytes, Charsets.US_ASCII)
            val b64 = text.lines().filterNot { it.startsWith("-----") }.joinToString("")
            val der = Base64.decode(b64, Base64.DEFAULT)
            return if (text.contains("BEGIN RSA PUBLIC KEY")) {
                val k = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(der)
                KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(k.modulus, k.publicExponent))
            } else {
                KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(der))
            }
        }
    }
}

/** TLS on top of a mux connection, with the pair record's host certificate as the client certificate. */
class TlsStream(private val raw: UsbMux.MuxConnection, record: PairRecord) : DeviceStream {
    private val engine: SSLEngine
    private var netIn: ByteBuffer
    private var appIn: ByteBuffer
    private val netOut: ByteBuffer

    init {
        val km = object : X509ExtendedKeyManager() {
            override fun getClientAliases(t: String?, i: Array<out java.security.Principal>?) = arrayOf("host")
            override fun chooseClientAlias(t: Array<out String>?, i: Array<out java.security.Principal>?, s: Socket?) = "host"
            override fun chooseEngineClientAlias(t: Array<out String>?, i: Array<out java.security.Principal>?, e: SSLEngine?) = "host"
            override fun getServerAliases(t: String?, i: Array<out java.security.Principal>?) = null
            override fun chooseServerAlias(t: String?, i: Array<out java.security.Principal>?, s: Socket?) = null
            override fun getCertificateChain(alias: String?) = arrayOf(record.hostCert)
            override fun getPrivateKey(alias: String?) = record.hostKey
        }
        // The device's certificate is the one we issued at pairing; there's no public CA to check.
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(arrayOf(km), arrayOf(tm), null)
        engine = ctx.createSSLEngine("ipod", 0)
        engine.useClientMode = true
        runCatching {
            engine.enabledProtocols = engine.supportedProtocols.filter { it in setOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3") }.toTypedArray()
        }
        val s = engine.session
        netIn = ByteBuffer.allocate(s.packetBufferSize).also { it.flip() }
        appIn = ByteBuffer.allocate(s.applicationBufferSize + 64).also { it.flip() }
        netOut = ByteBuffer.allocate(s.packetBufferSize)
        handshake()
    }

    private fun readNet(): Boolean {
        netIn.compact()
        if (!netIn.hasRemaining()) {
            val bigger = ByteBuffer.allocate(netIn.capacity() * 2); netIn.flip(); bigger.put(netIn); netIn = bigger
        }
        val tmp = ByteArray(netIn.remaining())
        val n = raw.read(tmp, 0, tmp.size, raw.readTimeoutMs)
        if (n > 0) netIn.put(tmp, 0, n)
        netIn.flip()
        return n > 0
    }

    private fun flushNet() {
        netOut.flip()
        if (netOut.hasRemaining()) {
            val b = ByteArray(netOut.remaining()); netOut.get(b); raw.write(b)
        }
        netOut.clear()
    }

    private fun handshake() {
        engine.beginHandshake()
        var hs = engine.handshakeStatus
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            when (hs) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    val r = engine.wrap(ByteBuffer.allocate(0), netOut); flushNet(); hs = r.handshakeStatus
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    if (!netIn.hasRemaining() && hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP && !readNet())
                        throw IOException("TLS handshake: connection closed")
                    appIn.compact()
                    val r = engine.unwrap(netIn, appIn); appIn.flip()
                    hs = r.handshakeStatus
                    if (r.status == SSLEngineResult.Status.BUFFER_UNDERFLOW && !readNet()) throw IOException("TLS handshake: connection closed")
                    if (r.status == SSLEngineResult.Status.CLOSED) throw IOException("TLS handshake refused")
                }
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    while (true) { val t = engine.delegatedTask ?: break; t.run() }
                    hs = engine.handshakeStatus
                }
                else -> break
            }
        }
        CrashLogger.log("TlsStream", "TLS up: ${engine.session.protocol} ${engine.session.cipherSuite}")
    }

    override fun write(b: ByteArray) {
        val src = ByteBuffer.wrap(b)
        while (src.hasRemaining()) {
            val r = engine.wrap(src, netOut)
            if (r.status != SSLEngineResult.Status.OK) throw IOException("TLS write: ${r.status}")
            flushNet()
        }
    }

    private fun fill(): Boolean {
        while (true) {
            if (netIn.hasRemaining()) {
                appIn.compact()
                val r = engine.unwrap(netIn, appIn); appIn.flip()
                when (r.status) {
                    SSLEngineResult.Status.OK -> if (appIn.hasRemaining()) return true else continue
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        val bigger = ByteBuffer.allocate(appIn.capacity() * 2); bigger.put(appIn); bigger.flip(); appIn = bigger; continue
                    }
                    SSLEngineResult.Status.CLOSED -> return false
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> { if (!readNet()) return false }
                    else -> return false
                }
            } else if (!readNet()) return false
        }
    }

    override fun readFully(n: Int, timeoutMs: Long): ByteArray {
        val out = ByteArray(n); var got = 0
        val prev = raw.readTimeoutMs; raw.readTimeoutMs = timeoutMs
        try {
            while (got < n) {
                if (!appIn.hasRemaining() && !fill()) throw IOException("Connection to the iPod closed")
                val k = minOf(appIn.remaining(), n - got)
                appIn.get(out, got, k); got += k
            }
        } finally { raw.readTimeoutMs = prev }
        return out
    }

    override fun close() { runCatching { engine.closeOutbound() }; raw.close() }
}

/**
 * lockdownd (device port 62078): device values, pairing, the TLS session, starting services.
 * Messages are a 4-byte big-endian length and an XML plist.
 */
class Lockdown(private val mux: UsbMux, private val appDir: File) {
    companion object {
        const val PORT = 62078
        private const val LABEL = "MobileDigger"
        private const val TAG = "Lockdown"
    }

    private var stream: DeviceStream = PlainStream(mux.connect(PORT))
    var record: PairRecord? = null; private set
    var udid: String = ""; private set

    fun request(msg: Map<String, Any?>): Map<String, Any?> {
        val body = Plist.toXml(LinkedHashMap(msg).apply { put("Label", LABEL) })
        stream.write(ByteBuffer.allocate(4).putInt(body.size).array() + body)
        val len = ByteBuffer.wrap(stream.readFully(4)).int
        if (len <= 0 || len > 8 * 1024 * 1024) throw IOException("Bad lockdown reply")
        val reply = Plist.dict(Plist.parse(stream.readFully(len)))
        Plist.str(reply["Error"])?.let { throw LockdownException(it, "${msg["Request"]}: $it") }
        return reply
    }

    fun getValue(key: String? = null, domain: String? = null): Any? =
        request(linkedMapOf("Request" to "GetValue", "Domain" to domain, "Key" to key))["Value"]

    private fun recordFile(udid: String) = File(appDir, "ipod/$udid.pairrecord")

    /** A stable id for this phone as a sync host. */
    private fun systemBuid(): String {
        val f = File(appDir, "ipod/system-buid")
        if (f.exists()) return f.readText().trim()
        f.parentFile?.mkdirs()
        return UUID.randomUUID().toString().uppercase().also { f.writeText(it) }
    }

    /**
     * Makes sure this phone is paired ("Trust this computer"). [onPrompt] is called while the
     * device waits for the user to tap Trust. Throws a LockdownException on refusal / locked device.
     */
    fun ensurePaired(onPrompt: (String) -> Unit, timeoutMs: Long = 120_000) {
        val type = request(mapOf("Request" to "QueryType"))["Type"]
        if (type != "com.apple.mobile.lockdown") throw IOException("Not a lockdown service: $type")
        udid = Plist.str(getValue("UniqueDeviceID")) ?: mux.serial
        PairRecord.load(recordFile(udid))?.let { record = it; return }

        val pub = getValue("DevicePublicKey") as? ByteArray ?: throw IOException("The iPod didn't give its public key")
        val rec = PairRecord.generate(pub, systemBuid())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                val reply = request(linkedMapOf(
                    "Request" to "Pair", "PairRecord" to rec.toPlistRequestRecord(),
                    "ProtocolVersion" to "2", "PairingOptions" to mapOf("ExtendedPairingErrors" to true)
                ))
                rec.escrowBag = reply["EscrowBag"] as? ByteArray
                rec.save(recordFile(udid))
                record = rec
                CrashLogger.log(TAG, "Paired with $udid")
                // lockdownd closes the connection after a successful Pair: open a fresh one for the session.
                runCatching { stream.close() }
                stream = PlainStream(mux.connect(PORT))
                return
            } catch (e: LockdownException) {
                when (e.code) {
                    "PairingDialogResponsePending" -> onPrompt("On the iPod, tap “Trust” to let this phone sync with it.")
                    "PasswordProtected" -> onPrompt("Unlock the iPod (enter its passcode), then tap “Trust”.")
                    "UserDeniedPairing" -> throw LockdownException(e.code, "“Don't Trust” was tapped on the iPod. Unplug it, plug it back in and tap “Trust”.")
                    else -> throw e
                }
                if (System.currentTimeMillis() > deadline) throw LockdownException("Timeout", "Nobody tapped “Trust” on the iPod.")
                Thread.sleep(1500)
            }
        }
    }

    /** Starts the encrypted session (after pairing). A stale pairing is dropped so the next try pairs anew. */
    fun startSession() {
        val rec = record ?: throw IllegalStateException("not paired")
        val reply = try {
            request(linkedMapOf("Request" to "StartSession", "HostID" to rec.hostId, "SystemBUID" to rec.systemBuid))
        } catch (e: LockdownException) {
            if (e.code == "InvalidHostID") {
                recordFile(udid).delete(); record = null
                throw LockdownException(e.code, "The iPod forgot this phone. Try again and tap “Trust” on the iPod.")
            }
            throw e
        }
        if (Plist.bool(reply["EnableSessionSSL"]) == true) {
            val c = (stream as PlainStream).c
            stream = TlsStream(c, rec)
        }
    }

    /** Starts a service; returns a stream to it (TLS when the device asks for it). */
    fun startService(name: String): DeviceStream {
        val rec = record ?: throw IllegalStateException("not paired")
        val req = linkedMapOf<String, Any?>("Request" to "StartService", "Service" to name)
        val reply = request(req)
        val port = Plist.long(reply["Port"])?.toInt() ?: throw IOException("No port for $name")
        val c = mux.connect(port)
        return if (Plist.bool(reply["EnableServiceSSL"]) == true) TlsStream(c, rec) else PlainStream(c)
    }

    fun close() = stream.close()
}

/** InputStream/OutputStream views (for code that wants them). */
fun DeviceStream.asOutput(): OutputStream = object : OutputStream() {
    override fun write(b: Int) = this@asOutput.write(byteArrayOf(b.toByte()))
    override fun write(b: ByteArray, off: Int, len: Int) = this@asOutput.write(b.copyOfRange(off, off + len))
}
