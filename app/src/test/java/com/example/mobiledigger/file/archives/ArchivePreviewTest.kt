package com.example.mobiledigger.file.archives

import com.example.mobiledigger.file.drives.DriveClient
import com.example.mobiledigger.file.drives.RemoteChannel
import com.example.mobiledigger.file.drives.RemoteEntry
import com.example.mobiledigger.file.drives.RemoteStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivePreviewTest {
    private fun res(name: String) = File(javaClass.classLoader!!.getResource("archives/$name")!!.toURI())
    private val expected = listOf("Album/01 Track.flac", "Album/cover.jpg", "Album/sub/02 B.mp3")

    @Test fun listsEveryType() {
        for (n in listOf("t.zip", "t.7z", "t.rar")) {
            val entries = ArchiveExtractor.list(res(n))
            assertEquals(n, expected, entries.map { it.path }.sorted())
            assertEquals(n, 2, entries.count { it.isAudio })
            assertTrue(n, entries.all { it.size > 0 })
        }
    }

    @Test fun extractsOnlyTheSelection() {
        for (n in listOf("t.zip", "t.7z", "t.rar")) {
            val got = LinkedHashMap<String, ByteArray>()
            val count = ArchiveExtractor.extract(res(n), { rel, input, _ -> got[rel] = input.readBytes() }, include = setOf("Album/sub/02 B.mp3"))
            assertEquals(n, 1, count)
            assertEquals(n, setOf("Album/sub/02 B.mp3"), got.keys)
            assertArrayEquals(n, res("expected/sub/02 B.mp3").readBytes(), got["Album/sub/02 B.mp3"])
        }
    }

    /** A "network drive" serving one local file, counting nothing itself (RemoteChannel counts). */
    private class FakeDrive(private val file: File) : DriveClient {
        var opens = 0
        override fun list(path: String) = emptyList<RemoteEntry>()
        override fun open(path: String, offset: Long): RemoteStream {
            opens++
            val raf = RandomAccessFile(file, "r").apply { seek(offset) }
            val input = object : InputStream() {
                override fun read() = raf.read()
                override fun read(b: ByteArray, off: Int, len: Int) = raf.read(b, off, len)
                override fun close() = raf.close()
            }
            return RemoteStream(input, file.length())
        }
        override fun delete(path: String) {}
        override fun mkdirs(path: String) {}
        override fun write(path: String, input: InputStream, length: Long) {}
    }

    @Test fun previewOfBigRemoteZipReadsOnlyTheTableOfContents() {
        val dir = Files.createTempDirectory("preview").toFile()
        val big = File(dir, "big.zip")
        val payload = ByteArray(10 * 1024 * 1024) { (it * 7).toByte() }
        ZipOutputStream(big.outputStream()).use { z ->
            for (i in 1..3) {
                val e = ZipEntry("Release/0$i Track.wav").apply {
                    method = ZipEntry.STORED; size = payload.size.toLong(); compressedSize = size
                    crc = CRC32().apply { update(payload) }.value
                }
                z.putNextEntry(e); z.write(payload); z.closeEntry()
            }
        }
        val drive = FakeDrive(big)
        val ch = RemoteChannel(drive, "big.zip", big.length())
        val entries = ArchiveExtractor.list(ch, "zip", dir)
        assertEquals(3, entries.size)
        println("listing a ${big.length() / 1024 / 1024} MB zip fetched ${ch.bytesFetched} bytes in ${drive.opens} requests")
        assertTrue("fetched ${ch.bytesFetched}", ch.bytesFetched < 300 * 1024)

        // Extract just one entry through the same remote channel
        val ch2 = RemoteChannel(drive, "big.zip", big.length())
        val got = LinkedHashMap<String, ByteArray>()
        ArchiveExtractor.extract(ch2, "zip", { rel, input, _ -> got[rel] = input.readBytes() }, dir, include = setOf("Release/02 Track.wav"))
        assertArrayEquals(payload, got["Release/02 Track.wav"])
        assertTrue("fetched ${ch2.bytesFetched}", ch2.bytesFetched < payload.size + 600 * 1024)
        dir.deleteRecursively()
    }
}
