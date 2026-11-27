package com.example.mobiledigger.ipod

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Property lists, XML and binary ("bplist00"), as Apple's lockdown / ATC services speak them.
 * Values: Map<String, Any?>, List<Any?>, String, Long (and Int), Double, Boolean, ByteArray, Date.
 * Integers are written as integers and Double as real, so pick the Kotlin type accordingly.
 */
object Plist {
    private const val APPLE_EPOCH_MS = 978_307_200_000L // 2001-01-01T00:00:00Z

    // ------------------------------------------------------------------ XML

    fun toXml(root: Any?): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
        sb.append("<plist version=\"1.0\">\n")
        xmlValue(sb, root, 0)
        sb.append("</plist>\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun xmlValue(sb: StringBuilder, v: Any?, depth: Int) {
        val ind = "\t".repeat(depth)
        when (v) {
            null -> sb.append(ind).append("<string></string>\n")
            is Map<*, *> -> {
                sb.append(ind).append("<dict>\n")
                for ((k, value) in v) {
                    if (value == null) continue
                    sb.append(ind).append("\t<key>").append(esc(k.toString())).append("</key>\n")
                    xmlValue(sb, value, depth + 1)
                }
                sb.append(ind).append("</dict>\n")
            }
            is List<*> -> {
                sb.append(ind).append("<array>\n")
                v.forEach { xmlValue(sb, it, depth + 1) }
                sb.append(ind).append("</array>\n")
            }
            is String -> sb.append(ind).append("<string>").append(esc(v)).append("</string>\n")
            is Boolean -> sb.append(ind).append(if (v) "<true/>\n" else "<false/>\n")
            is Int, is Long, is Short, is Byte -> sb.append(ind).append("<integer>").append(v).append("</integer>\n")
            is Double, is Float -> sb.append(ind).append("<real>").append(v).append("</real>\n")
            is ByteArray -> sb.append(ind).append("<data>").append(Base64.encodeToString(v, Base64.NO_WRAP)).append("</data>\n")
            is Date -> sb.append(ind).append("<date>").append(isoDate(v)).append("</date>\n")
            else -> sb.append(ind).append("<string>").append(esc(v.toString())).append("</string>\n")
        }
    }

    private fun isoDate(d: Date): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(d)
    }

    fun parse(bytes: ByteArray): Any? =
        if (bytes.size >= 8 && String(bytes, 0, 8, Charsets.US_ASCII) == "bplist00") parseBinary(bytes) else parseXml(bytes)

    fun parseXml(bytes: ByteArray): Any? {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(bytes.inputStream(), "UTF-8")
        // Advance to the first value inside <plist>
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name != "plist") return xmlRead(p)
        }
        return null
    }

    private fun xmlRead(p: XmlPullParser): Any? = when (p.name) {
        "dict" -> {
            val m = LinkedHashMap<String, Any?>()
            var key: String? = null
            while (p.next() != XmlPullParser.END_TAG || p.name != "dict") {
                if (p.eventType != XmlPullParser.START_TAG) continue
                if (p.name == "key") key = p.nextText() else { m[key ?: ""] = xmlRead(p); key = null }
            }
            m
        }
        "array" -> {
            val l = ArrayList<Any?>()
            while (p.next() != XmlPullParser.END_TAG || p.name != "array") {
                if (p.eventType == XmlPullParser.START_TAG) l.add(xmlRead(p))
            }
            l
        }
        "string" -> p.nextText()
        "integer" -> p.nextText().trim().let { it.toLongOrNull() ?: it.toBigInteger().toLong() }
        "real" -> p.nextText().trim().toDouble()
        "true" -> { p.next(); true }
        "false" -> { p.next(); false }
        "data" -> Base64.decode(p.nextText().replace(Regex("\\s"), ""), Base64.DEFAULT)
        "date" -> p.nextText().let { s ->
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC"); f.parse(s)
        }
        else -> { p.nextText(); null }
    }

    // ------------------------------------------------------------------ binary

    fun toBinary(root: Any?): ByteArray {
        val objects = ArrayList<Any?>()
        // Flatten: each object gets an index; containers hold their children's indexes.
        fun add(v: Any?): Int {
            val idx = objects.size
            objects.add(null)
            objects[idx] = when (v) {
                is Map<*, *> -> {
                    val entries = v.entries.filter { it.value != null }
                    val keys = entries.map { add(it.key.toString()) }
                    val vals = entries.map { add(it.value) }
                    DictRef(keys, vals)
                }
                is List<*> -> ArrayRef(v.map { add(it) })
                else -> v
            }
            return idx
        }
        add(root)
        val n = objects.size
        val refSize = if (n < 256) 1 else if (n < 65536) 2 else 4
        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray(Charsets.US_ASCII))
        val offsets = LongArray(n)
        for ((i, o) in objects.withIndex()) {
            offsets[i] = out.size().toLong()
            writeObject(out, o, refSize)
        }
        val tableOffset = out.size().toLong()
        val offSize = when {
            tableOffset < 256 -> 1; tableOffset < 65536 -> 2; tableOffset < 4294967296L -> 4; else -> 8
        }
        for (o in offsets) writeBE(out, o, offSize)
        // trailer
        repeat(6) { out.write(0) }
        out.write(offSize); out.write(refSize)
        writeBE(out, n.toLong(), 8); writeBE(out, 0, 8); writeBE(out, tableOffset, 8)
        return out.toByteArray()
    }

    private class DictRef(val keys: List<Int>, val values: List<Int>)
    private class ArrayRef(val items: List<Int>)

    private fun writeBE(out: ByteArrayOutputStream, v: Long, size: Int) {
        for (i in size - 1 downTo 0) out.write(((v ushr (8 * i)) and 0xFF).toInt())
    }

    private fun writeMarker(out: ByteArrayOutputStream, type: Int, count: Int) {
        if (count < 15) out.write(type or count)
        else { out.write(type or 0xF); writeInt(out, count.toLong()) }
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Long) {
        when {
            v < 0 -> { out.write(0x13); writeBE(out, v, 8) }
            v < 0x100 -> { out.write(0x10); writeBE(out, v, 1) }
            v < 0x10000 -> { out.write(0x11); writeBE(out, v, 2) }
            v < 0x100000000L -> { out.write(0x12); writeBE(out, v, 4) }
            else -> { out.write(0x13); writeBE(out, v, 8) }
        }
    }

    private fun writeObject(out: ByteArrayOutputStream, o: Any?, refSize: Int) {
        when (o) {
            null -> out.write(0x00)
            is Boolean -> out.write(if (o) 0x09 else 0x08)
            is Int -> writeInt(out, o.toLong())
            is Long -> writeInt(out, o)
            is Short -> writeInt(out, o.toLong())
            is Byte -> writeInt(out, o.toLong())
            is Double -> { out.write(0x23); writeBE(out, java.lang.Double.doubleToRawLongBits(o), 8) }
            is Float -> { out.write(0x23); writeBE(out, java.lang.Double.doubleToRawLongBits(o.toDouble()), 8) }
            is Date -> { out.write(0x33); writeBE(out, java.lang.Double.doubleToRawLongBits((o.time - APPLE_EPOCH_MS) / 1000.0), 8) }
            is ByteArray -> { writeMarker(out, 0x40, o.size); out.write(o) }
            is String -> {
                if (o.all { it.code < 128 }) { writeMarker(out, 0x50, o.length); out.write(o.toByteArray(Charsets.US_ASCII)) }
                else { val b = o.toByteArray(Charsets.UTF_16BE); writeMarker(out, 0x60, b.size / 2); out.write(b) }
            }
            is ArrayRef -> { writeMarker(out, 0xA0, o.items.size); o.items.forEach { writeBE(out, it.toLong(), refSize) } }
            is DictRef -> {
                writeMarker(out, 0xD0, o.keys.size)
                o.keys.forEach { writeBE(out, it.toLong(), refSize) }
                o.values.forEach { writeBE(out, it.toLong(), refSize) }
            }
            else -> { val s = o.toString(); writeMarker(out, 0x50, s.length); out.write(s.toByteArray(Charsets.US_ASCII)) }
        }
    }

    fun parseBinary(b: ByteArray): Any? {
        require(b.size >= 40) { "binary plist too short" }
        val t = b.size - 32
        val offSize = b[t + 6].toInt() and 0xFF
        val refSize = b[t + 7].toInt() and 0xFF
        val numObjects = readBE(b, t + 8, 8).toInt()
        val top = readBE(b, t + 16, 8).toInt()
        val tableOffset = readBE(b, t + 24, 8).toInt()
        val offsets = IntArray(numObjects) { readBE(b, tableOffset + it * offSize, offSize).toInt() }

        fun lenAt(pos: Int, low: Int): Pair<Int, Int> { // (length, start of content)
            if (low != 0xF) return low to pos + 1
            val m = b[pos + 1].toInt() and 0xFF
            val sz = 1 shl (m and 0xF)
            return readBE(b, pos + 2, sz).toInt() to pos + 2 + sz
        }

        fun obj(i: Int, depth: Int): Any? {
            require(depth < 64) { "plist too deep" }
            val pos = offsets[i]
            val marker = b[pos].toInt() and 0xFF
            val hi = marker shr 4
            val low = marker and 0xF
            return when (hi) {
                0x0 -> when (marker) { 0x08 -> false; 0x09 -> true; else -> null }
                0x1 -> {
                    val sz = 1 shl low
                    if (sz == 16) readBE(b, pos + 9, 8) else {
                        val v = readBE(b, pos + 1, sz)
                        if (sz == 8) v else v // 1/2/4-byte ints are unsigned, 8-byte signed
                    }
                }
                0x2 -> if (low == 2) java.lang.Float.intBitsToFloat(readBE(b, pos + 1, 4).toInt()).toDouble()
                       else java.lang.Double.longBitsToDouble(readBE(b, pos + 1, 8))
                0x3 -> Date(APPLE_EPOCH_MS + (java.lang.Double.longBitsToDouble(readBE(b, pos + 1, 8)) * 1000).toLong())
                0x4 -> { val (n, s) = lenAt(pos, low); b.copyOfRange(s, s + n) }
                0x5 -> { val (n, s) = lenAt(pos, low); String(b, s, n, Charsets.US_ASCII) }
                0x6 -> { val (n, s) = lenAt(pos, low); String(b, s, n * 2, Charsets.UTF_16BE) }
                0x7 -> { val (n, s) = lenAt(pos, low); String(b, s, n, Charsets.UTF_8) }
                0x8 -> readBE(b, pos + 1, low + 1) // UID
                0xA, 0xC -> {
                    val (n, s) = lenAt(pos, low)
                    (0 until n).map { obj(readBE(b, s + it * refSize, refSize).toInt(), depth + 1) }
                }
                0xD -> {
                    val (n, s) = lenAt(pos, low)
                    val m = LinkedHashMap<String, Any?>()
                    for (k in 0 until n) {
                        val key = obj(readBE(b, s + k * refSize, refSize).toInt(), depth + 1)
                        val value = obj(readBE(b, s + (n + k) * refSize, refSize).toInt(), depth + 1)
                        m[key.toString()] = value
                    }
                    m
                }
                else -> null
            }
        }
        return obj(top, 0)
    }

    private fun readBE(b: ByteArray, pos: Int, size: Int): Long {
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
        return v
    }

    // ------------------------------------------------------------------ helpers for reading values

    @Suppress("UNCHECKED_CAST")
    fun dict(v: Any?): Map<String, Any?> = (v as? Map<String, Any?>) ?: emptyMap()
    fun long(v: Any?): Long? = when (v) { is Number -> v.toLong(); is String -> v.toLongOrNull(); else -> null }
    fun str(v: Any?): String? = when (v) { is String -> v; is Number -> v.toString(); else -> null }
    fun bool(v: Any?): Boolean? = when (v) { is Boolean -> v; is Number -> v.toLong() != 0L; else -> null }
}
