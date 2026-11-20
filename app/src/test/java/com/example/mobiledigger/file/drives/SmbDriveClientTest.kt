package com.example.mobiledigger.file.drives

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.Socket

/**
 * Integration test against a local SMB server. Skipped unless one is running:
 *   smbserver.py MUSIC <dir> -smb2support -username dj -password crate -port 4455
 * with SMB_TEST_DIR=<dir> in the environment (same folder, used to compare bytes).
 */
class SmbDriveClientTest {
    private val dir = System.getenv("SMB_TEST_DIR")?.let { File(it) }

    private fun serverUp() = runCatching { Socket("127.0.0.1", 4455).close(); true }.getOrDefault(false)

    private fun drive(pass: String = "crate") = NetworkDrive(
        name = "test", type = DriveType.SMB, address = "127.0.0.1:4455",
        share = "MUSIC", username = "dj", password = pass
    )

    @Test fun listsAndReadsWithOffsets() {
        assumeTrue(dir != null && serverUp())
        val c = SmbDriveClient(drive())
        try {
            val top = c.list("")
            assertTrue(top.any { it.isDir && it.name == "Techno" })
            assertTrue(top.any { it.isDir && it.name == "House" })

            val house = c.list("House")
            val aiff = house.single { !it.isDir }
            assertEquals("Höhe & Tiefe [Edit].aiff", aiff.name)
            assertEquals("House/Höhe & Tiefe [Edit].aiff", aiff.path)
            val local = File(dir, aiff.path).readBytes()
            assertEquals(local.size.toLong(), aiff.size)

            // Full read
            c.open(aiff.path, 0).use { rs ->
                assertEquals(local.size.toLong(), rs.totalSize)
                assertArrayEquals(local, rs.input.readBytes())
            }
            // Ranged read (what a player does when you seek)
            val off = 1_000_003L
            c.open(aiff.path, off).use { rs ->
                val part = ByteArray(70_000)
                var got = 0
                while (got < part.size) { val n = rs.input.read(part, got, part.size - got); if (n <= 0) break; got += n }
                assertEquals(part.size, got)
                assertArrayEquals(local.copyOfRange(off.toInt(), off.toInt() + part.size), part)
            }

            val all = DriveClients.listAudioRecursive(c, "")
            assertEquals(3, all.size)
            assertTrue(all.any { (folder, e) -> folder == "Techno/Peak" && e.name == "Pink Noise 128k.mp3" })
        } finally { c.close() }
    }

    @Test fun writesMakesFoldersAndDeletes() {
        assumeTrue(dir != null && serverUp())
        val c = SmbDriveClient(drive())
        try {
            val data = ByteArray(300_000) { (it * 31).toByte() }
            c.mkdirs("_test/Unpacked Ä/sub")
            c.write("_test/Unpacked Ä/sub/x.flac", data.inputStream(), data.size.toLong())
            assertArrayEquals(data, File(dir, "_test/Unpacked Ä/sub/x.flac").readBytes())
            c.mkdirs("_test/Unpacked Ä/sub") // already exists: no error
            c.delete("_test/Unpacked Ä/sub/x.flac")
            assertTrue(!File(dir, "_test/Unpacked Ä/sub/x.flac").exists())
        } finally {
            c.close()
            File(dir, "_test").deleteRecursively()
        }
    }

    @Test fun wrongPasswordGivesReadableError() {
        assumeTrue(dir != null && serverUp())
        val c = SmbDriveClient(drive(pass = "nope"))
        val e = runCatching { c.list("") }.exceptionOrNull()
        assertTrue("expected a DriveException, got $e", e is DriveException)
        println("wrong password -> ${e?.message}")
        c.close()
    }
}
