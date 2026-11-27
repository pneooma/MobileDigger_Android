package com.example.mobiledigger.ipod

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Date

class TouchSignatureTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun vector1() {
        val r = ByteArray(83) { i -> if (i < 2) 1 else (i - 2).toByte() }
        assertEquals("014c5ba593afea9fd05e4e47387013a9fa1848e30a", hex(TouchSignature.cig("hello".toByteArray(), r)!!))
    }

    @Test fun vector2() {
        val r = TouchSignature.hostGrappa.copyOf(83)
        assertEquals("01d4eb70bae6add71ee28f76b96f06a40ad0397347", hex(TouchSignature.cig("<plist/>".toByteArray(), r)!!))
    }

    /** Writes a Sync-like binary plist for an external check (python plistlib) and round-trips it. */
    @Test fun binaryPlist() {
        val doc = linkedMapOf<String, Any?>(
            "revision" to 13L, "timestamp" to Date(1764000000000L),
            "operations" to listOf(
                linkedMapOf("operation" to "update_db_info", "pid" to -5155203555965375L,
                    "db_info" to mapOf("primary_container_pid" to -6076804201270996037L)),
                linkedMapOf("operation" to "insert_playlist", "pid" to 1234567890123L,
                    "container" to linkedMapOf("name" to "Café ☕ mix", "date_created" to Date(1764000000000L)),
                    "item_to_container" to listOf(5038471926650183427L, -2971064853318807615L, 7L, 300L, 70000L),
                    "flag" to true, "rate" to 44100.0, "blob" to byteArrayOf(1, 2, 3))
            )
        )
        val bytes = Plist.toBinary(doc)
        File("build/plist-test.bplist").apply { parentFile?.mkdirs() }.writeBytes(bytes)
        val back = Plist.dict(Plist.parseBinary(bytes))
        assertEquals(13L, back["revision"])
        val ops = back["operations"] as List<*>
        val pl = Plist.dict(ops[1])
        assertEquals(listOf(5038471926650183427L, -2971064853318807615L, 7L, 300L, 70000L), pl["item_to_container"])
        assertEquals("Café ☕ mix", Plist.dict(pl["container"])["name"])
        assertEquals(-6076804201270996037L, Plist.dict(Plist.dict(ops[0])["db_info"])["primary_container_pid"])
    }
}
