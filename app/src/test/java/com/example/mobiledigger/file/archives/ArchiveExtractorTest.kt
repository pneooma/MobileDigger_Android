package com.example.mobiledigger.file.archives

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ArchiveExtractorTest {
    private fun res(name: String) = File(javaClass.classLoader!!.getResource("archives/$name")!!.toURI())

    private val expected = listOf("Album/01 Track.flac", "Album/sub/02 B.mp3", "Album/cover.jpg")

    private fun extractAll(name: String): Map<String, ByteArray> {
        val got = LinkedHashMap<String, ByteArray>()
        val n = ArchiveExtractor.extract(res(name), { rel, input, _ -> got[rel] = input.readBytes() })
        assertEquals(got.size, n)
        return got
    }

    private fun check(name: String) {
        val got = extractAll(name)
        assertEquals("$name entries", expected.sorted(), got.keys.sorted())
        for (rel in expected) {
            assertArrayEquals("$name $rel bytes", res("expected/${rel.removePrefix("Album/")}").readBytes(), got[rel])
        }
    }

    @Test fun zip() = check("t.zip")          // also proves __MACOSX junk is skipped
    @Test fun sevenZ() = check("t.7z")
    @Test fun rar4() = check("t.rar")         // backslash paths become folders

    @Test fun zipSlipIsBlocked() {
        val got = extractAll("slip.zip")
        assertEquals(setOf("ok/good.txt"), got.keys)
    }

    @Test fun passwordZipGivesReadableError() {
        try {
            extractAll("locked.zip"); fail("expected ArchiveException")
        } catch (e: ArchiveException) {
            assertTrue(e.message!!, "password" in e.message!!)
        }
    }

    @Test fun safeRelRules() {
        assertNull(ArchiveExtractor.safeRel("../x"))
        assertNull(ArchiveExtractor.safeRel("__MACOSX/a"))
        assertNull(ArchiveExtractor.safeRel("a/.DS_Store"))
        assertEquals("a/b.mp3", ArchiveExtractor.safeRel("/a\\b.mp3"))
        assertEquals("a/what_.mp3", ArchiveExtractor.safeRel("a/what?.mp3"))
    }
}
