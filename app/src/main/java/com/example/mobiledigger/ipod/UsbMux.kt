package com.example.mobiledigger.ipod

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.example.mobiledigger.util.CrashLogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The USB multiplexer ("usbmux") an iOS device speaks on its vendor interface (class 255 / sub 254 /
 * proto 2): a stream of mux packets, each carrying a minimal TCP header, so several services
 * (lockdown, AFC, ATC…) run as separate "connections" over one USB link. This is what macOS's
 * usbmuxd does for Finder; reimplemented here after libimobiledevice's usbmuxd (device.c).
 */
class UsbMux private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
    val serial: String
) {
    companion object {
        const val APPLE_VENDOR = 0x05AC
        private const val PROTO_VERSION = 0
        private const val PROTO_CONTROL = 1
        private const val PROTO_SETUP = 2
        private const val PROTO_TCP = 6
        private const val MAGIC = 0xFEEDFACE.toInt()
        private const val TH_FIN = 0x01; private const val TH_SYN = 0x02; private const val TH_RST = 0x04
        private const val TH_ACK = 0x10
        private const val RX_WIN = 128 * 1024
        private const val MAX_PAYLOAD = 32 * 1024
        private const val TAG = "UsbMux"

        fun isApple(d: UsbDevice) = d.vendorId == APPLE_VENDOR

        /** (configuration index, interface) of the usbmux interface, or null. */
        fun findMux(d: UsbDevice): Pair<Int, UsbInterface>? {
            for (c in 0 until d.configurationCount) {
                val cfg = d.getConfiguration(c)
                for (i in 0 until cfg.interfaceCount) {
                    val itf = cfg.getInterface(i)
                    if (itf.interfaceClass == 255 && itf.interfaceSubclass == 254 && itf.interfaceProtocol == 2) return c to itf
                }
            }
            return null
        }

        /** Opens the device (permission must already be granted) and does the mux handshake. */
        fun open(usb: UsbManager, d: UsbDevice): UsbMux {
            val (cfgIndex, itf) = findMux(d) ?: throw IOException("This Apple device has no sync interface")
            val c = usb.openDevice(d) ?: throw IOException("Couldn't open the iPod over USB")
            try {
                // The kernel picks configuration 1 (camera/PTP only); the mux lives in a later one.
                val cfg = d.getConfiguration(cfgIndex)
                // Switch away and back so the iPod starts a fresh multiplexer (a previous, interrupted
                // link would otherwise leave its packet numbering and connections behind).
                if (cfgIndex != 0) runCatching { c.setConfiguration(d.getConfiguration(0)); Thread.sleep(300) }
                if (!c.setConfiguration(cfg)) CrashLogger.log(TAG, "setConfiguration(${cfg.id}) refused; trying anyway")
                if (!c.claimInterface(itf, true)) throw IOException("Couldn't claim the iPod's sync interface")
                var inEp: UsbEndpoint? = null; var outEp: UsbEndpoint? = null
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp == null || outEp == null) throw IOException("The iPod's sync interface has no bulk endpoints")
                val serial = runCatching { c.serial }.getOrNull() ?: d.serialNumber ?: ""
                return UsbMux(c, itf, inEp, outEp, serial).also { it.handshake() }
            } catch (e: Exception) {
                runCatching { c.close() }
                throw e
            }
        }
    }

    @Volatile private var version = 0
    private var txSeq = 0
    private var rxSeq = 0xFFFF
    private val sendLock = Any()
    @Volatile private var closed = false
    private val conns = ConcurrentHashMap<Int, MuxConnection>() // by our source port
    // Random start: ports of a previous link may still be open on the iPod's side.
    private var nextPort = 0x4000 + java.util.Random().nextInt(0x8000)
    private var reader: Thread? = null
    private val versionReply = LinkedBlockingQueue<Int>()

    // ------------------------------------------------------------------ low level

    private fun usbWrite(data: ByteArray) {
        if (closed) throw IOException("The USB link to the iPod is closed")
        var off = 0
        while (off < data.size) {
            // The iPod can hold off a write for many seconds while it stores an incoming file. A packet
            // must never be dropped (the mux numbers every packet and stops at a gap), so wait long, and if
            // a write still fails, close the link instead of carrying on with a hole in the sequence.
            val n = conn.bulkTransfer(epOut, data, off, data.size - off, 60_000)
            if (n < 0) {
                CrashLogger.log(TAG, "USB write failed after ${off}/${data.size} bytes; closing the link")
                failLink()
                throw IOException("The USB link to the iPod failed. Unplug it, plug it back in and try again.")
            }
            off += n
        }
        // A transfer that's an exact multiple of the packet size needs a zero-length packet to end it.
        if (data.isNotEmpty() && data.size % epOut.maxPacketSize == 0) conn.bulkTransfer(epOut, ByteArray(0), 0, 10_000)
    }

    /** Marks the link dead and wakes every connection, so waiting reads / writes fail instead of hanging. */
    private fun failLink() {
        closed = true
        conns.values.forEach { it.abort() }
    }

    val isClosed get() = closed

    /**
     * Packets go out through one sender thread, in sequence order. Nothing that reads from the iPod ever
     * waits for a USB write: if the reader blocked behind a large upload, the iPod's replies would back up
     * and it would stop accepting data (a deadlock that showed up as write timeouts and lost packets).
     */
    private val outQ = LinkedBlockingQueue<ByteArray>()
    private val sender = Thread({
        try {
            while (!closed) {
                val pkt = outQ.poll(1, TimeUnit.SECONDS) ?: continue
                usbWrite(pkt)
            }
        } catch (e: Exception) {
            if (!closed) CrashLogger.log(TAG, "sender stopped", e)
            failLink()
        }
    }, "usbmux-sender").apply { isDaemon = true }

    private fun sendPacket(proto: Int, header: ByteArray, payload: ByteArray?, payloadLen: Int = payload?.size ?: 0) {
        if (closed) throw IOException("The USB link to the iPod is closed")
        synchronized(sendLock) {
            val hsize = if (version < 2) 8 else 16
            val total = hsize + header.size + payloadLen
            val buf = ByteBuffer.allocate(total)
            buf.putInt(proto); buf.putInt(total)
            if (version >= 2) {
                if (proto == PROTO_SETUP) { txSeq = 0; rxSeq = 0xFFFF }
                buf.putInt(MAGIC); buf.putShort(txSeq.toShort()); buf.putShort(rxSeq.toShort())
                txSeq = (txSeq + 1) and 0xFFFF
            }
            buf.put(header)
            if (payload != null && payloadLen > 0) buf.put(payload, 0, payloadLen)
            // Sequence numbers are assigned here, under the lock, and the queue keeps that order.
            outQ.put(buf.array())
        }
    }

    private fun handshake() {
        sender.start()
        reader = Thread({ readLoop() }, "usbmux-reader").apply { isDaemon = true; start() }
        sendPacket(PROTO_VERSION, ByteBuffer.allocate(12).putInt(2).putInt(0).putInt(0).array(), null)
        val major = versionReply.poll(5, TimeUnit.SECONDS) ?: throw IOException("The iPod didn't answer the USB handshake")
        version = major
        if (version >= 2) sendPacket(PROTO_SETUP, ByteArray(0), byteArrayOf(0x07))
        CrashLogger.log(TAG, "mux version $version")
    }

    private fun readLoop() {
        val chunk = ByteArray(64 * 1024)
        var pending = ByteArray(0)
        while (!closed) {
            val n = conn.bulkTransfer(epIn, chunk, chunk.size, 1000)
            if (n < 0) continue // timeout or transient; keep polling until closed
            if (n == 0) continue
            pending = if (pending.isEmpty()) chunk.copyOf(n) else pending + chunk.copyOf(n)
            while (true) {
                if (pending.size < 8) break
                val bb = ByteBuffer.wrap(pending)
                val proto = bb.getInt(0)
                val length = bb.getInt(4)
                if (length < 8 || length > 16 * 1024 * 1024) { pending = ByteArray(0); break }
                if (pending.size < length) break
                val pkt = pending.copyOfRange(0, length)
                pending = if (pending.size == length) ByteArray(0) else pending.copyOfRange(length, pending.size)
                runCatching { handlePacket(proto, pkt) }.onFailure { CrashLogger.log(TAG, "bad packet", it) }
            }
        }
    }

    private fun handlePacket(proto: Int, pkt: ByteArray) {
        val bb = ByteBuffer.wrap(pkt)
        val hsize = if (version < 2) 8 else 16
        if (version >= 2 && proto != PROTO_VERSION) {
            rxSeq = bb.getShort(14).toInt() and 0xFFFF
        }
        when (proto) {
            PROTO_VERSION -> versionReply.offer(ByteBuffer.wrap(pkt, 8, 12).int)
            PROTO_CONTROL -> {
                val type = pkt.getOrNull(hsize)?.toInt() ?: return
                val msg = if (pkt.size > hsize + 1) String(pkt, hsize + 1, pkt.size - hsize - 1, Charsets.UTF_8).trim('\u0000') else ""
                CrashLogger.log(TAG, "control $type: $msg")
            }
            PROTO_TCP -> {
                val th = ByteBuffer.wrap(pkt, hsize, 20)
                val sport = th.short.toInt() and 0xFFFF
                val dport = th.short.toInt() and 0xFFFF
                val seq = th.int
                val ack = th.int
                val offFlags = th.short.toInt()
                val flags = offFlags and 0xFF
                val win = th.short.toInt() and 0xFFFF
                val payloadStart = hsize + 20
                val payload = if (pkt.size > payloadStart) pkt.copyOfRange(payloadStart, pkt.size) else ByteArray(0)
                val c = conns[dport]
                if (c == null) {
                    // A connection we don't have (left over from an earlier link): reset it on the iPod's side.
                    if (flags and TH_RST == 0) runCatching {
                        val h = ByteBuffer.allocate(20)
                        h.putShort(dport.toShort()); h.putShort(sport.toShort()); h.putInt(ack); h.putInt(seq + payload.size)
                        h.put((5 shl 4).toByte()); h.put(TH_RST.toByte()); h.putShort(0); h.putShort(0); h.putShort(0)
                        sendPacket(PROTO_TCP, h.array(), null)
                    }
                    return
                }
                c.onTcp(flags, seq, ack, win, payload)
            }
        }
    }

    fun close() {
        closed = true
        conns.values.forEach { runCatching { it.close() } }
        runCatching { conn.releaseInterface(iface) }
        runCatching { conn.close() }
    }

    // ------------------------------------------------------------------ connections

    /** Opens a stream to a device port (lockdown is 62078). */
    fun connect(port: Int, timeoutMs: Long = 10_000): MuxConnection {
        if (closed) throw IOException("USB link closed")
        val sport = synchronized(this) { nextPort = if (nextPort >= 0xFFF0) 0x4000 else nextPort + 1; nextPort }
        val c = MuxConnection(sport, port)
        conns[sport] = c
        c.sendTcp(TH_SYN, null)
        if (!c.awaitConnected(timeoutMs)) {
            conns.remove(sport)
            throw IOException(if (c.refused) "The iPod refused the connection (port $port)" else "The iPod didn't answer (port $port)")
        }
        return c
    }

    inner class MuxConnection(private val sport: Int, private val dport: Int) {
        private var txSeqT = 0
        private var txAck = 0
        @Volatile private var txAcked = 0
        @Volatile private var txWin = 0
        private val lock = Object()
        @Volatile var connected = false; private set
        @Volatile var refused = false; private set
        @Volatile var finished = false; private set
        private val rx = java.util.ArrayDeque<ByteArray>()
        private var rxHead: ByteArray? = null
        private var rxPos = 0

        internal fun sendTcp(flags: Int, payload: ByteArray?, len: Int = payload?.size ?: 0) {
            val h = ByteBuffer.allocate(20)
            h.putShort(sport.toShort()); h.putShort(dport.toShort())
            h.putInt(txSeqT); h.putInt(txAck)
            h.put((5 shl 4).toByte()); h.put(flags.toByte())
            h.putShort((RX_WIN shr 8).toShort()); h.putShort(0); h.putShort(0)
            sendPacket(PROTO_TCP, h.array(), payload, len)
        }

        internal fun onTcp(flags: Int, seq: Int, ack: Int, win: Int, payload: ByteArray) {
            synchronized(lock) {
                txWin = win shl 8
                if (flags and TH_RST != 0) {
                    if (!connected) refused = true
                    finished = true; connected = false
                    conns.remove(sport); lock.notifyAll(); return
                }
                if (!connected && flags and (TH_SYN or TH_ACK) == (TH_SYN or TH_ACK)) {
                    txSeqT += 1; txAck = seq + 1; txAcked = ack
                    connected = true
                    sendTcp(TH_ACK, null)
                    lock.notifyAll(); return
                }
                txAcked = ack
                if (payload.isNotEmpty()) {
                    txAck += payload.size
                    rx.add(payload)
                    sendTcp(TH_ACK, null)
                }
                if (flags and TH_FIN != 0) finished = true
                lock.notifyAll()
            }
        }

        internal fun awaitConnected(timeoutMs: Long): Boolean {
            val end = System.currentTimeMillis() + timeoutMs
            synchronized(lock) {
                while (!connected && !refused) {
                    val left = end - System.currentTimeMillis()
                    if (left <= 0) return false
                    lock.wait(left)
                }
            }
            return connected
        }

        fun write(data: ByteArray, off: Int = 0, len: Int = data.size) {
            var o = off
            val end = off + len
            while (o < end) {
                val n = minOf(MAX_PAYLOAD, end - o)
                synchronized(lock) {
                    val deadline = System.currentTimeMillis() + 30_000
                    // Respect the device's receive window.
                    while (connected && txWin > 0 && (txSeqT - txAcked) + n > txWin) {
                        val left = deadline - System.currentTimeMillis()
                        if (left <= 0) throw IOException("iPod stopped accepting data")
                        lock.wait(left)
                    }
                    if (!connected) throw IOException("Connection to the iPod closed")
                    val chunk = if (o == 0 && n == data.size) data else data.copyOfRange(o, o + n)
                    sendTcp(TH_ACK, chunk)
                    txSeqT += n
                }
                o += n
            }
        }

        /** Reads up to len bytes; blocks until at least one is available. Returns -1 at end of stream. */
        fun read(buf: ByteArray, off: Int, len: Int, timeoutMs: Long): Int {
            synchronized(lock) {
                val deadline = if (timeoutMs > 0) System.currentTimeMillis() + timeoutMs else Long.MAX_VALUE
                while (rxHead == null && rx.isEmpty()) {
                    if (finished || !connected && !rx.isNotEmpty()) return -1
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw java.net.SocketTimeoutException("iPod read timeout")
                    lock.wait(minOf(left, 1000))
                }
                if (rxHead == null) { rxHead = rx.poll(); rxPos = 0 }
                val h = rxHead!!
                val n = minOf(len, h.size - rxPos)
                System.arraycopy(h, rxPos, buf, off, n)
                rxPos += n
                if (rxPos >= h.size) rxHead = null
                return n
            }
        }

        @Volatile var readTimeoutMs: Long = 30_000

        val input: InputStream = object : InputStream() {
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF }
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else this@MuxConnection.read(b, off, len, readTimeoutMs)
            override fun available(): Int = synchronized(lock) { (rxHead?.size?.minus(rxPos) ?: 0) + rx.sumOf { it.size } }
        }
        val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()))
            override fun write(b: ByteArray, off: Int, len: Int) = this@MuxConnection.write(b, off, len)
        }

        fun readFully(n: Int, timeoutMs: Long = readTimeoutMs): ByteArray {
            val out = ByteArray(n); var got = 0
            while (got < n) {
                val r = read(out, got, n - got, timeoutMs)
                if (r < 0) throw IOException("Connection to the iPod closed")
                got += r
            }
            return out
        }

        internal fun abort() {
            synchronized(lock) { connected = false; finished = true; lock.notifyAll() }
        }

        fun close() {
            synchronized(lock) {
                if (connected && !closed) runCatching { sendTcp(TH_RST, null) }
                connected = false; finished = true
                lock.notifyAll()
            }
            conns.remove(sport)
        }
    }
}
